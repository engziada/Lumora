package com.firestick.aura.launcher.manager;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;

public class ProgramRatingsTest {
    private static final long NOW = at("2026-11-16T22:00:00Z");
    private static final String PLUTO_ID = "5e1f7e089f23700009d66303";

    private static final String XMLTV = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv>\n"
            + "<channel id=\"USBD1\"><display-name>One</display-name></channel>\n"
            + "<programme channel=\"USBD1\" start=\"20261116200000 +0000\" stop=\"20261116210000 +0000\">"
            + "<title>Ended</title><rating system=\"VCHIP\"><value>TV-MA</value></rating></programme>\n"
            // 21:30 to 22:30 UTC, written in a +0100 zone.
            + "<programme channel=\"USBD1\" start=\"20261116223000 +0100\" stop=\"20261116233000 +0100\">"
            + "<title>On air</title><rating system=\"VCHIP\">\n  <value> tv-14 </value>\n</rating></programme>\n"
            + "<programme channel=\"USBD1\" start=\"20261116223000 +0000\" stop=\"20261116233000 +0000\">"
            + "<title>Unrated</title><rating><value>NA</value></rating></programme>\n"
            + "<programme channel=\"USBD1\" start=\"20261116233000\" stop=\"20261117003000\">"
            + "<title>Late</title><rating><value>PG-13</value></rating></programme>\n"
            + "<programme channel=\"USBD2\" start=\"20261116210000 +0000\" stop=\"20261116230000 +0000\">"
            + "<title>No rating</title></programme>\n"
            + "</tv>\n";

    private static long at(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    @Test public void xmltvKeepsRatedProgrammesThatHaveNotEnded() throws IOException {
        for (byte[] guide : new byte[][]{XMLTV.getBytes(StandardCharsets.UTF_8), gzip(XMLTV)}) {
            Map<String, ProgramRatings.Slots> rows = ProgramRatings.parseXmltv(guide, "samsung:", NOW);
            assertNotNull(rows);
            assertEquals("Rows without a rating are left out", 1, rows.size());
            ProgramRatings.Slots slots = rows.get("samsung:USBD1");
            assertEquals("The ended and the NA programmes are dropped", 2, slots.starts.length);
            assertEquals("TV-14", slots.at(NOW));
            assertEquals("Nothing rated on air", "", slots.at(at("2026-11-16T22:45:00Z")));
            assertEquals("No zone is UTC", "PG-13", slots.at(at("2026-11-16T23:45:00Z")));
            assertEquals(Long.MAX_VALUE, slots.until);
        }
        assertNull(ProgramRatings.parseXmltv("<tv></tv>".getBytes(StandardCharsets.UTF_8), "samsung:", NOW));
        assertNull(ProgramRatings.parseXmltv("<html>Not found</html>".getBytes(StandardCharsets.UTF_8), "samsung:", NOW));
        assertNull("Broken gzip", ProgramRatings.parseXmltv(new byte[]{0x1f, (byte) 0x8b, 1, 2, 3}, "samsung:", NOW));
        assertNull(ProgramRatings.parseXmltv(null, "samsung:", NOW));
    }

    @Test public void xmltvElementsSplitAcrossReadsAreAllFound() throws IOException {
        StringBuilder guide = new StringBuilder("<tv>");
        int rows = 3000;
        for (int i = 0; i < rows; i++) {
            guide.append("<programme channel=\"C").append(i).append("\" start=\"20261116210000 +0000\" ")
                    .append("stop=\"20261116230000 +0000\"><title>Programme ").append(i)
                    .append("</title><desc>A longer description so elements cross the read size.</desc>")
                    .append("<rating system=\"VCHIP\"><value>TV-G</value></rating></programme>\n");
        }
        guide.append("</tv>");
        Map<String, ProgramRatings.Slots> parsed = ProgramRatings.parseXmltv(gzip(guide.toString()), "samsung:", NOW);
        assertNotNull(parsed);
        assertEquals(rows, parsed.size());
        assertEquals("TV-G", parsed.get("samsung:C" + (rows - 1)).at(NOW));
    }

    @Test public void plutoTimelinesGiveTheRatingOnAirAndWhenToReadAgain() {
        String json = "{\"data\":[{\"channelId\":\"" + PLUTO_ID + "\",\"timelines\":["
                + "{\"start\":\"2026-11-16T21:00:00.000Z\",\"stop\":\"2026-11-16T21:30:00.000Z\",\"episode\":{\"rating\":\"TV-MA\"}},"
                + "{\"start\":\"2026-11-16T21:30:00.000Z\",\"stop\":\"2026-11-16T22:30:00.000Z\",\"episode\":{\"rating\":\"TV-14\"}},"
                + "{\"start\":\"2026-11-16T22:30:00.000Z\",\"stop\":\"2026-11-16T23:00:00.000Z\",\"episode\":{\"rating\":\"Not Rated\"}},"
                + "{\"start\":\"soon\",\"stop\":\"2026-11-16T23:30:00.000Z\",\"episode\":{\"rating\":\"R\"}},"
                + "{\"start\":\"2026-11-16T23:00:00.000Z\",\"stop\":\"2026-11-17T01:00:00.000Z\",\"episode\":{}}]},"
                + "{\"channelId\":\"other\",\"timelines\":[{\"start\":\"2026-11-16T21:30:00.000Z\","
                + "\"stop\":\"2026-11-17T05:00:00.000Z\",\"episode\":{\"rating\":\"G\"}}]}]}";
        ProgramRatings.Slots slots = ProgramRatings.parseTimelines(json, PLUTO_ID, NOW);
        assertNotNull(slots);
        assertEquals("TV-14", slots.at(NOW));
        assertEquals("NR", slots.at(at("2026-11-16T22:45:00Z")));
        assertEquals("", slots.at(at("2026-11-16T23:30:00Z")));
        assertEquals("Read again when this channel's guide runs out", at("2026-11-17T01:00:00Z"), slots.until);

        ProgramRatings.Slots empty = ProgramRatings.parseTimelines("{\"data\":[]}", PLUTO_ID, NOW);
        assertEquals("", empty.at(NOW));
        assertEquals(NOW + ProgramRatings.RETRY_MS, empty.until);
        assertNull(ProgramRatings.parseTimelines("{}", PLUTO_ID, NOW));
        assertNull(ProgramRatings.parseTimelines("<html>", PLUTO_ID, NOW));
    }
}
