package com.firestick.aura.launcher.manager;

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

/** Opens an installed app on a search: its search activity, then its web search link, then a plain launch. */
public final class AppSearch {
    public static final int SEARCHED = 0, OPENED_ONLY = 1, NOT_INSTALLED = 2;

    private AppSearch() {
    }

    public static Intent launchIntent(PackageManager pm, String packageName) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(packageName);
        return intent != null ? intent : pm.getLaunchIntentForPackage(packageName);
    }

    /** Call from the main thread; activities start in a new task, so any context works. */
    public static int start(Context context, String packageName, String query) {
        PackageManager pm = context.getPackageManager();
        Intent launch = launchIntent(pm, packageName);
        if (launch == null) return NOT_INSTALLED;
        Intent search = new Intent(Intent.ACTION_SEARCH).setPackage(packageName)
                .putExtra(SearchManager.QUERY, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (search.resolveActivity(pm) != null && tryStart(context, search)) return SEARCHED;
        Intent web = webSearch(packageName, query);
        if (web != null && tryStart(context, web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return SEARCHED;
        return tryStart(context, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) ? OPENED_ONLY : NOT_INSTALLED;
    }

    private static Intent webSearch(String packageName, String query) {
        String encoded = Uri.encode(query);
        if (packageName.contains("youtube")) {
            return new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + encoded))
                    .setPackage(packageName);
        }
        if (packageName.equals("com.netflix.ninja")) {
            return new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.netflix.com/search?q=" + encoded)).setPackage(packageName);
        }
        return null;
    }

    private static boolean tryStart(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException exception) {
            return false;
        }
    }
}
