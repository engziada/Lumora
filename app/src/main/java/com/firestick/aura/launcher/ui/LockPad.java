package com.firestick.aura.launcher.ui;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.AppLock;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.SoundManager;

/** Arrow-key passcode pad: the code is a sequence of 4 to 8 D-pad directions. */
public final class LockPad {
    public interface OnDone {
        void onDone();
    }

    private static final int MODE_VERIFY = 0, MODE_CREATE = 1;
    private static final int MAX_FAILURES = 5;
    private static final long LOCKOUT_MS = 30_000L;
    private static int failures;
    private static long lockedUntil;

    private final Activity activity;
    private final PreferenceManager prefs;
    private final int mode;
    private final CharSequence subtitleText;
    private final OnDone done;
    private final StringBuilder entry = new StringBuilder();
    private String firstEntry;
    private Dialog dialog;
    private TextView subtitle, dots, error;

    private LockPad(Activity activity, PreferenceManager prefs, int mode, CharSequence subtitleText, OnDone done) {
        this.activity = activity;
        this.prefs = prefs;
        this.mode = mode;
        this.subtitleText = subtitleText;
        this.done = done;
    }

    /** Runs {@code done} after the correct code; runs it immediately when no code is set. */
    public static void verify(Activity activity, PreferenceManager prefs, CharSequence subtitle, OnDone done) {
        if (!AppLock.hasCode(prefs)) {
            done.onDone();
            return;
        }
        new LockPad(activity, prefs, MODE_VERIFY, subtitle, done).show();
    }

    public static void create(Activity activity, PreferenceManager prefs, OnDone done) {
        new LockPad(activity, prefs, MODE_CREATE, null, done).show();
    }

    /** Verifies the current code (if any), then records a new one. */
    public static void change(Activity activity, PreferenceManager prefs, OnDone done) {
        verify(activity, prefs, activity.getString(R.string.lock_enter_current), () -> create(activity, prefs, done));
    }

    private void show() {
        dialog = Glass.overlay(activity, R.layout.dialog_lock, Gravity.CENTER);
        TextView title = dialog.findViewById(R.id.lockTitle);
        subtitle = dialog.findViewById(R.id.lockSubtitle);
        dots = dialog.findViewById(R.id.lockDots);
        error = dialog.findViewById(R.id.lockError);
        int accent = prefs.getAccentColor(ContextCompat.getColor(activity, R.color.nova_accent));
        ((ImageView) dialog.findViewById(R.id.lockIcon)).setImageTintList(ColorStateList.valueOf(accent));
        dots.setTextColor(accent);
        if (mode == MODE_VERIFY) {
            title.setText(R.string.lock_title_verify);
            if (subtitleText != null) subtitle.setText(subtitleText);
            else subtitle.setText(R.string.lock_sub_verify);
        } else {
            title.setText(R.string.lock_title_create);
            subtitle.setText(R.string.lock_sub_create);
        }
        dialog.setOnKeyListener((d, keyCode, event) -> onKey(keyCode, event));
        dialog.show();
        render();
    }

    private static boolean isPadKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private boolean onKey(int keyCode, KeyEvent event) {
        if (!isPadKey(keyCode)) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return true;
        char symbol;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                symbol = 'U';
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                symbol = 'D';
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                symbol = 'L';
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                symbol = 'R';
                break;
            default:
                confirm();
                return true;
        }
        if (mode == MODE_VERIFY && SystemClock.elapsedRealtime() < lockedUntil) {
            showError(R.string.lock_wait);
            return true;
        }
        if (entry.length() >= AppLock.MAX_LENGTH) return true;
        entry.append(symbol);
        error.setVisibility(View.GONE);
        SoundManager.playFocus(dots, prefs);
        render();
        if (mode == MODE_VERIFY && entry.length() == prefs.getLockLength()) check();
        return true;
    }

    private void confirm() {
        if (mode == MODE_VERIFY) {
            if (entry.length() > 0) check();
            return;
        }
        if (entry.length() < AppLock.MIN_LENGTH) {
            showError(R.string.lock_too_short);
            return;
        }
        if (firstEntry == null) {
            firstEntry = entry.toString();
            entry.setLength(0);
            subtitle.setText(R.string.lock_sub_confirm);
            render();
            return;
        }
        if (firstEntry.contentEquals(entry)) {
            AppLock.setCode(prefs, firstEntry);
            dialog.dismiss();
            Toast.makeText(activity, R.string.lock_saved, Toast.LENGTH_SHORT).show();
            done.onDone();
        } else {
            firstEntry = null;
            entry.setLength(0);
            subtitle.setText(R.string.lock_sub_create);
            showError(R.string.lock_mismatch);
            render();
        }
    }

    private void check() {
        if (AppLock.verify(prefs, entry.toString())) {
            failures = 0;
            dialog.dismiss();
            done.onDone();
            return;
        }
        entry.setLength(0);
        failures++;
        if (failures >= MAX_FAILURES) {
            failures = 0;
            lockedUntil = SystemClock.elapsedRealtime() + LOCKOUT_MS;
            showError(R.string.lock_wait);
        } else {
            showError(R.string.lock_wrong);
        }
        render();
    }

    private void showError(int text) {
        error.setText(text);
        error.setVisibility(View.VISIBLE);
        ObjectAnimator.ofFloat(dots, View.TRANSLATION_X, 0f, 18f, -18f, 10f, -10f, 0f).setDuration(320).start();
    }

    private void render() {
        int length = entry.length();
        int slots = mode == MODE_VERIFY ? Math.max(length, prefs.getLockLength()) : Math.max(length, AppLock.MIN_LENGTH);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            if (i > 0) text.append(' ');
            text.append(i < length ? '\u25CF' : '\u25CB');
        }
        dots.setText(text);
    }
}
