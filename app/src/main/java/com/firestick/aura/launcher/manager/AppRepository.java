package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.model.AppCategory;
import com.firestick.aura.launcher.model.AppModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppRepository {
    public interface CategoriesCallback { void onLoaded(List<AppCategory> categories); }

    private final Context context;
    private final PackageManager pm;
    private final PreferenceManager preferences;
    private final Map<String, AppModel> appCache = new HashMap<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<AppCategory> categoriesCache;

    public AppRepository(Context context) {
        this.context = context.getApplicationContext();
        pm = context.getPackageManager();
        preferences = new PreferenceManager(context);
    }

    public void loadCategoriesAsync(CategoriesCallback callback) {
        loader.execute(() -> {
            List<AppCategory> result;
            synchronized (this) {
                if (categoriesCache == null) categoriesCache = buildCategories();
                result = categoriesCache;
            }
            main.post(() -> callback.onLoaded(result));
        });
    }

    public synchronized void invalidate() { categoriesCache = null; }

    /** Invalidates only the package named by a package broadcast; other decoded icons stay cached. */
    public synchronized void invalidatePackage(String packageName) {
        categoriesCache = null;
        if (packageName != null) appCache.remove(packageName);
    }

    public void shutdown() { loader.shutdownNow(); }

    private List<AppCategory> buildCategories() {
        Map<String, AppModel> appMap = new HashMap<>();
        query(Intent.CATEGORY_LEANBACK_LAUNCHER, appMap);
        query(Intent.CATEGORY_LAUNCHER, appMap);
        appMap.remove(context.getPackageName());
        appCache.keySet().retainAll(appMap.keySet());

        List<AppModel> all = new ArrayList<>(appMap.values());
        Collections.sort(all, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        List<AppModel> favorites = new ArrayList<>(), streaming = new ArrayList<>(), tools = new ArrayList<>(), visible = new ArrayList<>();
        for (AppModel app : all) {
            app.setHidden(preferences.isHidden(app.getPackageName()));
            if (app.isHidden()) continue;
            app.setFavorite(preferences.isFavorite(app.getPackageName()));
            if (app.isFavorite()) favorites.add(app);
            visible.add(app);
            String pkg = app.getPackageName().toLowerCase(Locale.ROOT), name = app.getLabel().toLowerCase(Locale.ROOT);
            if (isStreamingApp(pkg, name)) streaming.add(app); else if (isToolApp(pkg, name)) tools.add(app);
        }
        sortApps("favorites", favorites); sortApps("streaming", streaming); sortApps("all", visible); sortApps("tools", tools);
        int accent = preferences.getAccentColor(ContextCompat.getColor(context, R.color.nova_accent));
        List<AppCategory> categories = new ArrayList<>();
        addCategory(categories, "favorites", R.string.category_favorites, accent, favorites);
        addCategory(categories, "streaming", R.string.category_streaming, ContextCompat.getColor(context, R.color.accent_fire), streaming);
        addCategory(categories, "all", R.string.category_all_apps, ContextCompat.getColor(context, R.color.accent_emerald), visible);
        addCategory(categories, "tools", R.string.category_tools, ContextCompat.getColor(context, R.color.accent_silver), tools);
        return categories;
    }

    private void query(String category, Map<String, AppModel> apps) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) addResolveInfo(info, apps);
    }

    private void addCategory(List<AppCategory> output, String id, int title, int color, List<AppModel> apps) {
        if (apps.isEmpty()) return;
        AppCategory category = new AppCategory(id, context.getString(title), color); category.setApps(apps); output.add(category);
    }

    private void sortApps(String rowId, List<AppModel> apps) {
        List<String> order = preferences.getCustomOrder(rowId); Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < order.size(); i++) ranks.put(order.get(i), i);
        Collections.sort(apps, (a, b) -> {
            Integer ar = ranks.get(a.getPackageName()), br = ranks.get(b.getPackageName());
            if (ar != null && br != null) return Integer.compare(ar, br);
            if (ar != null) return -1; if (br != null) return 1;
            return a.getLabel().compareToIgnoreCase(b.getLabel());
        });
    }

    private void addResolveInfo(ResolveInfo info, Map<String, AppModel> apps) {
        if (info.activityInfo == null || info.activityInfo.packageName == null) return;
        String pkg = info.activityInfo.packageName; if (apps.containsKey(pkg)) return;
        AppModel cached = appCache.get(pkg); if (cached != null) { apps.put(pkg, cached); return; }
        String label; Drawable icon;
        try { label = info.loadLabel(pm).toString(); } catch (Exception e) { label = pkg; }
        try { icon = info.loadIcon(pm); } catch (Exception | OutOfMemoryError e) { icon = pm.getDefaultActivityIcon(); }
        AppModel model = new AppModel(pkg, info.activityInfo.name, label, icon); appCache.put(pkg, model); apps.put(pkg, model);
    }

    private boolean isStreamingApp(String pkg, String name) {
        return pkg.contains("stremio") || pkg.contains("netflix") || pkg.contains("primevideo") || pkg.contains("youtube")
                || pkg.contains("smarttube") || pkg.contains("disney") || pkg.contains("hbo") || pkg.contains("max")
                || pkg.contains("kodi") || pkg.contains("plex") || pkg.contains("hulu") || pkg.contains("twitch")
                || pkg.contains("crunchyroll") || pkg.contains("vlc") || pkg.contains("mxtech")
                || name.contains("tv") || name.contains("movie") || name.contains("stream");
    }

    private boolean isToolApp(String pkg, String name) {
        return pkg.contains("downloader") || pkg.contains("settings") || pkg.contains("explorer") || pkg.contains("browser")
                || pkg.contains("cleanup") || pkg.contains("speedtest") || pkg.contains("file") || pkg.contains("tool")
                || name.contains("tool") || name.contains("clean") || name.contains("setting");
    }
}
