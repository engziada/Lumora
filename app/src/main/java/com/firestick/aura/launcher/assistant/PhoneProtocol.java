package com.firestick.aura.launcher.assistant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP/1.1 subset and page for the phone link ({@link PhoneBridge}). Pure Java so it can be tested
 * on the JVM. Requests are size-limited, every POST must carry the 6-digit pairing code, and the
 * Host header must name the TV's own address (blocks DNS-rebinding pages in the phone's browser).
 */
public final class PhoneProtocol {
    public static final int MAX_HEADER_BYTES = 8 * 1024;
    public static final int MAX_BODY_BYTES = 8 * 1024;
    public static final int MAX_TEXT_CHARS = 2000;
    public static final int CODE_DIGITS = 6;

    private PhoneProtocol() {
    }

    /** A rejected request; {@link #error} is the machine-readable code returned to the page. */
    public static final class BadRequest extends IOException {
        public final int status;
        public final String error;

        BadRequest(int status, String error) {
            super(error);
            this.status = status;
            this.error = error;
        }
    }

    public static final class Request {
        public final String method;
        public final String path;
        public final Map<String, String> headers;
        public final String body;

        Request(String method, String path, Map<String, String> headers, String body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }

        public String header(String name) {
            String value = headers.get(name.toLowerCase(Locale.ROOT));
            return value == null ? "" : value;
        }
    }

    // --------------------------------------------------------------- parsing

