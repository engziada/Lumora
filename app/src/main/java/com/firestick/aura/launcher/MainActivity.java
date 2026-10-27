package com.firestick.aura.launcher;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firestick.aura.launcher.adapter.AppRowAdapter;
import com.firestick.aura.launcher.adapter.CategoryRowAdapter;
import com.firestick.aura.launcher.adapter.FeaturedRowAdapter;
import com.firestick.aura.launcher.adapter.ShowsAdapter;
import com.firestick.aura.launcher.manager.AppRepository;
import com.firestick.aura.launcher.manager.DebloatManager;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.RemoteImageLoader;
import com.firestick.aura.launcher.manager.ShowsRepository;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.manager.WallpaperManager;
import com.firestick.aura.launcher.model.AppCategory;
import com.firestick.aura.launcher.model.AppModel;
import com.firestick.aura.launcher.model.ShowItem;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity
        implements AppRowAdapter.OnAppActionListener, ShowsAdapter.OnShowClickListener {
    private static final int TAB_HOME = 0, TAB_SHOWS = 1, TAB_APPS = 2, TAB_STREAMING = 3;
    private static final int FEATURED_LIMIT = 6, SHOWS_COLUMNS = 7, MAX_CHANNEL_CHIPS = 8;
    private static final long SHOWS_MAX_AGE_MS = 60L * 60L * 1000L;
    private static final String FILTER_ALL = "\u0000all", FILTER_TV = "\u0000tv", FILTER_WEB = "\u0000web";

    /** Channel name (lower case) to the apps that carry it, in order of preference. */
    private static final String[][] CHANNEL_APPS = {
            {"netflix", "com.netflix.ninja"},
            {"prime video", "com.amazon.avod.thirdpartyclient", "com.amazon.amazonvideo.livingroom"},
            {"amazon", "com.amazon.avod.thirdpartyclient", "com.amazon.amazonvideo.livingroom"},
            {"disney+", "com.disney.disneyplus"},
            {"hulu", "com.hulu.plus"},
            {"max", "com.wbd.stream", "com.hbo.hbonow"},
            {"hbo", "com.wbd.stream", "com.hbo.hbonow"},
            {"apple tv", "com.apple.atve.androidtv.appletv"},
            {"paramount+", "com.cbs.ott"},
            {"peacock", "com.peacocktv.peacockandroid"},
            {"crunchyroll", "com.crunchyroll.crunchyroid"},
            {"youtube", "com.amazon.firetv.youtube", "com.google.android.youtube.tv", "com.google.android.youtube"},
            {"tubi", "com.tubitv.ott"},
            {"pluto", "tv.pluto.android"}};

    private RecyclerView categoriesView, featuredView, showsView;
    private CategoryRowAdapter categoryAdapter;
    private FeaturedRowAdapter featuredAdapter;
    private ShowsAdapter showsAdapter;
    private AppRepository repository;
    private ShowsRepository showsRepository;
    private PreferenceManager preferences;
    private WallpaperManager wallpapers;
    private QuickMenu quickMenu;
    private TextView greeting, greetingSub, showsStatus;
    private View showsPanel;
    private LinearLayout showsChips;
    private List<AppCategory> allCategories = new ArrayList<>(), currentCategories;
    private List<ShowItem> allShows;
    private String showsCountry, showsFilter = FILTER_ALL;
    private long appliedHomeRevision = -1L, showsLoadedAt;
    private int currentTab = TAB_HOME, refreshGeneration, showsGeneration;
    private boolean packageReceiverRegistered;

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
        int amber = ContextCompat.getColor(this, R.color.accent_amber);
        preferences.migrateAccent(new int[]{amber, ContextCompat.getColor(this, R.color.accent_copper),
                ContextCompat.getColor(this, R.color.accent_rose), ContextCompat.getColor(this, R.color.accent_moonlight)}, amber);
        repository = new AppRepository(this);
        showsRepository = new ShowsRepository(this);
        quickMenu = new QuickMenu(this, preferences, this::launchPackage);
        SoundManager.init(this);
        initViews();
        registerPackageReceiver();
        wallpapers = new WallpaperManager(this, preferences,
                findViewById(R.id.imgWallpaperBack), findViewById(R.id.imgWallpaperFront));
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                // A home screen never finishes on Back: return to Home, then to the top.
                if (currentTab != TAB_HOME) selectTab(TAB_HOME);
                else categoriesView.smoothScrollToPosition(0);
            }
        });
    }

    private void initViews() {
        greeting = findViewById(R.id.txtGreeting);
        greetingSub = findViewById(R.id.txtGreetingSub);
        categoriesView = findViewById(R.id.recyclerCategories);
        categoriesView.setLayoutManager(new LinearLayoutManager(this));
        categoryAdapter = new CategoryRowAdapter(preferences, this);
        categoriesView.setAdapter(categoryAdapter);
        featuredView = findViewById(R.id.recyclerFeatured);
        featuredView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        featuredAdapter = new FeaturedRowAdapter(preferences, this);
        featuredView.setAdapter(featuredAdapter);

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
        View settings = findViewById(R.id.navSettings);
        settings.setOnClickListener(view -> startActivity(new Intent(this, SettingsActivity.class)));
        bindNavFocus(settings);
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

    private void selectTab(int tab) {
        if (currentTab == tab) {
            if (tab == TAB_SHOWS) showsView.smoothScrollToPosition(0);
            else categoriesView.smoothScrollToPosition(0);
            return;
        }
        currentTab = tab;
        applyTabVisibility();
        updateGreeting();
        updateNavSelection();
        if (tab == TAB_SHOWS) {
            loadShows(false);
        } else {
            refreshApps(false);
            categoriesView.scrollToPosition(0);
        }
    }

    private void applyTabVisibility() {
        boolean shows = currentTab == TAB_SHOWS;
        showsPanel.setVisibility(shows ? View.VISIBLE : View.GONE);
        categoriesView.setVisibility(shows ? View.GONE : View.VISIBLE);
        featuredView.setVisibility(currentTab == TAB_HOME ? View.VISIBLE : View.GONE);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Pressing Home while Lumora is already in front returns to the Home tab.
        if (currentTab != TAB_HOME) selectTab(TAB_HOME);
        categoriesView.scrollToPosition(0);
    }

    private void updateNavSelection() {
        styleNav(R.id.txtNavHome, R.id.imgNavHome, currentTab == TAB_HOME);
        styleNav(R.id.txtNavShows, R.id.imgNavShows, currentTab == TAB_SHOWS);
        styleNav(R.id.txtNavApps, R.id.imgNavApps, currentTab == TAB_APPS);
        styleNav(R.id.txtNavMovies, R.id.imgNavMovies, currentTab == TAB_STREAMING);
        styleNav(R.id.txtNavQuick, R.id.imgNavQuick, false);
        styleNav(R.id.txtNavSettings, R.id.imgNavSettings, false);
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
        long revision = preferences.getHomeRevision();
        boolean changed = revision != appliedHomeRevision;
        appliedHomeRevision = revision;
        applyTabVisibility();
        refreshApps(changed);
        updateGreeting();
        updateNavSelection();
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
        if (wallpapers != null) wallpapers.stop();
        super.onPause();
    }

    // ---------------------------------------------------------------- apps

    private void refreshApps(boolean force) {
        if (force) repository.invalidate();
        int request = ++refreshGeneration;
        repository.loadCategoriesAsync(categories -> {
            if (request != refreshGeneration || isFinishing() || isDestroyed()) return;
            allCategories = categories;
            currentCategories = filterForTab(categories);
            categoryAdapter.setCategories(currentCategories);
            updateGreeting();
            updateFeatured();
        });
    }

    private List<AppCategory> filterForTab(List<AppCategory> categories) {
        if (currentTab != TAB_APPS && currentTab != TAB_STREAMING) return categories;
        List<AppCategory> filtered = new ArrayList<>();
        String wanted = currentTab == TAB_APPS ? "all" : "streaming";
        for (AppCategory category : categories) if (wanted.equals(category.getId())) filtered.add(category);
        if (filtered.isEmpty() && !categories.isEmpty()) filtered.add(categories.get(0));
        return filtered;
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

    private void updateFeatured() {
        if (currentTab != TAB_HOME) {
            featuredView.setVisibility(View.GONE);
            featuredAdapter.setItems(new ArrayList<>(), new ArrayList<>());
            return;
        }
        featuredView.setVisibility(View.VISIBLE);
        List<AppModel> picks = new ArrayList<>();
        List<String> metas = new ArrayList<>();
        for (AppCategory category : allCategories) if ("favorites".equals(category.getId())) picks.addAll(category.getApps());
        int pinned = picks.size();
        if (picks.isEmpty()) {
            for (AppCategory category : allCategories) {
                if ("streaming".equals(category.getId()) || "all".equals(category.getId())) {
                    picks.addAll(category.getApps());
                    break;
                }
            }
        }
        while (picks.size() > FEATURED_LIMIT) picks.remove(picks.size() - 1);
        for (int i = 0; i < picks.size(); i++) metas.add(getString(i < pinned ? R.string.featured_pinned : R.string.featured_ready));
        featuredAdapter.setItems(picks, metas);
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

    /** Launches an installed app and records it for the quick menu's Recent list. */
    void launchPackage(String packageName) {
        try {
            Intent launch = launchIntentFor(packageName);
            if (launch == null) {
                Toast.makeText(this, getString(R.string.error_cannot_launch, labelFor(packageName)), Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(launch);
            preferences.recordLaunch(packageName);
        } catch (ActivityNotFoundException | SecurityException exception) {
            Toast.makeText(this, getString(R.string.error_launch, exception.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onAppClick(AppModel app) {
        launchPackage(app.getPackageName());
    }

    @Override public void onAppLongClick(AppModel app, String rowId) {
        showAppOptionsDialog(app, rowId);
    }

    private AlertDialog buildDialog(View view) {
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_AuraLauncher_Dialog).setView(view).create();
        Window window = dialog.getWindow();
        // The layouts draw their own glass panel; avoid a second frame behind them.
        if (window != null) window.setBackgroundDrawable(new ColorDrawable(0));
        return dialog;
    }

    private void showAppOptionsDialog(AppModel app, String rowId) {
        View view = getLayoutInflater().inflate(R.layout.dialog_app_options, null);
        AlertDialog dialog = buildDialog(view);
        ((ImageView) view.findViewById(R.id.dialogAppIcon)).setImageDrawable(app.getIcon());
        ((TextView) view.findViewById(R.id.dialogAppTitle)).setText(app.getLabel());
        ((TextView) view.findViewById(R.id.dialogAppPackage)).setText(app.getPackageName());
        Button launch = view.findViewById(R.id.btnOptLaunch);
        Button left = view.findViewById(R.id.btnOptMoveLeft);
        Button right = view.findViewById(R.id.btnOptMoveRight);
        Button favorite = view.findViewById(R.id.btnOptToggleFavorite);
        Button uninstall = view.findViewById(R.id.btnOptUninstall);
        Button force = view.findViewById(R.id.btnOptForceUninstall);
        Button hide = view.findViewById(R.id.btnOptHide);
        Button info = view.findViewById(R.id.btnOptAppInfo);
        if (rowId == null) {
            left.setVisibility(View.GONE);
            right.setVisibility(View.GONE);
        }
        String pkg = app.getPackageName();
        boolean isFavorite = preferences.isFavorite(pkg);
        favorite.setText(isFavorite ? R.string.action_unfavorite : R.string.action_favorite);
        launch.setOnClickListener(source -> {
            dialog.dismiss();
            onAppClick(app);
        });
        left.setOnClickListener(source -> {
            moveApp(rowId, app, -1);
            dialog.dismiss();
        });
        right.setOnClickListener(source -> {
            moveApp(rowId, app, 1);
            dialog.dismiss();
        });
        favorite.setOnClickListener(source -> {
            if (isFavorite) preferences.removeFavorite(pkg);
            else preferences.addFavorite(pkg);
            dialog.dismiss();
            refreshApps(true);
        });
        uninstall.setOnClickListener(source -> {
            dialog.dismiss();
            DebloatManager.requestNormalUninstall(this, pkg);
        });
        force.setOnClickListener(source -> {
            dialog.dismiss();
            new AlertDialog.Builder(this, R.style.Theme_AuraLauncher_Dialog)
                    .setTitle(app.getLabel())
                    .setMessage(getString(R.string.force_uninstall_message, DebloatManager.forceUninstallCommand(pkg)))
                    .setPositiveButton(R.string.close, null)
                    .show();
        });
        hide.setOnClickListener(source -> {
            preferences.hideApp(pkg);
            dialog.dismiss();
            refreshApps(true);
        });
        info.setOnClickListener(source -> {
            dialog.dismiss();
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
            } catch (ActivityNotFoundException exception) {
                Toast.makeText(this, R.string.error_app_details, Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
        launch.requestFocus();
    }

    private void moveApp(String rowId, AppModel app, int direction) {
        if (rowId == null || currentCategories == null) return;
        for (AppCategory category : currentCategories) {
            if (!rowId.equals(category.getId())) continue;
            List<AppModel> apps = category.getApps();
            int index = -1;
            for (int i = 0; i < apps.size(); i++) {
                if (apps.get(i).getPackageName().equals(app.getPackageName())) {
                    index = i;
                    break;
                }
            }
            int target = index + direction;
            if (index < 0 || target < 0 || target >= apps.size()) return;
            Collections.swap(apps, index, target);
            List<String> packages = new ArrayList<>();
            for (AppModel model : apps) packages.add(model.getPackageName());
            preferences.setCustomOrder(rowId, packages);
            appliedHomeRevision = preferences.getHomeRevision();
            repository.invalidate();
            categoryAdapter.setCategories(currentCategories);
            return;
        }
    }

    // --------------------------------------------------------------- shows

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
        View view = getLayoutInflater().inflate(R.layout.dialog_show_details, null);
        AlertDialog dialog = buildDialog(view);
        ImageView poster = view.findViewById(R.id.imgShowPoster);
        poster.setClipToOutline(true);
        RemoteImageLoader.get(this).load(show.posterUrl, dp(132), poster::setImageBitmap);
        ((TextView) view.findViewById(R.id.txtShowTitle)).setText(show.title);

        StringBuilder meta = new StringBuilder();
        if (!show.channel.isEmpty() && !show.airtime.isEmpty()) meta.append(getString(R.string.show_channel_time, show.channel, show.airtime));
        else meta.append(show.channel).append(show.airtime);
        if (!show.genres.isEmpty()) appendMeta(meta, show.genres);
        if (show.rating > 0d) appendMeta(meta, getString(R.string.show_rating, show.rating));
        ((TextView) view.findViewById(R.id.txtShowMeta)).setText(meta);

        TextView episode = view.findViewById(R.id.txtShowEpisode);
        if (show.season > 0 && show.number > 0) episode.setText(getString(R.string.show_episode, show.season, show.number, show.episodeName));
        else if (!show.episodeName.isEmpty()) episode.setText(show.episodeName);
        else episode.setVisibility(View.GONE);
        ((TextView) view.findViewById(R.id.txtShowSummary)).setText(
                show.summary.isEmpty() ? getString(R.string.show_no_summary) : show.summary);

        Button open = view.findViewById(R.id.btnShowOpen);
        Button close = view.findViewById(R.id.btnShowClose);
        String pkg = appForChannel(show.channel);
        if (pkg != null) {
            open.setText(getString(R.string.show_open_in, labelFor(pkg)));
            open.setVisibility(View.VISIBLE);
            open.setOnClickListener(source -> {
                dialog.dismiss();
                launchPackage(pkg);
            });
        }
        close.setOnClickListener(source -> dialog.dismiss());
        dialog.show();
        (pkg != null ? open : close).requestFocus();
    }

    private static void appendMeta(StringBuilder meta, String part) {
        if (meta.length() > 0) meta.append(" \u00B7 ");
        meta.append(part);
    }

    /** First installed app that carries the channel. "max" must match exactly (it is a substring of many names). */
    private String appForChannel(String channel) {
        if (channel == null || channel.isEmpty()) return null;
        String name = channel.toLowerCase(Locale.ROOT);
        for (String[] row : CHANNEL_APPS) {
            boolean match = "max".equals(row[0]) ? name.equals("max") : name.contains(row[0]);
            if (!match) continue;
            for (int i = 1; i < row.length; i++) if (launchIntentFor(row[i]) != null) return row[i];
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
