package com.quantumcoinwallet.app.view.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.quantumcoinwallet.app.viewmodel.JsonViewModel;

/**
 * Reusable dialog asking the user for a backup password. Offers two modes:
 *  - "Use current wallet password" (default) -- returns the caller-supplied password.
 *  - "Use a different password" -- prompts for password + confirm, enforces length and no
 *    leading/trailing spaces.
 */
public class BackupPasswordDialog {

    public interface OnBackupPasswordListener {
        void onPasswordSelected(String password);
        void onCanceled();
    }

    public static void show(final Context ctx, final JsonViewModel vm,
                            final String currentPassword,
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

        final RadioGroup group = new RadioGroup(ctx);
        group.setOrientation(LinearLayout.VERTICAL);

        final RadioButton useCurrent = new RadioButton(ctx);
        useCurrent.setId(1);
        useCurrent.setText(safe(vm.getUseCurrentPasswordByLangValues(), "Use current wallet password"));
        useCurrent.setChecked(true);
        group.addView(useCurrent);

        final RadioButton useDifferent = new RadioButton(ctx);
        useDifferent.setId(2);
        useDifferent.setText(safe(vm.getUseDifferentPasswordByLangValues(), "Use a different password"));
        group.addView(useDifferent);

        root.addView(group);

        final LinearLayout diffContainer = new LinearLayout(ctx);
        diffContainer.setOrientation(LinearLayout.VERTICAL);
        diffContainer.setVisibility(ViewGroup.GONE);
        diffContainer.setPadding(0, dp(ctx, 8), 0, 0);

        final EditText pwd = new EditText(ctx);
        pwd.setHint(safe(vm.getPasswordByLangValues(), "Password"));
        pwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pwd.setSingleLine(true);
        diffContainer.addView(pwd);

        final EditText confirm = new EditText(ctx);
        confirm.setHint(safe(vm.getConfirmBackupPasswordByLangValues(), "Confirm password"));
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm.setSingleLine(true);
        diffContainer.addView(confirm);

        root.addView(diffContainer);

        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                if (checkedId == useDifferent.getId()) {
                    diffContainer.setVisibility(ViewGroup.VISIBLE);
                    pwd.requestFocus();
                } else {
                    diffContainer.setVisibility(ViewGroup.GONE);
                }
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(safe(vm.getBackupPasswordByLangValues(), "Backup password"))
                .setView(root)
                .setPositiveButton(safe(vm.getOkByLangValues(), "OK"), null)
                .setNegativeButton(safe(vm.getCancelByLangValues(), "Cancel"), null)
                .setCancelable(false)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (useCurrent.isChecked()) {
                    dialog.dismiss();
                    listener.onPasswordSelected(currentPassword);
                    return;
                }
                String p = pwd.getText() == null ? "" : pwd.getText().toString();
                String c = confirm.getText() == null ? "" : confirm.getText().toString();
                if (p.length() < 12) {
                    Toast.makeText(ctx, safe(vm.getPasswordSpecByErrors(),
                            "Password must be at least 12 characters"), Toast.LENGTH_LONG).show();
                    return;
                }
                if (!p.equals(p.trim())) {
                    Toast.makeText(ctx, safe(vm.getPasswordSpaceByErrors(),
                            "Password cannot start or end with spaces"), Toast.LENGTH_LONG).show();
                    return;
                }
                if (!p.equals(c)) {
                    Toast.makeText(ctx, safe(vm.getRetypePasswordMismatchByErrors(),
                            "Passwords do not match"), Toast.LENGTH_LONG).show();
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

    private static int dp(Context ctx, int value) {
        return (int) (value * ctx.getResources().getDisplayMetrics().density);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
