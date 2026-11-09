package com.firestick.aura.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.R;

import java.util.Locale;
import java.util.Random;

/**
 * Transparent, procedural 3D particle sphere for the home panel. Focus changes add rotational
 * energy; after a short sphere intro the same particles form the focused app name, burst apart,
 * and settle back into the sphere. No background, frame, network, video, or OpenGL is involved.
 */
public final class PreviewParticlesView extends View {
    private static final int PARTICLES = 420;
    private static final float INTRO_SECONDS = .35f;
    private static final float FORM_SECONDS = .72f;
    private static final float HOLD_SECONDS = 1.05f;
    private static final float EXPLODE_SECONDS = .58f;

    private static final int SPHERE = 0;
    private static final int FORMING = 1;
    private static final int HOLDING = 2;
    private static final int EXPLODING = 3;

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(1337);
    private final float[] baseX = new float[PARTICLES];
    private final float[] baseY = new float[PARTICLES];
    private final float[] baseZ = new float[PARTICLES];
    private final float[] shell = new float[PARTICLES];
    private final float[] size = new float[PARTICLES];
    private final float[] phase = new float[PARTICLES];
    private final float[] burstX = new float[PARTICLES];
    private final float[] burstY = new float[PARTICLES];
    private final float[] textX = new float[PARTICLES];
    private final float[] textY = new float[PARTICLES];

