package com.firestick.aura.launcher.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AmbientFramesTest {
    @Test public void drawsEveryOtherFrameAt60Hz() {
        AmbientFrames frames = new AmbientFrames();
        assertTrue(frames.decide(1000L));
        assertFalse(frames.decide(1016L));
        assertTrue(frames.decide(1033L));
        assertFalse(frames.decide(1050L));
        assertTrue(frames.decide(1066L));
    }

    @Test public void everyCallerInOneFrameGetsTheSameAnswer() {
        AmbientFrames frames = new AmbientFrames();
        assertTrue(frames.decide(1000L));
        assertTrue(frames.decide(1000L));
        assertFalse(frames.decide(1016L));
        assertFalse(frames.decide(1016L));
    }

    @Test public void lateFrameDrawsAtOnce() {
        AmbientFrames frames = new AmbientFrames();
        assertTrue(frames.decide(1000L));
        assertTrue(frames.decide(1000L + AmbientFrames.MIN_GAP_MS));
        assertTrue(frames.decide(5000L));
    }

    @Test public void clockGoingBackwardsDraws() {
        AmbientFrames frames = new AmbientFrames();
        assertTrue(frames.decide(5000L));
        assertTrue(frames.decide(100L));
        assertFalse(frames.decide(116L));
    }
}
