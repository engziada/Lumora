package com.firestick.aura.launcher.ui;

import android.content.Context;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.FastGuide;

import java.text.DateFormat;
import java.util.Date;

/** Wording for FAST schedules: clock times, time ranges, minutes left and the player's guide summary. */
public final class GuideText {
    private GuideText() {
    }

    /** The device's 12- or 24-hour clock. Building one is not free, so callers keep it for a whole pass. */
    public static DateFormat clock(Context context) {
        return android.text.format.DateFormat.getTimeFormat(context);
    }

    public static String time(DateFormat clock, long ms) {
        return clock.format(new Date(ms));
    }

    /** "9:00 – 10:30", or the start alone for the last listed program, whose end is unknown. */
    public static String range(DateFormat clock, FastGuide.Schedule schedule, int index) {
        String start = time(clock, schedule.start(index));
        long end = schedule.end(index);
        return end > 0L ? start + " – " + time(clock, end) : start;
    }

    /** "42 min left", or an empty string when the end is unknown. */
    public static String left(Context context, FastGuide.Schedule schedule, long nowMs) {
        int minutes = schedule.minutesLeft(nowMs);
        return minutes < 0 ? "" : context.getString(R.string.guide_min_left, minutes);
    }

    /**
     * The program on air, the next three and the channel description, or null when the schedule
     * lists nothing from now on.
     */
    public static String summary(Context context, FastGuide.Schedule schedule, long nowMs) {
        DateFormat clock = clock(context);
        StringBuilder text = new StringBuilder();
        int now = schedule.indexAt(nowMs);
        if (now >= 0) text.append(context.getString(R.string.guide_now, range(clock, schedule, now), schedule.title(now)));
        int next = now >= 0 ? now + 1 : schedule.nextIndex(nowMs);
        for (int i = next, shown = 0; i >= 0 && i < schedule.size() && shown < 3; i++, shown++) {
            if (text.length() > 0) text.append('\n');
            text.append(time(clock, schedule.start(i))).append("  ").append(schedule.title(i));
        }
        if (text.length() == 0) return null;
        if (!schedule.description.isEmpty()) text.append("\n\n").append(schedule.description);
        return text.toString();
    }
}
