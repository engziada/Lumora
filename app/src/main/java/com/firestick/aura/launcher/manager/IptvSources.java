package com.firestick.aura.launcher.manager;

import android.content.SharedPreferences;
import org.json.*;
import java.net.URI;
import java.util.*;

/** Private provider URLs may contain access tokens; deliberately excluded from launcher backups. */
public final class IptvSources {
    public static final class Source {
        public final String name, url;
        public Source(String name, String url) { this.name = name; this.url = url; }
    }
    private IptvSources() { }
    public static boolean validUrl(String value) {
        if (value == null || value.length() > 4096 || value.contains("\r") || value.contains("\n")) return false;
        try {
            URI uri = new URI(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (Exception invalid) { return false; }
    }
    public static boolean isWebPage(String value) {
        try {
            String host = new URI(value).getHost().toLowerCase(Locale.ROOT);
            return host.equals("youtu.be") || host.equals("youtube.com") || host.endsWith(".youtube.com")
                    || host.equals("twitch.tv") || host.endsWith(".twitch.tv");
        } catch (Exception invalid) { return true; }
    }
    public static List<Source> read(SharedPreferences preferences) {
        List<Source> result = new ArrayList<>();
        try {
            JSONArray rows = new JSONArray(preferences.getString("custom", "[]"));
            for (int i = 0; i < rows.length() && result.size() < 5; i++) {
                JSONObject row = rows.getJSONObject(i);
                if (validUrl(row.optString("url"))) result.add(new Source(row.optString("name", "Playlist"), row.getString("url")));
            }
        } catch (JSONException ignored) { }
        return result;
    }
    public static void save(SharedPreferences preferences, List<Source> sources) {
        JSONArray rows = new JSONArray();
        try {
            for (Source source : sources) rows.put(new JSONObject().put("name", source.name).put("url", source.url));
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
        preferences.edit().putString("custom", rows.toString()).apply();
    }
}
