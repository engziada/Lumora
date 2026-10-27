package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

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

/**
 * Loads the installed application catalogue.
 *
 * <p>v3 keeps two caches so that returning to the home screen is effectively free:
 * <ul>
 *   <li>{@code appCache} keeps every resolved {@link AppModel} (label + drawable) for the
 *       lifetime of the process, so icons are decoded once instead of on every resume;</li>
 *   <li>{@code categoriesCache} keeps the assembled rows until something actually changes
 *       (favourite, hidden app, package install/removal, accent colour, custom order).</li>
 * </ul>
 */
public class AppRepository {
    private final Context context;
    private final PackageManager pm;
    private final PreferenceManager preferenceManager;

    private final Map<String, AppModel> appCache = new HashMap<>();
    private List<AppCategory> categoriesCache;

    public AppRepository(Context context) {
        this.context = context.getApplicationContext();
        this.pm = context.getPackageManager();
        this.preferenceManager = new PreferenceManager(context);
    }

    /** Returns the cached rows when they are still valid, otherwise rebuilds them. */
    public List<AppCategory> loadCategories() {
        if (categoriesCache != null) {
            return categoriesCache;
        }
        categoriesCache = buildCategories();
        return categoriesCache;
    }

    /** Drops the assembled rows but keeps decoded labels/icons. */
    public void invalidate() {
        categoriesCache = null;
    }

    /** Drops everything, including decoded icons (used after uninstall/install events). */
    public void invalidateAll() {
        categoriesCache = null;
        appCache.clear();
    }

    private List<AppCategory> buildCategories() {
        Map<String, AppModel> appMap = new HashMap<>();

        // 1. Query Leanback Launcher apps (native TV entry points win over mobile ones)
        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN, null);
        leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo info : pm.queryIntentActivities(leanbackIntent, 0)) {
            addResolveInfoToMap(info, appMap);
        }

        // 2. Query standard Launcher apps (sideloaded mobile apps, downloader tools, etc.)
        Intent normalIntent = new Intent(Intent.ACTION_MAIN, null);
        normalIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : pm.queryIntentActivities(normalIntent, 0)) {
            addResolveInfoToMap(info, appMap);
        }

        // Exclude Aura launcher itself
        appMap.remove(context.getPackageName());

        // Forget cached models for apps that are no longer installed.
        appCache.keySet().retainAll(appMap.keySet());

        List<AppModel> allApps = new ArrayList<>(appMap.values());
        sortApps(allApps);

        List<AppModel> favoritesList = new ArrayList<>();
        List<AppModel> streamingList = new ArrayList<>();
        List<AppModel> toolsList = new ArrayList<>();
        List<AppModel> visibleAppsList = new ArrayList<>();

        for (AppModel app : allApps) {
            boolean isHidden = preferenceManager.isHidden(app.getPackageName());
            app.setHidden(isHidden);
            if (isHidden) {
                continue; // Skip hidden apps from main rows
            }

            boolean isFav = preferenceManager.isFavorite(app.getPackageName());
            app.setFavorite(isFav);

            if (isFav) {
                favoritesList.add(app);
            }

            visibleAppsList.add(app);

            String pkg = app.getPackageName().toLowerCase(Locale.ROOT);
            String name = app.getLabel().toLowerCase(Locale.ROOT);

            if (isStreamingApp(pkg, name)) {
                streamingList.add(app);
            } else if (isToolApp(pkg, name)) {
                toolsList.add(app);
            }
        }

        int accent = preferenceManager.getAccentColor(
                ContextCompat.getColor(context, R.color.nova_accent));
        int fire = ContextCompat.getColor(context, R.color.accent_fire);
        int emerald = ContextCompat.getColor(context, R.color.accent_emerald);
        int silver = ContextCompat.getColor(context, R.color.accent_silver);

        List<AppCategory> categories = new ArrayList<>();

        // Category 1: Pinned Favorites (always on top, tinted with the active theme)
        if (!favoritesList.isEmpty()) {
            AppCategory favCat = new AppCategory("favorites", context.getString(R.string.category_favorites), accent);
            favCat.setApps(favoritesList);
            categories.add(favCat);
        }

        // Category 2: Media & Streaming
        if (!streamingList.isEmpty()) {
            AppCategory streamCat = new AppCategory("streaming", context.getString(R.string.category_streaming), fire);
            streamCat.setApps(streamingList);
            categories.add(streamCat);
        }

        // Category 3: All Apps (Full Library)
        if (!visibleAppsList.isEmpty()) {
            AppCategory allCat = new AppCategory("all", context.getString(R.string.category_all_apps), emerald);
            allCat.setApps(visibleAppsList);
            categories.add(allCat);
        }

        // Category 4: Tools & System
        if (!toolsList.isEmpty()) {
            AppCategory toolsCat = new AppCategory("tools", context.getString(R.string.category_tools), silver);
            toolsCat.setApps(toolsList);
            categories.add(toolsCat);
        }

        return categories;
    }

    /**
     * Apps explicitly moved by the user keep their relative position; everything else
     * falls back to alphabetical order. This is what makes Move Left/Right stick
     * between launches.
     */
    private void sortApps(List<AppModel> apps) {
        final List<String> order = preferenceManager.getCustomOrder();
        final Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            rank.put(order.get(i), i);
        }

        Collections.sort(apps, (a, b) -> {
            Integer rankA = rank.get(a.getPackageName());
            Integer rankB = rank.get(b.getPackageName());
            if (rankA != null && rankB != null) {
                return Integer.compare(rankA, rankB);
            }
            if (rankA != null) return -1;
            if (rankB != null) return 1;
            return a.getLabel().compareToIgnoreCase(b.getLabel());
        });
    }

    private void addResolveInfoToMap(ResolveInfo info, Map<String, AppModel> appMap) {
        if (info.activityInfo == null) return;
        String pkg = info.activityInfo.packageName;
        if (pkg == null || appMap.containsKey(pkg)) return;

        AppModel cached = appCache.get(pkg);
        if (cached != null) {
            appMap.put(pkg, cached);
            return;
        }

        String label;
        Drawable icon;
        try {
            label = info.loadLabel(pm).toString();
        } catch (Exception e) {
            label = pkg;
        }
        try {
            icon = info.loadIcon(pm);
        } catch (Exception | OutOfMemoryError e) {
            icon = pm.getDefaultActivityIcon();
        }

        AppModel model = new AppModel(pkg, info.activityInfo.name, label, icon);
        appCache.put(pkg, model);
        appMap.put(pkg, model);
    }

    private boolean isStreamingApp(String pkg, String name) {
        return pkg.contains("stremio") || pkg.contains("netflix") || pkg.contains("primevideo") ||
               pkg.contains("youtube") || pkg.contains("smarttube") || pkg.contains("disney") ||
               pkg.contains("hbo") || pkg.contains("max") || pkg.contains("kodi") ||
               pkg.contains("plex") || pkg.contains("hulu") || pkg.contains("twitch") ||
               pkg.contains("crunchyroll") || pkg.contains("vlc") || pkg.contains("mxtech") ||
               name.contains("tv") || name.contains("movie") || name.contains("stream");
    }

    private boolean isToolApp(String pkg, String name) {
        return pkg.contains("downloader") || pkg.contains("settings") || pkg.contains("explorer") ||
               pkg.contains("browser") || pkg.contains("cleanup") || pkg.contains("speedtest") ||
               pkg.contains("file") || pkg.contains("tool") || name.contains("tool") ||
               name.contains("clean") || name.contains("setting");
    }
}

