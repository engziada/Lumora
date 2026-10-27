package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

public final class DebloatManager {
    public static final List<String> TELEMETRY_PACKAGES = Arrays.asList(
            "com.amazon.tv.acr",
            "com.amazon.hybridadidservice",
            "com.amazon.perfc",
            "com.amazon.perfcollection",
            "com.amazon.device.telemetry.emitter",
            "com.amazon.wirelessmetrics.service",
            "com.amazon.advertising.service"
    );

    public interface CommandCallback { void onResult(boolean success, String message); }

    private DebloatManager() { }

    public static void disableStockLauncher(CommandCallback callback) {
        new Thread(() -> {
            boolean first = runShellCommand("pm disable-user --user 0 com.amazon.tv.launcher");
            boolean second = runShellCommand("pm disable-user --user 0 com.amazon.firehomestarter");
            if (callback != null) callback.onResult(first && second,
                    first && second ? "Stock launcher successfully disabled!" : "Disable command requires root or ADB permissions.");
        }).start();
    }

    public static void enableStockLauncher(CommandCallback callback) {
        new Thread(() -> {
            boolean first = runShellCommand("pm enable com.amazon.tv.launcher");
            boolean second = runShellCommand("pm enable com.amazon.firehomestarter");
            if (callback != null) callback.onResult(first && second,
                    first && second ? "Stock launcher re-enabled!" : "Enable command requires root or ADB permissions.");
        }).start();
    }

    public static void forceUninstall(String packageName, CommandCallback callback) {
        new Thread(() -> {
            boolean success = runShellCommand("pm uninstall -k --user 0 " + packageName);
            if (callback != null) callback.onResult(success,
                    success ? "Force uninstalled: " + packageName : "Unable to uninstall directly without ADB.");
        }).start();
    }

    public static void debloatTelemetry(CommandCallback callback) {
        new Thread(() -> {
            int count = 0;
            for (String pkg : TELEMETRY_PACKAGES)
                if (runShellCommand("pm uninstall -k --user 0 " + pkg)) count++;
            if (callback != null) callback.onResult(count > 0,
                    "Debloated " + count + "/" + TELEMETRY_PACKAGES.size() + " tracking services.");
        }).start();
    }

    public static void requestNormalUninstall(Context context, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(context, "Unable to open the system uninstall screen.", Toast.LENGTH_LONG).show();
        }
    }

    private static boolean runShellCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            return process.waitFor() == 0;
        } catch (Exception exception) {
            return false;
        }
    }
}
