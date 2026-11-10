package com.firestick.aura.launcher.ui;

import android.content.SharedPreferences;
import android.view.ViewGroup;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Settings controls for Home organization, appearance profiles and portable backup. */
public final class LauncherSettings {
    private final AppCompatActivity activity;
    private final PreferenceManager preferences;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> export;
    private final ActivityResultLauncher<String[]> restore;
    private String pendingBackup;
    private static final String[] ROWS = {"favorites", "recent", "iptv", "streaming", "apps"};
    private static final int[] NAMES = {R.string.category_favorites, R.string.home_recent_apps,
            R.string.home_recent_channels, R.string.nav_streaming, R.string.nav_apps};
    private static final Set<String> APPEARANCE = new HashSet<>(Arrays.asList("pref_accent_color",
            "pref_wallpaper_interval", "pref_wallpaper_source", "pref_wall_categories", "pref_card_effect",
            "pref_panel_content", "pref_tile_size", "pref_reduced_motion", "pref_home_sections"));

    public LauncherSettings(AppCompatActivity activity, PreferenceManager preferences) {
        this.activity = activity;
        this.preferences = preferences;
        export = activity.registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri == null) return;
            String exported;
            try { exported = pendingBackup == null ? LauncherBackup.encode(preferences.storage().getAll()) : pendingBackup; }
            catch (JSONException error) { message(R.string.backup_failed); return; }
            String payload = exported;
            io.execute(() -> {
                try (OutputStream stream = activity.getContentResolver().openOutputStream(uri, "wt")) {
                    if (stream == null) throw new IOException("Cannot open backup");
                    stream.write(payload.getBytes(StandardCharsets.UTF_8));
                    message(R.string.backup_saved);
                } catch (Exception error) { message(R.string.backup_failed); }
            });
        });
        restore = activity.registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null) return;
            io.execute(() -> {
                try (InputStream stream = activity.getContentResolver().openInputStream(uri)) {
                    if (stream == null) throw new IOException("Cannot open backup");
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = stream.read(buffer)) != -1) {
                        if (bytes.size() + count > 1024 * 1024) throw new IOException("Backup too large");
                        bytes.write(buffer, 0, count);
                    }
                    Map<String, Object> values = LauncherBackup.decode(bytes.toString("UTF-8"));
                    confirmRestore(values);
                } catch (Exception error) { message(R.string.backup_failed); }
            });
        });
        activity.getLifecycle().addObserver(new androidx.lifecycle.DefaultLifecycleObserver() {
            @Override public void onDestroy(androidx.lifecycle.LifecycleOwner owner) { io.shutdownNow(); }
        });
    }

    public void bind(ViewGroup appearance, ViewGroup tools) {
        button(appearance, R.string.home_customize, this::rows);
        button(appearance, R.string.home_tile_size, () -> {
            List<GlassMenu.Item> items = Arrays.asList(
                    new GlassMenu.Item(R.drawable.ic_nav_home, activity.getString(R.string.tile_compact)),
                    new GlassMenu.Item(R.drawable.ic_nav_home, activity.getString(R.string.tile_comfortable)));
            GlassMenu.show(activity, activity.getString(R.string.home_tile_size), null, null, items, false,
                    (index, item) -> preferences.setTileSize(index == 0 ? "compact" : "comfortable"));
        });
        CheckBox motion = new CheckBox(activity);
        motion.setId(android.view.View.generateViewId());
        motion.setText(R.string.reduced_motion);
        motion.setTextColor(ContextCompat.getColor(activity, R.color.nova_text));
        motion.setBackgroundResource(R.drawable.bg_option_selector);
        motion.setChecked(preferences.reducedMotion());
        motion.setOnCheckedChangeListener((button, checked) -> preferences.setReducedMotion(checked));
        appearance.addView(motion);
        button(appearance, R.string.profile_save, () -> TextPrompt.show(activity,
                activity.getString(R.string.profile_save), null, "", false, name -> {
                    try {
                        JSONObject profiles = new JSONObject(preferences.storage().getString("pref_appearance_profiles", "{}"));
                        if (profiles.length() >= 8 && !profiles.has(name)) { message(R.string.profile_limit); return; }
                        JSONObject values = new JSONObject();
                        for (String key : APPEARANCE) {
                            Object value = preferences.storage().getAll().get(key);
                            if (value instanceof Set) value = new JSONArray((Set<?>) value);
                            if (value != null) values.put(key, value);
                        }
                        profiles.put(name.substring(0, Math.min(name.length(), 40)), values);
                        preferences.storage().edit().putString("pref_appearance_profiles", profiles.toString()).apply();
                        message(R.string.profile_saved);
                    } catch (JSONException error) { message(R.string.backup_failed); }
                }));
        button(appearance, R.string.profile_load, this::profiles);
        button(tools, R.string.backup_export, () -> guarded(() -> {
            try {
                pendingBackup = LauncherBackup.encode(preferences.storage().getAll());
                try { export.launch("Lumora-backup.json"); }
                catch (android.content.ActivityNotFoundException unavailable) { saveLocalBackup(); }
            } catch (JSONException error) { message(R.string.backup_failed); }
        }));
        button(tools, R.string.backup_restore, () -> guarded(() -> {
            try { restore.launch(new String[]{"application/json", "text/plain"}); }
            catch (android.content.ActivityNotFoundException unavailable) { localBackups(); }
        }));
    }

    private void saveLocalBackup() {
        io.execute(() -> {
            File root = activity.getExternalFilesDir("backups");
            if (root == null || !Http.writeFile(new File(root, "Lumora-backup.json"),
                    pendingBackup.getBytes(StandardCharsets.UTF_8))) { message(R.string.backup_failed); return; }
            activity.runOnUiThread(() -> {
                if (!activity.isDestroyed()) Glass.info(activity, activity.getString(R.string.backup_export),
                        activity.getString(R.string.backup_local_path, new File(root, "Lumora-backup.json").getAbsolutePath()),
                        null, null, null);
            });
        });
    }

    private void localBackups() {
        List<File> files = new ArrayList<>();
        File own = activity.getExternalFilesDir(null);
        File[] roots = {
                activity.getExternalFilesDir("backups"),
                own == null ? null : new File(own, "From Phone"),
                new File(activity.getCacheDir(), "From Phone"),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        };
        for (File root : roots) {
            File[] children = root == null ? null : root.listFiles(file -> file.isFile() && file.getName().endsWith(".json"));
            if (children != null) files.addAll(Arrays.asList(children));
        }
        if (files.isEmpty()) { message(R.string.backup_local_empty); return; }
        List<GlassMenu.Item> items = new ArrayList<>();
        for (File file : files) items.add(new GlassMenu.Item(R.drawable.ic_act_info, file.getName()));
        GlassMenu.show(activity, activity.getString(R.string.backup_restore), null, null, items, false,
                (index, item) -> io.execute(() -> {
                    File file = files.get(index);
                    if (file.length() > 1024 * 1024) { message(R.string.backup_failed); return; }
                    byte[] data = Http.readFile(file);
                    try {
                        if (data == null) throw new IOException("Cannot read backup");
                        confirmRestore(LauncherBackup.decode(new String(data, StandardCharsets.UTF_8)));
                    } catch (Exception error) { message(R.string.backup_failed); }
                }));
    }

    private void confirmRestore(Map<String, Object> values) {
        activity.runOnUiThread(() -> {
            if (activity.isDestroyed()) return;
            Glass.info(activity, activity.getString(R.string.backup_restore), activity.getString(R.string.backup_restore_confirm),
                    null, activity.getString(R.string.backup_restore), () -> guarded(() -> {
                        apply(values, false);
                        activity.recreate();
                    }));
        });
    }

    private void guarded(Runnable action) {
        if (AppLock.hasCode(preferences)) LockPad.verify(activity, preferences, activity.getString(R.string.lock_enter_current), action::run);
        else action.run();
    }

    private void profiles() {
        try {
            JSONObject profiles = new JSONObject(preferences.storage().getString("pref_appearance_profiles", "{}"));
            List<String> names = new ArrayList<>();
            profiles.keys().forEachRemaining(names::add);
            if (names.isEmpty()) { message(R.string.profile_empty); return; }
            List<GlassMenu.Item> items = new ArrayList<>();
            for (String name : names) items.add(new GlassMenu.Item(R.drawable.ic_palette, name));
            GlassMenu.show(activity, activity.getString(R.string.profile_load), null, null, items, false, (index, item) -> {
                try {
                    JSONObject root = new JSONObject().put("format", "lumora-launcher").put("version", 1)
                            .put("values", profiles.getJSONObject(names.get(index)));
                    apply(LauncherBackup.decode(root.toString()), true);
                    activity.recreate();
                } catch (JSONException error) { message(R.string.backup_failed); }
            });
        } catch (JSONException error) { message(R.string.backup_failed); }
    }

    @SuppressWarnings("unchecked")
    private void apply(Map<String, Object> values, boolean profile) {
        SharedPreferences.Editor editor = preferences.storage().edit();
        if (profile) for (String key : APPEARANCE) editor.remove(key);
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
        }
        editor.putLong("pref_home_revision", preferences.getHomeRevision() + 1).apply();
    }

    private void rows() {
        List<String> order = preferences.homeSections();
        List<GlassMenu.Item> items = new ArrayList<>();
        for (String key : order) items.add(new GlassMenu.Item(R.drawable.ic_nav_home, label(key)));
        items.add(new GlassMenu.Item(R.drawable.ic_nav_home, activity.getString(R.string.home_add_row)));
        GlassMenu.show(activity, activity.getString(R.string.home_customize), null, null, items, false, (index, item) -> {
            if (index == order.size()) {
                List<String> missing = new ArrayList<>(Arrays.asList(ROWS));
                missing.removeAll(order);
                List<GlassMenu.Item> choices = new ArrayList<>();
                for (String key : missing) choices.add(new GlassMenu.Item(R.drawable.ic_nav_home, label(key)));
                GlassMenu.show(activity, activity.getString(R.string.home_add_row), null, null, choices, false,
                        (n, choice) -> { order.add(missing.get(n)); preferences.setHomeSections(order); });
            } else {
                List<GlassMenu.Item> choices = Arrays.asList(
                        new GlassMenu.Item(R.drawable.ic_act_move, activity.getString(R.string.home_move_up)),
                        new GlassMenu.Item(R.drawable.ic_act_move, activity.getString(R.string.home_move_down)),
                        new GlassMenu.Item(R.drawable.ic_act_hide, activity.getString(R.string.home_hide_row)));
                GlassMenu.show(activity, label(order.get(index)), null, null, choices, false, (n, choice) -> {
                    if (n == 0 && index > 0) Collections.swap(order, index, index - 1);
                    if (n == 1 && index + 1 < order.size()) Collections.swap(order, index, index + 1);
                    if (n == 2 && order.size() > 1) order.remove(index);
                    preferences.setHomeSections(order);
                    rows();
                });
            }
        });
    }

    public CharSequence label(String key) {
        for (int i = 0; i < ROWS.length; i++) if (ROWS[i].equals(key)) return activity.getString(NAMES[i]);
        return key;
    }

    private void button(ViewGroup parent, int text, Runnable action) {
        Button button = new Button(activity);
        button.setId(android.view.View.generateViewId());
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTextColor(ContextCompat.getColor(activity, R.color.nova_text));
        button.setBackgroundResource(R.drawable.bg_option_selector);
        button.setOnClickListener(view -> action.run());
        parent.addView(button, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(44 * activity.getResources().getDisplayMetrics().density)));
    }

    private void message(int resource) {
        activity.runOnUiThread(() -> {
            if (!activity.isDestroyed()) Toast.makeText(activity, resource, Toast.LENGTH_LONG).show();
        });
    }
}
