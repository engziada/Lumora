package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;

import com.firestick.aura.launcher.model.ShowItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Today's schedule from the public TVmaze API (no key; CC BY-SA, credited in the UI):
 * broadcast TV of the chosen country, that country's streaming services, and global streaming
 * services (Netflix, Disney+, ...) limited to the country's languages. The unfiltered web
 * schedule lists every country's platforms, which is why v5.0 showed Chinese titles for Egypt.
 */
public final class ShowsRepository {
    public interface Callback {
        void onLoaded(List<ShowItem> shows, boolean failed);
    }

    private static final String API = "https://api.tvmaze.com";
    private static final long CACHE_MAX_AGE_MS = 3L * 60L * 60L * 1000L;
    private static final int TV_LIMIT = 120;
    private static final int STREAMING_LIMIT = 80;

    private final File cacheDir;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public ShowsRepository(Context context) {
        cacheDir = new File(context.getApplicationContext().getCacheDir(), "shows");
    }

    public void load(String countryCode, Callback callback) {
        io.execute(() -> {
            String date = LocalDate.now().toString();
            String country = countryCode.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
            String tv = fetch(API + "/schedule?country=" + country + "&date=" + date, "tv-" + country + "-" + date);
            String localWeb = fetch(API + "/schedule/web?date=" + date + "&country=" + country, "web-" + country + "-" + date);
            String globalWeb = fetch(API + "/schedule/web?date=" + date + "&country=", "web-global-" + date);
            List<ShowItem> shows = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            boolean tvOk = parse(tv, false, null, seen, shows);
            boolean localOk = parse(localWeb, true, null, seen, shows);
            boolean globalOk = parse(globalWeb, true, languagesFor(country), seen, shows);
            boolean failed = !tvOk && !localOk && !globalOk;
            main.post(() -> callback.onLoaded(shows, failed));
        });
    }

    public void shutdown() {
        io.shutdownNow();
    }

    /** Languages accepted from global streaming services for a country. */
    static Set<String> languagesFor(String country) {
        switch (country) {
            case "EG":
            case "AE":
            case "SA":
                return new HashSet<>(Arrays.asList("Arabic", "English"));
            case "DE":
                return new HashSet<>(Arrays.asList("German", "English"));
            case "FR":
                return new HashSet<>(Arrays.asList("French", "English"));
            case "NL":
                return new HashSet<>(Arrays.asList("Dutch", "English"));
            case "IN":
                return new HashSet<>(Arrays.asList("Hindi", "English"));
            default:
                return new HashSet<>(Arrays.asList("English"));
        }
    }

    /** Fresh cache first, then network, then a stale cache entry as an offline fallback. */
    private String fetch(String url, String key) {
        File file = new File(cacheDir, key + ".json");
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < CACHE_MAX_AGE_MS) {
            byte[] cached = Http.readFile(file);
            if (cached != null) return new String(cached, StandardCharsets.UTF_8);
        }
        byte[] data = Http.get(url, 5000, 15000);
        if (data != null) {
            Http.writeFile(file, data);
            return new String(data, StandardCharsets.UTF_8);
        }
        byte[] stale = file.exists() ? Http.readFile(file) : null;
        return stale == null ? null : new String(stale, StandardCharsets.UTF_8);
    }

    private static boolean parse(String json, boolean streaming, Set<String> languages, Set<Long> seen, List<ShowItem> out) {
        if (json == null) return false;
        try {
            JSONArray episodes = new JSONArray(json);
            List<ShowItem> batch = new ArrayList<>();
            for (int i = 0; i < episodes.length(); i++) {
                JSONObject episode = episodes.optJSONObject(i);
                if (episode == null) continue;
                JSONObject show = episode.optJSONObject("show");
                if (show == null) {
                    JSONObject embedded = episode.optJSONObject("_embedded");
                    if (embedded != null) show = embedded.optJSONObject("show");
                }
                if (show == null) continue;
                if (languages != null) {
                    String language = text(show, "language");
                    if (!language.isEmpty() && !languages.contains(language)) continue;
                }
                long id = show.optLong("id", -1L);
                if (id < 0 || seen.contains(id)) continue;
                String poster = text(show.optJSONObject("image"), "medium");
                if (poster.isEmpty()) continue;
                seen.add(id);

                String channel = text(show.optJSONObject("network"), "name");
                if (channel.isEmpty()) channel = text(show.optJSONObject("webChannel"), "name");
                JSONObject rating = show.optJSONObject("rating");
                double average = rating == null || rating.isNull("average") ? 0d : rating.optDouble("average", 0d);
                StringBuilder genres = new StringBuilder();
                JSONArray genreArray = show.optJSONArray("genres");
                if (genreArray != null) {
                    for (int g = 0; g < genreArray.length() && g < 3; g++) {
                        if (genres.length() > 0) genres.append(", ");
                        genres.append(genreArray.optString(g));
                    }
                }
                String summary = Html.fromHtml(text(show, "summary"), Html.FROM_HTML_MODE_LEGACY).toString().trim();
                batch.add(new ShowItem(id, text(show, "name"), channel, text(episode, "airtime"),
                        poster.replace("http://", "https://"), summary, text(episode, "name"), genres.toString(),
                        episode.optInt("season", 0), episode.optInt("number", 0), average,
                        show.optInt("weight", 0), streaming));
            }
            if (streaming) batch.sort((a, b) -> Integer.compare(b.weight, a.weight));
            int limit = streaming ? STREAMING_LIMIT : TV_LIMIT;
            for (int i = 0; i < batch.size() && i < limit; i++) out.add(batch.get(i));
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    /** org.json returns the string "null" for JSON null; treat it as empty. */
    private static String text(JSONObject object, String key) {
        if (object == null || object.isNull(key)) return "";
        return object.optString(key, "");
    }
}