    public static Request read(InputStream in) throws IOException {
        byte[] head = readHead(in);
        String[] lines = new String(head, StandardCharsets.ISO_8859_1).split("\r\n");
        String[] start = lines[0].split(" ");
        if (start.length != 3 || !start[2].startsWith("HTTP/1.")) throw new BadRequest(400, "request");
        String method = start[0];
        String path = start[1];
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) continue;
            headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
        }
        String body = "";
        String length = headers.get("content-length");
        if (length != null) {
            int bytes;
            try {
                bytes = Integer.parseInt(length);
            } catch (NumberFormatException exception) {
                throw new BadRequest(400, "length");
            }
            if (bytes < 0) throw new BadRequest(400, "length");
            if (bytes > MAX_BODY_BYTES) throw new BadRequest(413, "too_long");
            body = new String(readExactly(in, bytes), StandardCharsets.UTF_8);
        } else if (headers.containsKey("transfer-encoding")) {
            throw new BadRequest(411, "length");
        }
        return new Request(method, path, headers, body);
    }

    private static byte[] readHead(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int last4 = 0;
        while (true) {
            int b = in.read();
            if (b < 0) throw new BadRequest(400, "request");
            out.write(b);
            if (out.size() > MAX_HEADER_BYTES) throw new BadRequest(431, "headers");
            last4 = (last4 << 8) | b;
            if (last4 == 0x0D0A0D0A) {
                byte[] all = out.toByteArray();
                byte[] head = new byte[all.length - 4];
                System.arraycopy(all, 0, head, 0, head.length);
                return head;
            }
        }
    }

    private static byte[] readExactly(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(data, read, length - read);
            if (n < 0) throw new BadRequest(400, "body");
            read += n;
        }
        return data;
    }

    /** application/x-www-form-urlencoded, UTF-8. */
    public static Map<String, String> parseForm(String body) throws BadRequest {
        Map<String, String> form = new HashMap<>();
        if (body == null || body.isEmpty()) return form;
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            form.put(decode(name), decode(value));
        }
        return form;
    }

    private static String decode(String value) throws BadRequest {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (IllegalArgumentException | UnsupportedEncodingException exception) {
            throw new BadRequest(400, "encoding");
        }
    }

    // -------------------------------------------------------------- security

    public static boolean hostAllowed(String host, String address, int port) {
        if (host == null) return false;
        String value = host.trim().toLowerCase(Locale.ROOT);
        return value.equals(address + ":" + port) || (port == 80 && value.equals(address));
    }

    /** Constant-time comparison of the pairing code. */
    public static boolean codeMatches(String expected, String given) {
        if (expected == null || given == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.trim().getBytes(StandardCharsets.UTF_8));
    }

    public static String newCode(SecureRandom random) {
        StringBuilder code = new StringBuilder(CODE_DIGITS);
        for (int i = 0; i < CODE_DIGITS; i++) code.append((char) ('0' + random.nextInt(10)));
        return code.toString();
    }

    /** Text as sent to the assistant: trimmed, control characters except newlines and tabs removed. */
    public static String cleanText(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\t' || !Character.isISOControl(c)) out.append(c);
        }
        return out.toString().trim();
    }

    // ------------------------------------------------------------- responses

    public static byte[] response(int status, String contentType, byte[] body) {
        String head = "HTTP/1.1 " + status + " " + reason(status) + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Referrer-Policy: no-referrer\r\n"
                + "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; "
                + "script-src 'unsafe-inline'; connect-src 'self'; form-action 'self'; frame-ancestors 'none'\r\n"
                + "Connection: close\r\n\r\n";
        byte[] headBytes = head.getBytes(StandardCharsets.ISO_8859_1);
        byte[] out = new byte[headBytes.length + body.length];
        System.arraycopy(headBytes, 0, out, 0, headBytes.length);
        System.arraycopy(body, 0, out, headBytes.length, body.length);
        return out;
    }

    public static byte[] json(int status, boolean ok, String error) {
        String body = ok ? "{\"ok\":true}" : "{\"ok\":false,\"error\":\"" + error + "\"}";
        return response(status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 411: return "Length Required";
            case 413: return "Payload Too Large";
            case 429: return "Too Many Requests";
            case 431: return "Request Header Fields Too Large";
            default: return "Error";
        }
    }

    // ------------------------------------------------------------------ page

    /** Mobile page. {@code keyMode} asks for the OpenRouter key in a masked field instead of a question. */
    public static String page(boolean keyMode) {
        String field = keyMode
                ? "<input id=\"text\" type=\"password\" autocomplete=\"off\" autocapitalize=\"off\" spellcheck=\"false\" maxlength=\"" + MAX_TEXT_CHARS + "\">"
                : "<textarea id=\"text\" dir=\"auto\" rows=\"4\" maxlength=\"" + MAX_TEXT_CHARS + "\" placeholder=\"Ask anything\u2026\"></textarea>";
        return PAGE
                .replace("{{SUBTITLE}}", keyMode ? "Send your OpenRouter key to the TV" : "Talk to the assistant on your TV")
                .replace("{{LABEL}}", keyMode ? "OpenRouter key (starts with sk-or-)" : "Your question")
                .replace("{{FIELD}}", field)
                .replace("{{HINT}}", keyMode
                        ? "Paste the key, then Send. It travels only over your home network to the TV."
                        : "Tap the microphone on your keyboard to dictate, in English or Arabic, then Send.")
                .replace("{{SENT}}", keyMode ? "Key sent. Check the TV." : "Sent. The answer appears on the TV.")
                .replace("{{CHAT}}", keyMode ? "false" : "true");
    }

    private static final String PAGE = "<!doctype html>\n"
            + "<html lang=\"en\"><head><meta charset=\"utf-8\">\n"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
            + "<meta name=\"color-scheme\" content=\"dark\"><title>Lumora</title>\n"
            + "<style>\n"
            + ":root{--bg:#1c1511;--panel:#2a201a;--line:#4a3a2e;--text:#f6ece0;--muted:#c4b2a0;--accent:#e3a15a;--ink:#24170c}\n"
            + "*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);"
            + "font:clamp(1rem,0.95rem + 0.4vw,1.125rem)/1.5 system-ui,-apple-system,'Segoe UI',Roboto,sans-serif}\n"
            + "main{max-width:34rem;margin:0 auto;padding:1.5rem 1.25rem 3rem}\n"
            + "h1{margin:0;font-size:1.75rem;letter-spacing:.02em;color:var(--accent)}\n"
            + ".sub{margin:.25rem 0 1.5rem;color:var(--muted)}\n"
            + "label{display:block;margin-bottom:.5rem;font-weight:600}\n"
            + "textarea,input{width:100%;padding:.85rem 1rem;border:1px solid var(--line);border-radius:.9rem;"
            + "background:var(--panel);color:var(--text);font:inherit}\n"
            + "textarea:focus,input:focus{outline:2px solid var(--accent);outline-offset:1px}\n"
            + "button{margin-top:1rem;width:100%;min-height:3rem;border:0;border-radius:.9rem;background:var(--accent);"
            + "color:var(--ink);font:inherit;font-weight:700}\n"
            + "button:focus-visible{outline:3px solid var(--text);outline-offset:2px}\n"
            + "button:disabled{opacity:.55}\n"
            + ".hint{margin:.5rem 0 0;color:var(--muted);font-size:.9em}\n"
            + "#status{min-height:1.5em;margin:1rem 0 0}\n"
            + "ol{list-style:none;margin:1rem 0 0;padding:0}li{padding:.6rem .9rem;margin-top:.5rem;border-radius:.9rem;"
            + "background:var(--panel);border:1px solid var(--line)}\n"
            + "[hidden]{display:none!important}\n"
            + "</style></head><body><main>\n"
            + "<h1>Lumora</h1><p class=\"sub\">{{SUBTITLE}}</p>\n"
            + "<section id=\"pair\" hidden><label for=\"code\">Pairing code shown on the TV</label>\n"
            + "<input id=\"code\" inputmode=\"numeric\" autocomplete=\"one-time-code\" maxlength=\"6\">\n"
            + "<button id=\"pairBtn\" type=\"button\">Connect</button></section>\n"
            + "<form id=\"form\" hidden><label for=\"text\">{{LABEL}}</label>{{FIELD}}\n"
            + "<p class=\"hint\">{{HINT}}</p><button type=\"submit\">Send</button></form>\n"
            + "<p id=\"status\" role=\"status\" aria-live=\"polite\"></p><ol id=\"sent\"></ol>\n"
            + "</main><script>\n"
            + "(function(){\n"
            + "var code=sessionStorage.getItem('c')||'';\n"
            + "var m=location.hash.match(/c=(\\d{6})/);\n"
            + "if(m){code=m[1];sessionStorage.setItem('c',code);history.replaceState(null,'',location.pathname);}\n"
            + "var pair=document.getElementById('pair'),form=document.getElementById('form'),text=document.getElementById('text'),"
            + "codeInput=document.getElementById('code'),status=document.getElementById('status'),sent=document.getElementById('sent');\n"
            + "function show(){pair.hidden=!!code;form.hidden=!code;(code?text:codeInput).focus();}\n"
            + "document.getElementById('pairBtn').onclick=function(){var v=codeInput.value.trim();"
            + "if(/^\\d{6}$/.test(v)){code=v;sessionStorage.setItem('c',v);status.textContent='';show();}"
            + "else{status.textContent='Enter the 6 digits shown on the TV.';}};\n"
            + "form.onsubmit=function(e){e.preventDefault();var t=text.value.trim();if(!t)return;"
            + "var btn=form.querySelector('button');btn.disabled=true;status.textContent='Sending\\u2026';\n"
            + "fetch('/send',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},"
            + "body:'c='+encodeURIComponent(code)+'&t='+encodeURIComponent(t)})\n"
            + ".then(function(r){return r.json();}).then(function(j){\n"
            + "if(j.ok){status.textContent='{{SENT}}';text.value='';"
            + "if({{CHAT}}){var li=document.createElement('li');li.dir='auto';li.textContent=t;sent.insertBefore(li,sent.firstChild);}}\n"
            + "else if(j.error==='code'){code='';sessionStorage.removeItem('c');"
            + "status.textContent='The code is wrong or has changed. Enter the code shown on the TV.';show();}\n"
            + "else if(j.error==='rate'){status.textContent='Too many messages. Wait a moment.';}\n"
            + "else{status.textContent='Not sent ('+j.error+').';}\n"
            + "}).catch(function(){status.textContent='The TV did not answer. Is the phone link still open on the TV?';})\n"
            + ".then(function(){btn.disabled=false;});};\n"
            + "show();})();\n"
            + "</script></body></html>\n";
}
