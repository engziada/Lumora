package com.firestick.aura.launcher.assistant;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;

import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.AppSearch;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.service.QuickActionService;

/**
 * Runs {@link TvAction}s with the means Fire OS gives a normal app: media keys to the active media
 * session, the music stream volume, global actions of Lumora's accessibility service, and intents.
 */
public final class TvController {
    public static final int OK = 0, NEEDS_QUICK = 1, VOLUME_FIXED = 2, NOT_INSTALLED = 3, FAILED = 4,
            UNSUPPORTED = 5, OPENED_ONLY = 6;
    /** Time for the assistant window to close before a global action or key reaches the app below. */
    public static final long CLOSE_DELAY_MS = 450L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private TvController() {
    }

    /** Checks that can fail before the assistant closes, so the reason can still be shown. */
    public static int precheck(Context context, TvAction action) {
        if (action.needsQuickActions() && !QuickActionService.isRunning()) return NEEDS_QUICK;
        if (TvAction.SCREENSHOT.equals(action.name) && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return UNSUPPORTED;
        if (TvAction.SEARCH.equals(action.name)
                && AppSearch.launchIntent(context.getPackageManager(), action.arg) == null) {
            return NOT_INSTALLED;
        }
        return OK;
    }

    /** Runs after {@link #CLOSE_DELAY_MS}; the result is not reported. */
    public static void runLater(Context context, TvAction action) {
        Context app = context.getApplicationContext();
        MAIN.postDelayed(() -> run(app, action), CLOSE_DELAY_MS);
    }

    /** Main thread only. */
    public static int run(Context context, TvAction action) {
        if (action.isMedia()) return media(context, action.name);
        if (action.isVolume()) return volume(context, action);
        switch (action.name) {
            case TvAction.BACK:
                return global(AccessibilityService.GLOBAL_ACTION_BACK);
            case TvAction.HOME:
                return global(AccessibilityService.GLOBAL_ACTION_HOME);
            case TvAction.POWER_MENU:
                return global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
            case TvAction.SCREENSHOT:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) : UNSUPPORTED;
            case TvAction.SETTINGS:
                return settings(context, action.arg);
            case TvAction.SEARCH: {
                int result = AppSearch.start(context, action.arg, action.query);
                return result == AppSearch.SEARCHED ? OK : result == AppSearch.OPENED_ONLY ? OPENED_ONLY : NOT_INSTALLED;
            }
            case TvAction.THEME:
                return theme(context, action.arg);
            default:
                return UNSUPPORTED;
        }
    }

    private static int global(int action) {
        if (!QuickActionService.isRunning()) return NEEDS_QUICK;
        return QuickActionService.perform(action) ? OK : FAILED;
    }

    private static int media(Context context, String name) {
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio == null) return FAILED;
        int code;
        switch (name) {
            case TvAction.PLAY:
                code = KeyEvent.KEYCODE_MEDIA_PLAY;
                break;
            case TvAction.PAUSE:
                code = KeyEvent.KEYCODE_MEDIA_PAUSE;
                break;
            case TvAction.NEXT:
                code = KeyEvent.KEYCODE_MEDIA_NEXT;
                break;
            case TvAction.PREVIOUS:
                code = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
                break;
            case TvAction.STOP:
                code = KeyEvent.KEYCODE_MEDIA_STOP;
                break;
            case TvAction.FORWARD:
                code = KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
                break;
            case TvAction.REWIND:
                code = KeyEvent.KEYCODE_MEDIA_REWIND;
                break;
            default:
                code = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
                break;
        }
        long now = SystemClock.uptimeMillis();
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
        audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0));
        return OK;
    }

    /**
     * Fire TV usually controls the TV or soundbar volume over HDMI-CEC or IR; then the stick's own
     * stream volume is fixed or does not move, which is reported instead of claiming success.
     */
    private static int volume(Context context, TvAction action) {
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio == null) return FAILED;
        if (audio.isVolumeFixed()) return VOLUME_FIXED;
        int stream = AudioManager.STREAM_MUSIC;
        switch (action.name) {
            case TvAction.MUTE:
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                return OK;
            case TvAction.UNMUTE:
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
                return OK;
            default:
                break;
        }
        boolean up = TvAction.VOLUME_UP.equals(action.name);
        int before = audio.getStreamVolume(stream);
        int max = audio.getStreamMaxVolume(stream);
        for (int i = 0; i < action.steps; i++) {
            int flags = i == action.steps - 1 ? AudioManager.FLAG_SHOW_UI : 0;
            audio.adjustStreamVolume(stream, up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, flags);
        }
        int after = audio.getStreamVolume(stream);
        boolean atLimit = up ? before >= max : before <= 0;
        return after == before && !atLimit ? VOLUME_FIXED : OK;
    }

    private static int settings(Context context, String page) {
        String action;
        switch (page) {
            case "wifi":
                action = Settings.ACTION_WIFI_SETTINGS;
                break;
            case "bluetooth":
                action = Settings.ACTION_BLUETOOTH_SETTINGS;
                break;
            case "display":
                action = Settings.ACTION_DISPLAY_SETTINGS;
                break;
            case "sound":
                action = Settings.ACTION_SOUND_SETTINGS;
                break;
            case "apps":
                action = Settings.ACTION_APPLICATION_SETTINGS;
                break;
            case "about":
                action = Settings.ACTION_DEVICE_INFO_SETTINGS;
                break;
            default:
                action = Settings.ACTION_SETTINGS;
                break;
        }
        QuickActionService.suppressRedirect(15_000L);
        if (start(context, action)) return OK;
        // Fire OS lacks some of the specific pages; fall back to the main settings screen.
        return start(context, Settings.ACTION_SETTINGS) ? OPENED_ONLY : FAILED;
    }

    private static boolean start(Context context, String action) {
        try {
            context.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException exception) {
            return false;
        }
    }

    private static int theme(Context context, String theme) {
        int color;
        switch (theme) {
            case "copper":
                color = R.color.accent_copper;
                break;
            case "rose":
                color = R.color.accent_rose;
                break;
            case "moonlight":
                color = R.color.accent_moonlight;
                break;
            default:
                color = R.color.accent_amber;
                break;
        }
        new PreferenceManager(context).setAccentColor(ContextCompat.getColor(context, color));
        return OK;
    }
}
