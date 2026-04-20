package com.quantumcoinwallet.app.backup;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.quantumcoinwallet.app.bridge.QuantumCoinJSBridge;
import com.quantumcoinwallet.app.utils.PrefConnect;
import com.quantumcoinwallet.app.viewmodel.KeyViewModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Centralized logic for cloud backup, export, and restore of wallets. All I/O happens via
 * Android Storage Access Framework (SAF). Must be called from a background thread.
 */
public class CloudBackupManager {

    private static final String TAG = "CloudBackupManager";

    public static final String BACKUP_FILE_EXTENSION = ".wallet";
    public static final String BACKUP_MIME_TYPE = "application/octet-stream";

    public static class DecryptedWallet {
        public String address;
        public String privateKey;
        public String publicKey;
        public String seed;
        public String[] seedWords;
    }

    /** One qualifying backup file discovered in a folder scan: has a .wallet extension,
     *  parses as JSON, and carries a non-empty address. The encrypted JSON is already
     *  loaded so the decrypt loop does not re-read SAF on every password attempt. */
    public static class BackupCandidate {
        public final Uri uri;
        public final String filename;
        public final String encryptedJson;
        public final String address;
        public BackupCandidate(Uri uri, String filename, String encryptedJson, String address) {
            this.uri = uri;
            this.filename = filename;
            this.encryptedJson = encryptedJson;
            this.address = address;
        }
    }

    public static class EncryptedResult {
        public final String json;
        public final String address;
        public EncryptedResult(String json, String address) {
            this.json = json;
            this.address = address;
        }
    }

    /** Wallet input expected: { address, privateKey, publicKey, seed } where seed is a comma-
     *  separated seed phrase (as stored by SecureStorage). If seed is empty/missing, the
     *  private-key-only encryption path is used. */
    public static EncryptedResult encryptWallet(JSONObject walletJson, String password) throws Exception {
        JSONObject input = new JSONObject();
        String seed = walletJson.optString("seed", "");
        if (seed != null && !seed.isEmpty()) {
            String[] parts = seed.split(",");
            JSONArray arr = new JSONArray();
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) arr.put(trimmed);
            }
            input.put("seedWords", arr);
        } else {
            input.put("privateKey", walletJson.getString("privateKey"));
            input.put("publicKey", walletJson.getString("publicKey"));
        }
        String resultJson = KeyViewModel.getBridge().encryptWalletJson(input.toString(), password);
        JSONObject root = new JSONObject(resultJson);
        JSONObject data = root.getJSONObject("data");
        return new EncryptedResult(data.getString("json"), data.getString("address"));
    }

    public static DecryptedWallet decryptWallet(String encryptedJson, String password) throws Exception {
        String resultJson = KeyViewModel.getBridge().decryptWalletJson(encryptedJson, password);
        JSONObject root = new JSONObject(resultJson);
        JSONObject data = root.getJSONObject("data");
        DecryptedWallet w = new DecryptedWallet();
        w.address = data.optString("address", null);
        w.privateKey = data.optString("privateKey", null);
        w.publicKey = data.optString("publicKey", null);
        w.seed = data.isNull("seed") ? null : data.optString("seed", null);
        if (!data.isNull("seedWords")) {
            JSONArray words = data.optJSONArray("seedWords");
            if (words != null) {
                w.seedWords = new String[words.length()];
                for (int i = 0; i < words.length(); i++) {
                    w.seedWords[i] = words.getString(i);
                }
            }
        }
        return w;
    }

    public static String buildFilename(String address) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss.SSS'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String ts = fmt.format(new Date());
        String addr = address == null ? "unknown" : address.toLowerCase(Locale.US);
        if (addr.startsWith("0x")) addr = addr.substring(2);
        return "UTC--" + ts + "--" + addr + BACKUP_FILE_EXTENSION;
    }

    /** Writes contents to a new file under the given persisted SAF folder. Returns the DocumentFile
     *  that was created (or null on failure). */
    public static DocumentFile writeToSafFolder(Context ctx, Uri folderUri,
                                                String filename, String contents) throws IOException {
        DocumentFile folder = DocumentFile.fromTreeUri(ctx, folderUri);
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            throw new IOException("Backup folder is not accessible");
        }
        DocumentFile file = folder.createFile(BACKUP_MIME_TYPE, filename);
        if (file == null) {
            throw new IOException("Failed to create backup file");
        }
        ContentResolver cr = ctx.getContentResolver();
        OutputStream os = null;
        try {
            os = cr.openOutputStream(file.getUri());
            if (os == null) throw new IOException("openOutputStream returned null");
            os.write(contents.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } finally {
            if (os != null) {
                try { os.close(); } catch (IOException ignored) {}
            }
        }
        return file;
    }

    /** Walks the SAF folder, keeps only files that end in {@link #BACKUP_FILE_EXTENSION},
     *  can be read, parse as a JSON object, and expose a non-empty address field.
     *  Anything else is silently excluded (non-.wallet files, unreadable files,
     *  non-JSON content, JSON without an address). Sorted newest-first by mtime. */
    public static List<BackupCandidate> scanQualifyingBackups(Context ctx, Uri folderUri) {
        List<BackupCandidate> out = new ArrayList<>();
        DocumentFile folder = DocumentFile.fromTreeUri(ctx, folderUri);
        if (folder == null || !folder.exists() || !folder.isDirectory()) return out;

        List<DocumentFile> dotWalletFiles = new ArrayList<>();
        for (DocumentFile f : folder.listFiles()) {
            if (!f.isFile()) continue;
            String name = f.getName();
            if (name == null) continue;
            if (!name.toLowerCase(Locale.US).endsWith(BACKUP_FILE_EXTENSION)) continue;
            dotWalletFiles.add(f);
        }
        Collections.sort(dotWalletFiles, new Comparator<DocumentFile>() {
            @Override
            public int compare(DocumentFile a, DocumentFile b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });

        for (DocumentFile f : dotWalletFiles) {
            String name = f.getName();
            Uri uri = f.getUri();
            String content;
            try {
                content = readSafFile(ctx, uri);
            } catch (IOException ioe) {
                continue;
            }
            if (content == null || content.isEmpty()) continue;
            try {
                new JSONObject(content);
            } catch (Exception parseErr) {
                continue;
            }
            String address = extractAddressFromEncryptedJson(content);
            if (address == null || address.isEmpty()) continue;
            out.add(new BackupCandidate(uri, name, content, address));
        }
        return out;
    }

    public static String readSafFile(Context ctx, Uri fileUri) throws IOException {
        ContentResolver cr = ctx.getContentResolver();
        InputStream is = null;
        BufferedReader reader = null;
        try {
            is = cr.openInputStream(fileUri);
            if (is == null) throw new IOException("openInputStream returned null");
            reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } finally {
            if (reader != null) { try { reader.close(); } catch (IOException ignored) {} }
            if (is != null) { try { is.close(); } catch (IOException ignored) {} }
        }
    }

    public static String extractAddressFromEncryptedJson(String encryptedJson) {
        try {
            JSONObject obj = new JSONObject(encryptedJson);
            String address = obj.optString("address", null);
            if (address == null || address.isEmpty()) return null;
            if (!address.startsWith("0x")) {
                address = "0x" + address;
            }
            return address;
        } catch (Exception e) {
            return null;
        }
    }

}
