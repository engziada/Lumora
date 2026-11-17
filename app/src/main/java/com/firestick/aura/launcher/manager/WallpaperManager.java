package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import com.firestick.aura.launcher.ui.Frost;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Rotates the live wallpaper catalog (space, night sky, desert) in natural colour, slightly
 * darkened, falling back to generated offline scenes when offline. Every wallpaper also feeds
 * {@link Frost}, the blurred backdrop behind the frosted glass surfaces.
 */
public class WallpaperManager {
    private static final int MAX_WIDTH = 1280, MAX_HEIGHT = 720;
    /** Brightness kept by the online wallpaper grade, so text and glass stay readable. */
    private static final float DIM = 0.78f;

    /** Base, mid and glow colour of each offline scene: cool ink with a soft coloured glow. */
    private static final int[][] SCENES = {
            {0xFF0A0D14, 0xFF1B2440, 0xFF8FB4FF},
            {0xFF080B12, 0xFF16203A, 0xFFDCEAFF},
            {0xFF0B0E18, 0xFF22264A, 0xFFA79BFF},
            {0xFF090D12, 0xFF15303A, 0xFF7FD6E6},
            {0xFF0A0C10, 0xFF20242C, 0xFFC3CAD6},
            {0xFF0C0A16, 0xFF2A1F45, 0xFFF0B8D8}};

