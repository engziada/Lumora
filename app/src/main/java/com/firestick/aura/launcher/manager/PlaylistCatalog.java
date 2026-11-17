package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.content.SharedPreferences;
import com.firestick.aura.launcher.R;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Free channel lists the user can switch on in IPTV sources. The catalog ships with the app;
 * the lists load from the web like custom playlists and stay separate from the five custom URLs.
 */
public final class PlaylistCatalog {
    /** Source preference: the IDs of the lists switched on, comma separated. */
    public static final String PICKS = "catalog";

    public static final class Entry {
        public final String id, name, region, url, groupPrefix;

        Entry(String id, String name, String region, String url, String groupPrefix) {
            this.id = id;
            this.name = name;
            this.region = region;
            this.url = url;
            this.groupPrefix = groupPrefix;
        }

        /** Cuts the provider's prefix from group names ("XUMO🇺🇸: News" becomes "News"). */
        public String clean(String playlist) {
            if (playlist == null || groupPrefix.isEmpty()) return playlist;
            return playlist.replace("group-title=\"" + groupPrefix, "group-title=\"");
        }
    }

    private PlaylistCatalog() { }

    /** Entries with a lower-case ID, a name and an HTTPS URL. Rows without a country get {@code region}. */
    public static List<Entry> parse(String json) {
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray rows = new JSONArray(json);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                String id = row.optString("id"), name = row.optString("name"), url = row.optString("url");
                String region = row.optString("region").toUpperCase(Locale.ROOT);
                if (!id.matches("[a-z0-9_]+") || name.isEmpty() || !url.startsWith("https://")
                        || !IptvSources.validUrl(url)) continue;
                entries.add(new Entry(id, name, region.matches("[A-Z]{2}") ? region : "ALL", url,
                        row.optString("groupPrefix")));
            }
        } catch (Exception ignored) {
            // A broken catalog offers no lists; custom playlists still work.
        }
        return entries;
    }

    public static List<Entry> load(Context context) {
        try (InputStream in = context.getResources().openRawResource(R.raw.playlist_catalog)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int read; (read = in.read(buffer)) > 0; ) out.write(buffer, 0, read);
            return parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception unreadable) {
            return Collections.emptyList();
        }
    }

    public static Set<String> picks(SharedPreferences prefs) {
        Set<String> ids = new TreeSet<>();
        for (String id : prefs.getString(PICKS, "").split(",")) if (!id.isEmpty()) ids.add(id);
        return ids;
    }

    public static void pick(SharedPreferences prefs, String id, boolean on) {
        Set<String> ids = picks(prefs);
        if (on) ids.add(id);
        else ids.remove(id);
        prefs.edit().putString(PICKS, String.join(",", ids)).apply();
    }

    /** The catalog entries switched on, in catalog order. */
    public static List<Entry> picked(List<Entry> catalog, SharedPreferences prefs) {
        Set<String> ids = picks(prefs);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : catalog) if (ids.contains(entry.id)) result.add(entry);
        return result;
    }
}
