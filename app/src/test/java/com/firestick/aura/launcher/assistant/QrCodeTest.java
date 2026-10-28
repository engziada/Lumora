package com.firestick.aura.launcher.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Expected values come from tools/qr-reference/qr.py, whose output was decoded with OpenCV's QR
 * detectors for 66 strings and all eight masks (tools/qr-reference/verify.py).
 */
public class QrCodeTest {
    private static final String HELLO =
            "111111101101001111111\n"
            + "100000100110101000001\n"
            + "101110100111101011101\n"
            + "101110101001001011101\n"
            + "101110101000101011101\n"
            + "100000101011001000001\n"
            + "111111101010101111111\n"
            + "000000001111100000000\n"
            + "100010111111011111001\n"
            + "000111001011100101111\n"
            + "101100101011001110010\n"
            + "111001000100011010000\n"
            + "001011100100111000110\n"
            + "000000001110111001011\n"
            + "111111101100110001010\n"
            + "100000100001100100010\n"
            + "101110101001001110101\n"
            + "101110100001100001011\n"
            + "101110100111001111000\n"
            + "100000100100011000000\n"
            + "111111101000111110101";

    private static String rows(QrCode code) {
        StringBuilder out = new StringBuilder();
        for (int y = 0; y < code.size(); y++) {
            if (y > 0) out.append('\n');
            for (int x = 0; x < code.size(); x++) out.append(code.isDark(x, y) ? '1' : '0');
        }
        return out.toString();
    }

    private static String sha256(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    @Test
    public void helloMatchesReferenceExactly() {
        QrCode code = QrCode.encode("HELLO");
        assertEquals(21, code.size());
        assertEquals(4, code.mask());
        assertEquals(HELLO, rows(code));
    }

    @Test
    public void phoneLinkAddressMatchesReference() throws Exception {
        QrCode code = QrCode.encode("http://192.168.1.23:8765/#c=123456");
        assertEquals(29, code.size());
        assertEquals(2, code.mask());
        assertEquals("053b58e7123f6bcb647ed0d39895187e6f0e58ae7787d54c2b8c96395f7aa21c", sha256(rows(code)));
    }

    @Test
    public void largestVersionSixMatchesReference() throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < QrCode.MAX_BYTES; i++) text.append('x');
        QrCode code = QrCode.encode(text.toString());
        assertEquals(41, code.size());
        assertEquals(3, code.mask());
        assertEquals("536f804ac066bd508901c3133f627c8fe275f79e23bcdf61ae9e1f3fa94dfb8c", sha256(rows(code)));
    }

    @Test
    public void finderPatternsInCorners() {
        QrCode code = QrCode.encode("http://10.0.0.5:8770/#c=000001");
        int last = code.size() - 1;
        assertTrue(code.isDark(0, 0));
        assertTrue(code.isDark(last, 0));
        assertTrue(code.isDark(0, last));
        assertFalse(code.isDark(7, 7));
        assertTrue(code.isDark(8, code.size() - 8));
    }

    @Test
    public void tooLongIsRejected() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i <= QrCode.MAX_BYTES; i++) text.append('x');
        assertThrows(IllegalArgumentException.class, () -> QrCode.encode(text.toString()));
    }

    @Test
    public void galoisFieldMultiply() {
        assertEquals(0, QrCode.gfMultiply(0, 0x53));
        assertEquals(0x53, QrCode.gfMultiply(1, 0x53));
        assertEquals(0x1D, QrCode.gfMultiply(0x80, 0x02));
    }
}
