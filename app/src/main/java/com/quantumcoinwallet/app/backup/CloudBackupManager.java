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

    public static List<DocumentFile> listBackupFiles(Context ctx, Uri folderUri) {
        List<DocumentFile> out = new ArrayList<>();
        DocumentFile folder = DocumentFile.fromTreeUri(ctx, folderUri);
        if (folder == null || !folder.exists() || !folder.isDirectory()) return out;
        for (DocumentFile f : folder.listFiles()) {
            if (!f.isFile()) continue;
            String name = f.getName();
            if (name == null) continue;
            String lower = name.toLowerCase(Locale.US);
            boolean isDotWallet = lower.endsWith(BACKUP_FILE_EXTENSION);
            int dotIdx = name.lastIndexOf('.');
            boolean hasNoExt = dotIdx <= 0;
            if (isDotWallet || hasNoExt) {
                out.add(f);
            }
        }
        Collections.sort(out, new Comparator<DocumentFile>() {
            @Override
            public int compare(DocumentFile a, DocumentFile b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
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

    /** Callback used by the shared restore loop. All UI work must happen on the main thread;
     *  the loop itself runs on a background thread. */
    public interface RestoreCallback {
        /** Called on the UI thread to update the progress dialog. */
        void onProgress(int current, int total, String filename);

        /** Must show a password prompt (default = last successful password) and call
         *  one of PasswordPromptResult.onProvided / onSkipped. Called on the background
         *  thread; implementations should marshal to the UI thread and block until the
         *  user responds. */
        PasswordPromptResult promptPassword(String filename, String lastPassword, boolean previousAttemptFailed);

        /** Called on the UI thread with the final summary. */
        void onComplete(RestoreSummary summary);
    }

    public static class PasswordPromptResult {
        public final boolean skipped;
        public final String password;
        public PasswordPromptResult(boolean skipped, String password) {
            this.skipped = skipped;
            this.password = password;
        }
        public static PasswordPromptResult skip() { return new PasswordPromptResult(true, null); }
        public static PasswordPromptResult provide(String pw) { return new PasswordPromptResult(false, pw); }
    }

    public static class RestoreSummary {
        public final List<String> restored = new ArrayList<>();
        public final List<String> alreadyPresent = new ArrayList<>();
        public final List<String> failed = new ArrayList<>();
    }

    /** Iterates through the given list of URIs, reads each, extracts the address, checks for
     *  duplicates, prompts for password (with retry/skip) on decrypt failure, saves the wallet
     *  to SecureStorage, and builds a summary. */
    public static void runRestoreLoop(final Context ctx, final List<Uri> uris,
                                      final List<String> displayNames,
                                      final RestoreCallback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                RestoreSummary summary = new RestoreSummary();
                final int total = uris.size();
                String lastPassword = null;
                for (int i = 0; i < total; i++) {
                    final Uri uri = uris.get(i);
                    final String displayName = (displayNames != null && i < displayNames.size())
                            ? displayNames.get(i) : uri.getLastPathSegment();
                    final int current = i + 1;
                    cb.onProgress(current, total, displayName);
                    try {
                        String json = readSafFile(ctx, uri);
                        String address = extractAddressFromEncryptedJson(json);
                        if (address == null) {
                            summary.failed.add(displayName + " (invalid keystore)");
                            continue;
                        }
                        if (PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.containsKey(address)) {
                            summary.alreadyPresent.add(address);
                            continue;
                        }
                        boolean savedThisFile = false;
                        boolean previousFailed = false;
                        while (!savedThisFile) {
                            PasswordPromptResult pr = cb.promptPassword(displayName, lastPassword, previousFailed);
                            if (pr.skipped) {
                                summary.failed.add(address + " (skipped)");
                                break;
                            }
                            try {
                                DecryptedWallet dw = decryptWallet(json, pr.password);
                                lastPassword = pr.password;
                                saveDecryptedWallet(ctx, dw);
                                summary.restored.add(dw.address != null ? dw.address : address);
                                savedThisFile = true;
                            } catch (Exception decryptErr) {
                                Log.w(TAG, "decrypt failed for " + displayName, decryptErr);
                                previousFailed = true;
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "restore error for " + displayName, e);
                        summary.failed.add(displayName + " (" + e.getMessage() + ")");
                    }
                }
                cb.onComplete(summary);
            }
        }).start();
    }

    private static void saveDecryptedWallet(Context ctx, DecryptedWallet w) throws Exception {
        if (w.address == null || w.privateKey == null || w.publicKey == null) {
            throw new IllegalStateException("decrypted wallet missing fields");
        }
        com.quantumcoinwallet.app.keystorage.SecureStorage storage = KeyViewModel.getSecureStorage();
        if (storage == null) throw new IllegalStateException("SecureStorage unavailable");
        if (!storage.isUnlocked()) throw new IllegalStateException("SecureStorage is locked");

        int newIndex = storage.getMaxWalletIndex(ctx) + 1;
        JSONObject walletJson = new JSONObject();
        walletJson.put("address", w.address);
        walletJson.put("privateKey", w.privateKey);
        walletJson.put("publicKey", w.publicKey);
        boolean hasSeed = w.seedWords != null && w.seedWords.length > 0;
        if (hasSeed) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < w.seedWords.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(w.seedWords[i]);
            }
            walletJson.put("seed", sb.toString());
        } else {
            walletJson.put("seed", "");
        }
        storage.saveWallet(ctx, newIndex, walletJson.toString());
        storage.setMaxWalletIndex(ctx, newIndex);

        String indexKey = String.valueOf(newIndex);
        PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.put(w.address, indexKey);
        PrefConnect.WALLET_INDEX_TO_ADDRESS_MAP.put(indexKey, w.address);
        PrefConnect.writeBoolean(ctx, PrefConnect.WALLET_HAS_SEED_KEY_PREFIX + indexKey, hasSeed);
        PrefConnect.WALLET_INDEX_HAS_SEED_MAP.put(indexKey, hasSeed);
    }
}
