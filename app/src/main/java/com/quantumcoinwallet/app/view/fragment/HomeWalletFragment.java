package com.quantumcoinwallet.app.view.fragment;

import static android.content.Context.CLIPBOARD_SERVICE;
import static androidx.core.content.ContextCompat.getSystemService;

import androidx.appcompat.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.api.read.ApiException;
import com.quantumcoinwallet.app.api.read.model.BalanceResponse;
import com.quantumcoinwallet.app.asynctask.read.AccountBalanceRestTask;
import com.quantumcoinwallet.app.entity.ServiceException;
import com.quantumcoinwallet.app.keystorage.SecureStorage;
import com.quantumcoinwallet.app.utils.CoinUtils;
import com.quantumcoinwallet.app.utils.GlobalMethods;
import com.quantumcoinwallet.app.utils.PrefConnect;
import com.quantumcoinwallet.app.viewmodel.JsonViewModel;
import com.quantumcoinwallet.app.viewmodel.KeyViewModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.quantumcoinwallet.app.view.adapter.SeedWordAutoCompleteAdapter;

public class HomeWalletFragment extends Fragment {

    private static final String TAG = "HomeWalletFragment";

    private int homeCreateRestoreWalletRadio = -1;
    private int selectedKeyType = 3;
    private int selectedWordCount = 32;

    private JsonViewModel jsonViewModel;

    private KeyViewModel keyViewModel;
    private String[] tempSeedWords;
    private String tempAddress;
    private String tempPrivateKeyBase64;
    private String tempPublicKeyBase64;
    private SeedWordAutoCompleteAdapter seedWordAutoCompleteAdapter;
   // private TextView[] homeSeedWordsViewTextViews;
    private AutoCompleteTextView[] homeSeedWordsViewAutoCompleteTextViews;
    private boolean autoCompleteIndexStatus = false;
    private int autoCompleteCurrentIndex = 0;
    private  String  walletPassword = null;
    private String walletIndexKey = "0";
    private OnHomeWalletCompleteListener mHomeWalletListener;
    private ActivityResultLauncher<Intent> cloudFolderPickerLauncher;
    private ActivityResultLauncher<Intent> fileBackupLauncher;
    private ActivityResultLauncher<Intent> restoreFilePickerLauncher;
    private ActivityResultLauncher<Intent> restoreCloudFolderPickerLauncher;
    private Runnable pendingCloudContinuation;
    private JSONObject pendingBackupWalletJson;
    private String pendingBackupEncryptedJson;
    private String pendingBackupAddress;
    private LinearLayout backupOptionsLinearLayout;
    private TextView backupCloudStatusTextView;
    private TextView backupFileStatusTextView;

    // Phone-backup radio step (shown in place of a yes/no dialog). Held as fields
    // because showBackupPromptIfNeeded() lives outside onViewCreated and needs
    // to toggle these views from multiple call sites.
    private LinearLayout homePhoneBackupLinearLayout;
    private TextView homePhoneBackupTitleTextView;
    private TextView homePhoneBackupDescriptionTextView;
    private RadioButton homePhoneBackupYesRadioButton;
    private RadioButton homePhoneBackupNoRadioButton;
    private Button homePhoneBackupNextButton;
    private Runnable pendingPhoneBackupOnComplete;

    // Confirm-Wallet step (held as fields because showConfirmWalletScreen()
    // and the back-arrow handler both need to toggle visibility).
    private LinearLayout homeConfirmWalletLinearLayout;
    private LinearLayout homeSeedWordsEditLinearLayoutRef;
    private TextView homeConfirmWalletAddressValueTextView;
    private TextView homeConfirmWalletBalanceValueTextView;
    private ProgressBar homeConfirmWalletBalanceProgressBar;

    public static HomeWalletFragment newInstance() {
        HomeWalletFragment fragment = new HomeWalletFragment();
        return fragment;
    }

