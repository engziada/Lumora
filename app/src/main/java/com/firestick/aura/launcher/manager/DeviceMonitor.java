package com.firestick.aura.launcher.manager;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Locale;

/**
 * Reads live hardware figures. Call {@link #read()} from a background thread. Readings Fire OS
 * blocks for apps (for example /proc/stat on Android 8+) come back as "unavailable" values.
 */
public final class DeviceMonitor {
    public static final class Snapshot {
        public String model, system, hardware, network, address;
        public int cores, cpuPercent = -1, cpuCurMhz = -1, cpuMaxMhz = -1, wifiRssi = Integer.MIN_VALUE, wifiMbps = -1;
        public float temperature = Float.NaN;
        public long ramTotal, ramAvailable, storageTotal, storageFree, uptimeMs, lumoraPssKb;
        public boolean lowMemory;
    }

    private final Context context;
    private long[] lastCpu;

    public DeviceMonitor(Context context) {
        this.context = context.getApplicationContext();
    }

    public Snapshot read() {
        Snapshot s = new Snapshot();
        s.model = (Build.MANUFACTURER + " " + Build.MODEL).trim();
        s.system = String.format(Locale.US, "Android %s (API %d) \u00B7 %s", Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Build.DISPLAY);
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "";
        s.hardware = (Build.HARDWARE + " \u00B7 " + abi).trim();
        s.cores = Runtime.getRuntime().availableProcessors();
        readCpu(s);
        readMemory(s);
        readStorage(s);
        readNetwork(s);
        s.temperature = readTemperature();
        s.uptimeMs = SystemClock.elapsedRealtime();
        s.lumoraPssKb = Debug.getPss();
        return s;
    }

    private void readCpu(Snapshot s) {
        long[] current = DeviceStats.parseCpuLine(firstLine(new File("/proc/stat")));
        s.cpuPercent = DeviceStats.cpuPercent(lastCpu, current);
        if (current != null) lastCpu = current;
        long cur = 0, max = 0;
        int counted = 0;
        for (int i = 0; i < s.cores; i++) {
            long c = readLong(new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq"));
            long m = readLong(new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq"));
            if (c > 0) {
                cur += c;
                counted++;
            }
            if (m > max) max = m;
        }
        if (counted > 0) s.cpuCurMhz = (int) (cur / counted / 1000);
        if (max > 0) s.cpuMaxMhz = (int) (max / 1000);
    }

    private void readMemory(Snapshot s) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager == null) return;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        s.ramTotal = info.totalMem;
        s.ramAvailable = info.availMem;
        s.lowMemory = info.lowMemory;
    }

    private void readStorage(Snapshot s) {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            s.storageTotal = stat.getTotalBytes();
            s.storageFree = stat.getAvailableBytes();
        } catch (IllegalArgumentException ignored) {
            // Data partition not readable; leave zero.
        }
    }

    private void readNetwork(Snapshot s) {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        Network network = manager == null ? null : manager.getActiveNetwork();
        NetworkCapabilities caps = network == null ? null : manager.getNetworkCapabilities(network);
        if (caps == null) {
            s.network = "";
            return;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) s.network = "Ethernet";
        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) s.network = "Wi-Fi";
        else s.network = "Other";
        LinkProperties properties = manager.getLinkProperties(network);
        if (properties != null) {
            for (LinkAddress link : properties.getLinkAddresses()) {
                InetAddress address = link.getAddress();
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    s.address = address.getHostAddress();
                    break;
                }
            }
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            WifiManager wifi = context.getSystemService(WifiManager.class);
            WifiInfo info = wifi == null ? null : wifi.getConnectionInfo();
            if (info != null) {
                s.wifiRssi = info.getRssi();
                s.wifiMbps = info.getLinkSpeed();
            }
        }
    }

    /** Prefers a CPU / SoC zone; falls back to the first plausible zone. */
    private static float readTemperature() {
        File[] zones = new File("/sys/class/thermal").listFiles((dir, name) -> name.startsWith("thermal_zone"));
        if (zones == null) return Float.NaN;
        float fallback = Float.NaN;
        for (File zone : zones) {
            float celsius = DeviceStats.normaliseTemperature(readLong(new File(zone, "temp")));
            if (Float.isNaN(celsius)) continue;
            String type = firstLine(new File(zone, "type"));
            String lower = type == null ? "" : type.toLowerCase(Locale.US);
            if (lower.contains("cpu") || lower.contains("soc") || lower.contains("tsens")) return celsius;
            if (Float.isNaN(fallback)) fallback = celsius;
        }
        return fallback;
    }

    private static long readLong(File file) {
        String line = firstLine(file);
        if (line == null) return -1L;
        try {
            return Long.parseLong(line.trim());
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }

    private static String firstLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (IOException | SecurityException exception) {
            return null;
        }
    }
}
