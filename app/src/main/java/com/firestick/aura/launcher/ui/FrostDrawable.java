package com.firestick.aura.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.PreferenceManager;

/**
 * Background painted by {@link Frost}: the host's slice of the blurred wallpaper, the scrim that
 * darkens the wallpaper around the host, the themed glass fill, a top sheen and a light edge.
 * With Reduce transparency on, only the opaque fill and the edge are drawn.
 */
final class FrostDrawable extends Drawable {
    private static final int EDGE_TOP = 0x40FFFFFF, EDGE_BOTTOM = 0x14FFFFFF;

    private final Frost frost;
    private final View host;
    private final float radius, stroke;
    private final boolean solid;
    private final int scrim, fill, sheenColor;
    private final Paint backdrop = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF(), edgeRect = new RectF();
    private final Matrix matrix = new Matrix();
    private final int[] hostAt = new int[2], wallAt = new int[2];
    // Two backdrops are on screen during a cross-fade; keep one shader per bitmap.
    private final Bitmap[] cachedBitmaps = new Bitmap[2];
    private final BitmapShader[] cachedShaders = new BitmapShader[2];
    private final Matrix[] cachedMatrices = {new Matrix(), new Matrix()};
    private int alpha = 255;

    FrostDrawable(Frost frost, View host, float radiusDp, int fillAttr, int scrim) {
        Context context = host.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        this.frost = frost;
        this.host = host;
        this.scrim = scrim;
        radius = radiusDp * density;
        stroke = density;
        solid = PreferenceManager.reduceTransparency(context);
        fill = Glass.color(context, fillAttr);
        sheenColor = ContextCompat.getColor(context, R.color.glass_sheen);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(stroke);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        rect.set(bounds);
        float half = stroke / 2f;
        edgeRect.set(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half);
        sheen.setShader(new LinearGradient(0f, rect.top, 0f, rect.top + rect.height() * .6f,
                sheenColor, sheenColor & 0x00FFFFFF, Shader.TileMode.CLAMP));
        edge.setShader(new LinearGradient(0f, rect.top, 0f, rect.bottom, EDGE_TOP, EDGE_BOTTOM, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas canvas) {
        if (rect.isEmpty()) return;
        int layer = alpha < 255 ? canvas.saveLayerAlpha(rect, alpha) : -1;
        if (!solid) drawFrost(canvas);
        paint.setColor(fill);
        canvas.drawRoundRect(rect, radius, radius, paint);
        if (!solid) canvas.drawRoundRect(rect, radius, radius, sheen);
        float edgeRadius = Math.max(0f, radius - stroke / 2f);
        canvas.drawRoundRect(edgeRect, edgeRadius, edgeRadius, edge);
        if (layer >= 0) canvas.restoreToCount(layer);
    }

    private void drawFrost(Canvas canvas) {
        float t = frost.progress();
        boolean fading = t < 1f;
        boolean drewPrevious = fading && frost.previous != null && drawBackdrop(canvas, frost.previous, 255);
        boolean drewCurrent = frost.current != null
                && drawBackdrop(canvas, frost.current, fading ? Math.round(255 * t) : 255);
        if (scrim != 0 && (drewPrevious || drewCurrent)) {
            paint.setColor(scrim);
            // The first backdrop fades in over the live wallpaper, which the real scrim already darkens.
            if (!drewPrevious && fading) paint.setAlpha(Math.round(Color.alpha(scrim) * t));
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
        if (fading) invalidateSelf();
        else frost.settle();
    }

    /** Draws {@code bitmap} where the wallpaper view shows it (centre crop) behind the host. */
    private boolean drawBackdrop(Canvas canvas, Bitmap bitmap, int alpha) {
        View wall = frost.reference;
        int ww = wall.getWidth(), wh = wall.getHeight();
        if (ww == 0 || wh == 0 || alpha <= 0 || bitmap.isRecycled()) return false;
        int bw = bitmap.getWidth(), bh = bitmap.getHeight();
        float scale = Math.max(ww / (float) bw, wh / (float) bh);
        wall.getLocationInWindow(wallAt);
        host.getLocationInWindow(hostAt);
        matrix.setScale(scale, scale);
        matrix.postTranslate((ww - bw * scale) / 2f + wallAt[0] - hostAt[0],
                (wh - bh * scale) / 2f + wallAt[1] - hostAt[1]);
        backdrop.setShader(shaderFor(bitmap, matrix));
        backdrop.setAlpha(alpha);
        canvas.drawRoundRect(rect, radius, radius, backdrop);
        return true;
    }

    private BitmapShader shaderFor(Bitmap bitmap, Matrix local) {
        int slot = cachedBitmaps[0] == bitmap ? 0 : cachedBitmaps[1] == bitmap ? 1 : -1;
        if (slot < 0) {
            Bitmap first = cachedBitmaps[0];
            slot = first == null || (first != frost.previous && first != frost.current) ? 0 : 1;
            cachedBitmaps[slot] = bitmap;
            cachedShaders[slot] = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            cachedMatrices[slot].reset();
        }
        // Setting a local matrix rebuilds the native shader, so only do it when the geometry moves.
        if (!cachedMatrices[slot].equals(local)) {
            cachedShaders[slot].setLocalMatrix(local);
            cachedMatrices[slot].set(local);
        }
        return cachedShaders[slot];
    }

    @Override public void getOutline(Outline outline) {
        outline.setRoundRect(getBounds(), radius);
    }

    @Override public void setAlpha(int alpha) {
        if (this.alpha == alpha) return;
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override public int getAlpha() {
        return alpha;
    }

    @Override public void setColorFilter(ColorFilter filter) {
        backdrop.setColorFilter(filter);
        paint.setColorFilter(filter);
        sheen.setColorFilter(filter);
        edge.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
