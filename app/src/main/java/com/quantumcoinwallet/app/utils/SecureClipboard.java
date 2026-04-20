package com.quantumcoinwallet.app.utils;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import timber.log.Timber;

/**
 * Clipboard helper for sensitive values (seed phrases, private keys).
 *
 * MF-07:
 *   - Marks the ClipData as sensitive on API 33+ so the clipboard preview
 *     does not leak the value to anyone glancing at the screen.
 *   - Schedules an auto-clear of the clipboard after {@link #AUTO_CLEAR_MS}.
 *     If the user has copied something else in the meantime we leave that
 *     newer value alone, so we never clobber unrelated data.
 */
public final class SecureClipboard {

    private static final long AUTO_CLEAR_MS = 30_000L;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private SecureClipboard() { }

    /**
     * Copy a sensitive value to the clipboard with the "sensitive" flag set
     * (API 33+) and schedule an automatic clear after 30 seconds.
     */
    public static void copySensitive(Context ctx, String label, CharSequence value) {
        copyInternal(ctx, label, value, true);
    }

    /**
     * L-05: copy a wallet address with the "sensitive" flag set (API 33+)
     * so it does not show up in Android 13+ clipboard previews, but
     * <b>without</b> the auto-clear - addresses are public information
     * and clobbering them after 30s would hurt usability (users often
     * paste into another app after a delay).
     */
    public static void copyAddress(Context ctx, String label, CharSequence value) {
        copyInternal(ctx, label, value, false);
    }

    private static void copyInternal(Context ctx, String label, CharSequence value,
                                     boolean autoClear) {
        if (ctx == null || value == null) return;
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;

        final ClipData clip = ClipData.newPlainText(label, value);

        if (Build.VERSION.SDK_INT >= 33) {
            ClipDescription desc = clip.getDescription();
            if (desc != null) {
                android.os.PersistableBundle extras = new android.os.PersistableBundle(1);
                extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
                desc.setExtras(extras);
            }
        }

        cm.setPrimaryClip(clip);

        if (!autoClear) {
            return;
        }

        final Context app = ctx.getApplicationContext() != null
                ? ctx.getApplicationContext() : ctx;
        final String targetText = value.toString();

        MAIN_HANDLER.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    ClipboardManager m = (ClipboardManager) app.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (m == null) return;
                    ClipData current = m.getPrimaryClip();
                    String currentText = extractText(current);
                    if (currentText != null && currentText.equals(targetText)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            m.clearPrimaryClip();
                        } else {
                            m.setPrimaryClip(ClipData.newPlainText("", ""));
                        }
                        Timber.tag("SecureClipboard").d("auto-cleared");
                    }
                } catch (Throwable t) {
                    Timber.tag("SecureClipboard").w(t, "auto-clear");
                }
            }
        }, AUTO_CLEAR_MS);
    }

    @Nullable
    private static String extractText(@Nullable ClipData clip) {
        if (clip == null || clip.getItemCount() == 0) return null;
        CharSequence cs = clip.getItemAt(0).getText();
        return cs == null ? null : cs.toString();
    }
}
