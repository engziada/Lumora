package com.firestick.aura.launcher.manager;

import android.util.JsonReader;
import android.util.JsonToken;

import com.firestick.aura.launcher.model.IptvChannel;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Downloads the Pluto TV and Samsung TV Plus lineups that i.mjh.nz republishes, keeps one
 * region of each in memory plus the adult channels of every other region, and records each
 * channel's schedule in {@link FastGuide}.
 */
public final class FastChannels {
    private static final String[] SOURCES = {
            "https://raw.githubusercontent.com/matthuisman/i.mjh.nz/master/",
            "https://i.mjh.nz/"};
    private static final long MAX_AGE_MS = 60L * 60L * 1000L;
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PROGRAMS = 48;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Map<String, Lineup> MEMORY = new ConcurrentHashMap<>();
    private static final Map<String, Object> LOCKS = new ConcurrentHashMap<>();

    private FastChannels() {
    }

    private static final class Lineup {
        final long loadedAt;
        final List<IptvChannel> rows;
        final Map<String, FastGuide.Schedule> schedules;

        Lineup(long loadedAt, List<IptvChannel> rows, Map<String, FastGuide.Schedule> schedules) {
            this.loadedAt = loadedAt;
            this.rows = rows;
            this.schedules = schedules;
        }
    }

    private static final class Entry {
        final String id;
        String name = "";
        String logo = "";
        String art = "";
        String group = "";
        String description = "";
        int number;
        boolean protectedStream;
        final List<Long> starts = new ArrayList<>();
        final List<String> titles = new ArrayList<>();

        Entry(String id) {
            this.id = id;
        }
    }

    /**
     * Returns the rows of one service's lineup for a region, or of the US lineup when the region
     * is missing, followed by the adult channels that only other regions carry. Blocks on the
     * network, so call it off the main thread. Uses a cached download for up to an hour, and an
     * older one when the service cannot be reached.
     */
    public static List<IptvChannel> load(File cacheDir, String service, String region) {
        String key = service + "|" + region;
        synchronized (LOCKS.computeIfAbsent(service, name -> new Object())) {
            long now = System.currentTimeMillis();
            Lineup memory = MEMORY.get(key);
            if (memory != null && now - memory.loadedAt < MAX_AGE_MS) return memory.rows;
            File file = new File(cacheDir, "fast-" + service + ".json.gz");
            long loadedAt = now;
            Lineup lineup = null;
            if (file.isFile() && now - file.lastModified() < MAX_AGE_MS) {
                lineup = parse(Http.readFile(file), service, region);
                if (lineup != null) loadedAt = file.lastModified();
            }
            if (lineup == null) {
                String path = (FastGuide.SAMSUNG.equals(service) ? "SamsungTVPlus" : "PlutoTV") + "/.channels.json.gz";
                for (String source : SOURCES) {
                    byte[] bytes = Http.getBounded(source + path, 8000, 20000, MAX_BYTES);
                    lineup = parse(bytes, service, region);
                    if (lineup != null) {
                        Http.writeFile(file, bytes);
                        break;
                    }
                }
            }
            if (lineup == null && file.isFile()) {
                // A stale copy keeps the guide usable offline; the next load tries the network again.
                lineup = parse(Http.readFile(file), service, region);
                if (lineup != null) loadedAt = 0L;
            }
            if (lineup == null) return memory == null ? Collections.emptyList() : memory.rows;
            for (Map.Entry<String, FastGuide.Schedule> schedule : lineup.schedules.entrySet())
                FastGuide.putSchedule(schedule.getKey(), schedule.getValue());
            MEMORY.put(key, new Lineup(loadedAt, lineup.rows, lineup.schedules));
            return lineup.rows;
        }
    }

    /** Forgets lineups and schedules so the next load reads the cache file or the network again. */
    public static void clear() {
        MEMORY.clear();
        FastGuide.clearSchedules();
    }

