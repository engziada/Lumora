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

    private static final int TAB_HOME = 0;
    private static final int TAB_APPS = 1;
    private static final int TAB_MOVIES = 2;
    private static final int FEATURED_LIMIT = 6;

    private RecyclerView recyclerCategories;
    private RecyclerView recyclerFeatured;
    private CategoryRowAdapter categoryAdapter;
    private FeaturedRowAdapter featuredAdapter;

    private AppRepository repository;
    private PreferenceManager preferenceManager;
    private WallpaperManager wallpaperManager;

    private TextView txtGreeting;
    private TextView txtGreetingSub;

    private List<AppCategory> allCategories;
    private List<AppCategory> currentCategories;
    private int appliedAccent;
    private int currentTab = TAB_HOME;
    private boolean packageReceiverRegistered;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            repository.invalidateAll();
            refreshApps(true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferenceManager = new PreferenceManager(this);
        repository = new AppRepository(this);

        SoundManager.init(this);

        initViews();
        registerPackageReceiver();

        ImageView imgBack = findViewById(R.id.imgWallpaperBack);
        ImageView imgFront = findViewById(R.id.imgWallpaperFront);
        wallpaperManager = new WallpaperManager(this, preferenceManager, imgBack, imgFront);
    }

    private void initViews() {
        txtGreeting = findViewById(R.id.txtGreeting);
        txtGreetingSub = findViewById(R.id.txtGreetingSub);

        recyclerCategories = findViewById(R.id.recyclerCategories);
        recyclerCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        categoryAdapter = new CategoryRowAdapter(preferenceManager, this);
        recyclerCategories.setAdapter(categoryAdapter);

        recyclerFeatured = findViewById(R.id.recyclerFeatured);
        recyclerFeatured.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        featuredAdapter = new FeaturedRowAdapter(preferenceManager, this);
        recyclerFeatured.setAdapter(featuredAdapter);

        findViewById(R.id.navHome).setOnClickListener(v -> selectTab(TAB_HOME));
        findViewById(R.id.navApps).setOnClickListener(v -> selectTab(TAB_APPS));
        findViewById(R.id.navMovies).setOnClickListener(v -> selectTab(TAB_MOVIES));
        findViewById(R.id.navSettings).setOnClickListener(v -> {
            SoundManager.playClick(v, preferenceManager);
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        bindNavFocus(R.id.navHome, TAB_HOME);
        bindNavFocus(R.id.navApps, TAB_APPS);
        bindNavFocus(R.id.navMovies, TAB_MOVIES);
        bindNavFocus(R.id.navSettings, -1);
    }

    private void bindNavFocus(int navId, int tab) {
        View nav = findViewById(navId);
        nav.setOnFocusChangeListener((v, hasFocus) -> {
            v.animate().scaleX(hasFocus ? 1.04f : 1f).scaleY(hasFocus ? 1.04f : 1f)
                    .setDuration(120).start();
            if (hasFocus) {
                SoundManager.playFocus(v, preferenceManager);
            }
        });
        nav.setOnLongClickListener(null);
        if (tab >= 0) {
            nav.setOnClickListener(v -> selectTab(tab));
        }
    }

    private void selectTab(int tab) {
        if (currentTab == tab) {
            if (recyclerCategories != null) recyclerCategories.smoothScrollToPosition(0);
            return;
        }
        currentTab = tab;
        SoundManager.playClick(findViewById(tab == TAB_HOME ? R.id.navHome
                : tab == TAB_APPS ? R.id.navApps : R.id.navMovies), preferenceManager);
        refreshApps(false);
        if (recyclerCategories != null) recyclerCategories.scrollToPosition(0);
    }

    private void updateNavSelection() {
        styleNavItem(R.id.navHome, R.id.txtNavHome, R.id.imgNavHome, currentTab == TAB_HOME);
        styleNavItem(R.id.navApps, R.id.txtNavApps, R.id.imgNavApps, currentTab == TAB_APPS);
        styleNavItem(R.id.navMovies, R.id.txtNavMovies, R.id.imgNavMovies, currentTab == TAB_MOVIES);
        styleNavItem(R.id.navSettings, R.id.txtNavSettings, R.id.imgNavSettings, false);
    }

    private void styleNavItem(int navId, int labelId, int iconId, boolean selected) {
        int accent = preferenceManager.getAccentColor(
                ContextCompat.getColor(this, R.color.nova_accent));
        TextView label = findViewById(labelId);
        ImageView icon = findViewById(iconId);
        if (label != null) {
            label.setTextColor(selected ? accent : ContextCompat.getColor(this, R.color.nova_text_dim));
            label.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        if (icon != null) {
            icon.setImageTintList(ColorStateList.valueOf(selected
                    ? accent
                    : ContextCompat.getColor(this, R.color.nova_text_dim)));
        }
    }

    private void registerPackageReceiver() {
        if (packageReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        packageReceiverRegistered = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (packageReceiverRegistered) {
            try {
                unregisterReceiver(packageReceiver);
            } catch (Exception ignored) {
            }
            packageReceiverRegistered = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        int accent = preferenceManager.getAccentColor(
                ContextCompat.getColor(this, R.color.nova_accent));
        boolean accentChanged = accent != appliedAccent;
        appliedAccent = accent;

        refreshApps(accentChanged);
        updateNavSelection();
        if (wallpaperManager != null) {
            wallpaperManager.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (wallpaperManager != null) {
            wallpaperManager.stop();
        }
    }

    private void refreshApps(boolean force) {
        if (force) {
            repository.invalidate();
        }
        allCategories = repository.loadCategories();
        List<AppCategory> filtered = filterForTab(allCategories);

        if (force || filtered != currentCategories) {
            currentCategories = filtered;
            categoryAdapter.setCategories(filtered);
        }

        updateGreeting();
        updateNavSelection();
        updateFeatured();
    }

    private List<AppCategory> filterForTab(List<AppCategory> categories) {
        if (currentTab == TAB_HOME) {
            return categories;
        }
        List<AppCategory> filtered = new ArrayList<>();
        String wanted = currentTab == TAB_APPS ? "all" : "streaming";
        for (AppCategory category : categories) {
            if (wanted.equals(category.getId())) {
                filtered.add(category);
            }
        }
        if (filtered.isEmpty() && !categories.isEmpty()) {
            filtered.add(categories.get(0));
        }
        return filtered;
    }

    private void updateGreeting() {
        if (txtGreeting == null) return;
        switch (currentTab) {
            case TAB_APPS:
                txtGreeting.setText("ALL APPS");
                txtGreetingSub.setText("Everything installed. One clean grid.");
                break;
            case TAB_MOVIES:
                txtGreeting.setText("CINEMA");
                txtGreetingSub.setText("Your streaming services, front and centre.");
                break;
            default:
                int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                if (hour < 5) {
                    txtGreeting.setText("GOOD NIGHT");
                } else if (hour < 12) {
                    txtGreeting.setText("GOOD MORNING");
                } else if (hour < 18) {
                    txtGreeting.setText("GOOD AFTERNOON");
                } else {
                    txtGreeting.setText("GOOD EVENING");
                }
                int launches = preferenceManager.getLaunchCount();
                txtGreetingSub.setText(launches == 0
                        ? "Everything you love, none of the noise."
                        : "Welcome back. " + preferenceManager.getFavorites().size() + " pinned, "
                          + preferenceManager.getHiddenApps().size() + " hidden.");
                break;
        }
    }

    /** Featured strip: pinned favourites first, otherwise the first apps of the library. */
    private void updateFeatured() {
        if (recyclerFeatured == null) return;
        if (currentTab != TAB_HOME) {
            recyclerFeatured.setVisibility(View.GONE);
            featuredAdapter.setItems(new ArrayList<>(), new ArrayList<>());
            return;
        }
        recyclerFeatured.setVisibility(View.VISIBLE);

        List<AppModel> picks = new ArrayList<>();
        List<String> metas = new ArrayList<>();

        for (AppCategory category : allCategories) {
            if ("favorites".equals(category.getId())) {
                picks.addAll(category.getApps());
            }
        }
        int pinnedCount = picks.size();

        if (picks.isEmpty()) {
            for (AppCategory category : allCategories) {
                if ("streaming".equals(category.getId()) || "all".equals(category.getId())) {
                    picks.addAll(category.getApps());
                    break;
                }
            }
        }
        while (picks.size() > FEATURED_LIMIT) {
            picks.remove(picks.size() - 1);
        }

        for (int i = 0; i < picks.size(); i++) {
            metas.add(i < pinnedCount ? "PINNED FAVORITE" : "READY TO WATCH");
        }
        featuredAdapter.setItems(picks, metas);
    }

    // ------------------------------------------------------------------ apps

    @Override
    public void onAppClick(AppModel app) {
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.getPackageName());
            if (launchIntent != null) {
                startActivity(launchIntent);
                preferenceManager.incrementLaunchCount();
            } else {
                Toast.makeText(this, "Cannot launch " + app.getLabel(), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error launching: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAppLongClick(AppModel app) {
        showAppOptionsDialog(app);
    }

    private void showAppOptionsDialog(AppModel app) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_app_options, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_AuraLauncher_Dialog)
                .setView(dialogView)
                .create();

        ImageView imgIcon = dialogView.findViewById(R.id.dialogAppIcon);
        TextView txtTitle = dialogView.findViewById(R.id.dialogAppTitle);
        TextView txtPackage = dialogView.findViewById(R.id.dialogAppPackage);
        Button btnLaunch = dialogView.findViewById(R.id.btnOptLaunch);
        Button btnMoveLeft = dialogView.findViewById(R.id.btnOptMoveLeft);
        Button btnMoveRight = dialogView.findViewById(R.id.btnOptMoveRight);
        Button btnToggleFav = dialogView.findViewById(R.id.btnOptToggleFavorite);
        Button btnUninstall = dialogView.findViewById(R.id.btnOptUninstall);
        Button btnForceUninstall = dialogView.findViewById(R.id.btnOptForceUninstall);
        Button btnHide = dialogView.findViewById(R.id.btnOptHide);
        Button btnInfo = dialogView.findViewById(R.id.btnOptAppInfo);

        imgIcon.setImageDrawable(app.getIcon());
        txtTitle.setText(app.getLabel());
        txtPackage.setText(app.getPackageName());

        boolean isFav = preferenceManager.isFavorite(app.getPackageName());
        btnToggleFav.setText(isFav ? getString(R.string.action_unfavorite) : getString(R.string.action_favorite));

        btnLaunch.setOnClickListener(v -> {
            SoundManager.playClick(v, preferenceManager);
            dialog.dismiss();
            onAppClick(app);
        });

        btnMoveLeft.setOnClickListener(v -> {
            SoundManager.playClick(v, preferenceManager);
            moveApp(app, -1);
            dialog.dismiss();
        });

        btnMoveRight.setOnClickListener(v -> {
            SoundManager.playClick(v, preferenceManager);
            moveApp(app, 1);
            dialog.dismiss();
        });

        btnToggleFav.setOnClickListener(v -> {
            SoundManager.playClick(v, preferenceManager);
            if (isFav) {
                preferenceManager.removeFavorite(app.getPackageName());
                Toast.makeText(this, "Removed from Favorites", Toast.LENGTH_SHORT).show();
            } else {
                preferenceManager.addFavorite(app.getPackageName());
                Toast.makeText(this, "Pinned to Favorites", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
            refreshApps(true);
        });

        btnUninstall.setOnClickListener(v -> {
            SoundManager.playClick(v, preferenceManager);
            dialog.dismiss();
            DebloatManager.requestNormalUninstall(this, app.getPackageName());
        });

        btnForceUninstall.setOnClickListener(v -> {
            SoundManager.playClick(v, preferenceManager);
            dialog.dismiss();
            DebloatManager.forceUninstall(app.getPackageName(), (success, msg) -> runOnUiThread(() -> {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                refreshApps(true);
            }));
        });

        btnHide.setOnClickListener(v -> {
            SoundManager.playClick(v, preferenceManager);
            preferenceManager.hideApp(app.getPackageName());
            Toast.makeText(this, app.getLabel() + " hidden from launcher", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshApps(true);
        });

        btnInfo.setOnClickListener(v -> {
            SoundManager.playClick(v, preferenceManager);
            dialog.dismiss();
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + app.getPackageName()));
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Could not open app details", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
        btnLaunch.requestFocus();
    }

    private void moveApp(AppModel app, int direction) {
        for (AppCategory category : currentCategories) {
            List<AppModel> apps = category.getApps();
            int idx = -1;
            for (int i = 0; i < apps.size(); i++) {
                if (apps.get(i).getPackageName().equals(app.getPackageName())) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1) continue;

            int target = idx + direction;
            if (target < 0 || target >= apps.size()) {
                Toast.makeText(this, direction < 0 ? "Already first in this row" : "Already last in this row",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Collections.swap(apps, idx, target);
            persistOrder(apps);
            repository.invalidate();
            categoryAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Moved " + app.getLabel(), Toast.LENGTH_SHORT).show();
            return;
        }
    }

    private void persistOrder(List<AppModel> orderedRow) {
        List<String> moved = new ArrayList<>();
        for (AppModel model : orderedRow) {
            moved.add(model.getPackageName());
        }

        List<String> global = new ArrayList<>(preferenceManager.getCustomOrder());
        int insertAt = Integer.MAX_VALUE;
        for (int i = 0; i < global.size(); i++) {
            if (moved.contains(global.get(i))) {
                insertAt = Math.min(insertAt, i);
                break;
            }
        }
        global.removeAll(moved);
        if (insertAt == Integer.MAX_VALUE || insertAt > global.size()) {
            insertAt = global.size();
        }
        global.addAll(insertAt, moved);
        preferenceManager.setCustomOrder(global);
    }

    @Override
    public void onBackPressed() {
        if (currentTab != TAB_HOME) {
            selectTab(TAB_HOME);
            return;
        }
        if (recyclerCategories != null) {
            recyclerCategories.smoothScrollToPosition(0);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            if (currentTab != TAB_HOME) {
                selectTab(TAB_HOME);
                return true;
            }
            if (recyclerCategories != null) {
                recyclerCategories.smoothScrollToPosition(0);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
