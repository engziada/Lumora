package com.firestick.aura.launcher.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

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
    private static final String KEY_REMOTE_CODES = "remote_key_codes";
    private static final String KEY_SHARE_APPS = "share_app_names";
    private static final String KEY_LEARN_UNTIL = "learn_until";
    private static final String KEY_LEARNED = "learned_code";
    /** Keys Fire TV and Android TV remotes send for the voice / assistant button. */
    private static final String DEFAULT_CODES = KeyEvent.KEYCODE_SEARCH + "," + KeyEvent.KEYCODE_VOICE_ASSIST
            + "," + KeyEvent.KEYCODE_ASSIST;

    private final SharedPreferences secrets, prefs;

    public AssistantSettings(Context context) {
        Context app = context.getApplicationContext();
        secrets = app.getSharedPreferences(SECRETS_FILE, Context.MODE_PRIVATE);
        prefs = app.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
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

    public boolean isRemoteButtonEnabled() {
        return prefs.getBoolean(KEY_REMOTE, true);
    }

    public void setRemoteButtonEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REMOTE, enabled).apply();
    }

    public List<Integer> getRemoteKeyCodes() {
        List<Integer> codes = new ArrayList<>();
        String raw = prefs.getString(KEY_REMOTE_CODES, DEFAULT_CODES);
        if (raw == null) raw = DEFAULT_CODES;
        for (String part : raw.split(",")) {
            try {
                codes.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // Skip a corrupted entry.
            }
        }
        return codes;
    }

    public boolean isAssistantKey(int keyCode) {
        return getRemoteKeyCodes().contains(keyCode);
    }

    public void resetRemoteKeyCodes() {
        prefs.edit().remove(KEY_REMOTE_CODES).apply();
    }

    public String describeRemoteKeys() {
        List<String> names = new ArrayList<>();
        for (int code : getRemoteKeyCodes()) names.add(KeyEvent.keyCodeToString(code).replace("KEYCODE_", ""));
        return String.join(", ", names);
    }

    // ------------------------------------------------------------- learning

    /** The accessibility service records the next non-navigation key while learning is active. */
    public void startLearning(long durationMs) {
        prefs.edit().putLong(KEY_LEARN_UNTIL, SystemClock.elapsedRealtime() + durationMs).remove(KEY_LEARNED).apply();
    }

    public boolean isLearning() {
        return SystemClock.elapsedRealtime() < prefs.getLong(KEY_LEARN_UNTIL, 0L);
    }

    public void cancelLearning() {
        prefs.edit().remove(KEY_LEARN_UNTIL).apply();
    }

    public void finishLearning(int keyCode) {
        prefs.edit().remove(KEY_LEARN_UNTIL).putInt(KEY_LEARNED, keyCode)
                .putString(KEY_REMOTE_CODES, String.valueOf(keyCode)).apply();
    }

    /** @return the learned key code once, or 0. */
    public int takeLearnedCode() {
        int code = prefs.getInt(KEY_LEARNED, 0);
        if (code != 0) prefs.edit().remove(KEY_LEARNED).apply();
        return code;
    }

    // -------------------------------------------------------------- privacy

    public boolean isShareAppsEnabled() {
        return prefs.getBoolean(KEY_SHARE_APPS, true);
    }

    public void setShareAppsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHARE_APPS, enabled).apply();
    }
}
