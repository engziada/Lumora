package com.firestick.aura.launcher;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.manager.AppLock;
import com.firestick.aura.launcher.manager.DebloatManager;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.QuickAccess;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.manager.WallpaperCatalog;
import com.firestick.aura.launcher.manager.WallpaperManager;
import com.firestick.aura.launcher.service.QuickActionService;
import com.firestick.aura.launcher.ui.Glass;
import com.firestick.aura.launcher.ui.GlassMenu;
import com.firestick.aura.launcher.ui.LockPad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {
    private static final int[] SECTIONS = {R.id.secAppearance, R.id.secWallpaper, R.id.secAudio, R.id.secShows,
            R.id.secLock, R.id.secSystem, R.id.secAdb, R.id.secAbout};
    private static final int[] PANELS = {R.id.panelAppearance, R.id.panelWallpaper, R.id.panelAudio, R.id.panelShows,
            R.id.panelLock, R.id.panelSystem, R.id.scrollAdb, R.id.panelAbout};
    private static final int[] FIRST_FOCUS = {R.id.btnThemeAmber, R.id.btnWallpaperSource, R.id.btnToggleSound,
            R.id.btnShowsToggle, R.id.btnLockCode, R.id.btnQuickPermission, R.id.scrollAdb, R.id.btnAboutEmail};

    private static final int[] THEME_BUTTONS = {R.id.btnThemeAmber, R.id.btnThemeCopper, R.id.btnThemeRose, R.id.btnThemeMoon};
    private static final int[] THEME_COLORS = {R.color.accent_amber, R.color.accent_copper, R.color.accent_rose, R.color.accent_moonlight};
    private static final int[] THEME_NAMES = {R.string.accent_amber, R.string.accent_copper, R.string.accent_rose, R.string.accent_moonlight};

    private static final int[] WALL_BUTTONS = {R.id.btnWall30s, R.id.btnWall1m, R.id.btnWall5m, R.id.btnWallOff};
    private static final int[] WALL_SECONDS = {30, 60, 300, 0};
    private static final int[] WALL_LABELS = {R.string.wall_30s_long, R.string.wall_1m_long, R.string.wall_5m_long, 0};
    /** Labels for {@link WallpaperCatalog#ALL}, same order. */
    private static final int[] CATEGORY_NAMES = {R.string.wall_cat_nebulae, R.string.wall_cat_galaxies,
            R.string.wall_cat_night_sky, R.string.wall_cat_desert};

    /** Countries offered for the TVmaze schedules (ISO 3166-1 alpha-2). */
    private static final String[] COUNTRIES = {"US", "GB", "CA", "AU", "IE", "DE", "FR", "NL", "IN", "EG", "AE", "SA"};
    private static final String SUPPORT_EMAIL = "support@zakaa-ai.net";
    private static final String WEBSITE = "https://zakaa-ai.net";

    private PreferenceManager preferences;
    private WallpaperManager wallpapers;
    private TextView stats, themeHint;
    private Button iconStyle, wallpaperSource, wallCategories, sound, showsToggle, showsCountry;
    private Button lockCode, lockedApps, lockSettings, quickPermission, homeRedirect;
    private int currentSection;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        preferences = new PreferenceManager(this);
        wallpapers = new WallpaperManager(this, preferences, findViewById(R.id.imgSettingsBack), findViewById(R.id.imgSettingsFront));
        stats = findViewById(R.id.txtStats);
        themeHint = findViewById(R.id.txtThemeHint);
        iconStyle = findViewById(R.id.btnIconStyle);
        wallpaperSource = findViewById(R.id.btnWallpaperSource);
        wallCategories = findViewById(R.id.btnWallCategories);
        sound = findViewById(R.id.btnToggleSound);
        showsToggle = findViewById(R.id.btnShowsToggle);
        showsCountry = findViewById(R.id.btnShowsCountry);
        lockCode = findViewById(R.id.btnLockCode);
        lockedApps = findViewById(R.id.btnLockedApps);
        lockSettings = findViewById(R.id.btnLockSettings);
        quickPermission = findViewById(R.id.btnQuickPermission);
        homeRedirect = findViewById(R.id.btnHomeRedirect);
        bindSections();
        bindAppearance();
        bindWallpaper();
        bindAudio();
        bindShows();
        bindLock();
        bindSystem();
        buildAdbGuidance();
        bindAbout();
        findViewById(R.id.btnDone).setOnClickListener(v -> finish());
        showSection(0);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshAll();
        for (int i = 0; i < SECTIONS.length; i++) findViewById(SECTIONS[i]).setSelected(i == currentSection);
        wallpapers.start();
    }

    @Override protected void onPause() {
        wallpapers.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        wallpapers.shutdown();
        super.onDestroy();
    }

    private void refreshAll() {
        refreshStats();
        updateThemeButtons();
        updateIconStyleButton();
        updateWallpaperButtons();
        updateSoundButton();
        updateShowsButtons();
        updateLockButtons();
        updateSystemButtons();
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(CharSequence text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------- appearance

    private void bindAppearance() {
        for (int i = 0; i < THEME_BUTTONS.length; i++) {
            int index = i;
            Button button = findViewById(THEME_BUTTONS[i]);
            GradientDrawable swatch = new GradientDrawable();
            swatch.setShape(GradientDrawable.OVAL);
            swatch.setColor(color(THEME_COLORS[i]));
            swatch.setSize(dp(14), dp(14));
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(swatch, null, null, null);
            button.setCompoundDrawablePadding(dp(8));
            button.setOnClickListener(v -> {
                SoundManager.playClick(v, preferences);
                preferences.setAccentColor(color(THEME_COLORS[index]));
                updateThemeButtons();
                refreshStats();
                wallpapers.next();
                toast(getString(R.string.accent_saved, getString(THEME_NAMES[index])));
            });
        }
        iconStyle.setOnClickListener(v -> {
            preferences.setHdIconsEnabled(!preferences.isHdIconsEnabled());
            SoundManager.playClick(v, preferences);
            updateIconStyleButton();
        });
    }

    private String themeName() {
        int current = preferences.getAccentColor(color(R.color.nova_accent));
        for (int i = 0; i < THEME_COLORS.length; i++) if (color(THEME_COLORS[i]) == current) return getString(THEME_NAMES[i]);
        return getString(R.string.accent_custom);
    }

    private void updateThemeButtons() {
        int current = preferences.getAccentColor(color(R.color.nova_accent));
        for (int i = 0; i < THEME_BUTTONS.length; i++) {
            Button button = findViewById(THEME_BUTTONS[i]);
            boolean active = color(THEME_COLORS[i]) == current;
            button.setTextColor(color(active ? R.color.nova_text : R.color.nova_text_dim));
            button.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
            button.setSelected(active);
        }
        themeHint.setText(getString(R.string.theme_hint, themeName()));
    }

    private void updateIconStyleButton() {
        boolean hd = preferences.isHdIconsEnabled();
        iconStyle.setText(hd ? R.string.icon_style_hd : R.string.icon_style_installed);
    }

    // ----------------------------------------------------------- wallpaper

    private void bindWallpaper() {
        wallpaperSource.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            boolean online = preferences.isOnlineWallpapers();
            preferences.setWallpaperSource(online ? PreferenceManager.WALLPAPER_SOURCE_OFFLINE : PreferenceManager.WALLPAPER_SOURCE_ONLINE);
            updateWallpaperButtons();
            wallpapers.next();
        });
        wallCategories.setOnClickListener(v -> pickCategories());
        for (int i = 0; i < WALL_BUTTONS.length; i++) {
            int index = i;
            findViewById(WALL_BUTTONS[i]).setOnClickListener(v -> {
                SoundManager.playClick(v, preferences);
                preferences.setWallpaperIntervalSeconds(WALL_SECONDS[index]);
                updateWallpaperButtons();
                toast(WALL_LABELS[index] == 0 ? getString(R.string.wall_static)
                        : getString(R.string.wall_rotate, getString(WALL_LABELS[index])));
            });
        }
    }

    private void pickCategories() {
        Set<String> selected = preferences.getWallpaperCategories();
        List<GlassMenu.Item> items = new ArrayList<>();
        for (int i = 0; i < WallpaperCatalog.ALL.size(); i++) {
            items.add(new GlassMenu.Item(R.drawable.ic_sec_wallpaper, getString(CATEGORY_NAMES[i]))
                    .checked(selected.contains(WallpaperCatalog.ALL.get(i))));
        }
        GlassMenu.show(this, getString(R.string.wall_categories_label), getString(R.string.wall_categories_pick), null,
                items, true, (index, item) -> {
                    Set<String> current = preferences.getWallpaperCategories();
                    String id = WallpaperCatalog.ALL.get(index);
                    if (item.isChecked()) current.add(id);
                    else current.remove(id);
                    preferences.setWallpaperCategories(current);
                    updateWallpaperButtons();
                    wallpapers.next();
                });
    }

    private String categorySummary() {
        Set<String> selected = preferences.getWallpaperCategories();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < WallpaperCatalog.ALL.size(); i++) {
            if (selected.contains(WallpaperCatalog.ALL.get(i))) names.add(getString(CATEGORY_NAMES[i]));
        }
        return names.isEmpty() ? getString(R.string.wall_categories_none) : String.join(", ", names);
    }

    private void updateWallpaperButtons() {
        boolean online = preferences.isOnlineWallpapers();
        wallpaperSource.setText(online ? R.string.wallpaper_source_online : R.string.wallpaper_source_offline);
        wallCategories.setText(getString(R.string.wall_categories_value, categorySummary()));
        wallCategories.setEnabled(online);
        wallCategories.setAlpha(online ? 1f : .5f);
        int seconds = preferences.getWallpaperIntervalSeconds();
        for (int i = 0; i < WALL_BUTTONS.length; i++) {
            Button button = findViewById(WALL_BUTTONS[i]);
            boolean active = WALL_SECONDS[i] == seconds;
            button.setTextColor(color(active ? R.color.nova_accent : R.color.nova_text));
            button.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        }
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
        sound.setText(preferences.isSoundEnabled() ? R.string.sound_on : R.string.sound_off);
    }

    // --------------------------------------------------------------- shows

    private void bindShows() {
        showsToggle.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            preferences.setShowsEnabled(!preferences.isShowsEnabled());
            updateShowsButtons();
        });
        showsCountry.setOnClickListener(v -> {
            String current = preferences.getShowsCountry();
            List<GlassMenu.Item> items = new ArrayList<>();
            for (String code : COUNTRIES) {
                items.add(new GlassMenu.Item(R.drawable.ic_web, countryLabel(code)).checked(code.equals(current)));
            }
            GlassMenu.show(this, getString(R.string.shows_label), null, null, items, false, (index, item) -> {
                preferences.setShowsCountry(COUNTRIES[index]);
                updateShowsButtons();
            });
        });
    }

    private static String countryLabel(String code) {
        String name = new Locale.Builder().setRegion(code).build().getDisplayCountry();
        return name.isEmpty() ? code : name + " (" + code + ")";
    }

    private void updateShowsButtons() {
        boolean enabled = preferences.isShowsEnabled();
        showsToggle.setText(enabled ? R.string.shows_toggle_on : R.string.shows_toggle_off);
        showsCountry.setText(getString(R.string.shows_country, countryLabel(preferences.getShowsCountry())));
        showsCountry.setEnabled(enabled);
        showsCountry.setAlpha(enabled ? 1f : .5f);
    }

    // ---------------------------------------------------------------- lock

    private void bindLock() {
        lockCode.setOnClickListener(v -> {
            if (AppLock.hasCode(preferences)) LockPad.change(this, preferences, this::updateLockButtons);
            else LockPad.create(this, preferences, this::updateLockButtons);
        });
        lockedApps.setOnClickListener(v -> {
            if (preferences.getLockedApps().isEmpty()) {
                toast(getString(R.string.lock_none));
                return;
            }
            LockPad.verify(this, preferences, getString(R.string.lock_sub_manage), this::showLockedApps);
        });
        lockSettings.setOnClickListener(v -> {
            if (preferences.isSettingsLocked()) {
                LockPad.verify(this, preferences, getString(R.string.lock_sub_manage), () -> {
                    preferences.setSettingsLocked(false);
                    updateLockButtons();
                });
            } else if (!AppLock.hasCode(preferences)) {
                LockPad.create(this, preferences, () -> {
                    preferences.setSettingsLocked(true);
                    updateLockButtons();
                });
            } else {
                preferences.setSettingsLocked(true);
                updateLockButtons();
            }
        });
    }

    private void showLockedApps() {
        List<String> packages = new ArrayList<>(preferences.getLockedApps());
        Collections.sort(packages, (a, b) -> labelFor(a).compareToIgnoreCase(labelFor(b)));
        List<GlassMenu.Item> items = new ArrayList<>();
        for (String pkg : packages) items.add(appItem(pkg).checked(true));
        GlassMenu.show(this, getString(R.string.lock_apps_title), getString(R.string.lock_apps_sub), null, items, true,
                (index, item) -> {
                    preferences.setLocked(packages.get(index), item.isChecked());
                    updateLockButtons();
                });
    }

    private void updateLockButtons() {
        boolean hasCode = AppLock.hasCode(preferences);
        lockCode.setText(hasCode ? R.string.lock_change_code : R.string.lock_set_code);
        lockedApps.setText(getString(R.string.lock_apps_count, preferences.getLockedApps().size()));
        lockSettings.setText(preferences.isSettingsLocked() ? R.string.lock_settings_on : R.string.lock_settings_off);
    }

    // -------------------------------------------------------------- system

    private void bindSystem() {
        quickPermission.setOnClickListener(v -> {
            if (QuickActionService.isRunning()) {
                toast(getString(R.string.quick_enabled));
            } else if (QuickAccess.ensureServiceEnabled(this)) {
                toast(getString(R.string.quick_enabling));
            } else {
                QuickAccess.showSetup(this);
            }
            updateSystemButtons();
        });
        homeRedirect.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            preferences.setHomeRedirectEnabled(!preferences.isHomeRedirectEnabled());
            updateSystemButtons();
        });
        findViewById(R.id.btnSetHome).setOnClickListener(v -> Glass.info(this, getString(R.string.home_default_title),
                getString(R.string.home_default_message), null, getString(R.string.home_default_setup),
                () -> QuickAccess.showSetup(this)));
        findViewById(R.id.btnManageHidden).setOnClickListener(v -> showHiddenApps());
        findViewById(R.id.btnResetHidden).setOnClickListener(v -> {
            preferences.resetHiddenApps();
            refreshStats();
            toast(getString(R.string.hidden_reset));
        });
        findViewById(R.id.btnOpenDeviceSettings).setOnClickListener(v -> {
            QuickActionService.suppressRedirect(15_000L);
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException exception) {
                toast(getString(R.string.settings_open_failed));
            }
        });
    }

    private void updateSystemButtons() {
        int status = QuickActionService.isRunning() ? R.string.quick_permission_on
                : QuickAccess.isServiceEnabled(this) ? R.string.quick_permission_starting : R.string.quick_permission_off;
        quickPermission.setText(status);
        homeRedirect.setText(preferences.isHomeRedirectEnabled() ? R.string.home_redirect_on : R.string.home_redirect_off);
    }

    private void showHiddenApps() {
        Set<String> hidden = preferences.getHiddenApps();
        if (hidden.isEmpty()) {
            toast(getString(R.string.hidden_none));
            return;
        }
        List<String> packages = new ArrayList<>(hidden);
        Collections.sort(packages, (a, b) -> labelFor(a).compareToIgnoreCase(labelFor(b)));
        List<GlassMenu.Item> items = new ArrayList<>();
        for (String pkg : packages) items.add(appItem(pkg).checked(true));
        GlassMenu.show(this, getString(R.string.hidden_title, packages.size()), getString(R.string.hidden_sub), null, items, true,
                (index, item) -> {
                    if (item.isChecked()) preferences.hideApp(packages.get(index));
                    else preferences.unhideApp(packages.get(index));
                    refreshStats();
                });
    }

    private GlassMenu.Item appItem(String pkg) {
        try {
            Drawable icon = getPackageManager().getApplicationIcon(pkg);
            return new GlassMenu.Item(icon, labelFor(pkg));
        } catch (PackageManager.NameNotFoundException exception) {
            return new GlassMenu.Item(R.drawable.ic_act_info, pkg);
        }
    }

    private String labelFor(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return getPackageManager().getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException exception) {
            return packageName;
        }
    }

    private void refreshStats() {
        stats.setText(getString(R.string.settings_stats, themeName(),
                preferences.getHiddenApps().size(), preferences.getLaunchCount()));
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
        addCommand(list, getString(R.string.adb_quick_title), QuickAccess.grantCommand(this));
        addCommand(list, getString(R.string.adb_disable_title), DebloatManager.disableStockLauncherCommands());
        addCommand(list, getString(R.string.adb_enable_title), DebloatManager.enableStockLauncherCommands());
        addCommand(list, getString(R.string.adb_telemetry_title), DebloatManager.telemetryCommands());
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

    // --------------------------------------------------------------- about

    private void bindAbout() {
        String version;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version = info.versionName;
        } catch (PackageManager.NameNotFoundException exception) {
            version = "?";
        }
        ((TextView) findViewById(R.id.txtVersion)).setText(getString(R.string.version_label, version));
        ((TextView) findViewById(R.id.txtAboutVersion)).setText(getString(R.string.about_version, version));
        ((TextView) findViewById(R.id.txtAboutBuild)).setText(getString(R.string.about_build, getString(R.string.build_date)));
        findViewById(R.id.btnAboutEmail).setOnClickListener(v -> {
            Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + SUPPORT_EMAIL))
                    .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.about_email_subject, getString(R.string.app_name)));
            openOrExplain(mail, getString(R.string.about_email_title), SUPPORT_EMAIL);
        });
        findViewById(R.id.btnAboutWeb).setOnClickListener(v ->
                openOrExplain(new Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE)), getString(R.string.about_web_title), WEBSITE));
    }

    /** TVs often have no mail app or browser; then show the address so it can be typed elsewhere. */
    private void openOrExplain(Intent intent, String title, String value) {
        QuickActionService.suppressRedirect(15_000L);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Glass.info(this, title, getString(R.string.about_no_handler), value, null, null);
        }
    }
}
