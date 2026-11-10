package com.firestick.aura.launcher;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firestick.aura.launcher.adapter.AppRowAdapter;
import com.firestick.aura.launcher.adapter.IptvAdapter;
import com.firestick.aura.launcher.assistant.AssistantSettings;
import com.firestick.aura.launcher.assistant.LongPress;
import com.firestick.aura.launcher.manager.AppLock;
import com.firestick.aura.launcher.manager.AppRepository;
import com.firestick.aura.launcher.manager.DebloatManager;
import com.firestick.aura.launcher.manager.IptvRepository;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.QuickAccess;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.manager.WallpaperManager;
import com.firestick.aura.launcher.model.AppCategory;
import com.firestick.aura.launcher.model.AppModel;
import com.firestick.aura.launcher.model.IptvCountry;
import com.firestick.aura.launcher.model.IptvChannel;
import com.firestick.aura.launcher.service.QuickActionService;
import com.firestick.aura.launcher.ui.Glass;
import com.firestick.aura.launcher.ui.AppPreview;
import com.firestick.aura.launcher.ui.GlassMenu;
import com.firestick.aura.launcher.ui.LockPad;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity
        implements AppRowAdapter.OnAppActionListener, ShowDetails.Host {
    /** Package to launch on arrival; sent by the assistant so locks and launch history apply. */
    public static final String EXTRA_LAUNCH_PACKAGE = "com.firestick.aura.launcher.extra.LAUNCH_PACKAGE";
    private static final int TAB_HOME = 0, TAB_SHOWS = 1, TAB_APPS = 2, TAB_STREAMING = 3;
    private static final int GRID_COLUMNS = 7, IPTV_COLUMNS = 8, HOME_FALLBACK = 8;
    private static final long SHOWS_MAX_AGE_MS = 60L * 60L * 1000L;
    private static final long GEAR_DELAY_MS = 350L, GEAR_KEY_WINDOW_MS = 600L, GEAR_REOPEN_GUARD_MS = 900L;
    /** Bridge targets for a DOWN key that a tile of the ribbon or a grid cannot resolve. */
    private static final int[] NAV_TARGETS = {R.id.navHome, R.id.navShows, R.id.navApps,
            R.id.navMovies};

    private RecyclerView homeView, gridView, showsView;
    private AppRowAdapter homeAdapter, appsAdapter, streamingAdapter;
    private IptvAdapter iptvAdapter;
    private AppRepository repository;
    private IptvRepository iptvRepository;
    private PreferenceManager preferences;
    private AssistantSettings assistant;
    private WallpaperManager wallpapers;
    private QuickMenu quickMenu;
    private AppPreview appPreview;
    private boolean previewResumed;
    private final ViewTreeObserver.OnGlobalFocusChangeListener previewFocusListener =
            (oldFocus, newFocus) -> updateAppPreview();
    private TextView greeting, greetingSub, heroTitle, heroMeta, showsStatus, gridEmpty, moveHint,
            homeIptvFavorites;
    private ImageView assistantIcon;
    private AutoCompleteTextView iptvCountry;
    private EditText iptvSearch;
    private View homePanel, gridPanel, showsPanel, gear, assistantButton;
    private TextView iptvFilter;
    private PopupWindow gearMenu;
    private final Map<String, List<AppModel>> rows = new HashMap<>();
    private final LongPress menuPress = new LongPress();
    private List<AppModel> homeApps = new ArrayList<>();
    private final List<IptvCountry> iptvCountries = new ArrayList<>();
    private List<IptvChannel> countryChannels = new ArrayList<>();
    private String selectedIptvCountry, selectedIptvCategory = "all", iptvSearchQuery = "";
    private long appliedHomeRevision = -1L, iptvLoadedAt, lastKeyDownAt, gearDismissedAt;
    private int currentTab = TAB_HOME, refreshGeneration, iptvGeneration;
    private boolean iptvCountriesLoaded, iptvCountriesLoading, channelsLoaded;
    private boolean packageReceiverRegistered, homeIsFallback, initialFocusPending = true;

    // Move mode: the focused app follows the arrow keys until OK or Back.
    private String movingRow, movingPackage;
    private List<AppModel> movingList;
    private AppRowAdapter movingAdapter;
    private int movingStep;

    private final Runnable openGear = () -> {
        if (gear != null && gear.isFocused()) showGearMenu();
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String packageName = intent.getData() == null ? null : intent.getData().getSchemeSpecificPart();
            repository.invalidatePackage(packageName);
            refreshApps(false);
        }
    };

    /**
     * The Fire TV stays awake when the TV panel turns off, so the only reliable signal that
     * nobody is watching is the screen-off broadcast. Hiding the preview here stops the public
     * video WebView from pulling feed data and stream bytes while the room is dark; particles
     * cost nothing but hide as well for consistency. Screens-on recomputes the panel.
     */
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (appPreview == null) return;
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) appPreview.hide();
            else homeView.post(MainActivity.this::updateAppPreview);
        }
    };
    private boolean screenReceiverRegistered;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        preferences = new PreferenceManager(this);
        assistant = new AssistantSettings(this);
        int amber = ContextCompat.getColor(this, R.color.accent_amber);
        preferences.migrateAccent(new int[]{amber, ContextCompat.getColor(this, R.color.accent_copper),
                ContextCompat.getColor(this, R.color.accent_rose), ContextCompat.getColor(this, R.color.accent_moonlight)}, amber);
        repository = new AppRepository(this);
        iptvRepository = new IptvRepository(this);
        quickMenu = new QuickMenu(this, preferences, this::launchPackage);
        SoundManager.init(this);
        initViews();
        registerPackageReceiver();
        registerScreenReceiver();
        wallpapers = new WallpaperManager(this, preferences,
                findViewById(R.id.imgWallpaperBack), findViewById(R.id.imgWallpaperFront));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                // A home screen never finishes on Back: return to Home, then to the first card.
                if (movingPackage != null) finishMove();
                else if (currentTab != TAB_HOME) selectTab(TAB_HOME);
                else homeView.smoothScrollToPosition(0);
            }
        });
        handleLaunchRequest(getIntent());
    }

    private void initViews() {
        greeting = findViewById(R.id.txtGreeting);
        greetingSub = findViewById(R.id.txtGreetingSub);
        heroTitle = findViewById(R.id.txtHeroTitle);
        heroMeta = findViewById(R.id.txtHeroMeta);
        moveHint = findViewById(R.id.txtMoveHint);
        homePanel = findViewById(R.id.homePanel);
        gridPanel = findViewById(R.id.gridPanel);
        gridEmpty = findViewById(R.id.txtGridEmpty);

        homeView = findViewById(R.id.recyclerHome);
        homeView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        homeAdapter = new AppRowAdapter(preferences, this, AppRepository.ROW_HOME, AppRowAdapter.STYLE_WIDE,
                (rowId, app) -> setHero(app));
        homeView.setAdapter(homeAdapter);
        homeIptvFavorites = findViewById(R.id.btnHomeIptvFavorites);
        homeIptvFavorites.setOnClickListener(view -> {
            SoundManager.playClick(view, preferences);
            openHomeIptvFavorites();
        });
        homeIptvFavorites.setOnFocusChangeListener((view, focused) -> {
            view.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f)
                    .setDuration(120).start();
            if (focused) SoundManager.playFocus(view, preferences);
        });
        updateHomeIptvFavorites();
        appPreview = new AppPreview(findViewById(R.id.appPreview));
        homeView.getRootView().getViewTreeObserver().addOnGlobalFocusChangeListener(previewFocusListener);

        gridView = findViewById(R.id.recyclerGrid);
        gridView.setLayoutManager(new GridLayoutManager(this, GRID_COLUMNS));
        RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();
        gridView.setRecycledViewPool(pool);
        appsAdapter = new AppRowAdapter(preferences, this, AppRepository.ROW_APPS, AppRowAdapter.STYLE_TILE, null);
        streamingAdapter = new AppRowAdapter(preferences, this, AppRepository.ROW_STREAMING, AppRowAdapter.STYLE_TILE, null);

        showsPanel = findViewById(R.id.showsPanel);
        iptvFilter = findViewById(R.id.iptvFilter);
        iptvFilter.setOnClickListener(v -> openCategoryMenu());
        showsStatus = findViewById(R.id.txtShowsStatus);
        showsView = findViewById(R.id.recyclerShows);
        showsView.setLayoutManager(new GridLayoutManager(this, IPTV_COLUMNS));
        iptvAdapter = new IptvAdapter(preferences, this::playIptvChannel, this::toggleIptvFavorite);
        showsView.setAdapter(iptvAdapter);
        iptvCountry = findViewById(R.id.iptvCountry);
        iptvCountry.setThreshold(0);
        iptvCountry.setOnClickListener(view -> {
            iptvCountry.setText("", false);
            iptvCountry.post(() -> iptvCountry.showDropDown());
        });
        iptvCountry.setOnDismissListener(() -> {
            for (IptvCountry country : iptvCountries) {
                if (country.code.equals(selectedIptvCountry)) {
                    iptvCountry.setText(countryLabel(country), false);
                    break;
                }
            }
        });
        iptvCountry.setOnItemClickListener((parent, view, position, id) -> {
            for (IptvCountry option : iptvCountries) {
                if (countryLabel(option).equals(parent.getItemAtPosition(position))) {
                    selectIptvCountry(option);
                    break;
                }
            }
        });
        iptvSearch = findViewById(R.id.iptvSearch);
        iptvSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                iptvSearchQuery = s == null ? "" : s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                applyIptvFilters();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        bindNav(R.id.navHome, TAB_HOME);
        bindNav(R.id.navShows, TAB_SHOWS);
        bindNav(R.id.navApps, TAB_APPS);
        bindNav(R.id.navMovies, TAB_STREAMING);
        View quick = findViewById(R.id.navQuick);
        quick.setOnClickListener(view -> {
            SoundManager.playClick(view, preferences);
            quickMenu.show();
        });
        quick.setOnFocusChangeListener((view, focused) -> {
            findViewById(R.id.quickTooltip).setVisibility(focused ? View.VISIBLE : View.GONE);
            view.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f).setDuration(120).start();
            if (focused) SoundManager.playFocus(view, preferences);
        });

        assistantButton = findViewById(R.id.btnAssistant);
        assistantIcon = findViewById(R.id.imgAssistant);
        assistantButton.setOnClickListener(view -> {
            SoundManager.playClick(view, preferences);
            openAssistant();
        });
        assistantButton.setOnFocusChangeListener((view, focused) -> {
            view.animate().scaleX(focused ? 1.14f : 1f).scaleY(focused ? 1.14f : 1f).setDuration(140).start();
            if (focused) SoundManager.playFocus(view, preferences);
        });

        gear = findViewById(R.id.btnGear);
        gear.setOnClickListener(view -> showGearMenu());
        gear.setOnFocusChangeListener((view, focused) -> {
            view.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f).setDuration(120).start();
            view.removeCallbacks(openGear);
            if (!focused) return;
            SoundManager.playFocus(view, preferences);
            long now = SystemClock.uptimeMillis();
            // Open on D-pad hover only: not on initial focus and not right after the menu closed.
            if (now - lastKeyDownAt < GEAR_KEY_WINDOW_MS && now - gearDismissedAt > GEAR_REOPEN_GUARD_MS) {
                view.postDelayed(openGear, GEAR_DELAY_MS);
            }
        });
    }

    private void bindNav(int id, int tab) {
        View view = findViewById(id);
        view.setOnClickListener(source -> {
            SoundManager.playClick(source, preferences);
            selectTab(tab);
        });
        bindNavFocus(view);
    }

    private void bindNavFocus(View view) {
        view.setOnFocusChangeListener((source, focused) -> {
            source.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(120).start();
            if (focused) SoundManager.playFocus(source, preferences);
        });
    }

    // ------------------------------------------------------------ assistant

    private void openAssistant() {
        startActivity(new Intent(this, AssistantActivity.class));
    }

    private void updateAssistantButton() {
        boolean visible = assistant.isFloatingButtonEnabled();
        if (!visible && assistantButton.hasFocus()) findViewById(R.id.navMovies).requestFocus();
        assistantButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        assistantIcon.setImageTintList(ColorStateList.valueOf(accent()));
    }

    private void handleLaunchRequest(Intent intent) {
        if (intent == null) return;
        String pkg = intent.getStringExtra(EXTRA_LAUNCH_PACKAGE);
        if (pkg == null || pkg.isEmpty()) return;
        intent.removeExtra(EXTRA_LAUNCH_PACKAGE);
        homeView.post(() -> launchPackage(pkg));
    }

    // ------------------------------------------------------------ gear menu

    private void showGearMenu() {
        if (gearMenu != null && gearMenu.isShowing()) return;
        View content = getLayoutInflater().inflate(R.layout.popup_gear_menu, null);
        PopupWindow popup = new PopupWindow(content, dp(240), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(16));
        int accent = accent();
        ((ImageView) content.findViewById(R.id.gearIconLauncher)).setImageTintList(ColorStateList.valueOf(accent));
        ((ImageView) content.findViewById(R.id.gearIconFire)).setImageTintList(ColorStateList.valueOf(accent));
        View launcherRow = content.findViewById(R.id.gearLauncherSettings);
        View fireRow = content.findViewById(R.id.gearFireSettings);
        launcherRow.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            popup.dismiss();
            openLauncherSettings();
        });
        fireRow.setOnClickListener(v -> {
            SoundManager.playClick(v, preferences);
            popup.dismiss();
            openFireSettings();
        });
        popup.setOnDismissListener(() -> {
            gearDismissedAt = SystemClock.uptimeMillis();
            gearMenu = null;
        });
        gearMenu = popup;
        popup.showAsDropDown(gear, gear.getWidth() - dp(240), dp(8));
        launcherRow.requestFocus();
    }

    private void openLauncherSettings() {
        if (preferences.isSettingsLocked()) {
            LockPad.verify(this, preferences, getString(R.string.lock_sub_settings),
                    () -> startActivity(new Intent(this, SettingsActivity.class)));
        } else {
            startActivity(new Intent(this, SettingsActivity.class));
        }
    }

    private void openFireSettings() {
        QuickActionService.suppressRedirect(15_000L);
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ focus bridging

    /**
     * The ribbon and both grids run edge to edge, while the focusable nav items sit in a
     * centred block. The D-pad's downward beam search only considers candidates that overlap
     * the focused tile's horizontal span, so from a tile that extends left or right of the nav
     * block DOWN finds nothing and the press is lost. When the standard search misses from a
     * tile of either list, focus the nav item whose centre is horizontally closest.
     */
    private boolean bridgeFocusToNav() {
        View focused = getCurrentFocus();
        if (focused == null || !insideRecyclerView(focused)) return false;
        if (focused.focusSearch(View.FOCUS_DOWN) != null) return false;
        int[] location = new int[2];
        focused.getLocationOnScreen(location);
        int focusCenter = location[0] + focused.getWidth() / 2;
        View best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int navId : NAV_TARGETS) {
            View nav = findViewById(navId);
            if (nav == null || !nav.isShown()) continue;
            nav.getLocationOnScreen(location);
            int distance = Math.abs(focusCenter - (location[0] + nav.getWidth() / 2));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = nav;
            }
        }
        if (best == null) return false;
        best.requestFocus(View.FOCUS_DOWN);
        return true;
    }

    /** True when {@code view} sits inside one of the three tile lists (not the channel chips). */
    private boolean insideRecyclerView(View view) {
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == homeView || parent == gridView || parent == showsView) return true;
        }
        return false;
    }

    // ----------------------------------------------------------------- tabs

    public void selectTab(int tab) {
        if (movingPackage != null) finishMove();
        if (currentTab == tab) {
            if (tab == TAB_SHOWS) showsView.smoothScrollToPosition(0);
            else if (tab == TAB_HOME) homeView.smoothScrollToPosition(0);
            else gridView.smoothScrollToPosition(0);
            return;
        }
        currentTab = tab;
        applyTabVisibility();
        updateGreeting();
        updateNavSelection();
        if (tab == TAB_SHOWS) loadIptv();
        else applyRows();
    }

    private void applyTabVisibility() {
        if (currentTab != TAB_HOME && appPreview != null) appPreview.hide();
        homePanel.setVisibility(currentTab == TAB_HOME ? View.VISIBLE : View.GONE);
        showsPanel.setVisibility(currentTab == TAB_SHOWS ? View.VISIBLE : View.GONE);
        boolean grid = currentTab == TAB_APPS || currentTab == TAB_STREAMING;
        gridPanel.setVisibility(grid ? View.VISIBLE : View.GONE);
        if (grid) {
            AppRowAdapter wanted = currentTab == TAB_APPS ? appsAdapter : streamingAdapter;
            if (gridView.getAdapter() != wanted) gridView.setAdapter(wanted);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Home pressed (or redirected) while Lumora is in front: return to the Home tab.
        if (gearMenu != null) gearMenu.dismiss();
        if (currentTab != TAB_HOME) selectTab(TAB_HOME);
        homeView.scrollToPosition(0);
        handleLaunchRequest(intent);
    }

    private void updateNavSelection() {
        styleNav(R.id.txtNavHome, R.id.imgNavHome, currentTab == TAB_HOME);
        styleNav(R.id.txtNavShows, R.id.imgNavShows, currentTab == TAB_SHOWS);
        styleNav(R.id.txtNavApps, R.id.imgNavApps, currentTab == TAB_APPS);
        styleNav(R.id.txtNavMovies, R.id.imgNavMovies, currentTab == TAB_STREAMING);

    }

    private void styleNav(int labelId, int iconId, boolean selected) {
        int color = selected ? accent() : ContextCompat.getColor(this, R.color.nova_text_dim);
        TextView label = findViewById(labelId);
        ImageView icon = findViewById(iconId);
        label.setTextColor(color);
        label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        icon.setImageTintList(ColorStateList.valueOf(color));
    }

    private int accent() {
        return preferences.getAccentColor(ContextCompat.getColor(this, R.color.nova_accent));
    }

    private void registerPackageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        packageReceiverRegistered = true;
    }

    private void registerScreenReceiver() {
        ContextCompat.registerReceiver(this, screenReceiver,
                new IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED);
        screenReceiverRegistered = true;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == QuickMenu.WIFI_PERMISSION_REQUEST && quickMenu != null) quickMenu.wifiPermissionResult();
    }

    @Override protected void onDestroy() {
        if (quickMenu != null) quickMenu.dismiss();
        if (appPreview != null) appPreview.destroy();
        homeView.getRootView().getViewTreeObserver().removeOnGlobalFocusChangeListener(previewFocusListener);
        if (packageReceiverRegistered) {
            try {
                unregisterReceiver(packageReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered.
            }
        }
        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered.
            }
        }
        if (wallpapers != null) wallpapers.shutdown();
        repository.shutdown();
        if (iptvRepository != null) iptvRepository.shutdown();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        previewResumed = true;
        // Keeps the quick-actions service on once WRITE_SECURE_SETTINGS has been granted over ADB.
        QuickAccess.ensureServiceEnabled(this);
        appliedHomeRevision = preferences.getHomeRevision();
        applyTabVisibility();
        // Always rebuild: packages removed for user 0 over ADB do not always broadcast.
        refreshApps(true);
        updateGreeting();
        updateNavSelection();
        updateAssistantButton();
        updateHomeIptvFavorites();
        if (currentTab == TAB_SHOWS) loadIptv();
        if (wallpapers != null) wallpapers.start();
        homeView.post(this::updateAppPreview);
    }

    @Override protected void onPause() {
        previewResumed = false;
        appPreview.hide();
        if (movingPackage != null) finishMove();
        if (gearMenu != null) gearMenu.dismiss();
        if (wallpapers != null) wallpapers.stop();
        menuPress.up();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (appPreview == null) return;
        if (!hasFocus) appPreview.hide();
        else homeView.post(this::updateAppPreview);
    }

    private void updateAppPreview() {
        if (appPreview == null) return;
        AppModel focusedHomeApp = homeAdapter.getFocusedApp();
        if (!previewResumed || !hasWindowFocus() || currentTab != TAB_HOME || !homeView.hasFocus()
                || !preferences.isAppPreviewEnabled() || movingPackage != null || focusedHomeApp == null) {
            appPreview.hide();
            return;
        }
        appPreview.focus(focusedHomeApp.getPackageName(), focusedHomeApp.getLabel());
    }

    /**
     * Fallback when the quick-actions service is off: holding Menu still opens the assistant inside
     * Lumora, using the key repeats the activity receives. With the service on, it handles the hold.
     */
    private boolean handleMenuHold(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_MENU || movingPackage != null
                || QuickActionService.isRunning() || !assistant.isRemoteButtonEnabled()) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                menuPress.down(event.getEventTime());
                return false;
            }
            if (menuPress.check(event.getEventTime())) openAssistant();
            return menuPress.hasFired();
        }
        if (event.getAction() == KeyEvent.ACTION_UP) return menuPress.up();
        return false;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        // Play/Pause is unused on the launcher; Menu retains app actions and assistant hold.
        if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && movingPackage == null) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) quickMenu.show();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && movingPackage == null) {
            if (bridgeFocusToNav()) return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) lastKeyDownAt = SystemClock.uptimeMillis();
        if (handleMenuHold(event)) return true;
        if (movingPackage == null) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
        boolean moveKey = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_BACK;
        if (!moveKey) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    shiftMoving(-1);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    shiftMoving(1);
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    shiftMoving(-movingStep);
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    shiftMoving(movingStep);
                    break;
                default:
                    if (event.getRepeatCount() == 0) finishMove();
                    break;
            }
        }
        return true;
    }

    // ----------------------------------------------------------------- apps

    private List<AppModel> row(String id) {
        List<AppModel> apps = rows.get(id);
        return apps == null ? new ArrayList<>() : apps;
    }

    private void refreshApps(boolean force) {
        if (force) repository.invalidate();
        int request = ++refreshGeneration;
        repository.loadCategoriesAsync(categories -> {
            if (request != refreshGeneration || isFinishing() || isDestroyed()) return;
            rows.clear();
            for (AppCategory category : categories) rows.put(category.getId(), new ArrayList<>(category.getApps()));
            applyRows();
            updateGreeting();
        });
    }

    private void applyRows() {
        if (movingPackage != null) return;
        List<AppModel> favorites = row(AppRepository.ROW_HOME);
        List<AppModel> streaming = row(AppRepository.ROW_STREAMING);
        List<AppModel> apps = row(AppRepository.ROW_APPS);
        homeIsFallback = favorites.isEmpty();
        if (homeIsFallback) {
            List<AppModel> source = streaming.isEmpty() ? apps : streaming;
            homeApps = new ArrayList<>(source.subList(0, Math.min(HOME_FALLBACK, source.size())));
        } else {
            homeApps = favorites;
        }
        homeAdapter.setApps(homeApps);
        appsAdapter.setApps(apps);
        streamingAdapter.setApps(streaming);

        boolean emptyGrid = (currentTab == TAB_APPS && apps.isEmpty()) || (currentTab == TAB_STREAMING && streaming.isEmpty());
        gridEmpty.setVisibility(emptyGrid ? View.VISIBLE : View.GONE);
        gridEmpty.setText(currentTab == TAB_APPS ? R.string.grid_empty_apps : R.string.grid_empty_streaming);

        if (homeApps.isEmpty()) {
            appPreview.hide();
            heroTitle.setText(R.string.hero_no_apps);
            heroMeta.setText("");
        } else if (heroTitle.getText().length() == 0 || !homeView.hasFocus()) {
            setHero(homeApps.get(0));
        }
        if (initialFocusPending && currentTab == TAB_HOME && !homeApps.isEmpty()) {
            initialFocusPending = false;
            homeView.post(() -> {
                RecyclerView.ViewHolder first = homeView.findViewHolderForAdapterPosition(0);
                if (first != null) first.itemView.requestFocus();
            });
        }
    }

    private void setHero(AppModel app) {
        heroTitle.setText(app.getLabel());
        heroMeta.setText(homeIsFallback ? R.string.hero_empty_home : R.string.hero_hint);
        updateAppPreview();
    }

    private void updateGreeting() {
        if (currentTab == TAB_APPS) {
            greeting.setText(R.string.greeting_all_apps);
            greetingSub.setText(R.string.greeting_all_apps_subtitle);
            return;
        }
        if (currentTab == TAB_STREAMING) {
            greeting.setText(R.string.greeting_streaming);
            greetingSub.setText(R.string.greeting_streaming_subtitle);
            return;
        }
        if (currentTab == TAB_SHOWS) {
            greeting.setText(R.string.greeting_shows);
            greetingSub.setText(R.string.greeting_shows_subtitle);
            return;
        }
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(hour < 5 ? R.string.greeting_night : hour < 12 ? R.string.greeting_morning
                : hour < 18 ? R.string.greeting_afternoon : R.string.greeting_evening);
        greetingSub.setText(preferences.getLaunchCount() == 0 ? getString(R.string.greeting_first_launch)
                : getString(R.string.greeting_returning, preferences.getFavorites().size(), preferences.getHiddenApps().size()));
    }

    private Intent launchIntentFor(String packageName) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(packageName);
        return intent != null ? intent : pm.getLaunchIntentForPackage(packageName);
    }

    private String labelFor(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException exception) {
            return packageName;
        }
    }

    /** Launches an app, asking for the arrow code first when the app is locked. */
    void launchPackage(String packageName) {
        runUnlocked(packageName, () -> startApp(packageName));
    }

    private void startApp(String packageName) {
        Intent launch = launchIntentFor(packageName);
        if (launch == null) {
            Toast.makeText(this, getString(R.string.error_not_installed, labelFor(packageName)), Toast.LENGTH_SHORT).show();
            refreshApps(true);
            return;
        }
        try {
            startActivity(launch);
            onLaunched(packageName);
        } catch (ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, getString(R.string.error_launch_short, labelFor(packageName)), Toast.LENGTH_SHORT).show();
            refreshApps(true);
        }
    }

    @Override public void runUnlocked(String packageName, Runnable action) {
        if (preferences.isLocked(packageName)) {
            LockPad.verify(this, preferences, getString(R.string.lock_sub_app, labelFor(packageName)), action::run);
        } else {
            action.run();
        }
    }

    @Override public void onLaunched(String packageName) {
        preferences.recordLaunch(packageName);
    }

    @Override public void onAppClick(AppModel app) {
        if (movingPackage == null) launchPackage(app.getPackageName());
    }

    @Override public void onAppLongClick(AppModel app, String rowId) {
        if (movingPackage == null) showAppMenu(app, rowId);
    }

    private boolean rowContains(String rowId, String packageName) {
        for (AppModel model : row(rowId)) if (model.getPackageName().equals(packageName)) return true;
        return false;
    }

    private void showAppMenu(AppModel app, String rowId) {
        String pkg = app.getPackageName();
        String label = app.getLabel();
        boolean onHome = preferences.isFavorite(pkg);
        boolean streaming = rowContains(AppRepository.ROW_STREAMING, pkg);
        boolean locked = preferences.isLocked(pkg);
        // The fallback Home row shows suggestions, not a saved order, so it cannot be rearranged.
        String moveRow = AppRepository.ROW_HOME.equals(rowId) && homeIsFallback ? null : rowId;

        List<GlassMenu.Item> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(new GlassMenu.Item(R.drawable.ic_act_open, getString(R.string.action_open)));
        actions.add(() -> startApp(pkg));
        if (moveRow != null) {
            items.add(new GlassMenu.Item(R.drawable.ic_act_move, getString(R.string.action_move)));
            actions.add(() -> startMove(app, moveRow));
        }
        items.add(new GlassMenu.Item(R.drawable.ic_nav_home, getString(onHome ? R.string.action_remove_home : R.string.action_add_home)));
        actions.add(() -> {
            if (onHome) preferences.removeFavorite(pkg);
            else preferences.addFavorite(pkg);
            Toast.makeText(this, getString(onHome ? R.string.toast_removed_home : R.string.toast_added_home, label), Toast.LENGTH_SHORT).show();
            refreshApps(true);
        });
        items.add(new GlassMenu.Item(R.drawable.ic_act_swap, getString(streaming ? R.string.action_move_to_apps : R.string.action_move_to_streaming)));
        actions.add(() -> {
            preferences.setAppCategory(pkg, !streaming);
            Toast.makeText(this, getString(streaming ? R.string.toast_moved_apps : R.string.toast_moved_streaming, label), Toast.LENGTH_SHORT).show();
            refreshApps(true);
        });
        items.add(new GlassMenu.Item(R.drawable.ic_act_lock, getString(locked ? R.string.action_unlock : R.string.action_lock)));
        actions.add(() -> toggleLock(pkg, label, locked));
        items.add(new GlassMenu.Item(R.drawable.ic_act_hide, getString(R.string.action_hide)));
        actions.add(() -> {
            preferences.hideApp(pkg);
            refreshApps(true);
        });
        items.add(new GlassMenu.Item(R.drawable.ic_act_info, getString(R.string.action_info)));
        actions.add(() -> openAppInfo(pkg));
        items.add(new GlassMenu.Item(R.drawable.ic_act_delete, getString(R.string.action_uninstall)).danger());
        actions.add(() -> uninstall(pkg, label));
        GlassMenu.show(this, label, pkg, app.getIcon(), items, false, (index, item) -> runUnlocked(pkg, actions.get(index)));
    }

    private void toggleLock(String pkg, String label, boolean locked) {
        if (locked) {
            // The selected menu action has already verified the key.
            preferences.setLocked(pkg, false);
            refreshApps(true);
        } else if (!AppLock.hasCode(preferences)) {
            LockPad.create(this, preferences, () -> {
                preferences.setLocked(pkg, true);
                Toast.makeText(this, getString(R.string.toast_locked, label), Toast.LENGTH_SHORT).show();
                refreshApps(true);
            });
        } else {
            preferences.setLocked(pkg, true);
            Toast.makeText(this, getString(R.string.toast_locked, label), Toast.LENGTH_SHORT).show();
            refreshApps(true);
        }
    }

    private void openAppInfo(String pkg) {
        QuickActionService.suppressRedirect(15_000L);
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.error_app_details, Toast.LENGTH_SHORT).show();
        }
    }

    /** System apps cannot be removed through the normal dialog; show the ADB command instead. */
    private void uninstall(String pkg, String label) {
        boolean system;
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
            system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException exception) {
            refreshApps(true);
            return;
        }
        if (system) {
            Glass.info(this, label, getString(R.string.force_uninstall_message), DebloatManager.forceUninstallCommand(pkg), null, null);
        } else {
            QuickActionService.suppressRedirect(15_000L);
            DebloatManager.requestNormalUninstall(this, pkg);
        }
    }

    // ------------------------------------------------------------ move mode

    private AppRowAdapter adapterFor(String rowId) {
        if (AppRepository.ROW_HOME.equals(rowId)) return homeAdapter;
        if (AppRepository.ROW_STREAMING.equals(rowId)) return streamingAdapter;
        return appsAdapter;
    }

    private void startMove(AppModel app, String rowId) {
        appPreview.hide();
        List<AppModel> source = AppRepository.ROW_HOME.equals(rowId) ? homeApps : row(rowId);
        if (source.size() < 2) return;
        movingRow = rowId;
        movingPackage = app.getPackageName();
        movingList = new ArrayList<>(source);
        movingAdapter = adapterFor(rowId);
        movingStep = AppRepository.ROW_HOME.equals(rowId) ? 0 : GRID_COLUMNS;
        movingAdapter.setMovingPackage(movingPackage);
        moveHint.setVisibility(View.VISIBLE);
    }

    private void shiftMoving(int delta) {
        if (delta == 0 || movingList == null) return;
        int index = -1;
        for (int i = 0; i < movingList.size(); i++) {
            if (movingList.get(i).getPackageName().equals(movingPackage)) {
                index = i;
                break;
            }
        }
        int target = Math.max(0, Math.min(movingList.size() - 1, index + delta));
        if (index < 0 || target == index) return;
        AppModel item = movingList.remove(index);
        movingList.add(target, item);
        movingAdapter.setApps(movingList);
        SoundManager.playFocus(moveHint, preferences);
    }

    private void finishMove() {
        if (movingPackage == null) return;
        List<String> order = new ArrayList<>();
        for (AppModel model : movingList) order.add(model.getPackageName());
        preferences.setCustomOrder(movingRow, order);
        appliedHomeRevision = preferences.getHomeRevision();
        rows.put(movingRow, new ArrayList<>(movingList));
        if (AppRepository.ROW_HOME.equals(movingRow)) homeApps = new ArrayList<>(movingList);
        AppRowAdapter adapter = movingAdapter;
        movingPackage = null;
        movingRow = null;
        movingList = null;
        movingAdapter = null;
        adapter.setMovingPackage(null);
        moveHint.setVisibility(View.GONE);
        repository.invalidate();
    }

    // ---------------------------------------------------------------- IPTV

    private void loadIptv() {
        if (!preferences.isShowsEnabled()) {
            iptvGeneration++;
            countryChannels = new ArrayList<>();
            channelsLoaded = false;
            iptvFilter.setEnabled(false);
            iptvAdapter.setItems(null);
            setShowsStatus(R.string.shows_disabled);
            return;
        }
        if (iptvCountriesLoaded) {
            String defaultCountry = preferences.getShowsCountry();
            if (!defaultCountry.equalsIgnoreCase(selectedIptvCountry)) {
                for (IptvCountry country : iptvCountries) {
                    if (country.code.equalsIgnoreCase(defaultCountry)) {
                        selectIptvCountry(country);
                        return;
                    }
                }
            }
            if (selectedIptvCountry == null || selectedIptvCountry.isEmpty()) {
                selectInitialIptvCountry();
            } else if (!channelsLoaded || System.currentTimeMillis() - iptvLoadedAt > SHOWS_MAX_AGE_MS) {
                loadCountryChannels(selectedIptvCountry);
            } else {
                applyIptvFilters();
            }
            return;
        }
        if (iptvCountriesLoading) return;
        iptvCountriesLoading = true;
        setShowsStatus(R.string.iptv_loading_countries);
        iptvRepository.loadCountries((countries, failed) -> {
            iptvCountriesLoading = false;
            if (isFinishing() || isDestroyed()) return;
            if (failed || countries.isEmpty()) {
                setShowsStatus(R.string.iptv_country_error);
                return;
            }
            iptvCountries.clear();
            iptvCountries.add(new IptvCountry("ALL", getString(R.string.iptv_all_countries), "", java.util.Collections.emptyList()));
            iptvCountries.addAll(countries);
            List<String> labels = new ArrayList<>(countries.size());
            for (IptvCountry country : iptvCountries) labels.add(countryLabel(country));
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, labels);
            iptvCountry.setAdapter(adapter);
            iptvCountry.setDropDownBackgroundDrawable(new ColorDrawable(
                    ContextCompat.getColor(this, R.color.nova_card_hi)));
            iptvCountriesLoaded = true;
            selectInitialIptvCountry();
        });
    }

    private void selectInitialIptvCountry() {
        String wanted = preferences.getShowsCountry();
        IptvCountry selected = null;
        for (IptvCountry country : iptvCountries) {
            if (country.code.equalsIgnoreCase(wanted)) {
                selected = country;
                break;
            }
        }
        if (selected == null && !iptvCountries.isEmpty()) selected = iptvCountries.get(0);
        if (selected != null) selectIptvCountry(selected);
    }

    private String countryLabel(IptvCountry country) {
        return (country.flag.isEmpty() ? "" : country.flag + "  ") + country.name;
    }

    private void selectIptvCountry(IptvCountry country) {
        iptvCountry.setText(countryLabel(country), false);
        if (country.code.equals(selectedIptvCountry) && channelsLoaded) return;
        selectedIptvCountry = country.code;
        preferences.setShowsCountry(country.code);
        loadCountryChannels(country.code);
    }

    private void loadCountryChannels(String countryCode) {
        int request = ++iptvGeneration;
        channelsLoaded = false;
        countryChannels = new ArrayList<>();
        iptvFilter.setEnabled(false);
        iptvAdapter.setItems(null);
        setShowsStatus(R.string.iptv_loading_channels);
        iptvRepository.loadChannels(countryCode, (channels, failed) -> {
            if (request != iptvGeneration || isFinishing() || isDestroyed()) return;
            if (failed) {
                setShowsStatus(R.string.iptv_channels_error);
                return;
            }
            countryChannels = new ArrayList<>(channels.size());
            java.util.Set<String> favorites = preferences.getIptvFavorites();
            for (IptvChannel channel : channels) {
                countryChannels.add(copyChannel(channel, favorites.contains(iptvFavoriteKey(channel))));
            }
            channelsLoaded = true;
            iptvLoadedAt = System.currentTimeMillis();
            iptvFilter.setEnabled(true);
            applyIptvFilters();
        });
    }

    private String categoryLabel(String key) {
        if ("all".equals(key)) return getString(R.string.iptv_all_categories);
        if ("favorites".equals(key)) return getString(R.string.iptv_favorites);
        return key.substring("category:".length());
    }

    private void openCategoryMenu() {
        java.util.Set<String> genres = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        java.util.Collections.addAll(genres, "Movies", "Music", "18+");
        for (IptvChannel channel : countryChannels) genres.addAll(channel.genres);
        List<String> keys = new ArrayList<>();
        keys.add("all");
        keys.add("favorites");
        for (String genre : genres) keys.add("category:" + genre);
        List<GlassMenu.Item> items = new ArrayList<>();
        for (String key : keys) items.add(new GlassMenu.Item(R.drawable.ic_tv, categoryLabel(key))
                .checked(key.equals(selectedIptvCategory)));
        GlassMenu.show(this, getString(R.string.iptv_all_categories), null, null, items, false,
                (index, item) -> {
                    selectedIptvCategory = keys.get(index);
                    applyIptvFilters();
                    showsView.scrollToPosition(0);
                });
    }

    private void applyIptvFilters() {
        if (iptvAdapter == null || !channelsLoaded) return;
        iptvFilter.setText(categoryLabel(selectedIptvCategory));
        List<IptvChannel> filtered = new ArrayList<>();
        for (IptvChannel channel : countryChannels) {
            if ("favorites".equals(selectedIptvCategory) && !channel.isFavorite) continue;
            if (selectedIptvCategory.startsWith("category:")
                    && !channel.genres.contains(selectedIptvCategory.substring("category:".length()))) continue;
            if (!iptvSearchQuery.isEmpty()
                    && !channel.name.toLowerCase(java.util.Locale.ROOT).contains(iptvSearchQuery)) continue;
            filtered.add(channel);
        }
        iptvAdapter.setItems(filtered);
        if (filtered.isEmpty()) {
            int message = countryChannels.isEmpty() ? R.string.iptv_no_channels : R.string.iptv_no_results;
            setShowsStatus(message);
        } else {
            showsStatus.setVisibility(View.GONE);
        }
    }

    private void toggleIptvFavorite(IptvChannel channel, boolean favorite) {
        preferences.setIptvFavorite(iptvFavoriteKey(channel), favorite);
        for (int i = 0; i < countryChannels.size(); i++) {
            IptvChannel current = countryChannels.get(i);
            if (current.id == channel.id) {
                countryChannels.set(i, copyChannel(current, favorite));
                break;
            }
        }
        applyIptvFilters();
        updateHomeIptvFavorites();
        Toast.makeText(this, favorite ? R.string.iptv_favorite_added : R.string.iptv_favorite_removed,
                Toast.LENGTH_SHORT).show();
    }

    private String iptvFavoriteKey(IptvChannel channel) {
        return channel.country + ":" + channel.id;
    }

    private void updateHomeIptvFavorites() {
        if (homeIptvFavorites == null) return;
        int count = preferences.getIptvFavorites().size();
        homeIptvFavorites.setText(getString(R.string.iptv_home_favorites, count));
        homeIptvFavorites.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private void openHomeIptvFavorites() {
        Map<String, Integer> counts = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String favorite : preferences.getIptvFavorites()) {
            int separator = favorite.indexOf(':');
            if (separator > 0) counts.merge(favorite.substring(0, separator), 1, Integer::sum);
        }
        if (counts.isEmpty()) return;
        List<String> countries = new ArrayList<>(counts.keySet());
        List<GlassMenu.Item> items = new ArrayList<>(countries.size());
        for (String code : countries) {
            String label = null;
            for (IptvCountry country : iptvCountries) {
                if (country.code.equalsIgnoreCase(code)) {
                    label = countryLabel(country);
                    break;
                }
            }
            if (label == null && "ALL".equals(code)) label = getString(R.string.iptv_all_countries);
            if (label == null) {
                String name = new java.util.Locale.Builder().setRegion(code).build().getDisplayCountry();
                label = name.isEmpty() ? code : name + " (" + code + ")";
            }
            items.add(new GlassMenu.Item(R.drawable.ic_web,
                    label + " · " + counts.get(code)));
        }
        GlassMenu.show(this, getString(R.string.iptv_favorites),
                getString(R.string.iptv_favorites_country_hint), null, items, false,
                (index, item) -> {
                    preferences.setShowsCountry(countries.get(index));
                    selectedIptvCategory = "favorites";
                    selectTab(TAB_SHOWS);
                });
    }

    private IptvChannel copyChannel(IptvChannel channel, boolean favorite) {
        return new IptvChannel(channel.id, channel.name, channel.logo, channel.category, channel.genres,
                channel.country, channel.language, channel.url, favorite, channel.currentProgram,
                channel.nextProgram, channel.currentTime, channel.nextTime);
    }

    private void playIptvChannel(IptvChannel channel) {
        if (channel.url == null || channel.url.isEmpty()) return;
        Intent play = new Intent(this, IptvPlayerActivity.class);
        play.putExtra(IptvPlayerActivity.EXTRA_CHANNEL_NAME, channel.name);
        play.putExtra(IptvPlayerActivity.EXTRA_COUNTRY, channel.country);
        play.putExtra(IptvPlayerActivity.EXTRA_STREAM_URL, channel.url);
        startActivity(play);
    }

    private void setShowsStatus(int textRes) {
        showsStatus.setText(textRes);
        showsStatus.setVisibility(View.VISIBLE);
    }

    private StateListDrawable chipBackground() {
        int accent = accent();
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_selected, android.R.attr.state_focused},
                pill(accent, ContextCompat.getColor(this, R.color.nova_text), 2));
        states.addState(new int[]{android.R.attr.state_selected}, pill(accent, accent, 1));
        states.addState(new int[]{android.R.attr.state_focused},
                pill(ContextCompat.getColor(this, R.color.nova_card_hi), accent, 2));
        states.addState(new int[]{}, pill(ContextCompat.getColor(this, R.color.glass_plate),
                ContextCompat.getColor(this, R.color.glass_card_stroke), 1));
        return states;
    }

    private GradientDrawable pill(int fill, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(16));
        drawable.setColor(fill);
        drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
    }
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
