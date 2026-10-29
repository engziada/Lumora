package com.firestick.aura.launcher.ui;

import java.util.Random;

/**
 * Pure maths for the focused-card background effects (lightning strikes, Arabic ornaments).
 * No Android types, so it runs in JVM unit tests.
 */
public final class CardEffects {
    public static final String LIGHTNING = "lightning";
    public static final String ARABIC = "arabic";
    public static final String OFF = "off";
    public static final String DEFAULT = LIGHTNING;
    private static final String[] ORDER = {LIGHTNING, ARABIC, OFF};

    private CardEffects() {
    }

    /** Unknown or missing values fall back to the default effect. */
    public static String normalize(String mode) {
        for (String known : ORDER) if (known.equals(mode)) return known;
        return DEFAULT;
    }

    /** Lightning, then Arabic ornaments, then Off, then back to Lightning. */
    public static String next(String mode) {
        String current = normalize(mode);
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i].equals(current)) return ORDER[(i + 1) % ORDER.length];
        }
        return DEFAULT;
    }

    /**
     * A jagged bolt crossing the card, as x,y pairs inside [0,w] x [0,h]. It runs top to bottom or
     * left to right; inner points are pushed sideways at random.
     */
    public static float[] bolt(Random random, float w, float h, int segments) {
        int count = Math.max(2, segments);
        boolean vertical = random.nextBoolean();
        float x0, y0, x1, y1;
        if (vertical) {
            x0 = w * (0.1f + 0.8f * random.nextFloat());
            x1 = w * (0.1f + 0.8f * random.nextFloat());
            y0 = 0f;
            y1 = h;
        } else {
            y0 = h * (0.15f + 0.7f * random.nextFloat());
            y1 = h * (0.15f + 0.7f * random.nextFloat());
            x0 = 0f;
            x1 = w;
        }
        float jitter = 0.12f * (vertical ? w : h);
        float[] points = new float[(count + 1) * 2];
        for (int i = 0; i <= count; i++) {
            float t = i / (float) count;
            float x = x0 + (x1 - x0) * t;
            float y = y0 + (y1 - y0) * t;
            if (i > 0 && i < count) {
                float offset = (random.nextFloat() * 2f - 1f) * jitter;
                if (vertical) x += offset;
                else y += offset;
            }
            points[2 * i] = clamp(x, 0f, w);
            points[2 * i + 1] = clamp(y, 0f, h);
        }
        return points;
    }

    /** A short fork that leaves an inner point of {@code bolt} at 25-55 degrees from its course. */
    public static float[] branch(Random random, float[] bolt, float w, float h, int segments) {
        int pointCount = bolt.length / 2;
        int count = Math.max(2, segments);
        int start = pointCount > 2 ? 1 + random.nextInt(pointCount - 2) : 0;
        float sx = bolt[2 * start], sy = bolt[2 * start + 1];
        float dx = bolt[bolt.length - 2] - bolt[0], dy = bolt[bolt.length - 1] - bolt[1];
        float length = (float) Math.hypot(dx, dy);
        if (length <= 0f) {
            dx = 0f;
            dy = 1f;
            length = 1f;
        }
        double angle = Math.toRadians(25 + 30 * random.nextFloat()) * (random.nextBoolean() ? 1 : -1);
        float ux = dx / length, uy = dy / length;
        float bx = (float) (ux * Math.cos(angle) - uy * Math.sin(angle));
        float by = (float) (ux * Math.sin(angle) + uy * Math.cos(angle));
        float reach = Math.min(w, h) * (0.3f + 0.3f * random.nextFloat());
        float jitter = reach * 0.15f;
        float[] points = new float[(count + 1) * 2];
        for (int i = 0; i <= count; i++) {
            float t = i / (float) count;
            float x = sx + bx * reach * t;
            float y = sy + by * reach * t;
            if (i > 0) {
                float offset = (random.nextFloat() * 2f - 1f) * jitter;
                x += -by * offset;
                y += bx * offset;
            }
            points[2 * i] = clamp(x, 0f, w);
            points[2 * i + 1] = clamp(y, 0f, h);
        }
        return points;
    }

    /** Lightning brightness over its life t in [0,1]: fast flash, one flicker, then a fade. */
    public static float flash(float t) {
        if (t <= 0f || t >= 1f) return 0f;
        if (t < 0.06f) return t / 0.06f;
        if (t < 0.14f) return 1f - 0.6f * (t - 0.06f) / 0.08f;
        if (t < 0.2f) return 0.4f + 0.5f * (t - 0.14f) / 0.06f;
        float u = (t - 0.2f) / 0.8f;
        return 0.9f * (1f - u) * (1f - u);
    }

    /** Ornament opacity over its life t in [0,1]: smooth rise and fall, 0 at both ends. */
    public static float breathe(float t) {
        if (t <= 0f || t >= 1f) return 0f;
        return (float) Math.sin(Math.PI * t);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
