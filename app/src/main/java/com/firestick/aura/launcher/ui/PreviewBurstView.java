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
import android.view.animation.DecelerateInterpolator;

/** Brief procedural nebula flash; idle state schedules no drawing or animation. */
public final class PreviewBurstView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float progress;
    private boolean opening;
    private RadialGradient glow;

    public PreviewBurstView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        glow = new RadialGradient(0, 0, Math.max(1, Math.min(w, h) * .5f),
                new int[]{0xFFFFF1D4, 0xAAE3A15A, 0x557B67BD, 0x007B67BD},
                new float[]{0, .12f, .45f, 1}, Shader.TileMode.CLAMP);
    }

    public void burst(boolean opening) {
        stop();
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        this.opening = opening;
        setVisibility(VISIBLE);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(opening ? 520 : 320);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            if (progress >= 1f) setVisibility(GONE);
            invalidate();
        });
        animator.start();
    }

    public void stop() {
        if (animator != null) { animator.cancel(); animator = null; }
        setVisibility(GONE);
    }

    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas canvas) {
        float p = progress;
        float radius = Math.min(getWidth(), getHeight()) * .45f;
        float spread = opening ? .12f + p * 1.2f : 1f - p * .9f;
        float fade = (1f - p) * (opening ? .7f : .4f);
        canvas.save();
        canvas.translate(getWidth() * .5f, getHeight() * .5f);
        canvas.save();
        canvas.scale(spread * 2.1f, spread);
        paint.setShader(glow);
        paint.setAlpha((int) (255 * fade));
        canvas.drawCircle(0, 0, radius, paint);
        paint.setShader(null);
        canvas.restore();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(getResources().getDisplayMetrics().density * 1.2f);
        paint.setColor(Color.argb((int) (160 * fade), 242, 211, 160));
        canvas.drawOval(-radius * spread * 1.65f, -radius * spread * .6f,
                radius * spread * 1.65f, radius * spread * .6f, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 30; i++) {
            double angle = i * 2.399963;
            float distance = radius * spread * (.35f + (i % 7) / 9f);
            float x = (float) Math.cos(angle) * distance * 1.7f;
            float y = (float) Math.sin(angle) * distance * .75f;
            paint.setColor(Color.argb((int) (255 * fade), 255, 228, 185));
            canvas.drawCircle(x, y, (1 + i % 3) * getResources().getDisplayMetrics().density * .6f, paint);
        }
        canvas.restore();
    }
}
