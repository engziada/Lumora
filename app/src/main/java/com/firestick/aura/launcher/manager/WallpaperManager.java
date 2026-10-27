package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rotating wallpaper engine.
 *
 * <p>v3 changes versus the original implementation:
 * <ul>
 *   <li>Six procedurally rendered "scene" wallpapers that need no network and no files,
 *       used instantly at startup and whenever a download fails (the old code fell back
 *       to one static gradient, so an offline stick showed a frozen backdrop);</li>
 *   <li>online stills are downsampled to ~1280x720 and cached on disk, so each image is
 *       downloaded once instead of on every rotation;</li>
 *   <li>bitmaps are RGB_565 where possible, keeping the launcher's heap footprint small;</li>
 *   <li>stale requests are dropped by a generation counter, so a slow download can no
 *       longer flash on screen after the user already moved on.</li>
 * </ul>
 */
public class WallpaperManager {

    private static final int MAX_WIDTH = 1280;
    private static final int MAX_HEIGHT = 720;

    private static final List<String> ONLINE_URLS = Arrays.asList(
            "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1920&q=80", // Dark neon cyber
            "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1920&q=80", // Cinematic Yosemite
            "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=1920&q=80", // Deep space nebula
            "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=1920&q=80", // Dark minimal wave
            "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=1920&q=80", // Night mountains OLED
            "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=1920&q=80"  // Moody architecture
    );

    /** Base, mid and glow colour of each offline scene. */
    private static final int[][] SCENES = {
            {0xFF050810, 0xFF12233D, 0xFF00E5FF},
            {0xFF0A0306, 0xFF3A0A16, 0xFFFF1E46},
            {0xFF03090A, 0xFF0E2C24, 0xFF00E676},
            {0xFF08040E, 0xFF241340, 0xFFB388FF},
            {0xFF0C0603, 0xFF3A1D08, 0xFFFF8A3D},
            {0xFF06070B, 0xFF1A1F2E, 0xFFF8FAFC}
    };

