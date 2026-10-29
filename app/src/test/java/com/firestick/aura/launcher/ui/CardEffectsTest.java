package com.firestick.aura.launcher.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class CardEffectsTest {
    private static final float W = 400f, H = 216f;

    @Test public void normalizeFallsBackToDefault() {
        assertEquals(CardEffects.LIGHTNING, CardEffects.normalize(null));
        assertEquals(CardEffects.LIGHTNING, CardEffects.normalize("sparkles"));
        assertEquals(CardEffects.ARABIC, CardEffects.normalize("arabic"));
        assertEquals(CardEffects.OFF, CardEffects.normalize("off"));
    }

    @Test public void nextCyclesThroughAllEffects() {
        assertEquals(CardEffects.ARABIC, CardEffects.next(CardEffects.LIGHTNING));
        assertEquals(CardEffects.OFF, CardEffects.next(CardEffects.ARABIC));
        assertEquals(CardEffects.LIGHTNING, CardEffects.next(CardEffects.OFF));
        assertEquals(CardEffects.ARABIC, CardEffects.next("unknown"));
    }

    @Test public void boltStaysInsideAndCrossesTheCard() {
        Random random = new Random(7);
        for (int run = 0; run < 500; run++) {
            float[] points = CardEffects.bolt(random, W, H, 8);
            assertEquals(18, points.length);
            assertInside(points);
            boolean vertical = points[1] == 0f && points[points.length - 1] == H;
            boolean horizontal = points[0] == 0f && points[points.length - 2] == W;
            assertTrue("bolt must run edge to edge", vertical || horizontal);
        }
    }

    @Test public void branchStartsOnTheBoltAndStaysInside() {
        Random random = new Random(11);
        for (int run = 0; run < 500; run++) {
            float[] bolt = CardEffects.bolt(random, W, H, 8);
            float[] fork = CardEffects.branch(random, bolt, W, H, 3);
            assertEquals(8, fork.length);
            assertInside(fork);
            boolean onBolt = false;
            for (int i = 0; i < bolt.length; i += 2) {
                if (bolt[i] == fork[0] && bolt[i + 1] == fork[1]) onBolt = true;
            }
            assertTrue(onBolt);
        }
    }

    @Test public void flashAndBreatheStayInRangeAndEndDark() {
        assertEquals(0f, CardEffects.flash(0f), 0f);
        assertEquals(0f, CardEffects.flash(1f), 0f);
        assertEquals(1f, CardEffects.flash(0.06f), 1e-4f);
        assertEquals(0f, CardEffects.breathe(0f), 0f);
        assertEquals(0f, CardEffects.breathe(1f), 0f);
        assertEquals(1f, CardEffects.breathe(0.5f), 1e-4f);
        for (int i = 0; i <= 1000; i++) {
            float t = i / 1000f;
            float flash = CardEffects.flash(t), breathe = CardEffects.breathe(t);
            assertTrue(flash >= 0f && flash <= 1f);
            assertTrue(breathe >= 0f && breathe <= 1f);
        }
    }

    private static void assertInside(float[] points) {
        for (int i = 0; i < points.length; i += 2) {
            assertTrue(points[i] >= 0f && points[i] <= W);
            assertTrue(points[i + 1] >= 0f && points[i + 1] <= H);
        }
    }
}
