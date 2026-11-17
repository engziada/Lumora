package com.firestick.aura.launcher.ui;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Frosted glass for surfaces that sit directly on the wallpaper. Fire OS has no backdrop blur
 * (RenderEffect needs Android 12), so each wallpaper is blurred once at 1/8 scale on a worker
 * thread and every frosted surface paints its own slice of that small bitmap behind its tint.
 * One instance per wallpaper: Home and Settings each own one.
 */
public final class Frost {
    private static final int SCALE = 8;
    private static final int BLUR_RADIUS = 3;

    final View reference;
    private final Set<FrostDrawable> clients = Collections.newSetFromMap(new WeakHashMap<>());
    Bitmap previous, current;
    private long fadeStart;
    private int fadeMs;

    /** @param reference the full-screen wallpaper view whose centre-crop the backdrop follows */
    public Frost(View reference) {
        this.reference = reference;
    }

    /** Small blurred copy of {@code wallpaper}, or {@code null}. Safe on a worker thread. */
    public static Bitmap render(Bitmap wallpaper) {
        if (wallpaper == null || wallpaper.isRecycled()) return null;
        int width = wallpaper.getWidth(), height = wallpaper.getHeight();
        int w = Math.max(1, width / SCALE), h = Math.max(1, height / SCALE);
        try {
            Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(small);
            canvas.scale(w / (float) width, h / (float) height);
            canvas.drawBitmap(wallpaper, 0f, 0f, new Paint(Paint.FILTER_BITMAP_FLAG));
            Glass.blur(small, BLUR_RADIUS);
            return small;
        } catch (OutOfMemoryError | RuntimeException error) {
            return null;
        }
    }

    /**
     * Shows {@code blurred} behind every frosted surface, fading in over {@code fadeMs} in step
     * with the wallpaper's own cross-fade. {@code null} drops the backdrop. Main thread only.
     */
    public void publish(Bitmap blurred, int fadeMs) {
        // Like the wallpaper views: a fade that is cut short restarts from the last settled image.
        if (progress() >= 1f) previous = current;
        current = blurred;
        // The wallpaper animator jumps to its end when the system turns animations off.
        this.fadeMs = blurred == null || !ValueAnimator.areAnimatorsEnabled() ? 0 : Math.max(0, fadeMs);
        if (this.fadeMs == 0) previous = null;
        fadeStart = SystemClock.uptimeMillis();
        for (FrostDrawable client : clients) client.invalidateSelf();
    }

    /**
     * Replaces the background of {@code host} with frosted glass: the blurred wallpaper,
     * {@code scrim} (whatever already darkens the wallpaper around the host), the glass fill
     * from the theme attribute {@code fillAttr}, a top sheen and a light edge.
     */
    public void apply(View host, float radiusDp, int fillAttr, int scrim) {
        FrostDrawable drawable = new FrostDrawable(this, host, radiusDp, fillAttr, scrim);
        clients.add(drawable);
        host.setBackground(drawable);
    }

    /** Cross-fade position from 0 to 1, eased like the wallpaper's ViewPropertyAnimator. */
    float progress() {
        if (fadeMs <= 0) return 1f;
        float x = (SystemClock.uptimeMillis() - fadeStart) / (float) fadeMs;
        if (x >= 1f) return 1f;
        return (float) (Math.cos((x + 1f) * Math.PI) / 2.0 + 0.5);
    }

    /** Releases the outgoing backdrop once its fade has finished. */
    void settle() {
        if (previous != null && progress() >= 1f) previous = null;
    }
}
