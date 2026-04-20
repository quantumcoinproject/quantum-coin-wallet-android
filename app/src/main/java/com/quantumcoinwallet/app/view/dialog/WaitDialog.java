package com.quantumcoinwallet.app.view.dialog;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.quantumcoinwallet.app.R;

/**
 * Non-cancelable wait dialog used during long scrypt-based encrypt/decrypt operations.
 * Call {@link #show(Context, String)} on the UI thread before the heavy work starts, then
 * call {@link AlertDialog#dismiss()} on the returned dialog (from the UI thread) in every
 * completion branch (success and failure).
 */
public final class WaitDialog {

    private WaitDialog() { }

    /**
     * Handle for a rich wait overlay that can be mutated while visible. Used by the
     * batched restore flow to update the currently-processing address and the "i of N"
     * progress text as each file in the pending queue is attempted. All mutators must
     * run on the UI thread.
     */
    public static final class Handle {
        public final AlertDialog dialog;
        private final TextView addressView;
        private final TextView progressView;
        Handle(AlertDialog dialog, TextView addressView, TextView progressView) {
            this.dialog = dialog;
            this.addressView = addressView;
            this.progressView = progressView;
        }
        public void setAddress(String s) {
            addressView.setText(s == null ? "" : s);
        }
        public void setProgress(String s) {
            progressView.setText(s == null ? "" : s);
        }
        public void dismiss() {
            try { dialog.dismiss(); } catch (Throwable ignore) { }
        }
    }

    public static AlertDialog show(Context ctx, String message) {
        float d = ctx.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(pad, pad, pad, pad);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.center_container);

        ProgressBar pb = new ProgressBar(ctx);
        LinearLayout.LayoutParams lpPb =
                new LinearLayout.LayoutParams((int) (32 * d), (int) (32 * d));
        lpPb.setMarginEnd((int) (12 * d));
        pb.setLayoutParams(lpPb);
        row.addView(pb);

        TextView tv = new TextView(ctx);
        tv.setText(message);
        tv.setTextSize(14);
        tv.setTextColor(ContextCompat.getColor(ctx, R.color.colorCommon6));
        LinearLayout.LayoutParams lpTv = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lpTv);
        row.addView(tv);

        AlertDialog dlg = new AlertDialog.Builder(ctx)
                .setView(row)
                .setCancelable(false)
                .create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dlg.show();
        return dlg;
    }

    /**
     * Rich variant of {@link #show(Context, String)} that exposes two updatable labels
     * (address + progress) underneath the spinner + title row. Used by the batched cloud
     * restore to show the wallet currently being decrypted and the "i of N" counter
     * while the password dialog stays visible behind this overlay.
     */
    public static Handle showWithDetails(Context ctx, String title) {
        float d = ctx.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundResource(R.drawable.center_container);

        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        ProgressBar pb = new ProgressBar(ctx);
        LinearLayout.LayoutParams lpPb =
                new LinearLayout.LayoutParams((int) (32 * d), (int) (32 * d));
        lpPb.setMarginEnd((int) (12 * d));
        pb.setLayoutParams(lpPb);
        topRow.addView(pb);

        TextView titleView = new TextView(ctx);
        titleView.setText(title == null ? "" : title);
        titleView.setTextSize(14);
        titleView.setTextColor(ContextCompat.getColor(ctx, R.color.colorCommon6));
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        titleView.setLayoutParams(lpTitle);
        topRow.addView(titleView);

        root.addView(topRow);

        TextView addressView = new TextView(ctx);
        addressView.setText("");
        addressView.setTypeface(Typeface.MONOSPACE);
        addressView.setTextSize(12);
        addressView.setTextColor(ContextCompat.getColor(ctx, R.color.colorCommon6));
        LinearLayout.LayoutParams lpAddr = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpAddr.topMargin = (int) (10 * d);
        addressView.setLayoutParams(lpAddr);
        root.addView(addressView);

        TextView progressView = new TextView(ctx);
        progressView.setText("");
        progressView.setTextSize(12);
        progressView.setTextColor(ContextCompat.getColor(ctx, R.color.colorCommon6));
        LinearLayout.LayoutParams lpProg = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lpProg.topMargin = (int) (4 * d);
        progressView.setLayoutParams(lpProg);
        root.addView(progressView);

        AlertDialog dlg = new AlertDialog.Builder(ctx)
                .setView(root)
                .setCancelable(false)
                .create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dlg.show();
        return new Handle(dlg, addressView, progressView);
    }
}
