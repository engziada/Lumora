package com.firestick.aura.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.ui.PaneFocus;
import java.io.*;
import java.lang.reflect.Field;

/** Offline pane-boundary checks plus an explicit bounded internet probe on the TV emulator. */
final class NavigationRegression {
    private final Instrumentation test;
    private Activity activity;
    private Dialog quick;
    private int checks;
    NavigationRegression(Instrumentation test) { this.test = test; }
    private void main(Runnable task) {
        Throwable[] error = {null};
        test.runOnMainSync(() -> { try { task.run(); } catch(Throwable e) { error[0] = e; } });
        if(error[0] != null) throw new AssertionError(error[0]);
        SystemClock.sleep(60);
    }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private void key(int code) {
        main(() -> {
            for(int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                KeyEvent event = new KeyEvent(action, code);
                if(quick != null) quick.dispatchKeyEvent(event); else activity.dispatchKeyEvent(event);
            }
        });
    }
    private Activity start(Class<?> type) {
        Instrumentation.ActivityMonitor monitor = test.addMonitor(type.getName(), null, false);
        test.getTargetContext().startActivity(new Intent(test.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Activity result = test.waitForMonitorWithTimeout(monitor, 15000); test.removeMonitor(monitor);
        check(result != null, "Activity started"); SystemClock.sleep(1600); return result;
    }
    void run() {
        Bundle result = new Bundle();
        SharedPreferences shared = test.getTargetContext().getSharedPreferences("ztv_launcher_prefs", Context.MODE_PRIVATE);
        String recent = shared.getString("pref_recent_apps", null);
        try {
            activity = start(SettingsActivity.class);
            int[] rail = {R.id.secAppearance,R.id.secWallpaper,R.id.secAudio,R.id.secShows,R.id.secLock,R.id.secAssistant,
                    R.id.secSetup,R.id.secSystem,R.id.secTools,R.id.secDevice,R.id.secAdb,R.id.secAbout};
            int[] panels = {R.id.panelAppearance,R.id.panelWallpaper,R.id.panelAudio,R.id.panelShows,R.id.panelLock,R.id.panelAssistant,
                    R.id.panelSetup,R.id.panelSystem,R.id.panelTools,R.id.panelDevice,R.id.scrollAdb,R.id.panelAbout};
            for(int i=0;i<rail.length;i++) {
                final int index=i;
                main(() -> activity.findViewById(rail[index]).requestFocus());
                key(KeyEvent.KEYCODE_DPAD_RIGHT);
                main(() -> check(PaneFocus.contains(activity.findViewById(panels[index]),activity.getCurrentFocus()), "Right enters panel " + index));
                key(KeyEvent.KEYCODE_DPAD_UP);
                main(() -> check(PaneFocus.contains(activity.findViewById(panels[index]),activity.getCurrentFocus()), "Up stays inside panel " + index));
                key(KeyEvent.KEYCODE_DPAD_DOWN);
                main(() -> check(PaneFocus.contains(activity.findViewById(panels[index]),activity.getCurrentFocus()), "Down stays inside panel " + index));
                key(KeyEvent.KEYCODE_DPAD_LEFT);
                main(() -> check(activity.getCurrentFocus() == activity.findViewById(rail[index]),"Left returns to matching rail " + index));
            }
            main(() -> activity.findViewById(rail[0]).requestFocus());
            key(KeyEvent.KEYCODE_DPAD_UP);
            main(() -> check(activity.getCurrentFocus() == activity.findViewById(rail[0]),"Rail top is bounded"));
            main(() -> activity.findViewById(rail[rail.length-1]).requestFocus());
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            main(() -> check(activity.getCurrentFocus() == activity.findViewById(rail[rail.length-1]),"Rail bottom is bounded"));
            main(() -> activity.finish());
            new PreferenceManager(test.getTargetContext()).recordLaunch("com.google.android.youtube.tv");
            activity = start(MainActivity.class);
            main(() -> activity.findViewById(R.id.navQuick).performClick());
            main(() -> {
                try {
                    Field menu = MainActivity.class.getDeclaredField("quickMenu");menu.setAccessible(true);
                    Field window = QuickMenu.class.getDeclaredField("dialog");window.setAccessible(true);
                    quick=(Dialog)window.get(menu.get(activity));
                } catch(Exception e) {throw new AssertionError(e);}
                ViewGroup detail=quick.findViewById(R.id.quickDetail);
                check(findIcon(detail),"Recent has app icons");
                check(findText(detail,"YouTube") != null,"Recent has app names");
            });
            SystemClock.sleep(700);
            capture("recent-grid");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            key(KeyEvent.KEYCODE_DPAD_UP);
            main(() -> check(PaneFocus.contains(quick.findViewById(R.id.quickDetail),quick.getCurrentFocus()),"Recent Up stays in content"));
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            main(() -> check(PaneFocus.contains(quick.findViewById(R.id.quickDetail),quick.getCurrentFocus()),"Memory Down stays in content"));
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            main(() -> check(((ViewGroup)quick.findViewById(R.id.quickTiles)).getChildAt(1).hasFocus(),"Memory returns to same menu"));
            key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_DOWN);
            capture("wifi-download");
            main(() -> {
                TextView button = findText(quick.findViewById(R.id.quickDetail), activity.getString(R.string.qa_speed_start));
                check(button != null,"Internet speed test is explicit"); button.performClick();
            });
            SystemClock.sleep(16000);
            main(() -> {
                ViewGroup detail=quick.findViewById(R.id.quickDetail);
                check(findText(detail,activity.getString(R.string.qa_speed_running)) == null,"Download completes or reports failure");
            });
            capture("wifi-download-result");
            result.putString("stream", "PASS: " + checks + " navigation/recent/download assertions.\n");
        } catch(Throwable error) { result.putString("stream", "FAIL: " + error + "\n"); }
        finally {
            if(quick!=null) main(() -> quick.dismiss());
            SharedPreferences.Editor editor=shared.edit(); if(recent==null) editor.remove("pref_recent_apps");else editor.putString("pref_recent_apps",recent);editor.commit();
        }
        test.finish(result.getString("stream").startsWith("PASS") ? Activity.RESULT_OK : Activity.RESULT_CANCELED,result);
    }
    private boolean findIcon(View view) {
        if(view instanceof ImageView && ((ImageView)view).getDrawable()!=null) return true;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) if(findIcon(((ViewGroup)view).getChildAt(i)))return true;
        return false;
    }
    private TextView findText(View view,String text) {
        if(view instanceof TextView && text.contentEquals(((TextView)view).getText()))return (TextView)view;
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=findText(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private void capture(String name)throws Exception {
        Bitmap bitmap=test.getUiAutomation().takeScreenshot();
        try(FileOutputStream output=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}bitmap.recycle();
    }
}
