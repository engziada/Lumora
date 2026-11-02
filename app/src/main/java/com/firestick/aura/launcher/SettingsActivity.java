package com.firestick.aura.launcher;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.assistant.AssistantSettings;
import com.firestick.aura.launcher.assistant.AssistantText;
import com.firestick.aura.launcher.assistant.OpenRouterClient;
import com.firestick.aura.launcher.assistant.PhoneBridge;
import com.firestick.aura.launcher.manager.AppLock;
import com.firestick.aura.launcher.manager.DebloatManager;
import com.firestick.aura.launcher.manager.DeviceMonitor;
import com.firestick.aura.launcher.manager.DeviceStats;
import com.firestick.aura.launcher.manager.MaintenanceTools;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.QuickAccess;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.manager.WallpaperCatalog;
import com.firestick.aura.launcher.manager.WallpaperManager;
import com.firestick.aura.launcher.service.QuickActionService;
import com.firestick.aura.launcher.ui.CardEffects;
import com.firestick.aura.launcher.ui.Glass;
import com.firestick.aura.launcher.ui.GlassMenu;
import com.firestick.aura.launcher.ui.LockPad;
import com.firestick.aura.launcher.ui.OptionToggleView;
import com.firestick.aura.launcher.ui.PhonePairDialog;
import com.firestick.aura.launcher.ui.TextPrompt;
import com.firestick.aura.launcher.ui.UpdateFlow;
import com.firestick.aura.launcher.update.UpdateManager;
import com.firestick.aura.launcher.update.UpdateSchedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {
    /** Optional int extra: index of the section to open. */
    public static final String EXTRA_SECTION = "com.firestick.aura.launcher.extra.SETTINGS_SECTION";
    public static final int SECTION_ASSISTANT = 5;
    private static final int SECTION_HOME = 6;
    private static final int SECTION_TOOLS = 8;
    private static final int SECTION_DEVICE = 9;
    private static final int SECTION_ADB = 10;

    private static final int[] SECTIONS = {R.id.secAppearance, R.id.secWallpaper, R.id.secAudio, R.id.secShows,
            R.id.secLock, R.id.secAssistant, R.id.secSetup, R.id.secSystem, R.id.secTools, R.id.secDevice,
            R.id.secAdb, R.id.secAbout};
    private static final int[] PANELS = {R.id.panelAppearance, R.id.panelWallpaper, R.id.panelAudio, R.id.panelShows,
            R.id.panelLock, R.id.panelAssistant, R.id.panelSetup, R.id.panelSystem, R.id.panelTools, R.id.panelDevice,
            R.id.scrollAdb, R.id.panelAbout};
    private static final int[] FIRST_FOCUS = {R.id.btnThemeAmber, R.id.btnWallpaperSource, R.id.toggleSound,
            R.id.toggleShows, R.id.btnLockCode, R.id.btnAiKey, R.id.setupRows, R.id.btnQuickPermission, R.id.toolsRows,
            R.id.panelDevice, R.id.scrollAdb, R.id.btnAboutEmail};

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
    private static final long DEVICE_REFRESH_MS = 1000L;

    private PreferenceManager preferences;
    private AssistantSettings assistant;
    private WallpaperManager wallpapers;
    private TextView stats, themeHint;
    private OptionToggleView iconStyle, sound, showsToggle, lockSettings, homeRedirect;
    private OptionToggleView aiFloating, aiRemote, aiShareApps, aiControl;
    private Button wallpaperSource, wallCategories, showsCountry, lockCode, lockedApps, quickPermission;
    private Button aiKey, aiTest, aiRemoveKey, aiModel;
    private Button updateCheck, updateAuto, cardEffect, appPreviewBtn;
    private TextView updateStatus;
    private UpdateManager updates;
    private int currentSection;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private DeviceMonitor monitor;
    private boolean deviceRunning;
    private TextView devModel, devSystem, devFirmware, devKernel, devSecurity, devHome;
    private TextView devCpu, devClock, devTemp, devRam, devStorage, devNetwork, devDisplay,
            devUptime, devLumora;
    private ProgressBar barCpu, barRam, barStorage;
    private PhoneBridge keyPhone;
    private PhonePairDialog keyDialog;
    /** MODE_OUT link used by the ADB screen to beam commands to the phone. */
    private PhoneBridge sharePhone;
    private PhonePairDialog shareDialog;
    private Button firstAdbShare;
    private Button lastAdbShare;
    /** MODE_URL / MODE_FILES links used by the Tools screen to receive links and files. */
    private PhoneBridge urlPhone, filePhone;
    private PhonePairDialog urlDialog, fileDialog;
    /** Setup (guided health check) rows. */
    private TextView setupHome, setupQuick, setupRedirect, setupSecure, setupInstall, setupAssistant, setupAuto, setupNetwork;
    private Button setupNetworkFix;
    private View firstSetup;
    /** Tools (maintenance) rows. */
    private TextView txtCacheBreakdown, txtDoctor, txtToolUpdate;
    private Button toolChannel, toolClean, toolDoctor, toolCheck;
    private UpdateManager toolUpdates;
    private View firstTools;

    private final Runnable deviceTick = new Runnable() {
        @Override public void run() {
            if (!deviceRunning) return;
            worker.execute(() -> {
                DeviceMonitor.Snapshot snapshot = monitor.read();
                handler.post(() -> {
                    if (deviceRunning && !isFinishing() && !isDestroyed()) renderDevice(snapshot);
                });
            });
            handler.postDelayed(this, DEVICE_REFRESH_MS);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        preferences = new PreferenceManager(this);
        assistant = new AssistantSettings(this);
        monitor = new DeviceMonitor(this);
        wallpapers = new WallpaperManager(this, preferences, findViewById(R.id.imgSettingsBack), findViewById(R.id.imgSettingsFront));
        stats = findViewById(R.id.txtStats);
        themeHint = findViewById(R.id.txtThemeHint);
        iconStyle = findViewById(R.id.toggleIconStyle);
        wallpaperSource = findViewById(R.id.btnWallpaperSource);
        wallCategories = findViewById(R.id.btnWallCategories);
        sound = findViewById(R.id.toggleSound);
        showsToggle = findViewById(R.id.toggleShows);
        showsCountry = findViewById(R.id.btnShowsCountry);
        lockCode = findViewById(R.id.btnLockCode);
        lockedApps = findViewById(R.id.btnLockedApps);
        lockSettings = findViewById(R.id.toggleLockSettings);
        quickPermission = findViewById(R.id.btnQuickPermission);
        homeRedirect = findViewById(R.id.toggleHomeRedirect);
        bindSections();
        bindAppearance();
        bindWallpaper();
        bindAudio();
        bindShows();
        bindLock();
        bindAssistant();
        bindSystem();
        buildDevicePanel();
        buildAdbGuidance();
        buildSetupPanel();
        buildToolsPanel();
        bindAbout();
        findViewById(R.id.btnDone).setOnClickListener(v -> finish());
        int requested = getIntent().getIntExtra(EXTRA_SECTION, 0);
        showSection(requested >= 0 && requested < SECTIONS.length ? requested : 0);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshAll();
        for (int i = 0; i < SECTIONS.length; i++) findViewById(SECTIONS[i]).setSelected(i == currentSection);
        wallpapers.start();
        if (currentSection == SECTION_DEVICE) startDeviceUpdates();
    }

    @Override protected void onPause() {
        stopDeviceUpdates();
        wallpapers.stop();
        super.onPause();
    }

    /** The phone link closes as soon as Settings is no longer on screen. */
    @Override protected void onStop() {
        stopKeyPhone();
        stopSharePhone();
        stopUrlPhone();
        stopFilePhone();
        super.onStop();
    }

    @Override protected void onDestroy() {
        stopDeviceUpdates();
        stopKeyPhone();
        stopSharePhone();
        stopUrlPhone();
        stopFilePhone();
        worker.shutdownNow();
        wallpapers.shutdown();
        super.onDestroy();
    }

    /** Wires one graphical checkbox card to a boolean preference. */
    private OptionToggleView bindToggle(OptionToggleView toggle, boolean checked) {
        toggle.accentColor(accent());
        toggle.setCheckedState(checked);
        toggle.setOnFocusChangeListener((v, focused) -> SoundManager.playFocus(v, preferences));
        return toggle;
    }

    private void refreshAll() {
        refreshStats();
        updateThemeButtons();
        updateIconStyleButton();
        updateAppPreviewButton();
        updateCardEffectButton();
        updateWallpaperButtons();
        updateSoundButton();
        updateShowsButtons();
        updateLockButtons();
        updateAssistantButtons();
        updateSystemButtons();
        updateUpdateButtons();
        refreshSetup();
        refreshTools();
    }

    private void bindSections() {
        for (int i = 0; i < SECTIONS.length; i++) {
            int section = i;
            View view = findViewById(SECTIONS[i]);
            view.setOnClickListener(v -> {
                SoundManager.playClick(v, preferences);
                showSection(section);
            });
            view.setOnFocusChangeListener((v, focused) -> {
                if (focused) {
                    SoundManager.playFocus(v, preferences);
                    // Follow the focused rail row so the panel never shows a different section.
                    // Focus itself stays on the row; OK switches and enters the panel.
                    setSection(section);
                }
            });
            // Deterministic rail paths: DOWN walks the rail, RIGHT lands on the section's first
            // control regardless of geometry.
            view.setNextFocusRightId(FIRST_FOCUS[section]);
            if (section < SECTIONS.length - 1) view.setNextFocusDownId(SECTIONS[section + 1]);
            // The section's first control returns UP to its rail row instead of the header.
            View first = findViewById(FIRST_FOCUS[section]);
            if (first != null) first.setNextFocusUpId(SECTIONS[section]);
        }
    }

    private void showSection(int section) {
        setSection(section);
        View focus = section == SECTION_ADB && firstAdbShare != null ? firstAdbShare
                : section == SECTION_HOME && firstSetup != null ? firstSetup
                : section == SECTION_TOOLS && firstTools != null ? firstTools
                : findViewById(FIRST_FOCUS[section]);
        if (focus != null) focus.requestFocus();
    }

    /** Switches the visible panel without moving focus, so the panel matches the rail row. */
    private void setSection(int section) {
        currentSection = section;
        for (int i = 0; i < PANELS.length; i++) {
            findViewById(PANELS[i]).setVisibility(i == section ? View.VISIBLE : View.GONE);
            findViewById(SECTIONS[i]).setSelected(i == section);
        }
        if (section == SECTION_DEVICE) startDeviceUpdates();
        else stopDeviceUpdates();
        // Setup rows rebuild cheaply; the Tools cache scan is disk I/O, run only when opened.
        if (section == SECTION_HOME) refreshSetup();
        if (section == SECTION_TOOLS) scanCaches();
    }

    private int color(int res) {
        return ContextCompat.getColor(this, res);
    }

    private int accent() {
        return preferences.getAccentColor(color(R.color.nova_accent));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(CharSequence text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------- appearance

    private void bindAppearance() {
        appPreviewBtn = findViewById(R.id.btnAppPreview);
        appPreviewBtn.setOnClickListener(v -> pickPanelContent());
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
        iconStyle = findViewById(R.id.toggleIconStyle);
        bindToggle(iconStyle, preferences.isHdIconsEnabled());
        iconStyle.listener(checked -> {
            preferences.setHdIconsEnabled(checked);
            SoundManager.playClick(iconStyle, preferences);
        });
        ViewGroup appearance = findViewById(R.id.panelAppearance);
        View effectSection = getLayoutInflater().inflate(R.layout.section_card_effect, appearance, false);
        appearance.addView(effectSection);
        cardEffect = effectSection.findViewById(R.id.btnCardEffect);
        cardEffect.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            preferences.setCardEffect(CardEffects.next(preferences.getCardEffect()));
            updateCardEffectButton();
        });
    }

    /** The home panel shows generative particles (offline), public videos, or nothing. */
    private void pickPanelContent() {
        SoundManager.playClick(appPreviewBtn, preferences);
        int current = preferences.getPanelContent();
        List<GlassMenu.Item> items = new ArrayList<>();
        items.add(new GlassMenu.Item(R.drawable.ic_nav_home, getString(R.string.panel_mode_off))
                .checked(current == PreferenceManager.PANEL_CONTENT_OFF));
        items.add(new GlassMenu.Item(R.drawable.ic_palette, getString(R.string.panel_mode_particles))
                .checked(current == PreferenceManager.PANEL_CONTENT_PARTICLES));
        items.add(new GlassMenu.Item(R.drawable.ic_web, getString(R.string.panel_mode_videos))
                .checked(current == PreferenceManager.PANEL_CONTENT_VIDEOS));
        GlassMenu.show(this, getString(R.string.panel_content_title), getString(R.string.panel_content_pick),
                null, items, false, (index, item) -> {
                    int mode = index == 1 ? PreferenceManager.PANEL_CONTENT_PARTICLES
                            : index == 2 ? PreferenceManager.PANEL_CONTENT_VIDEOS
                            : PreferenceManager.PANEL_CONTENT_OFF;
                    preferences.setPanelContent(mode);
                    updateAppPreviewButton();
                });
    }

    private void updateCardEffectButton() {
        if (cardEffect == null) return;
        String effect = preferences.getCardEffect();
        cardEffect.setText(CardEffects.ARABIC.equals(effect) ? R.string.card_effect_arabic
                : CardEffects.OFF.equals(effect) ? R.string.card_effect_off : R.string.card_effect_lightning);
    }

    private void updateAppPreviewButton() {
        int mode = preferences.getPanelContent();
        appPreviewBtn.setText(getString(R.string.panel_content_value,
                getString(mode == PreferenceManager.PANEL_CONTENT_PARTICLES ? R.string.panel_mode_particles
                        : mode == PreferenceManager.PANEL_CONTENT_VIDEOS ? R.string.panel_mode_videos
                        : R.string.panel_mode_off)));
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
        bindToggle(iconStyle, hd);
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
        sound = findViewById(R.id.toggleSound);
        bindToggle(sound, preferences.isSoundEnabled());
        sound.listener(checked -> {
            preferences.setSoundEnabled(checked);
            SoundManager.playClick(sound, preferences);
        });
    }

    private void updateSoundButton() {
        bindToggle(sound, preferences.isSoundEnabled());
    }

    // --------------------------------------------------------------- shows

    private void bindShows() {
        showsToggle = findViewById(R.id.toggleShows);
        bindToggle(showsToggle, preferences.isShowsEnabled());
        showsToggle.listener(checked -> {
            preferences.setShowsEnabled(checked);
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
        bindToggle(showsToggle, enabled);
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
        lockSettings.listener(checked -> {
            if (checked) {
                if (!AppLock.hasCode(preferences)) {
                    bindToggle(lockSettings, false);
                    LockPad.create(this, preferences, () -> {
                        preferences.setSettingsLocked(true);
                        updateLockButtons();
                    });
                } else {
                    preferences.setSettingsLocked(true);
                }
            } else if (preferences.isSettingsLocked()) {
                LockPad.verify(this, preferences, getString(R.string.lock_sub_manage), () -> {
                    preferences.setSettingsLocked(false);
                    updateLockButtons();
                });
            } else {
                preferences.setSettingsLocked(false);
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
        bindToggle(lockSettings, preferences.isSettingsLocked());
    }

    // ----------------------------------------------------------- assistant

    private void bindAssistant() {
        aiKey = findViewById(R.id.btnAiKey);
        aiTest = findViewById(R.id.btnAiTest);
        aiRemoveKey = findViewById(R.id.btnAiRemoveKey);
        aiModel = findViewById(R.id.btnAiModel);
        aiFloating = findViewById(R.id.toggleAiFloating);
        aiRemote = findViewById(R.id.toggleAiRemote);
        aiShareApps = findViewById(R.id.toggleAiShareApps);
        ViewGroup assistantList = (ViewGroup) ((ViewGroup) findViewById(R.id.panelAssistant)).getChildAt(0);
        View controlSection = getLayoutInflater().inflate(R.layout.section_ai_control, assistantList, false);
        assistantList.addView(controlSection, assistantList.indexOfChild(findViewById(R.id.btnAiOpen)));
        aiControl = controlSection.findViewById(R.id.toggleAiControl);
        aiControl.listener(checked -> {
            SoundManager.playClick(aiControl, preferences);
            assistant.setControlEnabled(checked);
            updateAssistantButtons();
        });

        aiKey.setOnClickListener(v -> TextPrompt.show(this, getString(R.string.ai_key_title),
                getString(R.string.ai_key_message), null, true, this::saveKey));
        findViewById(R.id.btnAiKeyPhone).setOnClickListener(v -> receiveKeyFromPhone());
        aiTest.setOnClickListener(v -> testKey());
        aiRemoveKey.setOnClickListener(v -> {
            assistant.clearApiKey();
            updateAssistantButtons();
            toast(getString(R.string.ai_key_removed));
        });
        aiModel.setOnClickListener(v -> pickModel());
        aiFloating.listener(checked -> {
            SoundManager.playClick(aiFloating, preferences);
            assistant.setFloatingButtonEnabled(checked);
            updateAssistantButtons();
        });
        aiRemote.listener(checked -> {
            SoundManager.playClick(aiRemote, preferences);
            assistant.setRemoteButtonEnabled(checked);
            updateAssistantButtons();
            if (checked && !QuickActionService.isRunning()) {
                Glass.info(this, getString(R.string.ai_remote_title), getString(R.string.ai_remote_needs_quick), null,
                        getString(R.string.home_default_setup), () -> QuickAccess.showSetup(this));
            }
        });
        aiShareApps.listener(checked -> {
            SoundManager.playClick(aiShareApps, preferences);
            assistant.setShareAppsEnabled(checked);
            updateAssistantButtons();
        });
        findViewById(R.id.btnAiOpen).setOnClickListener(v -> startActivity(new Intent(this, AssistantActivity.class)));
        // User guide below the settings; each block is focusable so the D-pad scrolls through it.
        assistantList.addView(getLayoutInflater().inflate(R.layout.section_ai_manual, assistantList, false));
    }

    private void saveKey(String value) {
        assistant.setApiKey(value);
        updateAssistantButtons();
        if (!value.startsWith("sk-or-")) toast(getString(R.string.ai_key_format));
        testKey();
    }

    /** Opens the phone link in key mode; the first text received is saved as the key. */
    private void receiveKeyFromPhone() {
        stopKeyPhone();
        PhoneBridge bridge = new PhoneBridge(PhoneBridge.MODE_KEY, new PhoneBridge.Listener() {
            @Override public void onText(String text) {
                stopKeyPhone();
                toast(getString(R.string.phone_key_received));
                saveKey(text);
            }

            @Override public void onCodeChanged(String code) {
                if (keyDialog != null && keyDialog.isShowing()) keyDialog.refresh();
            }

            @Override public void onStopped(boolean idle) {
                stopKeyPhone();
            }
        });
        int result = bridge.start();
        if (result != PhoneBridge.START_OK) {
            Glass.info(this, getString(R.string.phone_title_key), getString(result == PhoneBridge.START_NO_NETWORK
                    ? R.string.phone_no_network : R.string.phone_start_failed), null, null, null);
            return;
        }
        keyPhone = bridge;
        keyDialog = PhonePairDialog.show(this, bridge, getString(R.string.phone_title_key),
                getString(R.string.phone_message_key), this::stopKeyPhone);
    }

    private void stopKeyPhone() {
        if (keyDialog != null) keyDialog.dismiss();
        keyDialog = null;
        if (keyPhone != null) keyPhone.stop();
        keyPhone = null;
    }

    private void pickModel() {
        String current = assistant.getModel();
        boolean isDefault = AssistantSettings.DEFAULT_MODEL.equals(current);
        List<GlassMenu.Item> items = new ArrayList<>();
        items.add(new GlassMenu.Item(R.drawable.ic_assistant, getString(R.string.ai_model_free)).checked(isDefault));
        items.add(new GlassMenu.Item(R.drawable.ic_act_open, getString(R.string.ai_model_custom)).checked(!isDefault));
        GlassMenu.show(this, getString(R.string.ai_model_label), null, null, items, false, (index, item) -> {
            if (index == 0) {
                assistant.setModel(AssistantSettings.DEFAULT_MODEL);
                updateAssistantButtons();
            } else {
                TextPrompt.show(this, getString(R.string.ai_model_custom), getString(R.string.ai_model_custom_message),
                        isDefault ? "" : current, false, value -> {
                            assistant.setModel(value);
                            updateAssistantButtons();
                        });
            }
        });
    }

    private void testKey() {
        if (!assistant.hasApiKey()) {
            toast(getString(R.string.ai_key_none));
            return;
        }
        String key = assistant.getApiKey();
        aiTest.setEnabled(false);
        aiTest.setText(R.string.ai_testing);
        worker.execute(() -> {
            OpenRouterClient.Result result = OpenRouterClient.checkKey(key);
            handler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                aiTest.setEnabled(true);
                aiTest.setText(R.string.ai_test);
                int message;
                switch (result.error) {
                    case OpenRouterClient.OK:
                        message = R.string.ai_test_ok;
                        break;
                    case OpenRouterClient.ERR_AUTH:
                        message = R.string.assistant_err_auth;
                        break;
                    case OpenRouterClient.ERR_NETWORK:
                        message = R.string.assistant_err_network;
                        break;
                    default:
                        message = R.string.assistant_err_server;
                        break;
                }
                toast(getString(message));
            });
        });
    }

    private void updateAssistantButtons() {
        boolean hasKey = assistant.hasApiKey();
        aiKey.setText(hasKey ? getString(R.string.ai_key_set, AssistantText.mask(assistant.getApiKey()))
                : getString(R.string.ai_key_unset));
        aiTest.setEnabled(hasKey);
        aiTest.setAlpha(hasKey ? 1f : .5f);
        aiRemoveKey.setEnabled(hasKey);
        aiRemoveKey.setAlpha(hasKey ? 1f : .5f);
        aiModel.setText(getString(R.string.ai_model_value, assistant.getModel()));
        bindToggle(aiFloating, assistant.isFloatingButtonEnabled());
        bindToggle(aiRemote, assistant.isRemoteButtonEnabled());
        bindToggle(aiShareApps, assistant.isShareAppsEnabled());
        bindToggle(aiControl, assistant.isControlEnabled());
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
        homeRedirect.listener(checked -> {
            SoundManager.playClick(homeRedirect, preferences);
            preferences.setHomeRedirectEnabled(checked);
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
        bindToggle(homeRedirect, preferences.isHomeRedirectEnabled());
        if (devHome != null) devHome.setText(homeSummary());
    }

    /** The home intent resolves to whoever Fire OS currently treats as the default home. */
    private boolean isDefaultHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && getPackageName().equals(resolved.activityInfo.packageName);
    }

    private CharSequence homeSummary() {
        if (isDefaultHome()) return getString(R.string.dev_home_lumora);
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolved = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolved != null && resolved.activityInfo != null) {
                return getString(R.string.dev_home_other, getPackageManager()
                        .getApplicationLabel(getPackageManager().getApplicationInfo(resolved.activityInfo.packageName, 0)));
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Fall through to the generic line.
        }
        return getString(R.string.dev_home_other, getString(R.string.dev_home_unknown));
    }

    // -------------------------------------------------- d-pad scrolling for text panels

    /**
     * Device info rows are plain text, so its panel scrolls directly if no view holds focus.
     * ADB instead enters its first real button; the explicitly chained buttons then scroll into
     * view through normal Android focus handling rather than leaving focus on the ScrollView.
     */
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && getCurrentFocus() == null
                && (currentSection == SECTION_DEVICE || currentSection == SECTION_ADB)) {
            int key = event.getKeyCode();
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                    || key == KeyEvent.KEYCODE_PAGE_UP || key == KeyEvent.KEYCODE_PAGE_DOWN) {
                if (currentSection == SECTION_ADB && firstAdbShare != null) {
                    firstAdbShare.requestFocus();
                    return true;
                }
                ScrollView scroller = findViewById(R.id.panelDevice);
                scroller.requestFocus();
                int current = scroller.getScrollY();
                int max = Math.max(0, scroller.getChildAt(0).getHeight() - scroller.getHeight());
                int step = key == KeyEvent.KEYCODE_PAGE_UP || key == KeyEvent.KEYCODE_PAGE_DOWN
                        ? scroller.getHeight() - dp(80) : dp(150);
                scroller.smoothScrollTo(0, Math.max(0, Math.min(max, current
                        + (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_PAGE_UP ? -step : step))));
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
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

    // -------------------------------------------------------------- device

    private void buildDevicePanel() {
        LinearLayout list = findViewById(R.id.deviceRows);
        list.removeAllViews();
        addEyebrow(list, R.string.dev_static_label);
        devModel = addDeviceRow(list, R.string.dev_model);
        devSystem = addDeviceRow(list, R.string.dev_system);
        devFirmware = addDeviceRow(list, R.string.dev_firmware);
        devKernel = addDeviceRow(list, R.string.dev_kernel);
        devSecurity = addDeviceRow(list, R.string.dev_security);
        devHome = addDeviceRow(list, R.string.dev_home);
        devFirmware.setText(firmwareSummary());
        devKernel.setText(kernelVersion());
        devSecurity.setText(Build.VERSION.SECURITY_PATCH == null || Build.VERSION.SECURITY_PATCH.isEmpty()
                ? getString(R.string.dev_unavailable) : Build.VERSION.SECURITY_PATCH);
        devHome.setText(homeSummary());
        addEyebrow(list, R.string.dev_live_label);
        devCpu = addDeviceRow(list, R.string.dev_cpu);
        barCpu = addBar(list);
        devClock = addDeviceRow(list, R.string.dev_clock);
        devTemp = addDeviceRow(list, R.string.dev_temperature);
        devRam = addDeviceRow(list, R.string.dev_memory);
        barRam = addBar(list);
        devStorage = addDeviceRow(list, R.string.dev_storage);
        barStorage = addBar(list);
        devNetwork = addDeviceRow(list, R.string.dev_network);
        devDisplay = addDeviceRow(list, R.string.dev_display);
        devUptime = addDeviceRow(list, R.string.dev_uptime);
        devLumora = addDeviceRow(list, R.string.dev_lumora);
    }

    private void addEyebrow(LinearLayout list, int labelRes) {
        TextView eyebrow = new TextView(this);
        eyebrow.setText(labelRes);
        eyebrow.setTextColor(color(R.color.nova_text_muted));
        eyebrow.setTextSize(8);
        eyebrow.setAllCaps(true);
        eyebrow.setLetterSpacing(.22f);
        eyebrow.setPadding(0, dp(12), 0, dp(2));
        list.addView(eyebrow);
    }

    /**
     * Fire OS version. Amazon exposes it in read-only system properties whose names vary by
     * generation; the firmware image id ({@link Build#DISPLAY}) is the fallback.
     */
    private String firmwareSummary() {
        String[] candidates = {"ro.build.version.fireos", "ro.amazon.os.version",
                "ro.amazon.fireos", "ro.fireos.version"};
        for (String name : candidates) {
            String value = systemProperty(name);
            if (value == null || value.isEmpty()) continue;
            return value.toLowerCase(Locale.US).startsWith("fire os") ? value : "Fire OS " + value;
        }
        return Build.DISPLAY;
    }

    private static String systemProperty(String name) {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            return (String) properties.getMethod("get", String.class).invoke(null, name);
        } catch (ReflectiveOperationException error) {
            return null;
        }
    }

    private String kernelVersion() {
        String version = System.getProperty("os.version");
        return version == null || version.isEmpty() ? getString(R.string.dev_unavailable) : version;
    }

    private TextView addDeviceRow(LinearLayout list, int labelRes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(7), 0, dp(3));
        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setAllCaps(true);
        label.setLetterSpacing(.14f);
        label.setTextColor(color(R.color.nova_text_muted));
        label.setTextSize(8);
        row.addView(label, new LinearLayout.LayoutParams(dp(130), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView value = new TextView(this);
        value.setTextColor(color(R.color.nova_text));
        value.setTextSize(11);
        value.setText(R.string.dev_reading);
        row.addView(value, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        list.addView(row);
        return value;
    }

    private ProgressBar addBar(LinearLayout list) {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(ColorStateList.valueOf(accent()));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(color(R.color.glass_card_stroke)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
        params.setMarginStart(dp(130));
        params.bottomMargin = dp(4);
        list.addView(bar, params);
        return bar;
    }

    private void startDeviceUpdates() {
        if (deviceRunning) return;
        deviceRunning = true;
        barCpu.setProgressTintList(ColorStateList.valueOf(accent()));
        barRam.setProgressTintList(ColorStateList.valueOf(accent()));
        barStorage.setProgressTintList(ColorStateList.valueOf(accent()));
        devDisplay.setText(displaySummary());
        handler.post(deviceTick);
    }

    private void stopDeviceUpdates() {
        deviceRunning = false;
        handler.removeCallbacks(deviceTick);
    }

    private String displaySummary() {
        Display display = getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        return String.format(Locale.US, "%d \u00D7 %d \u00B7 %.0f Hz \u00B7 %d dpi",
                metrics.widthPixels, metrics.heightPixels, display.getRefreshRate(), metrics.densityDpi);
    }

    private void renderDevice(DeviceMonitor.Snapshot s) {
        String unavailable = getString(R.string.dev_unavailable);
        devModel.setText(s.model);
        devSystem.setText(s.system);
        if (s.cpuPercent >= 0) {
            devCpu.setText(getString(R.string.dev_cpu_value, s.cpuPercent, s.cores, s.hardware));
            barCpu.setProgress(s.cpuPercent);
        } else {
            devCpu.setText(getString(R.string.dev_cpu_cores, s.cores, s.hardware));
            barCpu.setProgress(s.cpuCurMhz > 0 && s.cpuMaxMhz > 0 ? DeviceStats.percent(s.cpuCurMhz, s.cpuMaxMhz) : 0);
        }
        devClock.setText(s.cpuCurMhz > 0
                ? getString(R.string.dev_clock_value, s.cpuCurMhz, s.cpuMaxMhz > 0 ? s.cpuMaxMhz : s.cpuCurMhz)
                : unavailable);
        devTemp.setText(Float.isNaN(s.temperature) ? unavailable
                : String.format(Locale.US, "%.1f \u00B0C", s.temperature));
        long ramUsed = s.ramTotal - s.ramAvailable;
        devRam.setText(getString(s.lowMemory ? R.string.dev_memory_low : R.string.dev_memory_value,
                DeviceStats.formatBytes(ramUsed), DeviceStats.formatBytes(s.ramTotal),
                DeviceStats.formatBytes(s.ramAvailable)));
        barRam.setProgress(DeviceStats.percent(ramUsed, s.ramTotal));
        long storageUsed = s.storageTotal - s.storageFree;
        devStorage.setText(getString(R.string.dev_storage_value, DeviceStats.formatBytes(storageUsed),
                DeviceStats.formatBytes(s.storageTotal), DeviceStats.formatBytes(s.storageFree)));
        barStorage.setProgress(DeviceStats.percent(storageUsed, s.storageTotal));
        devNetwork.setText(networkSummary(s));
        devUptime.setText(DeviceStats.formatUptime(s.uptimeMs));
        devLumora.setText(DeviceStats.formatBytes(s.lumoraPssKb * 1024L));
    }

    private String networkSummary(DeviceMonitor.Snapshot s) {
        if (s.network == null || s.network.isEmpty()) return getString(R.string.dev_offline);
        StringBuilder text = new StringBuilder(s.network);
        if (s.address != null) text.append(" \u00B7 ").append(s.address);
        if (s.wifiRssi != Integer.MIN_VALUE && s.wifiRssi < 0) text.append(" \u00B7 ").append(s.wifiRssi).append(" dBm");
        if (s.wifiMbps > 0) text.append(" \u00B7 ").append(s.wifiMbps).append(" Mbps");
        return text.toString();
    }

    // ----------------------------------------------------------------- adb

    /** Read-only command reference: these need ADB, the launcher cannot run them itself. */
    private void buildAdbGuidance() {
        LinearLayout list = findViewById(R.id.adbCommands);
        list.removeAllViews();
        firstAdbShare = null;
        lastAdbShare = null;
        TextView warning = new TextView(this);
        warning.setText(R.string.adb_warning);
        warning.setTextColor(color(R.color.state_error));
        warning.setTextSize(10);
        warning.setLineSpacing(0f, 1.2f);
        list.addView(warning);
        TextView localNote = new TextView(this);
        localNote.setText(R.string.adb_local_note);
        localNote.setTextColor(color(R.color.nova_text_dim));
        localNote.setTextSize(9);
        localNote.setLineSpacing(0f, 1.2f);
        localNote.setPadding(0, dp(8), 0, 0);
        list.addView(localNote);
        // Failed setup drops Lumora from the default-home slot: offer the exact fix plus status.
        boolean defaultHome = isDefaultHome();
        String restore = "adb shell cmd package set-home-activity " + getPackageName() + "/.MainActivity";
        addCommand(list, getString(R.string.adb_home_title),
                getString(defaultHome ? R.string.adb_home_ok : R.string.adb_home_fix) + "\n" + restore,
                getString(R.string.adb_share));
        addCommand(list, getString(R.string.adb_quick_title), QuickAccess.grantCommand(this), getString(R.string.adb_share));
        addCommand(list, getString(R.string.adb_key_title), getString(R.string.adb_key_command), getString(R.string.adb_share));
        addCommand(list, getString(R.string.adb_install_title), new UpdateManager(this).grantInstallCommand(), getString(R.string.adb_share));
        addCommand(list, getString(R.string.adb_disable_title), DebloatManager.disableStockLauncherCommands(), getString(R.string.adb_share));
        addCommand(list, getString(R.string.adb_enable_title), DebloatManager.enableStockLauncherCommands(), getString(R.string.adb_share));
        addCommand(list, getString(R.string.adb_telemetry_title), DebloatManager.telemetryCommands(), getString(R.string.adb_share));
        if (firstAdbShare != null) {
            findViewById(R.id.secAdb).setNextFocusRightId(firstAdbShare.getId());
            firstAdbShare.setNextFocusUpId(R.id.secAdb);
        }
        if (lastAdbShare != null) lastAdbShare.setNextFocusDownId(lastAdbShare.getId());
    }

    /** Each command block ends with a small focused button that beams it to the paired phone. */
    private void addCommand(LinearLayout list, String title, String commands, String buttonLabel) {
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
        body.setFocusable(false); // Selectable command text must not trap TV D-pad navigation.
        list.addView(body);
        Button share = new Button(this);
        share.setId(View.generateViewId());
        share.setFocusable(true);
        share.setText(buttonLabel);
        share.setAllCaps(false);
        share.setTextColor(color(R.color.nova_accent));
        share.setTextSize(9);
        share.setMinHeight(0);
        share.setMinimumHeight(0);
        share.setBackgroundResource(R.drawable.bg_option_selector);
        share.setNextFocusLeftId(R.id.secAdb);
        if (lastAdbShare != null) {
            lastAdbShare.setNextFocusDownId(share.getId());
            share.setNextFocusUpId(lastAdbShare.getId());
        } else {
            firstAdbShare = share;
        }
        lastAdbShare = share;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
        params.topMargin = dp(6);
        share.setLayoutParams(params);
        share.setOnClickListener(v -> sendToPhone(commands));
        share.setOnFocusChangeListener((v, focused) -> SoundManager.playFocus(v, preferences));
        list.addView(share);
    }

    /** Opens the MODE_OUT phone link; the polling page shows and copies every pushed command. */
    private void startSharePhone() {
        stopSharePhone();
        PhoneBridge bridge = new PhoneBridge(PhoneBridge.MODE_OUT, new PhoneBridge.Listener() {
            @Override public void onText(String text) {
                // Commands only travel TV → phone on this link.
            }

            @Override public void onCodeChanged(String code) {
                if (shareDialog != null && shareDialog.isShowing()) shareDialog.refresh();
            }

            @Override public void onStopped(boolean idle) {
                stopSharePhone();
            }
        });
        int result = bridge.start();
        if (result != PhoneBridge.START_OK) {
            Glass.info(this, getString(R.string.phone_title_share),
                    getString(result == PhoneBridge.START_NO_NETWORK ? R.string.phone_no_network
                            : R.string.phone_start_failed), null, null, null);
            return;
        }
        sharePhone = bridge;
        shareDialog = PhonePairDialog.show(this, bridge, getString(R.string.phone_title_share),
                getString(R.string.phone_message_share), this::stopSharePhone);
    }

    private void stopSharePhone() {
        if (shareDialog != null) shareDialog.dismiss();
        shareDialog = null;
        if (sharePhone != null) sharePhone.stop();
        sharePhone = null;
    }

    /** Sends one command block to the phone, opening the link first when needed. */
    private void sendToPhone(String commands) {
        if (sharePhone == null || !sharePhone.isRunning()) {
            Glass.info(this, getString(R.string.phone_title_share), getString(R.string.phone_share_first),
                    null, getString(R.string.quick_setup_button), this::startSharePhone);
            return;
        }
        if (shareDialog == null || !shareDialog.isShowing()) {
            toast(getString(R.string.phone_share_ready));
        }
        boolean queued = sharePhone.post(commands);
        toast(getString(queued ? R.string.phone_share_sent : R.string.phone_start_failed));
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
        updates = new UpdateManager(this);
        ((TextView) findViewById(R.id.txtAboutVersion)).setText(
                getString(R.string.about_version_build, version, updates.installedVersionCode()));
        bindUpdates();
        ((TextView) findViewById(R.id.txtAboutBuild)).setText(getString(R.string.about_build, getString(R.string.build_date)));
        findViewById(R.id.btnAboutEmail).setOnClickListener(v -> {
            Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + SUPPORT_EMAIL))
                    .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.about_email_subject, getString(R.string.app_name)));
            openOrExplain(mail, getString(R.string.about_email_title), SUPPORT_EMAIL);
        });
        findViewById(R.id.btnAboutWeb).setOnClickListener(v ->
                openOrExplain(new Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE)), getString(R.string.about_web_title), WEBSITE));
    }

    // ------------------------------------------------------------- updates

    /** Updates section, inserted in About above the credits. */
    private void bindUpdates() {
        ViewGroup about = (ViewGroup) ((ViewGroup) findViewById(R.id.panelAbout)).getChildAt(0);
        View section = getLayoutInflater().inflate(R.layout.section_updates, about, false);
        about.addView(section, Math.max(0, about.getChildCount() - 2));
        updateCheck = section.findViewById(R.id.btnUpdateCheck);
        updateAuto = section.findViewById(R.id.btnUpdateAuto);
        updateStatus = section.findViewById(R.id.txtUpdateStatus);
        updateCheck.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            UpdateFlow.checkNow(this, this::updateUpdateButtons);
        });
        updateAuto.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            updates.setIntervalHours(UpdateSchedule.next(updates.getIntervalHours()));
            updateUpdateButtons();
        });
        updateUpdateButtons();
    }

    private void updateUpdateButtons() {
        if (updateAuto == null) return;
        long hours = updates.getIntervalHours();
        int label = hours <= 0 ? R.string.update_auto_off : hours == 24 ? R.string.update_auto_daily
                : hours == 72 ? R.string.update_auto_3days : R.string.update_auto_weekly;
        updateAuto.setText(label);
        long last = updates.getLastCheck();
        CharSequence when = last <= 0 ? getString(R.string.update_never)
                : DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        updateStatus.setText(getString(R.string.update_status, updates.installedVersionCode(), when));
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

    // --------------------------------------------------------------- setup

    /** One guided-setup row: capability label, current status and an optional Fix button. */
    private static final class Row {
        final TextView value;
        final Button fix;

        Row(TextView value, Button fix) {
            this.value = value;
            this.fix = fix;
        }
    }

    private Row setupHomeRow, setupQuickRow, setupRedirectRow, setupSecureRow,
            setupInstallRow, setupAssistantRow, setupAutoRow, setupNetworkRow;

    /** Guided setup & recovery: what is healthy, and the one-tap fix for what is not. */
    private void buildSetupPanel() {
        LinearLayout list = findViewById(R.id.setupRows);
        list.removeAllViews();
        firstSetup = null;
        TextView intro = new TextView(this);
        intro.setText(R.string.setup_intro);
        intro.setTextColor(color(R.color.nova_text_dim));
        intro.setTextSize(10);
        intro.setLineSpacing(0f, 1.25f);
        list.addView(intro);
        addEyebrow(list, R.string.setup_health_label);
        setupHomeRow = addStatusRow(list, R.string.setup_row_home, R.string.setup_fix, v -> {
            SoundManager.playClick(v, preferences);
            Glass.info(this, getString(R.string.setup_home_title), getString(R.string.setup_home_needed),
                    "adb shell cmd package set-home-activity " + getPackageName() + "/.MainActivity", null, null);
        });
        setupQuickRow = addStatusRow(list, R.string.setup_row_quick, R.string.setup_fix, v -> {
            SoundManager.playClick(v, preferences);
            if (QuickActionService.isRunning()) toast(getString(R.string.quick_enabled));
            else if (QuickAccess.ensureServiceEnabled(this)) toast(getString(R.string.quick_enabling));
            else QuickAccess.showSetup(this);
            refreshSetup();
        });
        setupRedirectRow = addStatusRow(list, R.string.setup_row_redirect, R.string.setup_fix, v -> {
            SoundManager.playClick(v, preferences);
            preferences.setHomeRedirectEnabled(true);
            updateSystemButtons();
            refreshSetup();
            toast(getString(R.string.setup_redirect_fixed));
        });
        setupSecureRow = addStatusRow(list, R.string.setup_row_secure, R.string.setup_fix, v -> {
            SoundManager.playClick(v, preferences);
            Glass.info(this, getString(R.string.setup_secure_title), getString(R.string.setup_secure_help),
                    getString(R.string.adb_key_command), getString(R.string.setup_open_advanced),
                    () -> showSection(SECTION_ADB));
        });
        setupInstallRow = addStatusRow(list, R.string.setup_row_install, R.string.setup_fix, v -> {
            SoundManager.playClick(v, preferences);
            QuickActionService.suppressRedirect(60_000L);
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException exception) {
                Glass.info(this, getString(R.string.setup_install_title), getString(R.string.setup_install_help),
                        new UpdateManager(this).grantInstallCommand(), getString(R.string.setup_open_advanced),
                        () -> showSection(SECTION_ADB));
            }
        });
        setupAssistantRow = addStatusRow(list, R.string.setup_row_assistant, R.string.setup_assistant_fix,
                v -> showSection(SECTION_ASSISTANT));
        setupAutoRow = addStatusRow(list, R.string.setup_row_updates, R.string.setup_auto_fix, v -> {
            SoundManager.playClick(v, preferences);
            toolUpdates.setIntervalHours(24L);
            refreshSetup();
            updateUpdateButtons();
            toast(getString(R.string.setup_auto_fixed));
        });
        setupNetworkRow = addStatusRow(list, R.string.setup_row_network, R.string.setup_doctor_fix,
                v -> showSection(SECTION_TOOLS));
        TextView note = new TextView(this);
        note.setText(R.string.setup_adb_note);
        note.setTextColor(color(R.color.nova_text_muted));
        note.setTextSize(9);
        note.setLineSpacing(0f, 1.25f);
        note.setPadding(0, dp(14), 0, 0);
        list.addView(note);
        firstSetup = intro;
    }

    /** One labelled row with a status TextView and a Fix button (hidden until it is needed). */
    private Row addStatusRow(LinearLayout list, int labelRes, int fixLabel, View.OnClickListener fixAction) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(3));
        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setAllCaps(true);
        label.setLetterSpacing(.14f);
        label.setTextColor(color(R.color.nova_text_muted));
        label.setTextSize(8);
        row.addView(label, new LinearLayout.LayoutParams(dp(130), LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView value = new TextView(this);
        value.setTextColor(color(R.color.nova_text));
        value.setTextSize(11);
        row.addView(value, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button fix = new Button(this);
        fix.setId(View.generateViewId());
        fix.setFocusable(true);
        fix.setText(fixLabel);
        fix.setAllCaps(false);
        fix.setTextColor(color(R.color.nova_accent));
        fix.setTextSize(9);
        fix.setMinHeight(0);
        fix.setMinimumHeight(0);
        fix.setBackgroundResource(R.drawable.bg_option_selector);
        fix.setVisibility(View.GONE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
        params.leftMargin = dp(6);
        fix.setLayoutParams(params);
        fix.setOnClickListener(fixAction);
        fix.setOnFocusChangeListener((v, focused) -> SoundManager.playFocus(v, preferences));
        row.addView(fix);
        list.addView(row);
        return new Row(value, fix);
    }

    /** Recomputes every setup row's status; cheap reads only, safe from onResume. */
    private void refreshSetup() {
        if (setupHomeRow == null) return;
        setupHomeRow.value.setText(homeSummary());
        setupHomeRow.fix.setVisibility(isDefaultHome() ? View.GONE : View.VISIBLE);

        int quick = QuickActionService.isRunning() ? R.string.quick_permission_on
                : QuickAccess.isServiceEnabled(this) ? R.string.quick_permission_starting : R.string.quick_permission_off;
        setupQuickRow.value.setText(quick);
        setupQuickRow.fix.setVisibility(quick == R.string.quick_permission_on ? View.GONE : View.VISIBLE);

        boolean redirect = preferences.isHomeRedirectEnabled();
        setupRedirectRow.value.setText(redirect ? R.string.setup_state_on : R.string.setup_state_off);
        setupRedirectRow.fix.setVisibility(redirect ? View.GONE : View.VISIBLE);

        boolean secure = secureWriteGranted();
        setupSecureRow.value.setText(secure ? R.string.setup_state_on : R.string.setup_secure_off);
        setupSecureRow.fix.setVisibility(secure ? View.GONE : View.VISIBLE);

        boolean install = new UpdateManager(this).canInstall();
        setupInstallRow.value.setText(install ? R.string.setup_state_on : R.string.setup_install_off);
        setupInstallRow.fix.setVisibility(install ? View.GONE : View.VISIBLE);

        boolean assistantKey = assistant.hasApiKey();
        setupAssistantRow.value.setText(assistantKey ? R.string.setup_state_on : R.string.setup_assistant_off);
        setupAssistantRow.fix.setVisibility(assistantKey ? View.GONE : View.VISIBLE);

        long interval = toolUpdates == null || toolUpdates.getIntervalHours() <= 0 ? 0L : toolUpdates.getIntervalHours();
        setupAutoRow.value.setText(interval == 0 ? R.string.update_auto_off
                : interval <= 24 ? R.string.update_auto_daily : R.string.update_auto_weekly);
        setupAutoRow.fix.setVisibility(interval == 0 ? View.VISIBLE : View.GONE);

        boolean online = deviceOnline();
        setupNetworkRow.value.setText(online ? R.string.setup_state_online : R.string.setup_state_offline);
        setupNetworkRow.fix.setVisibility(online ? View.GONE : View.VISIBLE);
    }

    /**
     * WRITE_SECURE_SETTINGS is granted over ADB; probing it with a throwaway setting is the only
     * in-app read-out. The probe value is removed again right away.
     */
    private boolean secureWriteGranted() {
        try {
            boolean wrote = Settings.Secure.putString(getContentResolver(), "lumora_setup_probe", "1");
            if (!wrote) return false;
            Settings.Secure.putString(getContentResolver(), "lumora_setup_probe", null);
            return true;
        } catch (RuntimeException denied) {
            return false;
        }
    }

    /** Connectivity state only — no network traffic on the main thread. */
    private boolean deviceOnline() {
        NetworkInfo network = ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        return network != null && network.isConnected();
    }

    // --------------------------------------------------------------- tools

    private Button toolUrlPhone, toolFilePhone;

    /** Maintenance tools: Lumora's own cache dashboard, a network doctor and update diagnostics. */
    private void buildToolsPanel() {
        toolUpdates = new UpdateManager(this);
        LinearLayout list = findViewById(R.id.toolsRows);
        list.removeAllViews();
        firstTools = null;

        addEyebrow(list, R.string.tools_storage_label);
        txtCacheBreakdown = new TextView(this);
        txtCacheBreakdown.setText(R.string.tools_storage_scanning);
        txtCacheBreakdown.setTypeface(Typeface.MONOSPACE);
        txtCacheBreakdown.setTextColor(color(R.color.nova_text));
        txtCacheBreakdown.setTextSize(9);
        txtCacheBreakdown.setLineSpacing(0f, 1.35f);
        txtCacheBreakdown.setPadding(0, dp(4), 0, dp(2));
        list.addView(txtCacheBreakdown);
        toolClean = toolButton(list, R.string.tools_clean);
        toolClean.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            cleanCaches();
        });
        firstTools = toolClean;

        addEyebrow(list, R.string.tools_network_label);
        toolDoctor = toolButton(list, R.string.tools_doctor);
        toolDoctor.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            runNetworkDoctor();
        });
        txtDoctor = new TextView(this);
        txtDoctor.setText(R.string.tools_doctor_idle);
        txtDoctor.setTextColor(color(R.color.nova_text));
        txtDoctor.setTextSize(9);
        txtDoctor.setTypeface(Typeface.MONOSPACE);
        txtDoctor.setLineSpacing(0f, 1.35f);
        txtDoctor.setPadding(0, dp(4), 0, dp(2));
        list.addView(txtDoctor);

        addEyebrow(list, R.string.tools_updates_label);
        toolChannel = toolButton(list, R.string.tools_channel);
        toolChannel.setOnClickListener(v -> pickChannel());
        toolCheck = toolButton(list, R.string.update_check);
        toolCheck.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            UpdateFlow.checkNow(this, this::refreshTools);
        });
        txtToolUpdate = new TextView(this);
        txtToolUpdate.setTextColor(color(R.color.nova_text));
        txtToolUpdate.setTextSize(9);
        txtToolUpdate.setLineSpacing(0f, 1.35f);
        txtToolUpdate.setPadding(0, dp(4), 0, dp(2));
        list.addView(txtToolUpdate);

        addEyebrow(list, R.string.tools_phone_label);
        toolUrlPhone = toolButton(list, R.string.tools_phone_url);
        toolUrlPhone.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            startUrlPhone();
        });
        toolFilePhone = toolButton(list, R.string.tools_phone_file);
        toolFilePhone.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            startFilePhone();
        });
        TextView hint = new TextView(this);
        hint.setText(R.string.tools_phone_hint);
        hint.setTextColor(color(R.color.nova_text_muted));
        hint.setTextSize(9);
        hint.setLineSpacing(0f, 1.25f);
        hint.setPadding(0, dp(2), 0, 0);
        list.addView(hint);
    }

    private Button toolButton(LinearLayout list, int label) {
        Button button = new Button(this);
        button.setId(View.generateViewId());
        button.setFocusable(true);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(color(R.color.nova_accent));
        button.setTextSize(9);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackgroundResource(R.drawable.bg_option_selector);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
        params.topMargin = dp(6);
        button.setLayoutParams(params);
        button.setOnFocusChangeListener((v, focused) -> SoundManager.playFocus(v, preferences));
        list.addView(button);
        return button;
    }

    /** Disk scan of every cache folder Lumora owns; runs on the settings worker thread. */
    private void scanCaches() {
        if (txtCacheBreakdown == null) return;
        worker.execute(() -> {
            List<MaintenanceTools.Dir> dirs = MaintenanceTools.cacheDirs(this);
            long known = 0L;
            for (MaintenanceTools.Dir entry : dirs) known += MaintenanceTools.size(entry.dir);
            long whole = MaintenanceTools.size(getCacheDir());
            StringBuilder report = new StringBuilder();
            for (MaintenanceTools.Dir entry : dirs) {
                report.append(entry.label).append(':').append(' ')
                        .append(DeviceStats.formatBytes(MaintenanceTools.size(entry.dir))).append('\n');
            }
            report.append(getString(R.string.tools_storage_other))
                    .append(' ').append(DeviceStats.formatBytes(Math.max(0L, whole - known))).append('\n');
            report.append(getString(R.string.tools_storage_total))
                    .append(' ').append(DeviceStats.formatBytes(whole));
            String text = report.toString();
            handler.post(() -> {
                if (txtCacheBreakdown != null && !isDestroyed()) txtCacheBreakdown.setText(text);
            });
        });
    }

    private void cleanCaches() {
        if (txtCacheBreakdown == null) return;
        toolClean.setEnabled(false);
        toolClean.setAlpha(.5f);
        worker.execute(() -> {
            long freed = 0L;
            for (MaintenanceTools.Dir entry : MaintenanceTools.cacheDirs(this)) {
                freed += MaintenanceTools.deleteContents(entry.dir);
            }
            long released = freed;
            handler.post(() -> {
                if (isDestroyed()) return;
                toolClean.setEnabled(true);
                toolClean.setAlpha(1f);
                toast(getString(R.string.tools_clean_done, DeviceStats.formatBytes(released)));
                scanCaches();
            });
        });
    }

    private void runNetworkDoctor() {
        if (txtDoctor == null) return;
        toolDoctor.setEnabled(false);
        toolDoctor.setAlpha(.5f);
        txtDoctor.setText(R.string.tools_doctor_running);
        worker.execute(() -> {
            StringBuilder report = new StringBuilder();
            for (MaintenanceTools.Probe target : MaintenanceTools.probes()) {
                MaintenanceTools.Result result = MaintenanceTools.probe(target);
                report.append(target.label).append(": ")
                        .append(result.ok ? getString(R.string.tools_probe_ok, result.ms)
                                : getString(R.string.tools_probe_fail, result.ms, result.detail))
                        .append('\n');
            }
            String text = report.toString();
            handler.post(() -> {
                if (isDestroyed()) return;
                toolDoctor.setEnabled(true);
                toolDoctor.setAlpha(1f);
                txtDoctor.setText(text);
            });
        });
    }

    private void refreshTools() {
        if (txtToolUpdate == null) return;
        boolean beta = toolUpdates.getChannel() == UpdateManager.CHANNEL_BETA;
        toolChannel.setText(getString(R.string.tools_channel_value, getString(beta
                ? R.string.tools_channel_beta : R.string.tools_channel_stable)));
        long last = toolUpdates.getLastCheck();
        CharSequence when = last <= 0 ? getString(R.string.update_never)
                : DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        String result = toolUpdates.lastResult();
        txtToolUpdate.setText(getString(R.string.tools_update_status, toolUpdates.installedVersionCode(), when)
                + (result.isEmpty() ? "" : "\n" + result));
    }

    private void pickChannel() {
        SoundManager.playClick(toolChannel, preferences);
        boolean beta = toolUpdates.getChannel() == UpdateManager.CHANNEL_BETA;
        List<GlassMenu.Item> items = new ArrayList<>();
        items.add(new GlassMenu.Item(R.drawable.ic_sec_device, getString(R.string.tools_channel_stable)).checked(!beta));
        items.add(new GlassMenu.Item(R.drawable.ic_web, getString(R.string.tools_channel_beta)).checked(beta));
        GlassMenu.show(this, getString(R.string.tools_channel_title), getString(R.string.tools_channel_sub), null,
                items, false, (index, item) -> {
                    toolUpdates.setChannel(index == 1 ? UpdateManager.CHANNEL_BETA : UpdateManager.CHANNEL_STABLE);
                    refreshTools();
                });
    }

    // -------------------------------------------------- phone link: urls and files

    /** Download folder for phone transfers; falls back to app-visible external storage. */
    private File receiveDir() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if ((downloads.isDirectory() || downloads.mkdirs()) && downloads.canWrite()) return downloads;
        File own = getExternalFilesDir(null);
        File fallback = own == null ? new File(getCacheDir(), "From Phone")
                : new File(own, "From Phone");
        return fallback.isDirectory() || fallback.mkdirs() ? fallback : getCacheDir();
    }

    /** Opens the MODE_URL phone link; each link received is opened in the TV's browser. */
    private void startUrlPhone() {
        stopUrlPhone();
        PhoneBridge bridge = new PhoneBridge(PhoneBridge.MODE_URL, new PhoneBridge.Listener() {
            @Override public void onText(String text) {
                // Links travel phone → TV only on this link.
            }

            @Override public void onCodeChanged(String code) {
                if (urlDialog != null && urlDialog.isShowing()) urlDialog.refresh();
            }

            @Override public void onStopped(boolean idle) {
                stopUrlPhone();
            }

            @Override public void onUrl(String url) {
                openOnTv(url);
            }
        });
        int result = bridge.start();
        if (result != PhoneBridge.START_OK) {
            Glass.info(this, getString(R.string.phone_title_url), getString(result == PhoneBridge.START_NO_NETWORK
                    ? R.string.phone_no_network : R.string.phone_start_failed), null, null, null);
            return;
        }
        urlPhone = bridge;
        urlDialog = PhonePairDialog.show(this, bridge, getString(R.string.phone_title_url),
                getString(R.string.phone_message_url), this::stopUrlPhone);
    }

    private void openOnTv(String url) {
        // URLs and files land on the TV (never the other way around); links open in the browser.
        QuickActionService.suppressRedirect(15_000L);
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            toast(getString(R.string.phone_url_sent));
        } catch (ActivityNotFoundException exception) {
            Glass.info(this, getString(R.string.phone_title_url),
                    getString(R.string.phone_url_open_fail), url, null, null);
        }
    }

    private void stopUrlPhone() {
        if (urlDialog != null) urlDialog.dismiss();
        urlDialog = null;
        if (urlPhone != null) urlPhone.stop();
        urlPhone = null;
    }

    /** Opens the MODE_FILES phone link; files stream into Downloads and are indexed there. */
    private void startFilePhone() {
        stopFilePhone();
        PhoneBridge bridge = new PhoneBridge(PhoneBridge.MODE_FILES, new PhoneBridge.Listener() {
            @Override public void onText(String text) {
                // Files travel phone → TV only on this link.
            }

            @Override public void onCodeChanged(String code) {
                if (fileDialog != null && fileDialog.isShowing()) fileDialog.refresh();
            }

            @Override public void onStopped(boolean idle) {
                stopFilePhone();
            }

            @Override public void onFileSaved(File file) {
                MediaScannerConnection.scanFile(SettingsActivity.this,
                        new String[]{file.getAbsolutePath()}, null, null);
                Glass.info(SettingsActivity.this, getString(R.string.phone_file_title),
                        getString(R.string.phone_file_saved, file.getAbsolutePath()), null, null, null);
            }
        }, receiveDir());
        int result = bridge.start();
        if (result != PhoneBridge.START_OK) {
            Glass.info(this, getString(R.string.phone_title_file), getString(result == PhoneBridge.START_NO_NETWORK
                    ? R.string.phone_no_network : R.string.phone_start_failed), null, null, null);
            return;
        }
        filePhone = bridge;
        fileDialog = PhonePairDialog.show(this, bridge, getString(R.string.phone_title_file),
                getString(R.string.phone_message_file), this::stopFilePhone);
    }

    private void stopFilePhone() {
        if (fileDialog != null) fileDialog.dismiss();
        fileDialog = null;
        if (filePhone != null) filePhone.stop();
        filePhone = null;
    }
}
