package com.firestick.aura.launcher.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;

public final class IptvSourceSettings {
    private static final int PLUTO = 0, SAMSUNG = 1, REGION = 2, FREE_TV = 3, ADD = 4, CATALOG = 5, FIRST_PLAYLIST = 6;
    private IptvSourceSettings() { }
    public static void show(AppCompatActivity activity, ExecutorService io) {
        SharedPreferences prefs = activity.getSharedPreferences("iptv_sources", Context.MODE_PRIVATE);
        List<IptvSources.Source> sources = IptvSources.read(prefs);
        List<GlassMenu.Item> items = new ArrayList<>();
        items.add(new GlassMenu.Item(R.drawable.ic_tv, activity.getString(R.string.iptv_pluto_source))
                .checked(prefs.getBoolean(FastGuide.PLUTO, true)));
        items.add(new GlassMenu.Item(R.drawable.ic_tv, activity.getString(R.string.iptv_samsung_source))
                .checked(prefs.getBoolean(FastGuide.SAMSUNG, false)));
        items.add(new GlassMenu.Item(R.drawable.ic_tv, activity.getString(R.string.iptv_fast_region,
                regionName(activity, prefs.getString(IptvRepository.FAST_REGION, FastGuide.AUTO)))));
        items.add(new GlassMenu.Item(R.drawable.ic_tv, activity.getString(R.string.iptv_curated_source))
                .checked(prefs.getBoolean("free_tv", true)));
        items.add(new GlassMenu.Item(R.drawable.ic_tv, activity.getString(R.string.iptv_source_add)));
        items.add(new GlassMenu.Item(R.drawable.ic_tv, activity.getString(R.string.iptv_catalog)));
        for (IptvSources.Source source : sources)
            items.add(new GlassMenu.Item(R.drawable.ic_tv, activity.getString(R.string.iptv_source_remove, source.name)));
        // Toggles keep the menu open; the other rows close it and reopen it when its labels change.
        Dialog[] menu = new Dialog[1];
        menu[0] = GlassMenu.show(activity, activity.getString(R.string.iptv_sources), null, null, items, true, (index, item) -> {
            if (index == PLUTO) prefs.edit().putBoolean(FastGuide.PLUTO, item.isChecked()).apply();
            else if (index == SAMSUNG) prefs.edit().putBoolean(FastGuide.SAMSUNG, item.isChecked()).apply();
            else if (index == FREE_TV) prefs.edit().putBoolean("free_tv", item.isChecked()).apply();
            else if (index == REGION) pickRegion(activity, prefs, () -> { menu[0].dismiss(); show(activity, io); });
            else if (index == CATALOG) pickLists(activity, prefs);
            else if (index >= FIRST_PLAYLIST) {
                sources.remove(index - FIRST_PLAYLIST);
                IptvSources.save(prefs, sources);
                menu[0].dismiss();
                show(activity, io);
            } else {
                menu[0].dismiss();
                if (sources.size() >= 5) info(activity, R.string.iptv_source_limit);
                else addPlaylist(activity, io, prefs);
            }
        });
    }

    /** Free lists switch on and off in place; IPTV reloads them when it next opens. */
    private static void pickLists(AppCompatActivity activity, SharedPreferences prefs) {
        List<PlaylistCatalog.Entry> catalog = PlaylistCatalog.load(activity);
        Set<String> picks = PlaylistCatalog.picks(prefs);
        List<GlassMenu.Item> items = new ArrayList<>();
        for (PlaylistCatalog.Entry entry : catalog) {
            String label = "ALL".equals(entry.region) ? entry.name : activity.getString(R.string.iptv_catalog_entry,
                    entry.name, new Locale("", entry.region).getDisplayCountry());
            items.add(new GlassMenu.Item(R.drawable.ic_tv, label).checked(picks.contains(entry.id)));
        }
        GlassMenu.show(activity, activity.getString(R.string.iptv_catalog), activity.getString(R.string.iptv_catalog_hint),
                null, items, true, (index, item) -> PlaylistCatalog.pick(prefs, catalog.get(index).id, item.isChecked()));
    }

    private static void pickRegion(AppCompatActivity activity, SharedPreferences prefs, Runnable changed) {
        String current = prefs.getString(IptvRepository.FAST_REGION, FastGuide.AUTO);
        List<String> codes = new ArrayList<>(Arrays.asList(FastGuide.REGIONS));
        codes.sort(Comparator.comparing(code -> regionName(activity, code)));
        codes.add(0, FastGuide.AUTO);
        List<GlassMenu.Item> items = new ArrayList<>();
        for (String code : codes)
            items.add(new GlassMenu.Item(R.drawable.ic_tv, regionName(activity, code)).checked(code.equals(current)));
        GlassMenu.show(activity, activity.getString(R.string.iptv_fast_region_title),
                activity.getString(R.string.iptv_fast_region_hint), null, items, false, (index, item) -> {
                    prefs.edit().putString(IptvRepository.FAST_REGION, codes.get(index)).apply();
                    changed.run();
                });
    }

    private static String regionName(Context context, String code) {
        return FastGuide.AUTO.equals(code) ? context.getString(R.string.iptv_fast_region_auto)
                : new Locale("", code.toUpperCase(Locale.ROOT)).getDisplayCountry();
    }

    private static void addPlaylist(AppCompatActivity activity, ExecutorService io, SharedPreferences prefs) {
        TextPrompt.show(activity, activity.getString(R.string.iptv_source_name), null, "", false, name ->
            TextPrompt.show(activity, activity.getString(R.string.iptv_source_url),
                    activity.getString(R.string.iptv_source_hint), "", true, url -> {
                if (!IptvSources.validUrl(url)) { info(activity, R.string.iptv_source_invalid); return; }
                Dialog loading = Glass.info(activity, activity.getString(R.string.iptv_sources),
                        activity.getString(R.string.iptv_source_checking), null, null, null);
                io.execute(() -> {
                    byte[] bytes = Http.getBounded(url, 8000, 15000, 8 * 1024 * 1024);
                    List<com.firestick.aura.launcher.model.IptvChannel> rows = bytes == null ? null
                            : IptvRepository.parsePlaylist(new String(bytes, StandardCharsets.UTF_8), "ALL");
                    activity.runOnUiThread(() -> {
                        if (activity.isDestroyed()) return;
                        loading.dismiss();
                        if (rows == null || rows.isEmpty()) { info(activity, R.string.iptv_source_invalid); return; }
                        List<IptvSources.Source> latest = IptvSources.read(prefs);
                        for (IptvSources.Source existing : latest) if (existing.url.equals(url)) {
                            info(activity, R.string.iptv_source_duplicate); return;
                        }
                        if (latest.size() >= 5) { info(activity, R.string.iptv_source_limit); return; }
                        latest.add(new IptvSources.Source(name.substring(0, Math.min(40, name.length())), url));
                        IptvSources.save(prefs, latest);
                        info(activity, R.string.iptv_source_saved);
                    });
                });
            }));
    }

    private static void info(AppCompatActivity activity, int message) {
        Glass.info(activity, activity.getString(R.string.iptv_sources), activity.getString(message), null, null, null);
    }
}
