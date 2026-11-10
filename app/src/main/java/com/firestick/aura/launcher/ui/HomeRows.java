package com.firestick.aura.launcher.ui;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.*;
import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.adapter.*;
import com.firestick.aura.launcher.manager.*;
import com.firestick.aura.launcher.model.*;
import java.util.*;
import java.util.function.Consumer;

/** Ordered Home sections. The original favorites ribbon retains its adapter and move behavior. */
public final class HomeRows {
    private final Activity activity;
    private final PreferenceManager prefs;
    private final LinearLayout parent;
    private final View favorites;
    private final AppRowAdapter.OnAppActionListener listener;
    private final Consumer<IptvChannel> play;
    private final Consumer<AppModel> focus;
    private String fingerprint = "";

    public HomeRows(Activity activity, PreferenceManager prefs, AppRowAdapter.OnAppActionListener listener,
                    Consumer<IptvChannel> play, Consumer<AppModel> focus) {
        this.activity = activity;
        this.prefs = prefs;
        this.listener = listener;
        this.play = play;
        this.focus = focus;
        parent = activity.findViewById(R.id.homeSections);
        favorites = activity.findViewById(R.id.homeFavoritesSection);
    }

    public void render(Map<String, List<AppModel>> rows) {
        String current = prefs.getHomeRevision() + "|" + prefs.getRecentApps() + "|" + prefs.tileSize()
                + "|" + prefs.storage().getString("pref_recent_channels", "") + "|" + prefs.getIptvFavorites() + "|" + rows.keySet();
        for (List<AppModel> list : rows.values()) for (AppModel app : list) current += app.getPackageName();
        if (current.equals(fingerprint)) return;
        fingerprint = current;
        parent.removeAllViews();
        Map<String, AppModel> installed = new HashMap<>();
        for (List<AppModel> list : rows.values()) for (AppModel app : list) installed.put(app.getPackageName(), app);
        for (String key : prefs.homeSections()) {
            if ("favorites".equals(key)) { parent.addView(favorites); continue; }
            LinearLayout section = new LinearLayout(activity);
            section.setOrientation(LinearLayout.VERTICAL);
            if ("iptv".equals(key)) {
                List<IptvChannel> recent = prefs.recentChannels();
                recent.replaceAll(channel -> channel.withFavorite(prefs.isIptvFavorite(channel.country + ":" + channel.id)));
                if (recent.isEmpty() || !prefs.isShowsEnabled()) continue;
                heading(section, R.string.home_recent_channels);
                TextView resume = new TextView(activity);
                resume.setText(activity.getString(R.string.home_continue_live, recent.get(0).name));
                resume.setTextColor(ContextCompat.getColor(activity, R.color.nova_text));
                resume.setTextSize(12);
                resume.setPadding(dp(16), dp(8), dp(16), dp(8));
                resume.setBackgroundResource(R.drawable.bg_option_selector);
                resume.setFocusable(true);
                resume.setOnClickListener(view -> play.accept(recent.get(0)));
                LinearLayout.LayoutParams resumeParams = new LinearLayout.LayoutParams(-2, dp(36));
                resumeParams.setMarginStart(dp(38));
                section.addView(resume, resumeParams);
                RecyclerView list = ribbon();
                IptvAdapter adapter = new IptvAdapter(prefs, play::accept, (channel, favorite) -> {
                    prefs.setIptvFavorite(channel.country + ":" + channel.id, favorite);
                    ((IptvAdapter) list.getAdapter()).updateFavoriteState(channel.id, favorite);
                });
                list.setAdapter(adapter);
                adapter.setItems(recent);
                section.addView(list);
            } else {
                List<AppModel> models = new ArrayList<>();
                if ("recent".equals(key)) {
                    for (String pkg : prefs.getRecentApps()) if (installed.containsKey(pkg)) models.add(installed.get(pkg));
                } else models.addAll(rows.getOrDefault(key, Collections.emptyList()));
                if (models.isEmpty()) continue;
                heading(section, "recent".equals(key) ? R.string.home_recent_apps
                        : "streaming".equals(key) ? R.string.nav_streaming : R.string.nav_apps);
                RecyclerView list = ribbon();
                AppRowAdapter adapter = new AppRowAdapter(prefs, listener, key, AppRowAdapter.STYLE_WIDE, (row, app) -> focus.accept(app));
                list.setAdapter(adapter);
                adapter.setApps(models);
                section.addView(list);
            }
            parent.addView(section);
        }
        if (parent.getChildCount() == 0) {
            TextView empty = new TextView(activity);
            empty.setText(R.string.home_empty_rows);
            empty.setTextColor(ContextCompat.getColor(activity, R.color.nova_text_dim));
            empty.setPadding(dp(40), dp(24), dp(40), dp(24));
            parent.addView(empty);
        }
    }

    private RecyclerView ribbon() {
        RecyclerView view = new RecyclerView(activity);
        view.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false));
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(118)));
        view.setPadding(dp(32), dp(5), dp(32), dp(6));
        view.setClipToPadding(false);
        view.setNestedScrollingEnabled(false);
        view.setItemAnimator(null);
        return view;
    }
    private void heading(LinearLayout section, int title) {
        TextView view = new TextView(activity);
        view.setText(title);
        view.setTextSize(14);
        view.setTextColor(ContextCompat.getColor(activity, R.color.nova_text));
        view.setPadding(dp(40), dp(8), dp(40), dp(2));
        section.addView(view);
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
