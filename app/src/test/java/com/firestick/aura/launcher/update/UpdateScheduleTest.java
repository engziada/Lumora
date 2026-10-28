package com.firestick.aura.launcher.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateScheduleTest {
    private static final long HOUR = 3_600_000L;

    @Test
    public void dueAfterInterval() {
        long now = 1_000L * HOUR;
        assertTrue(UpdateSchedule.isDue(0L, now, 24));
        assertFalse(UpdateSchedule.isDue(now - 23 * HOUR, now, 24));
        assertTrue(UpdateSchedule.isDue(now - 24 * HOUR, now, 24));
        assertFalse(UpdateSchedule.isDue(now - 71 * HOUR, now, 72));
    }

    @Test
    public void offNeverDueAndClockSkewIsDue() {
        assertFalse(UpdateSchedule.isDue(0L, 5 * HOUR, 0));
        assertTrue(UpdateSchedule.isDue(10 * HOUR, 5 * HOUR, 24));
    }

    @Test
    public void nextCyclesThroughIntervals() {
        assertEquals(72L, UpdateSchedule.next(24L));
        assertEquals(168L, UpdateSchedule.next(72L));
        assertEquals(0L, UpdateSchedule.next(168L));
        assertEquals(24L, UpdateSchedule.next(0L));
        assertEquals(UpdateSchedule.DEFAULT_HOURS, UpdateSchedule.next(5L));
    }
}
