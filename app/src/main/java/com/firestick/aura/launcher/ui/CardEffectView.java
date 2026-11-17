package com.firestick.aura.launcher.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Background of the focused card: random lightning strikes or Arabic letters and eight-point stars
 * that fade in and out, in the theme colour, clipped to the card plate. It draws only while the
 * card is focused (and while the last items fade out), so idle cards cost nothing. Frames follow
 * the shared {@link AmbientFrames} cadence, skip the gaps between strikes, and no new items start
 * while a dialog or menu covers Home.
 */
public final class CardEffectView extends View {
    private static final int MAX_BOLTS = 2, MAX_GLYPHS = 6;
    private static final int ICE_WHITE = 0xFFF4F8FF;
    private static final String[] LETTERS = {"\u0646", "\u0648", "\u0639", "\u0647", "\u0645", "\u0644",
            "\u0633", "\u064A", "\u062D", "\u0628", "\u0643", "\u0631", "\u0636", "\u0646\u0648\u0631",
            "\u0636\u064A\u0627\u0621"};

    private static final class Bolt {
        final Path main = new Path(), forks = new Path();
        final long born, life;

        Bolt(long born, long life) {
            this.born = born;
            this.life = life;
        }
    }

    private static final class Glyph {
        final String text;
        final float x, y, size, rotation, drift;
        final long born, life;

