package com.firestick.aura.launcher.manager;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

public class PreviewFeedParserTest {
    private byte[] feed(String entries) {
        return ("<feed xmlns='http://www.w3.org/2005/Atom' xmlns:yt='http://www.youtube.com/xml/schemas/2015'>" + entries + "</feed>").getBytes(StandardCharsets.UTF_8);
    }
    private String entry(String id, String title) {
        return "<entry><yt:videoId>" + id + "</yt:videoId><title>" + title + "</title></entry>";
    }
    @Test public void prefersTrailersAndDeduplicatesInFeedOrder() throws Exception {
        assertEquals(Arrays.asList("abcdefghijk", "12345678901"), PreviewFeedParser.parse(feed(entry("12345678901", "Interview") + entry("abcdefghijk", "Official Trailer") + entry("abcdefghijk", "Teaser")), true));
    }
    @Test public void returnsRecentUploadsWhenNoTrailersExist() throws Exception {
        assertEquals(Arrays.asList("12345678901"), PreviewFeedParser.parse(feed(entry("12345678901", "Latest upload")), true));
    }
    @Test public void youtubeKeepsOtherVideosAndRejectsInvalidIds() throws Exception {
        assertEquals(Arrays.asList("12345678901", "abcdefghijk"), PreviewFeedParser.parse(feed(entry("12345678901", "Interview") + entry("abcdefghijk", "Teaser") + entry("invalid", "Invalid")), false));
    }
    @Test public void emptyFeedStaysEmpty() throws Exception {
        assertTrue(PreviewFeedParser.parse(feed(""), false).isEmpty());
    }
    @Test(expected = Exception.class) public void rejectsMalformedFeed() throws Exception {
        PreviewFeedParser.parse("<feed>".getBytes(StandardCharsets.UTF_8), false);
    }
}
