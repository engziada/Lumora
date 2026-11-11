package com.firestick.aura.launcher.manager;

import com.firestick.aura.launcher.model.IptvChannel;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class IptvCatalogTest {
    private List<IptvChannel> playlist(String id, String name, String country, String url) {
        return IptvRepository.parsePlaylist("#EXTINF:-1 tvg-id=\"" + id + "\" tvg-country=\"" + country
                + "\" group-title=\"Music\"," + name + "\n" + url, "ALL");
    }
    @Test public void curatedCountryGroupsDoNotBecomeGenresAndWebPagesAreExcluded() {
        List<IptvChannel> rows = IptvRepository.parseCurated("\uFEFF#EXTM3U\n"
                + "#EXTINF:-1 tvg-id=\"One.eg\" tvg-country=\"EG\" group-title=\"Egypt\",One\nhttps://a.test/live\n"
                + "#EXTINF:-1 group-title=\"Egypt\",Two\nhttps://www.youtube.com/@two/live\n");
        assertEquals(1, rows.size());
        assertEquals("EG", rows.get(0).country);
        assertEquals(Arrays.asList("General"), rows.get(0).genres);
        assertEquals(0, IptvRepository.filterCountry(rows, "US").size());
    }
    @Test public void multipleSourcesKeepStableIdAndDeduplicateUrls() {
        List<IptvChannel> first = playlist("One.eg", "One", "EG", "https://a.test/live");
        List<IptvChannel> result = IptvRepository.merge(first, playlist("One.eg", "One HD", "EG", "https://b.test/live"));
        result = IptvRepository.merge(result, first);
        assertEquals(1, result.size());
        assertEquals(first.get(0).id, result.get(0).id);
        assertEquals(2, result.get(0).streamUrls.size());
    }
    @Test public void noIdNameMatchKeepsLegacyFavoriteButDistinctIdsStaySeparate() {
        List<IptvChannel> primary = playlist("One.eg", "One", "EG", "https://a.test/live");
        List<IptvChannel> extra = playlist("", "One", "EG", "https://b.test/live");
        IptvChannel merged = IptvRepository.merge(primary, extra).get(0);
        assertTrue(merged.legacyIds.contains(extra.get(0).id));
        assertEquals(2, IptvRepository.merge(primary, playlist("Other.eg", "One", "EG", "https://c.test/live")).size());
        assertEquals(2, IptvRepository.merge(primary, playlist("", "One", "US", "https://d.test/live")).size());
    }
    @Test public void ambiguousNameDoesNotAttachToWrongRegionalFeed() {
        List<IptvChannel> primary = IptvRepository.merge(playlist("One.eg", "News", "EG", "https://a.test/live"),
                playlist("Two.eg", "News", "EG", "https://b.test/live"));
        assertEquals(3, IptvRepository.merge(primary, playlist("", "News", "EG", "https://c.test/live")).size());
    }
    @Test public void apiFeedsPreserveHeadersAcrossQueueAndFavoriteCopies() throws Exception {
        List<IptvChannel> channels = playlist("One.eg", "One", "EG", "https://a.test/live");
        String json = "[{\"channel\":\"One.eg\",\"url\":\"https://b.test/live\",\"referrer\":\"https://site.test/\",\"user_agent\":\"Fixture\"},"
                + "{\"channel\":\"Other.eg\",\"url\":\"https://other.test/live\"}]";
        IptvChannel channel = IptvRepository.addApiFeeds(channels, json).get(0).withFavorite(true);
        channel = IptvChannel.fromJson(new JSONObject(channel.toJson().toString()));
        assertEquals(2, channel.streamUrls.size());
        assertEquals("Fixture", channel.streamHeaders.get("https://b.test/live").get("User-Agent"));
        assertEquals("https://site.test/", channel.streamHeaders.get("https://b.test/live").get("Referer"));
        assertEquals(channels, IptvRepository.addApiFeeds(channels, "broken"));
    }
    @Test public void m3uHeadersAndCommasInTitlesAreParsed() {
        IptvChannel row = IptvRepository.parsePlaylist("#EXTINF:-1 tvg-id=\"News.eg\" group-title=\"News\",News, live\n"
                + "#EXTVLCOPT:http-referrer=https://site.test/\n#EXTVLCOPT:http-user-agent=Fixture\nhttps://a.test/live\n", "ALL").get(0);
        assertEquals("News, live", row.name);
        assertEquals("Fixture", row.streamHeaders.get(row.url).get("User-Agent"));
        IptvChannel merged = IptvRepository.merge(playlist("News.eg", "News", "EG", row.url), Arrays.asList(row)).get(0);
        assertEquals("Fixture", merged.streamHeaders.get(row.url).get("User-Agent"));
    }
    @Test public void unknownCountryOnlyAppearsInAll() {
        List<IptvChannel> rows = playlist("", "Custom", "", "https://a.test/live");
        assertEquals(1, IptvRepository.filterCountry(rows, "ALL").size());
        assertEquals(0, IptvRepository.filterCountry(rows, "EG").size());
        assertEquals("UK", playlist("Example.uk", "Example", "GB", "https://a.test/uk").get(0).country);
    }
    @Test public void sourceUrlsValidateSchemesAndHeaderInjection() {
        assertTrue(IptvSources.validUrl("https://provider.test/get.php?username=fixture&password=fixture"));
        for (String url : Arrays.asList("file:///tmp/a", "ftp://test/a", "https://u:p@test/a", "https://test/a\r\nx:a", "garbage"))
            assertFalse(url, IptvSources.validUrl(url));
    }
    @Test public void playbackRanksFailuresBelowUnknownAndStableStreams() {
        long now = 1_000_000_000L;
        JSONObject data = new JSONObject();
        StreamHealth.observe(data, "https://fast", false, 500, 60000, 0, now);
        StreamHealth.observe(data, "https://slow", false, 10000, 30000, 20000, now);
        StreamHealth.observe(data, "https://failed", true, 0, 0, 0, now);
        assertEquals(Arrays.asList("https://fast", "https://unknown", "https://slow", "https://failed"),
                StreamHealth.rank(Arrays.asList("https://failed", "https://slow", "https://unknown", "https://fast"), data, now));
        StreamHealth.observe(data, "https://failed", false, 250, 60000, 0, now + 1);
        assertEquals("https://failed", StreamHealth.rank(Arrays.asList("https://unknown", "https://failed"), data, now + 1).get(0));
    }
    @Test public void staleHealthExpiresAndStorageIsBounded() {
        JSONObject data = new JSONObject();
        for (int i = 0; i < 300; i++) StreamHealth.observe(data, "https://" + i, true, 0, 0, 0, i);
        assertEquals(256, data.length());
        List<String> urls = Arrays.asList("https://299", "https://unknown");
        assertEquals(urls, StreamHealth.rank(urls, data, 8L * 86400000));
    }
}
