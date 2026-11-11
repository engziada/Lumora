package com.firestick.aura.launcher.manager;

import org.json.JSONException;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LauncherBackupTest {
    @Test public void supportedPreferencesRoundTripWithTheirTypes() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("pref_sound_enabled", true);
        values.put("pref_panel_content", 2);
        values.put("pref_home_revision", 23L);
        values.put("pref_favorites", new HashSet<>(Arrays.asList("com.example.tv")));
        values.put("pref_home_sections", "favorites,recent,iptv");
        values.put("unrelated_token", "private");
        values.put("pref_recent_channels", "[{\"urls\":[\"https://provider.test/live?token=private\"]}]");
        Map<String, Object> restored = LauncherBackup.decode(LauncherBackup.encode(values));
        assertEquals(true, restored.get("pref_sound_enabled"));
        assertEquals(2, restored.get("pref_panel_content"));
        assertEquals(23L, restored.get("pref_home_revision"));
        assertEquals(values.get("pref_favorites"), restored.get("pref_favorites"));
        assertFalse(restored.containsKey("unrelated_token"));
        assertFalse(restored.containsKey("pref_recent_channels"));
    }

    @Test(expected = JSONException.class) public void invalidTypeIsRejectedBeforeRestore() throws Exception {
        LauncherBackup.decode("{\"format\":\"lumora-launcher\",\"version\":1,\"values\":{\"pref_sound_enabled\":\"yes\"}}");
    }
    @Test(expected = JSONException.class) public void partialLockCannotBeRestored() throws Exception {
        LauncherBackup.decode("{\"format\":\"lumora-launcher\",\"version\":1,\"values\":{\"pref_lock_hash\":\"bad\"}}");
    }
    @Test(expected = JSONException.class) public void unsupportedHomeRowsAreRejected() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("pref_home_sections", "favorites,unknown");
        LauncherBackup.decode(LauncherBackup.encode(values));
    }
    @Test(expected = JSONException.class) public void unsupportedVersionIsRejected() throws Exception {
        LauncherBackup.decode("{\"format\":\"lumora-launcher\",\"version\":99,\"values\":{}}");
    }
}
