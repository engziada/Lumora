package com.firestick.aura.launcher;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.manager.DebloatManager;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.SoundManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {
    private static final int APPEARANCE = 0, WALLPAPER = 1, AUDIO = 2, DEBLOAT = 3, SYSTEM = 4;
    private static final int[] SECTIONS = {R.id.secAppearance, R.id.secWallpaper, R.id.secAudio, R.id.secDebloat, R.id.secSystem};
    private static final int[] PANELS = {R.id.panelAppearance, R.id.panelWallpaper, R.id.panelAudio, R.id.panelDebloat, R.id.panelSystem};
    private static final int[] FIRST_FOCUS = {R.id.btnThemeViolet, R.id.btnWall30s, R.id.btnToggleSound, R.id.panelDebloat, R.id.btnManageHidden};

    private PreferenceManager preferences;
    private TextView stats, themeHint, version;
    private Button wallpaperSource, iconStyle, sound;
    private int currentSection = APPEARANCE;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_settings);
        preferences = new PreferenceManager(this); migrateLegacyAccent();
        stats = findViewById(R.id.txtStats); themeHint = findViewById(R.id.txtThemeHint); version = findViewById(R.id.txtVersion);
        wallpaperSource = findViewById(R.id.btnWallpaperSource); iconStyle = findViewById(R.id.btnIconStyle); sound = findViewById(R.id.btnToggleSound);
        bindSections(); bindAppearance(); bindWallpaper(); bindAudio(); buildAdbGuidance(); bindSystem();
        findViewById(R.id.btnDone).setOnClickListener(v -> finish()); showSection(APPEARANCE);
        try { PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0); version.setText("Z TV " + info.versionName); }
        catch (Exception ignored) { version.setText("Z TV"); }
    }

    private void migrateLegacyAccent() {
        int accent = preferences.getAccentColor(ContextCompat.getColor(this, R.color.nova_accent));
        boolean known = accent == ContextCompat.getColor(this, R.color.nova_accent)
                || accent == ContextCompat.getColor(this, R.color.brand_z_red)
                || accent == ContextCompat.getColor(this, R.color.accent_cyan)
                || accent == ContextCompat.getColor(this, R.color.accent_emerald);
        if (!known) preferences.setAccentColor(ContextCompat.getColor(this, R.color.nova_accent));
    }

    private void bindSections() {
        for (int i = 0; i < SECTIONS.length; i++) {
            int section = i; View view = findViewById(SECTIONS[i]);
            view.setOnClickListener(v -> showSection(section));
            view.setOnFocusChangeListener((v, focused) -> { if (focused) SoundManager.playFocus(v, preferences); });
        }
    }

    private void showSection(int section) {
        currentSection = section;
        for (int i = 0; i < PANELS.length; i++) {
            findViewById(PANELS[i]).setVisibility(i == section ? View.VISIBLE : View.GONE);
            findViewById(SECTIONS[i]).setSelected(i == section);
        }
        View focus = findViewById(FIRST_FOCUS[section]); if (focus != null) focus.requestFocus();
    }

    @Override protected void onResume() {
        super.onResume(); refreshStats(); updateThemeButtons(); updateIconStyleButton(); updateWallpaperSourceButton(); updateSoundButton();
        for (int i = 0; i < SECTIONS.length; i++) findViewById(SECTIONS[i]).setSelected(i == currentSection);
    }

    private void bindAppearance() {
        findViewById(R.id.btnThemeViolet).setOnClickListener(v -> saveAccent(v, R.color.nova_accent, "Violet"));
        findViewById(R.id.btnThemeCrimson).setOnClickListener(v -> saveAccent(v, R.color.brand_z_red, "Crimson"));
        findViewById(R.id.btnThemeCyan).setOnClickListener(v -> saveAccent(v, R.color.accent_cyan, "Cyan"));
        findViewById(R.id.btnThemeEmerald).setOnClickListener(v -> saveAccent(v, R.color.accent_emerald, "Emerald"));
        iconStyle.setOnClickListener(v -> { preferences.setHdIconsEnabled(!preferences.isHdIconsEnabled()); SoundManager.playClick(v, preferences); updateIconStyleButton(); });
    }

    private void saveAccent(View source, int color, String name) {
        SoundManager.playClick(source, preferences); preferences.setAccentColor(ContextCompat.getColor(this, color));
        updateThemeButtons(); refreshStats(); Toast.makeText(this, "Accent: " + name, Toast.LENGTH_SHORT).show();
    }

    private void updateIconStyleButton() {
        boolean hd = preferences.isHdIconsEnabled();
        iconStyle.setText(hd ? "Icon Style: HD Logos (online)" : "Icon Style: Installed App Icons");
        iconStyle.setTextColor(ContextCompat.getColor(this, hd ? R.color.nova_accent : R.color.nova_text_dim));
    }

    private void updateThemeButtons() {
        int current = preferences.getAccentColor(ContextCompat.getColor(this, R.color.nova_accent));
        String active = markTheme(R.id.btnThemeViolet, R.color.nova_accent, "Violet", current);
        String value = markTheme(R.id.btnThemeCrimson, R.color.brand_z_red, "Crimson", current); if (!value.isEmpty()) active = value;
        value = markTheme(R.id.btnThemeCyan, R.color.accent_cyan, "Cyan", current); if (!value.isEmpty()) active = value;
        value = markTheme(R.id.btnThemeEmerald, R.color.accent_emerald, "Emerald", current); if (!value.isEmpty()) active = value;
        themeHint.setText("Active accent: " + (active.isEmpty() ? "Custom" : active) + ". It drives the focus ring, active tab and favourites row.");
    }

    private String markTheme(int id, int color, String label, int current) {
        Button button = findViewById(id); boolean active = ContextCompat.getColor(this, color) == current;
        button.setText(label); button.setAlpha(active ? 1f : .55f); button.setTypeface(null, active ? 1 : 0); return active ? label : "";
    }

    private void bindWallpaper() {
        findViewById(R.id.btnWall30s).setOnClickListener(v -> setWallInterval(30, "30 seconds"));
        findViewById(R.id.btnWall1m).setOnClickListener(v -> setWallInterval(60, "1 minute"));
        findViewById(R.id.btnWall5m).setOnClickListener(v -> setWallInterval(300, "5 minutes"));
        findViewById(R.id.btnWallOff).setOnClickListener(v -> setWallInterval(0, "Static"));
        wallpaperSource.setOnClickListener(v -> {
            boolean online = preferences.isOnlineWallpapers();
            preferences.setWallpaperSource(online ? PreferenceManager.WALLPAPER_SOURCE_OFFLINE : PreferenceManager.WALLPAPER_SOURCE_ONLINE);
            updateWallpaperSourceButton();
        });
    }
    private void setWallInterval(int seconds, String label) { preferences.setWallpaperIntervalSeconds(seconds); Toast.makeText(this, "Wallpapers rotate every " + label, Toast.LENGTH_SHORT).show(); }
    private void updateWallpaperSourceButton() {
        boolean online = preferences.isOnlineWallpapers(); wallpaperSource.setText(online ? "Wallpaper Source: Online Stills" : "Wallpaper Source: Offline Scenes");
        wallpaperSource.setTextColor(ContextCompat.getColor(this, online ? R.color.accent_cyan : R.color.accent_emerald));
    }

    private void bindAudio() { sound.setOnClickListener(v -> { preferences.setSoundEnabled(!preferences.isSoundEnabled()); updateSoundButton(); SoundManager.playClick(v, preferences); }); }
    private void updateSoundButton() {
        boolean enabled = preferences.isSoundEnabled(); sound.setText(enabled ? "Remote Sounds: ENABLED" : "Remote Sounds: MUTED");
        sound.setTextColor(ContextCompat.getColor(this, enabled ? R.color.accent_emerald : R.color.nova_text_muted));
    }

    private void buildAdbGuidance() {
        LinearLayout panel = findViewById(R.id.panelDebloat);
        panel.removeAllViews(); panel.setFocusable(true); panel.setPadding(dp(22), dp(18), dp(22), dp(18));
        TextView warning = commandText("WARNING\nThese commands modify Fire OS packages and can make the Home screen unavailable. Run them only from an ADB-connected computer and keep the re-enable commands available.", 0xFFFF7A7A);
        panel.addView(warning);
        panel.addView(commandText("DISABLE STOCK LAUNCHER\n" + DebloatManager.disableStockLauncherCommands(), 0xFFF3F7FF));
        panel.addView(commandText("RE-ENABLE STOCK LAUNCHER\n" + DebloatManager.enableStockLauncherCommands(), 0xFFF3F7FF));
        panel.addView(commandText("REMOVE TELEMETRY FOR USER 0\n" + DebloatManager.telemetryCommands(), 0xFFD6DEEC));
    }

    private TextView commandText(String value, int color) {
        TextView text = new TextView(this); text.setText(value); text.setTextColor(color); text.setTextSize(10); text.setTextIsSelectable(true);
        text.setPadding(0, dp(8), 0, dp(8)); return text;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void bindSystem() {
        findViewById(R.id.btnManageHidden).setOnClickListener(v -> showHiddenAppsDialog());
        findViewById(R.id.btnResetHidden).setOnClickListener(v -> { preferences.resetHiddenApps(); refreshStats(); Toast.makeText(this, "All hidden apps unhidden", Toast.LENGTH_SHORT).show(); });
        findViewById(R.id.btnSetHome).setOnClickListener(v -> openHomeSettings());
        findViewById(R.id.btnOpenDeviceSettings).setOnClickListener(v -> { try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception e) { Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show(); } });
    }

    private void refreshStats() {
        int accent = preferences.getAccentColor(ContextCompat.getColor(this, R.color.nova_accent));
        String theme = accent == ContextCompat.getColor(this, R.color.nova_accent) ? "Violet"
                : accent == ContextCompat.getColor(this, R.color.brand_z_red) ? "Crimson"
                : accent == ContextCompat.getColor(this, R.color.accent_cyan) ? "Cyan"
                : accent == ContextCompat.getColor(this, R.color.accent_emerald) ? "Emerald" : "Custom";
        stats.setText(String.format("Theme: %s   •   Hidden apps: %d   •   Apps launched: %d", theme, preferences.getHiddenApps().size(), preferences.getLaunchCount()));
    }

    private void showHiddenAppsDialog() {
        Set<String> hidden = preferences.getHiddenApps();
        if (hidden.isEmpty()) { Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show(); return; }
        List<String> packages = new ArrayList<>(hidden); Collections.sort(packages, (a, b) -> labelFor(a).compareToIgnoreCase(labelFor(b)));
        String[] labels = new String[packages.size()]; for (int i = 0; i < labels.length; i++) labels[i] = labelFor(packages.get(i));
        new AlertDialog.Builder(this, R.style.Theme_AuraLauncher_Dialog).setTitle("Hidden Apps (" + packages.size() + ")")
                .setItems(labels, (dialog, which) -> { preferences.unhideApp(packages.get(which)); refreshStats(); })
                .setNegativeButton("Close", null).show();
    }

    private String labelFor(String packageName) {
        try { ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0); return getPackageManager().getApplicationLabel(info).toString(); }
        catch (Exception e) { return packageName; }
    }

    private void openHomeSettings() {
        try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        catch (Exception e) { Toast.makeText(this, "Use ADB: adb shell cmd package set-home-activity com.firestick.aura.launcher/.MainActivity", Toast.LENGTH_LONG).show(); }
    }
}
