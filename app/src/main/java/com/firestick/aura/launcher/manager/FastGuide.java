package com.firestick.aura.launcher.manager;

import com.firestick.aura.launcher.model.IptvChannel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rules for the free ad-supported TV (FAST) lineups that i.mjh.nz republishes for Pluto TV and
 * Samsung TV Plus: region choice, stream addresses, channel rows, schedules, and the merge with
 * iptv-org rows. Pure Java, so unit tests cover it without a device.
 */
public final class FastGuide {
    public static final String PLUTO = "pluto";
    public static final String SAMSUNG = "samsung";
    /** Region setting that follows the browsed IPTV country. */
    public static final String AUTO = "auto";
    /** The lineup used when a service has none for the wanted country. */
    public static final String FALLBACK = "us";
    /** Every region that either service publishes, for the region picker. */
    public static final String[] REGIONS = {"ar", "at", "br", "ca", "ch", "cl", "de", "dk", "es", "fr", "gb",
            "in", "it", "kr", "mx", "no", "se", "us"};
    private static final String[] PLUTO_REGIONS = {"ar", "br", "ca", "cl", "de", "dk", "es", "fr", "gb", "it",
            "mx", "no", "se", "us"};
    private static final String[] SAMSUNG_REGIONS = {"at", "ca", "ch", "de", "es", "fr", "gb", "in", "it",
            "kr", "us"};
    /** The last program of a schedule has no end time; it counts as on air this long after its start. */
    static final long LAST_PROGRAM_MS = 3L * 60L * 60L * 1000L;
    private static final Pattern FEED = Pattern.compile(
            "^https?://(?:www\\.)?jmp2\\.uk/(plu|stvp)-([A-Za-z0-9_-]+?)(?:\\.m3u8)?(?:[?#].*)?$");
    private static final Map<String, Schedule> SCHEDULES = new ConcurrentHashMap<>();

    private FastGuide() {
    }

    /**
     * Picks the lineup region for a service. An explicit setting wins; {@link #AUTO} follows the
     * browsed country. Both fall back to {@link #FALLBACK} when the service has no such lineup.
     */
    public static String region(String service, String setting, String browsedCountry) {
        String wanted = setting == null || setting.isEmpty() || AUTO.equals(setting) ? browsedCountry : setting;
        wanted = wanted == null ? "" : wanted.trim().toLowerCase(Locale.ROOT);
        if ("uk".equals(wanted)) wanted = "gb";
        for (String code : SAMSUNG.equals(service) ? SAMSUNG_REGIONS : PLUTO_REGIONS) {
            if (code.equals(wanted)) return code;
        }
        return FALLBACK;
    }

    /** The jmp2.uk address that redirects to the service's current stream for a channel. */
    public static String streamUrl(String service, String id) {
        return SAMSUNG.equals(service) ? "https://jmp2.uk/stvp-" + id : "https://jmp2.uk/plu-" + id + ".m3u8";
    }

    /** True for rows built by {@link #row}. */
    public static boolean isFast(IptvChannel channel) {
        return channel != null && (channel.channelId.startsWith(PLUTO + ":") || channel.channelId.startsWith(SAMSUNG + ":"));
    }

    /** Country code of a region as the IPTV country picker spells it (iptv-org uses UK for gb). */
    public static String country(String region) {
        return "gb".equalsIgnoreCase(region) ? "UK" : region.toUpperCase(Locale.ROOT);
    }

