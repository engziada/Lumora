package com.firestick.aura.launcher.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.assistant.PhoneBridge;
import com.firestick.aura.launcher.assistant.QrCode;

/** Shows the QR code, address and pairing code of a running {@link PhoneBridge}. */
public final class PhonePairDialog {
    private static final int QUIET_ZONE = 4, TARGET_PX = 480;
    private static final int DARK = Color.rgb(0x1C, 0x15, 0x11), LIGHT = Color.rgb(0xFB, 0xF5, 0xEC);

    private final Activity activity;
    private final Dialog dialog;
    private final PhoneBridge bridge;
    private final ImageView qr;
    private final TextView url, code;

    private PhonePairDialog(Activity activity, PhoneBridge bridge, CharSequence title, CharSequence message,
                            Runnable onClosed) {
        this.activity = activity;
        this.bridge = bridge;
        dialog = Glass.overlay(activity, R.layout.dialog_phone_pair, Gravity.CENTER);
        ((TextView) dialog.findViewById(R.id.phoneTitle)).setText(title);
        ((TextView) dialog.findViewById(R.id.phoneMessage)).setText(message);
        qr = dialog.findViewById(R.id.phoneQr);
        url = dialog.findViewById(R.id.phoneUrl);
        code = dialog.findViewById(R.id.phoneCode);
        Button close = dialog.findViewById(R.id.btnPhoneClose);
        // Glass.overlay owns the dismiss listener (it recycles the backdrop), so report closing from
        // the Close button and from Back (cancel) instead. dismiss() from code does not report.
        close.setOnClickListener(v -> {
            dialog.dismiss();
            if (onClosed != null) onClosed.run();
        });
        dialog.setOnCancelListener(d -> {
            if (onClosed != null) onClosed.run();
        });
        refresh();
        dialog.show();
        close.requestFocus();
    }

    public static PhonePairDialog show(Activity activity, PhoneBridge bridge, CharSequence title,
                                       CharSequence message, Runnable onClosed) {
        return new PhonePairDialog(activity, bridge, title, message, onClosed);
    }

    /** Redraws the QR code and texts, for example after the pairing code changed. */
    public void refresh() {
        url.setText(bridge.url());
        code.setText(activity.getString(R.string.phone_code, bridge.code()));
        BitmapDrawable image = new BitmapDrawable(activity.getResources(), render(QrCode.encode(bridge.pairUrl())));
        image.setFilterBitmap(false);
        qr.setImageDrawable(image);
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    public void dismiss() {
        if (dialog.isShowing()) dialog.dismiss();
    }

    private static Bitmap render(QrCode code) {
        int modules = code.size() + QUIET_ZONE * 2;
        int scale = Math.max(4, TARGET_PX / modules);
        int width = modules * scale;
        int[] pixels = new int[width * width];
        for (int y = 0; y < width; y++) {
            int my = y / scale - QUIET_ZONE;
            for (int x = 0; x < width; x++) {
                int mx = x / scale - QUIET_ZONE;
                boolean dark = mx >= 0 && my >= 0 && mx < code.size() && my < code.size() && code.isDark(mx, my);
                pixels[y * width + x] = dark ? DARK : LIGHT;
            }
        }
        return Bitmap.createBitmap(pixels, width, width, Bitmap.Config.ARGB_8888);
    }
}
