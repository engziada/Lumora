package com.firestick.aura.launcher.assistant;

/**
 * Detects a held key from its down/up events plus a timer or key repeats. Times are
 * {@code SystemClock.uptimeMillis()} values, the same base as {@code KeyEvent.getEventTime()}.
 */
public final class LongPress {
    /** Hold time that opens the assistant; above Android's 500 ms long-press so taps never trigger it. */
    public static final long HOLD_MS = 600L;

    private final long holdMs;
    private long downAt = -1L;
    private boolean fired;

    public LongPress() {
        this(HOLD_MS);
    }

    public LongPress(long holdMs) {
        this.holdMs = holdMs;
    }

    /** First down event of a press (repeat count 0). Starts a new press. */
    public void down(long eventTime) {
        downAt = eventTime;
        fired = false;
    }

    /** @return {@code true} exactly once, when the current press has been held long enough. */
    public boolean check(long now) {
        if (downAt < 0L || fired || now - downAt < holdMs) return false;
        fired = true;
        return true;
    }

    /** Ends the press. @return {@code true} when it was a long press, so the up event should be consumed. */
    public boolean up() {
        boolean wasLong = fired;
        downAt = -1L;
        fired = false;
        return wasLong;
    }

    public boolean isPressed() {
        return downAt >= 0L;
    }

    public boolean hasFired() {
        return fired;
    }

    public long holdMs() {
        return holdMs;
    }
}
