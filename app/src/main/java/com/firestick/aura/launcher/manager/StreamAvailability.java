package com.firestick.aura.launcher.manager;

import android.os.Handler;
import android.os.Looper;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Small, bounded checks of visible feeds. Reachability does not verify decoding. */
public final class StreamAvailability {
    public interface Callback {
        void onChecked(Boolean reachable);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(
            3, 3, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(24));
    private static final LinkedHashMap<String, Result> CACHE = new LinkedHashMap<>();
    private static final long TTL_MS = 5 * 60 * 1000L;

    private StreamAvailability() { }

    public static void check(String url, Callback callback) {
        synchronized (CACHE) {
            Result result = CACHE.get(url);
            if (result != null && android.os.SystemClock.elapsedRealtime() - result.checkedAt < TTL_MS) {
                MAIN.post(() -> callback.onChecked(result.reachable));
                return;
            }
        }
        try {
            IO.execute(() -> {
                boolean reachable = probe(url);
                synchronized (CACHE) {
                    CACHE.put(url, new Result(reachable));
                    while (CACHE.size() > 256) CACHE.remove(CACHE.keySet().iterator().next());
                }
                MAIN.post(() -> callback.onChecked(reachable));
            });
        } catch (RejectedExecutionException full) {
            MAIN.post(() -> callback.onChecked(null));
        }
    }

    private static boolean probe(String url) {
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL(url);
            if (!"https".equals(endpoint.getProtocol()) && !"http".equals(endpoint.getProtocol())) return false;
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setRequestProperty("Range", "bytes=0-1023");
            connection.setRequestProperty("User-Agent", "Lumora IPTV");
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

    private static final class Result {
        final boolean reachable;
        final long checkedAt = android.os.SystemClock.elapsedRealtime();

        Result(boolean reachable) {
            this.reachable = reachable;
        }
    }
}
