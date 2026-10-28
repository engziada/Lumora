package com.firestick.aura.launcher.assistant;

import java.nio.charset.StandardCharsets;

/**
 * Minimal QR Code encoder for the phone-link address: byte mode, error correction level M,
 * versions 1 to 6 (up to 106 bytes). Follows ISO/IEC 18004; mask chosen by the standard penalty.
 * Reference implementation and decoder check: tools/qr-reference.
 */
public final class QrCode {
    public static final int MAX_BYTES = 106;
    private static final int MAX_VERSION = 6;
    private static final int[] ECC_PER_BLOCK = {-1, 10, 16, 26, 18, 24, 16};
    private static final int[] NUM_BLOCKS = {-1, 1, 1, 1, 2, 2, 4};
    private static final int[][] ALIGN = {{}, {}, {6, 18}, {6, 22}, {6, 26}, {6, 30}, {6, 34}};
    private static final int FORMAT_ECL_M = 0;

    private final int size;
    private final boolean[][] modules;
    private final boolean[][] function;
    private final int mask;

    public static QrCode encode(String text) {
        return new QrCode(text.getBytes(StandardCharsets.UTF_8), -1);
    }

    static QrCode encode(String text, int forcedMask) {
        return new QrCode(text.getBytes(StandardCharsets.UTF_8), forcedMask);
    }

    private QrCode(byte[] data, int forcedMask) {
        int version = 0;
        for (int v = 1; v <= MAX_VERSION; v++) {
            if (4 + 8 + data.length * 8 <= dataCodewords(v) * 8) {
                version = v;
                break;
            }
        }
        if (version == 0) throw new IllegalArgumentException("Text too long for a version 6 QR code");
        size = version * 4 + 17;
        modules = new boolean[size][size];
        function = new boolean[size][size];

        drawFunctionPatterns(version);
        drawCodewords(addEccAndInterleave(dataBytes(data, version), version));
        int best = forcedMask;
        if (best < 0) {
            int minPenalty = Integer.MAX_VALUE;
            for (int m = 0; m < 8; m++) {
                applyMask(m);
                drawFormat(m);
                int penalty = penalty();
                if (penalty < minPenalty) {
                    best = m;
                    minPenalty = penalty;
                }
                applyMask(m);
            }
        }
        mask = best;
        applyMask(best);
        drawFormat(best);
    }

    public int size() {
        return size;
    }

    public int mask() {
        return mask;
    }

    /** @return {@code true} for a dark module. */
    public boolean isDark(int x, int y) {
        return modules[y][x];
    }

    // ------------------------------------------------------------------ data

    private static int rawModules(int v) {
        int result = (16 * v + 128) * v + 64;
        if (v >= 2) {
            int n = v / 7 + 2;
            result -= (25 * n - 10) * n - 55;
        }
        return result;
    }

    private static int dataCodewords(int v) {
        return rawModules(v) / 8 - ECC_PER_BLOCK[v] * NUM_BLOCKS[v];
    }

    private static int[] dataBytes(byte[] data, int version) {
        int capacity = dataCodewords(version) * 8;
        BitBuffer bits = new BitBuffer(capacity);
        bits.append(0x4, 4);
        bits.append(data.length, 8);
        for (byte b : data) bits.append(b & 0xFF, 8);
        bits.append(0, Math.min(4, capacity - bits.length));
        bits.append(0, (8 - bits.length % 8) % 8);
        for (int pad = 0xEC; bits.length < capacity; pad ^= 0xEC ^ 0x11) bits.append(pad, 8);
        int[] codewords = new int[bits.length / 8];
        for (int i = 0; i < bits.length; i++) codewords[i >>> 3] |= bits.get(i) << (7 - (i & 7));
        return codewords;
    }

    private static int[] addEccAndInterleave(int[] data, int version) {
        int numBlocks = NUM_BLOCKS[version];
        int blockEcc = ECC_PER_BLOCK[version];
        int raw = rawModules(version) / 8;
        int numShort = numBlocks - raw % numBlocks;
        int shortLen = raw / numBlocks;
        int[] divisor = reedSolomonDivisor(blockEcc);
        int[][] blocks = new int[numBlocks][];
        for (int i = 0, k = 0; i < numBlocks; i++) {
            int dataLen = shortLen - blockEcc + (i < numShort ? 0 : 1);
            int[] dat = new int[dataLen];
            System.arraycopy(data, k, dat, 0, dataLen);
            k += dataLen;
            int[] ecc = reedSolomonRemainder(dat, divisor);
            int[] block = new int[shortLen + 1];
            System.arraycopy(dat, 0, block, 0, dataLen);
            System.arraycopy(ecc, 0, block, shortLen + 1 - blockEcc, blockEcc);
            blocks[i] = block;
        }
        int[] result = new int[raw];
        int n = 0;
        for (int i = 0; i < shortLen + 1; i++) {
            for (int j = 0; j < numBlocks; j++) {
                if (i != shortLen - blockEcc || j >= numShort) result[n++] = blocks[j][i];
            }
        }
        return result;
    }

