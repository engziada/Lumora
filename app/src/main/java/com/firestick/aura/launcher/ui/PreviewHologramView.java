package com.firestick.aura.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * Playful holographic ambient view: flowing grid, scanlines, noise speckles,
 * and reactive glowing streaks. Zero network, fully procedural.
 */
public final class PreviewHologramView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noisePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random(1337);

    private ValueAnimator flow;
    private float t = 0f;      // 0..1 flow
    private float scan = 0f;   // scanline position
    private float pulse = 0f;  // subtle pulse
    private float hueShift = 0f;
    private boolean active = false;

    private float[] noiseX;
    private float[] noiseY;
    private float[] noiseR;
    private int noiseCount;

    public PreviewHologramView(Context context) {
        super(context);
        init();
    }

    public PreviewHologramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PreviewHologramView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);

        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(1f));
        gridPaint.setAlpha(70);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1.2f));

        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(dp(8f), android.graphics.BlurMaskFilter.Blur.NORMAL));

        scanPaint.setStyle(Paint.Style.FILL);

        noisePaint.setStyle(Paint.Style.FILL);
        noisePaint.setAlpha(40);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1.4f));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) return;
        buildNoise(w, h);
        buildShaders(w, h);
    }

    private void buildNoise(int w, int h) {
        noiseCount = Math.max(60, (w * h) / 18000);
        noiseX = new float[noiseCount];
        noiseY = new float[noiseCount];
        noiseR = new float[noiseCount];
        for (int i = 0; i < noiseCount; i++) {
            noiseX[i] = rnd.nextFloat() * w;
            noiseY[i] = rnd.nextFloat() * h;
            noiseR[i] = dp(0.8f + rnd.nextFloat() * 1.6f);
        }
    }

    private void buildShaders(int w, int h) {
        // subtle horizontal sheen
        LinearGradient sheen = new LinearGradient(0, 0, w, 0,
                new int[]{Color.TRANSPARENT, 0x22F2D3A0, Color.TRANSPARENT},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        scanPaint.setShader(sheen);
    }

    public void start(String seed) {
        if (active) {
            // shift hue slightly on app change
            if (seed != null) {
                int s = Math.abs(seed.hashCode());
                hueShift = ((s % 37) - 18) / 180f; // -0.1..0.1
            }
            invalidate();
            return;
        }
        active = true;
        setVisibility(VISIBLE);
        if (flow != null) flow.cancel();
        flow = ValueAnimator.ofFloat(0f, 1f);
        flow.setDuration(3200);
        flow.setRepeatCount(ValueAnimator.INFINITE);
        flow.setRepeatMode(ValueAnimator.RESTART);
        flow.setInterpolator(new LinearInterpolator());
        flow.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            // scanline moves top to bottom slowly
            scan = (t * 1.7f) % 1.2f;
            pulse = (float) (0.5f + 0.5f * Math.sin(t * Math.PI * 2.6f));
            invalidate();
        });
        flow.start();
        if (seed != null) {
            int s = Math.abs(seed.hashCode());
            hueShift = ((s % 37) - 18) / 180f;
        }
        invalidate();
    }

    public void stop() {
        if (!active && getVisibility() == GONE) return;
        active = false;
        if (flow != null) {
            flow.cancel();
            flow = null;
        }
        setVisibility(GONE);
        invalidate();
    }

    public void burst() {
        // quick flicker on focus
        if (!active) return;
        pulse = 1.0f;
        invalidate();
        postDelayed(() -> {
            if (active) invalidate();
        }, 120);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!active && getVisibility() != VISIBLE) return;
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Background wash
        canvas.drawColor(Color.parseColor("#0F0A07"));

        float cy = h * 0.42f;
        float baseGrid = w / 14f;

        // Perspective grid
        gridPaint.setColor(adjustColor(0xE3A15A, hueShift));
        gridPaint.setAlpha((int) (55 + 25 * pulse));
        // horizontal lines
        for (int i = 0; i < 9; i++) {
            float p = (i / 8f + t * 0.12f) % 1f;
            float y = cy + (p * p) * h * 0.9f; // perspective curve
            float alphaFade = (1f - p) * 0.8f;
            gridPaint.setAlpha((int) ((55 + 25 * pulse) * alphaFade));
            canvas.drawLine(0, y, w, y, gridPaint);
        }
        // vertical converging lines
        for (int i = -6; i <= 6; i++) {
            float xOff = i * baseGrid;
            Path path = new Path();
            float x0 = w * 0.5f + xOff * 0.25f;
            float y0 = cy;
            float x1 = w * 0.5f + xOff * 2.2f;
            float y1 = h;
            path.moveTo(x0, y0);
            path.lineTo(x1, y1);
            gridPaint.setAlpha((int) (48 + 22 * pulse));
            canvas.drawPath(path, gridPaint);
        }

        // Flowing streaks
        linePaint.setColor(adjustColor(0xF2D3A0, hueShift));
        linePaint.setAlpha((int) (90 + 110 * pulse));
        float sx = (t * w * 1.6f) % (w * 1.6f);
        canvas.drawLine(sx - w * 0.4f, cy - h * 0.1f, sx + w * 0.25f, cy + h * 0.05f, linePaint);
        float sx2 = ((t + 0.33f) * w * 1.4f) % (w * 1.4f);
        linePaint.setAlpha((int) (70 + 80 * pulse));
        canvas.drawLine(sx2 - w * 0.3f, cy + h * 0.08f, sx2 + w * 0.22f, cy + h * 0.16f, linePaint);

        // Glowing orbs
        glowPaint.setColor(adjustColor(0xE3A15A, hueShift));
        glowPaint.setAlpha((int) (60 + 90 * pulse));
        float gx = w * (0.25f + 0.5f * (float) Math.sin(t * Math.PI * 1.4f));
        float gy = cy + h * (0.12f * (float) Math.cos(t * Math.PI * 1.1f));
        canvas.drawCircle(gx, gy, dp(10f + 6f * pulse), glowPaint);
        glowPaint.setColor(adjustColor(0xF2D3A0, hueShift));
        glowPaint.setAlpha((int) (40 + 70 * pulse));
        float gx2 = w * (0.7f + 0.25f * (float) Math.cos(t * Math.PI * 0.9f));
        float gy2 = cy + h * (0.16f * (float) Math.sin(t * Math.PI * 1.7f));
        canvas.drawCircle(gx2, gy2, dp(7f + 5f * pulse), glowPaint);

        // Scanline
        if (scan >= 0f && scan <= 1f) {
            scanPaint.setAlpha((int) (40 + 60 * pulse));
            RectF r = new RectF(0, h * scan, w, h * scan + dp(2.6f));
            canvas.drawRect(r, scanPaint);
        }

        // Noise speckles
        noisePaint.setColor(adjustColor(0xF3E6D6, hueShift));
        for (int i = 0; i < noiseCount; i++) {
            float flicker = (float) Math.sin((t * 6.0f + i * 0.37f));
            if (flicker < 0.2f) continue;
            noisePaint.setAlpha((int) (25 * flicker));
            canvas.drawCircle(noiseX[i], noiseY[i], noiseR[i], noisePaint);
        }

        // Soft vignette frame
        borderPaint.setColor(adjustColor(0xF2D3A0, hueShift));
        borderPaint.setAlpha((int) (90 + 40 * pulse));
        RectF frame = new RectF(dp(2f), dp(2f), w - dp(2f), h - dp(2f));
        canvas.drawRoundRect(frame, dp(10f), dp(10f), borderPaint);

        // Corner glints
        borderPaint.setAlpha((int) (120 + 60 * pulse));
        canvas.drawLine(dp(6f), dp(10f), dp(16f), dp(6f), borderPaint);
        canvas.drawLine(w - dp(10f), dp(6f), w - dp(6f), dp(16f), borderPaint);
        canvas.drawLine(dp(6f), h - dp(16f), dp(10f), h - dp(6f), borderPaint);
        canvas.drawLine(w - dp(16f), h - dp(6f), w - dp(6f), h - dp(16f), borderPaint);
    }

    private int adjustColor(int color, float shift) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + shift * 60f + 360f) % 360f;
        hsv[1] = Math.min(1f, hsv[1] + Math.abs(shift) * 0.08f);
        hsv[2] = Math.min(1f, hsv[2] + 0.02f);
        return Color.HSVToColor(hsv);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
