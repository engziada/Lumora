package com.firestick.aura.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.widget.TextView;

import androidx.annotation.NonNull;

/** A content rating such as TV-14 or PG-13, drawn as a small outlined tag inside a line of text. */
public final class RatingBadge extends ReplacementSpan {
    private static final float TEXT_SCALE = 0.78f;

    private final float density;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    private RatingBadge(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(Math.max(1f, density));
    }

    /** The text followed by the rating as a tag; the text alone when there is no rating. */
    public static CharSequence append(Context context, CharSequence base, String rating) {
        String value = rating == null ? "" : rating.trim();
        if (value.isEmpty()) return base;
        SpannableStringBuilder line = new SpannableStringBuilder(base);
        if (line.length() > 0) line.append("  ");
        int start = line.length();
        line.append(value);
        line.setSpan(new RatingBadge(context), start, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return line;
    }

    /**
     * Like {@link #append} for a single-line view: the text is shortened with an ellipsis so the
     * view does not cut the tag off. Before the view is laid out, the text stays whole.
     */
    public static CharSequence fit(TextView view, CharSequence base, String rating) {
        CharSequence full = append(view.getContext(), base, rating);
        int width = view.getWidth() - view.getTotalPaddingLeft() - view.getTotalPaddingRight();
        TextPaint paint = view.getPaint();
        if (full == base || width <= 0 || Layout.getDesiredWidth(full, paint) <= width) return full;
        float tag = Layout.getDesiredWidth(append(view.getContext(), "x", rating), paint) - paint.measureText("x");
        CharSequence shorter = TextUtils.ellipsize(base, paint, Math.max(0f, width - tag), TextUtils.TruncateAt.END);
        return append(view.getContext(), shorter, rating);
    }

    private void prepare(Paint paint) {
        text.set(paint);
        text.setTextSize(paint.getTextSize() * TEXT_SCALE);
        text.setFakeBoldText(true);
        text.setLetterSpacing(0.04f);
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence line, int start, int end, Paint.FontMetricsInt metrics) {
        prepare(paint);
        if (metrics != null) paint.getFontMetricsInt(metrics);
        return Math.round(text.measureText(line, start, end) + 12f * density);
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence line, int start, int end, float x, int top, int y, int bottom,
                     @NonNull Paint paint) {
        prepare(paint);
        float width = text.measureText(line, start, end);
        Paint.FontMetrics own = text.getFontMetrics(), host = paint.getFontMetrics();
        // Centred on the surrounding text rather than sitting on its baseline.
        float baseline = y + (host.ascent + host.descent) / 2f - (own.ascent + own.descent) / 2f;
        float pad = 2f * density, half = outline.getStrokeWidth() / 2f;
        box.set(x + 2f * density + half, baseline + own.ascent - pad / 2f, x + width + 10f * density - half,
                baseline + own.descent + pad / 2f);
        outline.setColor(paint.getColor());
        outline.setAlpha(Math.round(paint.getAlpha() * 0.7f));
        canvas.drawRoundRect(box, 4f * density, 4f * density, outline);
        canvas.drawText(line, start, end, x + 6f * density, baseline, text);
    }
}
