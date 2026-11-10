package com.firestick.aura.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.firestick.aura.launcher.manager.*;
import com.firestick.aura.launcher.model.*;
import com.firestick.aura.launcher.ui.LockPad;
import java.util.*;

/** One search surface for visible installed apps, public channels and launcher settings. */
public final class SearchActivity extends AppCompatActivity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private AppRepository apps;
    private IptvRepository iptv;
    private PreferenceManager preferences;
    private final List<AppModel> appList = new ArrayList<>();
    private List<IptvChannel> channels = new ArrayList<>();
    private LinearLayout results;
    private EditText field;
    private TextView status;
    private boolean channelsLoading, channelsLoaded;
    private final Runnable search = this::render;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = new PreferenceManager(this);
        apps = new AppRepository(this);
        iptv = new IptvRepository(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(36), dp(24), dp(36), dp(16));
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.nova_bg));
        field = new EditText(this);
        field.setId(R.id.globalSearchField);
        field.setSingleLine();
        field.setHint(R.string.global_search_hint);
        field.setTextSize(18);
        field.setTextColor(ContextCompat.getColor(this, R.color.nova_text));
        field.setHintTextColor(ContextCompat.getColor(this, R.color.nova_text_dim));
        field.setBackgroundResource(R.drawable.bg_option_selector);
        field.setPadding(dp(16), 0, dp(16), 0);
        root.addView(field, new LinearLayout.LayoutParams(-1, dp(48)));
        status = new TextView(this);
        status.setTextColor(ContextCompat.getColor(this, R.color.nova_text_dim));
        root.addView(status);
        ScrollView scroll = new ScrollView(this);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                main.removeCallbacks(search);
                main.postDelayed(search, 250);
            }
            @Override public void afterTextChanged(Editable text) { }
        });
        field.setOnEditorActionListener((view, action, event) -> {
            if (results.getChildCount() > 0) results.getChildAt(0).requestFocus();
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(field.getWindowToken(), 0);
            return true;
        });
        apps.loadCategoriesAsync(categories -> {
            if (isDestroyed()) return;
            Set<String> seen = new HashSet<>();
            for (AppCategory category : categories) for (AppModel app : category.getApps())
                if (seen.add(app.getPackageName())) appList.add(app);
            render();
        });
        field.requestFocus();
    }

    private void render() {
        if (isDestroyed()) return;
        String query = field.getText().toString().trim().toLowerCase(Locale.ROOT);
        CharSequence focused = getCurrentFocus() instanceof Button ? ((Button) getCurrentFocus()).getText() : null;
        results.removeAllViews();
        if (query.isEmpty()) { status.setText(R.string.global_search_hint); return; }
        for (AppModel app : appList) {
            if (app.getLabel().toLowerCase(Locale.ROOT).contains(query)) {
                add(app.getLabel() + " · " + getString(R.string.nav_apps), () -> {
                    Runnable launch = () -> {
                        Intent intent = getPackageManager().getLeanbackLaunchIntentForPackage(app.getPackageName());
                        if (intent == null) intent = getPackageManager().getLaunchIntentForPackage(app.getPackageName());
                        if (intent != null) {
                            try { startActivity(intent); preferences.recordLaunch(app.getPackageName()); }
                            catch (RuntimeException unavailable) { Toast.makeText(this, R.string.error_not_installed_short, Toast.LENGTH_SHORT).show(); }
                        }
                    };
                    if (preferences.isLocked(app.getPackageName()))
                        LockPad.verify(this, preferences, getString(R.string.lock_enter_current), launch::run);
                    else launch.run();
                });
            }
        }
        // Section captions share Settings' rail labels.
        int[] rail = {R.string.search_appearance, R.string.search_wallpaper, R.string.search_audio,
                R.string.shows_label, R.string.search_lock, R.string.search_assistant,
                R.string.search_home_setup, R.string.search_system, R.string.search_tools,
                R.string.search_device, R.string.search_adb, R.string.search_updates};
        for (int i = 0; i < rail.length; i++) {
            String label = getString(rail[i]);
            final int section = i;
            if (label.toLowerCase(Locale.ROOT).contains(query))
                add(label + " · " + getString(R.string.nav_settings), () -> openSettings(section));
        }
        if (!channelsLoading && !channelsLoaded && preferences.isShowsEnabled()) {
            channelsLoading = true;
            iptv.loadChannels("ALL", (list, failed) -> {
                if (isDestroyed()) return;
                channelsLoading = false;
                channelsLoaded = true;
                channels = list;
                render();
            });
        }
        List<IptvChannel> matches = new ArrayList<>();
        for (IptvChannel channel : channels)
            if (channel.name.toLowerCase(Locale.ROOT).contains(query) && matches.size() < 40) matches.add(channel);
        for (IptvChannel channel : matches) add(channel.name + " · IPTV", () -> {
            Runnable play = () -> PlaybackSession.open(this, matches, channel);
            if (channel.isAdult()) LockPad.verify(this, preferences, getString(R.string.iptv_adults_unlock), play::run);
            else play.run();
        });
        if (focused != null) {
            for (int i = 0; i < results.getChildCount(); i++) {
                Button button = (Button) results.getChildAt(i);
                if (focused.toString().contentEquals(button.getText())) { button.requestFocus(); break; }
            }
        }
        status.setText(channelsLoading ? R.string.global_search_loading
                : results.getChildCount() == 0 ? R.string.iptv_no_results : R.string.global_search_results);
    }

    private void openSettings(int section) {
        Runnable open = () -> startActivity(new Intent(this, SettingsActivity.class)
                .putExtra(SettingsActivity.EXTRA_SECTION, section));
        if (preferences.isSettingsLocked()) LockPad.verify(this, preferences, getString(R.string.lock_enter_current), open::run);
        else open.run();
    }

    private void add(String label, Runnable action) {
        if (results.getChildCount() >= 60) return;
        Button row = new Button(this);
        row.setText(label);
        row.setAllCaps(false);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setTextSize(14);
        row.setTextColor(ContextCompat.getColor(this, R.color.nova_text));
        row.setBackgroundResource(R.drawable.bg_option_selector);
        row.setOnClickListener(view -> action.run());
        results.addView(row, new LinearLayout.LayoutParams(-1, dp(46)));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        apps.shutdown();
        iptv.shutdown();
        super.onDestroy();
    }
}