    private ValueAnimator animator;
    private long lastFrame;
    private long lastDraw;
    private float time;
    private float spin;
    private float energy;
    private float kick = .3f;
    private float morph;
    private float morphFrom;
    private float scatter;
    private float phaseTime;
    private int state = SPHERE;
    private boolean active;
    private boolean locked;
    private boolean textReady;
    private String label = "";
    private int accent;
    private int light;

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
        dotPaint.setStyle(Paint.Style.FILL);
        accent = ContextCompat.getColor(getContext(), R.color.nova_accent);
        light = ContextCompat.getColor(getContext(), R.color.nova_accent_2);
        seedSphere();
    }

    /** A deterministic Fibonacci sphere keeps the point distribution even without a 3D engine. */
    private void seedSphere() {
        final double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < PARTICLES; i++) {
            float y = 1f - 2f * (i + .5f) / PARTICLES;
            float ring = (float) Math.sqrt(Math.max(0f, 1f - y * y));
            double angle = i * goldenAngle;
            baseX[i] = (float) Math.cos(angle) * ring;
            baseY[i] = y;
            baseZ[i] = (float) Math.sin(angle) * ring;
            shell[i] = .88f + random.nextFloat() * .14f;
            size[i] = dp(.65f + random.nextFloat() * .85f);
            phase[i] = random.nextFloat() * 6.28318f;
            float burstAngle = random.nextFloat() * 6.28318f;
            float force = .55f + random.nextFloat() * .85f;
            burstX[i] = (float) Math.cos(burstAngle) * force;
            burstY[i] = (float) Math.sin(burstAngle) * force;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) buildTextTargets(w, h);
    }

    /** Starts a fresh focus scene. {@code seed} varies the movement; {@code appName} becomes particles. */
    public void start(String seed, String appName) {
        start(seed, appName, false);
    }

    public void start(String seed, String appName, boolean appLocked) {
        locked = appLocked;
        boolean continuous = active;
        label = appName == null ? "" : appName.trim();
        kick = ((seed == null ? 0 : seed.hashCode()) & 1) == 0 ? .34f : -.34f;
        energy = 1f;
        if (!continuous) morph = 0f;
        scatter = 0f;
        morphFrom = morph;
        phaseTime = -INTRO_SECONDS;
        state = FORMING;
        active = true;
        setVisibility(VISIBLE);
        if (getWidth() > 0 && getHeight() > 0) buildTextTargets(getWidth(), getHeight());
        if (ValueAnimator.areAnimatorsEnabled()) {
            if (animator == null) startClock();
        } else {
            morph = 1f;
            state = HOLDING; // Keep the name and lock readable with system animations disabled.
        }
        invalidate();
    }

    private void startClock() {
        if (animator != null) animator.cancel();
        lastFrame = System.nanoTime();
        lastDraw = 0L;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(100_000L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            long now = System.nanoTime();
            float dt = (now - lastFrame) / 1_000_000_000f;
            lastFrame = now;
            update(dt);
            // Thirty frames per second is enough for this scene and leaves headroom on Fire TV.
            if (now - lastDraw >= 33_000_000L) {
                lastDraw = now;
                invalidate();
            }
        });
        animator.start();
    }

    private void update(float dt) {
        time += dt;
        // Bound the sphere's motion, but use elapsed time for formation and orbit timing.
        // Dropped frames must not leave a name stuck in its intro on slower Fire TVs.
        float motionStep = Math.min(dt, .05f);
        spin += motionStep * (.30f + energy * 1.5f);
        energy = Math.max(0f, energy - motionStep * .72f);
        if (state == SPHERE) return;
        phaseTime += dt;
        if (state == FORMING) {
            if (!textReady) {
                // A newly revealed panel may not have been measured on the first frame.
                // Wait for onSizeChanged to build the name instead of abandoning formation.
                phaseTime = -INTRO_SECONDS;
                return;
            } else if (phaseTime >= 0f) {
                float progress = Math.min(1f, phaseTime / FORM_SECONDS);
                morph = lerp(morphFrom, 1f, ease(progress));
                if (progress >= 1f) changeState(HOLDING);
            }
        } else if (state == HOLDING && !locked && phaseTime >= HOLD_SECONDS) {
            changeState(EXPLODING);
        } else if (state == EXPLODING) {
            float progress = Math.min(1f, phaseTime / EXPLODE_SECONDS);
            morph = 1f - ease(progress);
            scatter = (float) Math.sin(progress * Math.PI);
            if (progress >= 1f) {
                morph = 0f;
                scatter = 0f;
                changeState(SPHERE);
            }
        }
    }

    private void changeState(int next) {
        state = next;
        phaseTime = 0f;
        if (next == FORMING) morphFrom = morph;
    }

    /** Cancels the animation clock; a hidden panel performs no particle work. */
    public void stop() {
        active = false;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        setVisibility(GONE);
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!active) return;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        float cx = w * .5f, cy = h * .5f;
        float radius = Math.min(w, h) * .35f;
        float tilt = .24f + .08f * (float) Math.sin(time * .31f) + kick * energy;
        float cosY = (float) Math.cos(spin), sinY = (float) Math.sin(spin);
        float cosX = (float) Math.cos(tilt), sinX = (float) Math.sin(tilt);

        for (int i = 0; i < PARTICLES; i++) {
            float instability = 1f + .026f * (float) Math.sin(time * 1.8f + phase[i])
                    + energy * .055f * (float) Math.sin(time * 7f + phase[i] * 2f);
            float r = shell[i] * instability;
            float x = baseX[i] * r;
            float y = baseY[i] * r;
            float z = baseZ[i] * r;

            float rotatedX = x * cosY - z * sinY;
            float rotatedZ = x * sinY + z * cosY;
            float rotatedY = y * cosX - rotatedZ * sinX;
            rotatedZ = y * sinX + rotatedZ * cosX;

            float perspective = 2.8f / (2.8f - rotatedZ);
            float sphereX = cx + rotatedX * radius * perspective;
            float sphereY = cy + rotatedY * radius * perspective;
            float drawX = lerp(sphereX, textX[i], morph);
            float drawY = lerp(sphereY, textY[i], morph);
            if (scatter > 0f) {
                drawX += burstX[i] * scatter * radius * 1.45f;
                drawY += burstY[i] * scatter * radius * 1.45f;
            }

            float depth = Math.max(0f, Math.min(1f, (rotatedZ + 1f) * .5f));
            int alpha = (int) lerp(42f + depth * 198f, 225f, morph);
            alpha = (int) (alpha * (1f - scatter * .35f));
            int rgb = depth > .58f || morph > .5f ? light : accent;
            dotPaint.setColor(Color.argb(Math.max(0, Math.min(255, alpha)),
                    Color.red(rgb), Color.green(rgb), Color.blue(rgb)));
            float dot = lerp(size[i] * (.65f + depth * .85f), size[i] * 1.05f, morph);
            canvas.drawCircle(drawX, drawY, dot, dotPaint);
        }
        if (locked) drawOrbitingLock(canvas, w, h);
    }

    /** An upright dotted padlock travels around the name on an elliptical orbit. */
    private void drawOrbitingLock(Canvas canvas, int w, int h) {
        float angle = time * 1.15f;
        float x = w * .5f + (float) Math.cos(angle) * w * .39f;
        float y = h * .5f + (float) Math.sin(angle) * h * .30f;
        float unit = Math.min(dp(18f), h * .09f);
        dotPaint.setColor(light);
        dotPaint.setAlpha((int) (225 * morph));
        for (int i = 0; i < 64; i++) {
            float px, py;
            if (i < 24) {
                double arc = Math.PI + Math.PI * i / 23;
                px = (float) Math.cos(arc) * .55f;
                py = -.32f + (float) Math.sin(arc) * .65f;
            } else if (i < 60) {
                float edge = (i - 24) / 9f;
                if (edge < 1) { px = -.8f + 1.6f * edge; py = -.3f; }
                else if (edge < 2) { px = .8f; py = -.3f + (edge - 1); }
                else if (edge < 3) { px = .8f - 1.6f * (edge - 2); py = .7f; }
                else { px = -.8f; py = .7f - (edge - 3); }
            } else { px = 0; py = .05f + (i - 60) * .1f; }
            // Assemble the lock from scattered points in step with the app name.
            float spread = (1f - morph) * unit * 3;
            canvas.drawCircle(x + px * unit + (float) Math.cos(i * 2.4) * spread,
                    y + py * unit + (float) Math.sin(i * 2.4) * spread, dp(.9f), dotPaint);
        }
    }

    /** Rasterises one name, then distributes the existing sphere particles over its opaque pixels. */
    private void buildTextTargets(int w, int h) {
        textReady = false;
        if (label.isEmpty()) return;
        String text = label.toUpperCase(Locale.getDefault());
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        try {
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            textPaint.setTextAlign(Paint.Align.CENTER);
            float textSize = Math.min(h * .27f, dp(58f));
            float minimum = dp(15f);
            textPaint.setTextSize(textSize);
            while (textSize > minimum && textPaint.measureText(text) > w * (locked ? .68f : .88f)) {
                textSize = Math.max(minimum, textSize - dp(2f));
                textPaint.setTextSize(textSize);
            }
            textPaint.setTextScaleX(Math.min(1f, w * (locked ? .68f : .88f) / Math.max(1f, textPaint.measureText(text))));
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float baseline = h * .5f - (metrics.ascent + metrics.descent) * .5f;
            new Canvas(bitmap).drawText(text, w * .5f, baseline, textPaint);

            int step = Math.max(2, Math.round(dp(2f)));
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
            int capacity = ((w + step - 1) / step) * ((h + step - 1) / step);
            int[] ink = new int[Math.max(1, capacity)];
            int count = 0;
            for (int y = 0; y < h; y += step) {
                for (int x = 0; x < w; x += step) {
                    if (Color.alpha(pixels[y * w + x]) > 100) ink[count++] = y * w + x;
                }
            }
            if (count == 0) return;
            Random textRandom = new Random(text.hashCode());
            for (int i = count - 1; i > 0; i--) {
                int other = textRandom.nextInt(i + 1);
                int swap = ink[i]; ink[i] = ink[other]; ink[other] = swap;
            }
            for (int i = 0; i < PARTICLES; i++) {
                int pixel = ink[i < count ? i : textRandom.nextInt(count)];
                textX[i] = pixel % w + (textRandom.nextFloat() - .5f) * step * .45f;
                textY[i] = pixel / w + (textRandom.nextFloat() - .5f) * step * .45f;
            }
            textReady = true;
        } finally {
            bitmap.recycle();
        }
    }

    private static float ease(float value) {
        return value < .5f ? 4f * value * value * value
                : 1f - (float) Math.pow(-2f * value + 2f, 3) * .5f;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
