package com.firestick.aura.launcher.manager;

import android.os.Handler;
import android.os.Looper;

import com.firestick.aura.launcher.model.IptvChannel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Small, bounded checks of visible feeds. Reachability does not verify decoding. Results last a
 * day (half a day for unreachable feeds) and survive restarts, so the IPTV guide can hide dead
 * channels without checking every feed again on each visit.
 */
public final class StreamAvailability {
    public interface Callback {
        void onChecked(Boolean reachable);
    }

    static final long ONLINE_TTL_MS = 24L * 60L * 60L * 1000L;
    static final long OFFLINE_TTL_MS = 12L * 60L * 60L * 1000L;
    private static final int MAX_ENTRIES = 6000;
    private static final long FLUSH_DELAY_MS = 3000L;
    private static final String FILE_NAME = "stream-status.tsv";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(
            3, 3, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64));
    /** Every read and write of the results file runs here, one at a time. */
    private static final ScheduledExecutorService DISK = Executors.newSingleThreadScheduledExecutor();
    /** Results in check order, oldest first. Guards every static field below. */
    private static final LinkedHashMap<String, Result> CACHE = new LinkedHashMap<>();
    /** Lines for the results file that the next flush appends. */
    private static final List<String> PENDING = new ArrayList<>();
    private static File file;
    private static boolean flushQueued;

    private StreamAvailability() { }

    /**
     * Loads the results saved by earlier runs from {@code dir}, once per process. Blocks on disk,
     * so call it off the main thread; later calls return at once.
     */
    public static void restore(File dir) {
        synchronized (CACHE) {
            if (file != null || dir == null) return;
            file = new File(dir, FILE_NAME);
        }
        try {
            DISK.submit(StreamAvailability::readFile).get();
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } catch (Exception unreadable) {
            // The guide checks the feeds again.
        }
    }

    public static void check(String url, Callback callback) {
        check(url, Collections.emptyMap(), callback);
    }

    private static void check(String url, Map<String, String> headers, Callback callback) {
        String cacheKey = key(url, headers);
        synchronized (CACHE) {
            Result result = CACHE.get(cacheKey);
            if (result != null && result.freshAt(System.currentTimeMillis())) {
                MAIN.post(() -> callback.onChecked(result.reachable));
                return;
            }
        }
        try {
            IO.execute(() -> {
                boolean reachable = probe(url, headers);
                synchronized (CACHE) {
                    put(cacheKey, new Result(reachable, System.currentTimeMillis()));
                }
                MAIN.post(() -> callback.onChecked(reachable));
            });
        } catch (RejectedExecutionException full) {
            MAIN.post(() -> callback.onChecked(null));
        }
    }

    /**
     * The saved state of a channel without checking it: true when a feed is reachable, false when
     * every feed is unreachable, and null while any feed has no current result.
     */
    public static Boolean cached(IptvChannel channel) {
        if (channel.streamUrls.isEmpty()) return false;
        long now = System.currentTimeMillis();
        boolean allUnreachable = true;
        synchronized (CACHE) {
            for (String url : channel.streamUrls) {
                Result result = CACHE.get(key(url, headers(channel, url)));
                if (result == null || !result.freshAt(now)) allUnreachable = false;
                else if (result.reachable) return true;
            }
        }
        return allUnreachable ? Boolean.FALSE : null;
    }

    /** Forgets every result, including the saved ones. */
    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
            PENDING.clear();
        }
        DISK.execute(() -> {
            File target;
            synchronized (CACHE) { target = file; }
            //noinspection ResultOfMethodCallIgnored
            if (target != null) target.delete();
        });
    }

    public static void checkChannel(IptvChannel channel, Callback callback) {
        checkFeed(channel, 0, callback);
    }

    private static void checkFeed(IptvChannel channel, int index, Callback callback) {
        List<String> urls = channel.streamUrls;
        if (index >= urls.size()) { callback.onChecked(false); return; }
        check(urls.get(index), headers(channel, urls.get(index)), reachable -> {
            if (Boolean.TRUE.equals(reachable)) callback.onChecked(true);
            else if (reachable == null) callback.onChecked(null);
            else checkFeed(channel, index + 1, callback);
        });
    }

    private static Map<String, String> headers(IptvChannel channel, String url) {
        return channel.streamHeaders.getOrDefault(url, Collections.emptyMap());
    }

    private static String key(String url, Map<String, String> headers) {
        return url + "|" + headers.hashCode();
    }

    /** Call with the CACHE lock held. The newest result moves to the end; the oldest go first. */
    private static void put(String cacheKey, Result result) {
        CACHE.remove(cacheKey);
        CACHE.put(cacheKey, result);
        Iterator<String> oldest = CACHE.keySet().iterator();
        while (CACHE.size() > MAX_ENTRIES && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
        if (file == null || cacheKey.indexOf('\t') >= 0 || cacheKey.indexOf('\n') >= 0 || cacheKey.indexOf('\r') >= 0) return;
        PENDING.add(result.checkedAt + "\t" + (result.reachable ? '1' : '0') + "\t" + cacheKey);
        if (!flushQueued) {
            flushQueued = true;
            DISK.schedule(StreamAvailability::flush, FLUSH_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    /** Appends new results, so a long scan writes a few bytes at a time instead of the whole file. */
    private static void flush() {
        List<String> lines;
        File target;
        synchronized (CACHE) {
            lines = new ArrayList<>(PENDING);
            PENDING.clear();
            flushQueued = false;
            target = file;
        }
        if (target == null || lines.isEmpty()) return;
        try (Writer out = new OutputStreamWriter(new FileOutputStream(target, true), StandardCharsets.UTF_8)) {
            StringBuilder text = new StringBuilder(lines.size() * 96);
            for (String line : lines) text.append(line).append('\n');
            out.write(text.toString());
        } catch (IOException unwritable) {
            // The results stay in memory for this run.
        }
    }

    /** Runs on DISK. Later lines replace earlier ones; a file mostly made of old lines is rewritten. */
    private static void readFile() {
        File source;
        synchronized (CACHE) { source = file; }
        byte[] bytes = source == null || !source.isFile() ? null : Http.readFile(source);
        if (bytes == null) return;
        long now = System.currentTimeMillis();
        LinkedHashMap<String, Result> saved = new LinkedHashMap<>();
        int lines = 0;
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\n")) {
            int first = line.indexOf('\t');
            int second = first < 0 ? -1 : line.indexOf('\t', first + 1);
            if (second != first + 2) continue;
            lines++;
            long checkedAt;
            try {
                checkedAt = Long.parseLong(line.substring(0, first));
            } catch (NumberFormatException damaged) {
                continue;
            }
            Result result = new Result(line.charAt(first + 1) == '1', checkedAt);
            String cacheKey = line.substring(second + 1);
            saved.remove(cacheKey);
            if (result.freshAt(now) && !cacheKey.isEmpty()) saved.put(cacheKey, result);
        }
        Iterator<String> oldest = saved.keySet().iterator();
        while (saved.size() > MAX_ENTRIES && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
        synchronized (CACHE) {
            LinkedHashMap<String, Result> merged = new LinkedHashMap<>(saved);
            for (Map.Entry<String, Result> newer : CACHE.entrySet()) {
                merged.remove(newer.getKey());
                merged.put(newer.getKey(), newer.getValue());
            }
            CACHE.clear();
            CACHE.putAll(merged);
        }
        if (lines > saved.size() * 2 + 200) {
            StringBuilder text = new StringBuilder(saved.size() * 96);
            for (Map.Entry<String, Result> entry : saved.entrySet()) {
                text.append(entry.getValue().checkedAt).append('\t').append(entry.getValue().reachable ? '1' : '0')
                        .append('\t').append(entry.getKey()).append('\n');
            }
            Http.writeFile(source, text.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean probe(String url, Map<String, String> headers) {
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL(url);
            if (!"https".equals(endpoint.getProtocol()) && !"http".equals(endpoint.getProtocol())) return false;
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setRequestProperty("Range", "bytes=0-1023");
            connection.setRequestProperty("User-Agent", "Lumora IPTV");
            for (Map.Entry<String, String> header : headers.entrySet()) connection.setRequestProperty(header.getKey(), header.getValue());
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return false;
            String type = connection.getContentType();
            if (type != null && type.toLowerCase(java.util.Locale.ROOT).contains("text/html")) return false;
            try (java.io.InputStream input = connection.getInputStream()) {
                return input.read() >= 0;
            }
        } catch (Exception unavailable) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static final class Result {
        final boolean reachable;
        /** Wall-clock time, so results stay valid across restarts. */
        final long checkedAt;

        Result(boolean reachable, long checkedAt) {
            this.reachable = reachable;
            this.checkedAt = checkedAt;
        }

        /** A clock set back past the check also counts as stale, so a wrong clock cannot keep results forever. */
        boolean freshAt(long nowMs) {
            long age = nowMs - checkedAt;
            return age >= 0L && age < (reachable ? ONLINE_TTL_MS : OFFLINE_TTL_MS);
        }
    }
}
