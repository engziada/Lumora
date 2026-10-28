package com.firestick.aura.launcher.assistant;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
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
 * the QR code and sends text (typed or dictated with the phone keyboard's microphone) to Lumora.
 * Runs only while the pairing screen or the assistant is open, stops after 10 idle minutes, and
 * accepts text only with the current 6-digit code; 5 wrong codes rotate it. Text is never logged.
 */
public final class PhoneBridge {
    public static final int MODE_CHAT = 0, MODE_KEY = 1;
    public static final int START_OK = 0, START_NO_NETWORK = 1, START_PORT_BUSY = 2;

    private static final String TAG = "LumoraPhone";
    private static final int FIRST_PORT = 8765, LAST_PORT = 8775;
    private static final int MAX_FAILURES = 5, MAX_SENDS_PER_MINUTE = 30;
    private static final int ACCEPT_TIMEOUT_MS = 15_000, SOCKET_TIMEOUT_MS = 5_000;
    private static final long IDLE_TIMEOUT_MS = 10L * 60_000L, MINUTE_MS = 60_000L;

    public interface Listener {
        /** Text from the phone, on the main thread. */
        void onText(String text);

        /** The pairing code changed after repeated wrong codes; refresh the QR code. */
        void onCodeChanged(String code);

        /** The link closed itself (idle timeout or network error). */
        void onStopped(boolean idle);
    }

    private final int mode;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();
    private final ArrayDeque<Long> recentSends = new ArrayDeque<>();
    private volatile ServerSocket server;
    private volatile boolean running;
    private volatile String code;
    private volatile long lastUse;
    private String address;
    private int port;
    private int failures;

    public PhoneBridge(int mode, Listener listener) {
        this.mode = mode;
        this.listener = listener;
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
            request = PhoneProtocol.read(client.getInputStream());
        } catch (PhoneProtocol.BadRequest bad) {
            out.write(PhoneProtocol.json(bad.status, false, bad.error));
            return;
        }
        if (!PhoneProtocol.hostAllowed(request.header("host"), address, port)) {
            out.write(PhoneProtocol.json(403, false, "host"));
            return;
        }
        if ("/".equals(request.path)) {
            if (!"GET".equals(request.method)) {
                out.write(PhoneProtocol.json(405, false, "method"));
                return;
            }
            lastUse = SystemClock.elapsedRealtime();
            byte[] page = PhoneProtocol.page(mode == MODE_KEY).getBytes(StandardCharsets.UTF_8);
            out.write(PhoneProtocol.response(200, "text/html; charset=utf-8", page));
        } else if ("/send".equals(request.path)) {
            if (!"POST".equals(request.method)) {
                out.write(PhoneProtocol.json(405, false, "method"));
                return;
            }
            out.write(send(request.body));
        } else if ("/favicon.ico".equals(request.path)) {
            out.write(PhoneProtocol.response(204, "text/plain", new byte[0]));
        } else {
            out.write(PhoneProtocol.json(404, false, "not_found"));
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
