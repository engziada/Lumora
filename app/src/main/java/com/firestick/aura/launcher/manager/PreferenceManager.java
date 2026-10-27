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
    private static final String KEY_CUSTOM_ORDER = "pref_custom_order";
    private static final String KEY_LAUNCH_COUNT = "pref_launch_count";
    private static final String KEY_HD_ICONS = "pref_hd_icons";
    private static final String KEY_HOME_REVISION = "pref_home_revision";

    /** Procedurally rendered offline scenes: zero network, instant, about 1 MB each. */
    public static final int WALLPAPER_SOURCE_OFFLINE = 0;
    /** Downloads online stills once, then serves them from the on-disk cache. */
    public static final int WALLPAPER_SOURCE_ONLINE = 1;

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public long getHomeRevision() {
        return prefs.getLong(KEY_HOME_REVISION, 0L);
    }

    private void applyHomeChange(SharedPreferences.Editor editor) {
        editor.putLong(KEY_HOME_REVISION, getHomeRevision() + 1L).apply();
    }

    public Set<String> getFavorites() {
        return new HashSet<>(prefs.getStringSet(KEY_FAVORITES, new HashSet<>()));
    }

    public void addFavorite(String packageName) {
        Set<String> set = getFavorites();
        if (set.add(packageName)) {
            applyHomeChange(prefs.edit().putStringSet(KEY_FAVORITES, set));
        }
    }

    public void removeFavorite(String packageName) {
        Set<String> set = getFavorites();
        if (set.remove(packageName)) {
            applyHomeChange(prefs.edit().putStringSet(KEY_FAVORITES, set));
        }
    }

    public boolean isFavorite(String packageName) {
        return getFavorites().contains(packageName);
    }

    public Set<String> getHiddenApps() {
        return new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<>()));
    }

    public void hideApp(String packageName) {
        Set<String> set = getHiddenApps();
        if (set.add(packageName)) {
            applyHomeChange(prefs.edit().putStringSet(KEY_HIDDEN, set));
        }
    }

    public void unhideApp(String packageName) {
        Set<String> set = getHiddenApps();
        if (set.remove(packageName)) {
            applyHomeChange(prefs.edit().putStringSet(KEY_HIDDEN, set));
        }
    }

    public void resetHiddenApps() {
        if (prefs.contains(KEY_HIDDEN)) {
            applyHomeChange(prefs.edit().remove(KEY_HIDDEN));
        }
    }

    public boolean isHidden(String packageName) {
        return getHiddenApps().contains(packageName);
    }

    /** Returns the stored accent colour or the supplied default. */
    public int getAccentColor(int defaultColor) {
        return prefs.getInt(KEY_ACCENT, defaultColor);
    }

    public boolean hasAccentColor() {
        return prefs.contains(KEY_ACCENT);
    }

    public void setAccentColor(int color) {
        if (getAccentColor(color) != color || !prefs.contains(KEY_ACCENT)) {
            applyHomeChange(prefs.edit().putInt(KEY_ACCENT, color));
        }
    }

    public int getWallpaperIntervalSeconds() {
        return prefs.getInt(KEY_WALLPAPER_INTERVAL, 60);
    }

    public void setWallpaperIntervalSeconds(int seconds) {
        prefs.edit().putInt(KEY_WALLPAPER_INTERVAL, seconds).apply();
    }

    public int getWallpaperSource() {
        return prefs.getInt(KEY_WALLPAPER_SOURCE, WALLPAPER_SOURCE_ONLINE);
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

    public List<String> getCustomOrder() {
        String raw = prefs.getString(KEY_CUSTOM_ORDER, "");
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }

    public void setCustomOrder(List<String> packages) {
        applyHomeChange(prefs.edit().putString(KEY_CUSTOM_ORDER, String.join(",", packages)));
    }

    public void clearCustomOrder() {
        if (prefs.contains(KEY_CUSTOM_ORDER)) {
            applyHomeChange(prefs.edit().remove(KEY_CUSTOM_ORDER));
        }
    }

    public int getLaunchCount() {
        return prefs.getInt(KEY_LAUNCH_COUNT, 0);
    }

    public void incrementLaunchCount() {
        prefs.edit().putInt(KEY_LAUNCH_COUNT, getLaunchCount() + 1).apply();
    }

    public boolean isHdIconsEnabled() {
        return prefs.getBoolean(KEY_HD_ICONS, true);
    }

    public void setHdIconsEnabled(boolean enabled) {
        if (isHdIconsEnabled() != enabled) {
            applyHomeChange(prefs.edit().putBoolean(KEY_HD_ICONS, enabled));
        }
    }
}
