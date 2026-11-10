package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Xml;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

/** On-demand XMLTV current/next lookup using the public guide catalog. */
public final class EpgRepository {
    public interface Callback { void onLoaded(String guide); }
    private final File cache;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    public EpgRepository(Context context) { cache = new File(context.getCacheDir(), "iptv-guides.json"); }

    public void load(String channelId, Callback callback) {
        io.execute(() -> {
            String result = lookup(channelId);
            main.post(() -> callback.onLoaded(result));
        });
    }

    private String lookup(String channelId) {
        if (channelId.isEmpty()) return "";
        try {
            boolean cached = cache.isFile() && System.currentTimeMillis() - cache.lastModified() < 14400000;
            byte[] catalog = cached ? Http.readFile(cache) : Http.get("https://iptv-org.github.io/api/guides.json", 5000, 8000);
            if (catalog == null) return "";
            if (!cached) Http.writeFile(cache, catalog);
            JSONArray rows = new JSONArray(new String(catalog, StandardCharsets.UTF_8));
            int attempts = 0;
            for (int i = 0; i < rows.length() && attempts < 2; i++) {
                JSONObject row = rows.getJSONObject(i);
                if (!channelId.equals(row.optString("channel"))) continue;
                JSONArray sources = row.optJSONArray("sources");
                if (sources == null) continue;
                for (int n = 0; n < sources.length() && attempts < 2; n++) {
                    String url = sources.getJSONObject(n).optString("url");
                    if (!url.startsWith("https://") && !url.startsWith("http://")) continue;
                    attempts++;
                    String programs = readGuide(url, channelId, row.optString("site_id"));
                    if (!programs.isEmpty()) return programs;
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    private String readGuide(String url, String channelId, String siteId) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            if (connection.getResponseCode() != 200) return "";
            InputStream input = new BufferedInputStream(connection.getInputStream());
            if (url.endsWith(".gz") || "gzip".equals(connection.getContentEncoding())) input = new GZIPInputStream(input);
            // Bound decompressed XML to protect memory and slow providers.
            try (InputStream bounded = new LimitedInputStream(input, 12 * 1024 * 1024)) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(bounded, null);
                List<Program> programs = new ArrayList<>();
                long now = System.currentTimeMillis();
                long deadline = android.os.SystemClock.elapsedRealtime() + 12000;
                String title = "", channel = "", start = "", stop = "";
                for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                    if (android.os.SystemClock.elapsedRealtime() > deadline) break;
                    if (event == XmlPullParser.START_TAG && "programme".equals(parser.getName())) {
                        channel = parser.getAttributeValue(null, "channel");
                        start = parser.getAttributeValue(null, "start");
                        stop = parser.getAttributeValue(null, "stop");
                        title = "";
                    } else if (event == XmlPullParser.START_TAG && "title".equals(parser.getName())) {
                        title = parser.nextText();
                    } else if (event == XmlPullParser.END_TAG && "programme".equals(parser.getName())
                            && (channelId.equals(channel) || siteId.equals(channel))) {
                        long begins = time(start), ends = time(stop);
                        if (ends > now && begins > 0 && !title.isEmpty()) programs.add(new Program(begins, ends, title));
                    }
                }
                programs.sort(Comparator.comparingLong(program -> program.start));
                StringBuilder result = new StringBuilder();
                for (Program program : programs) {
                    if (result.length() > 0) result.append("\n\n");
                    result.append(program.start <= now ? "Now · " : "Next · ")
                            .append(DateTimeFormatter.ofPattern("HH:mm").format(
                                    java.time.Instant.ofEpochMilli(program.start).atZone(java.time.ZoneId.systemDefault())))
                            .append("  ").append(program.title);
                    if (result.indexOf("\n\n") >= 0) break;
                }
                return result.toString();
            }
        } catch (Exception ignored) { return ""; }
        finally { if (connection != null) connection.disconnect(); }
    }

    public static long time(String raw) {
        if (raw == null) return 0;
        try {
            return OffsetDateTime.parse(raw.trim(), DateTimeFormatter.ofPattern("yyyyMMddHHmmss xx"))
                    .toInstant().toEpochMilli();
        } catch (Exception ignored) { return 0; }
    }

    public void shutdown() { io.shutdownNow(); }
    private static final class Program {
        final long start, stop;
        final String title;
        Program(long start, long stop, String title) { this.start = start; this.stop = stop; this.title = title; }
    }
    private static final class LimitedInputStream extends FilterInputStream {
        private int remaining;
        LimitedInputStream(InputStream in, int limit) { super(in); remaining = limit; }
        @Override public int read() throws IOException {
            if (--remaining < 0) throw new IOException("Guide too large");
            return in.read();
        }
        @Override public int read(byte[] data, int offset, int length) throws IOException {
            if (remaining <= 0) throw new IOException("Guide too large");
            int count = in.read(data, offset, Math.min(length, remaining));
            if (count > 0) remaining -= count;
            return count;
        }
    }
}
