package com.quantumcoinwallet.app.view.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.documentfile.provider.DocumentFile;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

import com.quantumcoinwallet.app.backup.CloudBackupManager;
import com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.api.read.model.AccountPendingTransactionSummary;
import com.quantumcoinwallet.app.api.read.model.AccountPendingTransactionSummaryResponse;
import com.quantumcoinwallet.app.api.read.model.AccountTransactionSummary;
import com.quantumcoinwallet.app.api.read.model.AccountTransactionSummaryResponse;
import com.quantumcoinwallet.app.asynctask.read.AccountPendingTxnRestTask;
import com.quantumcoinwallet.app.asynctask.read.AccountTxnRestTask;
import com.quantumcoinwallet.app.entity.KeyServiceException;
import com.quantumcoinwallet.app.keystorage.SecureStorage;
import com.quantumcoinwallet.app.utils.GlobalMethods;
import com.quantumcoinwallet.app.utils.GridAutoFitLayoutManager;
import com.quantumcoinwallet.app.utils.PrefConnect;
import com.quantumcoinwallet.app.utils.Utility;
import com.quantumcoinwallet.app.view.adapter.AccountPendingTransactionAdapter;
import com.quantumcoinwallet.app.view.adapter.AccountTransactionAdapter;
import com.quantumcoinwallet.app.view.adapter.WalletAdapter;
import com.quantumcoinwallet.app.viewmodel.JsonViewModel;
import com.quantumcoinwallet.app.viewmodel.KeyViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import org.apache.commons.lang3.ObjectUtils;
import org.w3c.dom.Text;

