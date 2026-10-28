package com.firestick.aura.launcher.manager;

import java.util.Locale;

/** Plain-Java parsing and formatting for the live device readings. */
public final class DeviceStats {
    private DeviceStats() {
    }

    /**
     * Parses the aggregate {@code cpu} line of /proc/stat.
     *
     * @return {@code {idle, total}} jiffies, or {@code null} when the line is not a cpu line
     */
    public static long[] parseCpuLine(String line) {
        if (line == null || !line.startsWith("cpu ")) return null;
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 5) return null;
        long total = 0, idle = 0;
        try {
            for (int i = 1; i < parts.length; i++) {
                long value = Long.parseLong(parts[i]);
                total += value;
                if (i == 4 || i == 5) idle += value; // idle + iowait
            }
        } catch (NumberFormatException exception) {
            return null;
        }
        return new long[]{idle, total};
    }

    /** @return busy percentage between two samples (0–100), or -1 when it cannot be computed */
    public static int cpuPercent(long[] previous, long[] current) {
        if (previous == null || current == null) return -1;
        long total = current[1] - previous[1];
        long idle = current[0] - previous[0];
        if (total <= 0 || idle < 0 || idle > total) return -1;
        return (int) Math.round(100.0 * (total - idle) / total);
    }

    /** @return {@code used / total} as 0–100, or 0 when {@code total} is not positive */
    public static int percent(long used, long total) {
        if (total <= 0) return 0;
        long clamped = Math.max(0L, Math.min(used, total));
        return (int) Math.round(100.0 * clamped / total);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) return "?";
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb);
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1.0) return String.format(Locale.US, "%.0f MB", mb);
        return String.format(Locale.US, "%d KB", bytes / 1024);
    }

    public static String formatUptime(long millis) {
        long minutes = Math.max(0L, millis) / 60_000L;
        long days = minutes / (24 * 60);
        long hours = (minutes / 60) % 24;
        long mins = minutes % 60;
        if (days > 0) return String.format(Locale.US, "%d d %d h", days, hours);
        if (hours > 0) return String.format(Locale.US, "%d h %d min", hours, mins);
        return String.format(Locale.US, "%d min", mins);
    }

    /** Thermal zones report millidegrees on most kernels and degrees on a few. */
    public static float normaliseTemperature(long raw) {
        float celsius = raw > 1000 ? raw / 1000f : raw;
        return celsius >= 10f && celsius <= 125f ? celsius : Float.NaN;
    }
}
