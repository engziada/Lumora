package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PreferenceManager {
    private static final String PREF_NAME = "ztv_launcher_prefs";
    private static final String KEY_FAVORITES = "pref_favorites";
    private static final String KEY_HIDDEN = "pref_hidden";
    private static final String KEY_ACCENT = "pref_accent_color";
    private static final String KEY_WALLPAPER_INTERVAL = "pref_wallpaper_interval";
    private static final String KEY_WALLPAPER_SOURCE = "pref_wallpaper_source";
    private static final String KEY_SOUND_ENABLED = "pref_sound_enabled";
    private static final String KEY_CUSTOM_ORDER_PREFIX = "pref_custom_order_";
    private static final String KEY_LAUNCH_COUNT = "pref_launch_count";
    private static final String KEY_RECENT = "pref_recent_apps";
    private static final String KEY_HD_ICONS = "pref_hd_icons";
    private static final String KEY_SHOWS_ENABLED = "pref_shows_enabled";
    private static final String KEY_SHOWS_COUNTRY = "pref_shows_country";
    private static final String KEY_HOME_REVISION = "pref_home_revision";
    private static final int MAX_RECENT = 12;

    /** Procedurally rendered offline scenes: zero network, instant. */
    public static final int WALLPAPER_SOURCE_OFFLINE = 0;
    /** Unsplash stills at 1280 x 720, downloaded once and cached. */
    public static final int WALLPAPER_SOURCE_ONLINE = 1;

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** Incremented by every change that affects the Home rows, so Home can rebuild on resume. */
    public long getHomeRevision() {
        return prefs.getLong(KEY_HOME_REVISION, 0L);
    }

    private void applyHomeChange(SharedPreferences.Editor editor) {
        editor.putLong(KEY_HOME_REVISION, getHomeRevision() + 1L).apply();
    }

    public Set<String> getFavorites() {
        return new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    public boolean isFavorite(String packageName) {
        return getFavorites().contains(packageName);
    }

    public void addFavorite(String packageName) {
        Set<String> set = getFavorites();
        if (set.add(packageName)) applyHomeChange(prefs.edit().putStringSet(KEY_FAVORITES, set));
    }

    public void removeFavorite(String packageName) {
        Set<String> set = getFavorites();
        if (set.remove(packageName)) applyHomeChange(prefs.edit().putStringSet(KEY_FAVORITES, set));
    }

    public Set<String> getHiddenApps() {
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<>()));
    }

    public boolean isHidden(String packageName) {
        return getHiddenApps().contains(packageName);
    }

    public void hideApp(String packageName) {
        Set<String> set = getHiddenApps();
        if (set.add(packageName)) applyHomeChange(prefs.edit().putStringSet(KEY_HIDDEN, set));
    }

    public void unhideApp(String packageName) {
        Set<String> set = getHiddenApps();
        if (set.remove(packageName)) applyHomeChange(prefs.edit().putStringSet(KEY_HIDDEN, set));
    }

    public void resetHiddenApps() {
        if (prefs.contains(KEY_HIDDEN)) applyHomeChange(prefs.edit().remove(KEY_HIDDEN));
    }

    public int getAccentColor(int defaultColor) {
        return prefs.getInt(KEY_ACCENT, defaultColor);
    }

    public void setAccentColor(int color) {
        if (!prefs.contains(KEY_ACCENT) || getAccentColor(color) != color) {
            applyHomeChange(prefs.edit().putInt(KEY_ACCENT, color));
        }
    }

    /** Replaces a stored accent from an older palette (for example v4 violet) with {@code fallback}. */
    public void migrateAccent(int[] allowed, int fallback) {
        if (!prefs.contains(KEY_ACCENT)) return;
        int current = prefs.getInt(KEY_ACCENT, fallback);
        for (int color : allowed) {
            if (color == current) return;
        }
        applyHomeChange(prefs.edit().putInt(KEY_ACCENT, fallback));
    }

    public int getWallpaperIntervalSeconds() {
        return prefs.getInt(KEY_WALLPAPER_INTERVAL, 60);
    }

    public void setWallpaperIntervalSeconds(int seconds) {
        prefs.edit().putInt(KEY_WALLPAPER_INTERVAL, seconds).apply();
    }

    public int getWallpaperSource() {
        return prefs.getInt(KEY_WALLPAPER_SOURCE, WALLPAPER_SOURCE_OFFLINE);
    }

    public void setWallpaperSource(int source) {
        prefs.edit().putInt(KEY_WALLPAPER_SOURCE, source).apply();
    }

    public boolean isOnlineWallpapers() {
        return getWallpaperSource() == WALLPAPER_SOURCE_ONLINE;
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
    }

    private static String orderKey(String rowId) {
        return KEY_CUSTOM_ORDER_PREFIX + rowId;
    }

    public List<String> getCustomOrder(String rowId) {
        return split(prefs.getString(orderKey(rowId), ""));
    }

    public void setCustomOrder(String rowId, List<String> packages) {
        applyHomeChange(prefs.edit().putString(orderKey(rowId), String.join(",", packages)));
    }

    public void clearCustomOrder(String rowId) {
        String key = orderKey(rowId);
        if (prefs.contains(key)) applyHomeChange(prefs.edit().remove(key));
    }

    public int getLaunchCount() {
        return prefs.getInt(KEY_LAUNCH_COUNT, 0);
    }

    /** Counts a launch and moves the package to the front of the recent-apps list. */
    public void recordLaunch(String packageName) {
        List<String> recent = getRecentApps();
        recent.remove(packageName);
        recent.add(0, packageName);
        while (recent.size() > MAX_RECENT) recent.remove(recent.size() - 1);
        prefs.edit()
                .putString(KEY_RECENT, String.join(",", recent))
                .putInt(KEY_LAUNCH_COUNT, getLaunchCount() + 1)
                .apply();
    }

    public List<String> getRecentApps() {
        return split(prefs.getString(KEY_RECENT, ""));
    }

    /** HD icon pack (dashboard-icons). On by default; apps without a match keep their own icon. */
    public boolean isHdIconsEnabled() {
        return prefs.getBoolean(KEY_HD_ICONS, true);
    }

    public void setHdIconsEnabled(boolean enabled) {
        if (isHdIconsEnabled() != enabled) applyHomeChange(prefs.edit().putBoolean(KEY_HD_ICONS, enabled));
    }

    public boolean isShowsEnabled() {
        return prefs.getBoolean(KEY_SHOWS_ENABLED, true);
    }

    public void setShowsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHOWS_ENABLED, enabled).apply();
    }

    /** ISO 3166-1 alpha-2 code used for the TVmaze broadcast schedule. */
    public String getShowsCountry() {
        String code = prefs.getString(KEY_SHOWS_COUNTRY, "US");
        return code == null || code.isEmpty() ? "US" : code;
    }

    public void setShowsCountry(String countryCode) {
        prefs.edit().putString(KEY_SHOWS_COUNTRY, countryCode).apply();
    }

    private static List<String> split(String raw) {
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }
}
