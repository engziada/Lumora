package com.firestick.aura.launcher.manager;

import org.json.*;
import java.util.*;

/** Versioned, typed backup for launcher preferences. Assistant credentials use a separate store. */
public final class LauncherBackup {
    private static final Set<String> STRINGS = new HashSet<>(Arrays.asList(
            "pref_recent_apps", "pref_card_effect", "pref_shows_country", "pref_lock_hash", "pref_lock_salt",
            "pref_recent_channels", "pref_home_sections", "pref_tile_size", "pref_appearance_profiles"));
    private static final Set<String> BOOLS = new HashSet<>(Arrays.asList(
            "pref_sound_enabled", "pref_hd_icons", "pref_app_preview_demo", "pref_shows_enabled",
            "pref_lock_settings", "pref_home_redirect", "pref_reduced_motion"));
    private static final Set<String> INTS = new HashSet<>(Arrays.asList(
            "pref_accent_color", "pref_wallpaper_interval", "pref_wallpaper_source", "pref_launch_count",
            "pref_lock_length", "pref_panel_content"));
    private static final Set<String> SETS = new HashSet<>(Arrays.asList(
            "pref_favorites", "pref_iptv_favorites", "pref_hidden", "pref_wall_categories",
            "pref_force_streaming", "pref_force_apps", "pref_locked_apps"));
    private LauncherBackup() { }

    private static boolean stringKey(String key) { return STRINGS.contains(key) || key.startsWith("pref_custom_order_"); }

    public static String encode(Map<String, ?> preferences) throws JSONException {
        JSONObject values = new JSONObject();
        for (Map.Entry<String, ?> entry : preferences.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (stringKey(key) || BOOLS.contains(key) || INTS.contains(key) || "pref_home_revision".equals(key))
                values.put(key, value);
            else if (SETS.contains(key) && value instanceof Set) values.put(key, new JSONArray((Set<?>) value));
        }
        return new JSONObject().put("format", "lumora-launcher").put("version", 1).put("values", values).toString(2);
    }

    public static Map<String, Object> decode(String text) throws JSONException {
        if (text.length() > 1024 * 1024) throw new JSONException("Backup exceeds 1 MB");
        JSONObject root = new JSONObject(text);
        if (!"lumora-launcher".equals(root.optString("format")) || root.optInt("version") != 1)
            throw new JSONException("Unsupported backup");
        JSONObject values = root.getJSONObject("values");
        Map<String, Object> result = new HashMap<>();
        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = values.get(key);
            if (stringKey(key)) {
                if (!(value instanceof String) || ((String) value).length() > 262144) throw new JSONException("Invalid " + key);
            } else if (BOOLS.contains(key)) {
                if (!(value instanceof Boolean)) throw new JSONException("Invalid " + key);
            } else if (INTS.contains(key)) {
                if (!(value instanceof Number) || ((Number) value).doubleValue() != ((Number) value).intValue())
                    throw new JSONException("Invalid " + key);
                value = ((Number) value).intValue();
            } else if ("pref_home_revision".equals(key)) {
                if (!(value instanceof Number)) throw new JSONException("Invalid revision");
                value = ((Number) value).longValue();
            } else if (SETS.contains(key)) {
                if (!(value instanceof JSONArray)) throw new JSONException("Invalid " + key);
                Set<String> set = new HashSet<>();
                JSONArray array = (JSONArray) value;
                if (array.length() > 20000) throw new JSONException("Too many entries");
                for (int i = 0; i < array.length(); i++) {
                    Object item = array.get(i);
                    if (!(item instanceof String) || ((String) item).length() > 4096) throw new JSONException("Invalid entry");
                    set.add((String) item);
                }
                value = set;
            } else continue;
            result.put(key, value);
        }
        if (result.containsKey("pref_lock_hash") != result.containsKey("pref_lock_salt"))
            throw new JSONException("Incomplete lock credentials");
        if (result.containsKey("pref_lock_hash")
                && (!result.get("pref_lock_hash").toString().matches("[0-9a-f]{64}")
                || !result.get("pref_lock_salt").toString().matches("[0-9a-f]+")))
            throw new JSONException("Invalid lock credentials");
        for (String key : Arrays.asList("pref_panel_content", "pref_wallpaper_source")) {
            int max = "pref_panel_content".equals(key) ? 2 : 1;
            if (result.containsKey(key) && ((Integer) result.get(key) < 0 || (Integer) result.get(key) > max))
                throw new JSONException("Invalid mode");
        }
        if (result.containsKey("pref_lock_length") && ((Integer) result.get("pref_lock_length") < 4
                || (Integer) result.get("pref_lock_length") > 8)) throw new JSONException("Invalid lock length");
        if (result.containsKey("pref_home_sections")) {
            List<String> rows = Arrays.asList(result.get("pref_home_sections").toString().split(",", -1));
            if (rows.isEmpty() || rows.size() > 5 || new HashSet<>(rows).size() != rows.size()
                    || !Arrays.asList("favorites", "recent", "iptv", "streaming", "apps").containsAll(rows))
                throw new JSONException("Invalid Home rows");
        }
        if (result.containsKey("pref_shows_country") && !result.get("pref_shows_country").toString().matches("ALL|[A-Za-z]{2}"))
            throw new JSONException("Invalid country");
        if (result.containsKey("pref_iptv_favorites")) {
            for (Object favorite : (Set<?>) result.get("pref_iptv_favorites"))
                if (!favorite.toString().matches("(ALL|[A-Za-z]{2}):[0-9]+")) throw new JSONException("Invalid channel favorite");
        }
        if (result.containsKey("pref_tile_size") && !Arrays.asList("compact", "comfortable")
                .contains(result.get("pref_tile_size"))) throw new JSONException("Invalid tile size");
        return result;
    }
}
