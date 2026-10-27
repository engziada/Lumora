package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads optional publisher logos and caches the normalized 128 px bitmap on disk. */
public final class IconService {
    private static final String ENDPOINT = "https://www.google.com/s2/favicons?sz=256&domain=";
    private static final Map<String, String> DOMAINS;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("com.amazon.firetv.youtube", "youtube.com");
        map.put("com.google.android.youtube", "youtube.com");
        map.put("com.netflix.ninja", "netflix.com");
        map.put("com.amazon.avod.thirdpartyclient", "primevideo.com");
        map.put("com.amazon.amazonvideo.livingroom", "primevideo.com");
        map.put("com.disney.disneyplus", "disneyplus.com");
        map.put("com.hulu.plus", "hulu.com");
        map.put("com.hbo.hbonow", "max.com");
        map.put("com.wbd.stream", "max.com");
        map.put("com.plexapp.android", "plex.tv");
        map.put("org.xbmc.kodi", "kodi.tv");
        map.put("com.spotify.tv.android", "spotify.com");
        map.put("com.crunchyroll.crunchyroid", "crunchyroll.com");
        map.put("com.stremio.one", "stremio.com");
        map.put("org.videolan.vlc", "videolan.org");
        map.put("com.twitch.android.app", "twitch.tv");
        map.put("com.tubitv.ott", "tubitv.com");
        map.put("tv.pluto.android", "pluto.tv");
        map.put("com.apple.atve.androidtv.appletv", "tv.apple.com");
        map.put("cm.aptoidetv.pt", "aptoide.com");
        DOMAINS = Collections.unmodifiableMap(map);
    }

    private static volatile IconService instance;
    private final Context context;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memory = new LruCache<>(48);
    private final Set<String> inFlight = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, List<Callback>> waiters = new HashMap<>();

    public interface Callback { void onIcon(Bitmap bitmap); }

    private IconService(Context context) { this.context = context.getApplicationContext(); }

    public static IconService get(Context context) {
        if (instance == null) synchronized (IconService.class) {
            if (instance == null) instance = new IconService(context);
        }
        return instance;
    }

    public boolean hasLogo(String packageName) { return DOMAINS.containsKey(packageName); }

    public void load(String packageName, Callback callback) {
        String domain = DOMAINS.get(packageName);
        if (domain == null) return;
        Bitmap cached = memory.get(packageName);
        if (cached != null) { callback.onIcon(cached); return; }
        synchronized (waiters) {
            if (inFlight.contains(packageName)) {
                waiters.computeIfAbsent(packageName, ignored -> new ArrayList<>()).add(callback);
                return;
            }
            inFlight.add(packageName);
        }
        io.execute(() -> {
            Bitmap bitmap = readDisk(packageName);
            if (bitmap == null) {
                bitmap = download(domain);
                if (bitmap != null) writeDisk(packageName, bitmap);
            }
            inFlight.remove(packageName);
            List<Callback> queued;
            synchronized (waiters) { queued = waiters.remove(packageName); }
            if (bitmap == null) return;
            memory.put(packageName, bitmap);
            Bitmap result = bitmap;
            main.post(() -> {
                callback.onIcon(result);
                if (queued != null) for (Callback waiter : queued) waiter.onIcon(result);
            });
        });
    }

    private Bitmap download(String domain) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(ENDPOINT + domain).openConnection();
            connection.setConnectTimeout(4000); connection.setReadTimeout(5000); connection.connect();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                return decode(out.toByteArray(), 2);
            }
        } catch (Exception e) { return null; }
        finally { if (connection != null) connection.disconnect(); }
    }

    private Bitmap decode(byte[] data, int sampleSize) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (bitmap == null) return null;
            if (bitmap.getWidth() < 32 || bitmap.getHeight() < 32) { bitmap.recycle(); return null; }
            return bitmap;
        } catch (Exception e) { return null; }
    }

    private File iconFile(String packageName) {
        File dir = new File(context.getCacheDir(), "logos");
        if (!dir.exists() && !dir.mkdirs()) return null;
        return new File(dir, packageName + ".png");
    }

    private Bitmap readDisk(String packageName) {
        File file = iconFile(packageName);
        if (file == null || !file.exists() || file.length() == 0) return null;
        try (InputStream in = new java.io.FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return decode(out.toByteArray(), 1);
        } catch (Exception e) { return null; }
    }

    private void writeDisk(String packageName, Bitmap bitmap) {
        File file = iconFile(packageName);
        if (file == null) return;
        try (FileOutputStream out = new FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
        catch (Exception ignored) { }
    }
}
