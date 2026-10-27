package com.firestick.aura.launcher;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firestick.aura.launcher.adapter.AppRowAdapter;
import com.firestick.aura.launcher.adapter.CategoryRowAdapter;
import com.firestick.aura.launcher.adapter.FeaturedRowAdapter;
import com.firestick.aura.launcher.manager.AppRepository;
import com.firestick.aura.launcher.manager.DebloatManager;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.manager.WallpaperManager;
import com.firestick.aura.launcher.model.AppCategory;
import com.firestick.aura.launcher.model.AppModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AppRowAdapter.OnAppActionListener {
    private static final int TAB_HOME = 0, TAB_APPS = 1, TAB_MOVIES = 2, FEATURED_LIMIT = 6;
    private RecyclerView categoriesView, featuredView;
    private CategoryRowAdapter categoryAdapter;
    private FeaturedRowAdapter featuredAdapter;
    private AppRepository repository;
    private PreferenceManager preferences;
    private WallpaperManager wallpapers;
    private TextView greeting, greetingSub;
    private List<AppCategory> allCategories = new ArrayList<>(), currentCategories;
    private long appliedHomeRevision = -1L;
    private int currentTab = TAB_HOME, refreshGeneration;
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
        repository = new AppRepository(this);
        SoundManager.init(this);
        initViews();
        registerPackageReceiver();
        wallpapers = new WallpaperManager(this, preferences,
                findViewById(R.id.imgWallpaperBack), findViewById(R.id.imgWallpaperFront));
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
        bindNav(R.id.navHome, TAB_HOME);
        bindNav(R.id.navApps, TAB_APPS);
        bindNav(R.id.navMovies, TAB_MOVIES);
        View settings = findViewById(R.id.navSettings);
        settings.setOnClickListener(view -> startActivity(new Intent(this, SettingsActivity.class)));
        bindNavFocus(settings);
    }

    private void bindNav(int id, int tab) {
        View view = findViewById(id);
        view.setOnClickListener(source -> selectTab(tab));
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
            categoriesView.smoothScrollToPosition(0);
            return;
        }
        currentTab = tab;
        refreshApps(false);
        categoriesView.scrollToPosition(0);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        currentTab = TAB_HOME;
        refreshApps(false);
        categoriesView.scrollToPosition(0);
    }

    private void updateNavSelection() {
        styleNav(R.id.txtNavHome, R.id.imgNavHome, currentTab == TAB_HOME);
        styleNav(R.id.txtNavApps, R.id.imgNavApps, currentTab == TAB_APPS);
        styleNav(R.id.txtNavMovies, R.id.imgNavMovies, currentTab == TAB_MOVIES);
        styleNav(R.id.txtNavSettings, R.id.imgNavSettings, false);
    }

    private void styleNav(int labelId, int iconId, boolean selected) {
        int accent = preferences.getAccentColor(ContextCompat.getColor(this, R.color.nova_accent));
        TextView label = findViewById(labelId);
        ImageView icon = findViewById(iconId);
        label.setTextColor(selected ? accent : ContextCompat.getColor(this, R.color.nova_text_dim));
        label.setTypeface(null, selected ? 1 : 0);
        icon.setImageTintList(ColorStateList.valueOf(selected ? accent : ContextCompat.getColor(this, R.color.nova_text_dim)));
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
        if (packageReceiverRegistered) try { unregisterReceiver(packageReceiver); } catch (Exception ignored) { }
        if (wallpapers != null) wallpapers.shutdown();
        repository.shutdown();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        long revision = preferences.getHomeRevision();
        boolean changed = revision != appliedHomeRevision;
        appliedHomeRevision = revision;
        refreshApps(changed);
        updateNavSelection();
        if (wallpapers != null) wallpapers.start();
    }

    @Override protected void onPause() {
        if (wallpapers != null) wallpapers.stop();
        super.onPause();
    }

    private void refreshApps(boolean force) {
        if (force) repository.invalidate();
        int request = ++refreshGeneration;
        repository.loadCategoriesAsync(categories -> {
            if (request != refreshGeneration || isFinishing() || isDestroyed()) return;
            allCategories = categories;
            currentCategories = filterForTab(categories);
            categoryAdapter.setCategories(currentCategories);
            updateGreeting();
            updateNavSelection();
            updateFeatured();
        });
    }

    private List<AppCategory> filterForTab(List<AppCategory> categories) {
        if (currentTab == TAB_HOME) return categories;
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
        if (currentTab == TAB_MOVIES) {
            greeting.setText(R.string.greeting_cinema);
            greetingSub.setText(R.string.greeting_cinema_subtitle);
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
        if (picks.isEmpty()) for (AppCategory category : allCategories)
            if ("streaming".equals(category.getId()) || "all".equals(category.getId())) { picks.addAll(category.getApps()); break; }
        while (picks.size() > FEATURED_LIMIT) picks.remove(picks.size() - 1);
        for (int i = 0; i < picks.size(); i++) metas.add(getString(i < pinned ? R.string.featured_pinned : R.string.featured_ready));
        featuredAdapter.setItems(picks, metas);
    }

    @Override public void onAppClick(AppModel app) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(app.getPackageName());
            if (launch == null) {
                Toast.makeText(this, getString(R.string.error_cannot_launch, app.getLabel()), Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(launch);
            preferences.incrementLaunchCount();
        } catch (Exception exception) {
            Toast.makeText(this, getString(R.string.error_launch, exception.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onAppLongClick(AppModel app, String rowId) { showAppOptionsDialog(app, rowId); }

    private void showAppOptionsDialog(AppModel app, String rowId) {
        View view = getLayoutInflater().inflate(R.layout.dialog_app_options, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_AuraLauncher_Dialog).setView(view).create();
        ((ImageView) view.findViewById(R.id.dialogAppIcon)).setImageDrawable(app.getIcon());
        ((TextView) view.findViewById(R.id.dialogAppTitle)).setText(app.getLabel());
        ((TextView) view.findViewById(R.id.dialogAppPackage)).setText(app.getPackageName());
        Button launch = view.findViewById(R.id.btnOptLaunch), left = view.findViewById(R.id.btnOptMoveLeft), right = view.findViewById(R.id.btnOptMoveRight);
        Button favorite = view.findViewById(R.id.btnOptToggleFavorite), uninstall = view.findViewById(R.id.btnOptUninstall), force = view.findViewById(R.id.btnOptForceUninstall);
        Button hide = view.findViewById(R.id.btnOptHide), info = view.findViewById(R.id.btnOptAppInfo);
        if (rowId == null) { left.setVisibility(View.GONE); right.setVisibility(View.GONE); }
        boolean isFavorite = preferences.isFavorite(app.getPackageName());
        favorite.setText(isFavorite ? R.string.action_unfavorite : R.string.action_favorite);
        force.setText(R.string.action_force_uninstall);
        launch.setOnClickListener(source -> { dialog.dismiss(); onAppClick(app); });
        left.setOnClickListener(source -> { moveApp(rowId, app, -1); dialog.dismiss(); });
        right.setOnClickListener(source -> { moveApp(rowId, app, 1); dialog.dismiss(); });
        favorite.setOnClickListener(source -> { if (isFavorite) preferences.removeFavorite(app.getPackageName()); else preferences.addFavorite(app.getPackageName()); dialog.dismiss(); refreshApps(true); });
        uninstall.setOnClickListener(source -> { dialog.dismiss(); DebloatManager.requestNormalUninstall(this, app.getPackageName()); });
        force.setOnClickListener(source -> { dialog.dismiss(); DebloatManager.forceUninstall(app.getPackageName(), (success, message) -> Toast.makeText(this, message, Toast.LENGTH_LONG).show()); });
        hide.setOnClickListener(source -> { preferences.hideApp(app.getPackageName()); dialog.dismiss(); refreshApps(true); });
        info.setOnClickListener(source -> {
            dialog.dismiss();
            try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.getPackageName()))); }
            catch (ActivityNotFoundException exception) { Toast.makeText(this, R.string.error_app_details, Toast.LENGTH_SHORT).show(); }
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
            for (int i = 0; i < apps.size(); i++) if (apps.get(i).getPackageName().equals(app.getPackageName())) { index = i; break; }
            int target = index + direction;
            if (index < 0 || target < 0 || target >= apps.size()) return;
            Collections.swap(apps, index, target);
            List<String> packages = new ArrayList<>();
            for (AppModel model : apps) packages.add(model.getPackageName());
            preferences.setCustomOrder(rowId, packages);
            repository.invalidate();
            categoryAdapter.setCategories(currentCategories);
            return;
        }
    }

    @Override public void onBackPressed() {
        if (currentTab != TAB_HOME) selectTab(TAB_HOME); else categoriesView.smoothScrollToPosition(0);
    }
}
