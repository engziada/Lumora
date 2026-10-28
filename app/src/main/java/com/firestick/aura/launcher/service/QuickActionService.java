package com.firestick.aura.launcher.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.firestick.aura.launcher.AssistantActivity;
import com.firestick.aura.launcher.MainActivity;
import com.firestick.aura.launcher.assistant.AssistantSettings;
import com.firestick.aura.launcher.assistant.LongPress;
import com.firestick.aura.launcher.manager.PreferenceManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Accessibility service for the quick menu (screenshot, power dialog), the Home button and the
 * assistant shortcut: Fire OS ignores third-party HOME activities, so when the stock home window
 * appears this service brings Lumora to the front. It only receives window-state events from the
 * stock home packages (see res/xml/quick_action_service.xml) and never reads window content.
 * Key filtering looks only at the Menu button; key events are not stored or logged.
 */
public class QuickActionService extends AccessibilityService {
    private static final Set<String> STOCK_HOME = new HashSet<>(Arrays.asList(
            "com.amazon.tv.launcher", "com.amazon.firehomestarter"));
    private static final long REDIRECT_COOLDOWN_MS = 1500L;
    private static volatile QuickActionService instance;
    private static volatile long suppressUntil;
    private long lastRedirect;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LongPress menuPress = new LongPress();
    private final Runnable menuHeld = () -> {
        if (menuPress.check(SystemClock.uptimeMillis())) openAssistant();
    };
    private AssistantSettings assistant;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        assistant = new AssistantSettings(this);
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

    /**
     * Holding Menu opens the assistant. The first down event is passed on, because an accessibility
     * service cannot re-inject a key it swallowed and a short press must still work as Menu. Once the
     * hold fires, the remaining repeats and the up event are consumed, so apps that act on key-up
     * (Lumora does) see no Menu press; the focus change to the assistant cancels the key for the app.
     */
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_MENU) return false;
        if (assistant == null || !assistant.isRemoteButtonEnabled()) {
            handler.removeCallbacks(menuHeld);
            menuPress.up();
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                menuPress.down(event.getEventTime());
                handler.removeCallbacks(menuHeld);
                handler.postDelayed(menuHeld, menuPress.holdMs());
                return false;
            }
            // Some input stacks deliver repeats here; use them as a second trigger.
            if (menuPress.check(event.getEventTime())) {
                handler.removeCallbacks(menuHeld);
                openAssistant();
            }
            return menuPress.hasFired();
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(menuHeld);
            return menuPress.up();
        }
        return false;
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
        handler.removeCallbacks(menuHeld);
        menuPress.up();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        handler.removeCallbacks(menuHeld);
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(menuHeld);
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
