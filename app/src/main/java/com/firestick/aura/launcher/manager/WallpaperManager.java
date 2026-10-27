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

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Rotates generated offline scenes and optional 1280 x 720 online stills. */
public class WallpaperManager {
    private static final int MAX_WIDTH = 1280;
    private static final int MAX_HEIGHT = 720;
    private static final List<String> ONLINE_URLS = Arrays.asList(
            "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1280&h=720&fit=crop&q=80",
            "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1280&h=720&fit=crop&q=80",
            "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=1280&h=720&fit=crop&q=80",
            "https://images.unsplash.com/photo-1550684848-fac1c5b4e853?w=1280&h=720&fit=crop&q=80",
            "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=1280&h=720&fit=crop&q=80",
            "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?w=1280&h=720&fit=crop&q=80"
    );
    private static final int[][] SCENES = {
            {0xFF050810, 0xFF12233D, 0xFF00E5FF}, {0xFF0A0306, 0xFF3A0A16, 0xFFFF1E46},
            {0xFF03090A, 0xFF0E2C24, 0xFF00E676}, {0xFF08040E, 0xFF241340, 0xFFB388FF},
            {0xFF0C0603, 0xFF3A1D08, 0xFFFF8A3D}, {0xFF06070B, 0xFF1A1F2E, 0xFFF8FAFC}
    };

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
        @Override public void run() {
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
        if (context instanceof LifecycleOwner) {
            ((LifecycleOwner) context).getLifecycle().addObserver((source, event) -> {
                if (event == Lifecycle.Event.ON_DESTROY) shutdown();
            });
        }
        applyLocalScene();
    }

    public void start() {
        if (rotating || destroyed) return;
        rotating = true;
        if (preferences.getWallpaperIntervalSeconds() > 0) main.post(tick); else next();
    }

    public void stop() { rotating = false; main.removeCallbacks(tick); }

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
        if (preferences.isOnlineWallpapers()) loadOnlineWallpaper(request); else applyLocalScene();
    }

    private void applyLocalScene() {
        if (destroyed) return;
        int[] colors = SCENES[Math.floorMod(sceneIndex++, SCENES.length)];
        int request = generation;
        executor.execute(() -> {
            Bitmap bitmap = renderScene(colors);
            if (bitmap != null) main.post(() -> {
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
            paint.setShader(new RadialGradient(width * .74f, height * .22f, Math.max(width, height) * .72f,
                    new int[]{withAlpha(colors[2], 92), withAlpha(colors[2], 0)}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            paint.setShader(new LinearGradient(0, 0, 0, height, new int[]{0xB3000000, 0, 0, 0xA6000000},
                    new float[]{0f, .22f, .62f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, height, paint);
            return bitmap;
        } catch (OutOfMemoryError error) { return null; }
    }

    private static int withAlpha(int color, int alpha) { return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24); }

    private void loadOnlineWallpaper(int request) {
        String url = ONLINE_URLS.get(urlIndex++ % ONLINE_URLS.size());
        executor.execute(() -> {
            Bitmap bitmap = decodeCached(url);
            if (bitmap == null) {
                byte[] data = download(url);
                if (data != null) {
                    bitmap = decode(data);
                    if (bitmap != null) writeCache(url, data);
                }
            }
            Bitmap result = bitmap;
            main.post(() -> {
                if (destroyed || request != generation) {
                    if (result != null && !result.isRecycled()) result.recycle();
                } else if (result != null) crossfadeTo(result);
                else applyLocalScene();
            });
        });
    }

    private byte[] download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(4000); connection.setReadTimeout(6000); connection.setInstanceFollowRedirects(true); connection.connect();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16384]; int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                return out.toByteArray();
            }
        } catch (Exception exception) { return null; }
        finally { if (connection != null) connection.disconnect(); }
    }

    /** Unsplash already returns the requested target dimensions; decode at full response size. */
    private Bitmap decode(byte[] data) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (OutOfMemoryError | Exception exception) { return null; }
    }

    private File cacheFile(String url) {
        File directory = new File(context.getCacheDir(), "wallpapers");
        if (!directory.exists() && !directory.mkdirs()) return null;
        return new File(directory, cacheKey(url) + ".jpg");
    }

    private String cacheKey(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte item : digest) value.append(String.format("%02x", item));
            return value.toString();
        } catch (Exception exception) { return Integer.toHexString(url.hashCode()); }
    }

    private Bitmap decodeCached(String url) {
        File file = cacheFile(url);
        if (file == null || !file.exists() || file.length() == 0) return null;
        byte[] data = readFile(file);
        return data == null ? null : decode(data);
    }

    private void writeCache(String url, byte[] data) {
        File file = cacheFile(url);
        if (file == null) return;
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(data); }
        catch (Exception ignored) { }
    }

    private byte[] readFile(File file) {
        try (InputStream in = new java.io.FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384]; int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        } catch (Exception exception) { return null; }
    }

    private void crossfadeTo(Bitmap bitmap) {
        if (destroyed || bitmap == null) return;
        if (pending != null && pending != bitmap) retired.add(pending);
        pending = bitmap;
        long token = ++fadeToken;
        front.setImageBitmap(bitmap); front.setAlpha(0f);
        front.animate().alpha(1f).setDuration(700).withEndAction(() -> {
            if (token != fadeToken || destroyed) return;
            back.setImageBitmap(bitmap); front.setImageDrawable(null); front.setAlpha(0f);
            if (shown != null && shown != bitmap) retired.add(shown);
            shown = bitmap; pending = null; recycleRetired();
        }).start();
    }

    private void recycleRetired() {
        for (Bitmap bitmap : retired)
            if (bitmap != null && bitmap != shown && bitmap != pending && !bitmap.isRecycled()) bitmap.recycle();
        retired.clear();
    }
}
