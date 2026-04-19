package com.quantumcoinwallet.app.view.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.quantumcoinwallet.app.R;
import com.quantumcoinwallet.app.utils.GlobalMethods;
import com.quantumcoinwallet.app.viewmodel.JsonViewModel;

/**
 * Reusable dialog asking the user for a backup password.
 *
 * Two modes:
 *  - Create/backup mode (default): prompts the user for a new backup password with
 *    confirm + length/trim validation. The user is always asked afresh; no password
 *    is reused or cached.
 *  - Restore mode: a single password field with eye toggle, no confirmation, no length check;
 *    the user is entering an existing backup password, not creating one.
 */
public class BackupPasswordDialog {

    public interface OnBackupPasswordListener {
        void onPasswordSelected(String password);
        void onCanceled();
    }

    /**
     * Controls given to the caller of {@link #showRestore}, so the caller can keep the
     * dialog open on failure (wrong backup password) and only close it when decryption
     * succeeds. The dialog retains the typed password and stays on screen for retry.
     */
    public interface PasswordDialogControl {
        void dismiss();
        void onFailure();
    }

    public interface OnPasswordAttemptListener {
        void onAttempt(String password, PasswordDialogControl control);
        void onCanceled();
    }

    public static void show(final Context ctx, final JsonViewModel vm,
                            final String currentPassword,
                            final OnBackupPasswordListener listener) {
        showCreateMode(ctx, vm, listener);
    }

    public static void show(final Context ctx, final JsonViewModel vm,
                            final String currentPassword,
                            final boolean restoreMode,
                            final OnBackupPasswordListener listener) {
        if (restoreMode) {
            showRestore(ctx, vm, new OnPasswordAttemptListener() {
                @Override
                public void onAttempt(String password, PasswordDialogControl control) {
                    control.dismiss();
                    listener.onPasswordSelected(password);
                }
                @Override
                public void onCanceled() {
                    listener.onCanceled();
                }
            });
        } else {
            showCreateMode(ctx, vm, listener);
        }
    }

