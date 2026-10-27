package com.firestick.aura.launcher.manager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/** Salted SHA-256 of the arrow sequence (U/D/L/R). Only the hash and salt are stored. */
public final class AppLock {
    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 8;

    private AppLock() {
    }

    public static boolean hasCode(PreferenceManager prefs) {
        return prefs.getLockHash() != null && prefs.getLockSalt() != null;
    }

    public static void setCode(PreferenceManager prefs, String sequence) {
        String salt = newSalt();
        prefs.setLockCode(hash(salt, sequence), salt, sequence.length());
    }

    public static boolean verify(PreferenceManager prefs, String sequence) {
        String stored = prefs.getLockHash();
        String salt = prefs.getLockSalt();
        if (stored == null || salt == null) return false;
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8),
                hash(salt, sequence).getBytes(StandardCharsets.UTF_8));
    }

    static String hash(String salt, String sequence) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((salt + ":" + sequence).getBytes(StandardCharsets.UTF_8));
            return hex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return hex(salt);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }
}
