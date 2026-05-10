package com.quantumcoinwallet.app.backup;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.quantumcoinwallet.app.Logger;
import com.quantumcoinwallet.app.keystorage.SecureStorage;
import com.quantumcoinwallet.app.utils.GlobalMethods;
import com.quantumcoinwallet.app.utils.PrefConnect;
import com.quantumcoinwallet.app.viewmodel.JsonViewModel;
import com.quantumcoinwallet.app.viewmodel.KeyViewModel;

import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reusable encrypt + Storage-Access-Framework save pipeline for both
 * cloud-folder and file-export wallet backups. Owns the
 * {@link ActivityResultLauncher}s plus the pending-state stash so a
 * fragment that hosts a backup-options surface can fire either flow
 * without leaving the screen.
 *
 * <p><b>Why this exists:</b> previously the backup flow lived inside
 * {@code WalletsFragment} and the {@code BackupOptionsFragment} would
 * tell {@code HomeActivity} to swap to a fresh {@code WalletsFragment}
 * with auto-trigger arguments. The side effect was that a successful
 * backup left the user looking at the wallets list, NOT the backup
 * screen — they had to navigate back into the backup screen to pick
 * the second backup option (cloud after file or vice versa). Mirroring
 * iOS, the backup screen now stays open after a successful backup.
 *
 * <p><b>SAF launchers:</b> activity-result launchers MUST be registered
 * before {@link Fragment#onStart()}. Construct a {@code BackupExecutor}
 * in {@code Fragment.onCreate(...)} and never construct one lazily on
 * a button click.
 *
 * <p><b>iOS counterpart:</b> {@code BackupOptionsViewController.swift}
 * runs the equivalent encrypt + UIDocumentPickerViewController save
 * pipeline inline; the ViewController stays in the navigation stack.
 */
public final class BackupExecutor {

    private static final String TAG = "BackupExecutor";

    private final Fragment host;
    private final JsonViewModel vm;

    private final ActivityResultLauncher<Intent> exportCreateDocumentLauncher;
    private final ActivityResultLauncher<Intent> cloudBackupFolderPickerLauncher;

    private String pendingExportEncryptedJson;
    private String pendingExportWalletAddress;
    private String pendingExportFilename;
    private String pendingCloudEncryptedJson;
    private String pendingCloudAddress;

    public BackupExecutor(final Fragment host, final JsonViewModel vm) {
        this.host = host;
        this.vm = vm;
        this.exportCreateDocumentLauncher = host.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        markActivityUnlocked();
                        final String encryptedJson = pendingExportEncryptedJson;
                        pendingExportEncryptedJson = null;
                        pendingExportWalletAddress = null;
                        pendingExportFilename = null;
                        if (result == null
                                || result.getResultCode() != Activity.RESULT_OK
                                || result.getData() == null
                                || result.getData().getData() == null
                                || encryptedJson == null) {
                            return;
                        }
                        final Uri uri = result.getData().getData();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                writeExportToUri(uri, encryptedJson);
                            }
                        }).start();
                    }
                });
        this.cloudBackupFolderPickerLauncher = host.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        markActivityUnlocked();
                        final String encryptedJson = pendingCloudEncryptedJson;
                        final String address = pendingCloudAddress;
                        pendingCloudEncryptedJson = null;
                        pendingCloudAddress = null;
                        if (result == null
                                || result.getResultCode() != Activity.RESULT_OK
                                || result.getData() == null
                                || result.getData().getData() == null
                                || encryptedJson == null
                                || address == null) {
                            return;
                        }
                        final Uri treeUri = result.getData().getData();
                        try {
                            int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                            host.requireContext().getContentResolver()
                                    .takePersistableUriPermission(treeUri, takeFlags);
                        } catch (Exception ignore) { }
                        PrefConnect.writeString(host.requireContext(),
                                PrefConnect.CLOUD_BACKUP_FOLDER_URI_KEY,
                                treeUri.toString());
                        writeCloudBackupToFolder(treeUri, encryptedJson, address);
                    }
                });
    }

    /**
     * One-shot OK confirmation explaining the Android cloud-folder picker
     * depends on phone configuration; on OK the supplied {@code next}
     * runs.
     */
    public void showCloudBackupInfoAndContinue(final Runnable next) {
        if (host.getContext() == null) {
            if (next != null) next.run();
            return;
        }
        String message = vm.getCloudBackupInfoByLangValues();
        if (message == null || message.isEmpty()) {
            message = "You will be able to see cloud options only if configured in the phone";
        }
        new AlertDialog.Builder(host.requireContext())
                .setTitle(vm.getBackupByLangValues())
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(vm.getOkByLangValues(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (next != null) next.run();
                    }
                })
                .show();
    }

    /** Begin the cloud-folder backup flow. Prompts the user for the
     *  backup password, encrypts, then either re-uses the saved
     *  cloud folder or launches the document-tree picker. */
    public void startCloudBackup(final String walletAddress,
                                 final String walletPassword) {
        com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog.show(
                host.requireContext(), vm, walletAddress,
                new com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog
                        .OnBackupPasswordListener() {
                    @Override
                    public void onPasswordSelected(final String backupPassword) {
                        encryptAndStartCloudBackup(walletAddress,
                                walletPassword, backupPassword);
                    }
                    @Override
                    public void onCanceled() { }
                });
    }

    /** Begin the file-export backup flow. Same shape as the cloud
     *  flow except the SAF launcher is the create-document picker. */
    public void startFileExport(final String walletAddress,
                                final String walletPassword) {
        com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog.show(
                host.requireContext(), vm, walletAddress,
                new com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog
                        .OnBackupPasswordListener() {
                    @Override
                    public void onPasswordSelected(final String backupPassword) {
                        encryptAndPickExportLocation(walletAddress,
                                walletPassword, backupPassword);
                    }
                    @Override
                    public void onCanceled() { }
                });
    }

    // ---------------------------------------------------------------
    // Cloud (folder picker) pipeline
    // ---------------------------------------------------------------

    private void encryptAndStartCloudBackup(final String walletAddress,
                                            final String unlockPassword,
                                            final String backupPassword) {
        final AlertDialog waitDlg = com.quantumcoinwallet.app.view.dialog.WaitDialog
                .show(host.requireContext(), vm.getWaitWalletSaveByLangValues());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SecureStorage secureStorage = KeyViewModel.getSecureStorage();
                    String indexKey = PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.get(walletAddress);
                    if (indexKey == null) throw new IllegalStateException("wallet index missing");
                    String walletJsonStr = secureStorage.loadWallet(host.requireContext(),
                            Integer.parseInt(indexKey));
                    JSONObject walletJson = new JSONObject(walletJsonStr);
                    final CloudBackupManager.EncryptedResult enc =
                            CloudBackupManager.encryptWallet(walletJson, backupPassword);
                    if (host.getActivity() == null) return;
                    host.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                            dispatchCloudBackup(enc.json, enc.address);
                        }
                    });
                } catch (final Exception e) {
                    Logger.e(TAG, "encryptAndStartCloudBackup failed", e);
                    if (host.getActivity() == null) return;
                    host.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                            String tmpl = vm.getBackupFailedByLangValues();
                            String msg = tmpl != null
                                    ? tmpl.replace("[ERROR]",
                                            e.getMessage() == null ? "" : e.getMessage())
                                    : ("Backup failed: " + e.getMessage());
                            GlobalMethods.ShowErrorDialog(host.requireContext(),
                                    vm.getErrorTitleByLangValues(), msg);
                        }
                    });
                }
            }
        }).start();
    }

    private void dispatchCloudBackup(final String encryptedJson, final String address) {
        String folderUriStr = PrefConnect.readString(host.requireContext(),
                PrefConnect.CLOUD_BACKUP_FOLDER_URI_KEY, "");
        if (folderUriStr == null || folderUriStr.isEmpty()) {
            pendingCloudEncryptedJson = encryptedJson;
            pendingCloudAddress = address;
            try {
                setSuppressNextResumeLock(true);
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                cloudBackupFolderPickerLauncher.launch(intent);
            } catch (Exception e) {
                setSuppressNextResumeLock(false);
                pendingCloudEncryptedJson = null;
                pendingCloudAddress = null;
                GlobalMethods.ExceptionError(host.requireContext(), TAG, e);
            }
            return;
        }
        writeCloudBackupToFolder(Uri.parse(folderUriStr), encryptedJson, address);
    }

    private void writeCloudBackupToFolder(final Uri folderUri,
                                          final String encryptedJson,
                                          final String address) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String filename = CloudBackupManager.buildFilename(address);
                    final CloudBackupManager.BackupWriteOutcome outcome =
                            CloudBackupManager.writeToSafFolderVerified(
                                    host.requireContext(), folderUri, filename, encryptedJson);
                    if (outcome.kind == CloudBackupManager.BackupWriteKind.VERIFY_FAILED) {
                        throw new java.io.IOException("verify-by-readback failed: "
                                + (outcome.detail == null ? "unknown" : outcome.detail));
                    }
                    if (host.getActivity() == null) return;
                    host.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (outcome.kind
                                    == CloudBackupManager.BackupWriteKind.SUBMITTED_CLOUD) {
                                String title = vm.getBackupSubmittedTitleByLangValues();
                                String body = vm.getBackupSubmittedBodyByLangValues();
                                if (title == null || title.isEmpty()) title = "Backup submitted";
                                if (body == null || body.isEmpty()) {
                                    body = "Your backup has been submitted to the cloud destination. "
                                         + "It may take a moment to appear on your other devices "
                                         + "depending on the provider's sync state. Press OK to dismiss.";
                                }
                                GlobalMethods.ShowMessageDialog(host.requireContext(),
                                        title, body, null);
                            } else {
                                String tmpl = vm.getBackupSavedByLangValues();
                                String msg = tmpl != null
                                        ? tmpl.replace("[FOLDER]", "")
                                              .replace("[FILENAME]", filename)
                                        : "Wallet backed up";
                                GlobalMethods.ShowMessageDialog(host.requireContext(),
                                        null, msg, null);
                            }
                        }
                    });
                } catch (final Exception e) {
                    Logger.e(TAG, "writeCloudBackupToFolder failed", e);
                    if (host.getActivity() == null) return;
                    host.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String tmpl = vm.getBackupFailedByLangValues();
                            String msg = tmpl != null
                                    ? tmpl.replace("[ERROR]",
                                            e.getMessage() == null ? "" : e.getMessage())
                                    : ("Backup failed: " + e.getMessage());
                            GlobalMethods.ShowErrorDialog(host.requireContext(),
                                    vm.getErrorTitleByLangValues(), msg);
                        }
                    });
                }
            }
        }).start();
    }

    // ---------------------------------------------------------------
    // File (create-document) pipeline
    // ---------------------------------------------------------------

    private void encryptAndPickExportLocation(final String walletAddress,
                                              final String unlockPassword,
                                              final String backupPassword) {
        final AlertDialog waitDlg = com.quantumcoinwallet.app.view.dialog.WaitDialog
                .show(host.requireContext(), vm.getWaitWalletSaveByLangValues());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SecureStorage secureStorage = KeyViewModel.getSecureStorage();
                    String indexKey = PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.get(walletAddress);
                    if (indexKey == null) throw new IllegalStateException("wallet index missing");
                    String walletJsonStr = secureStorage.loadWallet(host.requireContext(),
                            Integer.parseInt(indexKey));
                    JSONObject walletJson = new JSONObject(walletJsonStr);
                    final CloudBackupManager.EncryptedResult enc =
                            CloudBackupManager.encryptWallet(walletJson, backupPassword);
                    final String filename = CloudBackupManager.buildFilename(enc.address);
                    if (host.getActivity() == null) return;
                    host.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                            launchExportSavePicker(enc.json, enc.address, filename);
                        }
                    });
                } catch (final Exception e) {
                    Logger.e(TAG, "encryptAndPickExportLocation failed", e);
                    if (host.getActivity() == null) return;
                    host.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                            String tmpl = vm.getBackupFailedByLangValues();
                            String msg = tmpl != null
                                    ? tmpl.replace("[ERROR]",
                                            e.getMessage() == null ? "" : e.getMessage())
                                    : ("Export failed: " + e.getMessage());
                            GlobalMethods.ShowErrorDialog(host.requireContext(),
                                    vm.getErrorTitleByLangValues(), msg);
                        }
                    });
                }
            }
        }).start();
    }

    private void launchExportSavePicker(String encryptedJson, String address, String filename) {
        pendingExportEncryptedJson = encryptedJson;
        pendingExportWalletAddress = address;
        pendingExportFilename = filename;
        try {
            setSuppressNextResumeLock(true);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(CloudBackupManager.BACKUP_MIME_TYPE);
            intent.putExtra(Intent.EXTRA_TITLE, filename);
            String folderUriStr = PrefConnect.readString(host.requireContext(),
                    PrefConnect.CLOUD_BACKUP_FOLDER_URI_KEY, "");
            if (folderUriStr != null && !folderUriStr.isEmpty()) {
                try {
                    intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                            Uri.parse(folderUriStr));
                } catch (Exception ignore) { }
            }
            exportCreateDocumentLauncher.launch(intent);
        } catch (Exception e) {
            setSuppressNextResumeLock(false);
            GlobalMethods.ExceptionError(host.requireContext(), TAG, e);
            pendingExportEncryptedJson = null;
            pendingExportWalletAddress = null;
            pendingExportFilename = null;
        }
    }

    /**
     * Single-file export path with verify-by-readback and
     * cloud-vs-local message branching that mirrors iOS.
     *
     * <ol>
     *   <li>Write the ciphertext bytes through the SAF
     *       {@link OutputStream}, then re-open the same Uri for
     *       reading and byte-compare against the original. A
     *       mismatch surfaces the existing
     *       {@code getBackupFailedByLangValues()} error dialog —
     *       the user must NEVER see "saved" for a corrupted
     *       readback (that would be a silent data-loss path).</li>
     *   <li>On verify success, branch on
     *       {@link CloudBackupManager#isCloudFolder(Uri)}:
     *       <ul>
     *         <li>Cloud authority → modal {@link AlertDialog} with
     *             {@code backup-submitted-cloud-title} /
     *             {@code backup-submitted-cloud-message} so the
     *             user knows the upload is still in flight and the
     *             backup is NOT yet fully durable.</li>
     *         <li>Local authority → existing
     *             {@code backup-saved} toast/dialog (durability is
     *             established at write time on local storage).</li>
     *       </ul>
     *   </li>
     * </ol>
     */
    private void writeExportToUri(final Uri uri, final String encryptedJson) {
        OutputStream os = null;
        try {
            byte[] payload = encryptedJson.getBytes(StandardCharsets.UTF_8);
            os = host.requireContext().getContentResolver().openOutputStream(uri);
            if (os == null) throw new IllegalStateException("openOutputStream returned null");
            os.write(payload);
            os.flush();
            try { os.close(); } catch (Exception ignore) {}
            os = null;

            // Verify-by-readback. Re-open the same Uri and
            // byte-compare so we never tell the user "saved" if the
            // SAF provider silently dropped or mangled the write.
            verifyReadbackOrThrow(uri, payload);

            if (host.getActivity() == null) return;
            final boolean cloud = CloudBackupManager.isCloudFolder(uri);
            host.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String fileName = uri.getLastPathSegment() != null
                            ? uri.getLastPathSegment() : "";
                    if (cloud) {
                        showCloudSubmittedModal("", fileName);
                    } else {
                        String tmpl = vm.getBackupSavedByLangValues();
                        String msg = tmpl != null
                                ? tmpl.replace("[FOLDER]", "")
                                      .replace("[FILENAME]", fileName)
                                : "Wallet exported";
                        GlobalMethods.ShowMessageDialog(host.requireContext(), null, msg, null);
                    }
                }
            });
        } catch (final Exception e) {
            Logger.e(TAG, "export failed", e);
            if (host.getActivity() == null) return;
            host.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String tmpl = vm.getBackupFailedByLangValues();
                    String msg = tmpl != null
                            ? tmpl.replace("[ERROR]",
                                    e.getMessage() == null ? "" : e.getMessage())
                            : ("Export failed: " + e.getMessage());
                    GlobalMethods.ShowErrorDialog(host.requireContext(),
                            vm.getErrorTitleByLangValues(), msg);
                }
            });
        } finally {
            if (os != null) { try { os.close(); } catch (Exception ignore) {} }
        }
    }

    /**
     * Read the just-written URI back, bounded by the same cap that
     * {@link CloudBackupManager#readSafFile} enforces, and fail
     * loudly on any byte-level mismatch. Centralised here as a
     * helper so unit tests can exercise the verify failure path.
     */
    static void verifyReadback(java.io.InputStream in, byte[] expected) throws java.io.IOException {
        if (in == null) throw new java.io.IOException("readback openInputStream returned null");
        byte[] readBack = new byte[expected.length];
        int off = 0;
        while (off < readBack.length) {
            int r = in.read(readBack, off, readBack.length - off);
            if (r < 0) {
                throw new java.io.IOException("readback truncated: wrote=" + expected.length
                        + " got=" + off);
            }
            off += r;
        }
        // Drain one extra byte to detect over-long files (provider
        // appended garbage).
        int extra = in.read();
        if (extra >= 0) {
            throw new java.io.IOException("readback longer than written: wrote="
                    + expected.length + ", extra byte present");
        }
        if (!java.util.Arrays.equals(readBack, expected)) {
            throw new java.io.IOException("readback bytes do not match write");
        }
    }

    private void verifyReadbackOrThrow(Uri uri, byte[] expected) throws java.io.IOException {
        java.io.InputStream in = null;
        try {
            in = host.requireContext().getContentResolver().openInputStream(uri);
            verifyReadback(in, expected);
        } finally {
            if (in != null) { try { in.close(); } catch (Exception ignore) {} }
        }
    }

    /**
     * Build and show the cloud-submitted modal. Substitutes
     * {@code [FOLDER]} / {@code [FILENAME]} the same way the
     * existing {@code backup-saved} dialog does.
     */
    private void showCloudSubmittedModal(String folder, String filename) {
        String titleTmpl = vm.getBackupSubmittedCloudTitleByLangValues();
        String bodyTmpl = vm.getBackupSubmittedCloudMessageByLangValues();
        String title = titleTmpl != null ? titleTmpl : "Backup submitted to cloud";
        String body;
        if (bodyTmpl != null) {
            body = bodyTmpl.replace("[FOLDER]", folder == null ? "" : folder)
                           .replace("[FILENAME]", filename == null ? "" : filename);
        } else {
            body = "Wallet submitted to cloud destination. Upload may take a moment.";
        }
        new AlertDialog.Builder(host.requireContext())
                .setTitle(title)
                .setMessage(body)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        d.dismiss();
                    }
                })
                .show();
    }

    // ---------------------------------------------------------------
    // HomeActivity bridge helpers (idle-lock window suppression).
    // ---------------------------------------------------------------

    private void markActivityUnlocked() {
        try {
            Activity act = host.getActivity();
            if (act instanceof com.quantumcoinwallet.app.view.activities.HomeActivity) {
                ((com.quantumcoinwallet.app.view.activities.HomeActivity) act).markUnlockedNow();
            }
        } catch (Exception ignore) { }
    }

    private void setSuppressNextResumeLock(boolean suppress) {
        try {
            Activity act = host.getActivity();
            if (act instanceof com.quantumcoinwallet.app.view.activities.HomeActivity) {
                ((com.quantumcoinwallet.app.view.activities.HomeActivity) act)
                        .setSuppressNextResumeLock(suppress);
            }
        } catch (Exception ignore) { }
    }
}
