package com.firestick.aura.launcher.manager;

import com.firestick.aura.launcher.model.IptvChannel;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FastGuideTest {
    private static final long MINUTE = 60_000L;

    @Test public void regionFollowsBrowsedCountryAndFallsBackToUs() {
        assertEquals("de", FastGuide.region(FastGuide.PLUTO, FastGuide.AUTO, "DE"));
        assertEquals("gb", FastGuide.region(FastGuide.PLUTO, FastGuide.AUTO, "UK"));
        assertEquals("us", FastGuide.region(FastGuide.PLUTO, FastGuide.AUTO, "EG"));
        assertEquals("us", FastGuide.region(FastGuide.PLUTO, FastGuide.AUTO, "ALL"));
        assertEquals("in", FastGuide.region(FastGuide.SAMSUNG, FastGuide.AUTO, "IN"));
        assertEquals("us", FastGuide.region(FastGuide.PLUTO, FastGuide.AUTO, "IN"));
        assertEquals("mx", FastGuide.region(FastGuide.PLUTO, "mx", "EG"));
        assertEquals("us", FastGuide.region(FastGuide.SAMSUNG, "mx", "DE"));
    }

    @Test public void rowsUseStableIdsJmpStreamsAndServiceUserAgent() {
        IptvChannel pluto = FastGuide.row(FastGuide.PLUTO, "gb", "5f12", "Movies", "https://logo.test/a.png", " ", "okhttp/4.9.0");
        assertEquals("https://jmp2.uk/plu-5f12.m3u8", pluto.url);
        assertEquals("UK", pluto.country);
        assertEquals("General", pluto.category);
        assertEquals("pluto:5f12", pluto.channelId);
        assertEquals("okhttp/4.9.0", pluto.streamHeaders.get(pluto.url).get("User-Agent"));
        assertEquals(pluto.id, FastGuide.row(FastGuide.PLUTO, "us", "5f12", "Other", "", "News", "").id);
        assertTrue(FastGuide.isFast(pluto));

        IptvChannel samsung = FastGuide.row(FastGuide.SAMSUNG, "us", "USBA1", "Late", "", "Adult Films", "bad\nagent");
        assertEquals("https://jmp2.uk/stvp-USBA1", samsung.url);
        assertEquals(Collections.singletonList("18+"), samsung.genres);
        assertTrue(samsung.isAdult());
        assertTrue(samsung.streamHeaders.isEmpty());
    }

    @Test public void feedKeyRecognisesOnlyJmpStreams() {
        assertEquals("plu:5f12", FastGuide.feedKey("https://jmp2.uk/plu-5f12.m3u8"));
        assertEquals("stvp:USBA1", FastGuide.feedKey("http://jmp2.uk/stvp-USBA1?region=us"));
        assertNull(FastGuide.feedKey("https://other.test/plu-5f12.m3u8"));
        assertNull(FastGuide.feedKey(null));
        assertTrue(FastGuide.isHls("https://jmp2.uk/stvp-USBA1"));
        assertTrue(FastGuide.isHls("https://other.test/live.M3U8?token=1"));
        assertFalse(FastGuide.isHls("https://other.test/live.mp4"));
    }

    @Test public void scheduleReportsNowNextProgressAndUnknownLastEnd() {
        long t0 = 1_794_825_900_000L;
        FastGuide.Schedule schedule = new FastGuide.Schedule(5, " About ", "art",
                new long[]{t0, t0 + 30 * MINUTE, t0 + 20 * MINUTE, t0 + 90 * MINUTE},
                new String[]{"First", "Second", "Out of order", "Last"});
        assertEquals(3, schedule.size());
        assertEquals("About", schedule.description);

        long now = t0 + 10 * MINUTE;
        assertEquals(0, schedule.indexAt(now));
        assertEquals(1, schedule.nextIndex(now));
        assertEquals(20, schedule.minutesLeft(now));
        assertEquals(33, schedule.progressPercent(now));

        now = t0 + 100 * MINUTE;
        assertEquals(2, schedule.indexAt(now));
        assertEquals("Last", schedule.title(schedule.indexAt(now)));
        assertEquals(-1, schedule.minutesLeft(now));
        assertEquals(-1, schedule.progressPercent(now));
        assertEquals(-1, schedule.nextIndex(now));

        assertEquals(-1, schedule.indexAt(t0 + 90 * MINUTE + FastGuide.LAST_PROGRAM_MS));
        assertEquals(-1, schedule.indexAt(t0 - MINUTE));
        assertEquals(0, schedule.nextIndex(t0 - MINUTE));
    }

    @Test public void attachFoldsMatchingIptvOrgRowsAndOrdersLineupsByCountry() {
        List<IptvChannel> iptvOrg = IptvRepository.parsePlaylist(
                "#EXTINF:-1 tvg-id=\"PlutoMovies.us\" tvg-country=\"US\" group-title=\"Classics\",Pluto TV Movies\n"
                        + "https://jmp2.uk/plu-aaa.m3u8\n"
                        + "#EXTINF:-1 tvg-id=\"PlutoMovies.us\" tvg-country=\"US\" group-title=\"Classics\",Pluto TV Movies\n"
                        + "https://backup.test/movies.m3u8\n"
                        + "#EXTINF:-1 tvg-id=\"Zeta.us\" tvg-country=\"US\" group-title=\"News\",Zeta\n"
                        + "https://zeta.test/live\n", "US");
        IptvChannel old = iptvOrg.get(0);
        List<IptvChannel> fast = Arrays.asList(
                FastGuide.row(FastGuide.PLUTO, "us", "aaa", "Pluto TV Movies Plus", "", "Movies", "okhttp/4.9.0"),
                FastGuide.row(FastGuide.PLUTO, "us", "bbb", "Pluto TV News", "", "News", "okhttp/4.9.0"));

        List<IptvChannel> local = FastGuide.attach(iptvOrg, fast, "US");
        assertEquals(3, local.size());
        IptvChannel merged = local.get(0);
        assertEquals("pluto:aaa", merged.channelId);
        assertEquals("Pluto TV Movies Plus", merged.name);
        assertEquals(Arrays.asList("https://jmp2.uk/plu-aaa.m3u8", "https://backup.test/movies.m3u8"), merged.streamUrls);
        assertEquals(Arrays.asList("Movies", "Classics"), merged.genres);
        assertTrue(merged.legacyIds.contains(old.id));
        assertEquals("okhttp/4.9.0", merged.streamHeaders.get(merged.url).get("User-Agent"));
        assertEquals("pluto:bbb", local.get(1).channelId);
        assertEquals("Zeta", local.get(2).name);

        List<IptvChannel> abroad = FastGuide.attach(iptvOrg, fast, "EG");
        assertEquals("Zeta", abroad.get(0).name);
        assertEquals("pluto:aaa", abroad.get(1).channelId);
        assertSame(iptvOrg, FastGuide.attach(iptvOrg, Collections.emptyList(), "US"));
    }
}