    private static void showCreateMode(final Context ctx, final JsonViewModel vm,
                                       final OnBackupPasswordListener listener) {
        final int pad = dp(ctx, 16);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView header = new TextView(ctx);
        header.setText(safe(vm.getBackupPasswordByLangValues(), "Backup password"));
        header.setTextSize(16);
        header.setPadding(0, 0, 0, dp(ctx, 8));
        root.addView(header);

        final TextInputLayout pwdLayout = new TextInputLayout(ctx);
        pwdLayout.setHintEnabled(false);
        pwdLayout.setPasswordVisibilityToggleEnabled(true);
        try {
            pwdLayout.setPasswordVisibilityToggleDrawable(R.drawable.show_password_selector);
        } catch (Throwable ignore) { }
        final TextInputEditText pwd = new TextInputEditText(ctx);
        pwd.setHint(safe(vm.getPasswordByLangValues(), "Password"));
        pwd.setSingleLine(true);
        pwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pwd.setTransformationMethod(PasswordTransformationMethod.getInstance());
        pwd.setBackground(null);
        pwdLayout.addView(pwd);
        root.addView(pwdLayout);

        final TextInputLayout confirmLayout = new TextInputLayout(ctx);
        confirmLayout.setHintEnabled(false);
        confirmLayout.setPasswordVisibilityToggleEnabled(true);
        try {
            confirmLayout.setPasswordVisibilityToggleDrawable(R.drawable.show_password_selector);
        } catch (Throwable ignore) { }
        final TextInputEditText confirm = new TextInputEditText(ctx);
        confirm.setHint(safe(vm.getConfirmBackupPasswordByLangValues(), "Confirm password"));
        confirm.setSingleLine(true);
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm.setTransformationMethod(PasswordTransformationMethod.getInstance());
        confirm.setBackground(null);
        confirmLayout.addView(confirm);
        root.addView(confirmLayout);

        final AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(safe(vm.getBackupPasswordByLangValues(), "Backup password"))
                .setView(root)
                .setPositiveButton(safe(vm.getOkByLangValues(), "OK"), null)
                .setNegativeButton(safe(vm.getCancelByLangValues(), "Cancel"), null)
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String errTitle = safe(vm.getErrorTitleByLangValues(), "Error");
                String p = pwd.getText() == null ? "" : pwd.getText().toString();
                String c = confirm.getText() == null ? "" : confirm.getText().toString();
                if (p.length() < 12) {
                    GlobalMethods.ShowErrorDialog(ctx, errTitle,
                            safe(vm.getPasswordSpecByErrors(),
                                    "Password must be at least 12 characters"));
                    return;
                }
                if (!p.equals(p.trim())) {
                    GlobalMethods.ShowErrorDialog(ctx, errTitle,
                            safe(vm.getPasswordSpaceByErrors(),
                                    "Password cannot start or end with spaces"));
                    return;
                }
                if (!p.equals(c)) {
                    GlobalMethods.ShowErrorDialog(ctx, errTitle,
                            safe(vm.getRetypePasswordMismatchByErrors(),
                                    "Passwords do not match"));
                    return;
                }
                dialog.dismiss();
                listener.onPasswordSelected(p);
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                dialog.dismiss();
                listener.onCanceled();
            });
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    /**
     * Restore-mode dialog with a retry-capable contract. The caller runs the (potentially
     * failing) decrypt attempt in its own thread and then calls either
     * {@link PasswordDialogControl#dismiss()} on success, or
     * {@link PasswordDialogControl#onFailure()} on failure (wrong password / corrupt file)
     * to re-enable the OK/Cancel buttons so the user can retry without re-typing.
     */
    public static void showRestore(final Context ctx, final JsonViewModel vm,
                                   final OnPasswordAttemptListener listener) {
        final int pad = dp(ctx, 16);
        final String title = safe(vm.getEnterBackupPasswordTitleByLangValues(),
                "Enter password of the backup");

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        final TextInputLayout pwdLayout = new TextInputLayout(ctx);
        pwdLayout.setHintEnabled(false);
        pwdLayout.setPasswordVisibilityToggleEnabled(true);
        try {
            pwdLayout.setPasswordVisibilityToggleDrawable(R.drawable.show_password_selector);
        } catch (Throwable ignore) { }

        final TextInputEditText pwd = new TextInputEditText(ctx);
        pwd.setHint(safe(vm.getPasswordByLangValues(), "Password"));
        pwd.setSingleLine(true);
        pwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pwd.setTransformationMethod(PasswordTransformationMethod.getInstance());
        pwd.setBackground(null);
        pwdLayout.addView(pwd);
        root.addView(pwdLayout);

        final AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(root)
                .setPositiveButton(safe(vm.getOkByLangValues(), "OK"), null)
                .setNegativeButton(safe(vm.getCancelByLangValues(), "Cancel"), null)
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(d -> {
            final android.widget.Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            final android.widget.Button cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            final CharSequence okLabel = okButton.getText();

            final PasswordDialogControl control = new PasswordDialogControl() {
                @Override
                public void dismiss() {
                    try { dialog.dismiss(); } catch (Throwable ignore) { }
                }
                @Override
                public void onFailure() {
                    try {
                        okButton.setEnabled(true);
                        cancelButton.setEnabled(true);
                        okButton.setText(okLabel);
                        pwd.setEnabled(true);
                        pwd.requestFocus();
                    } catch (Throwable ignore) { }
                }
            };

            okButton.setOnClickListener(v -> {
                String p = pwd.getText() == null ? "" : pwd.getText().toString();
                if (p.isEmpty()) {
                    GlobalMethods.ShowErrorDialog(ctx,
                            safe(vm.getErrorTitleByLangValues(), "Error"),
                            safe(vm.getEnterApasswordByLangValues(), "Enter a password"));
                    return;
                }
                okButton.setEnabled(false);
                cancelButton.setEnabled(false);
                pwd.setEnabled(false);
                okButton.setText("...");
                listener.onAttempt(p, control);
            });
            cancelButton.setOnClickListener(v -> {
                try { dialog.dismiss(); } catch (Throwable ignore) { }
                listener.onCanceled();
            });
        });

        dialog.show();
    }

    private static int dp(Context ctx, int value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
