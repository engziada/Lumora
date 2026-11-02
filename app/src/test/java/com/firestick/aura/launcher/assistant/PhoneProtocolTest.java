package com.firestick.aura.launcher.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;

public class PhoneProtocolTest {
    private static PhoneProtocol.Request read(String raw) throws IOException {
        return PhoneProtocol.read(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private static int status(String raw) {
        try {
            read(raw);
            return 200;
        } catch (PhoneProtocol.BadRequest bad) {
            return bad.status;
        } catch (IOException exception) {
            return -1;
        }
    }

    @Test
    public void parsesGetWithoutQuery() throws IOException {
        PhoneProtocol.Request request = read("GET /?x=1 HTTP/1.1\r\nHost: 192.168.1.5:8765\r\n\r\n");
        assertEquals("GET", request.method);
        assertEquals("/", request.path);
        assertEquals("192.168.1.5:8765", request.header("HOST"));
        assertEquals("", request.body);
    }

    @Test
    public void parsesPostBodyAsUtf8() throws IOException {
        String body = "c=123456&t=" + "%D9%85%D8%B1%D8%AD%D8%A8%D8%A7+there";
        PhoneProtocol.Request request = read("POST /send HTTP/1.1\r\nContent-Length: " + body.length()
                + "\r\nHost: a\r\n\r\n" + body);
        Map<String, String> form = PhoneProtocol.parseForm(request.body);
        assertEquals("123456", form.get("c"));
        assertEquals("\u0645\u0631\u062D\u0628\u0627 there", form.get("t"));
    }

    @Test
    public void rejectsBadRequests() {
        assertEquals(400, status("NONSENSE\r\n\r\n"));
        assertEquals(400, status("GET / HTTP/1.1\r\nHost: a"));
        assertEquals(400, status("POST /send HTTP/1.1\r\nContent-Length: abc\r\n\r\n"));
        assertEquals(400, status("POST /send HTTP/1.1\r\nContent-Length: 10\r\n\r\nshort"));
        assertEquals(411, status("POST /send HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"));
        assertEquals(413, status("POST /send HTTP/1.1\r\nContent-Length: " + (PhoneProtocol.MAX_BODY_BYTES + 1) + "\r\n\r\n"));
        StringBuilder big = new StringBuilder("GET / HTTP/1.1\r\nX: ");
        for (int i = 0; i < PhoneProtocol.MAX_HEADER_BYTES; i++) big.append('a');
        assertEquals(431, status(big + "\r\n\r\n"));
    }

    @Test
    public void rejectsBrokenPercentEncoding() {
        assertThrows(PhoneProtocol.BadRequest.class, () -> PhoneProtocol.parseForm("t=%zz"));
    }

    @Test
    public void hostMustBeTheTvAddress() {
        assertTrue(PhoneProtocol.hostAllowed("192.168.1.5:8765", "192.168.1.5", 8765));
        assertFalse(PhoneProtocol.hostAllowed("evil.example:8765", "192.168.1.5", 8765));
        assertFalse(PhoneProtocol.hostAllowed("192.168.1.5", "192.168.1.5", 8765));
        assertFalse(PhoneProtocol.hostAllowed("", "192.168.1.5", 8765));
        assertFalse(PhoneProtocol.hostAllowed(null, "192.168.1.5", 8765));
    }

    @Test
    public void codesAreSixDigitsAndCompared() {
        SecureRandom random = new SecureRandom();
        String code = PhoneProtocol.newCode(random);
        assertTrue(code.matches("\\d{6}"));
        assertTrue(PhoneProtocol.codeMatches(code, " " + code + " "));
        assertFalse(PhoneProtocol.codeMatches(code, null));
        assertFalse(PhoneProtocol.codeMatches(null, code));
        assertFalse(PhoneProtocol.codeMatches("123456", "123457"));
        assertFalse(PhoneProtocol.codeMatches("123456", "12345"));
    }

    @Test
    public void cleanTextKeepsNewlinesAndDropsControls() {
        assertEquals("a\nb c", PhoneProtocol.cleanText("  a\nb\u0000 c\u0007 "));
        assertEquals("", PhoneProtocol.cleanText(null));
    }

    @Test
    public void jsonResponsesAreWellFormed() {
        String ok = new String(PhoneProtocol.json(200, true, null), StandardCharsets.UTF_8);
        assertTrue(ok.startsWith("HTTP/1.1 200 OK\r\n"));
        assertTrue(ok.contains("Content-Length: 11\r\n"));
        assertTrue(ok.endsWith("\r\n\r\n{\"ok\":true}"));
        String error = new String(PhoneProtocol.json(401, false, "code"), StandardCharsets.UTF_8);
        assertTrue(error.startsWith("HTTP/1.1 401 Unauthorized\r\n"));
        assertTrue(error.contains("Cache-Control: no-store\r\n"));
        assertTrue(error.endsWith("{\"ok\":false,\"error\":\"code\"}"));
    }

    @Test
    public void pagesHaveNoPlaceholdersAndRightField() {
        String chat = PhoneProtocol.page(false);
        String key = PhoneProtocol.page(true);
        assertFalse(chat.contains("{{"));
        assertFalse(key.contains("{{"));
        assertTrue(chat.contains("<textarea id=\"text\""));
        assertTrue(key.contains("type=\"password\""));
        assertTrue(chat.contains("if(true)"));
        assertTrue(key.contains("if(false)"));
        assertNotEquals(chat, key);
    }

    @Test
    public void outboxPageServesThePollingCommandList() {
        String out = PhoneProtocol.page(PhoneBridge.MODE_OUT);
        assertFalse(out.contains("{{"));
        assertTrue(out.contains("fetch('/outbox'"));
        assertTrue(out.contains("X-Code"));
        assertTrue(out.contains("execCommand('copy')"));
        assertNotEquals(out, PhoneProtocol.page(false));
    }

    @Test
    public void jsonWithTextEscapesEverythingBrowsersParse() {
        byte[] payload = PhoneProtocol.jsonWithText(200, "a \"quoted\"\n\\path");
        String body = new String(payload, StandardCharsets.UTF_8)
                .substring(new String(payload, StandardCharsets.UTF_8).indexOf("\r\n\r\n") + 4);
        assertTrue(body.startsWith("{\"ok\":true,\"text\":\""));
        assertTrue(body.endsWith("\"}"));
        assertTrue(body.contains("a \\\"quoted\\\"\\n\\\\path"));
        assertFalse(body.contains("\n"));
    }

    // ---------------------------------------------------- urls and files (v5.5)

    @Test
    public void onlyHttpAndHttpsLinksAreAccepted() {
        assertTrue(PhoneProtocol.isHttpUrl("https://zakaa-ai.net/blog"));
        assertTrue(PhoneProtocol.isHttpUrl("http://192.168.100.111/watch?v=x"));
        assertFalse(PhoneProtocol.isHttpUrl(null));
        assertFalse(PhoneProtocol.isHttpUrl(""));
        assertFalse(PhoneProtocol.isHttpUrl("javascript:alert(1)"));
        assertFalse(PhoneProtocol.isHttpUrl("ftp://host/file"));
        assertFalse(PhoneProtocol.isHttpUrl("https://"));
        assertFalse(PhoneProtocol.isHttpUrl("https:/not-a-link"));
    }

    @Test
    public void filenamesAreCleanedAndCapped() {
        assertEquals("holiday photo.jpg", PhoneProtocol.sanitizeFilename(" holiday photo.jpg "));
        // Path separators and traversal never survive.
        assertEquals("etc passwd", PhoneProtocol.sanitizeFilename("../../etc/passwd"));
        assertEquals("a b", PhoneProtocol.sanitizeFilename("a\\b"));
        assertEquals("a b", PhoneProtocol.sanitizeFilename("a/b"));
        assertEquals("nul", PhoneProtocol.sanitizeFilename("nu\u0000l"));
        assertEquals("", PhoneProtocol.sanitizeFilename(null));
        assertEquals("", PhoneProtocol.sanitizeFilename("   "));
        assertEquals("", PhoneProtocol.sanitizeFilename(".."));
        String longName = PhoneProtocol.sanitizeFilename(new String(new char[500]).replace('\0', 'x'));
        assertEquals(PhoneProtocol.MAX_FILENAME_CHARS, longName.length());
    }

    @Test
    public void headAndBodyReadIsSplitForFileUploads() throws IOException {
        String raw = "POST /send HTTP/1.1\r\nContent-Length: 5\r\nHost: a\r\n\r\nhello";
        PhoneProtocol.Request head = PhoneProtocol.readHead(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        assertEquals("POST", head.method);
        assertNull(head.body);
        PhoneProtocol.Request full = PhoneProtocol.finish(head,
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        assertEquals("hello", full.body);
        assertEquals(2, full.headers.size());
    }

    @Test
    public void finishedHeadCannotBeFinishedTwice() {
        PhoneProtocol.Request head = new PhoneProtocol.Request("POST", "/send", java.util.Collections.emptyMap(), "");
        assertThrows(AssertionError.class, () -> PhoneProtocol.finish(head, new ByteArrayInputStream(new byte[0])));
    }

    @Test
    public void urlAndFilePagesMateWithTheirModes() {
        String url = PhoneProtocol.page(PhoneBridge.MODE_URL);
        String file = PhoneProtocol.page(PhoneBridge.MODE_FILES);
        assertFalse(url.contains("{{"));
        assertFalse(file.contains("{{"));
        assertTrue(url.contains("type=\"url\""));
        assertTrue(url.contains("'/send'"));
        assertTrue(file.contains("type=\"file\""));
        assertTrue(file.contains("fetch('/file'"));
        assertTrue(file.contains("X-Filename"));
        assertTrue(file.contains("'X-Code':code"));
        // The streaming limit is stated on the page so big transfers are not a surprise.
        assertTrue(file.contains("512"));
    }
}
