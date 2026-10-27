package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

/** Provides safe uninstall intents and read-only ADB command guidance. */
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

    public static String disableStockLauncherCommands() {
        return "adb shell pm disable-user --user 0 com.amazon.tv.launcher\n"
                + "adb shell pm disable-user --user 0 com.amazon.firehomestarter";
    }

    public static String enableStockLauncherCommands() {
        return "adb shell pm enable com.amazon.tv.launcher\n"
                + "adb shell pm enable com.amazon.firehomestarter";
    }

    public static String forceUninstallCommand(String packageName) {
        return "adb shell pm uninstall -k --user 0 " + packageName;
    }

    public static String telemetryCommands() {
        StringBuilder commands = new StringBuilder();
        for (String packageName : TELEMETRY_PACKAGES) {
            if (commands.length() > 0) commands.append('\n');
            commands.append(forceUninstallCommand(packageName));
        }
        return commands.toString();
    }

    /** Kept as a UI callback for the app-options dialog; no command is executed in-process. */
    public static void forceUninstall(String packageName, CommandCallback callback) {
        if (callback != null) callback.onResult(false,
                "Run from an ADB-connected computer:\n" + forceUninstallCommand(packageName));
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
}
