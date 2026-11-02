package com.firestick.aura.launcher.assistant;

import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

/**
 * Phone link: a tiny HTTP server on the TV's home-network address. The phone opens the page from
 * the QR code and, depending on the mode, sends spoken/typed questions, an OpenRouter key, opens
 * web links on the TV, or transfers files to the TV's Downloads folder (streamed to disk, size-
 * capped, and saved only — an APK on that link is never installed). Commands can also be pushed
 * the other way in the outbox mode. Runs only while a pairing screen is open, stops after 10
 * idle minutes, and accepts traffic only with the current 6-digit code; 5 wrong codes rotate it.
 * Text and URLs are never logged.
 */
public final class PhoneBridge {
    public static final int MODE_CHAT = 0, MODE_KEY = 1, MODE_OUT = 2, MODE_URL = 3, MODE_FILES = 4;
    /** How many pushed commands are kept for the phone to pick up. */
    private static final int MAX_OUTBOX = 8;
    public static final int START_OK = 0, START_NO_NETWORK = 1, START_PORT_BUSY = 2;

    private static final String TAG = "LumoraPhone";
    private static final int FIRST_PORT = 8765, LAST_PORT = 8775;
    private static final int MAX_FAILURES = 5, MAX_SENDS_PER_MINUTE = 30;
    private static final int ACCEPT_TIMEOUT_MS = 15_000, SOCKET_TIMEOUT_MS = 5_000;
    private static final long IDLE_TIMEOUT_MS = 10L * 60_000L, MINUTE_MS = 60_000L;
    /** Where transferred files land when the caller gave no directory. */
    private static final String RECEIVE_SUBDIR = "From Phone";

    public interface Listener {
        /** Text from the phone, on the main thread. */
        void onText(String text);

        /** The pairing code changed after repeated wrong codes; refresh the QR code. */
        void onCodeChanged(String code);

        /** The link closed itself (idle timeout or network error). */
        void onStopped(boolean idle);

        /** A web link from the phone (MODE_URL), on the main thread. Only http(s) arrive here. */
        default void onUrl(String url) {
        }

        /** A finished file transfer (MODE_FILES), on the main thread; the file is fully written. */
        default void onFileSaved(File file) {
        }
    }

    private final int mode;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();
    private final ArrayDeque<Long> recentSends = new ArrayDeque<>();
    private final ArrayDeque<String> outbox = new ArrayDeque<>();
    private volatile ServerSocket server;
    private volatile boolean running;
    private volatile String code;
    private volatile long lastUse;
    private String address;
    private int port;
    private int failures;
    /** Root for MODE_FILES transfers; null falls back to external app storage. */
    private volatile File receiveDir;
    private boolean fileBusy;

    public PhoneBridge(int mode, Listener listener) {
        this(mode, listener, null);
    }

    /**
     * @param receiveDir directory that file transfers (MODE_FILES) stream into; when null the
     *                   bridge writes to the app's external storage instead.
     */
    public PhoneBridge(int mode, Listener listener, File receiveDir) {
        this.mode = mode;
        this.listener = listener;
        this.receiveDir = receiveDir;
    }

    public synchronized int start() {
        if (running) return START_OK;
        InetAddress lan = lanAddress();
        if (lan == null) return START_NO_NETWORK;
        ServerSocket socket = null;
        for (int candidate = FIRST_PORT; candidate <= LAST_PORT && socket == null; candidate++) {
            try {
                ServerSocket attempt = new ServerSocket();
                attempt.setReuseAddress(true);
                try {
                    attempt.bind(new InetSocketAddress(lan, candidate), 4);
                } catch (IOException exception) {
                    attempt.close();
                    continue;
                }
                attempt.setSoTimeout(ACCEPT_TIMEOUT_MS);
                socket = attempt;
                port = candidate;
            } catch (IOException ignored) {
                // Try the next port.
            }
        }
        if (socket == null) return START_PORT_BUSY;
        server = socket;
        address = lan.getHostAddress();
        code = PhoneProtocol.newCode(random);
        failures = 0;
        lastUse = SystemClock.elapsedRealtime();
        running = true;
        Thread thread = new Thread(this::serve, "lumora-phone-link");
        thread.setDaemon(true);
        thread.start();
        return START_OK;
    }

