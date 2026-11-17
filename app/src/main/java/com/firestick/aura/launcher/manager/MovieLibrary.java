package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.model.Movie;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Films for the Movies tab. Shelves come from Pluto TV's free On Demand categories, newest films
 * first and nothing older than {@value #OLDEST}. Where Pluto TV does not answer, the tab falls
 * back to feature films on the Internet Archive that their uploaders marked public domain.
 * Shelves stay cached for half a day; the player resumes where each film stopped.
 */
public final class MovieLibrary {
    public static final class Shelf {
        public final String key;
        public final int title;
        public final boolean mature;
        /** Archive search terms; empty for Pluto shelves. */
        final String query;
        /** Pluto On Demand category ID; empty for archive shelves. */
        final String category;
        /** Pluto titles to read; genre shelves read more and keep the newest. */
        final int size;
        final boolean newestFirst;

        Shelf(String key, int title, String query, boolean mature) {
            this(key, title, query, "", 0, false, mature);
        }

        private Shelf(String key, int title, String query, String category, int size, boolean newestFirst,
                      boolean mature) {
            this.key = key;
            this.title = title;
            this.query = query;
            this.category = category;
            this.size = size;
            this.newestFirst = newestFirst;
            this.mature = mature;
        }

        static Shelf pluto(String key, int title, String category, int size, boolean newestFirst, boolean mature) {
            return new Shelf("pluto_" + key, title, "", category, size, newestFirst, mature);
        }

        public boolean isPluto() {
            return !category.isEmpty();
        }
    }

    /** A film started and not finished, with where it stopped. */
    public static final class Resume {
        public final Movie movie;
        public final long position, duration;

        Resume(Movie movie, long position, long duration) {
            this.movie = movie;
            this.position = position;
            this.duration = duration;
        }
    }

    public static final String PREFS = "movies";
    /** Only items marked public domain: the rest of feature_films may still be under copyright. */
    static final String BASE = "collection:feature_films AND mediatype:movies AND licenseurl:*publicdomain*";
    static final String MATURE = "(subject:(exploitation OR sexploitation OR erotic OR erotica OR nudist OR nudism"
            + " OR nudity OR nudie OR burlesque OR striptease OR \"adults only\" OR \"stag film\")"
            + " OR title:(nudist OR nudists OR nudism OR sex OR erotic OR burlesque OR striptease))";
    public static final List<Shelf> ARCHIVE_SHELVES = Collections.unmodifiableList(Arrays.asList(
            new Shelf("popular", R.string.movies_popular, "", false),
            new Shelf("noir", R.string.movies_noir, "subject:\"film noir\"", false),
            new Shelf("comedy", R.string.movies_comedy, "subject:comedy", false),
            new Shelf("horror", R.string.movies_horror, "subject:horror", false),
            new Shelf("scifi", R.string.movies_scifi, "subject:(\"science fiction\" OR \"sci-fi\" OR scifi)", false),
            new Shelf("western", R.string.movies_western, "subject:(western OR westerns)", false),
            new Shelf("action", R.string.movies_action, "subject:(action OR adventure)", false),
            new Shelf("romance", R.string.movies_romance, "subject:romance", false),
            new Shelf("drama", R.string.movies_drama, "subject:drama", false),
            new Shelf("mature", R.string.movies_mature, MATURE, true)));
    /** Pluto TV's US On Demand categories. A category Pluto removes leaves its shelf hidden. */
    public static final List<Shelf> PLUTO_SHELVES = Collections.unmodifiableList(Arrays.asList(
            Shelf.pluto("recent", R.string.movies_recent, "65e203ca66eec80008a0641a", 60, false, false),
            Shelf.pluto("trending", R.string.movies_trending, "606b3540ad13b100076f94e6", 60, false, false),
            Shelf.pluto("new", R.string.movies_new, "58c34fd4250dc501634e608b", 120, false, false),
            Shelf.pluto("top", R.string.movies_top, "58e1a299dca3c3a22fbd8319", 100, false, false),
            Shelf.pluto("action", R.string.movies_action, "6786c66957fff50008f97d76", 60, true, false),
            Shelf.pluto("thriller", R.string.movies_thriller, "592f622023f94c143e6e4dec", 200, true, false),
            Shelf.pluto("horror", R.string.movies_horror, "68dc2d1c1d26140175b98ac3", 60, true, false),
            Shelf.pluto("comedy", R.string.movies_comedy, "594dbf46a808923d4e1689fb", 200, true, false),
            Shelf.pluto("scifi", R.string.movies_scifi, "5b8eb1dfaff98879a8ee8ddf", 200, true, false),
            Shelf.pluto("crime", R.string.movies_crime, "5d4b47efc72321b15fa64a71", 200, true, false),
            Shelf.pluto("romance", R.string.movies_romance, "5b8ace7d913fbf798a080ce0", 200, true, false),
            Shelf.pluto("drama", R.string.movies_drama, "58c49d9d250dc501634e63d4", 200, true, false),
            Shelf.pluto("a24", R.string.movies_a24, "628bdd98fe79df0007c5a2c7", 80, true, false),
            Shelf.pluto("late", R.string.movies_mature, "5bbe7c9d74fb2e5d05a92c10", 150, true, true)));

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    private static final Pattern YEAR = Pattern.compile("\\b(1[89]\\d\\d|20\\d\\d)\\b");
    /** Pluto slugs end in the release year and two counters: "house-of-gucci-2021-1-1". */
    private static final Pattern SLUG_YEAR = Pattern.compile("-((?:19|20)\\d\\d)-\\d+-\\d+$");
    private static final Pattern NAME_YEAR = Pattern.compile("\\(((?:19|20)\\d\\d)\\)\\s*$");
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,3}):(\\d{2})(?::(\\d{2}))?");
    private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*h");
    private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*m");
    private static final String HISTORY = "history";
    private static final int ROWS = 40, HISTORY_MAX = 12;
    /** Pluto films released before this year are left out: the tab is for recent films. */
    static final int OLDEST = 1990;
    private static final long FRESH_MS = 12L * 60L * 60L * 1000L;

    private final File dir, cacheRoot;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, List<Movie>> shelves = new HashMap<>();

    public MovieLibrary(Context context) {
        cacheRoot = context.getApplicationContext().getCacheDir();
        dir = new File(cacheRoot, "movies");
    }

    /** Calls back on the main thread with the shelf's films; empty when offline with no cache. */
    public void load(Shelf shelf, Consumer<List<Movie>> done) {
        List<Movie> known = shelves.get(shelf.key);
        if (known != null) {
            done.accept(known);
            return;
        }
        io.execute(() -> {
            File file = new File(dir, shelf.key + ".json");
            List<Movie> movies = Collections.emptyList();
            if (file.exists() && System.currentTimeMillis() - file.lastModified() < FRESH_MS) movies = read(file, shelf);
            if (movies.isEmpty()) {
                byte[] body = download(shelf);
                movies = body == null ? Collections.emptyList() : parse(new String(body, StandardCharsets.UTF_8), shelf);
                if (!movies.isEmpty()) Http.writeFile(file, body);
                else if (file.exists()) movies = read(file, shelf);
            }
            List<Movie> result = movies;
            main.post(() -> {
                if (!result.isEmpty()) shelves.put(shelf.key, result);
                done.accept(result);
            });
        });
    }

    private byte[] download(Shelf shelf) {
        if (!shelf.isPluto()) return Http.getBounded(searchUrl(shelf, ROWS), 8000, 15000, 2 * 1024 * 1024);
        PlutoSession session = PlutoSession.get(cacheRoot);
        return session == null ? null : session.fetch(session.vod + "/v4/vod/categories/" + shelf.category
                + "/items?offset=" + shelf.size + "&page=1", 4 * 1024 * 1024);
    }

    /** Finds a stream for the film; calls back on the main thread with its URL, or null. */
    public void resolve(Movie movie, Consumer<String> done) {
        io.execute(() -> {
            String url;
            if (movie.isPluto()) {
                PlutoSession session = PlutoSession.get(cacheRoot);
                url = session == null || !ID.matcher(movie.id).matches() ? null : session.playUrl(movie.id);
            } else {
                byte[] body = Http.getBounded("https://archive.org/metadata/" + movie.id + "/files",
                        8000, 15000, 4 * 1024 * 1024);
                url = body == null ? null : pickVideo(movie.id, new String(body, StandardCharsets.UTF_8));
            }
            main.post(() -> done.accept(url));
        });
    }

    public void shutdown() {
        io.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }

    private static List<Movie> read(File file, Shelf shelf) {
        byte[] data = Http.readFile(file);
        return data == null ? Collections.emptyList() : parse(new String(data, StandardCharsets.UTF_8), shelf);
    }

    static List<Movie> parse(String json, Shelf shelf) {
        return shelf.isPluto() ? parsePluto(json, shelf) : parseSearch(json, shelf.mature);
    }

    /**
     * Films from one Pluto On Demand category: series, titles released before {@value #OLDEST}
     * and, outside the 18+ shelf, NC-17 titles are left out. Genre shelves put the newest first.
     */
    static List<Movie> parsePluto(String json, Shelf shelf) {
        List<Movie> movies = new ArrayList<>();
        try {
            JSONArray items = new JSONObject(json).getJSONArray("items");
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null || !"movie".equals(item.optString("type"))) continue;
                String id = item.optString("_id"), title = cleanText(item.optString("name"));
                if (!ID.matcher(id).matches() || title.isEmpty() || !seen.add(id)) continue;
                String rating = item.optString("rating").trim();
                if (!shelf.mature && rating.equalsIgnoreCase("NC-17")) continue;
                if (rating.equalsIgnoreCase("Not Rated")) rating = "NR";
                String year = plutoYear(item.optString("slug"), title);
                if (!year.isEmpty() && Integer.parseInt(year) < OLDEST) continue;
                long length = item.optLong("originalContentDuration");
                int minutes = (int) Math.round(length / 60_000.0);
                String description = item.optString("description");
                if (description.isEmpty()) description = item.optString("summary");
                movies.add(new Movie(Movie.PLUTO, id, title, year, minutes > 0 && minutes <= 600 ? minutes : 0,
                        cleanText(description), shelf.mature, rating.length() <= 8 ? rating : "",
                        cleanText(item.optString("genre")), plutoImage(item)));
            }
        } catch (Exception malformed) {
            // A broken answer shows no films; the shelf stays hidden.
        }
        // Stable: films from the same year keep Pluto's order, and films with no year go last.
        if (shelf.newestFirst) movies.sort((a, b) -> b.year.compareTo(a.year));
        return movies.size() > ROWS ? new ArrayList<>(movies.subList(0, ROWS)) : movies;
    }

    static String plutoYear(String slug, String title) {
        Matcher match = SLUG_YEAR.matcher(slug == null ? "" : slug);
        if (match.find()) return match.group(1);
        match = NAME_YEAR.matcher(title == null ? "" : title);
        return match.find() ? match.group(1) : "";
    }

    /** Pluto's 16:9 key art, which carries the title, asked for at card size. */
    private static String plutoImage(JSONObject item) {
        String url = path(item.optJSONObject("poster16_9"));
        JSONArray covers = item.optJSONArray("covers");
        for (int i = 0; url.isEmpty() && covers != null && i < covers.length(); i++) {
            JSONObject cover = covers.optJSONObject(i);
            if (cover != null && "16:9".equals(cover.optString("aspectRatio"))) url = cover.optString("url");
        }
        if (url.isEmpty()) url = path(item.optJSONObject("featuredImage"));
        if (!url.startsWith("https://images.pluto.tv/")) return "";
        int query = url.indexOf('?');
        return (query < 0 ? url : url.substring(0, query)) + "?fill=blur&fit=fill&fm=jpg&q=75&w=480&h=270";
    }

    private static String path(JSONObject image) {
        return image == null ? "" : image.optString("path");
    }

    static String searchUrl(Shelf shelf, int rows) {
        String query = BASE + (shelf.query.isEmpty() ? "" : " AND " + shelf.query)
                + (shelf.mature ? "" : " AND NOT " + MATURE);
        return "https://archive.org/advancedsearch.php?q=" + encode(query)
                + "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year&fl%5B%5D=runtime&fl%5B%5D=description"
                + "&sort%5B%5D=downloads+desc&rows=" + rows + "&output=json";
    }

    public static List<Movie> parseSearch(String json, boolean mature) {
        List<Movie> movies = new ArrayList<>();
        try {
            JSONArray docs = new JSONObject(json).getJSONObject("response").getJSONArray("docs");
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < docs.length(); i++) {
                JSONObject doc = docs.optJSONObject(i);
                if (doc == null) continue;
                String id = doc.optString("identifier"), title = cleanText(text(doc, "title"));
                if (!ID.matcher(id).matches() || title.isEmpty() || !seen.add(id)) continue;
                Matcher year = YEAR.matcher(text(doc, "year"));
                movies.add(new Movie(id, title, year.find() ? year.group(1) : "", minutes(text(doc, "runtime")),
                        cleanText(text(doc, "description")), mature));
            }
        } catch (Exception malformed) {
            // A broken answer shows no films; the shelf stays hidden.
        }
        return movies;
    }

    /** Archive fields hold a string, a number or a list of strings. */
    private static String text(JSONObject doc, String key) {
        Object value = doc.opt(key);
        if (value instanceof JSONArray) {
            JSONArray parts = (JSONArray) value;
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) joined.append(i == 0 ? "" : " ").append(parts.optString(i));
            return joined.toString();
        }
        return value == null || value == JSONObject.NULL ? "" : value.toString();
    }

    /** Plain text from the archive's HTML descriptions, cut to a few lines. */
    static String cleanText(String html) {
        if (html == null) return "";
        String text = html.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").replaceAll("\\s+", " ").trim();
        return text.length() > 600 ? text.substring(0, 599).trim() + "\u2026" : text;
    }

    /** Minutes from free-form running times: "67 min", "1:31:44", "1h 30m" or "95". 0 when unclear. */
    static int minutes(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int total = 0;
        try {
            Matcher clock = CLOCK.matcher(value), hours = HOURS.matcher(value), mins = MINUTES.matcher(value);
            boolean hasHours = hours.find(), hasMinutes = mins.find();
            if (clock.find()) {
                int first = Integer.parseInt(clock.group(1)), second = Integer.parseInt(clock.group(2));
                // h:mm:ss, or h:mm for a short first part (features run past an hour), else mm:ss.
                if (clock.group(3) != null) total = first * 60 + second + (Integer.parseInt(clock.group(3)) >= 30 ? 1 : 0);
                else total = first < 10 ? first * 60 + second : first + (second >= 30 ? 1 : 0);
            } else if (hasHours && hasMinutes && mins.start() > hours.start()) {
                total = Integer.parseInt(hours.group(1)) * 60 + Integer.parseInt(mins.group(1));
            } else if (hasMinutes) {
                total = Integer.parseInt(mins.group(1));
            } else if (hasHours) {
                total = Integer.parseInt(hours.group(1)) * 60;
            } else if (value.matches("\\d{2,3}")) {
                total = Integer.parseInt(value);
            }
        } catch (NumberFormatException tooLong) {
            return 0;
        }
        return total > 0 && total <= 600 ? total : 0;
    }

    /** The archive's H.264 copy plays on every Fire TV; MPEG-2, Ogg and DivX often do not. */
    static String pickVideo(String id, String filesJson) {
        if (!ID.matcher(id).matches()) return null;
        String best = null;
        int bestRank = Integer.MAX_VALUE;
        try {
            JSONObject root = new JSONObject(filesJson);
            JSONArray files = root.has("result") ? root.getJSONArray("result") : root.getJSONArray("files");
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.optJSONObject(i);
                if (file == null) continue;
                String name = file.optString("name"), format = file.optString("format").toLowerCase(Locale.ROOT);
                if (!name.toLowerCase(Locale.ROOT).endsWith(".mp4") || name.contains("..")) continue;
                int rank = format.equals("h.264") ? 0 : format.equals("mpeg4") ? 1 : format.startsWith("h.264") ? 2
                        : format.equals("512kb mpeg4") ? 3 : 4;
                if (rank < bestRank) {
                    bestRank = rank;
                    best = name;
                }
            }
        } catch (Exception malformed) {
            return null;
        }
        if (best == null) return null;
        StringBuilder path = new StringBuilder("https://archive.org/download/").append(id);
        for (String part : best.split("/")) path.append('/').append(encode(part).replace("+", "%20"));
        return path.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    // ------------------------------------------------------------------ resume

    public static List<Resume> history(SharedPreferences prefs) {
        return parseHistory(historyText(prefs));
    }

    /** The stored list as text; it changes whenever a film starts, stops or ends. */
    public static String historyText(SharedPreferences prefs) {
        return prefs.getString(HISTORY, "[]");
    }

    public static long resumeAt(SharedPreferences prefs, String id) {
        for (Resume entry : history(prefs)) if (entry.movie.id.equals(id)) return entry.position;
        return 0;
    }

    public static void remember(SharedPreferences prefs, Movie movie, long position, long duration) {
        prefs.edit().putString(HISTORY, remember(prefs.getString(HISTORY, "[]"), movie, position, duration)).apply();
    }

    /**
     * Puts the film first when it stopped after the first minute and before the closing credits;
     * otherwise it leaves the list. Keeps the latest {@value #HISTORY_MAX}.
     */
    static String remember(String json, Movie movie, long position, long duration) {
        JSONArray next = new JSONArray();
        boolean finished = duration > 0 && (duration - position < 180_000L || position > duration * 0.95);
        try {
            if (position >= 60_000L && !finished) {
                next.put(toJson(movie).put("position", position).put("duration", Math.max(0L, duration)));
            }
            JSONArray old = new JSONArray(json);
            for (int i = 0; i < old.length() && next.length() < HISTORY_MAX; i++) {
                JSONObject entry = old.optJSONObject(i);
                if (entry != null && !movie.id.equals(entry.optString("id"))) next.put(entry);
            }
        } catch (Exception malformed) {
            // A damaged list starts over with this film.
        }
        return next.toString();
    }

    static List<Resume> parseHistory(String json) {
        List<Resume> entries = new ArrayList<>();
        try {
            JSONArray rows = new JSONArray(json);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                Movie movie = row == null ? null : fromJson(row);
                if (movie != null) entries.add(new Resume(movie, row.optLong("position"), row.optLong("duration")));
            }
        } catch (Exception malformed) {
            // Nothing to resume.
        }
        return entries;
    }

    public static JSONObject toJson(Movie movie) {
        try {
            return new JSONObject().put("source", movie.source).put("id", movie.id).put("title", movie.title)
                    .put("year", movie.year).put("minutes", movie.minutes).put("description", movie.description)
                    .put("mature", movie.mature).put("rating", movie.rating).put("genre", movie.genre)
                    .put("image", movie.image);
        } catch (org.json.JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static Movie fromJson(String json) {
        try {
            return json == null ? null : fromJson(new JSONObject(json));
        } catch (Exception malformed) {
            return null;
        }
    }

    /** Entries saved before Pluto films existed have no source and are archive films. */
    static Movie fromJson(JSONObject row) {
        String id = row.optString("id"), title = row.optString("title"), image = row.optString("image");
        if (!ID.matcher(id).matches() || title.isEmpty()) return null;
        return new Movie(row.optString("source", Movie.ARCHIVE), id, title, row.optString("year"), row.optInt("minutes"),
                row.optString("description"), row.optBoolean("mature"), row.optString("rating"), row.optString("genre"),
                image.startsWith("https://") ? image : "");
    }
}