    /** Builds the channel row for one lineup entry. The ID stays stable across downloads. */
    public static IptvChannel row(String service, String region, String id, String name, String logo,
                                  String group, String userAgent) {
        String channelId = service + ":" + id;
        long stableId = Long.parseLong(Http.sha1(channelId).substring(0, 15), 16);
        String genre = group == null || group.trim().isEmpty() ? "General" : group.trim();
        if (IptvChannel.isAdultGroup(genre) || IptvChannel.isAdultName(name)) genre = "18+";
        String url = streamUrl(service, id);
        Map<String, Map<String, String>> headers = new HashMap<>();
        if (userAgent != null && !userAgent.isEmpty() && userAgent.indexOf('\r') < 0 && userAgent.indexOf('\n') < 0) {
            headers.put(url, Collections.singletonMap("User-Agent", userAgent));
        }
        return new IptvChannel(stableId, name, logo == null ? "" : logo, genre, Collections.singletonList(genre),
                country(region), "", url, false, "", "", "", "", channelId, Collections.singletonList(url),
                Collections.emptyList(), headers);
    }

    public static void putSchedule(String channelId, Schedule schedule) {
        if (channelId != null && schedule != null) SCHEDULES.put(channelId, schedule);
    }

    /** The schedule recorded for a FAST row, or null for other rows and lineups not loaded yet. */
    public static Schedule schedule(String channelId) {
        return channelId == null ? null : SCHEDULES.get(channelId);
    }

    public static void clearSchedules() {
        SCHEDULES.clear();
    }

    /**
     * Adds FAST rows to a country list. An iptv-org row that plays the same jmp2.uk stream folds
     * into the FAST row: its other feeds and genres stay, and its IDs become legacy IDs so saved
     * favorites move across. Order: lineups of the browsed country in channel-number order, then
     * the other rows as given, then lineups of other regions (the fallback for countries without one).
     */
    public static List<IptvChannel> attach(List<IptvChannel> channels, List<IptvChannel> fast, String browsedCountry) {
        if (fast == null || fast.isEmpty()) return channels;
        List<IptvChannel> merged = new ArrayList<>(fast);
        Map<String, Integer> byFeed = new HashMap<>();
        for (int i = 0; i < merged.size(); i++) {
            String key = feedKey(merged.get(i).url);
            if (key != null && !byFeed.containsKey(key)) byFeed.put(key, i);
        }
        List<IptvChannel> others = new ArrayList<>(channels.size());
        for (IptvChannel row : channels) {
            Integer at = null;
            for (String url : row.streamUrls) {
                String key = feedKey(url);
                at = key == null ? null : byFeed.get(key);
                if (at != null) break;
            }
            if (at == null) others.add(row);
            else merged.set(at, fold(merged.get(at), row));
        }
        String browsed = browsedCountry == null ? "" : browsedCountry.toUpperCase(Locale.ROOT);
        if ("GB".equals(browsed)) browsed = "UK";
        List<IptvChannel> result = new ArrayList<>(others.size() + merged.size());
        List<IptvChannel> foreign = new ArrayList<>();
        for (IptvChannel row : merged) {
            if ("ALL".equals(browsed) || row.country.equalsIgnoreCase(browsed)) result.add(row);
            else foreign.add(row);
        }
        result.addAll(others);
        result.addAll(foreign);
        return result;
    }

    /** The service prefix and channel ID of a jmp2.uk stream address, such as "plu:5f12", otherwise null. */
    static String feedKey(String url) {
        if (url == null) return null;
        Matcher matcher = FEED.matcher(url.trim());
        return matcher.matches() ? matcher.group(1) + ":" + matcher.group(2) : null;
    }

    /**
     * True when the player must treat an address as HLS: an .m3u8 path, or a jmp2.uk FAST
     * redirect, whose Samsung form has no extension for the player to detect.
     */
    public static boolean isHls(String url) {
        return url != null && (url.toLowerCase(Locale.ROOT).contains(".m3u8") || feedKey(url) != null);
    }

