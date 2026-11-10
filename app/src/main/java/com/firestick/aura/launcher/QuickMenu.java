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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.GridLayout;
import android.Manifest;
import androidx.core.app.ActivityCompat;
import com.firestick.aura.launcher.ui.PaneFocus;
import com.firestick.aura.launcher.manager.DownloadSpeedProbe;
import android.widget.Button;
import android.widget.ProgressBar;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import com.firestick.aura.launcher.manager.DeviceStats;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.QuickAccess;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.service.QuickActionService;
import com.firestick.aura.launcher.ui.Glass;
import com.firestick.aura.launcher.ui.LockPad;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;

/** Focus-driven quick menu with an animated detail pane and explicit actions. */
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
    private LinearLayout detail;
    private int selected;
    private boolean clearing;
    static final int WIFI_PERMISSION_REQUEST = 604;
    private final java.util.List<View> rail = new java.util.ArrayList<>();
    private DownloadSpeedProbe speedProbe;
    private TextView speedText;
    private Button speedButton;
    private boolean permissionDenied;



    QuickMenu(Activity activity, PreferenceManager preferences, Launcher launcher) {
        this.activity = activity;
        this.preferences = preferences;
        this.launcher = launcher;
    }

    void show() {
        if (dialog != null && dialog.isShowing()) return;
        dialog = Glass.overlay(activity, R.layout.dialog_quick_menu, Gravity.TOP | Gravity.END);
        LinearLayout tiles = dialog.findViewById(R.id.quickTiles);
        detail = dialog.findViewById(R.id.quickDetail);
        selected = 0;
        rail.clear();
        View first = addTile(tiles, R.drawable.ic_qa_recent, R.string.qa_recent, 0);
        addTile(tiles, R.drawable.ic_qa_memory, R.string.qa_memory, 1);
        addTile(tiles, R.drawable.ic_qa_screenshot, R.string.qa_screenshot, 2);
        addTile(tiles, R.drawable.ic_qa_restart, R.string.qa_power, 3);
        addTile(tiles, R.drawable.ic_qa_wifi, R.string.qa_wifi, 4);

        boolean ready = QuickActionService.isRunning();
        ((TextView) dialog.findViewById(R.id.txtQuickStatus)).setText(ready ? R.string.quick_status_ready : R.string.quick_status_setup);
        View setup = dialog.findViewById(R.id.btnQuickSetup);
        setup.setVisibility(ready ? View.GONE : View.VISIBLE);
        if (!ready) rail.add(setup);
        setup.setOnClickListener(v -> {
            dismiss();
            QuickAccess.showSetup(activity);
        });
        dialog.setOnCancelListener(window -> cancelSpeedTest());
        dialog.setOnKeyListener((window, code, event) -> {
            View focused = dialog.getCurrentFocus();
            int index = rail.indexOf(focused);
            boolean inDetail = PaneFocus.contains(detail, focused);
            boolean arrow = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
            if (!arrow || (index < 0 && !inDetail)) return false;
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
            if (index >= 0) {
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) focusDetail();
                else if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    rail.get(Math.max(0, Math.min(rail.size() - 1, index + (code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1)))).requestFocus();
                }
            } else if (code == KeyEvent.KEYCODE_DPAD_LEFT) rail.get(selected).requestFocus();
            else if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                PaneFocus.vertical(detail, focused, code == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN);
            }
            return true;
        });
        dialog.show();
        View shell = dialog.findViewById(R.id.quickShell);
        shell.setAlpha(0f);
        shell.setTranslationX(dp(28));
        shell.animate().alpha(1f).translationX(0f).setDuration(preferences.reducedMotion() ? 0 : 220).start();
        first.requestFocus();
    }

    private View addTile(LinearLayout row, int icon, int label, int section) {
        View tile = LayoutInflater.from(activity).inflate(R.layout.item_quick_tile, row, false);
        ImageView image = tile.findViewById(R.id.quickIcon);
        image.setImageResource(icon);
        int accent = preferences.getAccentColor(ContextCompat.getColor(activity, R.color.nova_accent));
        image.setImageTintList(ColorStateList.valueOf(accent));
        ((TextView) tile.findViewById(R.id.quickLabel)).setText(label);
        tile.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.02f : 1f).scaleY(focused ? 1.02f : 1f).setDuration(preferences.reducedMotion() ? 0 : 120).start();
            if (focused) {
                SoundManager.playFocus(v, preferences);
                if (selected != section || detail.getChildCount() == 0) showDetail(section, true);
            }
        });
        tile.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            if (selected != section || detail.getChildCount() == 0) showDetail(section, true);
        });
        tile.setOnKeyListener((v, code, event) -> {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) focusDetail();
                return true;
            }
            return false;
        });
        rail.add(tile);
        row.addView(tile);
        return tile;
    }

    void dismiss() {
        cancelSpeedTest();
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    private void toast(int text) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private void focusDetail() {
        for (View child : detail.getFocusables(View.FOCUS_FORWARD)) {
            if (child.isShown() && child.isEnabled()) { child.requestFocus(); return; }
        }
    }

    private void heading(int title, int subtitle) {
        TextView text = new TextView(activity);
        text.setText(title); text.setTextSize(21); text.setTextColor(activity.getColor(R.color.nova_text));
        text.setTypeface(null, android.graphics.Typeface.BOLD);
        detail.addView(text);
        note(activity.getString(subtitle));
    }

    private void note(String value) {
        TextView text = new TextView(activity);
        text.setText(value); text.setTextSize(11); text.setTextColor(activity.getColor(R.color.nova_text_dim));
        text.setPadding(0, dp(8), 0, dp(16));
        detail.addView(text);
    }

    private Button action(String label, Runnable run) {
        Button button = new Button(activity);
        button.setText(label); button.setTextSize(12); button.setAllCaps(false);
        button.setTextColor(activity.getColor(R.color.nova_text));
        button.setBackgroundResource(R.drawable.bg_quick_tile);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        params.bottomMargin = dp(8);
        detail.addView(button, params);
        button.setOnFocusChangeListener((v, focus) -> { if (focus) SoundManager.playFocus(v, preferences); });
        button.setOnClickListener(v -> { SoundManager.playClick(v, preferences); run.run(); });
        return button;
    }

    private void showDetail(int section, boolean animate) {
        if (section != selected) cancelSpeedTest();
        selected = section;
        detail.animate().cancel();
        detail.removeAllViews();
        if (section == 0) {
            heading(R.string.qa_recent, R.string.qa_recent_sub);
            PackageManager pm = activity.getPackageManager();
            int count = 0;
            GridLayout apps = new GridLayout(activity);
            apps.setColumnCount(3);
            detail.addView(apps);
            for (String pkg : preferences.getRecentApps()) {
                if (pm.getLeanbackLaunchIntentForPackage(pkg) == null && pm.getLaunchIntentForPackage(pkg) == null) continue;
                try {
                    String label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                    LinearLayout tile = new LinearLayout(activity);
                    tile.setOrientation(LinearLayout.VERTICAL);
                    tile.setGravity(Gravity.CENTER);
                    tile.setPadding(dp(5), dp(8), dp(5), dp(8));
                    tile.setBackgroundResource(R.drawable.bg_quick_tile);
                    tile.setFocusable(true); tile.setClickable(true);
                    tile.setContentDescription(label + (preferences.isLocked(pkg) ? activity.getString(R.string.qa_recent_locked) : ""));
                    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                    params.width = dp(113); params.height = dp(118); params.setMargins(dp(3), dp(3), dp(3), dp(3));
                    apps.addView(tile, params);
                    ImageView icon = new ImageView(activity);
                    icon.setImageDrawable(pm.getApplicationIcon(pkg));
                    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    tile.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));
                    TextView name = new TextView(activity);
                    name.setText(label); name.setTextColor(activity.getColor(R.color.nova_text));
                    name.setTextSize(10); name.setGravity(Gravity.CENTER); name.setMaxLines(2);
                    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    name.setPadding(0, dp(6), 0, 0); tile.addView(name);
                    tile.setOnFocusChangeListener((v, focus) -> { if (focus) SoundManager.playFocus(v, preferences); });
                    tile.setOnClickListener(v -> { dismiss(); launcher.launch(pkg); });
                    if (++count == 6) break;
                } catch (PackageManager.NameNotFoundException ignored) { }
            }
            if (count == 0) note(activity.getString(R.string.qa_recent_empty));
        } else if (section == 1) {
            heading(R.string.qa_memory, R.string.qa_memory_sub);
            ActivityManager manager = activity.getSystemService(ActivityManager.class);
            if (manager != null) {
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                manager.getMemoryInfo(info);
                note(activity.getString(R.string.qa_memory_stats, DeviceStats.formatBytes(info.totalMem),
                        DeviceStats.formatBytes(Math.max(0, info.totalMem - info.availMem)), DeviceStats.formatBytes(info.availMem)));
                ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
                bar.setProgress(DeviceStats.percent(info.totalMem - info.availMem, info.totalMem));
                bar.setProgressTintList(ColorStateList.valueOf(preferences.getAccentColor(activity.getColor(R.color.nova_accent))));
                detail.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
                note(activity.getString(R.string.qa_memory_note));
                action(activity.getString(clearing ? R.string.qa_memory_running : R.string.qa_clear_now), this::authorizeClearMemory);
                detail.getChildAt(detail.getChildCount() - 1).setEnabled(!clearing);
            }
        } else if (section == 2) {
            heading(R.string.qa_screenshot, R.string.qa_capture_sub);
            note(activity.getString(R.string.qa_capture_note));
            action(activity.getString(R.string.qa_capture_now), this::screenshot);
            action(activity.getString(R.string.qa_capture_timer), this::timedScreenshot);
        } else if (section == 3) {
            heading(R.string.qa_power, R.string.qa_power_sub);
            note(activity.getString(R.string.qa_power_system));
            action(activity.getString(R.string.qa_restart), () -> power(R.string.qa_restart));
            action(activity.getString(R.string.qa_power_off), () -> power(R.string.qa_power_off));
        } else {
            heading(R.string.qa_wifi, R.string.qa_wifi_sub);
            wifiStatus();
            speedText = new TextView(activity);
            speedText.setText(R.string.qa_speed_idle); speedText.setTextSize(12);
            speedText.setTextColor(activity.getColor(R.color.nova_text));
            speedText.setPadding(0, dp(8), 0, dp(8)); detail.addView(speedText);
            note(activity.getString(R.string.qa_speed_note));
            speedButton = action(activity.getString(R.string.qa_speed_start), this::startSpeedTest);
            action(activity.getString(R.string.qa_wifi_settings), this::openWifi);
        }
        detail.setAlpha(animate ? 0f : 1f);
        detail.setTranslationX(animate ? dp(16) : 0);
        if (animate) detail.animate().alpha(1f).translationX(0f).setDuration(preferences.reducedMotion() ? 0 : 180).start();
    }

    @SuppressWarnings("deprecation")
    private void wifiStatus() {
        ConnectivityManager connectivity = activity.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = connectivity == null ? null : connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            note(activity.getString(R.string.qa_wifi_disconnected)); return;
        }
        try {
            WifiManager wifi = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
            String ssid = info == null ? null : info.getSSID();
            if (ssid == null || ssid.equals("<unknown ssid>")) ssid = activity.getString(R.string.qa_wifi_private);
            else ssid = ssid.replace("\"", "");
            if (ssid.trim().isEmpty()) ssid = activity.getString(R.string.qa_wifi_private);
            note(activity.getString(R.string.qa_wifi_current, ssid));
            if (ssid.equals(activity.getString(R.string.qa_wifi_private))) {
                note(activity.getString(R.string.qa_wifi_permission_note));
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    action(activity.getString(R.string.qa_wifi_reveal), this::requestWifiName);
                } else {
                    action(activity.getString(R.string.qa_wifi_location), () -> {
                        dismiss();
                        try { activity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
                        catch (ActivityNotFoundException missing) { toast(R.string.qa_open_failed); }
                    });
                }
            }
        } catch (SecurityException denied) { note(activity.getString(R.string.qa_wifi_private)); }
    }

    private void requestWifiName() {
        if (permissionDenied && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
            dismiss();
            activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + activity.getPackageName())));
        } else ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, WIFI_PERMISSION_REQUEST);
    }

    void wifiPermissionResult() {
        permissionDenied = ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
        if (dialog != null && dialog.isShowing() && selected == 4) { showDetail(4, false); focusDetail(); }
    }

    private void cancelSpeedTest() {
        if (speedProbe != null) { speedProbe.cancel(); speedProbe = null; }
    }

    private void startSpeedTest() {
        if (speedProbe != null) { cancelSpeedTest(); speedText.setText(R.string.qa_speed_canceled); speedButton.setText(R.string.qa_speed_start); return; }
        DownloadSpeedProbe probe = new DownloadSpeedProbe(); speedProbe = probe;
        speedText.setText(R.string.qa_speed_running); speedButton.setText(R.string.qa_speed_cancel);
        java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            String result;
            try { result = activity.getString(R.string.qa_speed_result, probe.measure()); }
            catch (Exception error) { android.util.Log.w("LumoraSpeed", "Download test failed", error); result = activity.getString(R.string.qa_speed_failed); }
            final String value = result;
            main.post(() -> {
                if (speedProbe != probe) return;
                speedProbe = null;
                if (dialog != null && dialog.isShowing() && selected == 4) { speedText.setText(value); speedButton.setText(R.string.qa_speed_start); }
            });
            worker.shutdown();
        });
    }

    private void timedScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) { toast(R.string.qa_unsupported); return; }
        if (!QuickActionService.isRunning()) { runGlobal(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, 0); return; }
        dismiss();
        main.postDelayed(() -> {
            if (!QuickActionService.perform(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)) toast(R.string.qa_open_failed);
        }, 3000L);
    }

    private void power(int choice) {
        runGlobal(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
                choice == R.string.qa_restart ? R.string.qa_power_restart_hint : R.string.qa_power_off_hint);
    }

    /**
     * Asks the system to stop cached processes of every launchable app. Android only kills
     * background processes, so the foreground app (Lumora) is unaffected.
     */
    private void authorizeClearMemory() {
        if (preferences.getLockedApps().isEmpty()) {
            clearMemory();
        } else {
            dismiss();
            LockPad.verify(activity, preferences, activity.getString(R.string.lock_sub_memory), this::clearMemory);
        }
    }

    private void clearMemory() {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            toast(R.string.qa_open_failed);
            return;
        }
        if (clearing) return;
        clearing = true;
        if (dialog != null && dialog.isShowing()) showDetail(1, false);
        toast(R.string.qa_memory_running);
        Context app = activity.getApplicationContext();
        java.util.concurrent.ExecutorService worker = Executors.newSingleThreadExecutor();
        worker.execute(() -> {
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
            worker.shutdown();
            main.post(() -> {
                clearing = false;
                if (dialog != null && dialog.isShowing() && selected == 1) {
                    showDetail(1, false);
                    focusDetail();
                }
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
