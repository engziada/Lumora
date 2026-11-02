package com.firestick.aura.launcher.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.firestick.aura.launcher.R;

/**
 * Compact settings row: label on the left, a graphical checkbox on the right. Replaces the old
 * full-width "Something: ON/OFF" buttons; two of these can share one row like the Lumora.Option
 * buttons they replace. The indicator animates between the empty outline and the accent-filled
 * tick. {@link #setCheckedState(boolean)} applies the preference's state without firing the
 * listener while {@link #toggle()} flips and hands the new value to the listener.
 */
public final class OptionToggleView extends LinearLayout {
    public interface OnCheckedChangeListener {
        void onCheckedChange(boolean checked);
    }

    private final CheckIndicator indicator;
    private final TextView label;
    private OnCheckedChangeListener listener;
    private boolean checked;

    public OptionToggleView(Context context) {
        this(context, null);
    }

    public OptionToggleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        setBackgroundResource(R.drawable.bg_option_selector);
        setFocusable(true);
        setClickable(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        int padding = Math.round(14 * getResources().getDisplayMetrics().density);
        setPadding(padding, 0, padding, 0);

        label = new TextView(context);
        label.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        label.setAllCaps(false);
        label.setTextColor(getResources().getColor(R.color.nova_text));
        label.setTextSize(11);
        addView(label);

        indicator = new CheckIndicator(context);
        int side = Math.round(20 * getResources().getDisplayMetrics().density);
        LayoutParams params = new LayoutParams(side, side);
        params.leftMargin = padding / 2;
        addView(indicator, params);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.OptionToggleView);
        String text = a.getString(R.styleable.OptionToggleView_toggleLabel);
        a.recycle();
        if (text != null) label.setText(text);
        setOnClickListener(v -> toggle());
    }

    public OptionToggleView accentColor(int color) {
        indicator.accent = color;
        indicator.invalidate();
        return this;
    }

    public OptionToggleView listener(OnCheckedChangeListener listener) {
        this.listener = listener;
        return this;
    }

    public boolean isChecked() {
        return checked;
    }

    /** Applies the preference's current state without animation or callbacks. */
    public void setCheckedState(boolean value) {
        checked = value;
        indicator.snap();
    }

    /** Flips the checked state, animates, then notifies the listener with the new value. */
    public void toggle() {
        checked = !checked;
        indicator.animateTo(checked ? 1f : 0f);
        if (listener != null) listener.onCheckedChange(checked);
    }

    @Override public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : .5f);
    }

    /** Rounded-square box: outline when empty, accent fill and tick when checked. */
    private final class CheckIndicator extends View {
        private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path tickPath = new Path();
        private int accent = 0xFFE3A15A;
        private float progress; // 0 = empty, 1 = filled with the tick

        CheckIndicator(Context context) {
            super(context);
            box.setStyle(Paint.Style.STROKE);
            fill.setStyle(Paint.Style.FILL);
            tick.setStyle(Paint.Style.STROKE);
            tick.setStrokeCap(Paint.Cap.ROUND);
            tick.setStrokeJoin(Paint.Join.ROUND);
            tick.setColor(Color.WHITE);
            snap();
        }

        void snap() {
            progress = checked ? 1f : 0f;
            invalidate();
        }

        void animateTo(float target) {
            ValueAnimator animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(140);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w == 0) return;
            float density = getResources().getDisplayMetrics().density;
            box.setStrokeWidth(density * 1.5f);
            tick.setStrokeWidth(density * 2f);
            tickPath.reset();
            tickPath.moveTo(w * .27f, h * .52f);
            tickPath.lineTo(w * .46f, h * .70f);
            tickPath.lineTo(w * .76f, h * .30f);
        }

        @Override protected void onDraw(Canvas canvas) {
            int side = getWidth();
            float radius = side * .24f;
            box.setColor(isEnabled() ? 0x8CF3E6D6 : 0x3DF3E6D6);
            canvas.drawRoundRect(0, 0, side, side, radius, radius, box);
            if (progress <= 0f) return;
            fill.setColor(accent);
            fill.setAlpha((int) (255 * progress));
            canvas.drawRoundRect(0, 0, side, side, radius, radius, fill);
            box.setColor(isEnabled() ? accent : 0x3DF3E6D6);
            box.setStrokeWidth(getResources().getDisplayMetrics().density);
            canvas.drawRoundRect(0, 0, side, side, radius, radius, box);
            float reveal = Math.min(1f, (progress - .35f) / .65f);
            if (reveal > 0f) {
                tick.setPathEffect(null);
                tick.setAlpha((int) (255 * reveal));
                // Draw the first `reveal` share of the tick by clipping from the start point side.
                canvas.save();
                canvas.clipRect(0, 0, side * (0.27f + 0.49f * reveal), side);
                canvas.drawPath(tickPath, tick);
                canvas.restore();
            }
        }
    }
}
