package com.firestick.aura.launcher.update;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Contents of {@code update.json}, published by CI with every build of {@code main}. Only APKs from
 * this repository's GitHub releases are accepted, and the SHA-256 must match after download.
 */
public final class UpdateInfo {
    public static final String APK_PREFIX = "https://github.com/engziada/Lumora/releases/download/";
    public static final long MAX_APK_BYTES = 60L * 1024L * 1024L;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern APK_PATH = Pattern.compile("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\\.apk");

    public final long versionCode;
    public final String versionName;
    public final String apkUrl;
    public final String sha256;
    public final long size;
    public final String notes;

    private UpdateInfo(long versionCode, String versionName, String apkUrl, String sha256, long size, String notes) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.apkUrl = apkUrl;
        this.sha256 = sha256;
        this.size = size;
        this.notes = notes;
    }

    /** @throws IllegalArgumentException when the document is malformed or points outside the repository */
    public static UpdateInfo parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            long code = root.getLong("versionCode");
            String name = root.optString("versionName", "").trim();
            String apk = root.getString("apk").trim();
            String sha = root.getString("sha256").trim().toLowerCase(Locale.ROOT);
            long size = root.getLong("size");
            String notes = root.optString("notes", "").trim();
            if (code <= 0) throw new IllegalArgumentException("versionCode");
            if (!apk.startsWith(APK_PREFIX) || !APK_PATH.matcher(apk.substring(APK_PREFIX.length())).matches()
                    || apk.contains("..")) {
                throw new IllegalArgumentException("apk");
            }
            if (!SHA256.matcher(sha).matches()) throw new IllegalArgumentException("sha256");
            if (size <= 0 || size > MAX_APK_BYTES) throw new IllegalArgumentException("size");
            if (notes.length() > 300) notes = notes.substring(0, 300);
            return new UpdateInfo(code, name.isEmpty() ? "?" : name, apk, sha, size, notes);
        } catch (JSONException exception) {
            throw new IllegalArgumentException("json", exception);
        }
    }

    public boolean isNewerThan(long installedVersionCode) {
        return versionCode > installedVersionCode;
    }

    public static String sha256Hex(InputStream in) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        return hex(digest.digest());
    }

    static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return out.toString();
    }
}