    private static IptvChannel fold(IptvChannel fast, IptvChannel old) {
        List<String> feeds = new ArrayList<>(fast.streamUrls);
        for (String feed : old.streamUrls) if (!feeds.contains(feed) && feeds.size() < 30) feeds.add(feed);
        List<String> genres = new ArrayList<>(fast.genres);
        for (String genre : old.genres) if (!genres.contains(genre)) genres.add(genre);
        List<Long> aliases = new ArrayList<>(fast.legacyIds);
        if (old.id != fast.id && !aliases.contains(old.id)) aliases.add(old.id);
        for (Long alias : old.legacyIds) if (!aliases.contains(alias)) aliases.add(alias);
        Map<String, Map<String, String>> headers = new HashMap<>(old.streamHeaders);
        for (Map.Entry<String, Map<String, String>> entry : fast.streamHeaders.entrySet()) {
            Map<String, String> combined = new HashMap<>();
            Map<String, String> previous = headers.get(entry.getKey());
            if (previous != null) combined.putAll(previous);
            combined.putAll(entry.getValue());
            headers.put(entry.getKey(), combined);
        }
        return new IptvChannel(fast.id, fast.name, fast.logo.isEmpty() ? old.logo : fast.logo, fast.category,
                genres, fast.country, old.language, fast.url, fast.isFavorite || old.isFavorite, "", "", "", "",
                fast.channelId, feeds, aliases, headers);
    }

    /** One channel's program list. Each program ends when the next one starts. */
    public static final class Schedule {
        public final int number;
        public final String description;
        public final String art;
        private final long[] starts;
        private final String[] titles;

        /** Entries must ascend by start time; out-of-order and non-positive starts are dropped. */
        public Schedule(int number, String description, String art, long[] startsMs, String[] titles) {
            this.number = number;
            this.description = description == null ? "" : description.trim();
            this.art = art == null ? "" : art;
            int count = Math.min(startsMs.length, titles.length);
            long[] keptStarts = new long[count];
            String[] keptTitles = new String[count];
            int kept = 0;
            for (int i = 0; i < count; i++) {
                if (startsMs[i] <= 0 || (kept > 0 && startsMs[i] <= keptStarts[kept - 1])) continue;
                keptStarts[kept] = startsMs[i];
                keptTitles[kept++] = titles[i] == null ? "" : titles[i].trim();
            }
            starts = Arrays.copyOf(keptStarts, kept);
            this.titles = Arrays.copyOf(keptTitles, kept);
        }

        public int size() {
            return starts.length;
        }

        public long start(int index) {
            return starts[index];
        }

        public String title(int index) {
            return titles[index];
        }

        /** End of a program, or 0 for the last one, whose end is unknown. */
        public long end(int index) {
            return index + 1 < starts.length ? starts[index + 1] : 0L;
        }

        /** Index of the program on air, or -1 when the schedule does not cover {@code nowMs}. */
        public int indexAt(long nowMs) {
            int found = -1;
            for (int i = 0; i < starts.length && starts[i] <= nowMs; i++) found = i;
            if (found >= 0 && found == starts.length - 1 && nowMs - starts[found] >= LAST_PROGRAM_MS) return -1;
            return found;
        }

        /** Index of the first program that starts after {@code nowMs}, or -1. */
        public int nextIndex(long nowMs) {
            for (int i = 0; i < starts.length; i++) if (starts[i] > nowMs) return i;
            return -1;
        }

        /** Percent of the current program already aired, or -1 when unknown. */
        public int progressPercent(long nowMs) {
            int index = indexAt(nowMs);
            long end = index < 0 ? 0L : end(index);
            if (end <= 0L) return -1;
            long elapsed = nowMs - starts[index];
            return (int) Math.max(0L, Math.min(100L, elapsed * 100L / Math.max(1L, end - starts[index])));
        }

        /** Whole minutes left in the current program, rounded up, or -1 when unknown. */
        public int minutesLeft(long nowMs) {
            int index = indexAt(nowMs);
            long end = index < 0 ? 0L : end(index);
            if (end <= 0L) return -1;
            return (int) Math.max(0L, (end - nowMs + 59_999L) / 60_000L);
        }
    }
}
