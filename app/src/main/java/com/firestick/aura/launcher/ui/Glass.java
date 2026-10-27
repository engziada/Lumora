package com.firestick.aura.launcher.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.R;

/**
 * Glassmorphism without platform blur: Fire OS (Android 9/11) has no backdrop blur, so overlays
 * draw a downscaled, box-blurred snapshot of the current screen behind a translucent panel.
 */
public final class Glass {
    private static final int SCALE = 8;
    private static final int BLUR_RADIUS = 4;

    private Glass() {
    }

    /** Full-screen dialog: blurred snapshot, warm shade, and the given panel layout at {@code gravity}. */
    public static Dialog overlay(Activity activity, int layoutRes, int gravity) {
        Bitmap backdrop = snapshot(activity);
        Dialog dialog = new Dialog(activity, R.style.Theme_Lumora_Overlay);
        FrameLayout root = new FrameLayout(activity);
        if (backdrop != null) {
            ImageView image = new ImageView(activity);
            image.setScaleType(ImageView.ScaleType.FIT_XY);
            image.setImageBitmap(backdrop);
            root.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        View shade = new View(activity);
        shade.setBackgroundColor(ContextCompat.getColor(activity, R.color.glass_shade));
        root.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View panel = LayoutInflater.from(activity).inflate(layoutRes, root, false);
        ViewGroup.LayoutParams source = panel.getLayoutParams();
        FrameLayout.LayoutParams params = source instanceof FrameLayout.LayoutParams
                ? (FrameLayout.LayoutParams) source
                : new FrameLayout.LayoutParams(source.width, source.height);
        params.gravity = gravity;
        root.addView(panel, params);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dialog.setOnDismissListener(d -> {
            if (backdrop != null && !backdrop.isRecycled()) backdrop.recycle();
        });
        return dialog;
    }

    /** Simple message dialog with an optional selectable command block and one optional action. */
    public static Dialog info(Activity activity, CharSequence title, CharSequence message, CharSequence code,
                              CharSequence primary, Runnable onPrimary) {
        Dialog dialog = overlay(activity, R.layout.dialog_glass_info, Gravity.CENTER);
        ((TextView) dialog.findViewById(R.id.infoTitle)).setText(title);
        TextView body = dialog.findViewById(R.id.infoMessage);
        if (message == null) body.setVisibility(View.GONE);
        else body.setText(message);
        TextView block = dialog.findViewById(R.id.infoCode);
        if (code == null) block.setVisibility(View.GONE);
        else block.setText(code);
        Button action = dialog.findViewById(R.id.btnInfoPrimary);
        Button close = dialog.findViewById(R.id.btnInfoClose);
        if (primary == null) {
            action.setVisibility(View.GONE);
        } else {
            action.setText(primary);
            action.setOnClickListener(v -> {
                dialog.dismiss();
                if (onPrimary != null) onPrimary.run();
            });
        }
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        (primary != null ? action : close).requestFocus();
        return dialog;
    }

    /** Downscaled, blurred copy of what the activity currently shows, or {@code null}. */
    public static Bitmap snapshot(Activity activity) {
        View root = activity.getWindow().getDecorView();
        int width = root.getWidth(), height = root.getHeight();
        if (width <= 0 || height <= 0) return null;
        int w = Math.max(1, width / SCALE), h = Math.max(1, height / SCALE);
        try {
            Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(small);
            canvas.scale(w / (float) width, h / (float) height);
            root.draw(canvas);
            blur(small, BLUR_RADIUS);
            return small;
        } catch (RuntimeException | OutOfMemoryError error) {
            // Hardware bitmaps cannot be drawn into a software canvas; fall back to the plain shade.
            return null;
        }
    }

    /** Three passes of a separable box blur, which approximates a Gaussian. */
    static void blur(Bitmap bitmap, int radius) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        int[] scratch = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            boxBlur(pixels, scratch, w, h, radius, true);
            boxBlur(scratch, pixels, w, h, radius, false);
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
    }

    private static void boxBlur(int[] in, int[] out, int w, int h, int radius, boolean horizontal) {
        int lines = horizontal ? h : w;
        int length = horizontal ? w : h;
        int window = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int a = 0, r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int p = in[index(line, clamp(i, length), w, horizontal)];
                a += p >>> 24;
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                b += p & 0xFF;
            }
            for (int i = 0; i < length; i++) {
                out[index(line, i, w, horizontal)] = ((a / window) << 24) | ((r / window) << 16) | ((g / window) << 8) | (b / window);
                int leaving = in[index(line, clamp(i - radius, length), w, horizontal)];
                int entering = in[index(line, clamp(i + radius + 1, length), w, horizontal)];
                a += (entering >>> 24) - (leaving >>> 24);
                r += ((entering >> 16) & 0xFF) - ((leaving >> 16) & 0xFF);
                g += ((entering >> 8) & 0xFF) - ((leaving >> 8) & 0xFF);
                b += (entering & 0xFF) - (leaving & 0xFF);
            }
        }
    }

    private static int clamp(int value, int length) {
        return value < 0 ? 0 : Math.min(value, length - 1);
    }

    private static int index(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }
}
