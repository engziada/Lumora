package com.firestick.aura.launcher;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.recyclerview.widget.RecyclerView;
import com.firestick.aura.launcher.adapter.AppRowAdapter;
import com.firestick.aura.launcher.manager.AppLock;
import com.firestick.aura.launcher.manager.AppRepository;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.model.AppModel;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline device regression for lock authorization, particle lifecycle and the remote menu. */
final class LockQuickRegression {
    private final Instrumentation test;
    private MainActivity activity;
    private PreferenceManager prefs;
    private int assertions;
    private static final String PKG = "com.google.android.youtube.tv";
    LockQuickRegression(Instrumentation test) { this.test = test; }

    void run() {
        Bundle result = new Bundle();
        prefs = new PreferenceManager(test.getTargetContext());
        String hash = prefs.getLockHash(), salt = prefs.getLockSalt();
        int length = prefs.getLockLength(), content = prefs.getPanelContent();
        boolean locked = prefs.isLocked(PKG);
        try {
            AppLock.setCode(prefs, "UDLR");
            prefs.setLocked(PKG, true);
            prefs.setPanelContent(PreferenceManager.PANEL_CONTENT_PARTICLES);
            Instrumentation.ActivityMonitor monitor = test.addMonitor(MainActivity.class.getName(), null, false);
            test.getTargetContext().startActivity(new Intent(test.getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            activity = (MainActivity) test.waitForMonitorWithTimeout(monitor, 15000);
            test.removeMonitor(monitor);
            check(activity != null, "Launcher starts");
            pause(3500);
            AppModel app = new AppModel(PKG, "", "YouTube", test.getTargetContext().getDrawable(R.drawable.ic_nav_apps));
            main(() -> {
                activity.findViewById(R.id.navHome).requestFocus();
                RecyclerView home = activity.findViewById(R.id.recyclerHome);
                ((AppRowAdapter) home.getAdapter()).setApps(Arrays.asList(app));
            });
            pause(300);
            main(() -> ((RecyclerView) activity.findViewById(R.id.recyclerHome))
                    .findViewHolderForAdapterPosition(0).itemView.requestFocus());
            pause(3500);
            main(() -> {
                Object particles = activity.findViewById(R.id.previewParticles);
                try {
                    java.lang.reflect.Field lock = particles.getClass().getDeclaredField("locked"); lock.setAccessible(true);
                    java.lang.reflect.Field morph = particles.getClass().getDeclaredField("morph"); morph.setAccessible(true);
                    java.lang.reflect.Field name = particles.getClass().getDeclaredField("label"); name.setAccessible(true);
                    StringBuilder detail = new StringBuilder();
                    for (String field : new String[]{"locked", "morph", "label", "state", "textReady", "phaseTime", "time", "active"}) {
                        java.lang.reflect.Field value = particles.getClass().getDeclaredField(field); value.setAccessible(true);
                        detail.append(field).append('=').append(value.get(particles)).append(' ');
                    }
                    detail.append("width=").append(((View) particles).getWidth()).append(" height=").append(((View) particles).getHeight());
                    check(lock.getBoolean(particles) && morph.getFloat(particles) > .95f, detail.toString());
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            capture("locked-name");
            main(() -> check(activity.findViewById(R.id.previewParticles).isShown(), "Locked particles visible; window=" + activity.hasWindowFocus()));
            pause(1400);
            main(() -> check(activity.findViewById(R.id.previewParticles).isShown(), "Locked name persists"));
            capture("locked-orbit");
            int[] actions = {R.string.action_open, R.string.action_move, R.string.action_add_home,
                    R.string.action_move_to_apps, R.string.action_unlock, R.string.action_hide,
                    R.string.action_info, R.string.action_uninstall};
            for (int label : actions) {
                main(() -> activity.onAppLongClick(app, AppRepository.ROW_APPS));
                pause(150);
                node(activity.getString(label));
                int index = 0;
                while (actions[index] != label) index++;
                for (int i = 0; i < index; i++) key(KeyEvent.KEYCODE_DPAD_DOWN);
                key(KeyEvent.KEYCODE_DPAD_CENTER);
                pause(150);
                node(activity.getString(R.string.lock_title_verify));
                key(KeyEvent.KEYCODE_BACK);
                check(prefs.isLocked(PKG) && !prefs.isFavorite(PKG) && !prefs.getHiddenApps().contains(PKG), "Cancel preserves app");
            }
            AtomicInteger executed = new AtomicInteger();
            main(() -> activity.runUnlocked(PKG, executed::incrementAndGet));
            pause(500);
            for (int i = 0; i < 4; i++) key(KeyEvent.KEYCODE_DPAD_UP);
            check(executed.get() == 0, "Wrong key denied");
            key(KeyEvent.KEYCODE_BACK);
            check(executed.get() == 0, "Cancel denied");
            main(() -> activity.runUnlocked(PKG, executed::incrementAndGet));
            pause(500);
            node(activity.getString(R.string.lock_title_verify));
            for (int code : new int[]{19,20,21,22}) key(code);
            pause(800);
            check(executed.get() == 1 && prefs.isLocked(PKG), "Correct key runs once without removing lock: executed=" + executed.get() + " locked=" + prefs.isLocked(PKG));
            main(() -> activity.runUnlocked(PKG, executed::incrementAndGet));
            pause(500);
            node(activity.getString(R.string.lock_title_verify));
            key(KeyEvent.KEYCODE_BACK);
            check(executed.get() == 1, "Next action needs a fresh key");
            main(() -> {
                View quick = activity.findViewById(R.id.navQuick), gear = activity.findViewById(R.id.btnGear);
                int[] q = new int[2], g = new int[2]; quick.getLocationOnScreen(q); gear.getLocationOnScreen(g);
                check(q[0] < g[0] && q[1] == g[1], "Quick menu beside settings");
            });
            key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            node(activity.getString(R.string.quick_title));
            for (int label : new int[]{R.string.qa_recent,R.string.qa_memory,R.string.qa_screenshot,R.string.qa_power,R.string.qa_wifi}) {
                Rect bounds = new Rect(); node(activity.getString(label)).getBoundsInScreen(bounds);
                check(!bounds.isEmpty(), "Quick action visible");
            }
            capture("quick-menu");
            int[] panelTitles = {R.string.qa_memory_sub, R.string.qa_capture_sub, R.string.qa_power_sub, R.string.qa_wifi_sub};
            for (int subtitle : panelTitles) {
                key(KeyEvent.KEYCODE_DPAD_DOWN);
                node(activity.getString(subtitle));
                capture("quick-panel-" + subtitle);
            }
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            node(activity.getString(R.string.qa_wifi_settings));
            key(KeyEvent.KEYCODE_BACK);
            main(() -> activity.findViewById(R.id.navQuick).performClick());
            node(activity.getString(R.string.quick_title));
            key(KeyEvent.KEYCODE_BACK);
            prefs.setLockCode(null, null, 0);
            main(() -> activity.runUnlocked(PKG, executed::incrementAndGet));
            pause(500);
            check(executed.get() == 1, "Missing key fails closed");
            result.putString("stream", "PASS: " + assertions + " lock/quick-menu assertions.\n");
        } catch (Throwable error) {
            try { capture("failure"); } catch (Exception ignored) { }
            result.putString("stream", "FAIL: " + error + "\n");
        } finally {
            prefs.setLocked(PKG, locked);
            prefs.setLockCode(hash, salt, length);
            prefs.setPanelContent(content);
        }
        test.finish(result.getString("stream").startsWith("PASS") ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); assertions++; }
    private void main(Runnable action) {
        Throwable[] error = new Throwable[1];
        test.runOnMainSync(() -> { try { action.run(); } catch (Throwable e) { error[0] = e; } });
        if (error[0] != null) throw new AssertionError(error[0]);
    }
    private void pause(long ms) { SystemClock.sleep(ms); }
    private void key(int code) { test.sendKeyDownUpSync(code); pause(100); }
    private AccessibilityNodeInfo node(String text) {
        for (int attempt = 0; attempt < 20; attempt++) {
            AccessibilityNodeInfo root = test.getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                    if (text.contentEquals(node.getText() == null ? "" : node.getText())) { assertions++; return node; }
                }
            }
            pause(150);
        }
        try { capture("failure"); } catch (Exception ignored) { }
        throw new AssertionError("Missing UI: " + text);
    }
    private void capture(String name) throws Exception {
        Bitmap image = test.getUiAutomation().takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null), name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        image.recycle();
    }
}
