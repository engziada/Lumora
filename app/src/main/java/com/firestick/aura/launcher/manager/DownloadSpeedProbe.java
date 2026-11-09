package com.firestick.aura.launcher.manager;

import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/** Explicit, bounded single-stream internet download estimate; never runs on focus. */
public final class DownloadSpeedProbe {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private volatile boolean canceled;
    private volatile HttpURLConnection connection;
    public void cancel() {
        canceled = true;
        HttpURLConnection active = connection;
        if (active != null) active.disconnect();
    }
    public double measure() throws IOException {
        return measure(new URL("https://speed.cloudflare.com/__down?bytes=" + MAX_BYTES + "&cache=" + System.nanoTime()));
    }
    double measure(URL endpoint) throws IOException {
        long bytes = 0;
        long started = System.nanoTime();
        HttpURLConnection request = (HttpURLConnection) endpoint.openConnection();
        connection = request;
        try {
            if (canceled) throw new IOException("Canceled");
            request.setConnectTimeout(4000);
            request.setReadTimeout(3000);
            request.setUseCaches(false);
            request.setRequestProperty("Accept-Encoding", "identity");
            request.setRequestProperty("Cache-Control", "no-cache");
            if (request.getResponseCode() != 200) throw new IOException("Download unavailable");
            try (InputStream input = request.getInputStream()) {
                byte[] buffer = new byte[32768];
                long transferStart = System.nanoTime();
                int count;
                while (!canceled && bytes < MAX_BYTES && System.nanoTime() - started < 10_000_000_000L
                        && (count = input.read(buffer, 0, (int) Math.min(buffer.length, MAX_BYTES - bytes))) != -1) bytes += count;
                if (canceled || bytes == 0) throw new IOException("Download interrupted");
                return megabitsPerSecond(bytes, System.nanoTime() - transferStart);
            }
        } finally { request.disconnect(); connection = null; }
    }
    static double megabitsPerSecond(long bytes, long nanos) {
        return nanos <= 0 ? 0 : bytes * 8000.0 / nanos;
    }
}
