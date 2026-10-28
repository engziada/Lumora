package com.firestick.aura.launcher.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LongPressTest {
    @Test
    public void shortPressNeverFiresAndUpPassesThrough() {
        LongPress press = new LongPress(600);
        press.down(1000);
        assertFalse(press.check(1599));
        assertFalse(press.up());
        assertFalse(press.isPressed());
    }

    @Test
    public void holdFiresOnceAndConsumesUp() {
        LongPress press = new LongPress(600);
        press.down(1000);
        assertTrue(press.check(1600));
        assertTrue(press.hasFired());
        assertFalse(press.check(1700));
        assertTrue(press.up());
        assertFalse(press.hasFired());
    }

    @Test
    public void checkWithoutPressDoesNothing() {
        LongPress press = new LongPress(600);
        assertFalse(press.check(10_000));
        assertFalse(press.up());
    }

    @Test
    public void newPressResetsPreviousState() {
        LongPress press = new LongPress(600);
        press.down(0);
        assertTrue(press.check(700));
        press.down(5000);
        assertFalse(press.hasFired());
        assertFalse(press.check(5100));
        assertTrue(press.check(5600));
    }

    @Test
    public void defaultHoldIsAboveAndroidLongPress() {
        assertEquals(LongPress.HOLD_MS, new LongPress().holdMs());
        assertTrue(LongPress.HOLD_MS > 500);
    }
}
