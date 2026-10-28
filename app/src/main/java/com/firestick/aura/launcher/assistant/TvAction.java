package com.firestick.aura.launcher.assistant;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One whitelisted TV action the assistant may run. Built from a model directive such as
 * {@code [[do:volume_up|3]]} or from a local command; anything outside the whitelist is rejected.
 */
public final class TvAction {
    public static final String PLAY = "play", PAUSE = "pause", PLAY_PAUSE = "play_pause", NEXT = "next",
            PREVIOUS = "previous", STOP = "stop", FORWARD = "forward", REWIND = "rewind";
    public static final String VOLUME_UP = "volume_up", VOLUME_DOWN = "volume_down", MUTE = "mute", UNMUTE = "unmute";
    public static final String BACK = "back", HOME = "home", SCREENSHOT = "screenshot", POWER_MENU = "power_menu";
    public static final String SETTINGS = "settings", SEARCH = "search", THEME = "theme";

    public static final int DEFAULT_STEPS = 2, MAX_STEPS = 10, MAX_QUERY = 200;

    static final Set<String> MEDIA = set(PLAY, PAUSE, PLAY_PAUSE, NEXT, PREVIOUS, STOP, FORWARD, REWIND);
    static final Set<String> SIMPLE = set(PLAY, PAUSE, PLAY_PAUSE, NEXT, PREVIOUS, STOP, FORWARD, REWIND,
            MUTE, UNMUTE, BACK, HOME, SCREENSHOT, POWER_MENU);
    public static final Set<String> SETTINGS_PAGES = set("main", "wifi", "bluetooth", "display", "sound", "apps", "about");
    public static final Set<String> THEMES = set("amber", "copper", "rose", "moonlight");
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+");

    public final String name;
    /** Settings page, theme or package; empty when unused. */
    public final String arg;
    /** Search text; empty when unused. */
    public final String query;
    /** Volume steps; 0 when unused. */
    public final int steps;

    private TvAction(String name, String arg, String query, int steps) {
        this.name = name;
        this.arg = arg;
        this.query = query;
        this.steps = steps;
    }

    private static Set<String> set(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    public static TvAction simple(String name) {
        return of(name, null, null);
    }

    /** @return the action, or {@code null} when the name or its arguments are not allowed. */
    public static TvAction of(String rawName, String rawArg, String rawQuery) {
        if (rawName == null) return null;
        String name = rawName.trim().toLowerCase(Locale.ROOT);
        String arg = rawArg == null ? "" : rawArg.trim();
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (SIMPLE.contains(name)) return new TvAction(name, "", "", 0);
        switch (name) {
            case VOLUME_UP:
            case VOLUME_DOWN:
                return new TvAction(name, "", "", steps(arg));
            case SETTINGS: {
                String page = arg.isEmpty() ? "main" : arg.toLowerCase(Locale.ROOT);
                return SETTINGS_PAGES.contains(page) ? new TvAction(name, page, "", 0) : null;
            }
            case THEME: {
                String theme = arg.toLowerCase(Locale.ROOT);
                return THEMES.contains(theme) ? new TvAction(name, theme, "", 0) : null;
            }
            case SEARCH:
                if (!PACKAGE.matcher(arg).matches() || query.isEmpty()) return null;
                return new TvAction(name, arg, query.length() > MAX_QUERY ? query.substring(0, MAX_QUERY) : query, 0);
            default:
                return null;
        }
    }

    private static int steps(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return Math.max(1, Math.min(MAX_STEPS, parsed));
        } catch (NumberFormatException exception) {
            return DEFAULT_STEPS;
        }
    }

    public boolean isMedia() {
        return MEDIA.contains(name);
    }

    public boolean isVolume() {
        return VOLUME_UP.equals(name) || VOLUME_DOWN.equals(name) || MUTE.equals(name) || UNMUTE.equals(name);
    }

    /** Actions that need the assistant panel out of the way: they start or reveal something else. */
    public boolean closesPanel() {
        switch (name) {
            case PLAY:
            case PLAY_PAUSE:
            case BACK:
            case HOME:
            case SCREENSHOT:
            case POWER_MENU:
            case SETTINGS:
            case SEARCH:
                return true;
            default:
                return false;
        }
    }

    /** Global actions performed through the accessibility service. */
    public boolean needsQuickActions() {
        return BACK.equals(name) || HOME.equals(name) || SCREENSHOT.equals(name) || POWER_MENU.equals(name);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TvAction)) return false;
        TvAction action = (TvAction) other;
        return steps == action.steps && name.equals(action.name) && arg.equals(action.arg) && query.equals(action.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, arg, query, steps);
    }

    @Override
    public String toString() {
        return name + (arg.isEmpty() ? "" : "|" + arg) + (query.isEmpty() ? "" : "|" + query)
                + (steps > 0 ? "|" + steps : "");
    }
}
