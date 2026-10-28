package com.firestick.aura.launcher.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
}
