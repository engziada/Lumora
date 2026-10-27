package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Builds three rows: Home (pinned), Streaming and Apps. User overrides beat the defaults. */
public class AppRepository {
    public interface CategoriesCallback {
        void onLoaded(List<AppCategory> categories);
    }

    public static final String ROW_HOME = "favorites";
    public static final String ROW_STREAMING = "streaming";
    public static final String ROW_APPS = "apps";

    private static final Set<String> MEDIA_PACKAGES = new HashSet<>(Arrays.asList(
            "com.amazon.firetv.youtube", "com.google.android.youtube", "com.google.android.youtube.tv",
            "com.netflix.ninja", "com.amazon.avod.thirdpartyclient", "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus", "com.hulu.plus", "com.hbo.hbonow", "com.wbd.stream", "com.plexapp.android",
            "org.xbmc.kodi", "org.jellyfin.androidtv", "com.spotify.tv.android", "com.crunchyroll.crunchyroid",
            "com.stremio.one", "org.videolan.vlc", "com.twitch.android.app", "tv.twitch.android.app", "com.tubitv.ott",
            "tv.pluto.android", "com.apple.atve.androidtv.appletv", "com.cbs.ott", "com.peacocktv.peacockandroid",
            "com.lagradost.cloudstream3", "com.mxtech.videoplayer.ad"));

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

    public synchronized void invalidate() {
        categoriesCache = null;
    }

    public synchronized void invalidatePackage(String packageName) {
        categoriesCache = null;
        if (packageName != null) appCache.remove(packageName);
    }

    public void shutdown() {
        loader.shutdownNow();
    }

    private List<AppCategory> buildCategories() {
        Map<String, AppModel> appMap = new HashMap<>();
        query(Intent.CATEGORY_LEANBACK_LAUNCHER, appMap);
        query(Intent.CATEGORY_LAUNCHER, appMap);
        appMap.remove(context.getPackageName());
        appCache.keySet().retainAll(appMap.keySet());
        List<AppModel> all = new ArrayList<>(appMap.values());
        Collections.sort(all, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));

        Set<String> hidden = preferences.getHiddenApps();
        Set<String> favorites = preferences.getFavorites();
        Set<String> forcedStreaming = preferences.getForcedStreaming();
        Set<String> forcedApps = preferences.getForcedApps();
        List<AppModel> home = new ArrayList<>(), streaming = new ArrayList<>(), apps = new ArrayList<>();
        for (AppModel app : all) {
            String pkg = app.getPackageName();
            app.setHidden(hidden.contains(pkg));
            if (app.isHidden()) continue;
            app.setFavorite(favorites.contains(pkg));
            if (app.isFavorite()) home.add(app);
            boolean media = forcedStreaming.contains(pkg) || (!forcedApps.contains(pkg) && isMediaApp(pkg));
            (media ? streaming : apps).add(app);
        }
        sortApps(ROW_HOME, home);
        sortApps(ROW_STREAMING, streaming);
        sortApps(ROW_APPS, apps);
        int accent = preferences.getAccentColor(ContextCompat.getColor(context, R.color.nova_accent));
        List<AppCategory> categories = new ArrayList<>();
        categories.add(category(ROW_HOME, R.string.category_favorites, accent, home));
        categories.add(category(ROW_STREAMING, R.string.category_streaming, ContextCompat.getColor(context, R.color.accent_fire), streaming));
        categories.add(category(ROW_APPS, R.string.category_all_apps, ContextCompat.getColor(context, R.color.accent_emerald), apps));
        return categories;
    }

    private boolean isMediaApp(String packageName) {
        try {
            int category = pm.getApplicationInfo(packageName, 0).category;
            if (category == ApplicationInfo.CATEGORY_VIDEO || category == ApplicationInfo.CATEGORY_AUDIO) return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            // Removed while the list was built.
        }
        return MEDIA_PACKAGES.contains(packageName);
    }

    private void query(String category, Map<String, AppModel> apps) {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) addResolveInfo(info, apps);
    }

    private AppCategory category(String id, int title, int color, List<AppModel> apps) {
        AppCategory category = new AppCategory(id, context.getString(title), color);
        category.setApps(apps);
        return category;
    }

    private void sortApps(String rowId, List<AppModel> apps) {
        List<String> order = preferences.getCustomOrder(rowId);
        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < order.size(); i++) ranks.put(order.get(i), i);
        Collections.sort(apps, (a, b) -> {
            Integer ar = ranks.get(a.getPackageName()), br = ranks.get(b.getPackageName());
            if (ar != null && br != null) return Integer.compare(ar, br);
            if (ar != null) return -1;
            if (br != null) return 1;
            return a.getLabel().compareToIgnoreCase(b.getLabel());
        });
    }

    /**
     * Skips entries Lumora cannot start: disabled, not exported, or guarded by a permission we do
     * not hold (for example Amazon's Alexa settings, which require API_COMMUNICATION).
     */
    private boolean isLaunchable(ActivityInfo activity) {
        if (!activity.exported || !activity.enabled) return false;
        if (activity.applicationInfo != null && !activity.applicationInfo.enabled) return false;
        return activity.permission == null
                || context.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void addResolveInfo(ResolveInfo info, Map<String, AppModel> apps) {
        if (info.activityInfo == null || info.activityInfo.packageName == null) return;
        String pkg = info.activityInfo.packageName;
        if (apps.containsKey(pkg) || !isLaunchable(info.activityInfo)) return;
        AppModel cached = appCache.get(pkg);
        if (cached != null) {
            apps.put(pkg, cached);
            return;
        }
        String label;
        Drawable icon;
        try {
            label = info.loadLabel(pm).toString();
        } catch (RuntimeException exception) {
            label = pkg;
        }
        try {
            icon = info.loadIcon(pm);
        } catch (RuntimeException | OutOfMemoryError error) {
            icon = pm.getDefaultActivityIcon();
        }
        AppModel model = new AppModel(pkg, info.activityInfo.name, label, icon);
        appCache.put(pkg, model);
        apps.put(pkg, model);
    }
}
