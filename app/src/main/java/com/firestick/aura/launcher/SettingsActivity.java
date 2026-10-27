package com.firestick.aura.launcher;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
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
import com.firestick.aura.launcher.service.QuickActionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {
    private static final int[] SECTIONS = {R.id.secAppearance, R.id.secWallpaper, R.id.secAudio,
            R.id.secShows, R.id.secAdb, R.id.secSystem};
    private static final int[] PANELS = {R.id.panelAppearance, R.id.panelWallpaper, R.id.panelAudio,
            R.id.panelShows, R.id.scrollAdb, R.id.panelSystem};
    private static final int[] FIRST_FOCUS = {R.id.btnAccentAmber, R.id.btnWall30s, R.id.btnToggleSound,
            R.id.btnShowsToggle, R.id.scrollAdb, R.id.btnManageHidden};

    private static final int[] ACCENT_BUTTONS = {R.id.btnAccentAmber, R.id.btnAccentCopper, R.id.btnAccentRose, R.id.btnAccentMoon};
    private static final int[] ACCENT_COLORS = {R.color.accent_amber, R.color.accent_copper, R.color.accent_rose, R.color.accent_moonlight};
    private static final int[] ACCENT_NAMES = {R.string.accent_amber, R.string.accent_copper, R.string.accent_rose, R.string.accent_moonlight};

    private static final int[] WALL_BUTTONS = {R.id.btnWall30s, R.id.btnWall1m, R.id.btnWall5m, R.id.btnWallOff};
    private static final int[] WALL_SECONDS = {30, 60, 300, 0};
    private static final int[] WALL_LABELS = {R.string.wall_30s_long, R.string.wall_1m_long, R.string.wall_5m_long, 0};

    /** Countries offered for the TVmaze broadcast schedule (ISO 3166-1 alpha-2). */
    private static final String[] COUNTRIES = {"US", "GB", "CA", "AU", "IE", "DE", "FR", "NL", "IN", "EG", "AE", "SA"};

    private PreferenceManager preferences;
    private TextView stats, themeHint;
    private Button wallpaperSource, iconStyle, sound, showsToggle, showsCountry, quickPermission;
    private int currentSection;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        preferences = new PreferenceManager(this);
        stats = findViewById(R.id.txtStats);
        themeHint = findViewById(R.id.txtThemeHint);
        wallpaperSource = findViewById(R.id.btnWallpaperSource);
        iconStyle = findViewById(R.id.btnIconStyle);
        sound = findViewById(R.id.btnToggleSound);
        showsToggle = findViewById(R.id.btnShowsToggle);
        showsCountry = findViewById(R.id.btnShowsCountry);
        quickPermission = findViewById(R.id.btnQuickPermission);
        bindSections();
        bindAppearance();
        bindWallpaper();
        bindAudio();
        bindShows();
        buildAdbGuidance();
        bindSystem();
        findViewById(R.id.btnDone).setOnClickListener(v -> finish());
        TextView version = findViewById(R.id.txtVersion);
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version.setText(getString(R.string.version_label, info.versionName));
        } catch (PackageManager.NameNotFoundException exception) {
            version.setText(R.string.app_name);
        }
        showSection(0);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStats();
        updateAccentButtons();
        updateIconStyleButton();
        updateWallpaperButtons();
        updateSoundButton();
        updateShowsButtons();
        updateQuickPermissionButton();
        for (int i = 0; i < SECTIONS.length; i++) findViewById(SECTIONS[i]).setSelected(i == currentSection);
    }

    private void bindSections() {
        for (int i = 0; i < SECTIONS.length; i++) {
            int section = i;
            View view = findViewById(SECTIONS[i]);
            view.setOnClickListener(v -> showSection(section));
            view.setOnFocusChangeListener((v, focused) -> {
                if (focused) SoundManager.playFocus(v, preferences);
            });
        }
    }

    private void showSection(int section) {
        currentSection = section;
        for (int i = 0; i < PANELS.length; i++) {
            findViewById(PANELS[i]).setVisibility(i == section ? View.VISIBLE : View.GONE);
            findViewById(SECTIONS[i]).setSelected(i == section);
        }
        View focus = findViewById(FIRST_FOCUS[section]);
        if (focus != null) focus.requestFocus();
    }

    private int color(int res) {
        return ContextCompat.getColor(this, res);
    }

    // ---------------------------------------------------------- appearance

    private void bindAppearance() {
        for (int i = 0; i < ACCENT_BUTTONS.length; i++) {
            int index = i;
            findViewById(ACCENT_BUTTONS[i]).setOnClickListener(v -> {
                SoundManager.playClick(v, preferences);
                preferences.setAccentColor(color(ACCENT_COLORS[index]));
                updateAccentButtons();
                refreshStats();
                Toast.makeText(this, getString(R.string.accent_saved, getString(ACCENT_NAMES[index])), Toast.LENGTH_SHORT).show();
            });
        }
        iconStyle.setOnClickListener(v -> {
            preferences.setHdIconsEnabled(!preferences.isHdIconsEnabled());
            SoundManager.playClick(v, preferences);
            updateIconStyleButton();
        });
    }

    private String accentName() {
        int current = preferences.getAccentColor(color(R.color.nova_accent));
        for (int i = 0; i < ACCENT_COLORS.length; i++) if (color(ACCENT_COLORS[i]) == current) return getString(ACCENT_NAMES[i]);
        return getString(R.string.accent_custom);
    }

    private void updateAccentButtons() {
        int current = preferences.getAccentColor(color(R.color.nova_accent));
        for (int i = 0; i < ACCENT_BUTTONS.length; i++) {
            Button button = findViewById(ACCENT_BUTTONS[i]);
            boolean active = color(ACCENT_COLORS[i]) == current;
            button.setAlpha(active ? 1f : .6f);
            button.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
        themeHint.setText(getString(R.string.accent_hint, accentName()));
    }

    private void updateIconStyleButton() {
        boolean hd = preferences.isHdIconsEnabled();
        iconStyle.setText(hd ? R.string.icon_style_hd : R.string.icon_style_installed);
        iconStyle.setTextColor(color(hd ? R.color.nova_accent : R.color.nova_text_dim));
    }

    // ----------------------------------------------------------- wallpaper

    private void bindWallpaper() {
        for (int i = 0; i < WALL_BUTTONS.length; i++) {
            int index = i;
            findViewById(WALL_BUTTONS[i]).setOnClickListener(v -> {
                SoundManager.playClick(v, preferences);
                preferences.setWallpaperIntervalSeconds(WALL_SECONDS[index]);
                updateWallpaperButtons();
                String message = WALL_LABELS[index] == 0 ? getString(R.string.wall_static)
                        : getString(R.string.wall_rotate, getString(WALL_LABELS[index]));
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            });
        }
        wallpaperSource.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            boolean online = preferences.isOnlineWallpapers();
            preferences.setWallpaperSource(online ? PreferenceManager.WALLPAPER_SOURCE_OFFLINE : PreferenceManager.WALLPAPER_SOURCE_ONLINE);
            updateWallpaperButtons();
        });
    }

    private void updateWallpaperButtons() {
        int seconds = preferences.getWallpaperIntervalSeconds();
        for (int i = 0; i < WALL_BUTTONS.length; i++) {
            Button button = findViewById(WALL_BUTTONS[i]);
            boolean active = WALL_SECONDS[i] == seconds;
            button.setTextColor(color(active ? R.color.nova_accent : R.color.nova_text));
            button.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
        boolean online = preferences.isOnlineWallpapers();
        wallpaperSource.setText(online ? R.string.wallpaper_source_online : R.string.wallpaper_source_offline);
        wallpaperSource.setTextColor(color(online ? R.color.nova_accent : R.color.nova_text));
    }

    // --------------------------------------------------------------- audio

    private void bindAudio() {
        sound.setOnClickListener(v -> {
            preferences.setSoundEnabled(!preferences.isSoundEnabled());
            updateSoundButton();
            SoundManager.playClick(v, preferences);
        });
    }

    private void updateSoundButton() {
        boolean enabled = preferences.isSoundEnabled();
        sound.setText(enabled ? R.string.sound_on : R.string.sound_off);
        sound.setTextColor(color(enabled ? R.color.nova_accent : R.color.nova_text_muted));
    }

    // --------------------------------------------------------------- shows

    private void bindShows() {
        showsToggle.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            preferences.setShowsEnabled(!preferences.isShowsEnabled());
            updateShowsButtons();
        });
        showsCountry.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            String current = preferences.getShowsCountry();
            String[] labels = new String[COUNTRIES.length];
            int checked = 0;
            for (int i = 0; i < COUNTRIES.length; i++) {
                labels[i] = countryLabel(COUNTRIES[i]);
                if (COUNTRIES[i].equals(current)) checked = i;
            }
            new AlertDialog.Builder(this, R.style.Theme_AuraLauncher_Dialog)
                    .setTitle(R.string.shows_label)
                    .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                        preferences.setShowsCountry(COUNTRIES[which]);
                        updateShowsButtons();
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.close, null)
                    .show();
        });
    }

    private static String countryLabel(String code) {
        String name = new Locale.Builder().setRegion(code).build().getDisplayCountry();
        return name.isEmpty() ? code : name + " (" + code + ")";
    }

    private void updateShowsButtons() {
        boolean enabled = preferences.isShowsEnabled();
        showsToggle.setText(enabled ? R.string.shows_toggle_on : R.string.shows_toggle_off);
        showsToggle.setTextColor(color(enabled ? R.color.nova_accent : R.color.nova_text_muted));
        showsCountry.setText(getString(R.string.shows_country, countryLabel(preferences.getShowsCountry())));
        showsCountry.setEnabled(enabled);
        showsCountry.setAlpha(enabled ? 1f : .5f);
    }

    // ----------------------------------------------------------------- adb

    /** Read-only command reference: these need ADB, the launcher cannot run them itself. */
    private void buildAdbGuidance() {
        LinearLayout list = findViewById(R.id.adbCommands);
        list.removeAllViews();
        TextView warning = new TextView(this);
        warning.setText(R.string.adb_warning);
        warning.setTextColor(color(R.color.state_error));
        warning.setTextSize(10);
        warning.setLineSpacing(0f, 1.2f);
        list.addView(warning);
        addCommand(list, getString(R.string.adb_disable_title), DebloatManager.disableStockLauncherCommands());
        addCommand(list, getString(R.string.adb_enable_title), DebloatManager.enableStockLauncherCommands());
        addCommand(list, getString(R.string.adb_telemetry_title), DebloatManager.telemetryCommands());
        addCommand(list, getString(R.string.a11y_service_label), QuickMenu.adbEnableCommand(this));
    }

    private void addCommand(LinearLayout list, String title, String commands) {
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setAllCaps(true);
        heading.setLetterSpacing(.18f);
        heading.setTextColor(color(R.color.nova_text_muted));
        heading.setTextSize(8);
        heading.setPadding(0, dp(16), 0, dp(4));
        list.addView(heading);
        TextView body = new TextView(this);
        body.setText(commands);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextColor(color(R.color.nova_text));
        body.setTextSize(9);
        body.setTextIsSelectable(true);
        list.addView(body);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // -------------------------------------------------------------- system

    private void bindSystem() {
        findViewById(R.id.btnManageHidden).setOnClickListener(v -> showHiddenAppsDialog());
        findViewById(R.id.btnResetHidden).setOnClickListener(v -> {
            preferences.resetHiddenApps();
            refreshStats();
            Toast.makeText(this, R.string.hidden_reset, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnSetHome).setOnClickListener(v -> openHomeSettings());
        findViewById(R.id.btnOpenDeviceSettings).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException exception) {
                Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_SHORT).show();
            }
        });
        quickPermission.setOnClickListener(v -> QuickMenu.showAccessibilityPrompt(this));
        quickPermission.setOnLongClickListener(v -> {
            QuickMenu.showAdbFallback(this);
            return true;
        });
    }

    private void updateQuickPermissionButton() {
        boolean on = QuickActionService.isRunning();
        quickPermission.setText(on ? R.string.quick_permission_on : R.string.quick_permission_off);
        quickPermission.setTextColor(color(on ? R.color.state_success : R.color.nova_accent));
    }

    private void refreshStats() {
        stats.setText(getString(R.string.settings_stats, accentName(),
                preferences.getHiddenApps().size(), preferences.getLaunchCount()));
    }

    private void showHiddenAppsDialog() {
        Set<String> hidden = preferences.getHiddenApps();
        if (hidden.isEmpty()) {
            Toast.makeText(this, R.string.hidden_none, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> packages = new ArrayList<>(hidden);
        Collections.sort(packages, (a, b) -> labelFor(a).compareToIgnoreCase(labelFor(b)));
        String[] labels = new String[packages.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = labelFor(packages.get(i));
        new AlertDialog.Builder(this, R.style.Theme_AuraLauncher_Dialog)
                .setTitle(getString(R.string.hidden_title, packages.size()))
                .setItems(labels, (dialog, which) -> {
                    preferences.unhideApp(packages.get(which));
                    refreshStats();
                    Toast.makeText(this, getString(R.string.hidden_restored, labels[which]), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private String labelFor(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return getPackageManager().getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException exception) {
            return packageName;
        }
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.set_home_adb, Toast.LENGTH_LONG).show();
        }
    }
}
