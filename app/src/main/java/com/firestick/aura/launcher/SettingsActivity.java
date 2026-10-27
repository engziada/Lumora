package com.firestick.aura.launcher;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
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

/**
 * Two-pane settings screen matching the reference design: a section list on the left,
 * the selected panel on the right, with a single accent colour driving the whole UI.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int SECTION_APPEARANCE = 0;
    private static final int SECTION_WALLPAPER = 1;
    private static final int SECTION_AUDIO = 2;
    private static final int SECTION_DEBLOAT = 3;
    private static final int SECTION_SYSTEM = 4;

    private static final int[] SECTION_VIEWS = {
            R.id.secAppearance, R.id.secWallpaper, R.id.secAudio, R.id.secDebloat, R.id.secSystem
    };
    private static final int[] PANEL_VIEWS = {
            R.id.panelAppearance, R.id.panelWallpaper, R.id.panelAudio, R.id.panelDebloat, R.id.panelSystem
    };
    private static final int[] FIRST_FOCUS = {
            R.id.btnThemeViolet, R.id.btnWall30s, R.id.btnToggleSound,
            R.id.btnDisableStockLauncher, R.id.btnManageHidden
    };

    private PreferenceManager preferenceManager;
    private TextView txtStats;
    private TextView txtThemeHint;
    private TextView txtVersion;
    private Button btnWallpaperSource;
    private Button btnIconStyle;
    private Button btnToggleSound;

    private int currentSection = SECTION_APPEARANCE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        preferenceManager = new PreferenceManager(this);
        migrateLegacyAccent();
        txtStats = findViewById(R.id.txtStats);
        txtThemeHint = findViewById(R.id.txtThemeHint);
        txtVersion = findViewById(R.id.txtVersion);
        btnWallpaperSource = findViewById(R.id.btnWallpaperSource);
        btnIconStyle = findViewById(R.id.btnIconStyle);
        btnToggleSound = findViewById(R.id.btnToggleSound);

        bindSections();
        bindAppearance();
        bindWallpaper();
        bindAudio();
        bindDebloat();
        bindSystem();

        findViewById(R.id.btnDone).setOnClickListener(v -> finish());
        showSection(SECTION_APPEARANCE);

        if (txtVersion != null) {
            try {
                PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                txtVersion.setText("Z TV " + info.versionName);
            } catch (Exception ignored) {
                txtVersion.setText("Z TV");
            }
        }
    }

    /** v3 shipped a Fire Amber accent that no longer exists in the v4 palette. */
    private void migrateLegacyAccent() {
        int accent = preferenceManager.getAccentColor(
                ContextCompat.getColor(this, R.color.nova_accent));
        boolean known = accent == ContextCompat.getColor(this, R.color.nova_accent)
                || accent == ContextCompat.getColor(this, R.color.brand_z_red)
                || accent == ContextCompat.getColor(this, R.color.accent_cyan)
                || accent == ContextCompat.getColor(this, R.color.accent_emerald);
        if (!known) {
            preferenceManager.setAccentColor(ContextCompat.getColor(this, R.color.nova_accent));
        }
    }

    private void bindSections() {
        for (int i = 0; i < SECTION_VIEWS.length; i++) {
            final int section = i;
            View view = findViewById(SECTION_VIEWS[i]);
            view.setOnClickListener(v -> showSection(section));
            view.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    SoundManager.playFocus(v, preferenceManager);
                }
            });
        }
    }

    private void showSection(int section) {
        currentSection = section;
        for (int i = 0; i < PANEL_VIEWS.length; i++) {
            findViewById(PANEL_VIEWS[i]).setVisibility(i == section ? View.VISIBLE : View.GONE);
            findViewById(SECTION_VIEWS[i]).setSelected(i == section);
        }
        View focus = findViewById(FIRST_FOCUS[section]);
        if (focus != null) {
            focus.requestFocus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStats();
        updateThemeButtons();
        updateIconStyleButton();
        updateWallpaperSourceButton();
        updateSoundButton();
        for (int i = 0; i < SECTION_VIEWS.length; i++) {
            findViewById(SECTION_VIEWS[i]).setSelected(i == currentSection);
        }
    }

    // ------------------------------------------------------------- appearance

    private void bindAppearance() {
        findViewById(R.id.btnThemeViolet).setOnClickListener(v ->
                saveAccent(v, R.color.nova_accent, "Violet"));
        findViewById(R.id.btnThemeCrimson).setOnClickListener(v ->
                saveAccent(v, R.color.brand_z_red, "Crimson"));
        findViewById(R.id.btnThemeCyan).setOnClickListener(v ->
                saveAccent(v, R.color.accent_cyan, "Cyan"));
        findViewById(R.id.btnThemeEmerald).setOnClickListener(v ->
                saveAccent(v, R.color.accent_emerald, "Emerald"));

        btnIconStyle.setOnClickListener(v -> {
            preferenceManager.setHdIconsEnabled(!preferenceManager.isHdIconsEnabled());
            SoundManager.playClick(v, preferenceManager);
            updateIconStyleButton();
        });
    }

    private void updateIconStyleButton() {
        if (btnIconStyle == null) return;
        boolean hd = preferenceManager.isHdIconsEnabled();
        btnIconStyle.setText(hd ? "Icon Style: HD Logos (online)" : "Icon Style: Installed App Icons");
        btnIconStyle.setTextColor(ContextCompat.getColor(this,
                hd ? R.color.nova_accent : R.color.nova_text_dim));
    }

    private void saveAccent(View source, int colorResId, String name) {
        SoundManager.playClick(source, preferenceManager);
        preferenceManager.setAccentColor(ContextCompat.getColor(this, colorResId));
        updateThemeButtons();
        refreshStats();
        Toast.makeText(this, "Accent: " + name, Toast.LENGTH_SHORT).show();
    }

    private void updateThemeButtons() {
        int current = preferenceManager.getAccentColor(
                ContextCompat.getColor(this, R.color.nova_accent));

        String active = markTheme(R.id.btnThemeViolet, R.color.nova_accent, "Violet", current);
        String crimson = markTheme(R.id.btnThemeCrimson, R.color.brand_z_red, "Crimson", current);
        String cyan = markTheme(R.id.btnThemeCyan, R.color.accent_cyan, "Cyan", current);
        String emerald = markTheme(R.id.btnThemeEmerald, R.color.accent_emerald, "Emerald", current);

        if (!crimson.isEmpty()) active = crimson;
        if (!cyan.isEmpty()) active = cyan;
        if (!emerald.isEmpty()) active = emerald;
        if (active.isEmpty()) active = "Custom";

        if (txtThemeHint != null) {
            txtThemeHint.setText("Active accent: " + active
                    + ". It drives the focus ring, the active tab and the favourites row.");
        }
    }

    /** @return the label when this button is the active theme, otherwise an empty string. */
    private String markTheme(int buttonId, int colorResId, String label, int current) {
        Button button = findViewById(buttonId);
        if (button == null) return "";
        boolean active = ContextCompat.getColor(this, colorResId) == current;
        button.setText(label);
        button.setAlpha(active ? 1f : 0.55f);
        button.setTypeface(null, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        return active ? label : "";
    }
    // -------------------------------------------------------------- wallpaper

    private void bindWallpaper() {
        findViewById(R.id.btnWall30s).setOnClickListener(v -> setWallInterval(30, "30 seconds"));
        findViewById(R.id.btnWall1m).setOnClickListener(v -> setWallInterval(60, "1 minute"));
        findViewById(R.id.btnWall5m).setOnClickListener(v -> setWallInterval(300, "5 minutes"));
        findViewById(R.id.btnWallOff).setOnClickListener(v -> setWallInterval(0, "Static"));

        btnWallpaperSource.setOnClickListener(v -> {
            SoundManager.playClick(v, preferenceManager);
            boolean online = preferenceManager.isOnlineWallpapers();
            preferenceManager.setWallpaperSource(online
                    ? PreferenceManager.WALLPAPER_SOURCE_OFFLINE
                    : PreferenceManager.WALLPAPER_SOURCE_ONLINE);
            updateWallpaperSourceButton();
            Toast.makeText(this, online ? "Offline scenes: instant, zero data"
                    : "Online stills: downloaded once, then cached", Toast.LENGTH_SHORT).show();
        });
    }

    private void setWallInterval(int seconds, String label) {
        preferenceManager.setWallpaperIntervalSeconds(seconds);
        Toast.makeText(this, "Wallpapers rotate every " + label, Toast.LENGTH_SHORT).show();
    }

    private void updateWallpaperSourceButton() {
        if (btnWallpaperSource == null) return;
        boolean online = preferenceManager.isOnlineWallpapers();
        btnWallpaperSource.setText(online ? "Wallpaper Source: Online 4K Stills"
                : "Wallpaper Source: Offline Scenes");
        btnWallpaperSource.setTextColor(ContextCompat.getColor(this,
                online ? R.color.accent_cyan : R.color.accent_emerald));
    }

    // ------------------------------------------------------------------ audio

    private void bindAudio() {
        btnToggleSound.setOnClickListener(v -> {
            preferenceManager.setSoundEnabled(!preferenceManager.isSoundEnabled());
            updateSoundButton();
            SoundManager.playClick(v, preferenceManager);
        });
    }

    private void updateSoundButton() {
        if (btnToggleSound == null) return;
        boolean enabled = preferenceManager.isSoundEnabled();
        btnToggleSound.setText(enabled ? "Remote Sounds: ENABLED" : "Remote Sounds: MUTED");
        btnToggleSound.setTextColor(ContextCompat.getColor(this,
                enabled ? R.color.accent_emerald : R.color.nova_text_muted));
    }

    // ---------------------------------------------------------------- debloat

    private void bindDebloat() {
        findViewById(R.id.btnDisableStockLauncher).setOnClickListener(v ->
                DebloatManager.disableStockLauncher((success, msg) -> runOnUiThread(() ->
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show())));

        findViewById(R.id.btnEnableStockLauncher).setOnClickListener(v ->
                DebloatManager.enableStockLauncher((success, msg) -> runOnUiThread(() ->
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show())));

        findViewById(R.id.btnDebloatTelemetry).setOnClickListener(v ->
                DebloatManager.debloatTelemetry((success, msg) -> runOnUiThread(() ->
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show())));
    }

    // ----------------------------------------------------------------- system

    private void bindSystem() {
        findViewById(R.id.btnManageHidden).setOnClickListener(v -> {
            SoundManager.playClick(v, preferenceManager);
            showHiddenAppsDialog();
        });

        findViewById(R.id.btnResetHidden).setOnClickListener(v -> {
            preferenceManager.resetHiddenApps();
            refreshStats();
            Toast.makeText(this, "All hidden apps unhidden", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnSetHome).setOnClickListener(v -> {
            SoundManager.playClick(v, preferenceManager);
            openHomeSettings();
        });

        findViewById(R.id.btnOpenDeviceSettings).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Could not open settings", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshStats() {
        if (txtStats == null) return;
        int accent = preferenceManager.getAccentColor(
                ContextCompat.getColor(this, R.color.nova_accent));
        String theme = accent == ContextCompat.getColor(this, R.color.nova_accent) ? "Violet"
                : accent == ContextCompat.getColor(this, R.color.brand_z_red) ? "Crimson"
                : accent == ContextCompat.getColor(this, R.color.accent_cyan) ? "Cyan"
                : accent == ContextCompat.getColor(this, R.color.accent_emerald) ? "Emerald" : "Custom";
        txtStats.setText(String.format("Theme: %s   •   Hidden apps: %d   •   Apps launched: %d",
                theme, preferenceManager.getHiddenApps().size(), preferenceManager.getLaunchCount()));
    }

    private void showHiddenAppsDialog() {
        Set<String> hidden = preferenceManager.getHiddenApps();
        if (hidden.isEmpty()) {
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<String> packages = new ArrayList<>(hidden);
        Collections.sort(packages, (a, b) -> labelFor(a).compareToIgnoreCase(labelFor(b)));
        final String[] labels = new String[packages.size()];
        for (int i = 0; i < packages.size(); i++) {
            labels[i] = labelFor(packages.get(i));
        }

        new AlertDialog.Builder(this, R.style.Theme_AuraLauncher_Dialog)
                .setTitle("Hidden Apps (" + packages.size() + ")")
                .setItems(labels, (dialog, which) -> {
                    preferenceManager.unhideApp(packages.get(which));
                    refreshStats();
                    Toast.makeText(this, labels[which] + " restored", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private String labelFor(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return getPackageManager().getApplicationLabel(info).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "Fire OS hides the Home chooser on this build. Use: adb shell cmd package set-home-activity com.firestick.aura.launcher/.MainActivity",
                    Toast.LENGTH_LONG).show();
        }
    }
}
