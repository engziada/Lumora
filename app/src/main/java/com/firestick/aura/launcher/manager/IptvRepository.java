package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.firestick.aura.launcher.model.IptvChannel;
import com.firestick.aura.launcher.model.IptvCountry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads country metadata and on-demand country playlists from iptv-org. */
public final class IptvRepository {
    public interface CountriesCallback {
        void onLoaded(List<IptvCountry> countries, boolean failed);
    }

    public interface ChannelsCallback {
        void onLoaded(List<IptvChannel> channels, boolean failed);
    }

    private static final String API = "https://iptv-org.github.io/api";
    private static final String PLAYLISTS = "https://iptv-org.github.io/iptv/countries";
    private static final long CACHE_MAX_AGE_MS = 2L * 60L * 60L * 1000L;
    private static final Pattern ATTRIBUTE = Pattern.compile("(?:^|\\s)%s=\"([^\"]*)\"");

    private final File cacheDir;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public IptvRepository(Context context) {
        cacheDir = new File(context.getApplicationContext().getCacheDir(), "iptv");
        //noinspection ResultOfMethodCallIgnored
        cacheDir.mkdirs();
    }

    public void loadCountries(CountriesCallback callback) {
        io.execute(() -> {
            String json = fetch(API + "/countries.json", "countries.json");
            List<IptvCountry> countries = parseCountries(json);
            boolean failed = countries == null;
            main.post(() -> callback.onLoaded(failed ? Collections.emptyList() : countries, failed));
        });
    }

    public void loadChannels(String countryCode, ChannelsCallback callback) {
        String code = countryCode == null ? "" : countryCode.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        boolean worldwide = "ALL".equals(countryCode);
        if (!worldwide && code.length() != 2) {
            main.post(() -> callback.onLoaded(Collections.emptyList(), true));
            return;
        }
        io.execute(() -> {
            String playlist = fetch(worldwide ? "https://iptv-org.github.io/iptv/index.m3u" : PLAYLISTS + "/" + code + ".m3u", "country-" + code + ".m3u");
            List<IptvChannel> channels = parsePlaylist(playlist, code.toUpperCase(Locale.ROOT));
            boolean failed = playlist == null;
            main.post(() -> callback.onLoaded(failed ? Collections.emptyList() : channels, failed));
        });
    }

    private String fetch(String url, String cacheName) {
        File file = new File(cacheDir, cacheName);
        if (file.isFile() && System.currentTimeMillis() - file.lastModified() < CACHE_MAX_AGE_MS) {
            byte[] fresh = Http.readFile(file);
            if (fresh != null) return new String(fresh, StandardCharsets.UTF_8);
        }
        byte[] response = Http.get(url, 8000, 20000);
        if (response != null && response.length > 0) {
            Http.writeFile(file, response);
            return new String(response, StandardCharsets.UTF_8);
        }
        byte[] stale = file.isFile() ? Http.readFile(file) : null;
        return stale == null ? null : new String(stale, StandardCharsets.UTF_8);
    }

    private static List<IptvCountry> parseCountries(String json) {
        if (json == null) return null;
        try {
            JSONArray rows = new JSONArray(json);
            List<IptvCountry> countries = new ArrayList<>(rows.length());
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                String code = row.optString("code", "").trim().toUpperCase(Locale.ROOT);
                String name = row.optString("name", "").trim();
                String flag = row.optString("flag", "").trim();
                if (code.length() == 2 && !name.isEmpty()) {
                    countries.add(new IptvCountry(code, name, flag, Collections.emptyList()));
                }
            }
            countries.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            return countries;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static List<IptvChannel> parsePlaylist(String playlist, String countryCode) {
        if (playlist == null) return null;
        List<IptvChannel> channels = new ArrayList<>();
        java.util.Map<String, Integer> positions = new java.util.LinkedHashMap<>();
        Set<String> urls = new HashSet<>();
        String[] lines = playlist.split("\\r?\\n");
        String metadata = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF:")) {
                metadata = line;
                continue;
            }
            if (line.startsWith("#") || metadata == null) continue;

            int comma = metadata.lastIndexOf(',');
            String playlistName = comma >= 0 && comma + 1 < metadata.length()
                    ? metadata.substring(comma + 1).trim() : "";
            String name = attribute(metadata, "tvg-name");
            if (name.isEmpty()) name = playlistName;
            String originalId = attribute(metadata, "tvg-id");
            String id = originalId.split("@", 2)[0];
            String logo = attribute(metadata, "tvg-logo");
            String group = attribute(metadata, "group-title");
            List<String> categories = new ArrayList<>();
            for (String category : group.split(";")) {
                String normalized = category.trim();
                switch (normalized.toLowerCase(Locale.ROOT)) {
                    case "movie": case "movies": case "cinema": normalized = "Movies"; break;
                    case "music": normalized = "Music"; break;
                    case "adult": case "adults": case "xxx": case "nsfw": normalized = "18+"; break;
                    default: break;
                }
                if (!normalized.isEmpty() && !categories.contains(normalized)) categories.add(normalized);
            }
            if (!name.isEmpty() && (line.startsWith("http://") || line.startsWith("https://"))) {
                String identity = id.isEmpty()
                        ? countryCode + "|" + name.toLowerCase(Locale.ROOT) : id;
                if (urls.add(line)) {
                    Integer position = positions.get(identity);
                    if (position == null) {
                        long stableId = Long.parseLong(Http.sha1(identity).substring(0, 15), 16);
                        positions.put(identity, channels.size());
                        channels.add(new IptvChannel(stableId, name, logo,
                                categories.isEmpty() ? "" : categories.get(0), categories,
                                ("ALL".equals(countryCode) && id.matches(".*\\.[A-Za-z]{2}")
                                        ? id.substring(id.length() - 2).toUpperCase(Locale.ROOT) : countryCode),
                                "", line, false, "", "", "", "", id,
                                Collections.singletonList(line), originalId.equals(id) ? Collections.emptyList()
                                        : Collections.singletonList(Long.parseLong(Http.sha1(originalId).substring(0, 15), 16))));
                    } else {
                        IptvChannel first = channels.get(position);
                        List<String> feeds = new ArrayList<>(first.streamUrls);
                        feeds.add(line);
                        List<Long> legacy = new ArrayList<>(first.legacyIds);
                        legacy.add(Long.parseLong(Http.sha1(identity + "|" + line).substring(0, 15), 16));
                        if (!originalId.equals(id)) {
                            legacy.add(Long.parseLong(Http.sha1(originalId).substring(0, 15), 16));
                            legacy.add(Long.parseLong(Http.sha1(originalId + "|" + line).substring(0, 15), 16));
                        }
                        List<String> genres = new ArrayList<>(first.genres);
                        for (String genre : categories) if (!genres.contains(genre)) genres.add(genre);
                        channels.set(position, new IptvChannel(first.id, first.name, first.logo,
                                first.category, genres, first.country, first.language, first.url, false,
                                "", "", "", "", first.channelId, feeds, legacy));
                    }
                }
            }
            metadata = null;
        }
        channels.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return channels;
    }

    private static String attribute(String line, String name) {
        Matcher matcher = Pattern.compile(String.format(Locale.ROOT, ATTRIBUTE.pattern(), Pattern.quote(name)))
                .matcher(line);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    public void refreshChannels(String countryCode, ChannelsCallback callback) {
        String code = countryCode.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        io.execute(() -> {
            File cache = new File(cacheDir, "country-" + code + ".m3u");
            if (cache.exists()) cache.setLastModified(0);
        });
        loadChannels(countryCode, callback);
    }

    public void shutdown() {
        io.shutdownNow();
    }
}
