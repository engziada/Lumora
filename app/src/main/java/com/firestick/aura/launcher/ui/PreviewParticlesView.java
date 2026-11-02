package com.firestick.aura.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Generative particle / plasma ambient for the home preview panel. Zero network, fully
 * procedural: a soft plasma of drifting hue-tinted light blobs plus twinkling particles. Every
 * app gets its own hue derived from the package or label name, so moving focus visibly changes
 * the scene, and a short radial burst fires when the seed changes. Drawing runs only while the
 * panel is alive; {@link #stop()} cancels the animation clock so an idle panel costs nothing.
 */
public final class PreviewParticlesView extends View {
    private static final int PARTICLES = 90;
    private static final int BLOBS = 3;
    /** Base hue of the theme's amber; each seed drifts around it. */
    private static final float BASE_HUE = 34f;

    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(1337);
    private float[] px, py, vx, vy, radius, speed, phase;
    private ValueAnimator animator;
    private long lastFrame;
    private float u;                 // accumulated animation time, seconds
    private float hue = BASE_HUE;
    private float burst;             // 0..1, eased to 0 after a seed change
    private boolean active;

    public PreviewParticlesView(Context context) {
        super(context);
        init();
    }

    public PreviewParticlesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        blobPaint.setStyle(Paint.Style.FILL);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) return;
        seedParticles();
    }

    private void seedParticles() {
        if (px == null || px.length != PARTICLES) {
            px = new float[PARTICLES]; py = new float[PARTICLES];
            vx = new float[PARTICLES]; vy = new float[PARTICLES];
            radius = new float[PARTICLES]; speed = new float[PARTICLES]; phase = new float[PARTICLES];
        }
        for (int i = 0; i < PARTICLES; i++) {
            px[i] = random.nextFloat();
            py[i] = random.nextFloat();
            vx[i] = (random.nextFloat() - .5f) * .012f;
            vy[i] = (random.nextFloat() - .5f) * .010f;
            radius[i] = dp(1.1f + random.nextFloat() * 2.6f);
            speed[i] = .25f + random.nextFloat() * .9f;
            phase[i] = random.nextFloat() * 6.28f;
        }
    }

    /** Starts (or retunes) the ambient for an app. {@code seed} is the package or label name. */
    public void start(String seed) {
        if (!active) {
            lastFrame = System.nanoTime();
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(3_600_000L);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(a -> {
                long now = System.nanoTime();
                float seconds = (now - lastFrame) / 1_000_000_000f;
                lastFrame = now;
                u += Math.min(seconds, .06f);
                if (burst > 0f) burst = Math.max(0f, burst - seconds * 2.2f);
                drift(seconds);
                invalidate();
            });
        }
        int hash = seed == null ? 0 : Math.abs(seed.hashCode());
        float target = BASE_HUE + (hash % 100) - 40f; // a wide span around the theme amber
        if (animator == null) {
            // Fresh start after a stop: adopt the seed's hue quietly, no burst.
            hue = target;
        } else if (Math.abs(hue - target) > .5f) {
            // A different app was focused while the panel was alive: recolour and burst.
            hue = target;
            burst = 1f;
            if (vx != null) {
                for (int i = 0; i < PARTICLES; i++) {
                    float dx = px[i] - .5f, dy = py[i] - .5f;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy) + .05f;
                    vx[i] += dx / dist * .05f;
                    vy[i] += dy / dist * .05f;
                }
            }
        }
        active = true;
        setVisibility(VISIBLE);
        if (animator != null && !animator.isStarted()) animator.start();
        invalidate();
    }

    /** Cancels the animation clock; nothing draws while stopped. */
    public void stop() {
        active = false;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        setVisibility(GONE);
        invalidate();
    }

    private void drift(float dt) {
        if (vx == null) return;
        for (int i = 0; i < PARTICLES; i++) {
            px[i] += (vx[i] * speed[i] * dt) + (float) Math.sin(u * .5f + phase[i]) * .0006f;
            py[i] += (vy[i] * speed[i] * dt) + (float) Math.cos(u * .43f + phase[i] * 1.7f) * .0006f;
            px[i] = wrap(px[i]);
            py[i] = wrap(py[i]);
        }
    }

    private static float wrap(float value) {
        if (value < -0.04f) return value + 1.08f;
        if (value > 1.04f) return value - 1.08f;
        return value;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!active) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        // Barely-there veil instead of a solid box: the plasma drifts over the wallpaper, so the
        // panel reads as floating in space with no visible frame.
        canvas.drawColor(0x59140C07);

        // Plasma: soft hue-tinted light blobs drifting across the frame.
        for (int b = 0; b < BLOBS; b++) {
            float phase = b * 2.1f;
            float cx = w * (.5f + .38f * (float) Math.sin(u * (.09f + b * .031f) + phase));
            float cy = h * (.45f + .34f * (float) Math.cos(u * (.075f + b * .026f) + phase * 1.9f));
            float r = Math.max(w, h) * (.42f + .10f * (float) Math.sin(u * .11f + b));
            int color = Color.HSVToColor(new float[]{(hue + b * 18f + 360f) % 360f, .62f, 1f});
            int strong = (int) (64 + 30 * burst);
            int soft = (int) (24 + 20 * burst);
            blobPaint.setShader(new RadialGradient(cx, cy, r, new int[]{
                    Color.argb(strong, Color.red(color), Color.green(color), Color.blue(color)),
                    Color.argb(soft, Color.red(color), Color.green(color), Color.blue(color)),
                    0x00000000}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, blobPaint);
        }

        // Particles: gentle drift plus a shared twinkle and pulse after a seed change.
        for (int i = 0; i < PARTICLES; i++) {
            float twinkle = .5f + .5f * (float) Math.sin(u * speed[i] * 2.3f + phase[i]);
            int color = Color.HSVToColor(new float[]{(hue + i * 1.7f + 360f) % 360f, .55f, 1f});
            int alpha = (int) Math.min(200, 60 + 80 * twinkle + 60 * burst);
            dotPaint.setColor((alpha << 24) | (color & 0xFFFFFF));
            canvas.drawCircle(px[i] * w, py[i] * h, radius[i] * (1f + burst * 1.6f), dotPaint);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