    static int gfMultiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z & 0xFF;
    }

    private static int[] reedSolomonDivisor(int degree) {
        int[] result = new int[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = gfMultiply(result[j], root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = gfMultiply(root, 0x02);
        }
        return result;
    }

    private static int[] reedSolomonRemainder(int[] data, int[] divisor) {
        int[] result = new int[divisor.length];
        for (int b : data) {
            int factor = b ^ result[0];
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= gfMultiply(divisor[i], factor);
        }
        return result;
    }

    // -------------------------------------------------------------- drawing

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        function[y][x] = true;
    }

    private void drawFunctionPatterns(int version) {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        int[] positions = ALIGN[version];
        int n = positions.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        setFunction(positions[i] + dx, positions[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                    }
                }
            }
        }
        drawFormat(0);
    }

    private void drawFinder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x < 0 || x >= size || y < 0 || y >= size) continue;
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                setFunction(x, y, dist != 2 && dist != 4);
            }
        }
    }

    private void drawFormat(int maskValue) {
        int data = FORMAT_ECL_M << 3 | maskValue;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) setFunction(8, i, bit(bits, i));
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) setFunction(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) setFunction(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) setFunction(8, size - 15 + i, bit(bits, i));
        setFunction(8, size - 8, true);
    }

    private static boolean bit(int value, int index) {
        return ((value >>> index) & 1) != 0;
    }

    private void drawCodewords(int[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!function[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    static boolean maskBit(int maskValue, int x, int y) {
        switch (maskValue) {
            case 0: return (x + y) % 2 == 0;
            case 1: return y % 2 == 0;
            case 2: return x % 3 == 0;
            case 3: return (x + y) % 3 == 0;
            case 4: return (x / 3 + y / 2) % 2 == 0;
            case 5: return x * y % 2 + x * y % 3 == 0;
            case 6: return (x * y % 2 + x * y % 3) % 2 == 0;
            default: return ((x + y) % 2 + x * y % 3) % 2 == 0;
        }
    }

    private void applyMask(int maskValue) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (maskBit(maskValue, x, y) && !function[y][x]) modules[y][x] = !modules[y][x];
            }
        }
    }

    // -------------------------------------------------------------- penalty

    private int penalty() {
        int result = 0;
        for (int pass = 0; pass < 2; pass++) {
            boolean horizontal = pass == 0;
            for (int a = 0; a < size; a++) {
                boolean runColor = false;
                int run = 0;
                int[] history = new int[7];
                for (int b = 0; b < size; b++) {
                    boolean dark = horizontal ? modules[a][b] : modules[b][a];
                    if (dark == runColor) {
                        run++;
                        if (run == 5) result += 3;
                        else if (run > 5) result++;
                    } else {
                        addHistory(run, history);
                        if (!runColor) result += countPatterns(history) * 40;
                        runColor = dark;
                        run = 1;
                    }
                }
                result += terminate(runColor, run, history) * 40;
            }
        }
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += 3;
            }
        }
        int dark = 0;
        for (boolean[] row : modules) for (boolean c : row) if (c) dark++;
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        return result + k * 10;
    }

    private static int countPatterns(int[] h) {
        int n = h[1];
        boolean core = n > 0 && h[2] == n && h[3] == n * 3 && h[4] == n && h[5] == n;
        return (core && h[0] >= n * 4 && h[6] >= n ? 1 : 0) + (core && h[6] >= n * 4 && h[0] >= n ? 1 : 0);
    }

    private int terminate(boolean runColor, int run, int[] history) {
        if (runColor) {
            addHistory(run, history);
            run = 0;
        }
        addHistory(run + size, history);
        return countPatterns(history);
    }

    private void addHistory(int run, int[] history) {
        if (history[0] == 0) run += size;
        System.arraycopy(history, 0, history, 1, history.length - 1);
        history[0] = run;
    }

    private static final class BitBuffer {
        private final int[] bits;
        int length;

        BitBuffer(int capacity) {
            bits = new int[capacity];
        }

        void append(int value, int count) {
            for (int i = count - 1; i >= 0; i--) bits[length++] = (value >>> i) & 1;
        }

        int get(int index) {
            return bits[index];
        }
    }
}
