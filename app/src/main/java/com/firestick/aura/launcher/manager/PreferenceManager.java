package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.content.SharedPreferences;

import com.firestick.aura.launcher.ui.CardEffects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PreferenceManager {
    private static final String PREF_NAME = "ztv_launcher_prefs";
    private static final String KEY_FAVORITES = "pref_favorites";
    private static final String KEY_IPTV_FAVORITES = "pref_iptv_favorites";
    private static final String KEY_HIDDEN = "pref_hidden";
    private static final String KEY_ACCENT = "pref_accent_color";
    private static final String KEY_WALLPAPER_INTERVAL = "pref_wallpaper_interval";
    private static final String KEY_WALLPAPER_SOURCE = "pref_wallpaper_source";
    private static final String KEY_WALL_CATEGORIES = "pref_wall_categories";
    private static final String KEY_SOUND_ENABLED = "pref_sound_enabled";
    private static final String KEY_CUSTOM_ORDER_PREFIX = "pref_custom_order_";
    private static final String KEY_LAUNCH_COUNT = "pref_launch_count";
    private static final String KEY_RECENT = "pref_recent_apps";
    private static final String KEY_HD_ICONS = "pref_hd_icons";
    private static final String KEY_CARD_EFFECT = "pref_card_effect";
    private static final String KEY_APP_PREVIEW = "pref_app_preview_demo";
    private static final String KEY_SHOWS_ENABLED = "pref_shows_enabled";
    private static final String KEY_SHOWS_COUNTRY = "pref_shows_country";
    private static final String KEY_HOME_REVISION = "pref_home_revision";
    private static final String KEY_FORCE_STREAMING = "pref_force_streaming";
    private static final String KEY_FORCE_APPS = "pref_force_apps";
    private static final String KEY_LOCKED = "pref_locked_apps";
    private static final String KEY_LOCK_HASH = "pref_lock_hash";
    private static final String KEY_LOCK_SALT = "pref_lock_salt";
    private static final String KEY_LOCK_LENGTH = "pref_lock_length";
    private static final String KEY_LOCK_SETTINGS = "pref_lock_settings";
    private static final String KEY_HOME_REDIRECT = "pref_home_redirect";
    private static final String KEY_PANEL_CONTENT = "pref_panel_content";
    private static final int MAX_RECENT = 12;

    /** Procedurally rendered offline scenes: zero network, instant. */
    public static final int WALLPAPER_SOURCE_OFFLINE = 0;
    /** Live catalog: ESA/Hubble and Unsplash stills, downloaded once and cached. */
    public static final int WALLPAPER_SOURCE_ONLINE = 1;

    /** Home preview panel shows nothing. */
    public static final int PANEL_CONTENT_OFF = 0;
    /** Home preview panel shows the generative particles/plasma ambient: offline, no data. */
    public static final int PANEL_CONTENT_PARTICLES = 1;
    /** Home preview panel plays public videos from the provider feeds: needs network. */
    public static final int PANEL_CONTENT_VIDEOS = 2;

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        migrateAppPreview();
    }

    /**
     * The video preview's boolean toggle became a panel-content selector (off / particles /
     * videos). Users who had videos on keep playing videos; particles are the new default for
     * everyone else.
     */
    private void migrateAppPreview() {
        if (prefs.contains(KEY_PANEL_CONTENT) || !prefs.contains(KEY_APP_PREVIEW)) return;
        prefs.edit().putInt(KEY_PANEL_CONTENT,
                prefs.getBoolean(KEY_APP_PREVIEW, false) ? PANEL_CONTENT_VIDEOS : PANEL_CONTENT_OFF).apply();
    }

    /** Incremented by every change that affects the app rows, so Home can rebuild on resume. */
    public long getHomeRevision() {
        return prefs.getLong(KEY_HOME_REVISION, 0L);
    }

    /** True when the home preview panel shows anything at all. */
    public boolean isAppPreviewEnabled() {
        return getPanelContent() != PANEL_CONTENT_OFF;
    }

    /** What the home preview panel shows: {@link #PANEL_CONTENT_OFF}, {@code _PARTICLES} or {@code _VIDEOS}. */
    public int getPanelContent() {
        return prefs.getInt(KEY_PANEL_CONTENT, PANEL_CONTENT_PARTICLES);
    }

    public void setPanelContent(int content) {
        prefs.edit().putInt(KEY_PANEL_CONTENT, content).apply();
    }

    private void applyHomeChange(SharedPreferences.Editor editor) {
        editor.putLong(KEY_HOME_REVISION, getHomeRevision() + 1L).apply();
    }

    private Set<String> getSet(String key) {
        return new HashSet<>(prefs.getStringSet(key, new HashSet<>()));
    }

    // ------------------------------------------------------------ home (favourites)

    public Set<String> getFavorites() {
        return getSet(KEY_FAVORITES);
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

    public Set<String> getIptvFavorites() {
        return getSet(KEY_IPTV_FAVORITES);
    }

    public boolean isIptvFavorite(String channelId) {
        return getIptvFavorites().contains(channelId);
    }

    public void setIptvFavorite(String channelId, boolean favorite) {
        Set<String> set = getIptvFavorites();
        boolean changed = favorite ? set.add(channelId) : set.remove(channelId);
        if (changed) prefs.edit().putStringSet(KEY_IPTV_FAVORITES, set).apply();
    }

    // ------------------------------------------------------------------ hidden

    public Set<String> getHiddenApps() {
        return getSet(KEY_HIDDEN);
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

    // --------------------------------------------------------- apps vs streaming

    public Set<String> getForcedStreaming() {
        return getSet(KEY_FORCE_STREAMING);
    }

    public Set<String> getForcedApps() {
        return getSet(KEY_FORCE_APPS);
    }

    /** Pins an app to the Streaming tab ({@code true}) or the Apps tab ({@code false}). */
    public void setAppCategory(String packageName, boolean streaming) {
        Set<String> toStreaming = getForcedStreaming();
        Set<String> toApps = getForcedApps();
        if (streaming) {
            toStreaming.add(packageName);
            toApps.remove(packageName);
        } else {
            toApps.add(packageName);
            toStreaming.remove(packageName);
        }
        applyHomeChange(prefs.edit().putStringSet(KEY_FORCE_STREAMING, toStreaming).putStringSet(KEY_FORCE_APPS, toApps));
    }

    // -------------------------------------------------------------------- lock

    public Set<String> getLockedApps() {
        return getSet(KEY_LOCKED);
    }

    public boolean isLocked(String packageName) {
        return getLockedApps().contains(packageName);
    }

    public void setLocked(String packageName, boolean locked) {
        Set<String> set = getLockedApps();
        boolean changed = locked ? set.add(packageName) : set.remove(packageName);
        if (changed) applyHomeChange(prefs.edit().putStringSet(KEY_LOCKED, set));
    }

    public String getLockHash() {
        return prefs.getString(KEY_LOCK_HASH, null);
    }

    public String getLockSalt() {
        return prefs.getString(KEY_LOCK_SALT, null);
    }

    public int getLockLength() {
        return prefs.getInt(KEY_LOCK_LENGTH, AppLock.MIN_LENGTH);
    }

    public void setLockCode(String hash, String salt, int length) {
        prefs.edit().putString(KEY_LOCK_HASH, hash).putString(KEY_LOCK_SALT, salt).putInt(KEY_LOCK_LENGTH, length).apply();
    }

    public boolean isSettingsLocked() {
        return prefs.getBoolean(KEY_LOCK_SETTINGS, false);
    }

    public void setSettingsLocked(boolean locked) {
        prefs.edit().putBoolean(KEY_LOCK_SETTINGS, locked).apply();
    }

    // ------------------------------------------------------------- appearance

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

    /** HD icon pack (dashboard-icons). On by default; apps without a match keep their own icon. */
    public boolean isHdIconsEnabled() {
        return prefs.getBoolean(KEY_HD_ICONS, true);
    }

    public void setHdIconsEnabled(boolean enabled) {
        if (isHdIconsEnabled() != enabled) applyHomeChange(prefs.edit().putBoolean(KEY_HD_ICONS, enabled));
    }

    /** Background effect of the focused card, one of {@link CardEffects}' ids. */
    public String getCardEffect() {
        return CardEffects.normalize(prefs.getString(KEY_CARD_EFFECT, CardEffects.DEFAULT));
    }

    public void setCardEffect(String effect) {
        String normalized = CardEffects.normalize(effect);
        if (!normalized.equals(getCardEffect())) applyHomeChange(prefs.edit().putString(KEY_CARD_EFFECT, normalized));
    }

    // -------------------------------------------------------------- wallpaper

    public int getWallpaperIntervalSeconds() {
        return prefs.getInt(KEY_WALLPAPER_INTERVAL, 60);
    }

    public void setWallpaperIntervalSeconds(int seconds) {
        prefs.edit().putInt(KEY_WALLPAPER_INTERVAL, seconds).apply();
    }

    /** The live catalog is the default; offline scenes are the fallback and an option. */
    public int getWallpaperSource() {
        return prefs.getInt(KEY_WALLPAPER_SOURCE, WALLPAPER_SOURCE_ONLINE);
    }

    public void setWallpaperSource(int source) {
        prefs.edit().putInt(KEY_WALLPAPER_SOURCE, source).apply();
    }

    public boolean isOnlineWallpapers() {
        return getWallpaperSource() == WALLPAPER_SOURCE_ONLINE;
    }

    public Set<String> getWallpaperCategories() {
        if (!prefs.contains(KEY_WALL_CATEGORIES)) return new HashSet<>(WallpaperCatalog.DEFAULTS);
        return getSet(KEY_WALL_CATEGORIES);
    }

    public void setWallpaperCategories(Set<String> categories) {
        prefs.edit().putStringSet(KEY_WALL_CATEGORIES, new HashSet<>(categories)).apply();
    }

    // ------------------------------------------------------------------ audio

    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
    }

    // ------------------------------------------------------------------ order

    private static String orderKey(String rowId) {
        return KEY_CUSTOM_ORDER_PREFIX + rowId;
    }

    public List<String> getCustomOrder(String rowId) {
        return split(prefs.getString(orderKey(rowId), ""));
    }

    public void setCustomOrder(String rowId, List<String> packages) {
        applyHomeChange(prefs.edit().putString(orderKey(rowId), String.join(",", packages)));
    }

    // --------------------------------------------------------------- launches

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

    // ------------------------------------------------------------------ shows

    public boolean isShowsEnabled() {
        return prefs.getBoolean(KEY_SHOWS_ENABLED, true);
    }

    public void setShowsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHOWS_ENABLED, enabled).apply();
    }

    /** ISO 3166-1 alpha-2 code used for the TVmaze schedules. */
    public String getShowsCountry() {
        String code = prefs.getString(KEY_SHOWS_COUNTRY, "US");
        return code == null || code.isEmpty() ? "US" : code;
    }

    public void setShowsCountry(String countryCode) {
        prefs.edit().putString(KEY_SHOWS_COUNTRY, countryCode).apply();
    }

    // ------------------------------------------------------------------- home

    /** Bring Lumora back when the stock Fire TV home appears (needs the quick-actions service). */
    public boolean isHomeRedirectEnabled() {
        return prefs.getBoolean(KEY_HOME_REDIRECT, true);
    }

    public void setHomeRedirectEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HOME_REDIRECT, enabled).apply();
    }

    public List<com.firestick.aura.launcher.model.IptvChannel> recentChannels() {
        List<com.firestick.aura.launcher.model.IptvChannel> result = new ArrayList<>();
        try {
            org.json.JSONArray rows = new org.json.JSONArray(prefs.getString("pref_recent_channels", "[]"));
            for (int i = 0; i < rows.length() && i < 12; i++)
                result.add(com.firestick.aura.launcher.model.IptvChannel.fromJson(rows.getJSONObject(i)));
        } catch (org.json.JSONException ignored) { }
        return result;
    }

    public void recordChannel(com.firestick.aura.launcher.model.IptvChannel channel) {
        List<com.firestick.aura.launcher.model.IptvChannel> recent = recentChannels();
        recent.removeIf(item -> item.id == channel.id);
        recent.add(0, channel);
        org.json.JSONArray rows = new org.json.JSONArray();
        for (int i = 0; i < Math.min(12, recent.size()); i++) rows.put(recent.get(i).toJson());
        prefs.edit().putString("pref_recent_channels", rows.toString()).apply();
    }

    public List<String> homeSections() {
        return split(prefs.getString("pref_home_sections", "favorites,recent,iptv"));
    }

    public void setHomeSections(List<String> sections) {
        applyHomeChange(prefs.edit().putString("pref_home_sections", String.join(",", sections)));
    }

    public String tileSize() { return prefs.getString("pref_tile_size", "compact"); }
    public void setTileSize(String size) { applyHomeChange(prefs.edit().putString("pref_tile_size", size)); }
    public boolean reducedMotion() { return prefs.getBoolean("pref_reduced_motion", false); }
    public void setReducedMotion(boolean reduced) {
        applyHomeChange(prefs.edit().putBoolean("pref_reduced_motion", reduced));
    }

    public SharedPreferences storage() { return prefs; }

    private static List<String> split(String raw) {
        if (raw == null || raw.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(raw.split(",")));
    }
}
