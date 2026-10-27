package com.firestick.aura.launcher.manager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Minimal blocking HTTP and file helpers. Call from background threads only. */
public final class Http {
    private static final String USER_AGENT = "Lumora/5.0 (Android TV launcher; github.com/engziada/Lumora)";

    private Http() {
    }

    /** @return the response body for HTTP 200, otherwise {@code null}. */
    public static byte[] get(String url, int connectTimeoutMs, int readTimeoutMs) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream in = connection.getInputStream()) {
                return readAll(in);
            }
        } catch (Exception exception) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static byte[] readFile(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return readAll(in);
        } catch (Exception exception) {
            return null;
        }
    }

    /** Writes through a temporary file so a crash never leaves a truncated cache entry. */
    public static boolean writeFile(File file, byte[] data) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
        File temp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(data);
        } catch (Exception exception) {
            temp.delete();
            return false;
        }
        return temp.renameTo(file) || (file.delete() && temp.renameTo(file));
    }

    public static String sha1(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception exception) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return out.toByteArray();
    }
}
