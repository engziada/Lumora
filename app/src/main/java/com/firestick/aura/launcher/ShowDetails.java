package com.firestick.aura.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.firestick.aura.launcher.manager.RemoteImageLoader;
import com.firestick.aura.launcher.model.ShowItem;
import com.firestick.aura.launcher.ui.Glass;
import com.firestick.aura.launcher.ui.GlassMenu;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Show details with a path to watching: open the channel's app on a search for the title, or
 * hand off to the Amazon Appstore page when the app is missing. TVmaze has no per-service IDs,
 * so the title search is the closest deep link available; installing still needs the user to
 * confirm on the Appstore page (third-party apps cannot install silently).
 */
final class ShowDetails {
    interface Host {
        void runUnlocked(String packageName, Runnable action);

        void onLaunched(String packageName);
    }

    /** Channel name (lower case) to the apps that carry it, preferred first. */
    private static final String[][] CHANNEL_APPS = {
            {"netflix", "com.netflix.ninja"},
            {"prime video", "com.amazon.avod.thirdpartyclient", "com.amazon.amazonvideo.livingroom"},
            {"amazon", "com.amazon.avod.thirdpartyclient", "com.amazon.amazonvideo.livingroom"},
            {"disney+", "com.disney.disneyplus"},
            {"hulu", "com.hulu.plus"},
            {"max", "com.wbd.stream", "com.hbo.hbonow"},
            {"hbo", "com.wbd.stream", "com.hbo.hbonow"},
            {"apple tv", "com.apple.atve.androidtv.appletv"},
            {"paramount+", "com.cbs.ott"},
            {"peacock", "com.peacocktv.peacockandroid"},
            {"crunchyroll", "com.crunchyroll.crunchyroid"},
            {"youtube", "com.amazon.firetv.youtube", "com.google.android.youtube.tv", "com.google.android.youtube"},
            {"tubi", "com.tubitv.ott"},
            {"pluto", "tv.pluto.android"}};

    private final Activity activity;
    private final Host host;
    private final PackageManager pm;

    ShowDetails(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        this.pm = activity.getPackageManager();
    }

    void show(ShowItem show) {
        Dialog dialog = Glass.overlay(activity, R.layout.dialog_show_details, Gravity.CENTER);
        ImageView poster = dialog.findViewById(R.id.imgShowPoster);
        poster.setClipToOutline(true);
        int width = Math.round(132 * activity.getResources().getDisplayMetrics().density);
        RemoteImageLoader.get(activity).load(show.posterUrl, width, poster::setImageBitmap);
        ((TextView) dialog.findViewById(R.id.txtShowTitle)).setText(show.title);

        StringBuilder meta = new StringBuilder();
        if (!show.channel.isEmpty() && !show.airtime.isEmpty()) {
            meta.append(activity.getString(R.string.show_channel_time, show.channel, show.airtime));
        } else {
            meta.append(show.channel).append(show.airtime);
        }
        if (!show.genres.isEmpty()) append(meta, show.genres);
        if (show.rating > 0d) append(meta, activity.getString(R.string.show_rating, show.rating));
        ((TextView) dialog.findViewById(R.id.txtShowMeta)).setText(meta);

        TextView episode = dialog.findViewById(R.id.txtShowEpisode);
        if (show.season > 0 && show.number > 0) {
            episode.setText(activity.getString(R.string.show_episode, show.season, show.number, show.episodeName));
        } else if (!show.episodeName.isEmpty()) {
            episode.setText(show.episodeName);
        } else {
            episode.setVisibility(View.GONE);
        }
        ((TextView) dialog.findViewById(R.id.txtShowSummary)).setText(
                show.summary.isEmpty() ? activity.getString(R.string.show_no_summary) : show.summary);

        Button watch = dialog.findViewById(R.id.btnShowWatch);
        Button other = dialog.findViewById(R.id.btnShowSearch);
        Button close = dialog.findViewById(R.id.btnShowClose);
        String installed = installedAppFor(show.channel);
        String known = knownAppFor(show.channel);
        if (installed != null) {
            watch.setText(activity.getString(R.string.show_watch_on, label(installed)));
            watch.setOnClickListener(v -> {
                dialog.dismiss();
                watch(installed, show.title);
            });
        } else if (known != null) {
            watch.setText(activity.getString(R.string.show_get_app, show.channel));
            watch.setOnClickListener(v -> {
                dialog.dismiss();
                confirmInstall(known, show.channel);
            });
        } else if (!show.channel.isEmpty()) {
            watch.setText(activity.getString(R.string.show_find_app, show.channel));
            watch.setOnClickListener(v -> {
                dialog.dismiss();
                openStore(null, show.channel);
            });
        } else {
            watch.setVisibility(View.GONE);
        }
        List<String> searchable = searchableApps(installed);
        if (searchable.isEmpty()) {
            other.setVisibility(View.GONE);
        } else {
            other.setOnClickListener(v -> {
                dialog.dismiss();
                chooseApp(searchable, show.title);
            });
        }
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        (watch.getVisibility() == View.VISIBLE ? watch : close).requestFocus();
    }

