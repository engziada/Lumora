package com.firestick.aura.launcher.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.firestick.aura.launcher.AssistantActivity;
import com.firestick.aura.launcher.MainActivity;
import com.firestick.aura.launcher.assistant.AssistantSettings;
import com.firestick.aura.launcher.manager.PreferenceManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Accessibility service for the quick menu (screenshot, power dialog), the Home button and the
 * assistant button: Fire OS ignores third-party HOME activities, so when the stock home window
 * appears this service brings Lumora to the front. It only receives window-state events from the
 * stock home packages (see res/xml/quick_action_service.xml) and never reads window content.
 * Key events are filtered only to match the assistant key code; they are not stored or logged.
 */
public class QuickActionService extends AccessibilityService {
    private static final Set<String> STOCK_HOME = new HashSet<>(Arrays.asList(
            "com.amazon.tv.launcher", "com.amazon.firehomestarter"));
    private static final long REDIRECT_COOLDOWN_MS = 1500L;
    private static volatile QuickActionService instance;
    private static volatile long suppressUntil;
    private long lastRedirect;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence source = event.getPackageName();
        if (source == null || !STOCK_HOME.contains(source.toString())) return;
        long now = SystemClock.elapsedRealtime();
        if (now < suppressUntil || now - lastRedirect < REDIRECT_COOLDOWN_MS) return;
        if (!new PreferenceManager(this).isHomeRedirectEnabled()) return;
        lastRedirect = now;
        Intent home = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(home);
        } catch (RuntimeException ignored) {
            // The system refused a background start; the stock home stays in front.
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (isReservedKey(code)) return false;
        AssistantSettings settings = new AssistantSettings(this);
        if (settings.isLearning()) {
            // Swallow the key while learning so it does not also trigger Alexa or the focused app.
            if (event.getAction() == KeyEvent.ACTION_UP) settings.finishLearning(code);
            return true;
        }
        if (!settings.isRemoteButtonEnabled() || !settings.isAssistantKey(code)) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) openAssistant();
        return true;
    }

    /** Navigation, media and volume keys are never learned or captured. */
    private static boolean isReservedKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_POWER:
                return true;
            default:
                return false;
        }
    }

    private void openAssistant() {
        suppressRedirect(3_000L);
        Intent intent = new Intent(this, AssistantActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (RuntimeException ignored) {
            // Background start refused; nothing else to do from a key handler.
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public static boolean isRunning() {
        return instance != null;
    }

    /** Pauses the Home redirect, for example while the user opens Fire TV settings from Lumora. */
    public static void suppressRedirect(long millis) {
        suppressUntil = SystemClock.elapsedRealtime() + millis;
    }

    /** @return {@code true} when the service is connected and the system accepted the action. */
    public static boolean perform(int globalAction) {
        QuickActionService service = instance;
        return service != null && service.performGlobalAction(globalAction);
    }
}