    public void stop() {
        running = false;
        ServerSocket socket = server;
        server = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int mode() {
        return mode;
    }

    public String code() {
        return code;
    }

    /** Address to type into the phone's browser. */
    public String url() {
        return "http://" + address + ":" + port + "/";
    }

    /** Address with the pairing code in the fragment, which browsers never send to the server. */
    public String pairUrl() {
        return url() + "#c=" + code;
    }

    // ---------------------------------------------------------------- server

    private void serve() {
        boolean idle = false;
        while (running) {
            ServerSocket socket = server;
            if (socket == null) break;
            try (Socket client = socket.accept()) {
                client.setSoTimeout(SOCKET_TIMEOUT_MS);
                handle(client);
            } catch (SocketTimeoutException timeout) {
                // Wake up to check the idle timer.
            } catch (IOException exception) {
                if (!running) break;
                Log.w(TAG, "Connection failed: " + exception.getClass().getSimpleName());
            }
            if (SystemClock.elapsedRealtime() - lastUse > IDLE_TIMEOUT_MS) {
                idle = true;
                break;
            }
        }
        boolean wasRunning = running;
        stop();
        if (wasRunning) {
            boolean closedIdle = idle;
            main.post(() -> listener.onStopped(closedIdle));
        }
    }

    private void handle(Socket client) throws IOException {
        OutputStream out = client.getOutputStream();
        PhoneProtocol.Request request;
        try {
            request = PhoneProtocol.readHead(client.getInputStream());
        } catch (PhoneProtocol.BadRequest bad) {
            out.write(PhoneProtocol.json(bad.status, false, bad.error));
            return;
        }
        if (!PhoneProtocol.hostAllowed(request.header("host"), address, port)) {
            out.write(PhoneProtocol.json(403, false, "host"));
            return;
        }
        if ("/file".equals(request.path)) {
            if (!"POST".equals(request.method)) {
                out.write(PhoneProtocol.json(405, false, "method"));
                return;
            }
            out.write(receiveFile(request, client.getInputStream()));
        } else {
            // Every other route carries a small text body: read it whole.
            PhoneProtocol.Request withBody;
            try {
                withBody = PhoneProtocol.finish(request, client.getInputStream());
            } catch (PhoneProtocol.BadRequest bad) {
                out.write(PhoneProtocol.json(bad.status, false, bad.error));
                return;
            }
            if ("/".equals(withBody.path)) {
                if (!"GET".equals(withBody.method)) {
                    out.write(PhoneProtocol.json(405, false, "method"));
                    return;
                }
                lastUse = SystemClock.elapsedRealtime();
                byte[] page = PhoneProtocol.page(mode).getBytes(StandardCharsets.UTF_8);
                out.write(PhoneProtocol.response(200, "text/html; charset=utf-8", page));
            } else if ("/send".equals(withBody.path)) {
                if (!"POST".equals(withBody.method)) {
                    out.write(PhoneProtocol.json(405, false, "method"));
                    return;
                }
                out.write(send(withBody.body));
            } else if ("/outbox".equals(withBody.path)) {
                if (!"GET".equals(withBody.method)) {
                    out.write(PhoneProtocol.json(405, false, "method"));
                    return;
                }
                out.write(drainOutbox(withBody.header("x-code")));
            } else if ("/favicon.ico".equals(withBody.path)) {
                out.write(PhoneProtocol.response(204, "text/plain", new byte[0]));
            } else {
                out.write(PhoneProtocol.json(404, false, "not_found"));
            }
        }
    }

    private byte[] send(String body) {
        Map<String, String> form;
        try {
            form = PhoneProtocol.parseForm(body);
        } catch (PhoneProtocol.BadRequest bad) {
            return PhoneProtocol.json(bad.status, false, bad.error);
        }
        String text;
        synchronized (this) {
            if (!PhoneProtocol.codeMatches(code, form.get("c"))) {
                if (++failures >= MAX_FAILURES) {
                    failures = 0;
                    String fresh = PhoneProtocol.newCode(random);
                    code = fresh;
                    main.post(() -> {
                        if (running) listener.onCodeChanged(fresh);
                    });
                }
                return PhoneProtocol.json(401, false, "code");
            }
            failures = 0;
            long now = SystemClock.elapsedRealtime();
            while (!recentSends.isEmpty() && now - recentSends.peekFirst() > MINUTE_MS) recentSends.pollFirst();
            if (recentSends.size() >= MAX_SENDS_PER_MINUTE) return PhoneProtocol.json(429, false, "rate");
            if (mode == MODE_URL) {
                String url = PhoneProtocol.cleanText(form.get("u"));
                if (url.isEmpty()) return PhoneProtocol.json(400, false, "empty");
                if (url.length() > PhoneProtocol.MAX_URL_CHARS) return PhoneProtocol.json(413, false, "too_long");
                if (!PhoneProtocol.isHttpUrl(url)) return PhoneProtocol.json(400, false, "invalid_url");
                recentSends.addLast(now);
                lastUse = now;
                String address = url;
                main.post(() -> {
                    if (running) listener.onUrl(address);
                });
                return PhoneProtocol.json(200, true, null);
            }
            text = PhoneProtocol.cleanText(form.get("t"));
            if (text.isEmpty()) return PhoneProtocol.json(400, false, "empty");
            if (text.length() > PhoneProtocol.MAX_TEXT_CHARS) return PhoneProtocol.json(413, false, "too_long");
            recentSends.addLast(now);
            lastUse = now;
        }
        String delivered = text;
        main.post(() -> {
            if (running) listener.onText(delivered);
        });
        return PhoneProtocol.json(200, true, null);
    }

    /**
     * Streams one file from the phone to disk. Requests carry the pairing code (X-Code) and file
     * name (X-Filename) as headers and the bytes as one Content-Length body; the bridge never
     * holds the file in memory. APKs are saved only — nothing is installed from this link.
     */
    private byte[] receiveFile(PhoneProtocol.Request head, InputStream in) throws IOException {
        synchronized (this) {
            if (!PhoneProtocol.codeMatches(code, head.header("x-code"))) return PhoneProtocol.json(401, false, "code");
            if (fileBusy) return PhoneProtocol.json(429, false, "rate");
        }
        FileOutputStream file = null;
        try {
            // Header may legitimately omit the name; never trust it beyond the sanitizer.
            String name = PhoneProtocol.sanitizeFilename(head.header("x-filename"));
            if (name.isEmpty()) name = "received-file";
            String length = head.header("content-length");
            long expected;
            try {
                expected = length == null ? -1L : Long.parseLong(length);
            } catch (NumberFormatException bad) {
                return PhoneProtocol.json(400, false, "length");
            }
            if (expected < 0) return PhoneProtocol.json(411, false, "length");
            if (expected > PhoneProtocol.MAX_FILE_BYTES) return PhoneProtocol.json(413, false, "too_long");
            synchronized (this) {
                if (fileBusy) return PhoneProtocol.json(429, false, "rate");
                fileBusy = true;
            }
            File dir = receiveDir;
            if (dir == null) {
                // App-external storage: reachable over USB debugging and visible in file tools.
                dir = new File(Environment.getExternalStorageDirectory(), RECEIVE_SUBDIR);
            }
            if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
                fileBusy = false;
                return PhoneProtocol.json(500, false, "storage");
            }
            File target = unique(new File(dir, name));
            lastUse = SystemClock.elapsedRealtime();
            file = new FileOutputStream(target);
            byte[] buffer = new byte[64 * 1024];
            long done = 0L;
            int read;
            while (done < expected) {
                int chunk = in.read(buffer, 0, (int) Math.min(buffer.length, expected - done));
                if (chunk < 0) break;
                done += chunk;
                file.write(buffer, 0, chunk);
            }
            file.close();
            file = null;
            lastUse = SystemClock.elapsedRealtime();
            if (done != expected) {
                target.delete();
                return PhoneProtocol.json(400, false, "incomplete");
            }
            File saved = target;
            main.post(() -> {
                if (running) listener.onFileSaved(saved);
            });
            return PhoneProtocol.json(200, true, null);
        } catch (IOException exception) {
            return PhoneProtocol.json(500, false, "transfer");
        } finally {
            if (file != null) try {
                file.close();
            } catch (IOException ignored) {
                // The transfer failed anyway.
            }
            synchronized (this) {
                fileBusy = false;
            }
            lastUse = SystemClock.elapsedRealtime();
        }
    }