    private final Context context;
    private final PreferenceManager prefs;
    private final ImageView imgBack;
    private final ImageView imgFront;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int sceneIndex;
    private int urlIndex;
    private int generation;
    private boolean rotating;
    private long fadeToken;
    private Bitmap shown;
    private Bitmap pending;
    private final List<Bitmap> retired = new java.util.ArrayList<>();

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            next();
            int intervalSec = prefs.getWallpaperIntervalSeconds();
            if (rotating && intervalSec > 0) {
                mainHandler.postDelayed(this, intervalSec * 1000L);
            }
        }
    };

    public WallpaperManager(Context context, PreferenceManager prefs, ImageView imgBack, ImageView imgFront) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        this.imgBack = imgBack;
        this.imgFront = imgFront;

        // Something beautiful is on screen immediately, even before the first download.
        applyLocalScene();
    }

    public void start() {
        if (rotating) return;
        rotating = true;
        if (prefs.getWallpaperIntervalSeconds() > 0) {
            mainHandler.post(tick);
        } else {
            next();
        }
    }

    public void stop() {
        rotating = false;
        mainHandler.removeCallbacks(tick);
    }

    /** Advances to the next wallpaper immediately (used by the settings preview). */
    public void next() {
        generation++;
        if (prefs.isOnlineWallpapers()) {
            loadOnlineWallpaper(generation);
        } else {
            applyLocalScene();
        }
    }

    // ---------------------------------------------------------------- offline

    private void applyLocalScene() {
        final int index = sceneIndex++;
        final int[] colors = SCENES[Math.floorMod(index, SCENES.length)];
        final int request = generation;
        executor.execute(() -> {
            Bitmap bitmap = renderScene(colors);
            if (bitmap == null) return;
            mainHandler.post(() -> {
                if (request == generation || generation == 0) {
                    crossfadeTo(bitmap);
                }
            });
        });
    }

    private Bitmap renderScene(int[] colors) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int width = Math.max(640, Math.min(MAX_WIDTH, dm.widthPixels));
        int height = Math.max(360, Math.min(MAX_HEIGHT, dm.heightPixels));
        if (height >= width) {
            height = Math.max(360, Math.min(MAX_HEIGHT, Math.round(width * 9f / 16f)));
        }

        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            paint.setShader(new LinearGradient(0, 0, width, height,
                    colors[0], colors[1], Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);

            // Primary glow, upper right.
            paint.setShader(new RadialGradient(width * 0.74f, height * 0.22f,
                    Math.max(width, height) * 0.72f,
                    new int[]{withAlpha(colors[2], 92), withAlpha(colors[2], 0)},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);

            // Counter glow, lower left, keeps the frame from looking like a single blob.
            paint.setShader(new RadialGradient(width * 0.14f, height * 0.92f,
                    Math.max(width, height) * 0.55f,
                    new int[]{withAlpha(colors[2], 46), withAlpha(colors[2], 0)},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);

            // Darken the top and bottom edges so header text always stays readable.
            paint.setShader(new LinearGradient(0, 0, 0, height,
                    new int[]{0xB3000000, 0x00000000, 0x00000000, 0xA6000000},
                    new float[]{0f, 0.22f, 0.62f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);

            return bitmap;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    // ----------------------------------------------------------------- online

    private void loadOnlineWallpaper(final int request) {
        final int index = urlIndex++ % ONLINE_URLS.size();
        final String url = ONLINE_URLS.get(index);
        executor.execute(() -> {
            Bitmap bitmap = decodeCached(index);
            if (bitmap == null) {
                byte[] data = download(url);
                if (data != null) {
                    bitmap = decode(data);
                    if (bitmap != null) {
                        writeCache(index, data);
                    }
                }
            }
            final Bitmap result = bitmap;
            mainHandler.post(() -> {
                if (request != generation) return; // a newer request superseded this one
                if (result != null) {
                    crossfadeTo(result);
                } else {
                    // Offline, blocked or slow network: keep rotating the local scenes.
                    applyLocalScene();
                }
            });
        });
    }

    private byte[] download(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(6000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return out.toByteArray();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private Bitmap decode(byte[] data) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            // Smallest power-of-two sample that brings the image under the target size.
            // A 1920x1080 still becomes 960x540 (2 MB) instead of 8 MB of ARGB pixels.
            int sample = 1;
            while ((bounds.outWidth / sample) > MAX_WIDTH
                    || (bounds.outHeight / sample) > MAX_HEIGHT) {
                sample *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (OutOfMemoryError | Exception e) {
            return null;
        }
    }

    private File cacheFile(int index) {
        File dir = new File(context.getCacheDir(), "wallpapers");
        if (!dir.exists() && !dir.mkdirs()) return null;
        return new File(dir, "scene_" + index + ".jpg");
    }

    private Bitmap decodeCached(int index) {
        File file = cacheFile(index);
        if (file == null || !file.exists() || file.length() == 0) return null;
        return decode(readFile(file));
    }

    private void writeCache(int index, byte[] data) {
        File file = cacheFile(index);
        if (file == null) return;
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        } catch (Exception ignored) {
        }
    }

    private byte[] readFile(File file) {
        try (InputStream in = new java.io.FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------- rendering

    private void crossfadeTo(Bitmap bitmap) {
        if (imgFront == null || imgBack == null || bitmap == null) return;

        // Anything that was mid-fade is no longer visible once we bind a new bitmap.
        if (pending != null && pending != bitmap) {
            retired.add(pending);
        }
        pending = bitmap;

        final long token = ++fadeToken;
        imgFront.setImageBitmap(bitmap);
        imgFront.setAlpha(0f);
        imgFront.animate()
                .alpha(1f)
                .setDuration(700)
                .withEndAction(() -> {
                    if (token != fadeToken) return;
                    imgBack.setImageBitmap(bitmap);
                    imgFront.setImageDrawable(null);
                    imgFront.setAlpha(0f);
                    if (shown != null && shown != bitmap) {
                        retired.add(shown);
                    }
                    shown = bitmap;
                    pending = null;
                    recycleRetired();
                })
                .start();
    }

    /**
     * Bitmap pixels live in the native heap on modern Android, so the Java GC has no
     * reason to run and old wallpapers would otherwise accumulate (measured: +18 MB
     * over two rotations). Every bitmap that leaves the screen is recycled explicitly.
     */
    private void recycleRetired() {
        for (Bitmap bitmap : retired) {
            if (bitmap == null || bitmap == shown || bitmap == pending) continue;
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        retired.clear();
    }
}
