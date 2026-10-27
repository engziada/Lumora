package com.firestick.aura.launcher;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.service.QuickActionService;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/** Quick menu: recent apps, memory, screenshot, sleep, power, Wi-Fi and Bluetooth. */
final class QuickMenu {
    interface Launcher {
        void launch(String packageName);
    }

    private static final long ACTION_DELAY_MS = 350L;
    private static final long MEMORY_SETTLE_MS = 800L;

    private final Activity activity;
    private final PreferenceManager preferences;
    private final Launcher launcher;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AlertDialog dialog;

    QuickMenu(Activity activity, PreferenceManager preferences, Launcher launcher) {
        this.activity = activity;
        this.preferences = preferences;
        this.launcher = launcher;
    }

    void show() {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_quick_menu, null);
        LinearLayout row1 = view.findViewById(R.id.quickRow1);
        LinearLayout row2 = view.findViewById(R.id.quickRow2);
        View first = addTile(row1, R.drawable.ic_qa_recent, R.string.qa_recent, this::showRecent);
        addTile(row1, R.drawable.ic_qa_memory, R.string.qa_memory, this::clearMemory);
        addTile(row1, R.drawable.ic_qa_screenshot, R.string.qa_screenshot,
                () -> runGlobal(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, true, 0));
        addTile(row1, R.drawable.ic_qa_sleep, R.string.qa_sleep,
                () -> runGlobal(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, true, 0));
        // Restart and Power off need a signature permission; both open the system power dialog.
        addTile(row2, R.drawable.ic_qa_restart, R.string.qa_restart,
                () -> runGlobal(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, false, R.string.qa_power_hint));
        addTile(row2, R.drawable.ic_qa_power, R.string.qa_power,
                () -> runGlobal(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG, false, R.string.qa_power_hint));
        addTile(row2, R.drawable.ic_qa_wifi, R.string.qa_wifi, () -> openSettings(Settings.ACTION_WIFI_SETTINGS));
        addTile(row2, R.drawable.ic_qa_bluetooth, R.string.qa_bluetooth, () -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS));
        view.findViewById(R.id.txtQuickHint).setVisibility(QuickActionService.isRunning() ? View.GONE : View.VISIBLE);

        dialog = new AlertDialog.Builder(activity, R.style.Theme_AuraLauncher_Dialog).setView(view).create();
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(0));
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
            v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(120).start();
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

    // ------------------------------------------------------------------ recent

    private void showRecent() {
        PackageManager pm = activity.getPackageManager();
        List<String> packages = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (String pkg : preferences.getRecentApps()) {
            if (pm.getLeanbackLaunchIntentForPackage(pkg) == null && pm.getLaunchIntentForPackage(pkg) == null) continue;
            try {
                labels.add(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString());
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
        new AlertDialog.Builder(activity, R.style.Theme_AuraLauncher_Dialog)
                .setTitle(R.string.qa_recent)
                .setItems(labels.toArray(new String[0]), (d, which) -> launcher.launch(packages.get(which)))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    // ------------------------------------------------------------------ memory

    /**
     * Asks the system to kill the cached processes of every launchable app. Android only kills
     * processes that are in the background, so the foreground app (Lumora) is unaffected.
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

    // ------------------------------------------------------------ global actions

    private void runGlobal(int action, boolean needsPie, int successHint) {
        if (needsPie && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            toast(R.string.qa_unsupported);
            return;
        }
        dismiss();
        if (!QuickActionService.isRunning()) {
            showAccessibilityPrompt(activity);
            return;
        }
        // Let the menu close first so it is not captured in a screenshot.
        main.postDelayed(() -> {
            if (!QuickActionService.perform(action)) toast(R.string.qa_open_failed);
            else if (successHint != 0) toast(successHint);
        }, ACTION_DELAY_MS);
    }

    private void openSettings(String action) {
        dismiss();
        try {
            activity.startActivity(new Intent(action));
        } catch (ActivityNotFoundException missing) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException alsoMissing) {
                toast(R.string.settings_open_failed);
            }
        }
    }

    // ------------------------------------------------------------- permission

    /** Explains the accessibility permission and opens the system screen to grant it. */
    static void showAccessibilityPrompt(Activity activity) {
        new AlertDialog.Builder(activity, R.style.Theme_AuraLauncher_Dialog)
                .setTitle(R.string.perm_title)
                .setMessage(R.string.perm_message)
                .setPositiveButton(R.string.perm_open, (d, which) -> openAccessibilitySettings(activity))
                .setNegativeButton(R.string.perm_cancel, null)
                .show();
    }

    static void openAccessibilitySettings(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException missing) {
            showAdbFallback(activity);
        }
    }

    static void showAdbFallback(Activity activity) {
        new AlertDialog.Builder(activity, R.style.Theme_AuraLauncher_Dialog)
                .setTitle(R.string.perm_adb_title)
                .setMessage(activity.getString(R.string.perm_adb_message, adbEnableCommand(activity)))
                .setPositiveButton(R.string.close, null)
                .show();
    }

    static String adbEnableCommand(Context context) {
        String component = context.getPackageName() + "/" + QuickActionService.class.getName();
        return "adb shell settings put secure enabled_accessibility_services " + component
                + "\nadb shell settings put secure accessibility_enabled 1";
    }
}
