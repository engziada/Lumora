package com.firestick.aura.launcher.manager;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlutoSessionTest {
    private static String boot(String region, String vod, long refreshInSec) {
        return "{\"sessionToken\":\"tok\",\"refreshInSec\":" + refreshInSec + ","
                + "\"stitcherParams\":\"appName=web&deviceType=web\","
                + "\"session\":{\"activeRegion\":\"" + region + "\"},"
                + "\"servers\":{\"stitcher\":\"https://cfd-v4-service-channel-stitcher-use1-1.prd.pluto.tv/\","
                + "\"vod\":\"" + vod + "\",\"channels\":\"https://service-channels.clusters.pluto.tv\"}}";
    }

    @Test public void bootNeedsARegionAndPlutoServers() {
        PlutoSession session = PlutoSession.parse(boot("US", "https://service-vod.clusters.pluto.tv", 28_800), "id", 1000L);
        assertNotNull(session);
        assertEquals("tok", session.token);
        assertEquals("Capped at four hours", 1000L + PlutoSession.MAX_AGE_MS, session.expiresAt);
        assertEquals("https://cfd-v4-service-channel-stitcher-use1-1.prd.pluto.tv/v2/stitch/hls/episode/abc/master.m3u8"
                + "?appName=web&deviceType=web&jwt=tok&masterJWTPassthrough=true&includeExtendedEvents=true",
                session.playUrl("abc"));
        assertEquals(1000L + 60_000L,
                PlutoSession.parse(boot("US", "https://service-vod.clusters.pluto.tv", 5), "id", 1000L).expiresAt);
        assertNull("Region not served", PlutoSession.parse(boot("", "https://service-vod.clusters.pluto.tv", 60), "id", 0L));
        assertNull("Token only goes to Pluto", PlutoSession.parse(boot("US", "https://vod.example.com", 60), "id", 0L));
        assertNull(PlutoSession.parse("not json", "id", 0L));
    }

    @Test public void onlyPlutoHostsCount() {
        assertTrue(PlutoSession.isPluto("https://service-vod.clusters.pluto.tv/v4/vod"));
        assertTrue(PlutoSession.isPluto("https://pluto.tv/"));
        assertFalse(PlutoSession.isPluto("http://service-vod.clusters.pluto.tv/"));
        assertFalse(PlutoSession.isPluto("https://pluto.tv.example.com/"));
        assertFalse(PlutoSession.isPluto("https://notpluto.tv/"));
        assertFalse(PlutoSession.isPluto("https://images.pluto.tv@example.com/"));
        assertFalse(PlutoSession.isPluto(null));
    }
}
