package com.quantumcoinwallet.app.view.fragment;

import static android.content.Context.CLIPBOARD_SERVICE;
import static androidx.core.content.ContextCompat.getSystemService;

import android.app.AlertDialog;
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
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.keystorage.SecureStorage;
import com.quantumcoinwallet.app.utils.GlobalMethods;
import com.quantumcoinwallet.app.utils.PrefConnect;
import com.quantumcoinwallet.app.viewmodel.JsonViewModel;
import com.quantumcoinwallet.app.viewmodel.KeyViewModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
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
    private Runnable pendingCloudContinuation;

    public static HomeWalletFragment newInstance() {
        HomeWalletFragment fragment = new HomeWalletFragment();
        return fragment;
    }

    public HomeWalletFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cloudFolderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
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
                                PrefConnect.writeBoolean(getContext(),
                                        PrefConnect.CLOUD_BACKUP_ENABLED_KEY, true);
                            } else {
                                PrefConnect.writeBoolean(getContext(),
                                        PrefConnect.CLOUD_BACKUP_ENABLED_KEY, false);
                            }
                        } finally {
                            if (continuation != null) continuation.run();
                        }
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.home_wallet_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        walletPassword = getArguments().getString("walletPassword");
        getArguments().remove("walletPassword");
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
        Button homeCreateRestoreWalletNextButton = (Button) getView().findViewById(R.id.button_home_create_restore_wallet_next);

        LinearLayout homeWalletTypeLinearLayout = (LinearLayout) getView().findViewById(R.id.linear_layout_home_wallet_type);
        TextView homeWalletTypeTitleTextView = (TextView) getView().findViewById(R.id.textView_home_wallet_type_title);
        TextView homeWalletTypeDescriptionTextView = (TextView) getView().findViewById(R.id.textView_home_wallet_type_description);
        RadioGroup homeWalletTypeRadioGroup = (RadioGroup) getView().findViewById(R.id.radioGroup_home_wallet_type);
        RadioButton homeWalletTypeDefaultRadioButton = (RadioButton) getView().findViewById(R.id.radioButton_home_wallet_type_default);
        RadioButton homeWalletTypeAdvancedRadioButton = (RadioButton) getView().findViewById(R.id.radioButton_home_wallet_type_advanced);
        Button homeWalletTypeNextButton = (Button) getView().findViewById(R.id.button_home_wallet_type_next);

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
        TextView homeSeedWordsEditTitleTextView = (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_title);
        TextView homeSeedWordsEditSkip = (TextView) getView().findViewById(R.id.textView_home_seed_words_edit_skip);
        TextView[] homeSeedWordsEditCaptionTextViews = HomeSeedWordsEditCaptionTextViews();
        homeSeedWordsViewAutoCompleteTextViews = HomeSeedWordsViewAutoCompleteTextView();

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

        if (walletPassword==null || walletPassword.isEmpty()) {
            homeSetWalletLinearLayout.setVisibility(View.VISIBLE);
            SetWalletView(homeSetWalletTitleTextView, homeSetWalletDescriptionTextView, homeSetWalletPasswordTitleTextView,
                    homeSetWalletPasswordEditText, homeSetWalletRetypePasswordTitleTextView, homeSetWalletRetypePasswordEditText, homeSetWalletNextButton);
        } else {
            homeSetWalletLinearLayout.setVisibility(View.GONE);
            homeSetWalletTopLinearLayout.setVisibility(View.VISIBLE);
            homeCreateRestoreWalletLinearLayout.setVisibility(View.VISIBLE);
            CreateRestoreWalletView(homeCreateRestoreWalletTitleTextView, homeCreateRestoreWalletDescriptionTextView, homeCreateRestoreWalletRadioButton_0,
                    homeCreateRestoreWalletRadioButton_1, homeCreateRestoreWalletNextButton);
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
                                homeCreateRestoreWalletRadioButton_1, homeCreateRestoreWalletNextButton);
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
                    if (homeCreateRestoreWalletLinearLayout.getVisibility()==View.VISIBLE) {
                        if (walletPassword==null || walletPassword.isEmpty()) {
                            homeCreateRestoreWalletLinearLayout.setVisibility(View.GONE);
                            homeSetWalletTopLinearLayout.setVisibility(View.GONE);
                            homeSetWalletLinearLayout.setVisibility(View.VISIBLE);
                        } else {
                            mHomeWalletListener.onHomeWalletCompleteByWallets();
                        }
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
                            homeCreateRestoreWalletLinearLayout.setVisibility(View.GONE);
                            homeWalletTypeLinearLayout.setVisibility(View.VISIBLE);
                            WalletTypeView(homeWalletTypeTitleTextView, homeWalletTypeDescriptionTextView,
                                    homeWalletTypeDefaultRadioButton, homeWalletTypeAdvancedRadioButton, homeWalletTypeNextButton);
                        }
                    };
                    showBackupPromptIfNeeded(proceed);
                } else if (homeCreateRestoreWalletRadioButton_1.isChecked() == true) {
                    tempSeedWords = null;
                    Runnable proceed = new Runnable() {
                        @Override
                        public void run() {
                            homeCreateRestoreWalletLinearLayout.setVisibility(View.GONE);
                            homeSeedWordLengthLinearLayout.setVisibility(View.VISIBLE);
                            SeedWordLengthView(homeSeedWordLengthTitleTextView, homeSeedWordLengthDescriptionTextView,
                                    homeSeedWordLength32RadioButton, homeSeedWordLength36RadioButton, homeSeedWordLength48RadioButton,
                                    homeSeedWordLengthNextButton);
                        }
                    };
                    showBackupPromptIfNeeded(proceed);
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
                ShowEditSeedScreen(homeSeedWordsEditTitleTextView, homeSeedWordsAutoCompleteNextButton);
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
                                        ArrayList<String> seedWordsList = GlobalMethods.seedWords.getAllSeedWords();
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
                ClipboardManager clipBoard = (ClipboardManager) getActivity().getSystemService(getActivity().CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText("walletSeed", clipboardCopyData);
                clipBoard.setPrimaryClip(clipData);
                progressBar.setVisibility(View.GONE);
                homeSeedWordsViewCopied.setVisibility(View.VISIBLE);
                new Handler().postDelayed(new Runnable() {
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
                ShowEditSeedScreen(homeSeedWordsEditTitleTextView, homeSeedWordsAutoCompleteNextButton);

                homeSeedWordsViewAutoCompleteTextViews[autoCompleteCurrentIndex].requestFocus();

                ArrayList<String> seedWordsList = GlobalMethods.seedWords.getAllSeedWords();
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
                        if (!GlobalMethods.seedWords.doesSeedWordExist(actv.getText().toString())) {
                            actv.setText("");
                            return;
                        }
                        if (tempSeedWords != null && !actv.getText().toString().equalsIgnoreCase(tempSeedWords[i])) {
                            actv.setText("");
                            return;
                        }
                        seedWords[i] = actv.getText().toString().toLowerCase();
                    }

                    saveWalletFromSeedWords(seedWords, progressBar);

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
                                         Button createRestoreWalletNextButton) {

        createRestoreWalletTitleTextView.setText(jsonViewModel.getCreateRestoreWalletByLangValues());
        createRestoreWalletDescriptionTextView.setText(jsonViewModel.getSelectAnOptionByLangValues());

        createRestoreWalletRadioButton_0.setText(jsonViewModel.getCreateNewWalletByLangValues());
        createRestoreWalletRadioButton_1.setText(jsonViewModel.getRestoreWalletFromSeedByLangValues());

        createRestoreWalletRadioButton_0.setTag(0);
        createRestoreWalletRadioButton_1.setTag(1);

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

    private void ShowEditSeedScreen(TextView homeSeedWordsEditTitleTextView, Button homeSeedWordsEditNextButton) {
        homeSeedWordsEditTitleTextView.setText(jsonViewModel.getVerifySeedWordsByLangValues());
        homeSeedWordsEditNextButton.setText(jsonViewModel.getNextByLangValues());
    }

    private void saveWalletFromSeedWords(final String[] seedWords, final ProgressBar progressBar) {
        progressBar.setVisibility(View.VISIBLE);
        if (walletPassword == null || walletPassword.isEmpty()) {
            progressBar.setVisibility(View.GONE);
            GlobalMethods.ShowErrorDialog(getContext(), "Error", "Wallet password is not set.");
            return;
        }
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

                    PrefConnect.WALLET_ADDRESS_TO_INDEX_MAP.put(address, walletIndexKey);
                    PrefConnect.WALLET_INDEX_TO_ADDRESS_MAP.put(walletIndexKey, address);
                    PrefConnect.writeBoolean(getContext(),
                            PrefConnect.WALLET_HAS_SEED_KEY_PREFIX + walletIndexKey, true);
                    PrefConnect.WALLET_INDEX_HAS_SEED_MAP.put(walletIndexKey, true);

                    com.quantumcoinwallet.app.backup.CloudBackupManager
                            .autoBackupToCloudIfEnabled(getContext(), walletJson, walletPassword);

                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            mHomeWalletListener.onHomeWalletCompleteByHomeMain(walletIndexKey);
                        }
                    });
                } catch (Exception e) {
                    final String errorMsg = e.getMessage();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            public void run() {
                                progressBar.setVisibility(View.GONE);
                                GlobalMethods.ShowErrorDialog(getContext(), "Error", errorMsg);
                            }
                        });
                    }
                }
            }
        }).start();
    }

    private void showBackupPromptIfNeeded(final Runnable onComplete) {
        Runnable cloudStep = new Runnable() {
            @Override
            public void run() {
                showCloudBackupPromptIfNeeded(onComplete);
            }
        };

        boolean alreadyPrompted = getContext().getSharedPreferences(
                PrefConnect.PREF_NAME, Context.MODE_PRIVATE).contains(PrefConnect.BACKUP_ENABLED_KEY);
        if (alreadyPrompted) {
            cloudStep.run();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(jsonViewModel.getBackupByLangValues());
        builder.setMessage(jsonViewModel.getBackupPromptByLangValues());
        builder.setCancelable(false);
        builder.setPositiveButton(jsonViewModel.getYesByLangValues(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                PrefConnect.writeBoolean(getContext(), PrefConnect.BACKUP_ENABLED_KEY, true);
                dialog.dismiss();
                cloudStep.run();
            }
        });
        builder.setNegativeButton(jsonViewModel.getNoByLangValues(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                PrefConnect.writeBoolean(getContext(), PrefConnect.BACKUP_ENABLED_KEY, false);
                dialog.dismiss();
                cloudStep.run();
            }
        });
        builder.show();
    }

    private void showCloudBackupPromptIfNeeded(final Runnable onComplete) {
        boolean alreadyPrompted = getContext().getSharedPreferences(
                PrefConnect.PREF_NAME, Context.MODE_PRIVATE).contains(PrefConnect.CLOUD_BACKUP_ENABLED_KEY);
        if (alreadyPrompted) {
            onComplete.run();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(jsonViewModel.getCloudBackupByLangValues());
        builder.setMessage(jsonViewModel.getCloudBackupPromptByLangValues());
        builder.setCancelable(false);
        builder.setPositiveButton(jsonViewModel.getYesByLangValues(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                pendingCloudContinuation = onComplete;
                launchFolderPicker();
            }
        });
        builder.setNegativeButton(jsonViewModel.getNoByLangValues(), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                PrefConnect.writeBoolean(getContext(), PrefConnect.CLOUD_BACKUP_ENABLED_KEY, false);
                dialog.dismiss();
                onComplete.run();
            }
        });
        builder.show();
    }

    private void launchFolderPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            cloudFolderPickerLauncher.launch(intent);
        } catch (Exception e) {
            PrefConnect.writeBoolean(getContext(), PrefConnect.CLOUD_BACKUP_ENABLED_KEY, false);
            Runnable continuation = pendingCloudContinuation;
            pendingCloudContinuation = null;
            if (continuation != null) continuation.run();
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
}