    private static void append(StringBuilder meta, String part) {
        if (meta.length() > 0) meta.append(" \u00B7 ");
        meta.append(part);
    }

    private String label(String packageName) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException exception) {
            return packageName;
        }
    }

    private Intent launchIntent(String packageName) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(packageName);
        return intent != null ? intent : pm.getLaunchIntentForPackage(packageName);
    }

    private static boolean matches(String channel, String key) {
        // "max" is a substring of many channel names, so it must match exactly.
        return "max".equals(key) ? channel.equals(key) : channel.contains(key);
    }

    private String installedAppFor(String channel) {
        if (channel == null || channel.isEmpty()) return null;
        String name = channel.toLowerCase(Locale.ROOT);
        for (String[] row : CHANNEL_APPS) {
            if (!matches(name, row[0])) continue;
            for (int i = 1; i < row.length; i++) if (launchIntent(row[i]) != null) return row[i];
        }
        return null;
    }

    private static String knownAppFor(String channel) {
        if (channel == null || channel.isEmpty()) return null;
        String name = channel.toLowerCase(Locale.ROOT);
        for (String[] row : CHANNEL_APPS) if (matches(name, row[0])) return row[1];
        return null;
    }

    /** Installed launchable apps that declare a search activity. */
    private List<String> searchableApps(String exclude) {
        Set<String> packages = new LinkedHashSet<>();
        for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_SEARCH), 0)) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(exclude) || pkg.equals(activity.getPackageName()) || launchIntent(pkg) == null) continue;
            packages.add(pkg);
        }
        return new ArrayList<>(packages);
    }

    private void chooseApp(List<String> packages, String title) {
        List<GlassMenu.Item> items = new ArrayList<>();
        for (String pkg : packages) {
            Drawable icon;
            try {
                icon = pm.getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException exception) {
                icon = null;
            }
            items.add(icon != null ? new GlassMenu.Item(icon, label(pkg)) : new GlassMenu.Item(R.drawable.ic_search, label(pkg)));
        }
        GlassMenu.show(activity, activity.getString(R.string.show_search_in), title, null, items, false,
                (index, item) -> watch(packages.get(index), title));
    }

    private boolean tryStart(Intent intent) {
        if (intent.resolveActivity(pm) == null) return false;
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException exception) {
            return false;
        }
    }

    private static Intent webSearch(String packageName, String title) {
        String query = Uri.encode(title);
        if (packageName.contains("youtube")) {
            return new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + query)).setPackage(packageName);
        }
        if (packageName.equals("com.netflix.ninja")) {
            return new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.netflix.com/search?q=" + query)).setPackage(packageName);
        }
        return null;
    }

    /** Search intent, then the app's web search link, then a plain launch with a hint. */
    private void watch(String packageName, String title) {
        host.runUnlocked(packageName, () -> {
            Intent search = new Intent(Intent.ACTION_SEARCH).setPackage(packageName)
                    .putExtra(SearchManager.QUERY, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (tryStart(search)) {
                host.onLaunched(packageName);
                return;
            }
            Intent web = webSearch(packageName, title);
            if (web != null && tryStart(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                host.onLaunched(packageName);
                return;
            }
            Intent launch = launchIntent(packageName);
            if (launch != null && tryStart(launch)) {
                host.onLaunched(packageName);
                Toast.makeText(activity, activity.getString(R.string.show_search_manually, title), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(activity, activity.getString(R.string.error_not_installed, label(packageName)), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmInstall(String packageName, String channel) {
        Glass.info(activity, activity.getString(R.string.show_install_title, channel),
                activity.getString(R.string.show_install_message, channel), null,
                activity.getString(R.string.show_install_open), () -> openStore(packageName, channel));
    }

    /** Amazon Appstore first, then any Play-compatible store. */
    void openStore(String packageName, String query) {
        Uri[] targets = packageName != null
                ? new Uri[]{Uri.parse("amzn://apps/android?p=" + packageName), Uri.parse("market://details?id=" + packageName)}
                : new Uri[]{Uri.parse("amzn://apps/android?s=" + Uri.encode(query)), Uri.parse("market://search?q=" + Uri.encode(query))};
        for (Uri target : targets) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException ignored) {
                // Try the next store.
            }
        }
        Toast.makeText(activity, R.string.store_unavailable, Toast.LENGTH_LONG).show();
    }
}
