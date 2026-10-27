package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HD app icons from the open dashboard-icons collection (homarr-labs, Apache-2.0) via jsDelivr.
 *
 * <p>Resolution order: explicit package mapping, then the collection's name/alias index matched
 * against the app label, then a slug guessed from the label. Fresh downloads (512 px) are
 * downsampled to 128 px and cached; cache reads are decoded at full size. Apps without a match
 * keep their installed icon and are retried after three days.
 */
public final class IconService {
    public interface Callback {
        void onIcon(Bitmap bitmap);
    }

    private static final String CDN = "https://cdn.jsdelivr.net/gh/homarr-labs/dashboard-icons";
    private static final long INDEX_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final long MISS_RETRY_MS = 3L * 24L * 60L * 60L * 1000L;
    private static final int MAX_ATTEMPTS = 5;
    private static final Map<String, String[]> PACKAGE_SLUGS;

    static {
        Map<String, String[]> map = new HashMap<>();
        String[] youtube = {"youtube"};
        String[] prime = {"amazon-prime-video", "prime-video"};
        String[] max = {"max", "hbo-max", "hbo"};
        map.put("com.amazon.firetv.youtube", youtube);
        map.put("com.google.android.youtube", youtube);
        map.put("com.google.android.youtube.tv", youtube);
        map.put("com.google.android.apps.youtube.music", new String[]{"youtube-music"});
        map.put("com.teamsmart.videomanager.tv", new String[]{"smarttube", "youtube"});
        map.put("com.netflix.ninja", new String[]{"netflix"});
        map.put("com.amazon.avod.thirdpartyclient", prime);
        map.put("com.amazon.amazonvideo.livingroom", prime);
        map.put("com.disney.disneyplus", new String[]{"disney-plus", "disneyplus"});
        map.put("com.hulu.plus", new String[]{"hulu"});
        map.put("com.wbd.stream", max);
        map.put("com.hbo.hbonow", max);
        map.put("com.plexapp.android", new String[]{"plex"});
        map.put("org.xbmc.kodi", new String[]{"kodi"});
        map.put("org.jellyfin.androidtv", new String[]{"jellyfin"});
        map.put("com.spotify.tv.android", new String[]{"spotify"});
        map.put("com.crunchyroll.crunchyroid", new String[]{"crunchyroll"});
        map.put("com.stremio.one", new String[]{"stremio"});
        map.put("org.videolan.vlc", new String[]{"vlc"});
        map.put("com.twitch.android.app", new String[]{"twitch"});
        map.put("tv.twitch.android.app", new String[]{"twitch"});
        map.put("com.tubitv.ott", new String[]{"tubi", "tubi-tv"});
        map.put("tv.pluto.android", new String[]{"pluto-tv", "plutotv"});
        map.put("com.apple.atve.androidtv.appletv", new String[]{"apple-tv", "apple-tv-plus"});
        map.put("com.cbs.ott", new String[]{"paramount-plus", "paramount"});
        map.put("com.peacocktv.peacockandroid", new String[]{"peacock"});
        map.put("com.lagradost.cloudstream3", new String[]{"cloudstream"});
        map.put("com.mxtech.videoplayer.ad", new String[]{"mx-player"});
        map.put("com.amazon.venezia", new String[]{"amazon-appstore", "amazon"});
        map.put("com.amazon.bueller.music", new String[]{"amazon-music"});
        map.put("com.amazon.mp3", new String[]{"amazon-music"});
        map.put("cm.aptoidetv.pt", new String[]{"aptoide"});
        map.put("org.mozilla.tv.firefox", new String[]{"firefox"});
        map.put("com.android.chrome", new String[]{"google-chrome", "chrome"});
        PACKAGE_SLUGS = Collections.unmodifiableMap(map);
    }

    private static volatile IconService instance;

    private final File dir;
    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memory = new LruCache<>(96);
    private final Map<String, List<Callback>> waiters = new HashMap<>();
    private final Object indexLock = new Object();
    private Map<String, String> aliasIndex;
    private Map<String, String> lightVariants;

    private IconService(Context context) {
        dir = new File(context.getApplicationContext().getCacheDir(), "logos");
    }

    public static IconService get(Context context) {
        if (instance == null) {
            synchronized (IconService.class) {
                if (instance == null) instance = new IconService(context);
            }
        }
        return instance;
    }

    /** Delivers a cached icon immediately, or resolves and downloads it in the background. */
    public void load(String packageName, String label, Callback callback) {
        Bitmap cached = memory.get(packageName);
        if (cached != null && !cached.isRecycled()) {
            callback.onIcon(cached);
            return;
        }
        synchronized (waiters) {
            List<Callback> queue = waiters.get(packageName);
            if (queue != null) {
                queue.add(callback);
                return;
            }
            queue = new ArrayList<>();
            queue.add(callback);
            waiters.put(packageName, queue);
        }
        io.execute(() -> {
            Bitmap bitmap = readDisk(packageName);
            if (bitmap == null && !recentlyMissed(packageName)) {
                int attempts = 0;
                for (String slug : candidates(packageName, label)) {
                    if (attempts++ >= MAX_ATTEMPTS) break;
                    bitmap = download(slug);
                    if (bitmap != null) {
                        writeDisk(packageName, bitmap);
                        break;
                    }
                }
                if (bitmap == null) markMiss(packageName);
            }
            List<Callback> queue;
            synchronized (waiters) {
                queue = waiters.remove(packageName);
            }
            if (bitmap == null || queue == null) return;
            memory.put(packageName, bitmap);
            Bitmap result = bitmap;
            main.post(() -> {
                for (Callback waiter : queue) waiter.onIcon(result);
            });
        });
    }

