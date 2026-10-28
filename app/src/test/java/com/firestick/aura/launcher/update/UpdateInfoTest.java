package com.firestick.aura.launcher.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class UpdateInfoTest {
    private static final String SHA = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String APK = "https://github.com/engziada/Lumora/releases/download/build-1042/lumora-5.4.0-1042.apk";

    private static String json(String apk, String sha, long size, long code) {
        return "{\"versionCode\":" + code + ",\"versionName\":\"5.4.0\",\"apk\":\"" + apk + "\",\"sha256\":\"" + sha
                + "\",\"size\":" + size + ",\"notes\":\"feat: x\"}";
    }

    @Test
    public void parsesValidDocument() {
        UpdateInfo info = UpdateInfo.parse(json(APK, SHA.toUpperCase(), 1234, 1042));
        assertEquals(1042, info.versionCode);
        assertEquals("5.4.0", info.versionName);
        assertEquals(APK, info.apkUrl);
        assertEquals(SHA, info.sha256);
        assertEquals(1234, info.size);
        assertEquals("feat: x", info.notes);
        assertTrue(info.isNewerThan(1041));
        assertFalse(info.isNewerThan(1042));
    }

    @Test
    public void rejectsForeignOrUnsafeUrls() {
        assertThrows(IllegalArgumentException.class, () -> UpdateInfo.parse(json("https://evil.example/x.apk", SHA, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> UpdateInfo.parse(
                json("http://github.com/engziada/Lumora/releases/download/b/x.apk", SHA, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> UpdateInfo.parse(
                json("https://github.com/engziada/Lumora/releases/download/../../other/x.apk", SHA, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> UpdateInfo.parse(
                json("https://github.com/engziada/Lumora/releases/download/b/x.zip", SHA, 1, 1)));
    }

    @Test
    public void rejectsBadFields() {
        assertThrows(IllegalArgumentException.class, () -> UpdateInfo.parse(json(APK, "abc", 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> UpdateInfo.parse(json(APK, SHA, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> UpdateInfo.parse(json(APK, SHA, UpdateInfo.MAX_APK_BYTES + 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> UpdateInfo.parse(json(APK, SHA, 1, 0)));
        assertThrows(IllegalArgumentException.class, () -> UpdateInfo.parse("not json"));
        assertThrows(IllegalArgumentException.class, () -> UpdateInfo.parse("{}"));
    }

    @Test
    public void sha256OfKnownInput() throws Exception {
        String hash = UpdateInfo.sha256Hex(new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(SHA, hash);
    }
}
