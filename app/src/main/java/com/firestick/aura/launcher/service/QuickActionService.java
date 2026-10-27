package com.firestick.aura.launcher.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility service used only to trigger system actions from the quick menu
 * (screenshot, sleep, power dialog). It requests no window content and ignores events.
 */
public class QuickActionService extends AccessibilityService {
    private static volatile QuickActionService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally empty: the service never reads screen content.
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

    /** @return {@code true} when the service is connected and the system accepted the action. */
    public static boolean perform(int globalAction) {
        QuickActionService service = instance;
        return service != null && service.performGlobalAction(globalAction);
    }
}