    private List<String> candidates(String packageName, String label) {
        Set<String> slugs = new LinkedHashSet<>();
        String[] explicit = PACKAGE_SLUGS.get(packageName);
        if (explicit != null) Collections.addAll(slugs, explicit);
        Map<String, String> index = index();
        for (String key : labelKeys(label)) {
            String name = index.get(key);
            if (name != null) slugs.add(name);
        }
        String guess = kebab(label);
        if (!guess.isEmpty()) slugs.add(guess);

        // Prefer the "-light" variant: it is drawn for dark backgrounds such as the icon plate.
        List<String> ordered = new ArrayList<>();
        Map<String, String> light = lightVariants == null ? Collections.emptyMap() : lightVariants;
        for (String slug : slugs) {
            String variant = light.get(slug);
            if (variant != null && !ordered.contains(variant)) ordered.add(variant);
            if (!ordered.contains(slug)) ordered.add(slug);
        }
        return ordered;
    }

    private static List<String> labelKeys(String label) {
        List<String> keys = new ArrayList<>();
        if (label == null) return keys;
        String full = kebab(label);
        String trimmed = kebab(label.replaceAll("(?i)\\b(for|android|tv|app|the)\\b", " "));
        for (String key : new String[]{full, trimmed}) {
            if (key.isEmpty()) continue;
            if (!keys.contains(key)) keys.add(key);
            String compact = key.replace("-", "");
            if (!keys.contains(compact)) keys.add(compact);
        }
        return keys;
    }

    static String kebab(String value) {
        if (value == null) return "";
        String lower = value.toLowerCase(Locale.ROOT).replace("+", " plus ").replace("&", " and ");
        return lower.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    /** Name/alias index built from the collection's metadata.json, refreshed weekly. */
    private Map<String, String> index() {
        synchronized (indexLock) {
            if (aliasIndex != null) return aliasIndex;
            File file = new File(dir, "dashboard-metadata.json");
            byte[] data = null;
            if (file.exists() && System.currentTimeMillis() - file.lastModified() < INDEX_MAX_AGE_MS) {
                data = Http.readFile(file);
            }
            if (data == null) {
                data = Http.get(CDN + "/metadata.json", 6000, 20000);
                if (data != null) Http.writeFile(file, data);
                else if (file.exists()) data = Http.readFile(file);
            }
            Map<String, String> aliases = new HashMap<>();
            Map<String, String> light = new HashMap<>();
            if (data != null) {
                try {
                    JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
                    Iterator<String> names = root.keys();
                    while (names.hasNext()) {
                        String name = names.next();
                        JSONObject entry = root.optJSONObject(name);
                        aliases.put(name, name);
                        aliases.put(name.replace("-", ""), name);
                        if (entry == null) continue;
                        JSONArray list = entry.optJSONArray("aliases");
                        if (list != null) {
                            for (int i = 0; i < list.length(); i++) {
                                String alias = kebab(list.optString(i));
                                if (alias.isEmpty()) continue;
                                aliases.putIfAbsent(alias, name);
                                aliases.putIfAbsent(alias.replace("-", ""), name);
                            }
                        }
                        JSONObject colors = entry.optJSONObject("colors");
                        if (colors != null && !colors.isNull("light")) {
                            String variant = colors.optString("light", "");
                            if (!variant.isEmpty()) light.put(name, variant);
                        }
                    }
                } catch (Exception ignored) {
                    aliases.clear();
                    light.clear();
                }
            }
            lightVariants = light;
            // Keep a failed (empty) index for this process only; the next launch retries.
            aliasIndex = aliases;
            return aliasIndex;
        }
    }

    private Bitmap download(String slug) {
        byte[] data = Http.get(CDN + "/png/" + slug + ".png", 4000, 8000);
        // Fresh 512 px download: sample to 128 px, which is plenty for a 42 dp tile.
        return data == null ? null : decode(data, 4);
    }

    private static Bitmap decode(byte[] data, int sampleSize) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (bitmap == null) return null;
            if (bitmap.getWidth() < 32 || bitmap.getHeight() < 32) {
                bitmap.recycle();
                return null;
            }
            return bitmap;
        } catch (Exception | OutOfMemoryError error) {
            return null;
        }
    }

    private Bitmap readDisk(String packageName) {
        File file = new File(dir, packageName + ".png");
        if (!file.exists() || file.length() == 0) return null;
        byte[] data = Http.readFile(file);
        // Cached files are already 128 px; decode them at full size.
        return data == null ? null : decode(data, 1);
    }

    private void writeDisk(String packageName, Bitmap bitmap) {
        if (!dir.exists() && !dir.mkdirs()) return;
        try (FileOutputStream out = new FileOutputStream(new File(dir, packageName + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception ignored) {
        }
    }

    private boolean recentlyMissed(String packageName) {
        File marker = new File(dir, packageName + ".miss");
        return marker.exists() && System.currentTimeMillis() - marker.lastModified() < MISS_RETRY_MS;
    }

    private void markMiss(String packageName) {
        Http.writeFile(new File(dir, packageName + ".miss"), new byte[]{1});
    }
}