    /** Picks photo.png, photo (2).png … when a name that is already on disk arrives again. */
    private static File unique(File target) {
        if (!target.isFile()) return target;
        String name = target.getName();
        int dot = name.lastIndexOf('.');
        String stem = dot <= 0 ? name : name.substring(0, dot);
        String tail = dot <= 0 ? "" : name.substring(dot);
        for (int index = 2; index < 1000; index++) {
            File candidate = new File(target.getParentFile(), stem + " (" + index + ")" + tail);
            if (!candidate.isFile()) return candidate;
        }
        return new File(target.getParentFile(), stem + " " + System.currentTimeMillis() + tail);
    }

    /** Queues text pushed from the TV; the polling page picks it up. False when not running. */
    public synchronized boolean post(String text) {
        if (!running) return false;
        String clean = PhoneProtocol.cleanText(text);
        if (clean.isEmpty() || clean.length() > PhoneProtocol.MAX_TEXT_CHARS) return false;
        while (outbox.size() >= MAX_OUTBOX) outbox.pollFirst();
        outbox.addLast(clean);
        lastUse = SystemClock.elapsedRealtime();
        return true;
    }

    /** Only the browser holding the pairing code may drain the outbox. */
    private synchronized byte[] drainOutbox(String given) {
        if (!PhoneProtocol.codeMatches(code, given)) return PhoneProtocol.json(401, false, "code");
        lastUse = SystemClock.elapsedRealtime();
        StringBuilder text = new StringBuilder();
        while (!outbox.isEmpty()) {
            if (text.length() > 0) text.append("\n\n");
            text.append(outbox.pollFirst());
        }
        return PhoneProtocol.jsonWithText(200, text.toString());
    }

    // --------------------------------------------------------------- network

    /** Private IPv4 address of Wi-Fi or Ethernet; never the loopback or a point-to-point (VPN) interface. */
    static InetAddress lanAddress() {
        InetAddress fallback = null;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return null;
            for (NetworkInterface nif : Collections.list(interfaces)) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual() || nif.isPointToPoint()) continue;
                String name = nif.getName();
                boolean preferred = name.startsWith("wlan") || name.startsWith("eth");
                for (InetAddress candidate : Collections.list(nif.getInetAddresses())) {
                    if (!(candidate instanceof Inet4Address) || !candidate.isSiteLocalAddress()) continue;
                    if (preferred) return candidate;
                    if (fallback == null) fallback = candidate;
                }
            }
        } catch (SocketException exception) {
            return null;
        }
        return fallback;
    }
}