    private final Context context;
    private final PreferenceManager preferences;
    private final ImageView back, front;
    private final Frost frost;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Bitmap> retired = new ArrayList<>();
    private final List<String> playlist = new ArrayList<>();
    private String playlistKey = "";
    private int sceneIndex, urlIndex, generation;
    private boolean rotating, destroyed;
    private long fadeToken;
    private Bitmap shown, pending;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            next();
            int seconds = preferences.getWallpaperIntervalSeconds();
            if (rotating && seconds > 0) main.postDelayed(this, seconds * 1000L);
        }
    };

    public WallpaperManager(Context context, PreferenceManager preferences, ImageView back, ImageView front) {
        this.context = context.getApplicationContext();
        this.preferences = preferences;
        this.back = back;
        this.front = front;
        frost = new Frost(back);
        applyLocalScene();
    }

    /** Blurred backdrop of the current wallpaper, for {@link Frost#apply}. */
    public Frost frost() {
        return frost;
    }

    public void start() {
        if (rotating || destroyed) return;
        rotating = true;
        if (preferences.getWallpaperIntervalSeconds() > 0) main.post(tick);
        else next();
    }

    public void stop() {
        rotating = false;
        main.removeCallbacks(tick);
    }

    public void shutdown() {
        if (destroyed) return;
        destroyed = true;
        stop();
        generation++;
        front.animate().cancel();
        executor.shutdownNow();
        recycleRetired();
    }

    public void next() {
        if (destroyed) return;
        int request = ++generation;
        String url = preferences.isOnlineWallpapers() ? nextUrl() : null;
        if (url != null) loadOnlineWallpaper(url, request);
        else applyLocalScene();
    }

    /** Shuffled playlist of the selected categories, rebuilt when the selection changes. */
    private String nextUrl() {
        List<String> urls = WallpaperCatalog.urls(preferences.getWallpaperCategories());
        String key = String.join("|", urls);
        if (!key.equals(playlistKey)) {
            playlistKey = key;
            playlist.clear();
            playlist.addAll(urls);
            Collections.shuffle(playlist);
            urlIndex = 0;
        }
        if (playlist.isEmpty()) return null;
        return playlist.get(urlIndex++ % playlist.size());
    }

    private void applyLocalScene() {
        if (destroyed) return;
        int[] colors = SCENES[Math.floorMod(sceneIndex++, SCENES.length)];
        int request = generation;
        executor.execute(() -> {
            Bitmap bitmap = renderScene(colors);
            if (bitmap == null) return;
            Bitmap blurred = Frost.render(bitmap);
            main.post(() -> {
                if (!destroyed && (request == generation || generation == 0)) crossfadeTo(bitmap, blurred);
                else if (!bitmap.isRecycled()) bitmap.recycle();
            });
        });
    }

    private Bitmap renderScene(int[] colors) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = Math.max(640, Math.min(MAX_WIDTH, metrics.widthPixels));
        int height = Math.max(360, Math.min(MAX_HEIGHT, metrics.heightPixels));
        if (height >= width) height = Math.max(360, Math.min(MAX_HEIGHT, Math.round(width * 9f / 16f)));
        try {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new LinearGradient(0, 0, width, height, colors[0], colors[1], Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(new RadialGradient(width * .70f, height * .78f, Math.max(width, height) * .70f,
                    new int[]{withAlpha(colors[2], 96), withAlpha(colors[2], 0)}, new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(new RadialGradient(width * .18f, height * .16f, Math.max(width, height) * .40f,
                    new int[]{withAlpha(colors[2], 40), withAlpha(colors[2], 0)}, new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            return bitmap;
        } catch (OutOfMemoryError error) {
            return null;
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private void loadOnlineWallpaper(String url, int request) {
        executor.execute(() -> {
            File file = new File(new File(context.getCacheDir(), "wallpapers"), Http.sha1(url) + ".jpg");
            byte[] data = file.exists() && file.length() > 0 ? Http.readFile(file) : null;
            if (data == null) {
                data = Http.get(url, 5000, 12000);
                if (data != null) Http.writeFile(file, data);
            }
            Bitmap bitmap = data == null ? null : decodeNatural(data);
            Bitmap blurred = bitmap == null ? null : Frost.render(bitmap);
            main.post(() -> {
                if (destroyed || request != generation) {
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                } else if (bitmap != null) {
                    crossfadeTo(bitmap, blurred);
                } else {
                    applyLocalScene();
                }
            });
        });
    }

    /**
     * Decodes at no more than 1280 px wide (power-of-two sampling only above that), then darkens
     * the image slightly. Colours stay natural; the glass surfaces carry the theme.
     */
    static Bitmap decodeNatural(byte[] data) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            if (bounds.outWidth <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap source = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (source == null) return null;
            Bitmap graded = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.RGB_565);
            ColorMatrix matrix = new ColorMatrix();
            matrix.setScale(DIM, DIM, DIM, 1f);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            paint.setColorFilter(new ColorMatrixColorFilter(matrix));
            new Canvas(graded).drawBitmap(source, 0f, 0f, paint);
            source.recycle();
            return graded;
        } catch (OutOfMemoryError | RuntimeException error) {
            return null;
        }
    }

    /** {@code blurred} is the matching {@link Frost} backdrop; it may be {@code null}. */
    private void crossfadeTo(Bitmap bitmap, Bitmap blurred) {
        if (destroyed || bitmap == null) return;
        if (pending != null && pending != bitmap) retired.add(pending);
        pending = bitmap;
        long token = ++fadeToken;
        int fade = preferences.reducedMotion() ? 0 : 900;
        frost.publish(blurred, fade);
        front.setImageBitmap(bitmap);
        front.setAlpha(0f);
        front.animate().alpha(1f).setDuration(fade).withEndAction(() -> {
            if (token != fadeToken || destroyed) return;
            back.setImageBitmap(bitmap);
            front.setImageDrawable(null);
            front.setAlpha(0f);
            if (shown != null && shown != bitmap) retired.add(shown);
            shown = bitmap;
            pending = null;
            recycleRetired();
        }).start();
    }

    /** Bitmap pixels live in native memory; recycle every bitmap that leaves the screen. */
    private void recycleRetired() {
        for (Bitmap bitmap : retired) {
            if (bitmap != null && bitmap != shown && bitmap != pending && !bitmap.isRecycled()) bitmap.recycle();
        }
        retired.clear();
    }
}
