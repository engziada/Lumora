package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.firestick.aura.launcher.model.IptvChannel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Age ratings, such as TV-14 or PG-13, of what is on air on Pluto TV and Samsung TV Plus rows.
 * Samsung ratings come from the XMLTV guide that i.mjh.nz publishes for each region, read at most
 * every six hours and kept as a file. Pluto ratings come from Pluto TV's own guide, one channel at
 * a time. Other rows have none. {@link #cached} answers from memory without blocking, and
 * {@link #request} fills the memory in the background.
 */
public final class ProgramRatings {
    private static final String[] SOURCES = {
            "https://raw.githubusercontent.com/matthuisman/i.mjh.nz/master/",
            "https://i.mjh.nz/"};
    static final long SAMSUNG_FRESH_MS = 6L * 60L * 60L * 1000L;
    /** How long a failed or empty lookup stands before the next try. */
    static final long RETRY_MS = 10L * 60L * 1000L;
    /** Pluto guide hours read per channel, and the longest a reading is trusted. */
    static final long PLUTO_SPAN_MS = 4L * 60L * 60L * 1000L;
    private static final long DEBOUNCE_MS = 350L;
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    /** One programme element is a few hundred bytes; anything far larger is not one. */
    private static final int MAX_ELEMENT = 256 * 1024;
    private static final Pattern RATING = Pattern.compile("[A-Za-z0-9][A-Za-z0-9+-]{0,7}");
    private static final Pattern REGION = Pattern.compile("[a-z]{2}");
    private static final Pattern PLUTO_ID = Pattern.compile("[0-9a-f]{24}");
    private static final Pattern CHANNEL = Pattern.compile("\\bchannel=\"([^\"]+)\"");
    private static final Pattern START = Pattern.compile("\\bstart=\"(\\d{14})(?:\\s*([+-]\\d{4}))?\"");
    private static final Pattern STOP = Pattern.compile("\\bstop=\"(\\d{14})(?:\\s*([+-]\\d{4}))?\"");
    private static final Pattern VALUE = Pattern.compile("<rating\\b[^>]*>\\s*<value>\\s*([^<]*?)\\s*</value>");

    /** Rated programmes by row channel ID ("samsung:…", "pluto:…"); shared by the guide and the player. */
    private static final Map<String, Slots> SLOTS = new ConcurrentHashMap<>();
    /** When each Samsung region's guide was read. */
    private static final Map<String, Long> SAMSUNG_READ = new ConcurrentHashMap<>();

    /** One row's rated programmes in start order, and when to read its guide again. */
    static final class Slots {
        final long[] starts, stops;
        final String[] ratings;
        final long until;

        Slots(List<long[]> times, List<String> ratings, long until) {
            int count = times.size();
            Integer[] order = new Integer[count];
            for (int i = 0; i < count; i++) order[i] = i;
            java.util.Arrays.sort(order, (a, b) -> Long.compare(times.get(a)[0], times.get(b)[0]));
            starts = new long[count];
            stops = new long[count];
            this.ratings = new String[count];
            for (int i = 0; i < count; i++) {
                starts[i] = times.get(order[i])[0];
                stops[i] = times.get(order[i])[1];
                this.ratings[i] = ratings.get(order[i]);
            }
            this.until = until;
        }

        /** The rating on air at {@code now}, or "" when that programme has none. */
        String at(long now) {
            for (int i = 0; i < starts.length && starts[i] <= now; i++) {
                if (now < stops[i]) return ratings[i];
            }
            return "";
        }
    }

    /** Collects one row's programmes while a guide is read. */
    private static final class Collector {
        final List<long[]> times = new ArrayList<>();
        final List<String> ratings = new ArrayList<>();

        void add(long start, long stop, String rating) {
            times.add(new long[]{start, stop});
            ratings.add(rating);
        }
    }

    private final File cacheDir;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable queued;
    private volatile boolean closed;

    public ProgramRatings(Context context) {
        cacheDir = context.getApplicationContext().getCacheDir();
    }

    /**
     * The rating of what is on air: a code such as "TV-14", "" when there is none, or null when it
     * is not known yet and {@link #request} would look it up.
     */
    public String cached(IptvChannel channel, long now) {
        String id = channel == null ? "" : channel.channelId;
        if (id.startsWith(FastGuide.SAMSUNG + ":")) {
            String region = region(channel.country);
            if (!REGION.matcher(region).matches()) return "";
            Long read = SAMSUNG_READ.get(region);
            if (read == null || now < read || now - read >= SAMSUNG_FRESH_MS) return null;
            Slots slots = SLOTS.get(id);
            return slots == null ? "" : slots.at(now);
        }
        if (id.startsWith(FastGuide.PLUTO + ":")) {
            Slots slots = SLOTS.get(id);
            return slots == null || now >= slots.until ? null : slots.at(now);
        }
        return "";
    }

    /**
     * Looks up the rating in the background when {@link #cached} does not know it, then runs
     * {@code done} on the main thread. Of several requests in quick succession only the last one
     * is looked up, so moving through the guide does not start a download for every row.
     */
    public void request(IptvChannel channel, Runnable done) {
        if (closed || channel == null || cached(channel, System.currentTimeMillis()) != null) return;
        String id = channel.channelId, country = channel.country;
        if (queued != null) main.removeCallbacks(queued);
        queued = () -> {
            try {
                io.execute(() -> {
                    load(id, country);
                    main.post(() -> {
                        if (!closed) done.run();
                    });
                });
            } catch (RejectedExecutionException shutDown) {
                // The screen closed meanwhile.
            }
        };
        main.postDelayed(queued, DEBOUNCE_MS);
    }

    public void shutdown() {
        closed = true;
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
    }

    /** The XMLTV region of a row; FAST rows spell Great Britain as UK. */
    static String region(String country) {
        return country == null ? "" : "UK".equalsIgnoreCase(country) ? "gb" : country.toLowerCase(Locale.ROOT);
    }

    private void load(String id, String country) {
        long now = System.currentTimeMillis();
        if (id.startsWith(FastGuide.SAMSUNG + ":")) loadSamsung(region(country), now);
        else if (id.startsWith(FastGuide.PLUTO + ":")) loadPluto(id, now);
    }

    private void loadSamsung(String region, long now) {
        if (!REGION.matcher(region).matches()) return;
        Long read = SAMSUNG_READ.get(region);
        if (read != null && now >= read && now - read < SAMSUNG_FRESH_MS) return;
        String prefix = FastGuide.SAMSUNG + ":";
        File file = new File(cacheDir, "ratings/samsung-" + region + ".xml.gz");
        Map<String, Slots> slots = null;
        long readAt = now;
        long modified = file.lastModified();
        if (file.isFile() && now >= modified && now - modified < SAMSUNG_FRESH_MS) {
            slots = parseXmltv(Http.readFile(file), prefix, now);
            if (slots != null) readAt = modified;
        }
        for (int i = 0; slots == null && i < SOURCES.length; i++) {
            byte[] body = Http.getBounded(SOURCES[i] + "SamsungTVPlus/" + region + ".xml.gz", 8000, 20000, MAX_BYTES);
            slots = parseXmltv(body, prefix, now);
            if (slots != null) Http.writeFile(file, body);
        }
        if (slots == null) {
            // Rows of this region show no rating until the next try.
            SAMSUNG_READ.put(region, now - SAMSUNG_FRESH_MS + RETRY_MS);
            return;
        }
        SLOTS.putAll(slots);
        SAMSUNG_READ.put(region, readAt);
    }

    private void loadPluto(String id, long now) {
        Slots known = SLOTS.get(id);
        if (known != null && now < known.until) return;
        String channel = id.substring(FastGuide.PLUTO.length() + 1);
        Slots slots = null;
        if (PLUTO_ID.matcher(channel).matches()) {
            PlutoSession session = PlutoSession.get(cacheDir);
            long from = now - now % (30L * 60L * 1000L);
            byte[] body = session == null ? null : session.fetch(session.channels + "/v2/guide/timelines?start="
                    + PlutoSession.iso(from).replace(":", "%3A") + "&channelIds=" + channel + "&duration="
                    + PLUTO_SPAN_MS / 60_000L, 1024 * 1024);
            slots = body == null ? null : parseTimelines(new String(body, StandardCharsets.UTF_8), channel, now);
        }
        SLOTS.put(id, slots != null ? slots : new Slots(new ArrayList<>(), new ArrayList<>(), now + RETRY_MS));
    }

    /** Reads a Pluto guide answer for one channel; null when it is unusable. */
    static Slots parseTimelines(String json, String channel, long now) {
        try {
            JSONArray data = new JSONObject(json).getJSONArray("data");
            Collector rated = new Collector();
            long last = 0L;
            for (int i = 0; i < data.length(); i++) {
                JSONObject row = data.optJSONObject(i);
                JSONArray timelines = row == null || !channel.equals(row.optString("channelId")) ? null
                        : row.optJSONArray("timelines");
                for (int j = 0; timelines != null && j < timelines.length(); j++) {
                    JSONObject slot = timelines.optJSONObject(j);
                    if (slot == null) continue;
                    long start, stop;
                    try {
                        start = Instant.parse(slot.optString("start")).toEpochMilli();
                        stop = Instant.parse(slot.optString("stop")).toEpochMilli();
                    } catch (RuntimeException badTime) {
                        continue;
                    }
                    if (stop <= start) continue;
                    last = Math.max(last, stop);
                    JSONObject episode = slot.optJSONObject("episode");
                    String rating = clean(episode == null ? "" : episode.optString("rating"));
                    if (!rating.isEmpty() && stop > now) rated.add(start, stop, rating);
                }
            }
            // Read again when the hours read run out, or soon when the answer had none.
            long until = last > now ? Math.max(now + 60_000L, Math.min(last, now + PLUTO_SPAN_MS)) : now + RETRY_MS;
            return new Slots(rated.times, rated.ratings, until);
        } catch (Exception malformed) {
            return null;
        }
    }

    /** Reads an XMLTV guide, gzip or plain; null when it holds no programmes at all. */
    static Map<String, Slots> parseXmltv(byte[] bytes, String prefix, long now) {
        if (bytes == null || bytes.length < 2) return null;
        try {
            InputStream in = new ByteArrayInputStream(bytes);
            if ((bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) in = new GZIPInputStream(in, 16384);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return readXmltv(reader, prefix, now);
            }
        } catch (IOException | RuntimeException malformed) {
            return null;
        }
    }

    /**
     * Keeps the rated programmes that have not ended. Reads one programme element at a time, so
     * a large guide never sits in memory whole.
     */
    static Map<String, Slots> readXmltv(Reader reader, String prefix, long now) throws IOException {
        Map<String, Collector> rows = new HashMap<>();
        StringBuilder buffer = new StringBuilder(32 * 1024);
        char[] chunk = new char[16 * 1024];
        int programmes = 0, pos = 0;
        boolean end = false;
        while (true) {
            int open = buffer.indexOf("<programme", pos);
            int close = open < 0 ? -1 : buffer.indexOf("</programme>", open);
            if (close >= 0) {
                programmes++;
                readProgramme(buffer.substring(open, close), prefix, now, rows);
                pos = close + "</programme>".length();
                continue;
            }
            if (end) break;
            // Keep only an unfinished element, or a tail that may hold the start of the next tag.
            buffer.delete(0, open >= 0 ? open : Math.max(pos, buffer.length() - 16));
            pos = 0;
            if (buffer.length() > MAX_ELEMENT) buffer.setLength(0);
            int count = reader.read(chunk);
            if (count < 0) end = true;
            else buffer.append(chunk, 0, count);
        }
        if (programmes == 0) return null;
        Map<String, Slots> slots = new HashMap<>();
        for (Map.Entry<String, Collector> row : rows.entrySet()) {
            slots.put(row.getKey(), new Slots(row.getValue().times, row.getValue().ratings, Long.MAX_VALUE));
        }
        return slots;
    }

    private static void readProgramme(String element, String prefix, long now, Map<String, Collector> rows) {
        int tagEnd = element.indexOf('>');
        String tag = tagEnd < 0 ? element : element.substring(0, tagEnd);
        Matcher channel = CHANNEL.matcher(tag), start = START.matcher(tag), stop = STOP.matcher(tag);
        Matcher value = VALUE.matcher(element);
        if (!channel.find() || !start.find() || !stop.find()) return;
        String rating = "";
        while (rating.isEmpty() && value.find()) rating = clean(value.group(1));
        if (rating.isEmpty()) return;
        long from, to;
        try {
            from = xmltvTime(start.group(1), start.group(2));
            to = xmltvTime(stop.group(1), stop.group(2));
        } catch (RuntimeException badTime) {
            return;
        }
        if (to <= from || to <= now) return;
        rows.computeIfAbsent(prefix + channel.group(1), key -> new Collector()).add(from, to, rating);
    }

    /** "20261116230000" and "+0100" as epoch milliseconds; no zone means UTC. */
    static long xmltvTime(String digits, String zone) {
        LocalDateTime time = LocalDateTime.of(Integer.parseInt(digits.substring(0, 4)),
                Integer.parseInt(digits.substring(4, 6)), Integer.parseInt(digits.substring(6, 8)),
                Integer.parseInt(digits.substring(8, 10)), Integer.parseInt(digits.substring(10, 12)),
                Integer.parseInt(digits.substring(12, 14)));
        int offset = 0;
        if (zone != null) {
            offset = (zone.charAt(0) == '-' ? -1 : 1) * (Integer.parseInt(zone.substring(1, 3)) * 3600
                    + Integer.parseInt(zone.substring(3, 5)) * 60);
        }
        return time.toEpochSecond(ZoneOffset.ofTotalSeconds(offset)) * 1000L;
    }

    /** A short rating code, "NR" for not rated, or "" for none and anything unexpected. */
    static String clean(String rating) {
        String value = rating == null ? "" : rating.trim();
        if (value.equalsIgnoreCase("Not Rated")) return "NR";
        if (value.equalsIgnoreCase("NA") || !RATING.matcher(value).matches()) return "";
        return value.toUpperCase(Locale.ROOT);
    }
}
