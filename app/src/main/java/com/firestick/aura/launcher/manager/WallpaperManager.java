package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Rotates warm offline scenes and optional 1280 x 720 online stills. */
public class WallpaperManager {
    private static final int MAX_WIDTH = 1280, MAX_HEIGHT = 720;
    private static final List<String> ONLINE_URLS = Arrays.asList(
            "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1280&h=720&fit=crop&q=80",
            "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1280&h=720&fit=crop&q=80",
            "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=1280&h=720&fit=crop&q=80",
            "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=1280&h=720&fit=crop&q=80",
            "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=1280&h=720&fit=crop&q=80",
            "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=1280&h=720&fit=crop&q=80");

    /** Base, mid and glow colour of each offline scene, from the Lumora palette. */
    private static final int[][] SCENES = {
            {0xFF140A06, 0xFF3A2216, 0xFFE3A15A},
            {0xFF0E0806, 0xFF2A1A12, 0xFFF2D3A0},
            {0xFF160904, 0xFF4A2410, 0xFFD07A45},
            {0xFF120808, 0xFF3A1E1C, 0xFFD98A7E},
            {0xFF0C0806, 0xFF26201A, 0xFFC9B39C},
            {0xFF100A08, 0xFF33200F, 0xFFEDB06A}};

    private final Context context;
    private final PreferenceManager preferences;
    private final ImageView back, front;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Bitmap> retired = new ArrayList<>();
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
        applyLocalScene();
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
        if (preferences.isOnlineWallpapers()) loadOnlineWallpaper(request);
        else applyLocalScene();
    }

    private void applyLocalScene() {
        if (destroyed) return;
        int[] colors = SCENES[Math.floorMod(sceneIndex++, SCENES.length)];
        int request = generation;
        executor.execute(() -> {
            Bitmap bitmap = renderScene(colors);
            if (bitmap == null) return;
            main.post(() -> {
                if (!destroyed && (request == generation || generation == 0)) crossfadeTo(bitmap);
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
            // Horizon glow, lower right, like light behind the dunes of the logo.
            paint.setShader(new RadialGradient(width * .70f, height * .78f, Math.max(width, height) * .70f,
                    new int[]{withAlpha(colors[2], 96), withAlpha(colors[2], 0)}, new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            // Soft moon glow, upper left.
            paint.setShader(new RadialGradient(width * .18f, height * .16f, Math.max(width, height) * .40f,
                    new int[]{withAlpha(colors[2], 40), withAlpha(colors[2], 0)}, new float[]{0f, 1f},
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(new LinearGradient(0, 0, 0, height,
                    new int[]{0xB3140A06, 0x00140A06, 0x00140A06, 0xA6140A06}, new float[]{0f, .22f, .62f, 1f},
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

    private void loadOnlineWallpaper(int request) {
        String url = ONLINE_URLS.get(urlIndex++ % ONLINE_URLS.size());
        executor.execute(() -> {
            File file = new File(new File(context.getCacheDir(), "wallpapers"), Http.sha1(url) + ".jpg");
            byte[] data = file.exists() && file.length() > 0 ? Http.readFile(file) : null;
            if (data == null) {
                data = Http.get(url, 4000, 8000);
                if (data != null) Http.writeFile(file, data);
            }
            Bitmap bitmap = data == null ? null : decode(data);
            main.post(() -> {
                if (destroyed || request != generation) {
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                } else if (bitmap != null) {
                    crossfadeTo(bitmap);
                } else {
                    applyLocalScene();
                }
            });
        });
    }

    /** Unsplash already returns the requested 1280 x 720 crop; decode at full response size. */
    private static Bitmap decode(byte[] data) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (OutOfMemoryError | Exception exception) {
            return null;
        }
    }

    private void crossfadeTo(Bitmap bitmap) {
        if (destroyed || bitmap == null) return;
        if (pending != null && pending != bitmap) retired.add(pending);
        pending = bitmap;
        long token = ++fadeToken;
        front.setImageBitmap(bitmap);
        front.setAlpha(0f);
        front.animate().alpha(1f).setDuration(700).withEndAction(() -> {
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
