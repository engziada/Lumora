package com.firestick.aura.launcher.manager;

import org.junit.Test;
import java.net.*;
import java.io.*;
import static org.junit.Assert.*;

public class DownloadSpeedProbeTest {
    private URL endpoint(InputStream stream, int status) throws Exception {
        return new URL(null, "https://test.invalid/download", new URLStreamHandler() {
            protected URLConnection openConnection(URL url) {
                return new HttpURLConnection(url) {
                    public void disconnect() { }
                    public boolean usingProxy() { return false; }
                    public void connect() { }
                    public int getResponseCode() { return status; }
                    public InputStream getInputStream() { return stream; }
                };
            }
        });
    }
    @Test public void convertsBytesAndNanosecondsToDecimalMegabits() {
        assertEquals(8.0, DownloadSpeedProbe.megabitsPerSecond(1_000_000, 1_000_000_000L), .0001);
        assertEquals(0, DownloadSpeedProbe.megabitsPerSecond(100, 0), 0);
    }
    @Test public void capsDownloadsEvenIfServerSendsMore() throws Exception {
        final long[] read = {0};
        InputStream stream = new InputStream() {
            public int read() { read[0]++; return 1; }
            public int read(byte[] bytes, int offset, int count) { read[0] += count; return count; }
        };
        assertTrue(new DownloadSpeedProbe().measure(endpoint(stream, 200)) > 0);
        assertEquals(DownloadSpeedProbe.MAX_BYTES, read[0]);
    }
    @Test public void canceledProbeDoesNotConsumeData() throws Exception {
        DownloadSpeedProbe probe = new DownloadSpeedProbe(); probe.cancel();
        try { probe.measure(endpoint(new ByteArrayInputStream(new byte[100]), 200)); fail(); }
        catch (IOException expected) { }
    }
    @Test public void rejectsHttpErrorsAndEmptyBody() throws Exception {
        for (int status : new int[]{200, 503}) {
            try { new DownloadSpeedProbe().measure(endpoint(new ByteArrayInputStream(new byte[0]), status)); fail(); }
            catch (IOException expected) { }
        }
    }
}
