package com.quantumcoinwallet.app.view.dialog;

import android.content.Context;
import android.graphics.Color;
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
}
