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

    @Test public void uncategorisedChannelsReadAsOther() {
        IptvChannel channel = IptvRepository.parsePlaylist("#EXTINF:-1 group-title=\"Undefined\",Fixture\n"
                + "https://a.test/live\n", "US").get(0);
        assertEquals(Collections.singletonList("Other"), channel.genres);
        assertEquals("Other", channel.category);
        // A second feed of the same channel with a real genre replaces the placeholder.
        IptvChannel merged = IptvRepository.parsePlaylist(
                "#EXTINF:-1 tvg-id=\"Fixture.us\" group-title=\"Undefined\",Fixture\nhttps://a.test/one\n"
                        + "#EXTINF:-1 tvg-id=\"Fixture.us\" group-title=\"News\",Fixture\nhttps://a.test/two\n", "US").get(0);
        assertEquals(Collections.singletonList("News"), merged.genres);
        assertEquals("News", merged.category);
    }

    @Test public void adultWordsInsideGroupNamesLockTheChannel() {
        for (String group : Arrays.asList("Adult Movies", "FOR ADULTS (18+)", "XXX: Late Night", "+18", "Adultos", "News;Adult Movies")) {
            List<String> genres = IptvRepository.parsePlaylist("#EXTINF:-1 group-title=\"" + group
                    + "\",Fixture\nhttps://a.test/live\n", "US").get(0).genres;
            assertTrue(group, genres.contains("18+"));
        }
        for (String group : Arrays.asList("Movies", "Kids", "XXXL Shop", "Adult Swim", "Pop Adult", "Adult Contemporary")) {
            assertFalse(group, IptvRepository.parsePlaylist("#EXTINF:-1 group-title=\"" + group
                    + "\",Fixture\nhttps://a.test/live\n", "US").get(0).isAdult());
        }
        for (String group : Arrays.asList("Érotique", "Erotik", "Sinnliche Fantasien", "Kids;Adult Swim;Adults")) {
            assertTrue(group, IptvChannel.isAdultGroup(group));
        }
        // Watch history saved before this matching existed keeps the provider's group name.
        IptvChannel saved = new IptvChannel(1, "Fixture", "", "Adult Movies", Collections.singletonList("Adult Movies"),
                "US", "", "https://a.test/live", false, "", "", "", "");
        assertTrue(saved.isAdult());
    }

    @Test public void adultNamesAndNsfwIdsLockTheChannel() {
        for (String name : Arrays.asList("Pluto TV Erotica", "Pluto TV Spicy", "Heiße Nächte", "Liebe Sünde",
                "FashionTV Midnight Secrets", "Playboy TV")) {
            assertTrue(name, IptvRepository.parsePlaylist("#EXTINF:-1 group-title=\"Movies\"," + name
                    + "\nhttps://a.test/live\n", "US").get(0).isAdult());
        }
        for (String name : Arrays.asList("Adult Swim", "Spicy Kitchen", "Midnight News", "Hot Wheels TV")) {
            assertFalse(name, IptvChannel.isAdultName(name));
        }
        Set<String> nsfw = IptvRepository.parseNsfwIds("[{\"channel\":\"Blocked.us\",\"reason\":\"nsfw\"},"
                + "{\"channel\":\"Sports.us\",\"reason\":\"dmca\"}]");
        assertEquals(Collections.singleton("Blocked.us"), nsfw);
        List<IptvChannel> marked = IptvRepository.markNsfw(IptvRepository.parsePlaylist(
                "#EXTINF:-1 tvg-id=\"Blocked.us\" group-title=\"Undefined\",Blocked\nhttps://a.test/one\n"
                        + "#EXTINF:-1 tvg-id=\"Sports.us\" group-title=\"Sports\",Sports\nhttps://a.test/two\n", "US"), nsfw);
        assertEquals(Collections.singletonList("18+"), marked.get(0).genres);
        assertFalse(marked.get(1).isAdult());
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
}
