package com.firestick.aura.launcher.manager;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeviceStatsTest {
    @Test
    public void parseCpuLineSumsIdleAndIowait() {
        long[] sample = DeviceStats.parseCpuLine("cpu  100 20 30 400 50 0 5 0 0 0");
        assertArrayEquals(new long[]{450L, 605L}, sample);
    }

    @Test
    public void parseCpuLineRejectsOtherLines() {
        assertNull(DeviceStats.parseCpuLine("cpu0 1 2 3 4 5"));
        assertNull(DeviceStats.parseCpuLine("intr 1 2 3"));
        assertNull(DeviceStats.parseCpuLine("cpu  a b c d"));
        assertNull(DeviceStats.parseCpuLine(null));
    }

    @Test
    public void cpuPercentFromTwoSamples() {
        assertEquals(25, DeviceStats.cpuPercent(new long[]{100, 200}, new long[]{175, 300}));
        assertEquals(-1, DeviceStats.cpuPercent(null, new long[]{1, 2}));
        assertEquals(-1, DeviceStats.cpuPercent(new long[]{100, 200}, new long[]{100, 200}));
    }

    @Test
    public void percentClampsAndHandlesZeroTotal() {
        assertEquals(50, DeviceStats.percent(50, 100));
        assertEquals(100, DeviceStats.percent(150, 100));
        assertEquals(0, DeviceStats.percent(-5, 100));
        assertEquals(0, DeviceStats.percent(5, 0));
    }

    @Test
    public void formatBytesPicksUnit() {
        assertEquals("1.5 GB", DeviceStats.formatBytes(1610612736L));
        assertEquals("512 MB", DeviceStats.formatBytes(512L * 1024 * 1024));
        assertEquals("4 KB", DeviceStats.formatBytes(4096L));
        assertEquals("?", DeviceStats.formatBytes(-1L));
    }

    @Test
    public void formatUptime() {
        assertEquals("12 min", DeviceStats.formatUptime(12 * 60_000L));
        assertEquals("3 h 5 min", DeviceStats.formatUptime((3 * 60 + 5) * 60_000L));
        assertEquals("2 d 4 h", DeviceStats.formatUptime((2 * 24 * 60 + 4 * 60 + 30) * 60_000L));
    }

    @Test
    public void normaliseTemperature() {
        assertEquals(48.5f, DeviceStats.normaliseTemperature(48500L), 0.001f);
        assertEquals(52f, DeviceStats.normaliseTemperature(52L), 0.001f);
        assertTrue(Float.isNaN(DeviceStats.normaliseTemperature(-1L)));
        assertTrue(Float.isNaN(DeviceStats.normaliseTemperature(0L)));
    }
}
