package com.firestick.aura.launcher.manager;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.provider.Settings;
import android.widget.Toast;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.service.QuickActionService;
import com.firestick.aura.launcher.ui.Glass;

import java.net.Inet4Address;
import java.net.InetAddress;

/**
 * Fire OS does not list third-party accessibility services in its settings, so the quick-actions
 * service cannot be switched on from the TV. After a one-time
 * {@code adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS}, Lumora enables its own
 * service (appending to any services already enabled) and keeps it enabled.
 */
public final class QuickAccess {
    private QuickAccess() {
    }

    public static boolean canWriteSecureSettings(Context context) {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    private static String component(Context context) {
        return new ComponentName(context, QuickActionService.class).flattenToString();
    }

    public static boolean isServiceEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String full = component(context);
        String shortName = new ComponentName(context, QuickActionService.class).flattenToShortString();
        for (String entry : enabled.split(":")) {
            if (entry.equalsIgnoreCase(full) || entry.equalsIgnoreCase(shortName)) return true;
        }
        return false;
    }

    /** @return {@code true} when the service is (now) enabled in secure settings. */
    public static boolean ensureServiceEnabled(Context context) {
        if (isServiceEnabled(context)) return true;
        if (!canWriteSecureSettings(context)) return false;
        ContentResolver resolver = context.getContentResolver();
        String current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String value = current == null || current.isEmpty() ? component(context) : current + ":" + component(context);
        try {
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, value);
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (SecurityException exception) {
            return false;
        }
    }

    /** IPv4 address of the active network, for the {@code adb connect} line. */
    public static String deviceAddress(Context context) {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) return null;
        Network network = manager.getActiveNetwork();
        if (network == null) return null;
        LinkProperties properties = manager.getLinkProperties(network);
        if (properties == null) return null;
        for (LinkAddress link : properties.getLinkAddresses()) {
            InetAddress address = link.getAddress();
            if (address instanceof Inet4Address && !address.isLoopbackAddress()) return address.getHostAddress();
        }
        return null;
    }

    public static String grantCommand(Context context) {
        return "adb shell pm grant " + context.getPackageName() + " android.permission.WRITE_SECURE_SETTINGS";
    }

    public static String setupCommands(Context context) {
        String ip = deviceAddress(context);
        return "adb connect " + (ip == null ? "<device-ip>" : ip) + ":5555\n" + grantCommand(context);
    }

    /** Explains the one-time ADB grant and offers to re-check. */
    public static void showSetup(Activity activity) {
        Glass.info(activity, activity.getString(R.string.quick_setup_title), activity.getString(R.string.quick_setup_message),
                setupCommands(activity), activity.getString(R.string.quick_setup_check), () -> {
                    boolean enabled = ensureServiceEnabled(activity);
                    Toast.makeText(activity, enabled ? R.string.quick_enabled : R.string.quick_still_missing, Toast.LENGTH_LONG).show();
                });
    }
}
