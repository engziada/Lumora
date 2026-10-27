package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class DebloatManager {

    public static final List<String> TELEMETRY_PACKAGES = Arrays.asList(
            "com.amazon.tv.acr",                     // Automatic Content Recognition (watch tracker)
            "com.amazon.hybridadidservice",          // Advertising ID tracking service
            "com.amazon.perfc",                      // Performance Collection metrics
            "com.amazon.perfcollection",             // Performance Collection Service
            "com.amazon.device.telemetry.emitter",   // Telemetry emitter daemon
            "com.amazon.wirelessmetrics.service",    // WiFi/Bluetooth telemetry
            "com.amazon.advertising.service"         // Ad delivery service
    );

    public interface CommandCallback {
        void onResult(boolean success, String message);
    }

    public static void disableStockLauncher(CommandCallback callback) {
        new Thread(() -> {
            boolean r1 = runShellCommand("pm disable-user --user 0 com.amazon.tv.launcher");
            boolean r2 = runShellCommand("pm disable-user --user 0 com.amazon.firehomestarter");
            if (callback != null) {
                callback.onResult(r1 && r2, (r1 && r2) ? "Stock launcher successfully disabled!" : "Disable command attempted (check root/ADB permissions).");
            }
        }).start();
    }

    public static void enableStockLauncher(CommandCallback callback) {
        new Thread(() -> {
            boolean r1 = runShellCommand("pm enable com.amazon.tv.launcher");
            boolean r2 = runShellCommand("pm enable com.amazon.firehomestarter");
            if (callback != null) {
                callback.onResult(r1 && r2, (r1 && r2) ? "Stock launcher re-enabled!" : "Enable command attempted.");
            }
        }).start();
    }

    public static void forceUninstall(String packageName, CommandCallback callback) {
        new Thread(() -> {
            boolean ok = runShellCommand("pm uninstall -k --user 0 " + packageName);
            if (callback != null) {
                callback.onResult(ok, ok ? "Force uninstalled: " + packageName : "Unable to uninstall directly without ADB.");
            }
        }).start();
    }

    public static void debloatTelemetry(CommandCallback callback) {
        new Thread(() -> {
            int successCount = 0;
            for (String pkg : TELEMETRY_PACKAGES) {
                if (runShellCommand("pm uninstall -k --user 0 " + pkg)) {
                    successCount++;
                }
            }
            if (callback != null) {
                callback.onResult(successCount > 0, "Debloated " + successCount + "/" + TELEMETRY_PACKAGES.size() + " tracking services.");
            }
        }).start();
    }

    public static void requestNormalUninstall(Context context, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    private static boolean runShellCommand(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            int exit = process.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