    public HomeWalletFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initFileBackupLauncher();
        restoreFilePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        markActivityUnlocked();
                        if (result == null || result.getResultCode() != android.app.Activity.RESULT_OK
                                || result.getData() == null || result.getData().getData() == null) {
                            return;
                        }
                        handleRestoreFromUri(result.getData().getData());
                    }
                });
        restoreCloudFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        markActivityUnlocked();
                        if (result == null || result.getResultCode() != android.app.Activity.RESULT_OK
                                || result.getData() == null || result.getData().getData() == null) {
                            return;
                        }
                        Uri treeUri = result.getData().getData();
                        try {
                            int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                            getContext().getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                        } catch (Exception ignore) { }
                        PrefConnect.writeString(getContext(),
                                PrefConnect.CLOUD_BACKUP_FOLDER_URI_KEY, treeUri.toString());
                        showRestoreCloudFilePicker(treeUri);
                    }
                });
        cloudFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        markActivityUnlocked();
                        Runnable continuation = pendingCloudContinuation;
                        pendingCloudContinuation = null;
                        try {
                            if (result != null && result.getResultCode() == android.app.Activity.RESULT_OK
                                    && result.getData() != null && result.getData().getData() != null) {
                                Uri treeUri = result.getData().getData();
                                int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                                try {
                                    getContext().getContentResolver()
                                            .takePersistableUriPermission(treeUri, takeFlags);
                                } catch (Exception ignore) { }
                                PrefConnect.writeString(getContext(),
                                        PrefConnect.CLOUD_BACKUP_FOLDER_URI_KEY, treeUri.toString());
                            }
                        } finally {
                            if (continuation != null) continuation.run();
                        }
                    }
                });
    }

    private void initFileBackupLauncher() {
        if (fileBackupLauncher != null) return;
        fileBackupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        markActivityUnlocked();
                        final String encrypted = pendingBackupEncryptedJson;
                        pendingBackupEncryptedJson = null;
                        pendingBackupAddress = null;
                        if (result == null || result.getResultCode() != android.app.Activity.RESULT_OK
                                || result.getData() == null || result.getData().getData() == null
                                || encrypted == null) {
                            return;
                        }
                        final Uri uri = result.getData().getData();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                java.io.OutputStream os = null;
                                try {
                                    os = getContext().getContentResolver().openOutputStream(uri);
                                    if (os == null) throw new IllegalStateException("openOutputStream returned null");
                                    os.write(encrypted.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                    os.flush();
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            flagFileBackupSaved();
                                        }
                                    });
                                } catch (final Exception e) {
                                    android.util.Log.e(TAG, "backup export failed", e);
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            String tmpl = jsonViewModel.getBackupFailedByLangValues();
                                            String msg = tmpl != null
                                                    ? tmpl.replace("[ERROR]", e.getMessage() == null ? "" : e.getMessage())
                                                    : ("Backup failed: " + e.getMessage());
                                            GlobalMethods.ShowErrorDialog(getContext(),
                                                    jsonViewModel.getErrorTitleByLangValues(), msg);
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

    private void markActivityUnlocked() {
        try {
            android.app.Activity act = getActivity();
            if (act instanceof com.quantumcoinwallet.app.view.activities.HomeActivity) {
                ((com.quantumcoinwallet.app.view.activities.HomeActivity) act).markUnlockedNow();
            }
        } catch (Exception ignore) { }
    }

    private void setSuppressNextResumeLock(boolean suppress) {
        try {
            android.app.Activity act = getActivity();
            if (act instanceof com.quantumcoinwallet.app.view.activities.HomeActivity) {
                ((com.quantumcoinwallet.app.view.activities.HomeActivity) act).setSuppressNextResumeLock(suppress);
            }
        } catch (Exception ignore) { }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.home_wallet_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        walletPassword = null;
        String languageKey = getArguments().getString("languageKey");

        tempSeedWords = null;
        jsonViewModel = new JsonViewModel(getContext(), languageKey);
        keyViewModel = new KeyViewModel();

        LinearLayout homeSetWalletTopLinearLayout = (LinearLayout) getView().findViewById(R.id.top_linear_layout_home_wallet_id);
        ImageButton homeWalletBackArrowImageButton = (ImageButton) getView().findViewById(R.id.imageButton_home_wallet_back_arrow);

        LinearLayout homeSetWalletLinearLayout = (LinearLayout) getView().findViewById(R.id.linear_layout_home_set_wallet);
        TextView homeSetWalletTitleTextView = (TextView) getView().findViewById(R.id.textView_home_set_wallet_title);
        TextView homeSetWalletDescriptionTextView = (TextView) getView().findViewById(R.id.textView_home_set_wallet_description);
        TextView homeSetWalletPasswordTitleTextView = (TextView) getView().findViewById(R.id.textView_home_set_wallet_password_title);
        EditText homeSetWalletPasswordEditText = (EditText) getView().findViewById(R.id.editText_home_set_wallet_password);
        TextView homeSetWalletRetypePasswordTitleTextView = (TextView) getView().findViewById(R.id.textView_home_set_wallet_retype_password_title);
        EditText homeSetWalletRetypePasswordEditText = (EditText) getView().findViewById(R.id.editText_home_set_wallet_retype_password);
        Button homeSetWalletNextButton = (Button) getView().findViewById(R.id.button_home_set_wallet_next);

        ////homeSetWalletPasswordEditText.setText("Test123$$Test123$$");
        ////homeSetWalletRetypePasswordEditText.setText("Test123$$Test123$$");

        LinearLayout homeCreateRestoreWalletLinearLayout = (LinearLayout) getView().findViewById(R.id.linear_layout_home_create_restore_wallet);
        TextView homeCreateRestoreWalletTitleTextView = (TextView) getView().findViewById(R.id.textView_home_create_restore_wallet_title);
        TextView homeCreateRestoreWalletDescriptionTextView = (TextView) getView().findViewById(R.id.textView_home_create_restore_wallet_description);

        RadioGroup homeCreateRestoreWalletRadioGroup = (RadioGroup) getView().findViewById(R.id.radioGroup_home_create_restore_wallet);
        RadioButton homeCreateRestoreWalletRadioButton_0 = (RadioButton) getView().findViewById(R.id.radioButton_home_create_restore_wallet_0);
        RadioButton homeCreateRestoreWalletRadioButton_1 = (RadioButton) getView().findViewById(R.id.radioButton_home_create_restore_wallet_1);
        RadioButton homeCreateRestoreWalletRadioButton_2 = (RadioButton) getView().findViewById(R.id.radioButton_home_create_restore_wallet_2);
        RadioButton homeCreateRestoreWalletRadioButton_3 = (RadioButton) getView().findViewById(R.id.radioButton_home_create_restore_wallet_3);
        Button homeCreateRestoreWalletNextButton = (Button) getView().findViewById(R.id.button_home_create_restore_wallet_next);

        LinearLayout homeWalletTypeLinearLayout = (LinearLayout) getView().findViewById(R.id.linear_layout_home_wallet_type);
        TextView homeWalletTypeTitleTextView = (TextView) getView().findViewById(R.id.textView_home_wallet_type_title);
        TextView homeWalletTypeDescriptionTextView = (TextView) getView().findViewById(R.id.textView_home_wallet_type_description);
        RadioGroup homeWalletTypeRadioGroup = (RadioGroup) getView().findViewById(R.id.radioGroup_home_wallet_type);
        RadioButton homeWalletTypeDefaultRadioButton = (RadioButton) getView().findViewById(R.id.radioButton_home_wallet_type_default);
        RadioButton homeWalletTypeAdvancedRadioButton = (RadioButton) getView().findViewById(R.id.radioButton_home_wallet_type_advanced);
        Button homeWalletTypeNextButton = (Button) getView().findViewById(R.id.button_home_wallet_type_next);

        homePhoneBackupLinearLayout = (LinearLayout) getView().findViewById(R.id.linear_layout_home_phone_backup);
        homePhoneBackupTitleTextView = (TextView) getView().findViewById(R.id.textView_home_phone_backup_title);
        homePhoneBackupDescriptionTextView = (TextView) getView().findViewById(R.id.textView_home_phone_backup_description);
        homePhoneBackupYesRadioButton = (RadioButton) getView().findViewById(R.id.radioButton_home_phone_backup_yes);
        homePhoneBackupNoRadioButton = (RadioButton) getView().findViewById(R.id.radioButton_home_phone_backup_no);
        homePhoneBackupNextButton = (Button) getView().findViewById(R.id.button_home_phone_backup_next);

        LinearLayout homeSeedWordLengthLinearLayout = (LinearLayout) getView().findViewById(R.id.linear_layout_home_seed_word_length);
        TextView homeSeedWordLengthTitleTextView = (TextView) getView().findViewById(R.id.textView_home_seed_word_length_title);
        TextView homeSeedWordLengthDescriptionTextView = (TextView) getView().findViewById(R.id.textView_home_seed_word_length_description);
        RadioGroup homeSeedWordLengthRadioGroup = (RadioGroup) getView().findViewById(R.id.radioGroup_home_seed_word_length);
        RadioButton homeSeedWordLength32RadioButton = (RadioButton) getView().findViewById(R.id.radioButton_home_seed_word_length_32);
        RadioButton homeSeedWordLength36RadioButton = (RadioButton) getView().findViewById(R.id.radioButton_home_seed_word_length_36);
        RadioButton homeSeedWordLength48RadioButton = (RadioButton) getView().findViewById(R.id.radioButton_home_seed_word_length_48);
        Button homeSeedWordLengthNextButton = (Button) getView().findViewById(R.id.button_home_seed_word_length_next);

        LinearLayout homeSeedWordsLinearLayout = (LinearLayout) getView().findViewById(R.id.linear_layout_home_seed_words);
        TextView homeSeedWordsTitleTextView = (TextView) getView().findViewById(R.id.textView_home_seed_words_title);
        TextView homeSeedWords1 = (TextView) getView().findViewById(R.id.textView_home_seed_words_1);
        TextView homeSeedWords2 = (TextView) getView().findViewById(R.id.textView_home_seed_words_2);
        TextView homeSeedWords3 = (TextView) getView().findViewById(R.id.textView_home_seed_words_3);
        TextView homeSeedWords4 = (TextView) getView().findViewById(R.id.textView_home_seed_words_4);
        TextView homeSeedWordsShow = (TextView) getView().findViewById(R.id.textView_home_seed_words_show);

        LinearLayout homeSeedWordsViewLinearLayout = (LinearLayout) getView().findViewById(R.id.linear_layout_home_seed_words_view);
        TextView homeSeedWordsViewTitleTextView = (TextView) getView().findViewById(R.id.textView_home_seed_words_view_title);
        TextView[] homeSeedWordsViewCaptionTextViews = HomeSeedWordsViewCaptionTextViews();
        TextView[] homeSeedWordsViewTextViews = HomeSeedWordsViewTextViews();
        ImageButton homeSeedWordsViewCopyClipboardImageButton = (ImageButton) getView().findViewById(R.id.imageButton_home_seed_words_view_copy_clipboard);
        TextView homeSeedWordsViewCopyLink = (TextView) getView().findViewById(R.id.textView_home_seed_words_view_copy_link);
        TextView homeSeedWordsViewCopied = (TextView) getView().findViewById(R.id.textView_home_seed_words_view_copied);
        Button homeSeedWordsViewNextButton = (Button) getView().findViewById(R.id.button_home_seed_words_view_next);

        LinearLayout homeSeedWordsEditLinearLayout = (LinearLayout) getView().findViewById(R.id.linear_layout_home_seed_words_edit);
        homeSeedWordsEditLinearLayoutRef = homeSeedWordsEditLinearLayout;
        TextView homeSeedWordsEditTitleTextView = (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_title);
        TextView homeSeedWordsEditSkip = (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_skip);
        TextView[] homeSeedWordsEditCaptionTextViews = HomeSeedWordsEditCaptionTextViews();
        homeSeedWordsViewAutoCompleteTextViews = HomeSeedWordsViewAutoCompleteTextView();

        homeConfirmWalletLinearLayout = (LinearLayout) getView().findViewById(R.id.linear_layout_home_confirm_wallet);
        homeConfirmWalletAddressValueTextView = (TextView) getView().findViewById(R.id.textView_home_confirm_wallet_address_value);
        homeConfirmWalletBalanceValueTextView = (TextView) getView().findViewById(R.id.textView_home_confirm_wallet_balance_value);
        homeConfirmWalletBalanceProgressBar = (ProgressBar) getView().findViewById(R.id.progress_home_confirm_wallet_balance);

        int index=0;
        for (AutoCompleteTextView homeSeedWordsViewAutoCompleteTextView : homeSeedWordsViewAutoCompleteTextViews) {
            homeSeedWordsViewAutoCompleteTextView.addTextChangedListener(GetTextWatcher(homeSeedWordsViewAutoCompleteTextView, index));
            index = index + 1;
            homeSeedWordsViewAutoCompleteTextView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void  onFocusChange(View view, boolean hasFocus) {
                    if (hasFocus) {
                        if(autoCompleteIndexStatus == true){
                            if(homeSeedWordsViewAutoCompleteTextViews[autoCompleteCurrentIndex].getText().length() < 3){
                                homeSeedWordsViewAutoCompleteTextViews[autoCompleteCurrentIndex].requestFocus();
                            }
                        }
                        autoCompleteIndexStatus = false;
                    } else {
                        if(homeSeedWordsViewAutoCompleteTextView.length()>2) {
                            if(tempSeedWords != null) {
                                if (!homeSeedWordsViewAutoCompleteTextView.getText().toString().equalsIgnoreCase(homeSeedWordsViewTextViews[autoCompleteCurrentIndex].getText().toString())) {
                                    homeSeedWordsViewAutoCompleteTextView.setText("");
                                    autoCompleteIndexStatus = true;
                                }
                            }
                        }
                    }
                }
            });
        }

        Button homeSeedWordsAutoCompleteNextButton = (Button) getView().findViewById(R.id.button_home_seed_words_autoComplete_next);

        ProgressBar progressBar = (ProgressBar) getView().findViewById(R.id.progress_loader_home_wallet);

        final boolean firstTimeSetup = !KeyViewModel.getSecureStorage().isInitialized(getContext());

        if (firstTimeSetup) {
            homeSetWalletLinearLayout.setVisibility(View.VISIBLE);
            SetWalletView(homeSetWalletTitleTextView, homeSetWalletDescriptionTextView, homeSetWalletPasswordTitleTextView,
                    homeSetWalletPasswordEditText, homeSetWalletRetypePasswordTitleTextView, homeSetWalletRetypePasswordEditText, homeSetWalletNextButton);
        } else {
            homeSetWalletLinearLayout.setVisibility(View.GONE);
            homeSetWalletTopLinearLayout.setVisibility(View.VISIBLE);
            homeCreateRestoreWalletLinearLayout.setVisibility(View.VISIBLE);
            CreateRestoreWalletView(homeCreateRestoreWalletTitleTextView, homeCreateRestoreWalletDescriptionTextView, homeCreateRestoreWalletRadioButton_0,
                    homeCreateRestoreWalletRadioButton_1, homeCreateRestoreWalletRadioButton_2, homeCreateRestoreWalletRadioButton_3,
                    homeCreateRestoreWalletNextButton);
        }

        homeSetWalletNextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String message = jsonViewModel.getPasswordSpecByErrors();
                if (homeSetWalletPasswordEditText.getText().length() > GlobalMethods.MINIMUM_PASSWORD_LENGTH) {
                    if (homeSetWalletPasswordEditText.getText().toString().equals(homeSetWalletRetypePasswordEditText.getText().toString())) {
                        homeSetWalletLinearLayout.setVisibility(View.GONE);
                        homeSetWalletTopLinearLayout.setVisibility(View.VISIBLE);
                        homeCreateRestoreWalletLinearLayout.setVisibility(View.VISIBLE);
                        CreateRestoreWalletView(homeCreateRestoreWalletTitleTextView, homeCreateRestoreWalletDescriptionTextView, homeCreateRestoreWalletRadioButton_0,
                                homeCreateRestoreWalletRadioButton_1, homeCreateRestoreWalletRadioButton_2, homeCreateRestoreWalletRadioButton_3,
                                homeCreateRestoreWalletNextButton);
                        return;
                    }
                    message = jsonViewModel.getRetypePasswordMismatchByErrors();
                }
                Bundle bundleRoute = new Bundle();
                bundleRoute.putString("languageKey",languageKey);
                bundleRoute.putString("message", message);

                FragmentManager fragmentManager = getFragmentManager();
                MessageInformationDialogFragment messageDialogFragment = MessageInformationDialogFragment.newInstance();
                messageDialogFragment.setCancelable(false);
                messageDialogFragment.setArguments(bundleRoute);
                messageDialogFragment.show(fragmentManager, "");
            }
        });

        homeWalletBackArrowImageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                    View backupOptions = getView() != null
                            ? getView().findViewById(R.id.linear_layout_home_backup_options) : null;
                    if (backupOptions != null && backupOptions.getVisibility() == View.VISIBLE) {
                        finishBackupAndNavigateToHome();
                        return;
                    }
                    if (homeCreateRestoreWalletLinearLayout.getVisibility()==View.VISIBLE) {
                        if (firstTimeSetup) {
                            homeCreateRestoreWalletLinearLayout.setVisibility(View.GONE);
                            homeSetWalletTopLinearLayout.setVisibility(View.GONE);
                            homeSetWalletLinearLayout.setVisibility(View.VISIBLE);
                        } else {
                            mHomeWalletListener.onHomeWalletCompleteByWallets();
                        }
                    } else if (homePhoneBackupLinearLayout != null
                            && homePhoneBackupLinearLayout.getVisibility() == View.VISIBLE) {
                        // Cancel the pending continuation; the user will need to tap
                        // Next on create-or-restore again to re-enter this step.
                        pendingPhoneBackupOnComplete = null;
                        homePhoneBackupLinearLayout.setVisibility(View.GONE);
                        homeCreateRestoreWalletLinearLayout.setVisibility(View.VISIBLE);
                    } else if (homeWalletTypeLinearLayout.getVisibility()==View.VISIBLE) {
                        homeWalletTypeLinearLayout.setVisibility(View.GONE);
                        homeCreateRestoreWalletLinearLayout.setVisibility(View.VISIBLE);
                    } else if (homeSeedWordLengthLinearLayout.getVisibility()==View.VISIBLE) {
                        homeSeedWordLengthLinearLayout.setVisibility(View.GONE);
                        homeCreateRestoreWalletLinearLayout.setVisibility(View.VISIBLE);
                    } else if (homeSeedWordsLinearLayout.getVisibility()==View.VISIBLE) {
                        homeSeedWordsLinearLayout.setVisibility(View.GONE);
                        homeWalletTypeLinearLayout.setVisibility(View.VISIBLE);
                    } else if (homeSeedWordsViewLinearLayout.getVisibility()==View.VISIBLE) {
                        homeSeedWordsViewLinearLayout.setVisibility(View.GONE);
                        homeSeedWordsLinearLayout.setVisibility(View.VISIBLE);
                    } else if (homeConfirmWalletLinearLayout != null
                            && homeConfirmWalletLinearLayout.getVisibility() == View.VISIBLE) {
                        homeConfirmWalletLinearLayout.setVisibility(View.GONE);
                        homeSeedWordsEditLinearLayout.setVisibility(View.VISIBLE);
                    } else if (homeSeedWordsEditLinearLayout.getVisibility()==View.VISIBLE) {
                        if (homeCreateRestoreWalletRadioButton_0.isChecked()==true){
                            homeSeedWordsEditLinearLayout.setVisibility(View.GONE);
                            homeSeedWordsViewLinearLayout.setVisibility(View.VISIBLE);
                        }
                        if (homeCreateRestoreWalletRadioButton_1.isChecked()==true){
                            homeSeedWordsEditLinearLayout.setVisibility(View.GONE);
                            homeSeedWordLengthLinearLayout.setVisibility(View.VISIBLE);
                        }
                    }
            }
        });

        homeCreateRestoreWalletRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                RadioButton radioButton = (RadioButton) group.findViewById(checkedId);
                homeCreateRestoreWalletRadio = (int) radioButton.getTag();
            }
        });

        homeCreateRestoreWalletNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (homeCreateRestoreWalletRadioButton_0.isChecked() == true) {
                    tempSeedWords = null;
                    Runnable proceed = new Runnable() {
                        @Override
                        public void run() {
                            homeWalletTypeLinearLayout.setVisibility(View.VISIBLE);
                            WalletTypeView(homeWalletTypeTitleTextView, homeWalletTypeDescriptionTextView,
                                    homeWalletTypeDefaultRadioButton, homeWalletTypeAdvancedRadioButton, homeWalletTypeNextButton);
                        }
                    };
                    homeCreateRestoreWalletLinearLayout.setVisibility(View.GONE);
                    showBackupPromptIfNeeded(proceed);
                } else if (homeCreateRestoreWalletRadioButton_1.isChecked() == true) {
                    tempSeedWords = null;
                    Runnable proceed = new Runnable() {
                        @Override
                        public void run() {
                            homeSeedWordLengthLinearLayout.setVisibility(View.VISIBLE);
                            SeedWordLengthView(homeSeedWordLengthTitleTextView, homeSeedWordLengthDescriptionTextView,
                                    homeSeedWordLength32RadioButton, homeSeedWordLength36RadioButton, homeSeedWordLength48RadioButton,
                                    homeSeedWordLengthNextButton);
                        }
                    };
                    homeCreateRestoreWalletLinearLayout.setVisibility(View.GONE);
                    showBackupPromptIfNeeded(proceed);
                } else if (homeCreateRestoreWalletRadioButton_2.isChecked() == true) {
                    startRestoreFromFileFlow();
                } else if (homeCreateRestoreWalletRadioButton_3.isChecked() == true) {
                    startRestoreFromCloudFlow();
                } else {
                    String message = jsonViewModel.getSelectOptionByErrors();
                    Bundle bundleRoute = new Bundle();
                    bundleRoute.putString("languageKey",languageKey);
                    bundleRoute.putString("message", message);

                    FragmentManager fragmentManager = getFragmentManager();
                    MessageInformationDialogFragment messageDialogFragment = MessageInformationDialogFragment.newInstance();
                    messageDialogFragment.setCancelable(false);
                    messageDialogFragment.setArguments(bundleRoute);
                    messageDialogFragment.show(fragmentManager, "");
                }
            }
        });

        homePhoneBackupNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean yes = homePhoneBackupYesRadioButton.isChecked();
                boolean no = homePhoneBackupNoRadioButton.isChecked();
                if (!yes && !no) {
                    String message = jsonViewModel.getSelectOptionByErrors();
                    Bundle bundleRoute = new Bundle();
                    bundleRoute.putString("languageKey", languageKey);
                    bundleRoute.putString("message", message);
                    FragmentManager fragmentManager = getFragmentManager();
                    MessageInformationDialogFragment messageDialogFragment = MessageInformationDialogFragment.newInstance();
                    messageDialogFragment.setCancelable(false);
                    messageDialogFragment.setArguments(bundleRoute);
                    messageDialogFragment.show(fragmentManager, "");
                    return;
                }
                PrefConnect.writeBoolean(getContext(), PrefConnect.BACKUP_ENABLED_KEY, yes);
                homePhoneBackupLinearLayout.setVisibility(View.GONE);
                Runnable onComplete = pendingPhoneBackupOnComplete;
                pendingPhoneBackupOnComplete = null;
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });

        homeWalletTypeNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (homeWalletTypeDefaultRadioButton.isChecked()) {
                    selectedKeyType = 3;
                    selectedWordCount = 32;
                } else if (homeWalletTypeAdvancedRadioButton.isChecked()) {
                    selectedKeyType = 5;
                    selectedWordCount = 36;
                } else {
                    String message = jsonViewModel.getSelectOptionByErrors();
                    Bundle bundleRoute = new Bundle();
                    bundleRoute.putString("languageKey",languageKey);
                    bundleRoute.putString("message", message);
                    FragmentManager fragmentManager = getFragmentManager();
                    MessageInformationDialogFragment messageDialogFragment = MessageInformationDialogFragment.newInstance();
                    messageDialogFragment.setCancelable(false);
                    messageDialogFragment.setArguments(bundleRoute);
                    messageDialogFragment.show(fragmentManager, "");
                    return;
                }
                homeWalletTypeLinearLayout.setVisibility(View.GONE);
                homeSeedWordsLinearLayout.setVisibility(View.VISIBLE);
                SeedWordsView(homeSeedWordsTitleTextView, homeSeedWords1, homeSeedWords2, homeSeedWords3, homeSeedWords4, homeSeedWordsShow);
            }
        });

        homeSeedWordLengthNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (homeSeedWordLength32RadioButton.isChecked()) {
                    selectedWordCount = 32;
                } else if (homeSeedWordLength36RadioButton.isChecked()) {
                    selectedWordCount = 36;
                } else if (homeSeedWordLength48RadioButton.isChecked()) {
                    selectedWordCount = 48;
                } else {
                    String message = jsonViewModel.getSelectOptionByErrors();
                    Bundle bundleRoute = new Bundle();
                    bundleRoute.putString("languageKey",languageKey);
                    bundleRoute.putString("message", message);
                    FragmentManager fragmentManager = getFragmentManager();
                    MessageInformationDialogFragment messageDialogFragment = MessageInformationDialogFragment.newInstance();
                    messageDialogFragment.setCancelable(false);
                    messageDialogFragment.setArguments(bundleRoute);
                    messageDialogFragment.show(fragmentManager, "");
                    return;
                }
                homeSeedWordLengthLinearLayout.setVisibility(View.GONE);
                homeSeedWordsEditLinearLayout.setVisibility(View.VISIBLE);
                homeSeedWordsEditSkip.setVisibility(View.GONE);
                ShowEditSeedScreen(homeSeedWordsEditTitleTextView, homeSeedWordsAutoCompleteNextButton, true);
                updateSeedRowVisibility(homeSeedWordsViewCaptionTextViews, homeSeedWordsViewTextViews,
                        homeSeedWordsEditCaptionTextViews, homeSeedWordsViewAutoCompleteTextViews, selectedWordCount);

                homeSeedWordsAutoCompleteNextButton.setVisibility(View.GONE);

                if(progressBar.getVisibility() == View.VISIBLE){
                    return;
                }
                progressBar.setVisibility(View.VISIBLE);
                new Thread(new Runnable() {
                    public void run() {
                        while (true) {
                            getActivity().runOnUiThread(new Runnable() {
                                public void run() {
                                    if (GlobalMethods.seedLoaded) {
                                        ArrayList<String> seedWordsList = GlobalMethods.ALL_SEED_WORDS;
                                        homeSeedWordsAutoCompleteNextButton.setVisibility(View.VISIBLE);
                                        homeSeedWordsViewAutoCompleteTextViews[autoCompleteCurrentIndex].requestFocus();
                                        seedWordAutoCompleteAdapter = new SeedWordAutoCompleteAdapter(getContext(), android.R.layout.simple_dropdown_item_1line,
                                                android.R.id.text1, seedWordsList);
                                        progressBar.setVisibility(View.GONE);
                                    }
                                }
                            });
                            try {
                                if(homeSeedWordsAutoCompleteNextButton.getVisibility() == View.VISIBLE){
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
        });

        homeSeedWordsShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(progressBar.getVisibility() == View.VISIBLE){
                    return;
                }
                progressBar.setVisibility(View.VISIBLE);
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            KeyViewModel.getBridge().initializeOffline();
                            String createResult = KeyViewModel.getBridge().createRandom(selectedKeyType);
                            JSONObject json = new JSONObject(createResult);
                            JSONObject data = json.getJSONObject("data");
                            JSONArray wordsArray = data.getJSONArray("seedWords");
                            final String[] words = new String[wordsArray.length()];
                            for (int i = 0; i < wordsArray.length(); i++) {
                                words[i] = wordsArray.getString(i);
                            }
                            tempSeedWords = words;
                            tempAddress = data.getString("address");
                            tempPrivateKeyBase64 = data.getString("privateKey");
                            tempPublicKeyBase64 = data.getString("publicKey");
                            selectedWordCount = words.length;
                            getActivity().runOnUiThread(new Runnable() {
                                public void run() {
                                    ShowNewSeedScreen(homeSeedWordsViewTitleTextView, homeSeedWordsViewTextViews, homeSeedWordsViewNextButton, words);
                                    updateSeedRowVisibility(homeSeedWordsViewCaptionTextViews, homeSeedWordsViewTextViews,
                                            homeSeedWordsEditCaptionTextViews, homeSeedWordsViewAutoCompleteTextViews, selectedWordCount);
                                    homeSeedWordsLinearLayout.setVisibility(View.GONE);
                                    homeSeedWordsViewLinearLayout.setVisibility(View.VISIBLE);
                                    progressBar.setVisibility(View.GONE);
                                }
                            });
                        } catch (Exception e) {
                            final String errorMsg = e.getMessage();
                            getActivity().runOnUiThread(new Runnable() {
                                public void run() {
                                    progressBar.setVisibility(View.GONE);
                                    GlobalMethods.ShowErrorDialog(getContext(), "Error", errorMsg);
                                }
                            });
                        }
                    }
                }).start();
            }
        });

        homeSeedWordsViewCopyLink.setText(jsonViewModel.getCopyByLangValues());
        homeSeedWordsViewCopied.setText(jsonViewModel.getCopiedByLangValues());
        homeSeedWordsEditSkip.setText(jsonViewModel.getSkipByLangValues());

        View.OnClickListener homeCopyClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                progressBar.setVisibility(View.VISIBLE);
                String clipboardCopyData = ClipboardCopyData(homeSeedWordsViewCaptionTextViews, homeSeedWordsViewTextViews);
                com.quantumcoinwallet.app.utils.SecureClipboard.copySensitive(
                        getActivity(), "walletSeed", clipboardCopyData);
                progressBar.setVisibility(View.GONE);
                homeSeedWordsViewCopied.setVisibility(View.VISIBLE);
                new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        homeSeedWordsViewCopied.setVisibility(View.GONE);
                    }
                }, 600);
            }
        };
        homeSeedWordsViewCopyClipboardImageButton.setOnClickListener(homeCopyClickListener);
        homeSeedWordsViewCopyLink.setOnClickListener(homeCopyClickListener);

        homeSeedWordsViewNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                homeSeedWordsViewLinearLayout.setVisibility(View.GONE);
                homeSeedWordsEditLinearLayout.setVisibility(View.VISIBLE);
                homeSeedWordsEditSkip.setVisibility(View.VISIBLE);
                ShowEditSeedScreen(homeSeedWordsEditTitleTextView, homeSeedWordsAutoCompleteNextButton, false);

                homeSeedWordsViewAutoCompleteTextViews[autoCompleteCurrentIndex].requestFocus();

                ArrayList<String> seedWordsList = GlobalMethods.ALL_SEED_WORDS;
                seedWordAutoCompleteAdapter = new SeedWordAutoCompleteAdapter(getContext(), android.R.layout.simple_dropdown_item_1line,
                        android.R.id.text1, seedWordsList);
            }
        });


        homeSeedWordsAutoCompleteNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (walletPassword == null || walletPassword.isEmpty()) {
                        walletPassword = homeSetWalletPasswordEditText.getText().toString();
                    }

                    int wordCount;
                    if (tempSeedWords != null) {
                        wordCount = tempSeedWords.length;
                    } else {
                        wordCount = 0;
                        for (AutoCompleteTextView actv : homeSeedWordsViewAutoCompleteTextViews) {
                            if (actv.getText().length() > 0) {
                                wordCount++;
                            } else {
                                break;
                            }
                        }
                        if (wordCount == 0) {
                            homeSeedWordsViewAutoCompleteTextViews[0].requestFocus();
                            return;
                        }
                    }

                    final String[] seedWords = new String[wordCount];
                    for (int i = 0; i < wordCount; i++) {
                        AutoCompleteTextView actv = homeSeedWordsViewAutoCompleteTextViews[i];
                        if (actv.getText().length() == 0) {
                            actv.requestFocus();
                            return;
                        }
                        if (!GlobalMethods.SEED_WORD_SET.contains(actv.getText().toString().toLowerCase())) {
                            actv.setText("");
                            return;
                        }
                        if (tempSeedWords != null && !actv.getText().toString().equalsIgnoreCase(tempSeedWords[i])) {
                            actv.setText("");
                            return;
                        }
                        seedWords[i] = actv.getText().toString().toLowerCase();
                    }

                    if (homeCreateRestoreWalletRadioButton_1.isChecked()) {
                        showConfirmWalletScreen(seedWords, progressBar);
                    } else {
                        saveWalletFromSeedWords(seedWords, progressBar);
                    }

                } catch (Exception e) {
                    progressBar.setVisibility(View.GONE);
                    GlobalMethods.ShowErrorDialog(getContext(), "Error", e.getMessage());
                }
            }
        });

        homeSeedWordsEditSkip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(getContext())
                    .setMessage(jsonViewModel.getSkipVerifyConfirmByLangValues())
                    .setPositiveButton(jsonViewModel.getYesByLangValues(), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (walletPassword == null || walletPassword.isEmpty()) {
                                walletPassword = homeSetWalletPasswordEditText.getText().toString();
                            }
                            if (tempSeedWords != null) {
                                saveWalletFromSeedWords(tempSeedWords, progressBar);
                            }
                        }
                    })
                    .setNegativeButton(jsonViewModel.getNoByLangValues(), null)
                    .show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        walletPassword = null;
        super.onDestroyView();
    }

    public static interface OnHomeWalletCompleteListener {
        public abstract void onHomeWalletCompleteByHomeMain(String indexKey);
        public abstract void onHomeWalletCompleteByWallets();

    }

    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.mHomeWalletListener = (OnHomeWalletCompleteListener) context;
        } catch (final ClassCastException e) {
            throw new ClassCastException(context.toString() + " ");
        }
    }

    private void SetWalletView(TextView setWalletTitleTextView, TextView setWalletDescriptionTextView,
                               TextView setWalletPasswordTitleTextView, EditText setWalletPasswordEditText,
                               TextView setWalletRetypePasswordTitleTextView, EditText setWalletRetypePasswordEditText,
                               Button setWalletNextButton) {

        setWalletTitleTextView.setText(jsonViewModel.getSetWalletPasswordByLangValues());
        setWalletDescriptionTextView.setText(jsonViewModel.getUseStrongPasswordByLangValues());

        setWalletPasswordTitleTextView.setText(jsonViewModel.getPasswordByLangValues());
        setWalletPasswordEditText.setHint(jsonViewModel.getEnterApasswordByLangValues());

        setWalletRetypePasswordTitleTextView.setText(jsonViewModel.getRetypePasswordByLangValues());
        setWalletRetypePasswordEditText.setHint(jsonViewModel.getRetypeThePasswordByLangValues());

        setWalletNextButton.setText(jsonViewModel.getNextByLangValues());
    }

    private void CreateRestoreWalletView(TextView createRestoreWalletTitleTextView, TextView createRestoreWalletDescriptionTextView,
                                         RadioButton createRestoreWalletRadioButton_0, RadioButton createRestoreWalletRadioButton_1,
                                         RadioButton createRestoreWalletRadioButton_2, RadioButton createRestoreWalletRadioButton_3,
                                         Button createRestoreWalletNextButton) {

        createRestoreWalletTitleTextView.setText(jsonViewModel.getCreateRestoreWalletByLangValues());
        createRestoreWalletDescriptionTextView.setText(jsonViewModel.getSelectAnOptionByLangValues());

        createRestoreWalletRadioButton_0.setText(jsonViewModel.getCreateNewWalletByLangValues());
        createRestoreWalletRadioButton_1.setText(jsonViewModel.getRestoreWalletFromSeedByLangValues());
        createRestoreWalletRadioButton_2.setText(jsonViewModel.getRestoreFromFileByLangValues());
        createRestoreWalletRadioButton_3.setText(jsonViewModel.getRestoreFromCloudByLangValues());

        createRestoreWalletRadioButton_0.setTag(0);
        createRestoreWalletRadioButton_1.setTag(1);
        createRestoreWalletRadioButton_2.setTag(2);
        createRestoreWalletRadioButton_3.setTag(3);

        createRestoreWalletNextButton.setText(jsonViewModel.getNextByLangValues());
    }

    private void SeedWordsView(TextView seedWordsTitleTextView, TextView seedWords1TextView,
                               TextView seedWords2TextView, TextView seedWords3TextView, TextView seedWords4TextView, TextView seedWordsShowTextView) {

        seedWordsTitleTextView.setText(jsonViewModel.getSeedWordsByLangValues());
        seedWords1TextView.setText(jsonViewModel.getSeedWordsInfo1ByLangValues());
        seedWords2TextView.setText(jsonViewModel.getSeedWordsInfo2ByLangValues());
        seedWords3TextView.setText(jsonViewModel.getSeedWordsInfo3ByLangValues());
        seedWords4TextView.setText(jsonViewModel.getSeedWordsInfo4ByLangValues());

        SpannableString content = new SpannableString(jsonViewModel.getSeedWordsShowByLangValues());
        content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
        seedWordsShowTextView.setText(content);
    }

    private TextView[] HomeSeedWordsViewCaptionTextViews() {
        TextView[] homeSeedWordsViewCaptionTextViews = new TextView[]{
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_a1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_a2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_a3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_a4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_b1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_b2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_b3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_b4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_c1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_c2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_c3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_c4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_d1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_d2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_d3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_d4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_e1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_e2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_e3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_e4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_f1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_f2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_f3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_f4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_g1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_g2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_g3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_g4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_h1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_h2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_h3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_h4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_i1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_i2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_i3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_i4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_j1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_j2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_j3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_j4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_k1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_k2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_k3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_k4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_l1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_l2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_l3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_caption_l4),

        };
        return homeSeedWordsViewCaptionTextViews;
    }

    private TextView[] HomeSeedWordsViewTextViews() {
        TextView[] homeSeedWordsViewTextViews = new TextView[]{
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_a1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_a2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_a3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_a4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_b1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_b2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_b3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_b4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_c1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_c2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_c3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_c4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_d1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_d2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_d3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_d4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_e1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_e2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_e3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_e4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_f1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_f2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_f3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_f4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_g1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_g2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_g3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_g4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_h1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_h2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_h3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_h4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_i1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_i2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_i3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_i4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_j1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_j2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_j3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_j4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_k1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_k2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_k3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_k4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_l1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_l2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_l3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_view_l4),

        };
        return homeSeedWordsViewTextViews;
    }

    private void ShowNewSeedScreen(TextView homeSeedWordsViewTitleTextView, TextView[] textViews,
                                   Button homeSeedWordsViewNextButton, String[] words) {

        homeSeedWordsViewTitleTextView.setText(jsonViewModel.getSeedWordsByLangValues());

        for (int i = 0; i < words.length && i < textViews.length; i++) {
            textViews[i].setText(words[i].toUpperCase());
        }
        for (int i = words.length; i < textViews.length; i++) {
            textViews[i].setText("");
        }

        homeSeedWordsViewNextButton.setText(jsonViewModel.getNextByLangValues());
    }

    private AutoCompleteTextView[] HomeSeedWordsViewAutoCompleteTextView() {
        return new AutoCompleteTextView[]{
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_a1),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_a2),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_a3),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_a4),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_b1),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_b2),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_b3),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_b4),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_c1),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_c2),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_c3),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_c4),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_d1),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_d2),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_d3),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_d4),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_e1),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_e2),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_e3),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_e4),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_f1),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_f2),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_f3),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_f4),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_g1),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_g2),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_g3),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_g4),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_h1),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_h2),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_h3),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_h4),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_i1),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_i2),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_i3),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_i4),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_j1),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_j2),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_j3),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_j4),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_k1),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_k2),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_k3),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_k4),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_l1),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_l2),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_l3),
                (AutoCompleteTextView) getView().findViewById(R.id.autoComplete_home_seed_words_textView_l4)
        };
    }

    private String ClipboardCopyData(TextView[] homeSeedWordsViewCaptionTextViews, TextView[] homeSeedWordsViewTextViews){
        String copyData = "";
        int limit = Math.min(selectedWordCount, homeSeedWordsViewCaptionTextViews.length);
        for (int i=0; i<limit; i++) {
            copyData = copyData + homeSeedWordsViewCaptionTextViews[i].getText() + " = " +  homeSeedWordsViewTextViews[i].getText() + "\n";
        }
        return copyData.toString();
    }


    private TextWatcher GetTextWatcher(final AutoCompleteTextView autoCompleteTextView, int index) {
        return new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //autoCompleteTextView.setDropDownWidth(getResources().getDisplayMetrics().widthPixels);
                autoCompleteTextView.setAdapter(seedWordAutoCompleteAdapter);
            }
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                autoCompleteCurrentIndex = index;
            }
            @Override
            public void afterTextChanged(Editable s) {
                //if(autoCompleteTextView.getId() == R.id.autoComplete_home_seed_words_textView_a1){
                //}
            }
        };
    }

    private void ShowEditSeedScreen(TextView homeSeedWordsEditTitleTextView, Button homeSeedWordsEditNextButton,
                                    boolean restoreFromSeedFlow) {
        homeSeedWordsEditTitleTextView.setText(restoreFromSeedFlow
                ? jsonViewModel.getEnterSeedWordsByLangValues()
                : jsonViewModel.getVerifySeedWordsByLangValues());
        homeSeedWordsEditNextButton.setText(jsonViewModel.getNextByLangValues());
    }

    /**
     * Intermediate confirmation step shown after the user verifies (or skips) seed
     * words and before the backup-options screen. The wallet is NOT persisted here;
     * the user can press Back to edit the seed words they entered, or Next to commit
     * via {@link #saveWalletFromSeedWords}.
     *
     * <p>Address resolution: for the create-then-verify happy path the wallet was
     * already derived in {@code ShowNewSeedScreen} and cached in {@code tempAddress}
     * / {@code tempPrivateKeyBase64} / {@code tempPublicKeyBase64}, so we reuse it.
     * For the restore-from-seed path we run {@code walletFromPhrase} on a worker
     * thread to derive the address, populating the same temp fields so that the
     * existing reuse-cached short-circuit in {@link #saveWalletFromSeedWords} skips
     * recomputation when Next is finally pressed.
     */
    private void showConfirmWalletScreen(final String[] seedWords, final ProgressBar progressBar) {
        if (getView() == null || homeConfirmWalletLinearLayout == null) return;

        TextView titleTextView = (TextView) getView().findViewById(R.id.textView_home_confirm_wallet_title);
        TextView descriptionTextView = (TextView) getView().findViewById(R.id.textView_home_confirm_wallet_description);
        TextView addressLabelTextView = (TextView) getView().findViewById(R.id.textView_home_confirm_wallet_address_label);
        TextView balanceLabelTextView = (TextView) getView().findViewById(R.id.textView_home_confirm_wallet_balance_label);
        Button backButton = (Button) getView().findViewById(R.id.button_home_confirm_wallet_back);
        Button nextButton = (Button) getView().findViewById(R.id.button_home_confirm_wallet_next);
        final ImageButton copyButton = (ImageButton) getView().findViewById(R.id.imageButton_home_confirm_wallet_copy);
        final ImageButton exploreButton = (ImageButton) getView().findViewById(R.id.imageButton_home_confirm_wallet_explorer);
        final TextView copiedToast = (TextView) getView().findViewById(R.id.textView_home_confirm_wallet_copied);

        if (titleTextView != null) titleTextView.setText(jsonViewModel.getConfirmWalletByLangValues());
        if (descriptionTextView != null) descriptionTextView.setText(jsonViewModel.getConfirmWalletDescriptionByLangValues());
        if (addressLabelTextView != null) addressLabelTextView.setText(jsonViewModel.getAddressByLangValues());
        if (balanceLabelTextView != null) balanceLabelTextView.setText(jsonViewModel.getBalanceByLangValues());
        if (backButton != null) backButton.setText(jsonViewModel.getBackByLangValues());
        if (nextButton != null) nextButton.setText(jsonViewModel.getNextByLangValues());
        if (copiedToast != null) copiedToast.setText(jsonViewModel.getCopiedByLangValues());

        if (copyButton != null) {
            copyButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (homeConfirmWalletAddressValueTextView == null) return;
                    String addr = homeConfirmWalletAddressValueTextView.getText().toString();
                    if (addr.isEmpty()) return;
                    com.quantumcoinwallet.app.utils.SecureClipboard.copyAddress(
                            getActivity(), "confirmWalletAddress", addr);
                    if (copiedToast != null) {
                        copiedToast.setVisibility(View.VISIBLE);
                        new Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                copiedToast.setVisibility(View.GONE);
                            }
                        }, 600);
                    }
                }
            });
        }
        if (exploreButton != null) {
            exploreButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (homeConfirmWalletAddressValueTextView == null) return;
                    String addr = homeConfirmWalletAddressValueTextView.getText().toString();
                    if (addr.isEmpty() || GlobalMethods.BLOCK_EXPLORER_URL == null) return;
                    String url = GlobalMethods.BLOCK_EXPLORER_URL
                            + GlobalMethods.BLOCK_EXPLORER_ACCOUNT_TRANSACTION_URL.replace("{address}", addr);
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ex) {
                        GlobalMethods.ExceptionError(getContext(), TAG, ex);
                    }
                }
            });
        }

        if (homeSeedWordsEditLinearLayoutRef != null) {
            homeSeedWordsEditLinearLayoutRef.setVisibility(View.GONE);
        }
        homeConfirmWalletLinearLayout.setVisibility(View.VISIBLE);

        if (homeConfirmWalletAddressValueTextView != null) {
            homeConfirmWalletAddressValueTextView.setText("");
        }
        if (homeConfirmWalletBalanceValueTextView != null) {
            homeConfirmWalletBalanceValueTextView.setText("");
        }

        if (backButton != null) {
            backButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    homeConfirmWalletLinearLayout.setVisibility(View.GONE);
                    if (homeSeedWordsEditLinearLayoutRef != null) {
                        homeSeedWordsEditLinearLayoutRef.setVisibility(View.VISIBLE);
                    }
                }
            });
        }
        if (nextButton != null) {
            nextButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (tempSeedWords == null) {
                        return;
                    }
                    saveWalletFromSeedWords(tempSeedWords, progressBar);
                }
            });
        }

        boolean addressAlreadyCached = tempAddress != null
                && tempPrivateKeyBase64 != null
                && tempPublicKeyBase64 != null
                && tempSeedWords != null
                && tempSeedWords.length == seedWords.length
                && java.util.Arrays.equals(tempSeedWords, seedWords);

        if (addressAlreadyCached) {
            populateConfirmWalletAddressAndBalance(tempAddress);
            return;
        }

        final AlertDialog waitDlg = com.quantumcoinwallet.app.view.dialog.WaitDialog
                .show(getContext(), jsonViewModel.getWaitWalletOpenByLangValues());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    KeyViewModel.getBridge().initializeOffline();
                    String walletResult = KeyViewModel.getBridge().walletFromPhrase(seedWords);
                    JSONObject json = new JSONObject(walletResult);
                    JSONObject data = json.getJSONObject("data");
                    final String derivedAddress = data.getString("address");
                    final String derivedPrivateKey = data.getString("privateKey");
                    final String derivedPublicKey = data.getString("publicKey");
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                            tempSeedWords = seedWords;
                            tempAddress = derivedAddress;
                            tempPrivateKeyBase64 = derivedPrivateKey;
                            tempPublicKeyBase64 = derivedPublicKey;
                            populateConfirmWalletAddressAndBalance(derivedAddress);
                        }
                    });
                } catch (final Exception e) {
                    timber.log.Timber.w(e, "confirm wallet derive failed");
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                            homeConfirmWalletLinearLayout.setVisibility(View.GONE);
                            if (homeSeedWordsEditLinearLayoutRef != null) {
                                homeSeedWordsEditLinearLayoutRef.setVisibility(View.VISIBLE);
                            }
                            GlobalMethods.ShowErrorDialog(getContext(),
                                    jsonViewModel.getErrorTitleByLangValues(),
                                    e.getMessage() == null ? "" : e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    /**
     * Sets the address text and kicks off a balance fetch. Balance failures
     * (offline / API error) leave a "-" placeholder; Next remains enabled in all
     * cases.
     */
    private void populateConfirmWalletAddressAndBalance(final String address) {
        if (homeConfirmWalletAddressValueTextView != null) {
            homeConfirmWalletAddressValueTextView.setText(address == null ? "" : address);
        }
        if (homeConfirmWalletBalanceValueTextView != null) {
            homeConfirmWalletBalanceValueTextView.setText("-");
        }
        if (address == null || address.isEmpty()) return;
        if (!GlobalMethods.IsNetworkAvailable(getContext())) return;

        if (homeConfirmWalletBalanceProgressBar != null) {
            homeConfirmWalletBalanceProgressBar.setVisibility(View.VISIBLE);
        }
        try {
            String[] taskParams = { address };
            AccountBalanceRestTask task = new AccountBalanceRestTask(getContext(),
                    new AccountBalanceRestTask.TaskListener() {
                        @Override
                        public void onFinished(BalanceResponse balanceResponse) throws ServiceException {
                            if (homeConfirmWalletBalanceProgressBar != null) {
                                homeConfirmWalletBalanceProgressBar.setVisibility(View.GONE);
                            }
                            if (balanceResponse != null && balanceResponse.getResult() != null
                                    && balanceResponse.getResult().getBalance() != null
                                    && homeConfirmWalletBalanceValueTextView != null) {
                                String value = balanceResponse.getResult().getBalance().toString();
                                String quantity = CoinUtils.formatWei(value);
                                String coinsLabel = jsonViewModel.getCoinsByLangValues();
                                String text = (coinsLabel != null && !coinsLabel.isEmpty())
                                        ? quantity + " " + coinsLabel
                                        : quantity;
                                homeConfirmWalletBalanceValueTextView.setText(text);
                            }
                        }
                        @Override
                        public void onFailure(ApiException e) {
                            if (homeConfirmWalletBalanceProgressBar != null) {
                                homeConfirmWalletBalanceProgressBar.setVisibility(View.GONE);
                            }
                            // Leave the "-" placeholder in place; Next stays enabled.
                        }
                    });
            task.execute(taskParams);
        } catch (Exception e) {
            if (homeConfirmWalletBalanceProgressBar != null) {
                homeConfirmWalletBalanceProgressBar.setVisibility(View.GONE);
            }
            timber.log.Timber.w(e, "confirm wallet balance fetch failed");
        }
    }

    private void saveWalletFromSeedWords(final String[] seedWords, final ProgressBar progressBar) {
        SecureStorage secureStorageGuard = KeyViewModel.getSecureStorage();
        final boolean needsPassword =
                !secureStorageGuard.isInitialized(getContext()) || !secureStorageGuard.isUnlocked();
        if (needsPassword && (walletPassword == null || walletPassword.isEmpty())) {
            String msg = jsonViewModel.getWalletPasswordNotSetByErrors();
            if (msg == null || msg.isEmpty()) {
                msg = "Wallet password is not set.";
            }
            GlobalMethods.ShowErrorDialog(getContext(),
                    jsonViewModel.getErrorTitleByLangValues(), msg);
            return;
        }
        final AlertDialog waitDlg = com.quantumcoinwallet.app.view.dialog.WaitDialog
                .show(getContext(), jsonViewModel.getWaitWalletSaveByLangValues());
        new Thread(new Runnable() {
            public void run() {
                try {
                    final String address;
                    String privateKeyBase64;
                    String publicKeyBase64;
                    boolean reuseCached = tempAddress != null && tempPrivateKeyBase64 != null
                            && tempPublicKeyBase64 != null && tempSeedWords != null
                            && seedWords.length == tempSeedWords.length
                            && java.util.Arrays.equals(seedWords, tempSeedWords);
                    if (reuseCached) {
                        address = tempAddress;
                        privateKeyBase64 = tempPrivateKeyBase64;
                        publicKeyBase64 = tempPublicKeyBase64;
                    } else {
                        KeyViewModel.getBridge().initializeOffline();
                        String walletResult = KeyViewModel.getBridge().walletFromPhrase(seedWords);
                        JSONObject json = new JSONObject(walletResult);
                        JSONObject data = json.getJSONObject("data");
                        address = data.getString("address");
                        privateKeyBase64 = data.getString("privateKey");
                        publicKeyBase64 = data.getString("publicKey");
                    }
                    if (PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP != null
                            && PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.containsKey(address)) {
                        final String existingAddress = address;
                        getActivity().runOnUiThread(new Runnable() {
                            public void run() {
                                if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                                showDuplicateWalletDialog(existingAddress);
                            }
                        });
                        return;
                    }
                    String seedWordsJoined = android.text.TextUtils.join(",", seedWords);

                    SecureStorage secureStorage = KeyViewModel.getSecureStorage();

                    if (!secureStorage.isInitialized(getContext())) {
                        secureStorage.createMainKey(getContext(), walletPassword);
                    }
                    if (!secureStorage.isUnlocked()) {
                        secureStorage.unlock(getContext(), walletPassword);
                    }

                    int newIndex = secureStorage.getMaxWalletIndex(getContext()) + 1;
                    walletIndexKey = String.valueOf(newIndex);

                    JSONObject walletJson = new JSONObject();
                    walletJson.put("address", address);
                    walletJson.put("privateKey", privateKeyBase64);
                    walletJson.put("publicKey", publicKeyBase64);
                    walletJson.put("seed", seedWordsJoined);

                    secureStorage.saveWallet(getContext(), newIndex, walletJson.toString());
                    secureStorage.setMaxWalletIndex(getContext(), newIndex);

                    walletPassword = null;

                    PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.put(address, walletIndexKey);
                    PrefConnect.WALLET_INDEX_TO_ADDRESS_MAP.put(walletIndexKey, address);
                    PrefConnect.writeBoolean(getContext(),
                            PrefConnect.WALLET_HAS_SEED_KEY_PREFIX + walletIndexKey, true);
                    PrefConnect.WALLET_INDEX_HAS_SEED_MAP.put(walletIndexKey, true);

                    pendingBackupWalletJson = walletJson;

                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                            showBackupOptionsScreen();
                        }
                    });
                } catch (Exception e) {
                    final String errorMsg = e.getMessage();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            public void run() {
                                if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                                GlobalMethods.ShowErrorDialog(getContext(), "Error", errorMsg);
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private void showBackupPromptIfNeeded(final Runnable onComplete) {
        boolean alreadyPrompted = getContext().getSharedPreferences(
                PrefConnect.PREF_NAME, Context.MODE_PRIVATE).contains(PrefConnect.BACKUP_ENABLED_KEY);
        if (alreadyPrompted) {
            onComplete.run();
            return;
        }

        // Show the phone-backup radio step instead of a yes/no dialog. The
        // caller is responsible for hiding whatever section is currently
        // visible (see createRestoreWalletNextButton click handler).
        pendingPhoneBackupOnComplete = onComplete;
        if (homePhoneBackupYesRadioButton != null) {
            homePhoneBackupYesRadioButton.setChecked(false);
        }
        if (homePhoneBackupNoRadioButton != null) {
            homePhoneBackupNoRadioButton.setChecked(false);
        }
        PhoneBackupView(homePhoneBackupTitleTextView, homePhoneBackupDescriptionTextView,
                homePhoneBackupYesRadioButton, homePhoneBackupNoRadioButton,
                homePhoneBackupNextButton);
        homePhoneBackupLinearLayout.setVisibility(View.VISIBLE);
    }

    private void PhoneBackupView(TextView titleTextView, TextView descriptionTextView,
                                 RadioButton yesRadioButton, RadioButton noRadioButton,
                                 Button nextButton) {
        titleTextView.setText(jsonViewModel.getPhoneBackupByLangValues());
        descriptionTextView.setText(jsonViewModel.getBackupPromptByLangValues());
        yesRadioButton.setText(jsonViewModel.getYesByLangValues());
        noRadioButton.setText(jsonViewModel.getNoByLangValues());
        yesRadioButton.setTag(1);
        noRadioButton.setTag(0);
        nextButton.setText(jsonViewModel.getNextByLangValues());
    }

    private void launchFolderPicker() {
        try {
            setSuppressNextResumeLock(true);
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            cloudFolderPickerLauncher.launch(intent);
        } catch (Exception e) {
            setSuppressNextResumeLock(false);
            Runnable continuation = pendingCloudContinuation;
            pendingCloudContinuation = null;
            if (continuation != null) continuation.run();
        }
    }

    private void showBackupOptionsScreen() {
        if (getView() == null) return;
        hideAllHomeWalletLayouts();
        if (backupOptionsLinearLayout == null) {
            backupOptionsLinearLayout = (LinearLayout) getView().findViewById(R.id.linear_layout_home_backup_options);
            backupCloudStatusTextView = (TextView) getView().findViewById(R.id.textView_home_backup_cloud_status);
            backupFileStatusTextView = (TextView) getView().findViewById(R.id.textView_home_backup_file_status);
        }
        if (backupOptionsLinearLayout == null) return;

        TextView titleTextView = (TextView) getView().findViewById(R.id.textView_home_backup_options_title);
        TextView descriptionTextView = (TextView) getView().findViewById(R.id.textView_home_backup_options_description);
        Button cloudBtn = (Button) getView().findViewById(R.id.button_home_backup_to_cloud);
        Button fileBtn = (Button) getView().findViewById(R.id.button_home_backup_to_file);
        Button doneBtn = (Button) getView().findViewById(R.id.button_home_backup_done);

        titleTextView.setText(jsonViewModel.getBackupOptionsTitleByLangValues());
        descriptionTextView.setText(jsonViewModel.getBackupOptionsDescriptionByLangValues());
        cloudBtn.setText(jsonViewModel.getBackupToCloudByLangValues());
        fileBtn.setText(jsonViewModel.getBackupToFileByLangValues());
        doneBtn.setText(jsonViewModel.getBackupDoneByLangValues());

        backupCloudStatusTextView.setVisibility(View.GONE);
        backupFileStatusTextView.setVisibility(View.GONE);

        cloudBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCloudBackupInfoAndContinue(new Runnable() {
                    @Override
                    public void run() {
                        startCloudBackupFromOptionsScreen();
                    }
                });
            }
        });
        fileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFileBackupFromOptionsScreen();
            }
        });
        doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishBackupAndNavigateToHome();
            }
        });

        backupOptionsLinearLayout.setVisibility(View.VISIBLE);
    }

    private void hideAllHomeWalletLayouts() {
        if (getView() == null) return;
        int[] ids = new int[]{
                R.id.linear_layout_home_set_wallet,
                R.id.linear_layout_home_create_restore_wallet,
                R.id.linear_layout_home_wallet_type,
                R.id.linear_layout_home_seed_word_length,
                R.id.linear_layout_home_seed_words,
                R.id.linear_layout_home_seed_words_view,
                R.id.linear_layout_home_seed_words_edit,
                R.id.linear_layout_home_confirm_wallet,
                R.id.linear_layout_home_backup_options
        };
        for (int id : ids) {
            View v = getView().findViewById(id);
            if (v != null) v.setVisibility(View.GONE);
        }
    }

    /**
     * Show an OK-only confirmation explaining the Android cloud-folder picker
     * depends on phone configuration, and only continue with {@code next} when
     * the user acknowledges.
     */
    private void showCloudBackupInfoAndContinue(final Runnable next) {
        if (getContext() == null) {
            if (next != null) next.run();
            return;
        }
        String message = jsonViewModel.getCloudBackupInfoByLangValues();
        if (message == null || message.isEmpty()) {
            message = "You will be able to see cloud options only if configured in the phone";
        }
        new AlertDialog.Builder(getContext())
                .setTitle(jsonViewModel.getBackupByLangValues())
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(jsonViewModel.getOkByLangValues(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (next != null) next.run();
                    }
                })
                .show();
    }

    private void startCloudBackupFromOptionsScreen() {
        if (pendingBackupWalletJson == null) return;
        BackupPasswordDialogShow(new BackupPasswordReceiver() {
            @Override
            public void onPassword(final String backupPassword) {
                encryptWalletForBackup(backupPassword, new EncryptedReady() {
                    @Override
                    public void onReady(final String encryptedJson, final String address) {
                        String folderUriStr = PrefConnect.readString(getContext(),
                                PrefConnect.CLOUD_BACKUP_FOLDER_URI_KEY, "");
                        if (folderUriStr == null || folderUriStr.isEmpty()) {
                            pendingCloudContinuation = new Runnable() {
                                @Override
                                public void run() {
                                    String chosen = PrefConnect.readString(getContext(),
                                            PrefConnect.CLOUD_BACKUP_FOLDER_URI_KEY, "");
                                    if (chosen != null && !chosen.isEmpty()) {
                                        writeEncryptedToSafFolder(encryptedJson, address);
                                    }
                                }
                            };
                            launchFolderPicker();
                        } else {
                            writeEncryptedToSafFolder(encryptedJson, address);
                        }
                    }
                });
            }
        });
    }

    private void writeEncryptedToSafFolder(final String encryptedJson, final String address) {
        final String folderUriStr = PrefConnect.readString(getContext(),
                PrefConnect.CLOUD_BACKUP_FOLDER_URI_KEY, "");
        if (folderUriStr == null || folderUriStr.isEmpty()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String filename = com.quantumcoinwallet.app.backup.CloudBackupManager.buildFilename(address);
                    com.quantumcoinwallet.app.backup.CloudBackupManager.writeToSafFolder(
                            getContext(), Uri.parse(folderUriStr), filename, encryptedJson);
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() { flagCloudBackupSaved(); }
                    });
                } catch (final Exception e) {
                    android.util.Log.e(TAG, "cloud backup write failed", e);
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            String tmpl = jsonViewModel.getBackupFailedByLangValues();
                            String msg = tmpl != null
                                    ? tmpl.replace("[ERROR]", e.getMessage() == null ? "" : e.getMessage())
                                    : ("Backup failed: " + e.getMessage());
                            GlobalMethods.ShowErrorDialog(getContext(),
                                    jsonViewModel.getErrorTitleByLangValues(), msg);
                        }
                    });
                }
            }
        }).start();
    }

    private void startFileBackupFromOptionsScreen() {
        if (pendingBackupWalletJson == null) return;
        BackupPasswordDialogShow(new BackupPasswordReceiver() {
            @Override
            public void onPassword(final String backupPassword) {
                encryptWalletForBackup(backupPassword, new EncryptedReady() {
                    @Override
                    public void onReady(String encryptedJson, String address) {
                        pendingBackupEncryptedJson = encryptedJson;
                        pendingBackupAddress = address;
                        try {
                            setSuppressNextResumeLock(true);
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType(com.quantumcoinwallet.app.backup.CloudBackupManager.BACKUP_MIME_TYPE);
                            intent.putExtra(Intent.EXTRA_TITLE,
                                    com.quantumcoinwallet.app.backup.CloudBackupManager.buildFilename(address));
                            fileBackupLauncher.launch(intent);
                        } catch (Exception e) {
                            setSuppressNextResumeLock(false);
                            GlobalMethods.ExceptionError(getContext(), TAG, e);
                        }
                    }
                });
            }
        });
    }

    private interface BackupPasswordReceiver {
        void onPassword(String backupPassword);
    }

    private interface EncryptedReady {
        void onReady(String encryptedJson, String address);
    }

    private void BackupPasswordDialogShow(final BackupPasswordReceiver receiver) {
        com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog.show(
                getContext(), jsonViewModel,
                new com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog.OnBackupPasswordListener() {
                    @Override
                    public void onPasswordSelected(String password) {
                        receiver.onPassword(password);
                    }
                    @Override
                    public void onCanceled() { }
                });
    }

    private void encryptWalletForBackup(final String backupPassword, final EncryptedReady onReady) {
        final AlertDialog waitDlg = com.quantumcoinwallet.app.view.dialog.WaitDialog
                .show(getContext(), jsonViewModel.getWaitWalletSaveByLangValues());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final com.quantumcoinwallet.app.backup.CloudBackupManager.EncryptedResult enc =
                            com.quantumcoinwallet.app.backup.CloudBackupManager
                                    .encryptWallet(pendingBackupWalletJson, backupPassword);
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                            onReady.onReady(enc.json, enc.address);
                        }
                    });
                } catch (final Exception e) {
                    android.util.Log.e(TAG, "encryptWalletForBackup failed", e);
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                            String tmpl = jsonViewModel.getBackupFailedByLangValues();
                            String msg = tmpl != null
                                    ? tmpl.replace("[ERROR]", e.getMessage() == null ? "" : e.getMessage())
                                    : ("Backup failed: " + e.getMessage());
                            GlobalMethods.ShowErrorDialog(getContext(),
                                    jsonViewModel.getErrorTitleByLangValues(), msg);
                        }
                    });
                }
            }
        }).start();
    }

    private void flagCloudBackupSaved() {
        if (backupCloudStatusTextView == null) return;
        String msg = jsonViewModel.getBackupSavedShortByLangValues();
        backupCloudStatusTextView.setText(msg);
        backupCloudStatusTextView.setVisibility(View.VISIBLE);
        GlobalMethods.ShowMessageDialog(getContext(), null,
                msg != null && !msg.isEmpty() ? msg : "Saved", null);
    }

    private void flagFileBackupSaved() {
        if (backupFileStatusTextView == null) return;
        String msg = jsonViewModel.getBackupSavedShortByLangValues();
        backupFileStatusTextView.setText(msg);
        backupFileStatusTextView.setVisibility(View.VISIBLE);
        GlobalMethods.ShowMessageDialog(getContext(), null,
                msg != null && !msg.isEmpty() ? msg : "Saved", null);
    }

    private void finishBackupAndNavigateToHome() {
        final String indexKey = walletIndexKey;
        android.app.Activity act = getActivity();
        if (act instanceof com.quantumcoinwallet.app.view.activities.HomeActivity) {
            ((com.quantumcoinwallet.app.view.activities.HomeActivity) act)
                    .requirePasswordReentryThenNavigate(new Runnable() {
                        @Override
                        public void run() {
                            if (mHomeWalletListener != null) {
                                mHomeWalletListener.onHomeWalletCompleteByHomeMain(indexKey);
                            }
                        }
                    });
        } else if (mHomeWalletListener != null) {
            mHomeWalletListener.onHomeWalletCompleteByHomeMain(indexKey);
        }
    }

    private void WalletTypeView(TextView titleTextView, TextView descriptionTextView,
                                RadioButton defaultRadioButton, RadioButton advancedRadioButton,
                                Button nextButton) {
        titleTextView.setText(jsonViewModel.getSelectWalletTypeByLangValues());
        descriptionTextView.setText(jsonViewModel.getSelectAnOptionByLangValues());
        defaultRadioButton.setText(jsonViewModel.getWalletTypeDefaultByLangValues());
        advancedRadioButton.setText(jsonViewModel.getWalletTypeAdvancedByLangValues());
        defaultRadioButton.setTag(0);
        advancedRadioButton.setTag(1);
        nextButton.setText(jsonViewModel.getNextByLangValues());
    }

    private void SeedWordLengthView(TextView titleTextView, TextView descriptionTextView,
                                    RadioButton radio32, RadioButton radio36, RadioButton radio48,
                                    Button nextButton) {
        titleTextView.setText(jsonViewModel.getSelectSeedWordLengthByLangValues());
        descriptionTextView.setText(jsonViewModel.getSelectAnOptionByLangValues());
        radio32.setText(jsonViewModel.getSeedLength32ByLangValues());
        radio36.setText(jsonViewModel.getSeedLength36ByLangValues());
        radio48.setText(jsonViewModel.getSeedLength48ByLangValues());
        radio32.setTag(32);
        radio36.setTag(36);
        radio48.setTag(48);
        nextButton.setText(jsonViewModel.getNextByLangValues());
    }

    private TextView[] HomeSeedWordsEditCaptionTextViews() {
        return new TextView[]{
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_a1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_a2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_a3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_a4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_b1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_b2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_b3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_b4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_c1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_c2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_c3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_c4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_d1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_d2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_d3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_d4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_e1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_e2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_e3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_e4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_f1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_f2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_f3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_f4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_g1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_g2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_g3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_g4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_h1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_h2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_h3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_h4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_i1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_i2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_i3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_i4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_j1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_j2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_j3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_j4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_k1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_k2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_k3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_k4),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_l1),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_l2),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_l3),
                (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_caption_l4)
        };
    }

    private void updateSeedRowVisibility(TextView[] viewCaptions, TextView[] viewValues,
                                         TextView[] editCaptions, AutoCompleteTextView[] editFields,
                                         int wordCount) {
        int totalRows = wordCount / 4;
        for (int row = 0; row < 12; row++) {
            int visibility = (row < totalRows) ? View.VISIBLE : View.GONE;
            int idx = row * 4;
            if (idx < viewCaptions.length) {
                ((View) viewCaptions[idx].getParent()).setVisibility(visibility);
            }
            if (idx < viewValues.length) {
                ((View) viewValues[idx].getParent()).setVisibility(visibility);
            }
            if (idx < editCaptions.length) {
                ((View) editCaptions[idx].getParent()).setVisibility(visibility);
            }
            if (idx < editFields.length) {
                ((View) editFields[idx].getParent()).setVisibility(visibility);
            }
        }
    }

    private boolean CheckSeed(@NonNull TextView[] textViews, EditText[] editTexts, int index) {
        if (textViews[index].getText() != editTexts[index].getText()) {
            return false;
        }
        return true;
    }

    private void startRestoreFromFileFlow() {
        try {
            setSuppressNextResumeLock(true);
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(com.quantumcoinwallet.app.backup.CloudBackupManager.BACKUP_MIME_TYPE);
            restoreFilePickerLauncher.launch(intent);
        } catch (Exception e) {
            setSuppressNextResumeLock(false);
            GlobalMethods.ExceptionError(getContext(), TAG, e);
        }
    }

    private void startRestoreFromCloudFlow() {
        String folderUriStr = PrefConnect.readString(getContext(),
                PrefConnect.CLOUD_BACKUP_FOLDER_URI_KEY, "");
        if (folderUriStr == null || folderUriStr.isEmpty()) {
            try {
                setSuppressNextResumeLock(true);
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                restoreCloudFolderPickerLauncher.launch(intent);
            } catch (Exception e) {
                setSuppressNextResumeLock(false);
                GlobalMethods.ExceptionError(getContext(), TAG, e);
            }
            return;
        }
        showRestoreCloudFilePicker(Uri.parse(folderUriStr));
    }

    /** First restored wallet's index key, captured during the batched restore pass so the
     *  final summary dialog can hand it to the home listener when the user acknowledges. */
    private String firstRestoredIndexKey = null;

    private void showRestoreCloudFilePicker(final Uri folderUri) {
        final Context ctx = getContext();
        if (ctx == null) return;
        final ProgressBar progressBar = (ProgressBar) getView().findViewById(R.id.progress_loader_home_wallet);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<com.quantumcoinwallet.app.backup.CloudBackupManager.BackupCandidate> candidates =
                        com.quantumcoinwallet.app.backup.CloudBackupManager
                                .scanQualifyingBackups(ctx, folderUri);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        if (candidates.isEmpty()) {
                            GlobalMethods.ShowErrorDialog(getContext(),
                                    jsonViewModel.getErrorTitleByLangValues(),
                                    jsonViewModel.getRestoreNoBackupsFoundByLangValues());
                            return;
                        }
                        firstRestoredIndexKey = null;
                        final List<com.quantumcoinwallet.app.backup.CloudBackupManager.BackupCandidate> pending =
                                new ArrayList<>();
                        final List<String> restored = new ArrayList<>();
                        final List<String> alreadyExists = new ArrayList<>();
                        final List<String> skipped = new ArrayList<>();
                        for (com.quantumcoinwallet.app.backup.CloudBackupManager.BackupCandidate c : candidates) {
                            if (c.address != null
                                    && PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP != null
                                    && PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.containsKey(c.address)) {
                                alreadyExists.add(c.address);
                            } else {
                                pending.add(c);
                            }
                        }
                        // When every scanned file is already imported, skip the password
                        // dialog entirely and jump straight to the summary.
                        runBatchedRestorePass(pending, restored, alreadyExists, skipped);
                    }
                });
            }
        }).start();
    }

    /** Core batched-restore loop. Base case: pending is empty -> show summary. Otherwise
     *  open a fresh password dialog listing the remaining pending addresses and hand off
     *  to {@link #attemptBatchDecrypt} for the actual decrypt work. The dialog stays on
     *  screen while decryption runs; the attempt either dismisses it + re-enters this
     *  loop with a shrunken pending list, or re-enables it for the user to retry the
     *  same dialog with a different password. Cancel moves the remaining pending
     *  addresses into {@code skipped} and goes straight to the summary. */
    private void runBatchedRestorePass(
            final List<com.quantumcoinwallet.app.backup.CloudBackupManager.BackupCandidate> pending,
            final List<String> restored,
            final List<String> alreadyExists,
            final List<String> skipped) {
        if (getContext() == null) return;
        if (pending.isEmpty()) {
            showRestoreSummaryDialog(restored, alreadyExists, skipped);
            return;
        }
        final List<String> remainingAddresses = new ArrayList<>();
        for (com.quantumcoinwallet.app.backup.CloudBackupManager.BackupCandidate c : pending) {
            remainingAddresses.add(c.address);
        }
        com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog.showRestoreBatch(
                getContext(), jsonViewModel, remainingAddresses,
                new com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog.OnBatchPasswordListener() {
                    @Override
                    public void onPassword(String password,
                                           com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog.BatchDialogControl control) {
                        attemptBatchDecrypt(pending, restored, alreadyExists, skipped, password, control);
                    }
                    @Override
                    public void onCanceled() {
                        for (com.quantumcoinwallet.app.backup.CloudBackupManager.BackupCandidate c : pending) {
                            skipped.add(c.address);
                        }
                        pending.clear();
                        showRestoreSummaryDialog(restored, alreadyExists, skipped);
                    }
                });
    }

    private void attemptBatchDecrypt(
            final List<com.quantumcoinwallet.app.backup.CloudBackupManager.BackupCandidate> pending,
            final List<String> restored,
            final List<String> alreadyExists,
            final List<String> skipped,
            final String password,
            final com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog.BatchDialogControl control) {
        final Context ctx = getContext();
        if (ctx == null) return;
        final int before = pending.size();
        final int restoredBefore = restored.size();
        final String overlayTitle = jsonViewModel.getWaitWalletOpenByLangValues();
        final com.quantumcoinwallet.app.view.dialog.WaitDialog.Handle overlay =
                com.quantumcoinwallet.app.view.dialog.WaitDialog.showWithDetails(
                        ctx, overlayTitle == null ? "" : overlayTitle);
        final String progressTemplate = jsonViewModel.getRestoreProgressOfByLangValues();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SecureStorage secureStorage = KeyViewModel.getSecureStorage();
                    if (secureStorage == null) {
                        throw new IllegalStateException("SecureStorage unavailable");
                    }
                    // Mirror the create-wallet flow (HomeWalletFragment L1097-1104): on a
                    // fresh install the restore screen is the first touch of SecureStorage,
                    // so the backup password also bootstraps the app's main key. If
                    // SecureStorage is already initialized but locked (e.g. opened from
                    // the "add wallet" path after an idle timeout) try to unlock with the
                    // same password so the iteration below has a usable key.
                    if (!secureStorage.isInitialized(getContext())) {
                        secureStorage.createMainKey(getContext(), password);
                    }
                    if (!secureStorage.isUnlocked()) {
                        secureStorage.unlock(getContext(), password);
                    }
                    if (!secureStorage.isUnlocked()) {
                        // Wrong app-level password: keep every entry in pending, dismiss
                        // the overlay, surface the "try a different password" error, and
                        // then re-enable the password dialog for another attempt.
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    overlay.dismiss();
                                    GlobalMethods.ShowMessageDialog(getContext(),
                                            jsonViewModel.getErrorTitleByLangValues(),
                                            jsonViewModel.getRestoreTryDifferentPasswordByLangValues(),
                                            new Runnable() {
                                                @Override
                                                public void run() { control.reEnable(); }
                                            });
                                }
                            });
                        }
                        return;
                    }
                    int attemptIndex = 0;
                    Iterator<com.quantumcoinwallet.app.backup.CloudBackupManager.BackupCandidate> it =
                            pending.iterator();
                    while (it.hasNext()) {
                        final com.quantumcoinwallet.app.backup.CloudBackupManager.BackupCandidate c = it.next();
                        final int currentIndex = ++attemptIndex;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    overlay.setAddress(c.address);
                                    String tmpl = progressTemplate;
                                    String text;
                                    if (tmpl == null || tmpl.isEmpty()) {
                                        text = currentIndex + " of " + before;
                                    } else {
                                        text = tmpl
                                                .replace("[CURRENT]", String.valueOf(currentIndex))
                                                .replace("[TOTAL]", String.valueOf(before));
                                    }
                                    overlay.setProgress(text);
                                }
                            });
                        }
                        com.quantumcoinwallet.app.backup.CloudBackupManager.DecryptedWallet dw;
                        try {
                            dw = com.quantumcoinwallet.app.backup.CloudBackupManager
                                    .decryptWallet(c.encryptedJson, password);
                        } catch (Exception decryptErr) {
                            continue;
                        }
                        if (dw == null || dw.address == null) continue;

                        if (PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP != null
                                && PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.containsKey(dw.address)) {
                            alreadyExists.add(dw.address);
                            it.remove();
                            continue;
                        }

                        int newIndex = secureStorage.getMaxWalletIndex(getContext()) + 1;
                        String indexKey = String.valueOf(newIndex);

                        JSONObject walletJson = new JSONObject();
                        walletJson.put("address", dw.address);
                        if (dw.privateKey != null) walletJson.put("privateKey", dw.privateKey);
                        if (dw.publicKey != null) walletJson.put("publicKey", dw.publicKey);
                        String seedJoined = "";
                        if (dw.seedWords != null && dw.seedWords.length > 0) {
                            seedJoined = android.text.TextUtils.join(",", dw.seedWords);
                        } else if (dw.seed != null) {
                            seedJoined = dw.seed;
                        }
                        walletJson.put("seed", seedJoined);

                        secureStorage.saveWallet(getContext(), newIndex, walletJson.toString());
                        secureStorage.setMaxWalletIndex(getContext(), newIndex);

                        PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.put(dw.address, indexKey);
                        PrefConnect.WALLET_INDEX_TO_ADDRESS_MAP.put(indexKey, dw.address);
                        boolean hasSeed = !seedJoined.isEmpty();
                        PrefConnect.writeBoolean(getContext(),
                                PrefConnect.WALLET_HAS_SEED_KEY_PREFIX + indexKey, hasSeed);
                        PrefConnect.WALLET_INDEX_HAS_SEED_MAP.put(indexKey, hasSeed);

                        if (firstRestoredIndexKey == null) firstRestoredIndexKey = indexKey;
                        restored.add(dw.address);
                        it.remove();
                    }
                } catch (Exception e) {
                    timber.log.Timber.e(e, "Batched restore failed");
                }
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        overlay.dismiss();
                        markActivityUnlocked();
                        if (pending.size() < before) {
                            // At least one wallet was moved out of pending this pass.
                            final int restoredDelta = restored.size() - restoredBefore;
                            if (!pending.isEmpty() && restoredDelta > 0) {
                                // Partial progress with more to go: acknowledge the
                                // count and only then reopen the password dialog.
                                String tmpl = jsonViewModel.getRestorePartialProgressByLangValues();
                                String msg;
                                if (tmpl == null || tmpl.isEmpty()) {
                                    msg = restoredDelta + " wallet(s) were restored. Enter password for the remaining.";
                                } else {
                                    msg = tmpl.replace("[COUNT]", String.valueOf(restoredDelta));
                                }
                                GlobalMethods.ShowMessageDialog(getContext(),
                                        jsonViewModel.getBackupByLangValues(),
                                        msg,
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                control.dismiss();
                                                runBatchedRestorePass(pending, restored, alreadyExists, skipped);
                                            }
                                        });
                            } else {
                                // Either everything finished (pending empty, go to
                                // summary) or progress came purely from duplicates
                                // being moved to alreadyExists: reopen silently.
                                control.dismiss();
                                runBatchedRestorePass(pending, restored, alreadyExists, skipped);
                            }
                        } else {
                            // Nothing decrypted: keep the same password dialog on screen
                            // and surface a modal "try a different password" message.
                            GlobalMethods.ShowMessageDialog(getContext(),
                                    jsonViewModel.getErrorTitleByLangValues(),
                                    jsonViewModel.getRestoreTryDifferentPasswordByLangValues(),
                                    new Runnable() {
                                        @Override
                                        public void run() { control.reEnable(); }
                                    });
                        }
                    }
                });
            }
        }).start();
    }

    private void showRestoreSummaryDialog(final List<String> restored,
                                          final List<String> alreadyExists,
                                          final List<String> skipped) {
        if (getContext() == null) return;
        final Context ctx = getContext();
        final int pad = (int) (16 * ctx.getResources().getDisplayMetrics().density);

        ScrollView scroll = new ScrollView(ctx);
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, pad);

        TableLayout table = new TableLayout(ctx);
        table.setStretchAllColumns(true);
        table.setColumnShrinkable(1, true);

        TableRow headerRow = new TableRow(ctx);
        TextView statusHeader = new TextView(ctx);
        String statusCol = jsonViewModel.getRestoreSummaryStatusColumnByLangValues();
        statusHeader.setText(statusCol != null && !statusCol.isEmpty() ? statusCol : "Status");
        statusHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        statusHeader.setPadding(0, 0, pad, (int) (pad * 0.5f));
        TextView addressHeader = new TextView(ctx);
        String addrCol = jsonViewModel.getRestoreSummaryAddressColumnByLangValues();
        addressHeader.setText(addrCol != null && !addrCol.isEmpty() ? addrCol : "Address");
        addressHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        addressHeader.setPadding(0, 0, 0, (int) (pad * 0.5f));
        headerRow.addView(statusHeader);
        headerRow.addView(addressHeader);
        table.addView(headerRow);

        String restoredLabel = jsonViewModel.getRestoreSummaryStatusRestoredByLangValues();
        if (restoredLabel == null || restoredLabel.isEmpty()) restoredLabel = "Restored";
        String alreadyExistsLabel = jsonViewModel.getRestoreSummaryStatusAlreadyExistsByLangValues();
        if (alreadyExistsLabel == null || alreadyExistsLabel.isEmpty()) alreadyExistsLabel = "Already exists";
        String skippedLabel = jsonViewModel.getRestoreSummaryStatusSkippedByLangValues();
        if (skippedLabel == null || skippedLabel.isEmpty()) skippedLabel = "Skipped";

        for (String addr : restored) {
            table.addView(buildSummaryRow(ctx, restoredLabel, addr, pad));
        }
        if (alreadyExists != null) {
            for (String addr : alreadyExists) {
                table.addView(buildSummaryRow(ctx, alreadyExistsLabel, addr, pad));
            }
        }
        for (String addr : skipped) {
            table.addView(buildSummaryRow(ctx, skippedLabel, addr, pad));
        }

        container.addView(table);
        scroll.addView(container);

        String title = jsonViewModel.getRestoreFromCloudByLangValues();
        String ok = jsonViewModel.getOkByLangValues();
        new AlertDialog.Builder(ctx)
                .setTitle(title != null && !title.isEmpty() ? title : "Restore from cloud")
                .setView(scroll)
                .setCancelable(false)
                .setPositiveButton(ok != null && !ok.isEmpty() ? ok : "OK",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (!restored.isEmpty() && firstRestoredIndexKey != null
                                        && mHomeWalletListener != null) {
                                    mHomeWalletListener.onHomeWalletCompleteByHomeMain(
                                            firstRestoredIndexKey);
                                }
                            }
                        })
                .show();
    }

    private TableRow buildSummaryRow(Context ctx, String status, String address, int pad) {
        TableRow row = new TableRow(ctx);
        TextView statusView = new TextView(ctx);
        statusView.setText(status);
        statusView.setPadding(0, (int) (pad * 0.25f), pad, (int) (pad * 0.25f));
        TextView addressView = new TextView(ctx);
        addressView.setText(address == null ? "" : address);
        addressView.setTypeface(android.graphics.Typeface.MONOSPACE);
        addressView.setTextSize(12);
        addressView.setPadding(0, (int) (pad * 0.25f), 0, (int) (pad * 0.25f));
        row.addView(statusView);
        row.addView(addressView);
        return row;
    }

    private void handleRestoreFromUri(final Uri fileUri) {
        final ProgressBar progressBar = (ProgressBar) getView().findViewById(R.id.progress_loader_home_wallet);
        com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog.showRestore(getContext(), jsonViewModel,
                new com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog.OnPasswordAttemptListener() {
                    @Override
                    public void onAttempt(String password,
                                          com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog.PasswordDialogControl control) {
                        performRestoreFromUri(fileUri, password, progressBar, control);
                    }
                    @Override
                    public void onCanceled() { }
                });
    }

    private void performRestoreFromUri(final Uri fileUri, final String backupPassword,
                                       final ProgressBar progressBar,
                                       final com.quantumcoinwallet.app.view.dialog.BackupPasswordDialog.PasswordDialogControl control) {
        final AlertDialog waitDlg = com.quantumcoinwallet.app.view.dialog.WaitDialog
                .show(getContext(), jsonViewModel.getWaitWalletOpenByLangValues());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String encryptedJson = com.quantumcoinwallet.app.backup.CloudBackupManager
                            .readSafFile(getContext(), fileUri);
                    final com.quantumcoinwallet.app.backup.CloudBackupManager.DecryptedWallet dw =
                            com.quantumcoinwallet.app.backup.CloudBackupManager
                                    .decryptWallet(encryptedJson, backupPassword);
                    if (dw == null || dw.address == null) {
                        throw new IllegalStateException("decrypt returned empty");
                    }
                    if (PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP != null
                            && PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.containsKey(dw.address)) {
                        final String existingAddress = dw.address;
                        getActivity().runOnUiThread(new Runnable() {
                            public void run() {
                                if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                                if (control != null) control.dismiss();
                                showDuplicateWalletDialog(existingAddress);
                            }
                        });
                        return;
                    }
                    SecureStorage secureStorage = KeyViewModel.getSecureStorage();
                    // On a fresh install the file-restore screen is the first touch of
                    // SecureStorage, so the backup password also bootstraps the app's
                    // main key (mirrors create-wallet at L1097-1104 and the cloud-restore
                    // path in attemptBatchDecrypt). Skipped on subsequent restores when
                    // SecureStorage is already initialized + unlocked.
                    if (!secureStorage.isInitialized(getContext())) {
                        secureStorage.createMainKey(getContext(), backupPassword);
                    }
                    if (!secureStorage.isUnlocked()) {
                        secureStorage.unlock(getContext(), backupPassword);
                    }
                    if (!secureStorage.isUnlocked()) {
                        throw new IllegalStateException("SecureStorage is locked");
                    }
                    int newIndex = secureStorage.getMaxWalletIndex(getContext()) + 1;
                    final String indexKey = String.valueOf(newIndex);

                    JSONObject walletJson = new JSONObject();
                    walletJson.put("address", dw.address);
                    if (dw.privateKey != null) walletJson.put("privateKey", dw.privateKey);
                    if (dw.publicKey != null) walletJson.put("publicKey", dw.publicKey);
                    String seedJoined = "";
                    if (dw.seedWords != null && dw.seedWords.length > 0) {
                        seedJoined = android.text.TextUtils.join(",", dw.seedWords);
                    } else if (dw.seed != null) {
                        seedJoined = dw.seed;
                    }
                    walletJson.put("seed", seedJoined);

                    secureStorage.saveWallet(getContext(), newIndex, walletJson.toString());
                    secureStorage.setMaxWalletIndex(getContext(), newIndex);

                    PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.put(dw.address, indexKey);
                    PrefConnect.WALLET_INDEX_TO_ADDRESS_MAP.put(indexKey, dw.address);
                    boolean hasSeed = seedJoined != null && !seedJoined.isEmpty();
                    PrefConnect.writeBoolean(getContext(),
                            PrefConnect.WALLET_HAS_SEED_KEY_PREFIX + indexKey, hasSeed);
                    PrefConnect.WALLET_INDEX_HAS_SEED_MAP.put(indexKey, hasSeed);

                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                            if (control != null) control.dismiss();
                            markActivityUnlocked();
                            if (mHomeWalletListener != null) {
                                mHomeWalletListener.onHomeWalletCompleteByHomeMain(indexKey);
                            }
                        }
                    });
                } catch (final Exception e) {
                    android.util.Log.e(TAG, "Restore decrypt failed", e);
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            if (waitDlg != null) try { waitDlg.dismiss(); } catch (Throwable ignore) { }
                            if (control != null) control.onFailure();
                            String tmpl = jsonViewModel.getRestoreDecryptFailedByLangValues();
                            String msg = (tmpl != null && !tmpl.isEmpty())
                                    ? tmpl
                                    : "Unable to decrypt. Enter a different password or skip this file.";
                            GlobalMethods.ShowErrorDialog(getContext(),
                                    jsonViewModel.getErrorTitleByLangValues(), msg);
                        }
                    });
                }
            }
        }).start();
    }

    private void showDuplicateWalletDialog(String address) {
        if (getContext() == null || address == null) return;
        String tmpl = jsonViewModel.getWalletAlreadyExistsDetailedByLangValues();
        String msg = tmpl != null && !tmpl.isEmpty()
                ? tmpl.replace("[ADDRESS]", address)
                : "The wallet with following address already exists:\n" + address;
        String ok = jsonViewModel.getOkByLangValues();
        new AlertDialog.Builder(getContext())
                .setMessage(msg)
                .setPositiveButton(ok != null && !ok.isEmpty() ? ok : "OK", null)
                .setCancelable(true)
                .show();
    }
}