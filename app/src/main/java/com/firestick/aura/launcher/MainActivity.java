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
import android.view.KeyEvent;
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
    private RecyclerView recyclerCategories, recyclerFeatured;
    private CategoryRowAdapter categoryAdapter;
    private FeaturedRowAdapter featuredAdapter;
    private AppRepository repository;
    private PreferenceManager preferences;
    private WallpaperManager wallpaperManager;
    private TextView greeting, greetingSub;
    private List<AppCategory> allCategories, currentCategories;
    private long appliedHomeRevision = -1L;
    private int currentTab = TAB_HOME;
    private boolean packageReceiverRegistered;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { repository.invalidateAll(); refreshApps(true); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_main);
        preferences = new PreferenceManager(this); repository = new AppRepository(this); SoundManager.init(this);
        initViews(); registerPackageReceiver();
        wallpaperManager = new WallpaperManager(this, preferences, findViewById(R.id.imgWallpaperBack), findViewById(R.id.imgWallpaperFront));
    }

    private void initViews() {
        greeting = findViewById(R.id.txtGreeting); greetingSub = findViewById(R.id.txtGreetingSub);
        recyclerCategories = findViewById(R.id.recyclerCategories);
        recyclerCategories.setLayoutManager(new LinearLayoutManager(this));
        categoryAdapter = new CategoryRowAdapter(preferences, this); recyclerCategories.setAdapter(categoryAdapter);
        recyclerFeatured = findViewById(R.id.recyclerFeatured);
        recyclerFeatured.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        featuredAdapter = new FeaturedRowAdapter(preferences, this); recyclerFeatured.setAdapter(featuredAdapter);
        bindNav(R.id.navHome, TAB_HOME); bindNav(R.id.navApps, TAB_APPS); bindNav(R.id.navMovies, TAB_MOVIES);
        View settings = findViewById(R.id.navSettings);
        settings.setOnClickListener(v -> { SoundManager.playClick(v, preferences); startActivity(new Intent(this, SettingsActivity.class)); });
        bindNavFocus(settings);
    }

    private void bindNav(int id, int tab) { View view = findViewById(id); view.setOnClickListener(v -> selectTab(tab)); bindNavFocus(view); }
    private void bindNavFocus(View view) { view.setOnFocusChangeListener((v, focused) -> { v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(120).start(); if (focused) SoundManager.playFocus(v, preferences); }); }

    private void selectTab(int tab) {
        if (currentTab == tab) { recyclerCategories.smoothScrollToPosition(0); return; }
        currentTab = tab; refreshApps(false); recyclerCategories.scrollToPosition(0);
    }

    private void updateNavSelection() {
        styleNav(R.id.txtNavHome, R.id.imgNavHome, currentTab == TAB_HOME);
        styleNav(R.id.txtNavApps, R.id.imgNavApps, currentTab == TAB_APPS);
        styleNav(R.id.txtNavMovies, R.id.imgNavMovies, currentTab == TAB_MOVIES);
        styleNav(R.id.txtNavSettings, R.id.imgNavSettings, false);
    }

    private void styleNav(int labelId, int iconId, boolean selected) {
        int accent = preferences.getAccentColor(ContextCompat.getColor(this, R.color.nova_accent));
        TextView label = findViewById(labelId); ImageView icon = findViewById(iconId);
        label.setTextColor(selected ? accent : ContextCompat.getColor(this, R.color.nova_text_dim));
        label.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        icon.setImageTintList(ColorStateList.valueOf(selected ? accent : ContextCompat.getColor(this, R.color.nova_text_dim)));
    }

    private void registerPackageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED); filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED); filter.addAction(Intent.ACTION_PACKAGE_CHANGED); filter.addDataScheme("package");
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED); packageReceiverRegistered = true;
    }

    @Override protected void onDestroy() {
        if (packageReceiverRegistered) try { unregisterReceiver(packageReceiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume(); long revision = preferences.getHomeRevision(); boolean changed = revision != appliedHomeRevision;
        appliedHomeRevision = revision; refreshApps(changed); updateNavSelection(); if (wallpaperManager != null) wallpaperManager.start();
    }
    @Override protected void onPause() { if (wallpaperManager != null) wallpaperManager.stop(); super.onPause(); }

    private void refreshApps(boolean force) {
        if (force) repository.invalidate(); allCategories = repository.loadCategories();
        List<AppCategory> filtered = filterForTab(allCategories);
        if (force || filtered != currentCategories) { currentCategories = filtered; categoryAdapter.setCategories(filtered); }
        updateGreeting(); updateNavSelection(); updateFeatured();
    }

    private List<AppCategory> filterForTab(List<AppCategory> categories) {
        if (currentTab == TAB_HOME) return categories;
        List<AppCategory> filtered = new ArrayList<>(); String wanted = currentTab == TAB_APPS ? "all" : "streaming";
        for (AppCategory category : categories) if (wanted.equals(category.getId())) filtered.add(category);
        if (filtered.isEmpty() && !categories.isEmpty()) filtered.add(categories.get(0)); return filtered;
    }

    private void updateGreeting() {
        if (currentTab == TAB_APPS) { greeting.setText("ALL APPS"); greetingSub.setText("Everything installed. One clean grid."); return; }
        if (currentTab == TAB_MOVIES) { greeting.setText("CINEMA"); greetingSub.setText("Your streaming services, front and centre."); return; }
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText(hour < 5 ? "GOOD NIGHT" : hour < 12 ? "GOOD MORNING" : hour < 18 ? "GOOD AFTERNOON" : "GOOD EVENING");
        greetingSub.setText(preferences.getLaunchCount() == 0 ? "Everything you love, none of the noise."
                : "Welcome back. " + preferences.getFavorites().size() + " pinned, " + preferences.getHiddenApps().size() + " hidden.");
    }

    private void updateFeatured() {
        if (currentTab != TAB_HOME) { recyclerFeatured.setVisibility(View.GONE); featuredAdapter.setItems(new ArrayList<>(), new ArrayList<>()); return; }
        recyclerFeatured.setVisibility(View.VISIBLE); List<AppModel> picks = new ArrayList<>(); List<String> metas = new ArrayList<>();
        for (AppCategory category : allCategories) if ("favorites".equals(category.getId())) picks.addAll(category.getApps());
        int pinned = picks.size();
        if (picks.isEmpty()) for (AppCategory category : allCategories) if ("streaming".equals(category.getId()) || "all".equals(category.getId())) { picks.addAll(category.getApps()); break; }
        while (picks.size() > FEATURED_LIMIT) picks.remove(picks.size() - 1);
        for (int i = 0; i < picks.size(); i++) metas.add(i < pinned ? "PINNED FAVORITE" : "READY TO WATCH");
        featuredAdapter.setItems(picks, metas);
    }

    @Override public void onAppClick(AppModel app) {
        try { Intent launch = getPackageManager().getLaunchIntentForPackage(app.getPackageName());
            if (launch == null) { Toast.makeText(this, "Cannot launch " + app.getLabel(), Toast.LENGTH_SHORT).show(); return; }
            startActivity(launch); preferences.incrementLaunchCount();
        } catch (Exception e) { Toast.makeText(this, "Error launching: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
    }

    @Override public void onAppLongClick(AppModel app, String rowId) { showAppOptionsDialog(app, rowId); }

    private void showAppOptionsDialog(AppModel app, String rowId) {
        View view = getLayoutInflater().inflate(R.layout.dialog_app_options, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_AuraLauncher_Dialog).setView(view).create();
        ((ImageView) view.findViewById(R.id.dialogAppIcon)).setImageDrawable(app.getIcon());
        ((TextView) view.findViewById(R.id.dialogAppTitle)).setText(app.getLabel());
        ((TextView) view.findViewById(R.id.dialogAppPackage)).setText(app.getPackageName());
        Button launch = view.findViewById(R.id.btnOptLaunch), left = view.findViewById(R.id.btnOptMoveLeft), right = view.findViewById(R.id.btnOptMoveRight);
        Button favorite = view.findViewById(R.id.btnOptToggleFavorite), uninstall = view.findViewById(R.id.btnOptUninstall);
        Button force = view.findViewById(R.id.btnOptForceUninstall), hide = view.findViewById(R.id.btnOptHide), info = view.findViewById(R.id.btnOptAppInfo);
        if (rowId == null) { left.setVisibility(View.GONE); right.setVisibility(View.GONE); }
        boolean isFavorite = preferences.isFavorite(app.getPackageName()); favorite.setText(isFavorite ? R.string.action_unfavorite : R.string.action_favorite);
        launch.setOnClickListener(v -> { dialog.dismiss(); onAppClick(app); });
        left.setOnClickListener(v -> { moveApp(rowId, app, -1); dialog.dismiss(); }); right.setOnClickListener(v -> { moveApp(rowId, app, 1); dialog.dismiss(); });
        favorite.setOnClickListener(v -> { if (isFavorite) preferences.removeFavorite(app.getPackageName()); else preferences.addFavorite(app.getPackageName()); dialog.dismiss(); refreshApps(true); });
        uninstall.setOnClickListener(v -> { dialog.dismiss(); DebloatManager.requestNormalUninstall(this, app.getPackageName()); });
        force.setOnClickListener(v -> { dialog.dismiss(); DebloatManager.forceUninstall(app.getPackageName(), (ok, message) -> runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show())); });
        hide.setOnClickListener(v -> { preferences.hideApp(app.getPackageName()); dialog.dismiss(); refreshApps(true); });
        info.setOnClickListener(v -> { dialog.dismiss(); try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.getPackageName()))); } catch (ActivityNotFoundException e) { Toast.makeText(this, "Could not open app details", Toast.LENGTH_SHORT).show(); } });
        dialog.show(); launch.requestFocus();
    }

    private void moveApp(String rowId, AppModel app, int direction) {
        if (rowId == null) return;
        for (AppCategory category : currentCategories) {
            if (!rowId.equals(category.getId())) continue;
            List<AppModel> apps = category.getApps(); int index = -1;
            for (int i = 0; i < apps.size(); i++) if (apps.get(i).getPackageName().equals(app.getPackageName())) { index = i; break; }
            int target = index + direction; if (index < 0 || target < 0 || target >= apps.size()) return;
            Collections.swap(apps, index, target); persistOrder(rowId, apps); repository.invalidate(); categoryAdapter.notifyDataSetChanged(); return;
        }
    }

    private void persistOrder(String rowId, List<AppModel> row) {
        List<String> packages = new ArrayList<>(); for (AppModel app : row) packages.add(app.getPackageName());
        preferences.setCustomOrder(rowId, packages);
    }

    @Override public void onBackPressed() { if (currentTab != TAB_HOME) selectTab(TAB_HOME); else recyclerCategories.smoothScrollToPosition(0); }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) { if (keyCode == KeyEvent.KEYCODE_HOME) { if (currentTab != TAB_HOME) selectTab(TAB_HOME); else recyclerCategories.smoothScrollToPosition(0); return true; } return super.onKeyDown(keyCode, event); }
}
