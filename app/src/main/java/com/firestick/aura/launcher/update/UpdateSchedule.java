package com.firestick.aura.launcher.update;

/** Automatic update check intervals; pure logic so it can be tested on the JVM. */
public final class UpdateSchedule {
    /** Off, daily, every 3 days, weekly. */
    public static final long[] INTERVAL_HOURS = {0L, 24L, 72L, 168L};
    public static final long DEFAULT_HOURS = 24L;
    private static final long HOUR_MS = 3_600_000L;

    private UpdateSchedule() {
    }

    /** A clock that moved backwards counts as due, so a wrong clock cannot block checks forever. */
    public static boolean isDue(long lastCheckMs, long nowMs, long intervalHours) {
        if (intervalHours <= 0) return false;
        if (lastCheckMs <= 0 || nowMs < lastCheckMs) return true;
        return nowMs - lastCheckMs >= intervalHours * HOUR_MS;
    }

    /** Next interval in {@link #INTERVAL_HOURS}, wrapping; unknown values restart at the default. */
    public static long next(long currentHours) {
        for (int i = 0; i < INTERVAL_HOURS.length; i++) {
            if (INTERVAL_HOURS[i] == currentHours) return INTERVAL_HOURS[(i + 1) % INTERVAL_HOURS.length];
        }
        return DEFAULT_HOURS;
    }
}