        Glyph(String text, float x, float y, float size, float rotation, float drift, long born, long life) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.size = size;
            this.rotation = rotation;
            this.drift = drift;
            this.born = born;
            this.life = life;
        }
    }

    private final Random random = new Random();
    private final List<Bolt> bolts = new ArrayList<>();
    private final List<Glyph> glyphs = new ArrayList<>();
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path starShape = new Path();
    private final Runnable tick = this::tick;
    private final float density, radius;
    private String mode = CardEffects.DEFAULT;
    private int accent = 0xFFDCEAFF, boltCore = 0xFFEAF2FF;
    private boolean active, ticking, painted;
    private long nextSpawn, tickDue;

    public CardEffectView(Context context) {
        this(context, null);
    }

    public CardEffectView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        core.setStyle(Paint.Style.STROKE);
        core.setStrokeWidth(1.2f * density);
        core.setStrokeJoin(Paint.Join.ROUND);
        core.setStrokeCap(Paint.Cap.ROUND);
        halo.setStyle(Paint.Style.STROKE);
        halo.setStrokeWidth(4.5f * density);
        halo.setStrokeJoin(Paint.Join.ROUND);
        halo.setStrokeCap(Paint.Cap.ROUND);
        letter.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        letter.setTextAlign(Paint.Align.CENTER);
        star.setStyle(Paint.Style.STROKE);
        buildStar();
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Eight-point star (two squares, one turned 45 degrees) of radius 1, scaled when drawn. */
    private void buildStar() {
        for (int square = 0; square < 2; square++) {
            double offset = square * Math.PI / 4;
            for (int i = 0; i < 4; i++) {
                double angle = offset + i * Math.PI / 2;
                float x = (float) Math.cos(angle), y = (float) Math.sin(angle);
                if (i == 0) starShape.moveTo(x, y);
                else starShape.lineTo(x, y);
            }
            starShape.close();
        }
    }

    /** Effect id from {@link CardEffects} and the theme colour. */
    public void setEffect(String effect, int accentColor) {
        String normalized = CardEffects.normalize(effect);
        if (!normalized.equals(mode)) reset();
        mode = normalized;
        accent = accentColor;
        boltCore = blend(accentColor, ICE_WHITE, 0.6f);
        if (CardEffects.OFF.equals(mode)) reset();
    }

    public void start() {
        if (CardEffects.OFF.equals(mode)) return;
        if (!active) nextSpawn = SystemClock.uptimeMillis() + 120L;
        active = true;
        schedule(0L);
    }

    /** Stops new strikes; the ones on screen finish fading. */
    public void stop() {
        active = false;
    }

    public void reset() {
        active = false;
        clearItems();
    }

    /** Drops every item and the pending tick; the next draw shows an empty card. */
    private void clearItems() {
        bolts.clear();
        glyphs.clear();
        painted = false;
        removeCallbacks(tick);
        ticking = false;
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clip.reset();
        clip.addRoundRect(new RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW);
        bolts.clear();
        glyphs.clear();
        if (active) schedule(0L);
    }

    @Override protected void onDetachedFromWindow() {
        reset();
        super.onDetachedFromWindow();
    }

    @Override public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && active) schedule(0L);
    }

    /**
     * Hidden by another app or by a gone parent (tab switch), the card is no longer drawn, so the
     * frame that would clear its last item never comes. Stop ticking and drop the items at once
     * rather than waking every frame in the background; a still-focused card resumes when shown.
     */
    @Override public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (!isVisible) clearItems();
        else if (active) schedule(0L);
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        long now = SystemClock.uptimeMillis();
        canvas.save();
        canvas.clipPath(clip);
        drawBolts(canvas, now);
        drawGlyphs(canvas, now);
        canvas.restore();
        painted = !bolts.isEmpty() || !glyphs.isEmpty();
    }

    /**
     * One step per display frame while something is on screen: spawn when due, redraw on the
     * shared ambient cadence, then sleep until the next strike once the card is empty. No new
     * items start while another window (dialog, menu) has focus.
     */
    private void tick() {
        ticking = false;
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || !isAttachedToWindow()) return;
        long now = SystemClock.uptimeMillis();
        prune(now);
        boolean spawning = active && hasWindowFocus();
        if (spawning && now >= nextSpawn) spawn(now, w, h);
        if (!bolts.isEmpty() || !glyphs.isEmpty() || painted) {
            // A painted but now empty card needs one more frame to clear the last faint item.
            if (AmbientFrames.isDrawFrame(AnimationUtils.currentAnimationTimeMillis())) invalidate();
            schedule(0L);
        } else if (spawning) {
            schedule(nextSpawn - now);
        }
    }

    /** Runs {@link #tick()} on a display frame after {@code delayMs}, keeping the earlier of two requests. */
    private void schedule(long delayMs) {
        long due = SystemClock.uptimeMillis() + Math.max(0L, delayMs);
        if (ticking && tickDue <= due) return;
        removeCallbacks(tick);
        ticking = true;
        tickDue = due;
        if (delayMs <= 0L) postOnAnimation(tick);
        else postOnAnimationDelayed(tick, delayMs);
    }

    private void prune(long now) {
        Iterator<Bolt> bolt = bolts.iterator();
        while (bolt.hasNext()) {
            Bolt next = bolt.next();
            if (now - next.born >= next.life) bolt.remove();
        }
        Iterator<Glyph> glyph = glyphs.iterator();
        while (glyph.hasNext()) {
            Glyph next = glyph.next();
            if (now - next.born >= next.life) glyph.remove();
        }
    }

    private void spawn(long now, int w, int h) {
        if (CardEffects.LIGHTNING.equals(mode)) {
            if (bolts.size() < MAX_BOLTS) bolts.add(newBolt(now, w, h));
            nextSpawn = now + 350L + random.nextInt(1100);
        } else if (CardEffects.ARABIC.equals(mode)) {
            if (glyphs.size() < MAX_GLYPHS) glyphs.add(newGlyph(now, w, h));
            nextSpawn = now + 260L + random.nextInt(560);
        }
    }

    private Bolt newBolt(long now, int w, int h) {
        Bolt bolt = new Bolt(now, 520L + random.nextInt(320));
        float[] points = CardEffects.bolt(random, w, h, 7 + random.nextInt(4));
        addPolyline(bolt.main, points);
        int forks = random.nextInt(3);
        for (int i = 0; i < forks; i++) addPolyline(bolt.forks, CardEffects.branch(random, points, w, h, 3 + random.nextInt(2)));
        return bolt;
    }

    private static void addPolyline(Path path, float[] points) {
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
    }

    private Glyph newGlyph(long now, int w, int h) {
        boolean isStar = random.nextInt(4) == 0;
        float size = h * (0.16f + 0.14f * random.nextFloat());
        float margin = size * 0.6f;
        float x = margin + random.nextFloat() * Math.max(1f, w - 2 * margin);
        float y = margin + random.nextFloat() * Math.max(1f, h - 2 * margin);
        float rotation = (random.nextFloat() * 2f - 1f) * 18f;
        float drift = (4f + 6f * random.nextFloat()) * density;
        String text = isStar ? null : LETTERS[random.nextInt(LETTERS.length)];
        return new Glyph(text, x, y, size, rotation, drift, now, 1800L + random.nextInt(1600));
    }

    private void drawBolts(Canvas canvas, long now) {
        Iterator<Bolt> it = bolts.iterator();
        while (it.hasNext()) {
            Bolt bolt = it.next();
            float t = (now - bolt.born) / (float) bolt.life;
            if (t >= 1f) {
                it.remove();
                continue;
            }
            float a = CardEffects.flash(t);
            halo.setColor(withAlpha(accent, 0.4f * a));
            core.setColor(withAlpha(boltCore, a));
            canvas.drawPath(bolt.main, halo);
            canvas.drawPath(bolt.main, core);
            halo.setColor(withAlpha(accent, 0.25f * a));
            core.setColor(withAlpha(boltCore, 0.6f * a));
            canvas.drawPath(bolt.forks, halo);
            canvas.drawPath(bolt.forks, core);
        }
    }

    private void drawGlyphs(Canvas canvas, long now) {
        Iterator<Glyph> it = glyphs.iterator();
        while (it.hasNext()) {
            Glyph glyph = it.next();
            float t = (now - glyph.born) / (float) glyph.life;
            if (t >= 1f) {
                it.remove();
                continue;
            }
            int color = withAlpha(blend(accent, ICE_WHITE, 0.25f), 0.55f * CardEffects.breathe(t));
            canvas.save();
            canvas.translate(glyph.x, glyph.y - glyph.drift * t);
            canvas.rotate(glyph.rotation + 10f * t);
            if (glyph.text == null) {
                float scale = glyph.size * 0.42f;
                canvas.scale(scale, scale);
                star.setColor(color);
                star.setStrokeWidth(1f * density / scale);
                canvas.drawPath(starShape, star);
            } else {
                letter.setColor(color);
                letter.setTextSize(glyph.size);
                canvas.drawText(glyph.text, 0f, glyph.size * 0.35f, letter);
            }
            canvas.restore();
        }
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int blend(int from, int to, float amount) {
        int r = Math.round(((from >> 16) & 0xFF) * (1 - amount) + ((to >> 16) & 0xFF) * amount);
        int g = Math.round(((from >> 8) & 0xFF) * (1 - amount) + ((to >> 8) & 0xFF) * amount);
        int b = Math.round((from & 0xFF) * (1 - amount) + (to & 0xFF) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
