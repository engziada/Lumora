package com.firestick.aura.launcher.manager;

import com.firestick.aura.launcher.model.IptvChannel;
import org.json.JSONObject;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class IptvEnhancementTest {
    @Test public void oneCardKeepsAllFeedsAndLegacyFavoriteIdentity() {
        String playlist = "#EXTM3U\n#EXTINF:-1 tvg-id=\"Test.us\" group-title=\"Music\",Test\nhttps://a.test/live.m3u8\n"
                + "#EXTINF:-1 tvg-id=\"Test.us\" group-title=\"Movies\",Test HD\nhttps://b.test/live.m3u8\n";
        List<IptvChannel> channels = IptvRepository.parsePlaylist(playlist, "US");
        assertEquals(1, channels.size());
        IptvChannel channel = channels.get(0);
        assertEquals(2, channel.streamUrls.size());
        assertTrue(channel.genres.containsAll(Arrays.asList("Music", "Movies")));
        assertEquals("Test.us", channel.channelId);
        assertEquals(Long.parseLong(Http.sha1("Test.us|https://b.test/live.m3u8").substring(0, 15), 16),
                channel.legacyIds.get(0).longValue());
    }

    @Test public void queueAndHistoryRoundTripPreservesFallbackAndAdultTag() throws Exception {
        IptvChannel original = IptvRepository.parsePlaylist(
                "#EXTINF:-1 tvg-id=\"Test.us\" group-title=\"XXX\",Test\nhttps://a.test/live\n", "US").get(0);
        IptvChannel restored = IptvChannel.fromJson(new JSONObject(original.toJson().toString()));
        assertEquals(original.id, restored.id);
        assertEquals(original.streamUrls, restored.streamUrls);
        assertTrue(restored.isAdult());
        for (String category : Arrays.asList("adult", "Adults", "XXX", "18+")) {
            assertTrue(IptvRepository.parsePlaylist("#EXTINF:-1 group-title=\"" + category
                    + "\",Adult fixture\nhttps://a.test/live\n", "US").get(0).isAdult());
        }
    }

    @Test public void feedSuffixesGroupUnderTheGuideChannelId() {
        List<IptvChannel> channels = IptvRepository.parsePlaylist(
                "#EXTINF:-1 tvg-id=\"Test.us@HD\",Test HD\nhttps://a.test/live\n"
                + "#EXTINF:-1 tvg-id=\"Test.us@SD\",Test SD\nhttps://b.test/live\n", "ALL");
        assertEquals(1, channels.size());
        assertEquals("Test.us", channels.get(0).channelId);
        assertEquals("US", channels.get(0).country);
        assertEquals(2, channels.get(0).streamUrls.size());
        assertTrue(channels.get(0).legacyIds.contains(Long.parseLong(Http.sha1("Test.us@SD").substring(0, 15), 16)));
    }

    @Test public void worldwideAndCountryEntriesShareCanonicalIdentity() {
        String playlist = "#EXTINF:-1 tvg-id=\"News.eg\",News\nhttps://a.test/live\n";
        IptvChannel worldwide = IptvRepository.parsePlaylist(playlist, "ALL").get(0);
        IptvChannel local = IptvRepository.parsePlaylist(playlist, "EG").get(0);
        assertEquals(local.id, worldwide.id);
        assertEquals("EG", worldwide.country);
    }

    @Test public void xmltvTimestampHonorsOffset() {
        assertEquals(EpgRepository.time("20261110100000 +0000"), EpgRepository.time("20261110120000 +0200"));
        assertEquals(0, EpgRepository.time("bad"));
    }
}