import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WalletsFragment extends Fragment  {
    private static final String TAG = "WalletsFragment";
    private WalletAdapter walletAdapter;
    RecyclerView recycler;
    private JsonViewModel jsonViewModel;
    private KeyViewModel keyViewModel;
    private OnWalletsCompleteListener mWalletsListener;

    private ActivityResultLauncher<Intent> exportCreateDocumentLauncher;
    private String pendingExportEncryptedJson;
    private String pendingExportWalletAddress;

    public static WalletsFragment newInstance() {
        WalletsFragment fragment = new WalletsFragment();
        return fragment;
    }

    public WalletsFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        exportCreateDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        final String encryptedJson = pendingExportEncryptedJson;
                        final String address = pendingExportWalletAddress;
                        pendingExportEncryptedJson = null;
                        pendingExportWalletAddress = null;
                        if (result == null || result.getResultCode() != android.app.Activity.RESULT_OK
                                || result.getData() == null || result.getData().getData() == null
                                || encryptedJson == null) {
                            return;
                        }
                        final Uri uri = result.getData().getData();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                OutputStream os = null;
                                try {
                                    os = getContext().getContentResolver().openOutputStream(uri);
                                    if (os == null) throw new IllegalStateException("openOutputStream returned null");
                                    os.write(encryptedJson.getBytes(StandardCharsets.UTF_8));
                                    os.flush();
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            String tmpl = jsonViewModel.getBackupSavedByLangValues();
                                            String msg = tmpl != null
                                                    ? tmpl.replace("[FOLDER]", "")
                                                          .replace("[FILENAME]",
                                                                  uri.getLastPathSegment() != null
                                                                      ? uri.getLastPathSegment() : "")
                                                    : "Wallet exported";
                                            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                                        }
                                    });
                                } catch (final Exception e) {
                                    GlobalMethods.ExceptionError(getContext(), TAG, e);
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            String tmpl = jsonViewModel.getBackupFailedByLangValues();
                                            String msg = tmpl != null
                                                    ? tmpl.replace("[ERROR]",
                                                            e.getMessage() == null ? "" : e.getMessage())
                                                    : ("Export failed: " + e.getMessage());
                                            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                                        }
                                    });
                                } finally {
                                    if (os != null) { try { os.close(); } catch (Exception ignore) {} }
                                }
                            }
                        }).start();
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.wallet_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.recycler = view.findViewById(R.id.recycler_wallets);

        String languageKey = getArguments().getString("languageKey");
        String walletPassword = getArguments().getString("walletPassword");

        keyViewModel = new KeyViewModel();

        jsonViewModel = new JsonViewModel(getContext(), languageKey);

        ImageButton backArrowImageButton = (ImageButton) getView().findViewById(R.id.imageButton_wallets_back_arrow);
        TextView walletTitleTextView = (TextView) getView().findViewById(R.id.textview_wallets_langValues_wallets);

        TextView walletHeaderAddressTextView = (TextView) getView().findViewById(R.id.textView_wallet_header_langValues_address);
        TextView walletHeaderScanTextView = (TextView) getView().findViewById(R.id.textView_wallet_header_langValues_scan);
        TextView walletHeaderSeedTextView = (TextView) getView().findViewById(R.id.textView_wallet_header_langValues_reveal_seed);

        TextView walletCreateOrRestoreTextView = (TextView) getView().findViewById(R.id.textview_wallet_langValues_create_or_restore);

        ProgressBar progressBar = (ProgressBar) getView().findViewById(R.id.progress_wallets);

        walletTitleTextView.setText(jsonViewModel.getWalletsByLangValues());

        walletHeaderAddressTextView.setText(jsonViewModel.getAddressByLangValues());
        walletHeaderScanTextView.setText(jsonViewModel.getDpscanByLangValues());
        walletHeaderSeedTextView.setText(jsonViewModel.getRevealSeedByLangValues());

        walletCreateOrRestoreTextView.setText(jsonViewModel.getCreateRestoreWalletByLangValues());

        progressBar.setVisibility(View.VISIBLE);

        this.recycler.removeAllViewsInLayout();

        int mNoOfColumns = Utility.calculateNoOfColumns(getContext(), R.id.recycler_wallets);

        GridAutoFitLayoutManager mLayoutManager = new GridAutoFitLayoutManager(getContext(),
                mNoOfColumns, 1, false);

        this.recycler.setLayoutManager(mLayoutManager);

        this.walletAdapter = new WalletAdapter(getContext(), PrefConnect.WALLET_INDEX_TO_ADDRESS_MAP);

        this.recycler.setAdapter(walletAdapter);

        this.walletAdapter.notifyDataSetChanged();

        progressBar.setVisibility(View.GONE);

        backArrowImageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mWalletsListener.onWalletsCompleteByBackArrow();
            }
        });

        walletCreateOrRestoreTextView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (walletPassword==null || walletPassword.isEmpty()) {
                     unlockDialogFragment(progressBar, 0, null, languageKey);
                } else {
                    VerifyPassword(null, progressBar, walletPassword, 0, null, languageKey);
                }
            }
        });

        walletAdapter.SetOnWalletItemClickListener(new WalletAdapter.OnWalletItemClickListener() {
            @Override
            public void onWalletItemClick(View view, int position) {
                   mWalletsListener.onWalletsCompleteBySwitchAddress(String.valueOf(position));
            }
            @Override
            public void onWalletRevealClick(View view, int position) {
                String indexKey =   String.valueOf(position);
                if(PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP != null) {
                    for (Map.Entry<String, String> entry : PrefConnect.WALLET_INDEX_TO_ADDRESS_MAP.entrySet()) {
                        if (Objects.equals(indexKey, entry.getKey())) {
                            PrefConnect.writeString(getContext(), PrefConnect.WALLET_CURRENT_ADDRESS_INDEX_KEY, indexKey);
                            String walletAddress = entry.getValue();
                            if (walletPassword==null || walletPassword.isEmpty()) {
                                unlockDialogFragment(progressBar, 1, walletAddress, languageKey);
                            } else {
                                VerifyPassword(null, progressBar, walletPassword, 1, walletAddress, languageKey);
                            }
                            break;
                        }
                    }
                }
            }
            @Override
            public void onWalletExportClick(View view, int position) {
                String indexKey = String.valueOf(position);
                String walletAddress = PrefConnect.WALLET_INDEX_TO_ADDRESS_MAP.get(indexKey);
                if (walletAddress == null) return;
                if (walletPassword == null || walletPassword.isEmpty()) {
                    unlockDialogFragment(progressBar, 2, walletAddress, languageKey);
                } else {
                    VerifyPassword(null, progressBar, walletPassword, 2, walletAddress, languageKey);
                }
            }
        });

    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop(){
        super.onStop();
    }

    public static interface OnWalletsCompleteListener {
        //public abstract void onWalletsComplete(int status, String password, String indexKey);
        public abstract void onWalletsCompleteByBackArrow();
        public abstract void onWalletsCompleteByCreateOrRestore(String walletPassword);
        public abstract void onWalletsCompleteBySwitchAddress(String walletAddress);
        public abstract void onWalletsCompleteByReveal(String walletAddress, String walletPassword);
    }

    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.mWalletsListener = (OnWalletsCompleteListener)context;
        }
        catch (final ClassCastException e) {
            throw new ClassCastException(context.toString() + " ");
        }
    }

    private void unlockDialogFragment(ProgressBar progressBar, int listenerStatus, String walletAddress, String languageKey) {
        try {
            //Alert unlock dialog
            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setTitle((CharSequence) "").setView((int)
                            R.layout.unlock_dialog_fragment).create();
            dialog.dismiss();
            dialog.setCancelable(false);
            dialog.show();

            TextView unlockWalletTextView = (TextView) dialog.findViewById(R.id.textView_unlock_langValues_unlock_wallet);
            TextView unlockPasswordTextView = (TextView) dialog.findViewById(R.id.textView_unlock_langValues_enter_wallet_password);

            EditText passwordEditText = (EditText) dialog.findViewById(R.id.editText_unlock_langValues_enter_a_password);
            Button unlockButton = (Button) dialog.findViewById(R.id.button_unlock_langValues_unlock);
            Button closeButton = (Button) dialog.findViewById(R.id.button_unlock_langValues_close);

            unlockWalletTextView.setText(jsonViewModel.getUnlockWalletByLangValues());
            unlockPasswordTextView.setText(jsonViewModel.getEnterQuantumWalletPasswordByLangValues());
            passwordEditText.setHint(jsonViewModel.getEnterApasswordByLangValues());
            unlockButton.setText(jsonViewModel.getUnlockByLangValues());
            closeButton.setText(jsonViewModel.getCloseByLangValues());
            unlockButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String walletPassword = passwordEditText.getText().toString();
                    if (walletPassword==null || walletPassword.isEmpty()) {
                        messageDialogFragment(languageKey, jsonViewModel.getEnterApasswordByLangValues());
                    } else {
                        VerifyPassword(dialog, progressBar, walletPassword, listenerStatus, walletAddress, languageKey);
                    }
                }
            });
            closeButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });

        } catch (Exception e) {
            GlobalMethods.ExceptionError(getContext(), TAG, e);
        }
    }

    private void messageDialogFragment(String languageKey, String message) {
        try {
            Bundle bundleRoute = new Bundle();
            bundleRoute.putString("languageKey",languageKey);
            bundleRoute.putString("message", message);

            FragmentManager fragmentManager  = getFragmentManager();
            MessageInformationDialogFragment messageDialogFragment = MessageInformationDialogFragment.newInstance();
            messageDialogFragment.setCancelable(false);
            messageDialogFragment.setArguments(bundleRoute);
            messageDialogFragment.show(fragmentManager, "");
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getContext(), TAG, e);
        }
    }

    private void VerifyPassword(AlertDialog dialog, ProgressBar progressBar, String walletPassword, int listenerStatus, String walletAddress, String languageKey)  {
        try {
            SecureStorage secureStorage = KeyViewModel.getSecureStorage();
            if (secureStorage.verifyPassword(getContext(), walletPassword)) {
               if (dialog != null) dialog.dismiss();
               switch (listenerStatus) {
                   case 0:
                       mWalletsListener.onWalletsCompleteByCreateOrRestore(walletPassword);
                       break;
                   case 1:
                       mWalletsListener.onWalletsCompleteByReveal(walletAddress, walletPassword);
                       break;
                   case 2:
                       startExportFlow(walletAddress, walletPassword);
                       break;
               }
            } else {
                messageDialogFragment(languageKey, jsonViewModel.getWalletPasswordMismatchByErrors());
            }
        } catch (Exception e) {
            messageDialogFragment(languageKey, jsonViewModel.getWalletOpenErrorByErrors());
        }
    }

    private void startExportFlow(final String walletAddress, final String walletPassword) {
        BackupPasswordDialog.show(getContext(), jsonViewModel, walletPassword,
                new BackupPasswordDialog.OnBackupPasswordListener() {
                    @Override
                    public void onPasswordSelected(final String backupPassword) {
                        encryptAndPickExportLocation(walletAddress, walletPassword, backupPassword);
                    }
                    @Override
                    public void onCanceled() { }
                });
    }

    private void encryptAndPickExportLocation(final String walletAddress,
                                              final String unlockPassword,
                                              final String backupPassword) {
        final ProgressBar progressBar = (ProgressBar) getView().findViewById(R.id.progress_wallets);
        progressBar.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SecureStorage secureStorage = KeyViewModel.getSecureStorage();
                    String indexKey = PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.get(walletAddress);
                    if (indexKey == null) throw new IllegalStateException("wallet index missing");
                    String walletJsonStr = secureStorage.loadWallet(getContext(),
                            Integer.parseInt(indexKey));
                    JSONObject walletJson = new JSONObject(walletJsonStr);
                    final CloudBackupManager.EncryptedResult enc =
                            CloudBackupManager.encryptWallet(walletJson, backupPassword);
                    final String filename = CloudBackupManager.buildFilename(enc.address);
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            launchExportSavePicker(enc.json, enc.address, filename);
                        }
                    });
                } catch (final Exception e) {
                    GlobalMethods.ExceptionError(getContext(), TAG, e);
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            String tmpl = jsonViewModel.getBackupFailedByLangValues();
                            String msg = tmpl != null
                                    ? tmpl.replace("[ERROR]", e.getMessage() == null ? "" : e.getMessage())
                                    : ("Export failed: " + e.getMessage());
                            Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void launchExportSavePicker(String encryptedJson, String address, String filename) {
        pendingExportEncryptedJson = encryptedJson;
        pendingExportWalletAddress = address;
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(CloudBackupManager.BACKUP_MIME_TYPE);
            intent.putExtra(Intent.EXTRA_TITLE, filename);

            String folderUriStr = PrefConnect.readString(getContext(),
                    PrefConnect.CLOUD_BACKUP_FOLDER_URI_KEY, "");
            if (folderUriStr != null && !folderUriStr.isEmpty()) {
                try {
                    intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                            Uri.parse(folderUriStr));
                } catch (Exception ignore) { }
            }
            exportCreateDocumentLauncher.launch(intent);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getContext(), TAG, e);
            pendingExportEncryptedJson = null;
            pendingExportWalletAddress = null;
        }
    }

    /*
    private void CheckThread(ProgressBar progressBar, String walletAddress, String walletPassword) {
        progressBar.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            if (GlobalMethods.seedLoaded) {
                                try {
                                    progressBar.setVisibility(View.GONE);
                                } catch (Exception e) {
                                    progressBar.setVisibility(View.GONE);
                                    GlobalMethods.ExceptionError(getContext(), TAG, e);
                                }
                            }
                        }
                    });
                    try {
                        if(progressBar.getVisibility()==View.GONE){
                            return;
                        }
                        if(ThreadStop) {
                            return;
                        }
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        progressBar.setVisibility(View.GONE);
                        GlobalMethods.ExceptionError(getContext(), TAG, e);
                    }
                }
            }
        }).start();
    }
    */
}
