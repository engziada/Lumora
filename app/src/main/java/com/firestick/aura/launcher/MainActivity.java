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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firestick.aura.launcher.adapter.AppRowAdapter;
import com.firestick.aura.launcher.adapter.ShowsAdapter;
import com.firestick.aura.launcher.assistant.AssistantSettings;
import com.firestick.aura.launcher.manager.AppLock;
import com.firestick.aura.launcher.manager.AppRepository;
import com.firestick.aura.launcher.manager.DebloatManager;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.QuickAccess;
import com.firestick.aura.launcher.manager.ShowsRepository;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.manager.WallpaperManager;
import com.firestick.aura.launcher.model.AppCategory;
import com.firestick.aura.launcher.model.AppModel;
import com.firestick.aura.launcher.model.ShowItem;
import com.firestick.aura.launcher.service.QuickActionService;
import com.firestick.aura.launcher.ui.Glass;
import com.firestick.aura.launcher.ui.GlassMenu;
import com.firestick.aura.launcher.ui.LockPad;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity
        implements AppRowAdapter.OnAppActionListener, ShowsAdapter.OnShowClickListener, ShowDetails.Host {
    /** Package to launch on arrival; sent by the assistant so locks and launch history apply. */
    public static final String EXTRA_LAUNCH_PACKAGE = "com.firestick.aura.launcher.extra.LAUNCH_PACKAGE";
    private static final int TAB_HOME = 0, TAB_SHOWS = 1, TAB_APPS = 2, TAB_STREAMING = 3;
    private static final int GRID_COLUMNS = 7, SHOWS_COLUMNS = 7, MAX_CHANNEL_CHIPS = 8, HOME_FALLBACK = 8;
    private static final long SHOWS_MAX_AGE_MS = 60L * 60L * 1000L;
    private static final long GEAR_DELAY_MS = 350L, GEAR_KEY_WINDOW_MS = 600L, GEAR_REOPEN_GUARD_MS = 900L;
    private static final String FILTER_ALL = "\u0000all", FILTER_TV = "\u0000tv", FILTER_WEB = "\u0000web";

    private RecyclerView homeView, gridView, showsView;
    private AppRowAdapter homeAdapter, appsAdapter, streamingAdapter;
    private ShowsAdapter showsAdapter;
    private AppRepository repository;
    private ShowsRepository showsRepository;
    private PreferenceManager preferences;
    private AssistantSettings assistant;
    private WallpaperManager wallpapers;
    private QuickMenu quickMenu;
    private ShowDetails showDetails;
    private TextView greeting, greetingSub, heroTitle, heroMeta, showsStatus, gridEmpty, moveHint;
    private View homePanel, gridPanel, showsPanel, gear, assistantButton;
    private ImageView assistantIcon;
    private LinearLayout showsChips;
    private PopupWindow gearMenu;
    private final Map<String, List<AppModel>> rows = new HashMap<>();
    private List<AppModel> homeApps = new ArrayList<>();
    private List<ShowItem> allShows;
    private String showsCountry, showsFilter = FILTER_ALL;
    private long appliedHomeRevision = -1L, showsLoadedAt, lastKeyDownAt, gearDismissedAt;
    private int currentTab = TAB_HOME, refreshGeneration, showsGeneration;
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

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        preferences = new PreferenceManager(this);
        assistant = new AssistantSettings(this);
        int amber = ContextCompat.getColor(this, R.color.accent_amber);
        preferences.migrateAccent(new int[]{amber, ContextCompat.getColor(this, R.color.accent_copper),
                ContextCompat.getColor(this, R.color.accent_rose), ContextCompat.getColor(this, R.color.accent_moonlight)}, amber);
        repository = new AppRepository(this);
        showsRepository = new ShowsRepository(this);
        quickMenu = new QuickMenu(this, preferences, this::launchPackage);
        showDetails = new ShowDetails(this, this);
        SoundManager.init(this);
        initViews();
        registerPackageReceiver();
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

        gridView = findViewById(R.id.recyclerGrid);
        gridView.setLayoutManager(new GridLayoutManager(this, GRID_COLUMNS));
        RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();
        gridView.setRecycledViewPool(pool);
        appsAdapter = new AppRowAdapter(preferences, this, AppRepository.ROW_APPS, AppRowAdapter.STYLE_TILE, null);
        streamingAdapter = new AppRowAdapter(preferences, this, AppRepository.ROW_STREAMING, AppRowAdapter.STYLE_TILE, null);

        showsPanel = findViewById(R.id.showsPanel);
        showsChips = findViewById(R.id.showsChips);
        showsStatus = findViewById(R.id.txtShowsStatus);
        showsView = findViewById(R.id.recyclerShows);
        showsView.setLayoutManager(new GridLayoutManager(this, SHOWS_COLUMNS));
        showsAdapter = new ShowsAdapter(preferences, this);
        showsView.setAdapter(showsAdapter);

        bindNav(R.id.navHome, TAB_HOME);
        bindNav(R.id.navShows, TAB_SHOWS);
        bindNav(R.id.navApps, TAB_APPS);
        bindNav(R.id.navMovies, TAB_STREAMING);
        View quick = findViewById(R.id.navQuick);
        quick.setOnClickListener(view -> {
            SoundManager.playClick(view, preferences);
            quickMenu.show();
        });
        bindNavFocus(quick);

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
        if (!visible && assistantButton.hasFocus()) findViewById(R.id.navQuick).requestFocus();
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

    // ----------------------------------------------------------------- tabs

    private void selectTab(int tab) {
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
        if (tab == TAB_SHOWS) loadShows(false);
        else applyRows();
    }

    private void applyTabVisibility() {
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
        styleNav(R.id.txtNavQuick, R.id.imgNavQuick, false);
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

    @Override protected void onDestroy() {
        if (packageReceiverRegistered) {
            try {
                unregisterReceiver(packageReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered.
            }
        }
        if (wallpapers != null) wallpapers.shutdown();
        repository.shutdown();
        showsRepository.shutdown();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        // Keeps the quick-actions service on once WRITE_SECURE_SETTINGS has been granted over ADB.
        QuickAccess.ensureServiceEnabled(this);
        long revision = preferences.getHomeRevision();
        boolean changed = revision != appliedHomeRevision;
        appliedHomeRevision = revision;
        applyTabVisibility();
        // Always rebuild: packages removed for user 0 over ADB do not always broadcast.
        refreshApps(true);
        updateGreeting();
        updateNavSelection();
        updateAssistantButton();
        if (currentTab == TAB_SHOWS) {
            if (changed && allShows != null) {
                buildChips();
                showsAdapter.notifyItemRangeChanged(0, showsAdapter.getItemCount());
            }
            loadShows(false);
        }
        if (wallpapers != null) wallpapers.start();
    }

    @Override protected void onPause() {
        if (movingPackage != null) finishMove();
        if (gearMenu != null) gearMenu.dismiss();
        if (wallpapers != null) wallpapers.stop();
        super.onPause();
    }

    /** Fallback when the quick-actions service is off: the assistant key still works inside Lumora. */
    private boolean isAssistantKey(KeyEvent event) {
        return movingPackage == null && !QuickActionService.isRunning()
                && assistant.isRemoteButtonEnabled() && assistant.isAssistantKey(event.getKeyCode());
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) lastKeyDownAt = SystemClock.uptimeMillis();
        if (isAssistantKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) openAssistant();
            return true;
        }
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
        actions.add(() -> launchPackage(pkg));
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
        GlassMenu.show(this, label, pkg, app.getIcon(), items, false, (index, item) -> actions.get(index).run());
    }

    private void toggleLock(String pkg, String label, boolean locked) {
        if (locked) {
            LockPad.verify(this, preferences, getString(R.string.lock_sub_unlock, label), () -> {
                preferences.setLocked(pkg, false);
                refreshApps(true);
            });
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

    // ---------------------------------------------------------------- shows

    private void loadShows(boolean force) {
        if (!preferences.isShowsEnabled()) {
            showsGeneration++;
            allShows = null;
            showsChips.removeAllViews();
            showsAdapter.setItems(null);
            setShowsStatus(R.string.shows_disabled);
            return;
        }
        String country = preferences.getShowsCountry();
        boolean countryChanged = !country.equals(showsCountry);
        boolean stale = allShows == null || countryChanged
                || System.currentTimeMillis() - showsLoadedAt > SHOWS_MAX_AGE_MS;
        if (!stale && !force) {
            applyShowsFilter();
            return;
        }
        if (allShows == null || countryChanged) {
            showsChips.removeAllViews();
            showsAdapter.setItems(null);
            setShowsStatus(R.string.shows_loading);
        }
        int request = ++showsGeneration;
        showsRepository.load(country, (shows, failed) -> {
            if (request != showsGeneration || isFinishing() || isDestroyed()) return;
            if (failed && shows.isEmpty()) {
                allShows = null;
                showsChips.removeAllViews();
                showsAdapter.setItems(null);
                setShowsStatus(R.string.shows_error);
                return;
            }
            showsCountry = country;
            showsLoadedAt = System.currentTimeMillis();
            allShows = shows;
            buildChips();
            applyShowsFilter();
        });
    }

    private void setShowsStatus(int textRes) {
        showsStatus.setText(textRes);
        showsStatus.setVisibility(View.VISIBLE);
    }

    private void buildChips() {
        showsChips.removeAllViews();
        if (allShows == null) return;
        Map<String, Integer> counts = new HashMap<>();
        boolean hasTv = false, hasWeb = false;
        for (ShowItem show : allShows) {
            if (show.streaming) hasWeb = true;
            else hasTv = true;
            if (!show.channel.isEmpty()) counts.merge(show.channel, 1, Integer::sum);
        }
        List<String> channels = new ArrayList<>(counts.keySet());
        channels.sort((a, b) -> {
            int byCount = Integer.compare(counts.get(b), counts.get(a));
            return byCount != 0 ? byCount : a.compareToIgnoreCase(b);
        });
        List<String> keys = new ArrayList<>();
        addChip(keys, FILTER_ALL, getString(R.string.shows_all));
        if (hasTv) addChip(keys, FILTER_TV, getString(R.string.shows_tv));
        if (hasWeb) addChip(keys, FILTER_WEB, getString(R.string.shows_streaming));
        for (int i = 0; i < channels.size() && i < MAX_CHANNEL_CHIPS; i++) addChip(keys, channels.get(i), channels.get(i));
        if (!keys.contains(showsFilter)) showsFilter = FILTER_ALL;
        updateChipSelection();
    }

    private void addChip(List<String> keys, String key, String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTag(key);
        chip.setTextSize(10);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));
        chip.setBackground(chipBackground());
        chip.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_selected}, {}},
                new int[]{ContextCompat.getColor(this, R.color.nova_text_on_accent), ContextCompat.getColor(this, R.color.nova_text)}));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(8));
        chip.setOnClickListener(view -> {
            SoundManager.playClick(view, preferences);
            showsFilter = key;
            updateChipSelection();
            applyShowsFilter();
            showsView.scrollToPosition(0);
        });
        chip.setOnFocusChangeListener((view, focused) -> {
            if (focused) SoundManager.playFocus(view, preferences);
        });
        showsChips.addView(chip, params);
        keys.add(key);
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

    private void updateChipSelection() {
        for (int i = 0; i < showsChips.getChildCount(); i++) {
            View chip = showsChips.getChildAt(i);
            chip.setSelected(showsFilter.equals(chip.getTag()));
        }
    }

    private void applyShowsFilter() {
        if (allShows == null) return;
        List<ShowItem> filtered = new ArrayList<>();
        for (ShowItem show : allShows) {
            boolean keep = FILTER_ALL.equals(showsFilter)
                    || (FILTER_TV.equals(showsFilter) && !show.streaming)
                    || (FILTER_WEB.equals(showsFilter) && show.streaming)
                    || showsFilter.equals(show.channel);
            if (keep) filtered.add(show);
        }
        showsAdapter.setItems(filtered);
        if (filtered.isEmpty()) setShowsStatus(R.string.shows_empty);
        else showsStatus.setVisibility(View.GONE);
    }

    @Override public void onShowClick(ShowItem show) {
        showDetails.show(show);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
