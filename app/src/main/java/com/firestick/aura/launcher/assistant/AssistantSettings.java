package com.firestick.aura.launcher.assistant;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Assistant preferences. The OpenRouter key is kept in its own preferences file, which
 * res/xml/backup_rules.xml and data_extraction_rules.xml exclude from backup and device transfer.
 */
public final class AssistantSettings {
    public static final String DEFAULT_MODEL = "openrouter/free";
    private static final String SECRETS_FILE = "lumora_secrets";
    private static final String PREF_FILE = "lumora_assistant";
    private static final String KEY_API_KEY = "openrouter_api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_FLOATING = "floating_button";
    private static final String KEY_REMOTE = "remote_button";
    private static final String KEY_SHARE_APPS = "share_app_names";
    private static final String KEY_CONTROL = "tv_control";
    /** Left over from v5.2.0's learn mode; removed on first use. */
    private static final String[] LEGACY_KEYS = {"remote_key_codes", "learn_until", "learned_code"};

    private final SharedPreferences secrets, prefs;

    public AssistantSettings(Context context) {
        Context app = context.getApplicationContext();
        secrets = app.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE);
        prefs = app.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        if (prefs.contains(LEGACY_KEYS[0]) || prefs.contains(LEGACY_KEYS[1]) || prefs.contains(LEGACY_KEYS[2])) {
            SharedPreferences.Editor editor = prefs.edit();
            for (String key : LEGACY_KEYS) editor.remove(key);
            editor.apply();
        }
    }

    // ------------------------------------------------------------------ key

    public String getApiKey() {
        String key = secrets.getString(KEY_API_KEY, "");
        return key == null ? "" : key;
    }

    public boolean hasApiKey() {
        return !getApiKey().isEmpty();
    }

    public void setApiKey(String key) {
        secrets.edit().putString(KEY_API_KEY, key == null ? "" : key.trim()).apply();
    }

    public void clearApiKey() {
        secrets.edit().remove(KEY_API_KEY).apply();
    }

    // ---------------------------------------------------------------- model

    public String getModel() {
        String model = prefs.getString(KEY_MODEL, DEFAULT_MODEL);
        return model == null || model.trim().isEmpty() ? DEFAULT_MODEL : model;
    }

    public void setModel(String model) {
        prefs.edit().putString(KEY_MODEL, model == null ? DEFAULT_MODEL : model.trim()).apply();
    }

    // ------------------------------------------------------------- triggers

    public boolean isFloatingButtonEnabled() {
        return prefs.getBoolean(KEY_FLOATING, true);
    }

    public void setFloatingButtonEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FLOATING, enabled).apply();
    }

    /** Long-press of the remote's Menu button opens the assistant. */
    public boolean isRemoteButtonEnabled() {
        return prefs.getBoolean(KEY_REMOTE, true);
    }

    public void setRemoteButtonEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REMOTE, enabled).apply();
    }

    // -------------------------------------------------------------- control

    /** Whether the assistant may run TV actions (media keys, volume, navigation, settings, search). */
    public boolean isControlEnabled() {
        return prefs.getBoolean(KEY_CONTROL, true);
    }

    public void setControlEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CONTROL, enabled).apply();
    }

    // -------------------------------------------------------------- privacy

    public boolean isShareAppsEnabled() {
        return prefs.getBoolean(KEY_SHARE_APPS, true);
    }

    public void setShareAppsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHARE_APPS, enabled).apply();
    }
}
