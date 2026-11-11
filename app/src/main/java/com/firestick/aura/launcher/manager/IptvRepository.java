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

    private final android.content.SharedPreferences sources;
    private final File cacheDir;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService downloads = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    // These catalogs are shared by every country selection. Keep their parsed forms for the
    // lifetime of the repository so RC navigation does not repeatedly parse tens of thousands
    // of JSON rows and the worldwide curated playlist.
    private List<IptvChannel> curatedChannels;
    private java.util.Map<String, List<ApiFeed>> apiFeeds;
    private final java.util.Map<String, List<IptvChannel>> customChannels = new java.util.HashMap<>();

    private static final class ApiFeed {
        final String url;
        final String referrer;
        final String userAgent;
        ApiFeed(JSONObject row) {
            url = row.optString("url", "");
            referrer = row.optString("referrer", "");
            userAgent = row.optString("user_agent", "");
        }
    }

    public IptvRepository(Context context) {
        sources = context.getApplicationContext().getSharedPreferences("iptv_sources", Context.MODE_PRIVATE);
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
            java.util.concurrent.Future<String> primary = download(worldwide ? "https://iptv-org.github.io/iptv/index.m3u"
                    : PLAYLISTS + "/" + code + ".m3u", "country-" + code + ".m3u");
            boolean useCurated = sources.getBoolean("free_tv", true);
            java.util.concurrent.Future<String> curated = useCurated && curatedChannels == null ? download(
                    "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8", "free-tv.m3u") : null;
            java.util.concurrent.Future<String> api = apiFeeds == null ? download(API + "/streams.json", "streams.json") : null;
            List<IptvSources.Source> configuredSources = IptvSources.read(sources);
            List<java.util.concurrent.Future<String>> custom = new ArrayList<>();
            for (IptvSources.Source source : configuredSources)
                custom.add(download(source.url, "custom-" + Http.sha1(source.url) + ".m3u"));
            String playlist = downloaded(primary);
            List<IptvChannel> channels = parsePlaylist(playlist, code.toUpperCase(Locale.ROOT));
            if (channels == null) channels = new ArrayList<>();
            if (useCurated && curatedChannels == null && curated != null) {
                curatedChannels = parseCurated(downloaded(curated));
            }
            if (useCurated && curatedChannels != null)
                channels = merge(channels, filterCountry(curatedChannels, countryCode));
            for (int i = 0; i < custom.size(); i++) {
                IptvSources.Source source = configuredSources.get(i);
                List<IptvChannel> additions = customChannels.get(source.url);
                if (additions == null) {
                    additions = parsePlaylist(downloaded(custom.get(i)), "ALL");
                    if (additions != null) customChannels.put(source.url, additions);
                }
                if (additions != null) channels = merge(channels, filterCountry(additions, countryCode));
            }
            if (apiFeeds == null) {
                String json = downloaded(api);
                if (json != null) apiFeeds = parseApiFeeds(json);
            }
            channels = addApiFeeds(channels, apiFeeds);
            boolean failed = channels.isEmpty() && playlist == null;
            final List<IptvChannel> result = channels;
            main.post(() -> callback.onLoaded(failed ? Collections.emptyList() : result, failed));
        });
    }

    private java.util.concurrent.Future<String> download(String url, String cacheName) {
        return downloads.submit(() -> fetch(url, cacheName));
    }

    private static String downloaded(java.util.concurrent.Future<String> future) {
        try { return future.get(); }
        catch (InterruptedException stopped) { Thread.currentThread().interrupt(); return null; }
        catch (java.util.concurrent.ExecutionException unavailable) { return null; }
    }

    private String fetch(String url, String cacheName) {
        File file = new File(cacheDir, cacheName);
        if (file.isFile() && System.currentTimeMillis() - file.lastModified() < CACHE_MAX_AGE_MS) {
            byte[] fresh = Http.readFile(file);
            if (fresh != null) return new String(fresh, StandardCharsets.UTF_8);
        }
        byte[] response = Http.getBounded(url, 8000, 15000, 12 * 1024 * 1024);
        if (response != null && response.length > 0 && validDocument(response, cacheName)) {
            Http.writeFile(file, response);
            return new String(response, StandardCharsets.UTF_8);
        }
        byte[] stale = file.isFile() ? Http.readFile(file) : null;
        return stale == null ? null : new String(stale, StandardCharsets.UTF_8);
    }

    private static boolean validDocument(byte[] bytes, String name) {
        String text = new String(bytes, StandardCharsets.UTF_8).trim().replace("\uFEFF", "");
        if (!name.endsWith(".json")) return text.startsWith("#EXTM3U") || text.startsWith("#EXTINF:");
        try { new JSONArray(text); return true; }
        catch (Exception invalid) { return false; }
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
        playlist = playlist.replace("\uFEFF", "");
        List<IptvChannel> channels = new ArrayList<>();
        java.util.Map<String, Integer> positions = new java.util.LinkedHashMap<>();
        Set<String> urls = new HashSet<>();
        String[] lines = playlist.split("\\r?\\n");
        String metadata = null;
        java.util.Map<String, String> requestHeaders = new java.util.HashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#EXTINF:")) {
                metadata = line;
                requestHeaders.clear();
                continue;
            }
            if (metadata != null && line.startsWith("#EXTVLCOPT:http-")) {
                int equals = line.indexOf('=');
                if (equals > 0) {
                    String key = line.substring(0, equals);
                    String value = line.substring(equals + 1).trim();
                    if ("#EXTVLCOPT:http-referrer".equals(key)) requestHeaders.put("Referer", value);
                    if ("#EXTVLCOPT:http-user-agent".equals(key)) requestHeaders.put("User-Agent", value);
                }
            }
            if (line.startsWith("#") || metadata == null) continue;

            int comma = metadataComma(metadata);
            String playlistName = comma >= 0 && comma + 1 < metadata.length()
                    ? metadata.substring(comma + 1).trim() : "";
            String name = attribute(metadata, "tvg-name");
            if (name.isEmpty()) name = playlistName;
            String originalId = attribute(metadata, "tvg-id");
            String id = originalId.split("@", 2)[0];
            String country = attribute(metadata, "tvg-country").toUpperCase(Locale.ROOT);
            if (!country.matches("[A-Z]{2}")) country = id.matches(".*\\.[A-Za-z]{2}")
                    ? id.substring(id.length() - 2).toUpperCase(Locale.ROOT) : countryCode;
            if ("GB".equals(country)) country = "UK"; // iptv-org country selector uses UK.
            String logo = attribute(metadata, "tvg-logo");
            String group = attribute(metadata, "group-title");
            List<String> categories = new ArrayList<>();
            for (String category : group.split("[;|]")) {
                String normalized = category.trim();
                switch (normalized.toLowerCase(Locale.ROOT)) {
                    case "movie": case "movies": case "cinema": normalized = "Movies"; break;
                    case "music": normalized = "Music"; break;
                    case "adult": case "adults": case "xxx": case "nsfw": normalized = "18+"; break;
                    default: break;
                }
                if (!normalized.isEmpty() && !categories.contains(normalized)) categories.add(normalized);
            }
            if (!name.isEmpty() && IptvSources.validUrl(line) && !IptvSources.isWebPage(line)) {
                String identity = id.isEmpty()
                        ? country + "|" + name.toLowerCase(Locale.ROOT) : id;
                if (urls.add(identity + "|" + line)) {
                    Integer position = positions.get(identity);
                    if (position == null) {
                        long stableId = Long.parseLong(Http.sha1(identity).substring(0, 15), 16);
                        positions.put(identity, channels.size());
                        channels.add(new IptvChannel(stableId, name, logo,
                                categories.isEmpty() ? "" : categories.get(0), categories,
                                country,
                                "", line, false, "", "", "", "", id,
                                Collections.singletonList(line), originalId.equals(id) ? Collections.emptyList()
                                        : Collections.singletonList(Long.parseLong(Http.sha1(originalId).substring(0, 15), 16)),
                                Collections.singletonMap(line, new java.util.HashMap<>(requestHeaders))));
                    } else {
                        IptvChannel first = channels.get(position);
                        List<String> feeds = new ArrayList<>(first.streamUrls);
                        if (feeds.size() < 30) feeds.add(line);
                        List<Long> legacy = new ArrayList<>(first.legacyIds);
                        legacy.add(Long.parseLong(Http.sha1(identity + "|" + line).substring(0, 15), 16));
                        if (!originalId.equals(id)) {
                            legacy.add(Long.parseLong(Http.sha1(originalId).substring(0, 15), 16));
                            legacy.add(Long.parseLong(Http.sha1(originalId + "|" + line).substring(0, 15), 16));
                        }
                        List<String> genres = new ArrayList<>(first.genres);
                        for (String genre : categories) if (!genres.contains(genre)) genres.add(genre);
                        java.util.Map<String, java.util.Map<String, String>> headers = new java.util.HashMap<>(first.streamHeaders);
                        if (feeds.contains(line)) headers.put(line, new java.util.HashMap<>(requestHeaders));
                        channels.set(position, new IptvChannel(first.id, first.name, first.logo,
                                first.category, genres, first.country, first.language, first.url, false,
                                "", "", "", "", first.channelId, feeds, legacy, headers));
                    }
                }
            }
            metadata = null;
        }
        channels.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return channels;
    }

    private static int metadataComma(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"') quoted = !quoted;
            else if (line.charAt(i) == ',' && !quoted) return i;
        }
        return -1;
    }

    public static List<IptvChannel> parseCurated(String text) {
        // This provider uses country names as groups, rather than content categories.
        return parsePlaylist(text == null ? null : text.replaceAll("group-title=\"[^\"]*\"", "group-title=\"General\""), "ALL");
    }

    public static List<IptvChannel> filterCountry(List<IptvChannel> rows, String country) {
        List<IptvChannel> result = new ArrayList<>();
        for (IptvChannel row : rows) if ("ALL".equalsIgnoreCase(country) || country.equalsIgnoreCase(row.country)) result.add(row);
        return result;
    }

    /** Merge only unambiguous names; different canonical IDs must never be conflated. */
    public static List<IptvChannel> merge(List<IptvChannel> primary, List<IptvChannel> extra) {
        List<IptvChannel> result = new ArrayList<>(primary);
        java.util.Map<String, Integer> ids = new java.util.HashMap<>(), names = new java.util.HashMap<>(), urls = new java.util.HashMap<>();
        for (int i = 0; i < result.size(); i++) index(result.get(i), i, ids, names, urls);
        for (IptvChannel row : extra) {
            Integer at = row.channelId.isEmpty() ? null : ids.get(row.channelId.toLowerCase(Locale.ROOT));
            if (at == null) for (String url : row.streamUrls) { at = urls.get(url); if (at != null) break; }
            if (at == null) at = names.get(nameKey(row));
            if (at != null && at >= 0) {
                IptvChannel old = result.get(at);
                if (!old.channelId.isEmpty() && !row.channelId.isEmpty() && !old.channelId.equalsIgnoreCase(row.channelId)) at = null;
            }
            if (at == null || at < 0) { at = result.size(); result.add(row); }
            else {
                IptvChannel old = result.get(at);
                List<String> feeds = new ArrayList<>(old.streamUrls), genres = new ArrayList<>(old.genres);
                List<Long> aliases = new ArrayList<>(old.legacyIds);
                for (String feed : row.streamUrls) if (!feeds.contains(feed) && feeds.size() < 30) feeds.add(feed);
                for (String genre : row.genres) if (!genres.contains(genre)) genres.add(genre);
                if (row.id != old.id && !aliases.contains(row.id)) aliases.add(row.id);
                for (Long alias : row.legacyIds) if (!aliases.contains(alias)) aliases.add(alias);
                java.util.Map<String, java.util.Map<String, String>> headers = new java.util.HashMap<>(row.streamHeaders);
                for (String feed : old.streamHeaders.keySet()) {
                    java.util.Map<String, String> combined = new java.util.HashMap<>(headers.getOrDefault(feed, Collections.emptyMap()));
                    combined.putAll(old.streamHeaders.get(feed));
                    headers.put(feed, combined);
                }
                result.set(at, new IptvChannel(old.id, old.name, old.logo.isEmpty() ? row.logo : old.logo,
                        old.category.isEmpty() ? row.category : old.category, genres, old.country, old.language,
                        old.url, old.isFavorite, "", "", "", "", old.channelId.isEmpty() ? row.channelId : old.channelId,
                        feeds, aliases, headers));
            }
            index(result.get(at), at, ids, names, urls);
        }
        result.sort((a,b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    private static String nameKey(IptvChannel channel) {
        return channel.country + "|" + channel.name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static void index(IptvChannel row, int at, java.util.Map<String,Integer> ids,
            java.util.Map<String,Integer> names, java.util.Map<String,Integer> urls) {
        if (!row.channelId.isEmpty()) ids.put(row.channelId.toLowerCase(Locale.ROOT), at);
        String key = nameKey(row);
        Integer previous = names.get(key);
        names.put(key, previous == null || previous == at ? at : -1);
        for (String url : row.streamUrls) urls.put(url, at);
    }

    public static List<IptvChannel> addApiFeeds(List<IptvChannel> channels, String json) {
        return addApiFeeds(channels, parseApiFeeds(json));
    }

    private static java.util.Map<String, List<ApiFeed>> parseApiFeeds(String json) {
        if (json == null) return Collections.emptyMap();
        java.util.Map<String, List<ApiFeed>> feeds = new java.util.HashMap<>();
        try {
            JSONArray rows = new JSONArray(json);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null || !IptvSources.validUrl(row.optString("url"))) continue;
                feeds.computeIfAbsent(row.optString("channel"), key -> new ArrayList<>()).add(new ApiFeed(row));
            }
        } catch (Exception invalid) { return Collections.emptyMap(); }
        return feeds;
    }

    private static List<IptvChannel> addApiFeeds(List<IptvChannel> channels,
            java.util.Map<String, List<ApiFeed>> feeds) {
        if (feeds == null || feeds.isEmpty()) return channels;
        List<IptvChannel> result = new ArrayList<>();
        for (IptvChannel channel : channels) {
            List<String> urls = new ArrayList<>(channel.streamUrls);
            java.util.Map<String, java.util.Map<String, String>> headers = new java.util.HashMap<>(channel.streamHeaders);
            for (ApiFeed row : feeds.getOrDefault(channel.channelId, Collections.emptyList())) {
                String url = row.url;
                if (!urls.contains(url) && urls.size() < 30) urls.add(url);
                if (!urls.contains(url)) continue;
                java.util.Map<String, String> entry = new java.util.HashMap<>();
                if (!row.referrer.isEmpty() && !row.referrer.contains("\r") && !row.referrer.contains("\n"))
                    entry.put("Referer", row.referrer);
                if (!row.userAgent.isEmpty() && !row.userAgent.contains("\r") && !row.userAgent.contains("\n"))
                    entry.put("User-Agent", row.userAgent);
                if (!entry.isEmpty()) headers.put(url, entry);
            }
            result.add(new IptvChannel(channel.id, channel.name, channel.logo, channel.category, channel.genres,
                    channel.country, channel.language, channel.url, channel.isFavorite, "", "", "", "",
                    channel.channelId, urls, channel.legacyIds, headers));
        }
        return result;
    }

    private static String attribute(String line, String name) {
        Matcher matcher = Pattern.compile(String.format(Locale.ROOT, ATTRIBUTE.pattern(), Pattern.quote(name)))
                .matcher(line);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    public void refreshChannels(String countryCode, ChannelsCallback callback) {
        io.execute(() -> {
            File[] files = cacheDir.listFiles();
            if (files != null) for (File cache : files) if (cache.isFile()) cache.setLastModified(0);
            curatedChannels = null;
            apiFeeds = null;
            customChannels.clear();
            loadChannels(countryCode, callback);
        });
    }

    public void shutdown() {
        io.shutdownNow();
        downloads.shutdownNow();
    }
}
