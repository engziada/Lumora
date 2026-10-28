package com.firestick.aura.launcher.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.service.QuickActionService;
import com.firestick.aura.launcher.update.UpdateInfo;
import com.firestick.aura.launcher.update.UpdateManager;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Update dialogs: automatic check on Home, manual check from Settings, download and install. */
public final class UpdateFlow {
    private static final long AUTO_DELAY_MS = 8_000L;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private UpdateFlow() {
    }

    /** Called when Home resumes: checks in the background when the chosen interval has passed. */
    public static void autoCheck(Activity activity) {
        UpdateManager manager = new UpdateManager(activity);
        if (!manager.isAutoCheckDue() || BUSY.get()) return;
        MAIN.postDelayed(() -> {
            if (!alive(activity) || !activity.hasWindowFocus() || !manager.isAutoCheckDue()) return;
            if (!BUSY.compareAndSet(false, true)) return;
            WORKER.execute(() -> {
                UpdateManager.Check check = manager.check();
                MAIN.post(() -> {
                    BUSY.set(false);
                    if (check.status == UpdateManager.AVAILABLE && alive(activity)) offer(activity, manager, check.info);
                });
            });
        }, AUTO_DELAY_MS);
    }

    /** Manual check; {@code onDone} runs on the main thread afterwards (for refreshing the status line). */
    public static void checkNow(Activity activity, Runnable onDone) {
        if (!BUSY.compareAndSet(false, true)) {
            Toast.makeText(activity, R.string.update_busy, Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
        UpdateManager manager = new UpdateManager(activity);
        WORKER.execute(() -> {
            UpdateManager.Check check = manager.check();
            MAIN.post(() -> {
                BUSY.set(false);
                if (onDone != null) onDone.run();
                if (!alive(activity)) return;
                switch (check.status) {
                    case UpdateManager.AVAILABLE:
                        offer(activity, manager, check.info);
                        break;
                    case UpdateManager.UP_TO_DATE:
                        Toast.makeText(activity, activity.getString(R.string.update_current, manager.installedVersionCode()),
                                Toast.LENGTH_LONG).show();
                        break;
                    case UpdateManager.ERR_NO_RELEASE:
                        Glass.info(activity, activity.getString(R.string.update_title), activity.getString(R.string.update_err_no_release),
                                null, null, null);
                        break;
                    case UpdateManager.ERR_FORMAT:
                        Glass.info(activity, activity.getString(R.string.update_title), activity.getString(R.string.update_err_format),
                                null, null, null);
                        break;
                    default:
                        Glass.info(activity, activity.getString(R.string.update_title), activity.getString(R.string.update_err_network),
                                null, null, null);
                        break;
                }
            });
        });
    }

    private static boolean alive(Activity activity) {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void offer(Activity activity, UpdateManager manager, UpdateInfo info) {
        String message = activity.getString(R.string.update_available_message, info.versionName, info.versionCode,
                manager.installedVersionCode(), info.size / (1024f * 1024f));
        if (!info.notes.isEmpty()) message += "\n\n" + info.notes;
        Glass.info(activity, activity.getString(R.string.update_available_title), message, null,
                activity.getString(R.string.update_install), () -> start(activity, manager, info));
    }

    private static void start(Activity activity, UpdateManager manager, UpdateInfo info) {
        if (!manager.canInstall()) {
            Glass.info(activity, activity.getString(R.string.update_permission_title),
                    activity.getString(R.string.update_permission_message), manager.grantInstallCommand(),
                    activity.getString(R.string.update_permission_open), () -> openInstallSetting(activity));
            return;
        }
        if (!BUSY.compareAndSet(false, true)) return;
        AtomicBoolean cancelled = new AtomicBoolean();
        Dialog dialog = Glass.info(activity, activity.getString(R.string.update_downloading_title),
                activity.getString(R.string.update_downloading, 0), null, null, null);
        TextView text = dialog.findViewById(R.id.infoMessage);
        View close = dialog.findViewById(R.id.btnInfoClose);
        close.setOnClickListener(v -> {
            cancelled.set(true);
            dialog.dismiss();
        });
        dialog.setOnCancelListener(d -> cancelled.set(true));
        WORKER.execute(() -> {
            File apk = null;
            int error = UpdateManager.VERIFIED;
            try {
                apk = manager.download(info, (done, total) -> MAIN.post(() ->
                        text.setText(activity.getString(R.string.update_downloading, (int) (done * 100 / Math.max(1, total))))), cancelled);
            } catch (UpdateManager.UpdateException exception) {
                error = exception.code;
            }
            File result = apk;
            int failure = error;
            MAIN.post(() -> {
                BUSY.set(false);
                if (dialog.isShowing()) dialog.dismiss();
                if (failure == UpdateManager.ERR_CANCELLED || !alive(activity)) return;
                if (result == null) {
                    showFailure(activity, manager, failure);
                    return;
                }
                QuickActionService.suppressRedirect(120_000L);
                try {
                    activity.startActivity(manager.installIntent(result));
                } catch (ActivityNotFoundException | SecurityException exception) {
                    Glass.info(activity, activity.getString(R.string.update_title),
                            activity.getString(R.string.update_err_installer), null, null, null);
                }
            });
        });
    }

    private static void showFailure(Activity activity, UpdateManager manager, int code) {
        int message;
        String commands = null;
        switch (code) {
            case UpdateManager.ERR_SIGNATURE:
                message = R.string.update_err_signature;
                commands = "adb uninstall " + activity.getPackageName() + "\nadb install lumora.apk\n"
                        + "adb shell pm grant " + activity.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS\n"
                        + manager.grantInstallCommand();
                break;
            case UpdateManager.ERR_HASH:
                message = R.string.update_err_hash;
                break;
            case UpdateManager.ERR_NOT_NEWER:
                message = R.string.update_err_not_newer;
                break;
            case UpdateManager.ERR_PACKAGE:
                message = R.string.update_err_package;
                break;
            case UpdateManager.ERR_STORAGE:
                message = R.string.update_err_storage;
                break;
            default:
                message = R.string.update_err_download;
                break;
        }
        Glass.info(activity, activity.getString(R.string.update_title), activity.getString(message), commands, null, null);
    }

    /** Android's per-app "install unknown apps" screen; Fire OS keeps it under Developer options. */
    private static void openInstallSetting(Activity activity) {
        QuickActionService.suppressRedirect(60_000L);
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(activity, R.string.update_permission_missing_screen, Toast.LENGTH_LONG).show();
        }
    }
}
