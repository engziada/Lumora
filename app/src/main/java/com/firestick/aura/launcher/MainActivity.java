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
import com.firestick.aura.launcher.adapter.GuideAdapter;
import com.firestick.aura.launcher.adapter.GuideRailAdapter;
import com.firestick.aura.launcher.assistant.AssistantSettings;
import com.firestick.aura.launcher.assistant.LongPress;
import com.firestick.aura.launcher.manager.AppLock;
import com.firestick.aura.launcher.manager.AppRepository;
import com.firestick.aura.launcher.manager.DebloatManager;
import com.firestick.aura.launcher.manager.FastGuide;
import com.firestick.aura.launcher.manager.ProgramRatings;
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
import com.firestick.aura.launcher.ui.GuidePreview;
import com.firestick.aura.launcher.ui.GuideText;
import com.firestick.aura.launcher.ui.RatingBadge;
import com.firestick.aura.launcher.ui.LockPad;
import com.firestick.aura.launcher.ui.MoviesTab;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity
        implements AppRowAdapter.OnAppActionListener, ShowDetails.Host {
    /** Package to launch on arrival; sent by the assistant so locks and launch history apply. */
    public static final String EXTRA_LAUNCH_PACKAGE = "com.firestick.aura.launcher.extra.LAUNCH_PACKAGE";
    private static final int TAB_HOME = 0, TAB_SHOWS = 1, TAB_APPS = 2, TAB_STREAMING = 3, TAB_MOVIES = 4;
    private static final int GRID_COLUMNS = 7, HOME_FALLBACK = 8;
    /** How long focus rests on a guide category before the guide switches to it. */
    private static final long RAIL_REST_MS = 250L;
    private static final String ADULTS = "category:18+";
    private static final long SHOWS_MAX_AGE_MS = 60L * 60L * 1000L;
    private static final long GEAR_DELAY_MS = 350L, GEAR_KEY_WINDOW_MS = 600L, GEAR_REOPEN_GUARD_MS = 900L;
    /** Bridge targets for a DOWN key that a tile of the ribbon or a grid cannot resolve. */
    private static final int[] NAV_TARGETS = {R.id.navHome, R.id.navShows, R.id.navFilms, R.id.navApps,
            R.id.navMovies};

    private com.firestick.aura.launcher.ui.HomeRows homeRows;
    private Object lastHomeFocusTag;
    private long appsLoadedAt;
    private final Map<Integer, View> tabFocus = new HashMap<>();
    private final List<IptvChannel> visibleChannels = new ArrayList<>();
    private int onlineChecked, onlineTotal, onlinePending;
    private final android.os.Handler filterHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updateOnline = this::applyIptvFilters;
    private RecyclerView homeView, gridView, showsView;
    private AppRowAdapter homeAdapter, appsAdapter, streamingAdapter;
    private GuideAdapter guideAdapter;
    private GuideRailAdapter railAdapter;
    private RecyclerView railView;
    private GuidePreview guidePreview;
    private AppRepository repository;
    private IptvRepository iptvRepository;
    private PreferenceManager preferences;
    private AssistantSettings assistant;
    private WallpaperManager wallpapers;
    private QuickMenu quickMenu;
    private AppPreview appPreview;
    private boolean previewResumed;
    private final ViewTreeObserver.OnGlobalFocusChangeListener previewFocusListener =
            (oldFocus, newFocus) -> {
                if (newFocus != null && insideRecyclerView(newFocus)) tabFocus.put(MainActivity.this.currentTab, newFocus);
                updateAppPreview();
            };
    private TextView greeting, greetingSub, heroTitle, heroMeta, showsStatus, gridEmpty, moveHint,
            homeIptvFavorites;
    private ImageView assistantIcon;
    private AutoCompleteTextView iptvCountry;
    private EditText iptvSearch;
    private View homeScroll, homePanel, gridPanel, showsPanel, moviesPanel, gear, assistantButton;
    private MoviesTab moviesTab;
    private TextView guideEyebrow, guideTitle, guideMeta, guideSynopsis;
    private ProgramRatings programRatings;
    private android.widget.ProgressBar guideProgress;
    private View greetingBlock;
    /** The guide row focus last rested on; the top of the guide describes it. */
    private long guideFocusedId = Long.MIN_VALUE;
    private String pendingRailKey;
    /** Set by the screen-off broadcast; the guide preview stays stopped until a key press. */
    private boolean screenOff;
    private final Runnable applyRail = () -> {
        if (pendingRailKey != null) pickIptvCategory(pendingRailKey, false);
    };
    /** Moves the guide to the next minute while it is on screen. */
    private final Runnable guideTick = new Runnable() {
        @Override public void run() {
            if (!previewResumed || currentTab != TAB_SHOWS || guideAdapter == null) return;
            guideAdapter.tick();
            updateGuideInfo();
            scheduleGuideTick();
        }
    };
    private android.app.Dialog countryPicker;
    private PopupWindow gearMenu;
    private final Map<String, List<AppModel>> rows = new HashMap<>();
    private final LongPress menuPress = new LongPress();
    private AppModel focusedHeroApp;
    private List<AppModel> homeApps = new ArrayList<>();
    private final List<IptvCountry> iptvCountries = new ArrayList<>();
    private List<IptvChannel> countryChannels = new ArrayList<>();
    private String selectedIptvCountry, selectedIptvCategory = "all", iptvSearchQuery = "";
    private String sourceRevision = "";
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
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                appPreview.hide();
                screenOff = true;
                if (guidePreview != null) guidePreview.release();
            } else homeView.post(MainActivity.this::updateAppPreview);
        }
    };
    private boolean screenReceiverRegistered;
    /** Whether onCreate applied the opaque Reduce transparency theme; a change needs a recreate. */
    private boolean appliedSolid;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        appliedSolid = Glass.applyTheme(this);
        setContentView(R.layout.activity_main);
        preferences = new PreferenceManager(this);
        assistant = new AssistantSettings(this);
        repository = new AppRepository(this);
        iptvRepository = new IptvRepository(this);
        quickMenu = new QuickMenu(this, preferences, this::launchPackage);
        SoundManager.init(this);
        initViews();
        registerPackageReceiver();
        registerScreenReceiver();
        wallpapers = new WallpaperManager(this, preferences,
                findViewById(R.id.imgWallpaperBack), findViewById(R.id.imgWallpaperFront));
        // The vignette darkens the wallpaper around the dock; its frost gets a matching shade.
        wallpapers.frost().apply(findViewById(R.id.navDock), 26f, R.attr.lumoraGlassBar,
                ContextCompat.getColor(this, R.color.glass_shade));
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
        findViewById(R.id.updateBadge).setOnClickListener(view ->
                com.firestick.aura.launcher.ui.UpdateFlow.checkNow(this, this::updateBadge));
        greeting = findViewById(R.id.txtGreeting);
        greetingSub = findViewById(R.id.txtGreetingSub);
        heroTitle = findViewById(R.id.txtHeroTitle);
        heroMeta = findViewById(R.id.txtHeroMeta);
        moveHint = findViewById(R.id.txtMoveHint);
        homeScroll = findViewById(R.id.homeScroll);
        homePanel = findViewById(R.id.homePanel);
        gridPanel = findViewById(R.id.gridPanel);
        gridEmpty = findViewById(R.id.txtGridEmpty);

        homeView = findViewById(R.id.recyclerHome);
        homeView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        homeAdapter = new AppRowAdapter(preferences, this, AppRepository.ROW_HOME, AppRowAdapter.STYLE_WIDE,
                (rowId, app) -> setHero(app));
        homeView.setAdapter(homeAdapter);
        homeScroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> updateAppPreview());
        homeView.setNestedScrollingEnabled(false);
        homeView.setItemAnimator(null);
        homeRows = new com.firestick.aura.launcher.ui.HomeRows(this, preferences, this, this::playIptvChannel, this::setHero);
        findViewById(R.id.globalSearchButton).setOnClickListener(view -> startActivity(new Intent(this, SearchActivity.class)));
        homeIptvFavorites = findViewById(R.id.btnHomeIptvFavorites);
        homeIptvFavorites.setOnClickListener(view -> {
            SoundManager.playClick(view, preferences);
            openHomeIptvFavorites();
        });
        homeIptvFavorites.setOnFocusChangeListener((view, focused) -> {
            view.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f)
                    .setDuration(preferences.reducedMotion() ? 0 : 120).start();
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
        moviesPanel = findViewById(R.id.moviesPanel);
        moviesTab = new MoviesTab(this, preferences);
        greetingBlock = findViewById(R.id.greetingBlock);
        findViewById(R.id.iptvRefresh).setOnClickListener(v -> {
            onlineScanGeneration++;
            onlineScanRunning = false;
            onlineAttempted.clear();
            com.firestick.aura.launcher.manager.StreamAvailability.clear();
            channelsLoaded = false;
            loadCountryChannels(selectedIptvCountry == null ? preferences.getShowsCountry() : selectedIptvCountry, true);
        });
        showsStatus = findViewById(R.id.txtShowsStatus);
        initGuide();
        iptvCountry = findViewById(R.id.iptvCountry);
        iptvCountry.setThreshold(0);
        // Right would walk the caret through the country name first; Down enters the selected category.
        iptvCountry.setOnKeyListener((view, code, event) -> {
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) iptvSearch.requestFocus();
                return true;
            }
            return code == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN && focusSelectedRail();
        });
        iptvCountry.setOnClickListener(view -> openCountryPicker());
        iptvSearch = findViewById(R.id.iptvSearch);
        iptvSearch.setOnKeyListener((view, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_LEFT || iptvSearch.getSelectionStart() > 0) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) iptvCountry.requestFocus();
            return true;
        });
        iptvSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                iptvSearchQuery = s == null ? "" : s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                onlineScanGeneration++;
                onlineScanRunning = false;
                filterHandler.removeCallbacks(updateOnline);
                filterHandler.postDelayed(updateOnline, 200);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        bindNav(R.id.navHome, TAB_HOME);
        bindNav(R.id.navShows, TAB_SHOWS);
        bindNav(R.id.navFilms, TAB_MOVIES);
        bindNav(R.id.navApps, TAB_APPS);
        bindNav(R.id.navMovies, TAB_STREAMING);
        View quick = findViewById(R.id.navQuick);
        quick.setOnClickListener(view -> {
            SoundManager.playClick(view, preferences);
            quickMenu.show();
        });
        quick.setOnFocusChangeListener((view, focused) -> {
            findViewById(R.id.quickTooltip).setVisibility(focused ? View.VISIBLE : View.GONE);
            view.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f).setDuration(preferences.reducedMotion() ? 0 : 120).start();
            if (focused) SoundManager.playFocus(view, preferences);
        });

        assistantButton = findViewById(R.id.btnAssistant);
        assistantIcon = findViewById(R.id.imgAssistant);
        assistantButton.setOnClickListener(view -> {
            SoundManager.playClick(view, preferences);
            openAssistant();
        });
        assistantButton.setOnFocusChangeListener((view, focused) -> {
            view.animate().scaleX(focused ? 1.14f : 1f).scaleY(focused ? 1.14f : 1f).setDuration(preferences.reducedMotion() ? 0 : 140).start();
            if (focused) SoundManager.playFocus(view, preferences);
        });

        gear = findViewById(R.id.btnGear);
        gear.setOnClickListener(view -> showGearMenu());
        gear.setOnFocusChangeListener((view, focused) -> {
            view.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f).setDuration(preferences.reducedMotion() ? 0 : 120).start();
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
        // On Live TV and Movies, UP returns to the card focus left rather than the nearest edge row.
        view.setOnKeyListener((source, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_UP || event.getAction() != KeyEvent.ACTION_DOWN
                    || movingPackage != null) {
                return false;
            }
            if (currentTab == TAB_SHOWS) {
                return guideAdapter != null && guideAdapter.getItemCount() > 0 && focusGuideRow();
            }
            if (currentTab != TAB_MOVIES) return false;
            View remembered = tabFocus.get(TAB_MOVIES);
            return remembered != null && remembered.isShown() && inside(remembered, moviesPanel)
                    && remembered.requestFocus();
        });
        bindNavFocus(view);
    }

    private void bindNavFocus(View view) {
        view.setOnFocusChangeListener((source, focused) -> {
            source.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(preferences.reducedMotion() ? 0 : 120).start();
            if (focused) SoundManager.playFocus(source, preferences);
        });
    }

    // ------------------------------------------------------------ assistant

    private void openAssistant() {
        Glass.handOff(this);
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
        if (focused == null) return false;
        // Leaving the shelves lands on Movies; this also covers the 18+ pill outside the lists.
        if (currentTab == TAB_MOVIES && inside(focused, moviesPanel)) {
            View next = focused.focusSearch(View.FOCUS_DOWN);
            if (next != null && inside(next, moviesPanel)) return false;
            View nav = findViewById(R.id.navFilms);
            return nav.isShown() && nav.requestFocus(View.FOCUS_DOWN);
        }
        if (!insideRecyclerView(focused)) return false;
        View next = focused.focusSearch(View.FOCUS_DOWN);
        // Leaving the guide lands on its own tab, so OK after overshooting the rail cannot switch tabs.
        if (currentTab == TAB_SHOWS && inside(focused, showsPanel)) {
            if (next != null && inside(next, showsPanel)) return false;
            View nav = findViewById(R.id.navShows);
            return nav.isShown() && nav.requestFocus(View.FOCUS_DOWN);
        }
        if (next != null) return false;
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
            if (parent instanceof RecyclerView) return true;
        }
        return false;
    }

    /**
     * A held arrow runs the rail, the rows or the film shelves to their end and stops there. Without
     * this, the key repeat carries on into the toolbar, the header or the nav, where OK does something else.
     */
    private boolean heldAtGuideEdge(int code) {
        if (code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
        View focused = getCurrentFocus();
        if (focused == null) return false;
        View area = currentTab == TAB_MOVIES ? moviesPanel
                : currentTab != TAB_SHOWS ? null
                : inside(focused, railView) ? railView : inside(focused, showsView) ? showsView : null;
        if (area == null || !inside(focused, area)) return false;
        View next = focused.focusSearch(code == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN);
        return next == null || !inside(next, area);
    }

    private static boolean inside(View view, View ancestor) {
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == ancestor) return true;
        }
        return false;
    }

    // ----------------------------------------------------------------- tabs

    public void selectTab(int tab) {
        if (movingPackage != null) finishMove();
        if (currentTab == tab) {
            if (tab == TAB_SHOWS) showsView.smoothScrollToPosition(0);
            else if (tab == TAB_HOME) homeView.smoothScrollToPosition(0);
            else if (tab == TAB_MOVIES) moviesTab.scrollToTop();
            else gridView.smoothScrollToPosition(0);
            return;
        }
        currentTab = tab;
        applyTabVisibility();
        updateGreeting();
        updateNavSelection();
        if (tab == TAB_SHOWS) loadIptv();
        else if (tab == TAB_MOVIES) moviesTab.show();
        else applyRows();
        View remembered = tabFocus.get(tab);
        if (remembered != null) remembered.post(() -> { if (remembered.isShown()) remembered.requestFocus(); });
    }

    private void applyTabVisibility() {
        if (currentTab != TAB_HOME && appPreview != null) appPreview.hide();
        int home = currentTab == TAB_HOME ? View.VISIBLE : View.GONE;
        homePanel.setVisibility(home);
        // Left visible but empty, the ScrollView would take focus above the nav on other tabs.
        homeScroll.setVisibility(home);
        boolean shows = currentTab == TAB_SHOWS;
        boolean movies = currentTab == TAB_MOVIES;
        showsPanel.setVisibility(shows ? View.VISIBLE : View.GONE);
        moviesPanel.setVisibility(movies ? View.VISIBLE : View.GONE);
        // The guide and the shelves need the height; their own headers name the item in focus.
        greetingBlock.setVisibility(shows || movies ? View.GONE : View.VISIBLE);
        if (!shows && guidePreview != null) guidePreview.release();
        // Rows hidden or paused for a while show old times until the next minute; move them now.
        if (shows && guideAdapter != null) guideAdapter.tick();
        scheduleGuideTick();
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
        styleNav(R.id.txtNavFilms, R.id.imgNavFilms, currentTab == TAB_MOVIES);
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
        filterHandler.removeCallbacksAndMessages(null);
        onlineScanGeneration++;
        if (quickMenu != null) quickMenu.dismiss();
        if (appPreview != null) appPreview.destroy();
        if (guidePreview != null) guidePreview.release();
        if (moviesTab != null) moviesTab.release();
        if (programRatings != null) programRatings.shutdown();
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
        if (appliedSolid != PreferenceManager.reduceTransparency(this)) {
            // Reduce transparency changed in Settings; themes only apply at creation.
            recreate();
            return;
        }
        String sources = getSharedPreferences("iptv_sources", MODE_PRIVATE).getAll().toString();
        if (!sourceRevision.equals(sources)) { sourceRevision = sources; channelsLoaded = false; }
        previewResumed = true;
        screenOff = false;
        // Keeps the quick-actions service on once WRITE_SECURE_SETTINGS has been granted over ADB.
        QuickAccess.ensureServiceEnabled(this);
        boolean changed = appliedHomeRevision != preferences.getHomeRevision();
        appliedHomeRevision = preferences.getHomeRevision();
        applyTabVisibility();
        refreshApps(changed || SystemClock.elapsedRealtime() - appsLoadedAt > 60000);
        homeRows.render(rows);
        updateGreeting();
        updateNavSelection();
        updateAssistantButton();
        updateBadge();
        updateHomeIptvFavorites();
        if (currentTab == TAB_SHOWS) loadIptv();
        else if (currentTab == TAB_MOVIES) moviesTab.show();
        if (wallpapers != null) wallpapers.start();
        homeView.post(this::updateAppPreview);
        homePanel.post(() -> {
            if (lastHomeFocusTag != null && currentTab == TAB_HOME) {
                View previous = homePanel.findViewWithTag(lastHomeFocusTag);
                if (previous != null && previous.isShown()) previous.requestFocus();
            }
        });
    }

    @Override protected void onStop() {
        adultUnlocked = false;
        if (moviesTab != null) moviesTab.stop();
        if (ADULTS.equals(selectedIptvCategory)) selectedIptvCategory = "all";
        onlineScanGeneration++;
        onlineScanRunning = false;
        super.onStop();
    }

    @Override protected void onPause() {
        if (countryPicker != null) countryPicker.dismiss();
        if (currentTab == TAB_HOME && getCurrentFocus() != null) lastHomeFocusTag = getCurrentFocus().getTag();
        previewResumed = false;
        appPreview.hide();
        filterHandler.removeCallbacks(guideTick);
        filterHandler.removeCallbacks(applyRail);
        if (guidePreview != null) guidePreview.release();
        if (movingPackage != null) finishMove();
        if (gearMenu != null) gearMenu.dismiss();
        if (wallpapers != null) wallpapers.stop();
        menuPress.up();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (appPreview == null) return;
        if (currentTab == TAB_SHOWS && guidePreview != null) {
            if (hasFocus) updateGuideInfo();
            else guidePreview.stop();
        }
        if (hasFocus) updateBadge();
        if (!hasFocus) appPreview.hide();
        else homeView.post(this::updateAppPreview);
    }

    private void updateAppPreview() {
        if (appPreview == null) return;
        AppModel focusedHomeApp = focusedHeroApp;
        if (!previewResumed || !hasWindowFocus() || currentTab != TAB_HOME || !homePanel.hasFocus()
                || !findViewById(R.id.homeHeroArea).getGlobalVisibleRect(new android.graphics.Rect())
                || preferences.reducedMotion() || !preferences.isAppPreviewEnabled() || movingPackage != null || focusedHomeApp == null) {
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
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0
                && movingPackage == null && heldAtGuideEdge(event.getKeyCode())) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && movingPackage == null) {
            if (bridgeFocusToNav()) return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            lastKeyDownAt = SystemClock.uptimeMillis();
            screenOff = false;
        }
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
            appsLoadedAt = SystemClock.elapsedRealtime();
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
        homeRows.render(rows);
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
        focusedHeroApp = app;
        heroTitle.setText(app.getLabel());
        heroMeta.setText(homeIsFallback ? R.string.hero_empty_home : R.string.hero_hint);
        updateAppPreview();
    }

    private void updateBadge() {
        com.firestick.aura.launcher.update.UpdateManager manager = new com.firestick.aura.launcher.update.UpdateManager(this);
        findViewById(R.id.updateBadge).setVisibility(
                manager.latestVersionCode() > manager.installedVersionCode() ? View.VISIBLE : View.GONE);
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
        String moveRow = (AppRepository.ROW_HOME.equals(rowId) && homeIsFallback) || "recent".equals(rowId) ? null : rowId;

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
        items.add(new GlassMenu.Item(R.drawable.ic_act_info, getString(R.string.action_storage)));
        actions.add(() -> openAppInfo(pkg));
        items.add(new GlassMenu.Item(R.drawable.ic_act_info, getString(R.string.action_notifications)));
        actions.add(() -> {
            try {
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, pkg));
            } catch (ActivityNotFoundException | SecurityException unavailable) { openAppInfo(pkg); }
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
            visibleChannels.clear();
            railAdapter.setEntries(new ArrayList<>(), selectedIptvCategory);
            guideAdapter.setItems(null);
            updateGuideInfo();
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
            iptvCountriesLoaded = true;
            selectInitialIptvCountry();
        });
    }

    private void openCountryPicker() {
        if (iptvCountries.isEmpty()) { loadIptv(); return; }
        if (countryPicker != null && countryPicker.isShowing()) return;
        countryPicker = com.firestick.aura.launcher.ui.CountryPicker.show(this,
                new ArrayList<>(iptvCountries), selectedIptvCountry, this::selectIptvCountry);
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

    private void loadCountryChannels(String countryCode) { loadCountryChannels(countryCode, false); }

    private void loadCountryChannels(String countryCode, boolean refresh) {
        onlineScanGeneration++;
        onlineScanRunning = false;
        onlineAttempted.clear();
        int request = ++iptvGeneration;
        channelsLoaded = false;
        // Keep the existing rows attached while the next country loads. Clearing a focused
        // RecyclerView here makes Fire TV's D-pad focus jump to an unrelated control or vanish.
        // The loading status labels the transition; the new rows replace these atomically below.
        setShowsStatus(R.string.iptv_loading_channels);
        IptvRepository.ChannelsCallback loaded = (channels, failed) -> {
            if (request != iptvGeneration || isFinishing() || isDestroyed()) return;
            if (failed) {
                setShowsStatus(R.string.iptv_channels_error);
                return;
            }
            countryChannels = new ArrayList<>(channels.size());
            java.util.Set<String> favorites = preferences.getIptvFavorites();
            for (IptvChannel channel : channels) {
                String worldwideKey = "ALL:" + channel.id;
                boolean favorite = favorites.contains(iptvFavoriteKey(channel)) || favorites.contains(worldwideKey);
                if (!worldwideKey.equals(iptvFavoriteKey(channel)) && favorites.contains(worldwideKey))
                    preferences.setIptvFavorite(worldwideKey, false);
                for (Long old : channel.legacyIds) {
                    for (String prefix : new String[]{channel.country, "ALL"}) {
                        String legacy = prefix + ":" + old;
                        if (favorites.contains(legacy)) {
                            favorite = true;
                            preferences.setIptvFavorite(legacy, false);
                        }
                    }
                }
                if (favorite) preferences.setIptvFavorite(iptvFavoriteKey(channel), true);
                countryChannels.add(copyChannel(channel, favorite));
            }
            channelsLoaded = true;
            iptvLoadedAt = System.currentTimeMillis();
            buildRail();
            applyIptvFilters();
        };
        if (refresh) iptvRepository.refreshChannels(countryCode, loaded);
        else iptvRepository.loadChannels(countryCode, loaded);
    }

    // ------------------------------------------------------------- Live TV guide

    private void initGuide() {
        guideEyebrow = findViewById(R.id.guideEyebrow);
        guideTitle = findViewById(R.id.guideTitle);
        guideMeta = findViewById(R.id.guideMeta);
        guideProgress = findViewById(R.id.guideProgress);
        guideSynopsis = findViewById(R.id.guideSynopsis);
        guidePreview = new GuidePreview(findViewById(R.id.guidePreview), preferences);
        railView = findViewById(R.id.guideRail);
        railView.setLayoutManager(new LinearLayoutManager(this));
        railView.setItemAnimator(null);
        // RecyclerView makes itself focusable; empty, it would take focus and show no focus ring.
        railView.setFocusable(false);
        railAdapter = new GuideRailAdapter(preferences, new GuideRailAdapter.Listener() {
            @Override public void onRest(String key) {
                pendingRailKey = key;
                filterHandler.removeCallbacks(applyRail);
                // Adults asks for the code, so passing over it never opens it.
                if (!ADULTS.equals(key)) filterHandler.postDelayed(applyRail, RAIL_REST_MS);
            }

            @Override public void onPick(String key) {
                filterHandler.removeCallbacks(applyRail);
                pickIptvCategory(key, true);
            }

            @Override public boolean onRight(String key) {
                filterHandler.removeCallbacks(applyRail);
                if (key.equals(selectedIptvCategory)) return focusGuideRow();
                pickIptvCategory(key, true);
                return true;
            }
        });
        railView.setAdapter(railAdapter);
        showsView = findViewById(R.id.recyclerShows);
        showsView.setLayoutManager(new LinearLayoutManager(this));
        showsView.setItemAnimator(null);
        showsView.setFocusable(false);
        guideAdapter = new GuideAdapter(preferences, new GuideAdapter.Listener() {
            @Override public void onPlay(IptvChannel channel) {
                playIptvChannel(channel);
            }

            @Override public void onFavorite(IptvChannel channel, boolean favorite) {
                toggleIptvFavorite(channel, favorite);
            }

            @Override public void onFocus(IptvChannel channel) {
                guideFocusedId = channel.id;
                updateGuideInfo();
            }

            @Override public boolean onLeft() {
                return focusSelectedRail();
            }

            @Override public boolean onUpFromTop() {
                return iptvSearch.requestFocus();
            }
        });
        showsView.setAdapter(guideAdapter);
    }

    /**
     * Rebuilds the category rail from the loaded channels: All channels, Favorites, genres A to Z,
     * then Adults last. Adult rows count only toward Adults, which matches what each one lists.
     */
    private void buildRail() {
        java.util.TreeMap<String, Integer> genres = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        java.util.Set<String> seen = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        int all = 0, favorites = 0, adults = 0;
        for (IptvChannel channel : countryChannels) {
            if (channel.genres.contains("18+")) {
                adults++;
                continue;
            }
            all++;
            if (channel.isFavorite) favorites++;
            seen.clear();
            for (String genre : channel.genres) {
                String name = genre.trim();
                if (!name.isEmpty() && seen.add(name)) genres.merge(name, 1, Integer::sum);
            }
        }
        List<GuideRailAdapter.Entry> entries = new ArrayList<>(genres.size() + 3);
        entries.add(new GuideRailAdapter.Entry("all", getString(R.string.guide_all_channels), all));
        entries.add(new GuideRailAdapter.Entry("favorites", getString(R.string.iptv_favorites), favorites));
        Integer other = genres.remove(IptvChannel.OTHER);
        for (Map.Entry<String, Integer> genre : genres.entrySet()) {
            entries.add(new GuideRailAdapter.Entry("category:" + genre.getKey(), genre.getKey(), genre.getValue()));
        }
        if (other != null) {
            entries.add(new GuideRailAdapter.Entry("category:" + IptvChannel.OTHER, IptvChannel.OTHER, other));
        }
        entries.add(new GuideRailAdapter.Entry(ADULTS, getString(R.string.iptv_adults), adults));
        // Another country may spell a genre differently or lack it; fall back to All channels.
        if (selectedIptvCategory.startsWith("category:") && !ADULTS.equals(selectedIptvCategory)) {
            String name = selectedIptvCategory.substring("category:".length());
            String spelled = genres.ceilingKey(name);
            if (IptvChannel.OTHER.equalsIgnoreCase(name)) spelled = other != null ? IptvChannel.OTHER : null;
            selectedIptvCategory = spelled != null && spelled.equalsIgnoreCase(name) ? "category:" + spelled : "all";
        }
        railAdapter.setEntries(entries, selectedIptvCategory);
    }

    /** Switches the guide to a category. Adults needs the lock code once per visit. */
    private void pickIptvCategory(String key, boolean focusRows) {
        pendingRailKey = null;
        Runnable apply = () -> {
            if (!key.equals(selectedIptvCategory)) {
                onlineScanGeneration++;
                onlineScanRunning = false;
                selectedIptvCategory = key;
                guideFocusedId = Long.MIN_VALUE;
                applyIptvFilters();
                showsView.scrollToPosition(0);
            }
            if (focusRows) focusGuideRow();
        };
        if (ADULTS.equals(key) && !adultUnlocked) {
            LockPad.verify(this, preferences, getString(R.string.iptv_adults_unlock), () -> {
                adultUnlocked = true;
                apply.run();
            });
        } else {
            apply.run();
        }
    }

    /** Moves focus into the guide: the row focus last rested on when in view, else the first row in view. */
    private boolean focusGuideRow() {
        if (guideAdapter.getItemCount() == 0) return true;
        if (showsView.hasPendingAdapterUpdates() || showsView.isLayoutRequested()) {
            showsView.post(this::focusGuideRowNow);
            return true;
        }
        return focusGuideRowNow();
    }

    private boolean focusGuideRowNow() {
        if (guideAdapter.getItemCount() == 0) return false;
        LinearLayoutManager layout = (LinearLayoutManager) showsView.getLayoutManager();
        int first = layout == null ? 0 : Math.max(0, layout.findFirstCompletelyVisibleItemPosition());
        int last = layout == null ? 0 : layout.findLastCompletelyVisibleItemPosition();
        int position = guideAdapter.indexOf(guideFocusedId);
        if (position < first || position > last) position = first;
        return focusPosition(showsView, position);
    }

    /** Returns focus to the selected category in the rail. */
    private boolean focusSelectedRail() {
        if (railAdapter.getItemCount() == 0) return false;
        return focusPosition(railView, Math.max(0, railAdapter.indexOf(selectedIptvCategory)));
    }

    /**
     * Keeps focus in the guide when the focused row leaves the list: unfavorited under Favorites,
     * or found offline by the stream check. A removed row hands focus to the first view on screen,
     * the header's update pill, where OK starts an update. Focus moves first, while the rows are
     * still on screen: to the next row that stays, else the nearest one above, else the category.
     */
    private void keepGuideFocus(List<IptvChannel> next) {
        View focused = getCurrentFocus();
        if (focused == null || !inside(focused, showsView)) return;
        RecyclerView.ViewHolder holder = showsView.findContainingViewHolder(focused);
        if (holder == null) return;
        java.util.Set<Long> staying = new java.util.HashSet<>(next.size() * 2);
        for (IptvChannel channel : next) staying.add(channel.id);
        if (staying.contains(holder.getItemId())) return;
        int from = holder.getBindingAdapterPosition();
        if (from != RecyclerView.NO_POSITION) {
            for (int step = 1; step >= -1; step -= 2) {
                for (int i = from + step; ; i += step) {
                    RecyclerView.ViewHolder row = showsView.findViewHolderForAdapterPosition(i);
                    if (row == null) break;
                    if (staying.contains(row.getItemId())) {
                        row.itemView.requestFocus();
                        return;
                    }
                }
            }
        }
        focusSelectedRail();
    }

    private boolean focusPosition(RecyclerView list, int position) {
        RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
        if (holder != null) return holder.itemView.requestFocus();
        list.scrollToPosition(position);
        list.post(() -> {
            RecyclerView.ViewHolder later = list.findViewHolderForAdapterPosition(position);
            if (later != null) later.itemView.requestFocus();
        });
        return true;
    }

    /** The top of the guide: the focused (or first) channel, what is on, and the preview window. */
    private void updateGuideInfo() {
        if (guideAdapter == null) return;
        int position = guideAdapter.indexOf(guideFocusedId);
        IptvChannel channel = guideAdapter.get(position < 0 ? 0 : position);
        if (channel == null) {
            guideEyebrow.setText("");
            guideTitle.setText("");
            guideMeta.setText("");
            guideSynopsis.setText("");
            guideProgress.setVisibility(View.INVISIBLE);
            guidePreview.show(null, false);
            return;
        }
        long now = System.currentTimeMillis();
        FastGuide.Schedule schedule = FastGuide.schedule(channel.channelId);
        if (FastGuide.isFast(channel)) {
            int onAir = schedule == null ? -1 : schedule.indexAt(now);
            guideEyebrow.setText(schedule != null && schedule.number > 0 ? schedule.number + "  ·  " + channel.name : channel.name);
            String title = onAir < 0 ? "" : schedule.title(onAir);
            guideTitle.setText(ratedGuideTitle(channel, title.isEmpty() ? channel.name : title, now));
            if (onAir < 0) {
                guideMeta.setText(R.string.guide_live);
            } else {
                String left = GuideText.left(this, schedule, now);
                String range = GuideText.range(GuideText.clock(this), schedule, onAir);
                guideMeta.setText(left.isEmpty() ? range : range + "  ·  " + left);
            }
            int percent = schedule == null ? -1 : schedule.progressPercent(now);
            guideProgress.setVisibility(percent < 0 ? View.INVISIBLE : View.VISIBLE);
            if (percent >= 0) guideProgress.setProgress(percent);
            guideSynopsis.setText(schedule == null ? "" : schedule.description);
        } else {
            guideEyebrow.setText(countryName(channel.country));
            guideTitle.setText(channel.name);
            String genres = android.text.TextUtils.join(", ", channel.genres);
            guideMeta.setText(genres.isEmpty() ? getString(R.string.guide_live)
                    : getString(R.string.guide_live) + "  ·  " + genres);
            guideProgress.setVisibility(View.INVISIBLE);
            guideSynopsis.setText("");
        }
        boolean playback = previewResumed && !screenOff && hasWindowFocus() && currentTab == TAB_SHOWS
                && !preferences.reducedMotion() && (!channel.genres.contains("18+") || adultUnlocked);
        guidePreview.show(channel, playback);
    }

    /** The guide title with the rating of what is on air beside it; an unknown rating is looked up in the background. */
    private CharSequence ratedGuideTitle(IptvChannel channel, CharSequence title, long now) {
        if (programRatings == null) programRatings = new ProgramRatings(this);
        String rating = programRatings.cached(channel, now);
        if (rating == null) {
            long id = channel.id;
            programRatings.request(channel, () -> {
                if (guideAdapter == null || currentTab != TAB_SHOWS) return;
                int position = guideAdapter.indexOf(guideFocusedId);
                IptvChannel shown = guideAdapter.get(position < 0 ? 0 : position);
                if (shown != null && shown.id == id) updateGuideInfo();
            });
        }
        return RatingBadge.fit(guideTitle, title, rating);
    }

    /** Runs {@link #guideTick} just after the next minute starts, while the guide is on screen. */
    private void scheduleGuideTick() {
        filterHandler.removeCallbacks(guideTick);
        if (!previewResumed || currentTab != TAB_SHOWS) return;
        filterHandler.postDelayed(guideTick, 60_000L - System.currentTimeMillis() % 60_000L + 250L);
    }

    private String countryName(String code) {
        if (code == null || code.isEmpty() || "ALL".equalsIgnoreCase(code)) return "";
        try {
            String name = new java.util.Locale.Builder().setRegion("UK".equalsIgnoreCase(code) ? "GB" : code)
                    .build().getDisplayCountry();
            return name.isEmpty() ? code : name;
        } catch (java.util.IllformedLocaleException notARegion) {
            return code;
        }
    }

    private static boolean hasGenre(IptvChannel channel, String name) {
        for (String genre : channel.genres) if (genre.trim().equalsIgnoreCase(name)) return true;
        return false;
    }

    private boolean adultUnlocked, onlineScanRunning;
    private int onlineScanGeneration, offlineHidden;
    /** Rows a scan already tried since the list loaded; a busy checker or a full cache cannot loop on them. */
    private final java.util.Set<String> onlineAttempted = new java.util.HashSet<>();

    private void scanOnline(List<IptvChannel> candidates, int offset, int generation) {
        if (generation != onlineScanGeneration || isDestroyed()) return;
        if (offset >= candidates.size()) return;
        onlinePending++;
        IptvChannel candidate = candidates.get(offset);
        onlineAttempted.add(candidate.url);
        com.firestick.aura.launcher.manager.StreamAvailability.checkChannel(candidate, reachable -> {
            if (generation != onlineScanGeneration || isDestroyed()) return;
            onlineChecked++;
            onlinePending--;
            if (offset + 3 < candidates.size()) scanOnline(candidates, offset + 3, generation);
            if (onlinePending == 0) onlineScanRunning = false;
            filterHandler.removeCallbacks(updateOnline);
            filterHandler.postDelayed(updateOnline, 150);
        });
    }

    /**
     * Shows the rows of the selected category that are on air. FAST rows always count as online;
     * other rows appear once a feed check (saved for a day) finds them reachable, and unchecked
     * rows are checked in list order. Favorites stay listed whatever their state.
     */
    private void applyIptvFilters() {
        if (guideAdapter == null || !channelsLoaded) return;
        String genre = selectedIptvCategory.startsWith("category:")
                ? selectedIptvCategory.substring("category:".length()) : null;
        boolean favorites = "favorites".equals(selectedIptvCategory);
        List<IptvChannel> filtered = new ArrayList<>();
        List<IptvChannel> unchecked = new ArrayList<>();
        boolean anyFavorite = false, anyAdult = false;
        int offline = 0;
        for (IptvChannel channel : countryChannels) {
            if (channel.genres.contains("18+") && (!adultUnlocked || !ADULTS.equals(selectedIptvCategory))) continue;
            if (channel.isFavorite) anyFavorite = true;
            if (channel.genres.contains("18+")) anyAdult = true;
            if (favorites && !channel.isFavorite) continue;
            if (genre != null && !hasGenre(channel, genre)) continue;
            if (!iptvSearchQuery.isEmpty()
                    && !channel.name.toLowerCase(java.util.Locale.ROOT).contains(iptvSearchQuery)) continue;
            // FAST lineups are curated and on air around the clock; probing hundreds of redirects
            // would only load the relay, so they count as online.
            Boolean online = FastGuide.isFast(channel) ? Boolean.TRUE
                    : com.firestick.aura.launcher.manager.StreamAvailability.cached(channel);
            if (online == null && !onlineAttempted.contains(channel.url)) unchecked.add(channel);
            if (Boolean.FALSE.equals(online) && !favorites) offline++;
            if (favorites || Boolean.TRUE.equals(online)) filtered.add(channel);
        }
        offlineHidden = offline;
        if (!onlineScanRunning && !unchecked.isEmpty()) {
            onlineScanRunning = true;
            int generation = onlineScanGeneration;
            onlineChecked = 0;
            onlineTotal = unchecked.size();
            onlinePending = 0;
            showsView.post(() -> {
                for (int lane = 0; lane < Math.min(3, unchecked.size()); lane++) scanOnline(unchecked, lane, generation);
            });
        }
        visibleChannels.clear();
        visibleChannels.addAll(filtered);
        ((TextView) findViewById(R.id.onlineProgress)).setText(onlineScanRunning
                ? getString(R.string.online_progress, Math.min(onlineChecked, onlineTotal), onlineTotal)
                : offlineHidden > 0 ? getResources().getQuantityString(R.plurals.online_hidden, offlineHidden, offlineHidden)
                : "");
        railAdapter.setSelected(selectedIptvCategory);
        keepGuideFocus(filtered);
        guideAdapter.setItems(filtered);
        if (filtered.isEmpty()) {
            int message = countryChannels.isEmpty() ? R.string.iptv_no_channels
                    : favorites && !anyFavorite ? R.string.iptv_no_favorites
                    : ADULTS.equals(selectedIptvCategory) && !anyAdult ? R.string.iptv_no_adults
                    : offlineHidden > 0 ? R.string.iptv_none_online
                    : R.string.iptv_no_results;
            setShowsStatus(onlineScanRunning ? R.string.iptv_online_checking : message);
        } else {
            showsStatus.setVisibility(View.GONE);
        }
        updateGuideInfo();
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
        buildRail();
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
        return channel.withFavorite(favorite);
    }

    private void playIptvChannel(IptvChannel channel) {
        if (channel.url == null || channel.url.isEmpty()) return;
        List<IptvChannel> queue = visibleChannels.contains(channel) ? new ArrayList<>(visibleChannels)
                : preferences.recentChannels();
        Runnable play = () -> {
            // Hand the decoder to the full-screen player.
            guidePreview.release();
            com.firestick.aura.launcher.manager.PlaybackSession.open(this, queue, channel);
        };
        if (channel.isAdult() && !adultUnlocked)
            LockPad.verify(this, preferences, getString(R.string.iptv_adults_unlock), play::run);
        else play.run();
    }

    private void setShowsStatus(int textRes) {
        showsStatus.setText(textRes);
        showsStatus.setVisibility(View.VISIBLE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