    /** Parses a whole document, gzip or plain JSON. Returns null for anything unusable. */
    private static Lineup parse(byte[] bytes, String service, String region) {
        if (bytes == null || bytes.length < 2) return null;
        try {
            InputStream in = new ByteArrayInputStream(bytes);
            if ((bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) in = new GZIPInputStream(in, 16384);
            // Sorted, so rows added from other regions keep a stable order across downloads.
            Map<String, List<Entry>> regions = new TreeMap<>();
            String userAgent = "";
            try (JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if ("regions".equals(name) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String code = reader.nextName().toLowerCase(Locale.ROOT);
                            boolean whole = code.equals(region) || code.equals(FastGuide.FALLBACK);
                            // Other regions only lend their adult channels, so the Adults list is not limited to one country.
                            regions.put(code, readRegion(reader, !whole));
                        }
                        reader.endObject();
                    } else if ("headers".equals(name) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                        reader.beginObject();
                        while (reader.hasNext()) {
                            if ("user-agent".equalsIgnoreCase(reader.nextName())) userAgent = text(reader);
                            else reader.skipValue();
                        }
                        reader.endObject();
                    } else reader.skipValue();
                }
                reader.endObject();
            }
            String used = regions.containsKey(region) ? region : FastGuide.FALLBACK;
            List<Entry> entries = regions.get(used);
            if (entries == null) return null;
            entries.sort((a, b) -> a.number != b.number ? Integer.compare(a.number, b.number)
                    : a.name.compareToIgnoreCase(b.name));
            List<IptvChannel> rows = new ArrayList<>(entries.size());
            Map<String, FastGuide.Schedule> schedules = new HashMap<>();
            Set<String> seen = new HashSet<>();
            for (Entry entry : entries) add(service, used, entry, userAgent, rows, schedules, seen);
            for (Map.Entry<String, List<Entry>> other : regions.entrySet()) {
                if (other.getKey().equals(used)) continue;
                List<Entry> adults = new ArrayList<>();
                for (Entry entry : other.getValue()) if (isAdult(entry)) adults.add(entry);
                adults.sort((a, b) -> Integer.compare(a.number, b.number));
                for (Entry entry : adults) add(service, other.getKey(), entry, userAgent, rows, schedules, seen);
            }
            return new Lineup(0L, Collections.unmodifiableList(rows), schedules);
        } catch (Exception | OutOfMemoryError unusable) {
            return null;
        }
    }

    private static void add(String service, String region, Entry entry, String userAgent, List<IptvChannel> rows,
                            Map<String, FastGuide.Schedule> schedules, Set<String> seen) {
        if (!seen.add(entry.id)) return;
        IptvChannel row = FastGuide.row(service, region, entry.id, entry.name, entry.logo, entry.group, userAgent);
        rows.add(row);
        long[] starts = new long[entry.starts.size()];
        for (int i = 0; i < starts.length; i++) starts[i] = entry.starts.get(i);
        schedules.put(row.channelId, new FastGuide.Schedule(entry.number, entry.description, entry.art,
                starts, entry.titles.toArray(new String[0])));
    }

    private static boolean isAdult(Entry entry) {
        return IptvChannel.isAdultGroup(entry.group) || IptvChannel.isAdultName(entry.name);
    }

    private static List<Entry> readRegion(JsonReader reader, boolean adultsOnly) throws IOException {
        List<Entry> entries = new ArrayList<>();
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue();
            return entries;
        }
        reader.beginObject();
        while (reader.hasNext()) {
            if ("channels".equals(reader.nextName()) && reader.peek() == JsonToken.BEGIN_OBJECT) {
                reader.beginObject();
                while (reader.hasNext()) {
                    Entry entry = readChannel(reader.nextName(), reader, adultsOnly);
                    if (entry != null) entries.add(entry);
                }
                reader.endObject();
            } else reader.skipValue();
        }
        reader.endObject();
        return entries;
    }

    private static Entry readChannel(String id, JsonReader reader, boolean adultsOnly) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue();
            return null;
        }
        Entry entry = new Entry(id);
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "name": entry.name = text(reader).trim(); break;
                case "chno": entry.number = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, number(reader))); break;
                case "logo": entry.logo = web(text(reader)); break;
                case "art": entry.art = web(text(reader)); break;
                case "group": entry.group = text(reader); break;
                case "description": entry.description = text(reader); break;
                // Streams that need a DRM license do not play in the launcher's player.
                case "license_url": entry.protectedStream = !text(reader).isEmpty(); break;
                case "programs":
                    // Both lineups list programs after the name and group, so skipped channels cost no schedule.
                    if (adultsOnly && !isAdult(entry)) reader.skipValue();
                    else readPrograms(reader, entry);
                    break;
                default: reader.skipValue(); break;
            }
        }
        reader.endObject();
        if (adultsOnly && !isAdult(entry)) return null;
        return entry.protectedStream || entry.name.isEmpty() || !ID.matcher(id).matches() ? null : entry;
    }

    private static void readPrograms(JsonReader reader, Entry entry) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue();
            return;
        }
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_ARRAY || entry.starts.size() >= MAX_PROGRAMS) {
                reader.skipValue();
                continue;
            }
            reader.beginArray();
            long start = reader.hasNext() ? number(reader) : 0L;
            String title = reader.hasNext() ? text(reader).trim() : "";
            while (reader.hasNext()) reader.skipValue();
            reader.endArray();
            if (start > 0L && !title.isEmpty()) {
                entry.starts.add(start * 1000L);
                entry.titles.add(title);
            }
        }
        reader.endArray();
    }

    private static String text(JsonReader reader) throws IOException {
        JsonToken token = reader.peek();
        if (token == JsonToken.STRING || token == JsonToken.NUMBER) return reader.nextString();
        reader.skipValue();
        return "";
    }

    private static long number(JsonReader reader) throws IOException {
        try {
            return (long) Double.parseDouble(text(reader));
        } catch (NumberFormatException invalid) {
            return 0L;
        }
    }

    private static String web(String url) {
        String trimmed = url.trim();
        return trimmed.startsWith("https://") || trimmed.startsWith("http://") ? trimmed : "";
    }
}
