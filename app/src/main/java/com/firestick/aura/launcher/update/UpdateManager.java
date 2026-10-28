package com.firestick.aura.launcher.update;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.core.content.pm.PackageInfoCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Self-update from this repository's GitHub releases. CI publishes {@code update.json} and a signed
 * APK for every build of {@code main}; Lumora downloads the APK, checks size, SHA-256, package name,
 * version and signing certificate, then hands it to the system installer (the user confirms).
 * Blocking methods must run off the main thread.
 */
public final class UpdateManager {
    public static final String UPDATE_URL = "https://github.com/engziada/Lumora/releases/latest/download/update.json";
    public static final int UP_TO_DATE = 0, AVAILABLE = 1, ERR_NETWORK = 2, ERR_FORMAT = 3, ERR_NO_RELEASE = 4;
    public static final int VERIFIED = 0, ERR_HASH = 10, ERR_PACKAGE = 11, ERR_NOT_NEWER = 12, ERR_SIGNATURE = 13,
            ERR_DOWNLOAD = 14, ERR_CANCELLED = 15, ERR_STORAGE = 16;

    private static final String TAG = "LumoraUpdate";
    private static final String PREFS = "lumora_update", KEY_INTERVAL = "interval_hours", KEY_LAST_CHECK = "last_check";
    private static final String DIR = "updates", APK_NAME = "lumora-update.apk";
    private static final int CONNECT_TIMEOUT_MS = 15_000, READ_TIMEOUT_MS = 30_000, MAX_JSON_BYTES = 16 * 1024;

    public interface Progress {
        /** Called on the download thread. */
        void onProgress(long done, long total);
    }

    public static final class Check {
        public final int status;
        public final UpdateInfo info;

        Check(int status, UpdateInfo info) {
            this.status = status;
            this.info = info;
        }
    }

    public static final class UpdateException extends Exception {
        public final int code;

        UpdateException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private final Context context;
    private final SharedPreferences prefs;

    public UpdateManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // -------------------------------------------------------------- settings

    public long getIntervalHours() {
        return prefs.getLong(KEY_INTERVAL, UpdateSchedule.DEFAULT_HOURS);
    }

    public void setIntervalHours(long hours) {
        prefs.edit().putLong(KEY_INTERVAL, hours).apply();
    }

    public long getLastCheck() {
        return prefs.getLong(KEY_LAST_CHECK, 0L);
    }

    public boolean isAutoCheckDue() {
        return UpdateSchedule.isDue(getLastCheck(), System.currentTimeMillis(), getIntervalHours());
    }

    public long installedVersionCode() {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return PackageInfoCompat.getLongVersionCode(info);
        } catch (PackageManager.NameNotFoundException exception) {
            return 0L;
        }
    }

    // ----------------------------------------------------------------- check

    public Check check() {
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
        HttpURLConnection connection = null;
        try {
            connection = open(UPDATE_URL);
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) return new Check(ERR_NO_RELEASE, null);
            if (status != HttpURLConnection.HTTP_OK) return new Check(ERR_NETWORK, null);
            String json;
            try (InputStream in = connection.getInputStream()) {
                json = readLimited(in, MAX_JSON_BYTES);
            }
            UpdateInfo info = UpdateInfo.parse(json);
            return new Check(info.isNewerThan(installedVersionCode()) ? AVAILABLE : UP_TO_DATE, info);
        } catch (IllegalArgumentException exception) {
            Log.w(TAG, "update.json rejected: " + exception.getMessage());
            return new Check(ERR_FORMAT, null);
        } catch (IOException | RuntimeException exception) {
            Log.w(TAG, "Update check failed: " + exception.getClass().getSimpleName());
            return new Check(ERR_NETWORK, null);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // -------------------------------------------------------------- download

    public File download(UpdateInfo info, Progress progress, AtomicBoolean cancelled) throws UpdateException {
        File dir = new File(context.getCacheDir(), DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new UpdateException(ERR_STORAGE, "cache directory");
        File target = new File(dir, APK_NAME);
        if (target.exists() && !target.delete()) throw new UpdateException(ERR_STORAGE, "old download");
        MessageDigest digest = UpdateInfo.newDigest();
        HttpURLConnection connection = null;
        long done = 0L;
        try {
            connection = open(info.apkUrl);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new UpdateException(ERR_DOWNLOAD, "HTTP " + connection.getResponseCode());
            }
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                long lastReport = 0L;
                while ((read = in.read(buffer)) != -1) {
                    if (cancelled.get()) throw new UpdateException(ERR_CANCELLED, "cancelled");
                    done += read;
                    if (done > info.size) throw new UpdateException(ERR_HASH, "larger than announced");
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                    if (progress != null && done - lastReport >= info.size / 100) {
                        lastReport = done;
                        progress.onProgress(done, info.size);
                    }
                }
            }
        } catch (IOException exception) {
            target.delete();
            throw new UpdateException(ERR_DOWNLOAD, exception.getClass().getSimpleName());
        } catch (UpdateException exception) {
            target.delete();
            throw exception;
        } finally {
            if (connection != null) connection.disconnect();
        }
        if (done != info.size || !UpdateInfo.hex(digest.digest()).equals(info.sha256)) {
            target.delete();
            throw new UpdateException(ERR_HASH, "checksum");
        }
        int verified = verify(target);
        if (verified != VERIFIED) {
            target.delete();
            throw new UpdateException(verified, "verification");
        }
        return target;
    }

    /** Package name, newer version and the same signing certificates as the installed Lumora. */
    @SuppressWarnings("deprecation")
    int verify(File apk) {
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (archive == null || !context.getPackageName().equals(archive.packageName)) return ERR_PACKAGE;
        if (PackageInfoCompat.getLongVersionCode(archive) <= installedVersionCode()) return ERR_NOT_NEWER;
        try {
            PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
            Set<String> expected = signers(installed);
            Set<String> actual = signers(archive);
            if (expected.isEmpty() || !expected.equals(actual)) return ERR_SIGNATURE;
        } catch (PackageManager.NameNotFoundException exception) {
            return ERR_PACKAGE;
        }
        return VERIFIED;
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signers(PackageInfo info) {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }
        Set<String> digests = new HashSet<>();
        if (signatures == null) return digests;
        for (Signature signature : signatures) {
            MessageDigest digest = UpdateInfo.newDigest();
            digests.add(UpdateInfo.hex(digest.digest(signature.toByteArray())));
        }
        return digests;
    }

    // --------------------------------------------------------------- install

    public boolean canInstall() {
        return context.getPackageManager().canRequestPackageInstalls();
    }

    public Intent installIntent(File apk) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".updates", apk);
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public String grantInstallCommand() {
        return "adb shell appops set " + context.getPackageName() + " REQUEST_INSTALL_PACKAGES allow";
    }

    // ------------------------------------------------------------------ http

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        // GitHub redirects release downloads to its asset host; both are HTTPS, which Java follows.
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Lumora-Updater");
        connection.setRequestProperty("Accept", "application/octet-stream, application/json");
        return connection;
    }

    private static String readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > limit) throw new IOException("update.json too large");
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
