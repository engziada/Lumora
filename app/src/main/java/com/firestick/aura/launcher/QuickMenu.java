package com.firestick.aura.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.QuickAccess;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.service.QuickActionService;
import com.firestick.aura.launcher.ui.Glass;
import com.firestick.aura.launcher.ui.GlassMenu;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/** Quick menu: Recent apps, Clear memory, Screenshot, Restart and Wi-Fi. */
final class QuickMenu {
    interface Launcher {
        void launch(String packageName);
    }

    private static final long ACTION_DELAY_MS = 400L;
    private static final long MEMORY_SETTLE_MS = 800L;

    private final Activity activity;
    private final PreferenceManager preferences;
    private final Launcher launcher;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Dialog dialog;

    QuickMenu(Activity activity, PreferenceManager preferences, Launcher launcher) {
        this.activity = activity;
        this.preferences = preferences;
        this.launcher = launcher;
    }

    void show() {
        dialog = Glass.overlay(activity, R.layout.dialog_quick_menu, Gravity.CENTER);
        LinearLayout tiles = dialog.findViewById(R.id.quickTiles);
        View first = addTile(tiles, R.drawable.ic_qa_recent, R.string.qa_recent, this::showRecent);
        addTile(tiles, R.drawable.ic_qa_memory, R.string.qa_memory, this::clearMemory);
        addTile(tiles, R.drawable.ic_qa_screenshot, R.string.qa_screenshot, this::screenshot);
        addTile(tiles, R.drawable.ic_qa_restart, R.string.qa_restart,
                () -> runGlobal(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, R.string.qa_power_hint));
        addTile(tiles, R.drawable.ic_qa_wifi, R.string.qa_wifi, this::openWifi);

        boolean ready = QuickActionService.isRunning();
        ((TextView) dialog.findViewById(R.id.txtQuickStatus)).setText(ready ? R.string.quick_status_ready : R.string.quick_status_setup);
        View setup = dialog.findViewById(R.id.btnQuickSetup);
        setup.setVisibility(ready ? View.GONE : View.VISIBLE);
        setup.setOnClickListener(v -> {
            dismiss();
            QuickAccess.showSetup(activity);
        });
        dialog.show();
        first.requestFocus();
    }

    private View addTile(LinearLayout row, int icon, int label, Runnable action) {
        View tile = LayoutInflater.from(activity).inflate(R.layout.item_quick_tile, row, false);
        ImageView image = tile.findViewById(R.id.quickIcon);
        image.setImageResource(icon);
        int accent = preferences.getAccentColor(ContextCompat.getColor(activity, R.color.nova_accent));
        image.setImageTintList(ColorStateList.valueOf(accent));
        ((TextView) tile.findViewById(R.id.quickLabel)).setText(label);
        tile.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(120).start();
            if (focused) SoundManager.playFocus(v, preferences);
        });
        tile.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            action.run();
        });
        row.addView(tile);
        return tile;
    }

    private void dismiss() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    private void toast(int text) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    private void showRecent() {
        PackageManager pm = activity.getPackageManager();
        List<String> packages = new ArrayList<>();
        List<GlassMenu.Item> items = new ArrayList<>();
        for (String pkg : preferences.getRecentApps()) {
            if (pm.getLeanbackLaunchIntentForPackage(pkg) == null && pm.getLaunchIntentForPackage(pkg) == null) continue;
            try {
                CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                Drawable icon = pm.getApplicationIcon(pkg);
                items.add(new GlassMenu.Item(icon, label));
                packages.add(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
                // Uninstalled since it was launched.
            }
        }
        if (packages.isEmpty()) {
            toast(R.string.qa_recent_empty);
            return;
        }
        dismiss();
        GlassMenu.show(activity, activity.getString(R.string.qa_recent), activity.getString(R.string.qa_recent_sub), null,
                items, false, (index, item) -> launcher.launch(packages.get(index)));
    }

    /**
     * Asks the system to stop cached processes of every launchable app. Android only kills
     * background processes, so the foreground app (Lumora) is unaffected.
     */
    private void clearMemory() {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            toast(R.string.qa_open_failed);
            return;
        }
        toast(R.string.qa_memory_running);
        Context app = activity.getApplicationContext();
        Executors.newSingleThreadExecutor().execute(() -> {
            long before = availableMemory(manager);
            for (String pkg : launchablePackages(app)) {
                try {
                    manager.killBackgroundProcesses(pkg);
                } catch (SecurityException ignored) {
                    // Protected package.
                }
            }
            try {
                Thread.sleep(MEMORY_SETTLE_MS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            long freedMb = Math.max(0L, availableMemory(manager) - before) / (1024L * 1024L);
            main.post(() -> {
                if (freedMb >= 1L) {
                    Toast.makeText(app, app.getString(R.string.qa_memory_done, (int) freedMb), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(app, R.string.qa_memory_none, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private static long availableMemory(ActivityManager manager) {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        return info.availMem;
    }

    private static Set<String> launchablePackages(Context context) {
        PackageManager pm = context.getPackageManager();
        Set<String> packages = new LinkedHashSet<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : pm.queryIntentActivities(intent, 0)) {
                if (info.activityInfo != null) packages.add(info.activityInfo.packageName);
            }
        }
        packages.remove(context.getPackageName());
        return packages;
    }

    private void screenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            toast(R.string.qa_unsupported);
            return;
        }
        runGlobal(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, 0);
    }

    /** Global actions need the quick-actions service; set it up on first use. */
    private void runGlobal(int action, int successHint) {
        dismiss();
        if (!QuickActionService.isRunning()) {
            if (QuickAccess.ensureServiceEnabled(activity)) toast(R.string.quick_enabling);
            else QuickAccess.showSetup(activity);
            return;
        }
        // Let the menu fade out first so it is not captured in a screenshot.
        main.postDelayed(() -> {
            if (!QuickActionService.perform(action)) toast(R.string.qa_open_failed);
            else if (successHint != 0) toast(successHint);
        }, ACTION_DELAY_MS);
    }

    private void openWifi() {
        dismiss();
        QuickActionService.suppressRedirect(10_000L);
        try {
            activity.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (ActivityNotFoundException missing) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException alsoMissing) {
                toast(R.string.settings_open_failed);
            }
        }
    }
}
