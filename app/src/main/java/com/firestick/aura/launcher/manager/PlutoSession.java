package com.firestick.aura.launcher.manager;

import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * A Pluto TV session from boot.pluto.tv: the token and servers that On Demand films and the
 * guide timelines need. It stays in memory and in the cache for up to four hours; the token
 * itself lasts a day. Pluto answers only in the regions it serves, so {@link #get} returns null
 * elsewhere. Blocking: call from background threads only.
 */
public final class PlutoSession {
    private static final String BOOT = "https://boot.pluto.tv/v4/start?appName=web&appVersion=9.20.0"
            + "&deviceVersion=122.0.0&deviceModel=web&deviceMake=chrome&deviceType=web&clientModelNumber=1.0.0"
            + "&serverSideAds=false&drmCapabilities=&blockingMode=&notificationVersion=1&appLaunchCount=0"
            + "&lastAppLaunchDate=";
    static final long MAX_AGE_MS = 4L * 60L * 60L * 1000L;
    /** After a failed boot, callers get null at once for this long instead of each waiting on the network. */
    private static final long RETRY_MS = 60_000L;
    private static PlutoSession current;
    /** When the last boot failed; 0 before any failure. */
    private static long failedAt;

    public final String token, stitcher, params, vod, channels;
    final String clientId;
    final long expiresAt;

    PlutoSession(String token, String stitcher, String params, String vod, String channels, String clientId,
                 long expiresAt) {
        this.token = token;
        this.stitcher = stitcher;
        this.params = params;
        this.vod = vod;
        this.channels = channels;
        this.clientId = clientId;
        this.expiresAt = expiresAt;
    }

    /** A session that is still fresh, or a new one; null when Pluto TV cannot be reached or does not serve here. */
    public static synchronized PlutoSession get(File cacheDir) {
        long now = System.currentTimeMillis();
        if (current != null && now < current.expiresAt) return current;
        File file = new File(cacheDir, "pluto/session.json");
        byte[] saved = Http.readFile(file);
        PlutoSession stored = saved == null ? null : fromJson(new String(saved, StandardCharsets.UTF_8));
        // A clock moved back makes a saved session look too young; boot again then.
        if (stored != null && now < stored.expiresAt && stored.expiresAt - now <= MAX_AGE_MS) return current = stored;
        if (now >= failedAt && now - failedAt < RETRY_MS) return null;
        String clientId = stored != null ? stored.clientId : UUID.randomUUID().toString();
        byte[] body = Http.getBounded(BOOT + "&clientID=" + clientId + "&clientTime=" + iso(now).replace(":", "%3A"),
                8000, 15000, 512 * 1024);
        PlutoSession fresh = body == null ? null : parse(new String(body, StandardCharsets.UTF_8), clientId, now);
        if (fresh == null) {
            failedAt = System.currentTimeMillis();
            return null;
        }
        Http.writeFile(file, fresh.toJson().getBytes(StandardCharsets.UTF_8));
        return current = fresh;
    }

    /** Reads boot.pluto.tv's answer; null when the region is not served or a server is not Pluto's. */
    static PlutoSession parse(String json, String clientId, long now) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject servers = root.optJSONObject("servers"), session = root.optJSONObject("session");
            String token = root.optString("sessionToken");
            if (servers == null || session == null || token.isEmpty() || session.optString("activeRegion").isEmpty()) {
                return null;
            }
            String stitcher = servers.optString("stitcher"), vod = servers.optString("vod");
            String channels = servers.optString("channels");
            if (!isPluto(stitcher) || !isPluto(vod) || !isPluto(channels)) return null;
            long ttl = Math.max(60_000L, Math.min(MAX_AGE_MS, root.optLong("refreshInSec", 14_400L) * 1000L));
            return new PlutoSession(token, trim(stitcher), root.optString("stitcherParams"), trim(vod), trim(channels),
                    clientId, now + ttl);
        } catch (Exception malformed) {
            return null;
        }
    }

    /** True for HTTPS addresses on pluto.tv, the only hosts that get the session token. */
    public static boolean isPluto(String url) {
        if (url == null || !url.startsWith("https://")) return false;
        try {
            String host = new URI(url).getHost();
            return host != null && (host.equals("pluto.tv") || host.endsWith(".pluto.tv"));
        } catch (URISyntaxException malformed) {
            return false;
        }
    }

    /** The stream of one On Demand film, signed for this session. */
    public String playUrl(String episodeId) {
        return stitcher + "/v2/stitch/hls/episode/" + episodeId + "/master.m3u8?" + params + "&jwt=" + token
                + "&masterJWTPassthrough=true&includeExtendedEvents=true";
    }

    /** GET with the session token; the body for HTTP 200, otherwise null. */
    public byte[] fetch(String url, int maxBytes) {
        if (!isPluto(url)) return null;
        return Http.getBounded(url, 8000, 15000, maxBytes, Collections.singletonMap("Authorization", "Bearer " + token));
    }

    static String iso(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(millis));
    }

    private static String trim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String toJson() {
        try {
            return new JSONObject().put("token", token).put("stitcher", stitcher).put("params", params).put("vod", vod)
                    .put("channels", channels).put("clientId", clientId).put("expiresAt", expiresAt).toString();
        } catch (org.json.JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static PlutoSession fromJson(String json) {
        try {
            JSONObject row = new JSONObject(json);
            String clientId = row.optString("clientId");
            if (clientId.isEmpty()) return null;
            String stitcher = row.optString("stitcher"), vod = row.optString("vod"), channels = row.optString("channels");
            boolean usable = isPluto(stitcher) && isPluto(vod) && isPluto(channels) && !row.optString("token").isEmpty();
            // An unusable copy still keeps the device's client ID.
            return new PlutoSession(row.optString("token"), stitcher, row.optString("params"), vod, channels,
                    clientId, usable ? row.optLong("expiresAt") : 0L);
        } catch (Exception malformed) {
            return null;
        }
    }
}
