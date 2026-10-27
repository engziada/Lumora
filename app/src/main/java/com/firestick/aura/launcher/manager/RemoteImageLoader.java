package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads poster images: memory cache, then disk cache, then network. Bitmaps are decoded as RGB_565
 * and sampled down to the requested width, because posters are opaque and shown at about 216 px.
 */
public final class RemoteImageLoader {
    public interface Callback {
        void onLoaded(Bitmap bitmap);
    }

    private static final long DISK_MAX_AGE_MS = 14L * 24L * 60L * 60L * 1000L;
    private static volatile RemoteImageLoader instance;

    private final File dir;
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memory;
    private final Map<String, List<Callback>> waiting = new HashMap<>();

    private RemoteImageLoader(Context context) {
        dir = new File(context.getApplicationContext().getCacheDir(), "images");
        int budgetKb = (int) (Runtime.getRuntime().maxMemory() / 1024L / 16L);
        memory = new LruCache<String, Bitmap>(budgetKb) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return Math.max(1, value.getByteCount() / 1024);
            }
        };
        io.execute(this::pruneDisk);
    }

    public static RemoteImageLoader get(Context context) {
        if (instance == null) {
            synchronized (RemoteImageLoader.class) {
                if (instance == null) instance = new RemoteImageLoader(context);
            }
        }
        return instance;
    }

    public void load(String url, int targetWidthPx, Callback callback) {
        if (url == null || url.isEmpty()) return;
        String key = url + '@' + targetWidthPx;
        Bitmap hit = memory.get(key);
        if (hit != null && !hit.isRecycled()) {
            callback.onLoaded(hit);
            return;
        }
        synchronized (waiting) {
            List<Callback> queue = waiting.get(key);
            if (queue != null) {
                queue.add(callback);
                return;
            }
            queue = new ArrayList<>();
            queue.add(callback);
            waiting.put(key, queue);
        }
        io.execute(() -> {
            Bitmap bitmap = null;
            try {
                byte[] data = readOrDownload(url);
                if (data != null) bitmap = decode(data, targetWidthPx);
            } catch (OutOfMemoryError ignored) {
                memory.evictAll();
            }
            if (bitmap != null) memory.put(key, bitmap);
            List<Callback> queue;
            synchronized (waiting) {
                queue = waiting.remove(key);
            }
            Bitmap result = bitmap;
            if (result != null && queue != null) {
                main.post(() -> {
                    for (Callback waiter : queue) waiter.onLoaded(result);
                });
            }
        });
    }

    private byte[] readOrDownload(String url) {
        File file = new File(dir, Http.sha1(url));
        if (file.exists() && file.length() > 0) {
            byte[] cached = Http.readFile(file);
            if (cached != null) return cached;
        }
        byte[] data = Http.get(url, 5000, 10000);
        if (data != null) Http.writeFile(file, data);
        return data;
    }

    private static Bitmap decode(byte[] data, int targetWidthPx) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0) return null;
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private void pruneDisk() {
        File[] files = dir.listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - DISK_MAX_AGE_MS;
        for (File file : files) {
            if (file.lastModified() < cutoff) file.delete();
        }
    }
}
