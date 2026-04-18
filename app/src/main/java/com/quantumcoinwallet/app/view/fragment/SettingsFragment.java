package com.quantumcoinwallet.app.view.fragment;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.backup.CloudBackupManager;
import com.quantumcoinwallet.app.utils.GlobalMethods;
import com.quantumcoinwallet.app.utils.PrefConnect;
import com.quantumcoinwallet.app.viewmodel.JsonViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;

public class SettingsFragment extends Fragment  {

    private static final String TAG = "SettingsFragment";

    private LinearLayout linerLayoutOffline;
    private ImageView imageViewRetry;
    private TextView textViewTitleRetry;
    private TextView textViewSubTitleRetry;


    private OnSettingsCompleteListener mSettingsListener;

    private ActivityResultLauncher<Intent> folderPickerLauncher;
    private ActivityResultLauncher<Intent> restoreFilePickerLauncher;
    private Runnable pendingFolderSelectedCallback;
    private JsonViewModel cachedJsonViewModel;

    public static SettingsFragment newInstance() {
        SettingsFragment fragment = new SettingsFragment();
        return fragment;
    }

    public SettingsFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        Runnable cb = pendingFolderSelectedCallback;
                        pendingFolderSelectedCallback = null;
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
                        if (cb != null) cb.run();
                    }
                });
        restoreFilePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result == null || result.getResultCode() != android.app.Activity.RESULT_OK
                                || result.getData() == null) return;
                        List<Uri> uris = new ArrayList<>();
                        Intent data = result.getData();
                        if (data.getClipData() != null) {
                            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                                Uri u = data.getClipData().getItemAt(i).getUri();
                                if (u != null) uris.add(u);
                            }
                        } else if (data.getData() != null) {
                            uris.add(data.getData());
                        }
                        if (!uris.isEmpty()) {
                            List<String> names = new ArrayList<>();
                            for (Uri u : uris) {
                                DocumentFile df = DocumentFile.fromSingleUri(getContext(), u);
                                names.add(df != null && df.getName() != null ? df.getName()
                                        : u.getLastPathSegment());
                            }
                            startRestoreLoop(uris, names);
                        }
                    }
                });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.settings_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String languageKey = getArguments().getString("languageKey");

        JsonViewModel jsonViewModel = new JsonViewModel(getContext(), languageKey);

        ImageButton backArrowImageButton = (ImageButton) getView().findViewById(R.id.imageButton_setting_back_arrow);
        TextView settings = (TextView) getView().findViewById(R.id.textview_settings_langValues_settings);
        Button buttonNetworks = (Button) getView().findViewById(R.id.button_settings_langValues_networks);
        Button buttonSigning = (Button) getView().findViewById(R.id.button_settings_langValues_signing);
        Button buttonBackup = (Button) getView().findViewById(R.id.button_settings_langValues_backup);

        settings.setText(jsonViewModel.getSettingsByLangValues());
        buttonNetworks.setText(jsonViewModel.getNetworksByLangValues());
        buttonSigning.setText(jsonViewModel.getSigningByLangValues());
        buttonBackup.setText(jsonViewModel.getBackupByLangValues());

        linerLayoutOffline = (LinearLayout) getView().findViewById(R.id.linerLayout_setting_offline);
        imageViewRetry = (ImageView) getView().findViewById(R.id.image_retry);
        textViewTitleRetry = (TextView) getView().findViewById(R.id.textview_title_retry);
        textViewSubTitleRetry = (TextView) getView().findViewById(R.id.textview_subtitle_retry);
        Button buttonRetry = (Button) getView().findViewById(R.id.button_retry);

        backArrowImageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mSettingsListener.onSettingsCompleteCompleteByBackArrow();
            }
        });

        buttonNetworks.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                mSettingsListener.onSettingsCompleteByNetwork();
            }
        });

        buttonSigning.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                showAdvancedSigningDialog(jsonViewModel);
            }
        });

        buttonBackup.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                showBackupDialog(jsonViewModel);
            }
        });

        buttonRetry.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                mSettingsListener.onSettingsCompleteCompleteByBackArrow();
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

    private void showAdvancedSigningDialog(JsonViewModel jsonViewModel) {
        boolean currentValue = PrefConnect.readBoolean(getContext(), PrefConnect.ADVANCED_SIGNING_ENABLED_KEY, false);

        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);

        TextView description = new TextView(getContext());
        description.setText(jsonViewModel.getAdvancedSigningDescriptionByLangValues());
        description.setPadding(0, 0, 0, 24);
        layout.addView(description);

        RadioGroup radioGroup = new RadioGroup(getContext());
        radioGroup.setOrientation(RadioGroup.VERTICAL);

        RadioButton radioEnabled = new RadioButton(getContext());
        radioEnabled.setId(View.generateViewId());
        radioEnabled.setText(jsonViewModel.getAdvancedSigningOptionByLangValues());
        radioGroup.addView(radioEnabled);

        RadioButton radioDisabled = new RadioButton(getContext());
        radioDisabled.setId(View.generateViewId());
        radioDisabled.setText(jsonViewModel.getDisabledByLangValues());
        radioGroup.addView(radioDisabled);

        if (currentValue) {
            radioEnabled.setChecked(true);
        } else {
            radioDisabled.setChecked(true);
        }

        layout.addView(radioGroup);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(jsonViewModel.getSigningByLangValues());
        builder.setView(layout);
        builder.setPositiveButton(jsonViewModel.getOkByLangValues(), (dialog, which) -> {
            boolean enabled = radioEnabled.isChecked();
            PrefConnect.writeBoolean(getContext(), PrefConnect.ADVANCED_SIGNING_ENABLED_KEY, enabled);
            dialog.dismiss();
        });
        builder.setNegativeButton(jsonViewModel.getCancelByLangValues(), (dialog, which) -> {
            dialog.dismiss();
        });
        builder.show();
    }

    private void showBackupDialog(final JsonViewModel jsonViewModel) {
        this.cachedJsonViewModel = jsonViewModel;
        final int pad = dp(16);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        TextView phoneHeader = new TextView(getContext());
        phoneHeader.setText(safe(jsonViewModel.getPhoneBackupByLangValues(), "Phone Backup"));
        phoneHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        phoneHeader.setPadding(0, 0, 0, dp(8));
        root.addView(phoneHeader);

        TextView phoneDesc = new TextView(getContext());
        phoneDesc.setText(safe(jsonViewModel.getBackupDescriptionByLangValues(), ""));
        phoneDesc.setPadding(0, 0, 0, dp(8));
        root.addView(phoneDesc);

        final RadioGroup phoneGroup = new RadioGroup(getContext());
        phoneGroup.setOrientation(RadioGroup.VERTICAL);
        final RadioButton phoneEnabled = new RadioButton(getContext());
        phoneEnabled.setId(View.generateViewId());
        phoneEnabled.setText(safe(jsonViewModel.getEnabledByLangValues(), "Enabled"));
        phoneGroup.addView(phoneEnabled);
        final RadioButton phoneDisabled = new RadioButton(getContext());
        phoneDisabled.setId(View.generateViewId());
        phoneDisabled.setText(safe(jsonViewModel.getDisabledByLangValues(), "Disabled"));
        phoneGroup.addView(phoneDisabled);
        boolean phoneCurrent = PrefConnect.readBoolean(getContext(), PrefConnect.BACKUP_ENABLED_KEY, false);
        if (phoneCurrent) phoneEnabled.setChecked(true); else phoneDisabled.setChecked(true);
        root.addView(phoneGroup);

        View sep = new View(getContext());
        LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        sepParams.setMargins(0, dp(16), 0, dp(16));
        sep.setLayoutParams(sepParams);
        sep.setBackgroundColor(0x33888888);
        root.addView(sep);

        TextView cloudHeader = new TextView(getContext());
        cloudHeader.setText(safe(jsonViewModel.getCloudBackupByLangValues(), "Cloud Backup"));
        cloudHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        cloudHeader.setPadding(0, 0, 0, dp(8));
        root.addView(cloudHeader);

        TextView cloudDesc = new TextView(getContext());
        cloudDesc.setText(safe(jsonViewModel.getCloudBackupDescriptionByLangValues(), ""));
        cloudDesc.setPadding(0, 0, 0, dp(8));
        root.addView(cloudDesc);

        final RadioGroup cloudGroup = new RadioGroup(getContext());
        cloudGroup.setOrientation(RadioGroup.VERTICAL);
        final RadioButton cloudEnabled = new RadioButton(getContext());
        cloudEnabled.setId(View.generateViewId());
        cloudEnabled.setText(safe(jsonViewModel.getEnabledByLangValues(), "Enabled"));
        cloudGroup.addView(cloudEnabled);
        final RadioButton cloudDisabled = new RadioButton(getContext());
        cloudDisabled.setId(View.generateViewId());
        cloudDisabled.setText(safe(jsonViewModel.getDisabledByLangValues(), "Disabled"));
        cloudGroup.addView(cloudDisabled);
        boolean cloudCurrent = PrefConnect.readBoolean(getContext(), PrefConnect.CLOUD_BACKUP_ENABLED_KEY, false);
        if (cloudCurrent) cloudEnabled.setChecked(true); else cloudDisabled.setChecked(true);
        root.addView(cloudGroup);

        final TextView folderLabel = new TextView(getContext());
        folderLabel.setPadding(0, dp(8), 0, dp(8));
        root.addView(folderLabel);

        final Button pickFolderBtn = new Button(getContext());
        pickFolderBtn.setAllCaps(false);
        root.addView(pickFolderBtn);

        Runnable refreshFolderUi = new Runnable() {
            @Override
            public void run() {
                String folderUri = PrefConnect.readString(getContext(),
                        PrefConnect.CLOUD_BACKUP_FOLDER_URI_KEY, "");
                if (folderUri == null || folderUri.isEmpty()) {
                    folderLabel.setText(safe(jsonViewModel.getNoFolderSelectedByLangValues(),
                            "No folder selected"));
                    pickFolderBtn.setText(safe(jsonViewModel.getSelectBackupFolderByLangValues(),
                            "Select backup folder"));
                } else {
                    String display = describeFolder(Uri.parse(folderUri));
                    String tmpl = jsonViewModel.getCurrentFolderByLangValues();
                    String labelText = tmpl != null
                            ? tmpl.replace("[FOLDER]", display)
                            : ("Current folder: " + display);
                    folderLabel.setText(labelText);
                    pickFolderBtn.setText(safe(jsonViewModel.getChangeFolderByLangValues(),
                            "Change folder"));
                }
            }
        };
        refreshFolderUi.run();

        pickFolderBtn.setOnClickListener(v -> {
            pendingFolderSelectedCallback = refreshFolderUi;
            launchFolderPicker();
        });

        View sep2 = new View(getContext());
        LinearLayout.LayoutParams sep2Params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        sep2Params.setMargins(0, dp(16), 0, dp(16));
        sep2.setLayoutParams(sep2Params);
        sep2.setBackgroundColor(0x33888888);
        root.addView(sep2);

        LinearLayout restoreRow = new LinearLayout(getContext());
        restoreRow.setOrientation(LinearLayout.HORIZONTAL);

        Button restoreCloudBtn = new Button(getContext());
        restoreCloudBtn.setAllCaps(false);
        restoreCloudBtn.setText(safe(jsonViewModel.getRestoreFromCloudByLangValues(),
                "Restore from Cloud"));
        LinearLayout.LayoutParams rcParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        rcParams.setMargins(0, 0, dp(4), 0);
        restoreCloudBtn.setLayoutParams(rcParams);
        restoreRow.addView(restoreCloudBtn);

        Button restoreFileBtn = new Button(getContext());
        restoreFileBtn.setAllCaps(false);
        restoreFileBtn.setText(safe(jsonViewModel.getRestoreFromFileByLangValues(),
                "Restore from File"));
        LinearLayout.LayoutParams rfParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        rfParams.setMargins(dp(4), 0, 0, 0);
        restoreFileBtn.setLayoutParams(rfParams);
        restoreRow.addView(restoreFileBtn);

        root.addView(restoreRow);

        final ScrollViewWrapper scroll = ScrollViewWrapper.wrap(getContext(), root);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(jsonViewModel.getBackupByLangValues());
        builder.setView(scroll.view);
        builder.setPositiveButton(jsonViewModel.getOkByLangValues(), (dialog, which) -> {
            PrefConnect.writeBoolean(getContext(), PrefConnect.BACKUP_ENABLED_KEY, phoneEnabled.isChecked());
            PrefConnect.writeBoolean(getContext(), PrefConnect.CLOUD_BACKUP_ENABLED_KEY, cloudEnabled.isChecked());
            dialog.dismiss();
        });
        builder.setNegativeButton(jsonViewModel.getCancelByLangValues(), (dialog, which) -> dialog.dismiss());
        final AlertDialog dialog = builder.create();

        restoreCloudBtn.setOnClickListener(v -> {
            dialog.dismiss();
            startRestoreFromCloudFlow();
        });
        restoreFileBtn.setOnClickListener(v -> {
            dialog.dismiss();
            launchRestoreFilePicker();
        });

        dialog.show();
    }

    private static class ScrollViewWrapper {
        final View view;
        ScrollViewWrapper(View view) { this.view = view; }
        static ScrollViewWrapper wrap(Context ctx, View content) {
            android.widget.ScrollView sv = new android.widget.ScrollView(ctx);
            sv.addView(content);
            return new ScrollViewWrapper(sv);
        }
    }

    private void launchFolderPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            folderPickerLauncher.launch(intent);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getContext(), TAG, e);
        }
    }

    private void launchRestoreFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            restoreFilePickerLauncher.launch(intent);
        } catch (Exception e) {
            GlobalMethods.ExceptionError(getContext(), TAG, e);
        }
    }

    private void startRestoreFromCloudFlow() {
        final JsonViewModel vm = cachedJsonViewModel;
        String folderUriStr = PrefConnect.readString(getContext(),
                PrefConnect.CLOUD_BACKUP_FOLDER_URI_KEY, "");
        if (folderUriStr == null || folderUriStr.isEmpty()) {
            Toast.makeText(getContext(),
                    safe(vm.getNoFolderSelectedByLangValues(), "No folder selected"),
                    Toast.LENGTH_LONG).show();
            return;
        }
        Uri folderUri = Uri.parse(folderUriStr);
        final List<DocumentFile> files = CloudBackupManager.listBackupFiles(getContext(), folderUri);
        if (files.isEmpty()) {
            Toast.makeText(getContext(),
                    safe(vm.getRestoreNoBackupsFoundByLangValues(),
                            "No backup files were found in the selected folder."),
                    Toast.LENGTH_LONG).show();
            return;
        }
        showRestoreChecklistDialog(files);
    }

    private void showRestoreChecklistDialog(final List<DocumentFile> files) {
        final JsonViewModel vm = cachedJsonViewModel;
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), 0);

        final List<CheckBox> boxes = new ArrayList<>();
        for (DocumentFile f : files) {
            CheckBox cb = new CheckBox(getContext());
            cb.setText(f.getName() == null ? f.getUri().getLastPathSegment() : f.getName());
            cb.setChecked(true);
            boxes.add(cb);
            root.addView(cb);
        }

        LinearLayout headerRow = new LinearLayout(getContext());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        Button selectAll = new Button(getContext());
        selectAll.setAllCaps(false);
        selectAll.setText(safe(vm.getRestoreSelectAllByLangValues(), "Select all"));
        headerRow.addView(selectAll);
        Button selectNone = new Button(getContext());
        selectNone.setAllCaps(false);
        selectNone.setText(safe(vm.getRestoreSelectNoneByLangValues(), "Select none"));
        headerRow.addView(selectNone);

        selectAll.setOnClickListener(v -> { for (CheckBox cb : boxes) cb.setChecked(true); });
        selectNone.setOnClickListener(v -> { for (CheckBox cb : boxes) cb.setChecked(false); });

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(headerRow);
        android.widget.ScrollView sv = new android.widget.ScrollView(getContext());
        sv.addView(root);
        LinearLayout.LayoutParams svParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(320));
        sv.setLayoutParams(svParams);
        container.addView(sv);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(safe(vm.getRestoreFromCloudByLangValues(), "Restore from Cloud"));
        builder.setView(container);
        builder.setPositiveButton(safe(vm.getRestoreConfirmByLangValues(),
                "Restore selected wallets"), (dialog, which) -> {
            List<Uri> uris = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                if (boxes.get(i).isChecked()) {
                    uris.add(files.get(i).getUri());
                    names.add(files.get(i).getName());
                }
            }
            dialog.dismiss();
            if (!uris.isEmpty()) startRestoreLoop(uris, names);
        });
        builder.setNegativeButton(vm.getCancelByLangValues(), (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void startRestoreLoop(final List<Uri> uris, final List<String> names) {
        final JsonViewModel vm = cachedJsonViewModel != null ? cachedJsonViewModel
                : new JsonViewModel(getContext(), getArguments().getString("languageKey"));

        final ProgressBar bar = new ProgressBar(getContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(uris.size());
        final TextView progressMsg = new TextView(getContext());
        progressMsg.setPadding(dp(16), dp(16), dp(16), 0);

        LinearLayout wrap = new LinearLayout(getContext());
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(progressMsg);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barParams.setMargins(dp(16), dp(8), dp(16), dp(16));
        bar.setLayoutParams(barParams);
        wrap.addView(bar);

        final AlertDialog progressDialog = new AlertDialog.Builder(getContext())
                .setTitle(safe(vm.getBackupByLangValues(), "Backup"))
                .setView(wrap)
                .setCancelable(false)
                .create();
        progressDialog.show();

        final Handler ui = new Handler(Looper.getMainLooper());

        CloudBackupManager.runRestoreLoop(getContext(), uris, names,
                new CloudBackupManager.RestoreCallback() {
                    @Override
                    public void onProgress(int current, int total, String filename) {
                        ui.post(() -> {
                            bar.setProgress(current);
                            String tmpl = vm.getRestoreProgressByLangValues();
                            String msg = tmpl != null
                                    ? tmpl.replace("[CURRENT]", String.valueOf(current))
                                          .replace("[TOTAL]", String.valueOf(total))
                                    : ("Decrypting " + current + " of " + total + "...");
                            progressMsg.setText(msg + "\n" + filename);
                        });
                    }

                    @Override
                    public CloudBackupManager.PasswordPromptResult promptPassword(
                            String filename, String lastPassword, boolean previousAttemptFailed) {
                        final CountDownLatch latch = new CountDownLatch(1);
                        final AtomicReference<CloudBackupManager.PasswordPromptResult> out =
                                new AtomicReference<>();
                        ui.post(() -> showPasswordPromptDialog(vm, filename, lastPassword,
                                previousAttemptFailed, result -> {
                                    out.set(result);
                                    latch.countDown();
                                }));
                        try { latch.await(); } catch (InterruptedException ignored) { }
                        CloudBackupManager.PasswordPromptResult res = out.get();
                        return res != null ? res : CloudBackupManager.PasswordPromptResult.skip();
                    }

                    @Override
                    public void onComplete(CloudBackupManager.RestoreSummary summary) {
                        ui.post(() -> {
                            progressDialog.dismiss();
                            showSummaryDialog(vm, summary);
                        });
                    }
                });
    }

    private interface PasswordPromptResultListener {
        void onResult(CloudBackupManager.PasswordPromptResult result);
    }

    private void showPasswordPromptDialog(final JsonViewModel vm, String filename,
                                          String lastPassword, boolean previousFailed,
                                          final PasswordPromptResultListener listener) {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), 0);

        String promptTmpl = previousFailed
                ? safe(vm.getRestoreDecryptFailedByLangValues(),
                        "Unable to decrypt. Enter a different password or skip this file.")
                : safe(vm.getRestoreEnterPasswordByLangValues(),
                        "Enter password for [FILENAME]");
        TextView prompt = new TextView(getContext());
        prompt.setText(promptTmpl.replace("[FILENAME]", filename == null ? "" : filename));
        prompt.setPadding(0, 0, 0, dp(8));
        root.addView(prompt);

        final EditText pwd = new EditText(getContext());
        pwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pwd.setSingleLine(true);
        if (lastPassword != null) pwd.setText(lastPassword);
        root.addView(pwd);

        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(safe(vm.getBackupPasswordByLangValues(), "Password"))
                .setView(root)
                .setCancelable(false)
                .setPositiveButton(safe(vm.getOkByLangValues(), "OK"), null)
                .setNegativeButton(safe(vm.getRestoreSkipByLangValues(), "Skip"), null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String p = pwd.getText() == null ? "" : pwd.getText().toString();
                if (p.isEmpty()) return;
                dialog.dismiss();
                listener.onResult(CloudBackupManager.PasswordPromptResult.provide(p));
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                dialog.dismiss();
                listener.onResult(CloudBackupManager.PasswordPromptResult.skip());
            });
        });
        dialog.show();
    }

    private void showSummaryDialog(JsonViewModel vm, CloudBackupManager.RestoreSummary summary) {
        StringBuilder sb = new StringBuilder();
        appendSummaryLine(sb, vm.getRestoreRestoredByLangValues(),
                "Restored: [COUNT]", summary.restored);
        appendSummaryLine(sb, vm.getRestoreAlreadyPresentByLangValues(),
                "Already present: [COUNT]", summary.alreadyPresent);
        appendSummaryLine(sb, vm.getRestoreFailedByLangValues(),
                "Failed: [COUNT]", summary.failed);

        new AlertDialog.Builder(getContext())
                .setTitle(safe(vm.getRestoreSummaryByLangValues(), "Restore Summary"))
                .setMessage(sb.toString())
                .setPositiveButton(safe(vm.getOkByLangValues(), "OK"), (d, w) -> d.dismiss())
                .show();
    }

    private void appendSummaryLine(StringBuilder sb, String template, String fallback, List<String> items) {
        String header = template != null
                ? template.replace("[COUNT]", String.valueOf(items.size()))
                : fallback.replace("[COUNT]", String.valueOf(items.size()));
        sb.append(header).append('\n');
        for (String s : items) sb.append("  - ").append(s).append('\n');
        sb.append('\n');
    }

    private String describeFolder(Uri treeUri) {
        try {
            DocumentFile d = DocumentFile.fromTreeUri(getContext(), treeUri);
            if (d != null && d.getName() != null) return d.getName();
        } catch (Exception ignored) { }
        return treeUri.toString();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private static String safe(String v, String fallback) {
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    public static interface OnSettingsCompleteListener {
        public abstract void onSettingsCompleteCompleteByBackArrow();
        public abstract void onSettingsCompleteByNetwork();
    }

    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.mSettingsListener = (OnSettingsCompleteListener)context;
        }
        catch (final ClassCastException e) {
            throw new ClassCastException(context.toString() + " ");
        }
    }
}
