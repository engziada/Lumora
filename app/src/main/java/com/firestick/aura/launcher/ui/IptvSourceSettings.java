package com.firestick.aura.launcher.ui;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;

public final class IptvSourceSettings {
    private IptvSourceSettings() { }
    public static void show(AppCompatActivity activity, ExecutorService io) {
        SharedPreferences prefs = activity.getSharedPreferences("iptv_sources", Context.MODE_PRIVATE);
        List<IptvSources.Source> sources = IptvSources.read(prefs);
        List<GlassMenu.Item> items = new ArrayList<>();
        items.add(new GlassMenu.Item(R.drawable.ic_tv, activity.getString(R.string.iptv_curated_source))
                .checked(prefs.getBoolean("free_tv", true)));
        items.add(new GlassMenu.Item(R.drawable.ic_tv, activity.getString(R.string.iptv_source_add)));
        for (IptvSources.Source source : sources)
            items.add(new GlassMenu.Item(R.drawable.ic_tv, activity.getString(R.string.iptv_source_remove, source.name)));
        GlassMenu.show(activity, activity.getString(R.string.iptv_sources), null, null, items, false, (index, item) -> {
            if (index == 0) prefs.edit().putBoolean("free_tv", !prefs.getBoolean("free_tv", true)).apply();
            else if (index >= 2) { sources.remove(index - 2); IptvSources.save(prefs, sources); }
            else if (sources.size() >= 5) info(activity, R.string.iptv_source_limit);
            else TextPrompt.show(activity, activity.getString(R.string.iptv_source_name), null, "", false, name ->
                TextPrompt.show(activity, activity.getString(R.string.iptv_source_url),
                        activity.getString(R.string.iptv_source_hint), "", true, url -> {
                    if (!IptvSources.validUrl(url)) { info(activity, R.string.iptv_source_invalid); return; }
                    android.app.Dialog loading = Glass.info(activity, activity.getString(R.string.iptv_sources),
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
        });
    }
    private static void info(AppCompatActivity activity, int message) {
        Glass.info(activity, activity.getString(R.string.iptv_sources), activity.getString(message), null, null, null);
    }
}
