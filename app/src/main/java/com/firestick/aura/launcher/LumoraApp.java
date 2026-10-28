package com.firestick.aura.launcher;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.firestick.aura.launcher.ui.UpdateFlow;

/** Runs the automatic update check whenever the Home screen comes to the front. */
public class LumoraApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof MainActivity) UpdateFlow.autoCheck(activity);
            }

            @Override public void onActivityCreated(Activity activity, Bundle state) {
            }

            @Override public void onActivityStarted(Activity activity) {
            }

            @Override public void onActivityPaused(Activity activity) {
            }

            @Override public void onActivityStopped(Activity activity) {
            }

            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {
            }

            @Override public void onActivityDestroyed(Activity activity) {
            }
        });
    }
}
