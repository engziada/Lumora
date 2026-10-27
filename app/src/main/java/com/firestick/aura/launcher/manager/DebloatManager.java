package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.firestick.aura.launcher.R;

import java.util.Arrays;
import java.util.List;

/** Normal uninstall intents plus read-only ADB command text for protected Fire OS packages. */
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

    private DebloatManager() {
    }

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

    public static void requestNormalUninstall(Context context, String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception exception) {
            Toast.makeText(context, R.string.error_uninstall, Toast.LENGTH_LONG).show();
        }
    }
}
