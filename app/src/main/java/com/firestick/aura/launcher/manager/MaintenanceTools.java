package com.firestick.aura.launcher.manager;

import android.content.Context;

import com.firestick.aura.launcher.update.UpdateManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Maintenance tools (Settings › Tools): a Lumora-owned dashboard over the launcher's own cache
 * folders with a one-button cleanup, and a network doctor that probes every service the launcher
 * talks to (software updates, video previews, TV schedules, wallpapers, posters). Blocking work
 * here must run off the main thread.
 */
public final class MaintenanceTools {
    private MaintenanceTools() {
    }

    /** One cache folder the launcher owns, with its human label. */
    public static final class Dir {
        public final String label;
        public final File dir;

        Dir(String label, File dir) {
            this.label = label;
            this.dir = dir;
        }
    }

    /** A service the launcher talks to. */
    public static final class Probe {
        public final String label;
        public final String url;

        public Probe(String label, String url) {
            this.label = label;
            this.url = url;
        }
    }

    /** One probe outcome: ok/failure, round-trip milliseconds and the reason on failure. */
    public static final class Result {
        public final String label;
        public final boolean ok;
        public final long ms;
        public final String detail;

        Result(String label, boolean ok, long ms, String detail) {
            this.label = label;
            this.ok = ok;
            this.ms = ms;
            this.detail = detail;
        }
    }

    private static final int CONNECT_TIMEOUT_MS = 6_000, READ_TIMEOUT_MS = 10_000;

    /** Every cache folder the launcher itself writes to, in dashboard order. */
    public static List<Dir> cacheDirs(Context context) {
        File cache = context.getApplicationContext().getCacheDir();
        List<Dir> dirs = new ArrayList<>();
        dirs.add(new Dir("Wallpaper stills", new File(cache, "wallpapers")));
        dirs.add(new Dir("Show posters", new File(cache, "images")));
        dirs.add(new Dir("App logos", new File(cache, "logos")));
        dirs.add(new Dir("Video preview feeds", new File(cache, "preview-feeds")));
        dirs.add(new Dir("TV schedule cache", new File(cache, "shows")));
        dirs.add(new Dir("Update downloads", new File(cache, "updates")));
        return dirs;
    }

    /** Recursive size of a tree; unreadable entries count as zero. */
    public static long size(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        File[] children = file.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File child : children) total += size(child);
        return total;
    }

    /**
     * Deletes the contents of a folder (never the folder itself) and returns the freed bytes.
     * Missing or unreadable folders count as cleaned.
     */
    public static long deleteContents(File dir) {
        if (dir == null || !dir.isDirectory()) return 0L;
        File[] children = dir.listFiles();
        if (children == null) return 0L;
        long freed = 0L;
        for (File child : children) {
            freed += size(child);
            delete(child);
        }
        return freed;
    }

    private static void delete(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) delete(child);
        }
        file.delete();
    }

    /** Services probed by the network doctor, in display order. */
    public static List<Probe> probes() {
        List<Probe> list = new ArrayList<>();
        list.add(new Probe("Software updates", UpdateManager.UPDATE_URL));
        list.add(new Probe("Video previews", "https://www.youtube.com/feeds/videos.xml?channel_id=UCBR8-60-B28hp2BmDPdntcQ"));
        list.add(new Probe("TV schedules (TVMaze)", "https://api.tvmaze.com/schedule?country=US"));
        list.add(new Probe("Wallpapers (ESA/Hubble)", "https://cdn.esahubble.org/archives/images/screen/heic1509a.jpg"));
        list.add(new Probe("Posters (Unsplash)", "https://images.unsplash.com/photo-1462331940025-496dfbfc7564?w=64&q=30"));
        list.add(new Probe("Assistant (OpenRouter)", "https://openrouter.ai/api/v1/models"));
        return list;
    }

    /**
     * Runs one probe: a small GET with the launcher's timeouts. DNS failures, TLS failures and
     * HTTP errors are all reported in {@code detail}; the round trip includes connect time, so
     * the number is meaningful even when the response body is tiny.
     */
    public static Result probe(Probe target) {
        HttpURLConnection connection = null;
        long startedAt = System.currentTimeMillis();
        try {
            connection = (HttpURLConnection) new URL(target.url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Lumora-Network-Doctor");
            int status = connection.getResponseCode();
            long ms = System.currentTimeMillis() - startedAt;
            if (status >= 200 && status < 400) return new Result(target.label, true, ms, "HTTP " + status);
            return new Result(target.label, false, ms, "HTTP " + status);
        } catch (IOException exception) {
            long ms = System.currentTimeMillis() - startedAt;
            String reason = exception.getClass().getSimpleName();
            if (exception instanceof java.net.UnknownHostException) reason = "DNS lookup failed";
            else if (exception instanceof java.net.ConnectException) reason = "connection refused";
            else if (exception instanceof javax.net.ssl.SSLException) reason = "TLS error";
            return new Result(target.label, false, ms, reason);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
