package com.firestick.aura.launcher;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.KeyEvent;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.firestick.aura.launcher.adapter.AppRowAdapter;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.model.AppModel;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;

/** Focus/lifecycle smoke test; actual video advancement is verified separately from player telemetry. Injected tiles exist only in this test process; no provider apps or accounts required. */
public final class PreviewInstrumentation extends Instrumentation {
    private MainActivity activity;
    private RecyclerView home;
    private PreferenceManager preferences;
    private boolean originalEnabled;
    private boolean originalYouTubeLocked;
    private int assertions;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            preferences = new PreferenceManager(getTargetContext());
            originalEnabled = preferences.isAppPreviewEnabled();
            originalYouTubeLocked = preferences.isLocked("com.google.android.youtube.tv");
            preferences.setAppPreviewEnabled(true);
            Intent intent = new Intent(getTargetContext(), MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity = (MainActivity) startActivitySync(intent);
            SystemClock.sleep(1800L);
            waitForIdleSync();
            List<AppModel> samples = Arrays.asList(
                    app("com.netflix.ninja", "Netflix"),
                    app("com.amazon.amazonvideo.livingroom", "Prime Video"),
                    app("com.google.android.youtube.tv", "YouTube"),
                    app("com.example.unsupported", "Other app"));
            onMain(() -> {
                home = activity.findViewById(R.id.recyclerHome);
                ((AppRowAdapter) home.getAdapter()).setApps(samples);
            });
            waitForIdleSync();

            focus(0);
            visible(false, "Dwell must not reveal immediately");
            SystemClock.sleep(850L);
            visible(true, "Netflix preview appears");
            text(R.id.previewService, "Netflix · Public video preview");
            checkFocus();
            screenshot("netflix");
            SystemClock.sleep(6100L);
            text(R.id.previewService, "Netflix · Public video preview");
            checkFocus();

            focus(1);
            visible(false, "Switching services hides stale title");
            SystemClock.sleep(850L);
            text(R.id.previewService, "Prime Video · Public video preview");
            visible(true, "Prime preview appears");
            screenshot("prime");

            focus(2);
            SystemClock.sleep(150L);
            focus(3);
            SystemClock.sleep(850L);
            visible(false, "Fast traversal cancels pending reveal");
            focus(2);
            SystemClock.sleep(850L);
            visible(true, "YouTube preview appears");
            text(R.id.previewService, "YouTube · Public video preview");
            screenshot("youtube");
            checkFocus();

            focus(3);
            preferences.setLocked("com.google.android.youtube.tv", true);
            focus(2);
            SystemClock.sleep(850L);
            visible(false, "Locked apps never reveal previews");
            preferences.setLocked("com.google.android.youtube.tv", false);

            onMain(() -> activity.findViewById(R.id.navHome).requestFocus());
            SystemClock.sleep(750L);
            visible(false, "Leaving app strip cancels preview");
            focus(0);
            SystemClock.sleep(850L);
            onMain(() -> activity.findViewById(R.id.navApps).performClick());
            visible(false, "Changing tab cancels preview");

            onMain(() -> activity.findViewById(R.id.navHome).performClick());
            waitForIdleSync();
            // Refreshing Home restores installed apps; inject the test strip again.
            onMain(() -> ((AppRowAdapter) home.getAdapter()).setApps(samples));
            waitForIdleSync();
            preferences.setAppPreviewEnabled(false);
            focus(2);
            SystemClock.sleep(850L);
            visible(false, "Opt-out prevents previews");

            // The TV emulator includes YouTube. Exercise the real OK launch and pause path.
            if (getTargetContext().getPackageManager().getLeanbackLaunchIntentForPackage("com.google.android.youtube.tv") != null) {
                preferences.setAppPreviewEnabled(true);
                focus(3);
                focus(2);
                SystemClock.sleep(850L);
                visible(true, "Preview returns after opt-in");
                sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
                SystemClock.sleep(1200L);
                onMain(() -> {
                    if (activity.hasWindowFocus()) throw new AssertionError("OK did not open YouTube");
                    assertions++;
                });
                visible(false, "Launching app cancels preview on pause");
            }

            result.putString("stream", "PASS: " + assertions + " preview assertions; screenshots in target external files.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "FAIL: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (preferences != null) {
                preferences.setAppPreviewEnabled(originalEnabled);
                preferences.setLocked("com.google.android.youtube.tv", originalYouTubeLocked);
            }
        }
    }

    private AppModel app(String pkg, String label) {
        return new AppModel(pkg, "", label, getTargetContext().getDrawable(R.drawable.ic_nav_movies));
    }

    private void onMain(Runnable action) {
        Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    private void focus(int position) {
        onMain(() -> {
            RecyclerView.ViewHolder holder = home.findViewHolderForAdapterPosition(position);
            if (holder == null || !holder.itemView.requestFocus()) throw new AssertionError("Cannot focus tile " + position);
        });
    }

    private void visible(boolean expected, String reason) {
        onMain(() -> {
            boolean actual = activity.findViewById(R.id.appPreview).getVisibility() == View.VISIBLE;
            if (expected != actual) throw new AssertionError(reason);
            assertions++;
        });
    }

    private void text(int id, String expected) {
        onMain(() -> {
            if (!expected.contentEquals(((TextView) activity.findViewById(id)).getText()))
                throw new AssertionError("Expected " + expected);
            assertions++;
        });
    }

    private void checkFocus() {
        onMain(() -> {
            if (!home.hasFocus()) throw new AssertionError("Preview stole app focus");
            assertions++;
        });
    }

    private void screenshot(String name) throws Exception {
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new AssertionError("Screenshot unavailable");
        File file = new File(getTargetContext().getExternalFilesDir(null), "preview-" + name + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        bitmap.recycle();
    }
}
