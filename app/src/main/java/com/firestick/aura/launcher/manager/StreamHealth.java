package com.firestick.aura.launcher.manager;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Device-local observations, never uploaded. Unknown feeds remain eligible for fallback. */
public final class StreamHealth {
    private final SharedPreferences preferences;
    public StreamHealth(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("iptv_health", Context.MODE_PRIVATE);
    }
    public List<String> rank(List<String> urls) {
        return rank(urls, read(), System.currentTimeMillis());
    }
    public void record(String url, boolean failed, long startupMs, long playingMs, long bufferingMs) {
        if (!failed && playingMs < 10000) return;
        JSONObject data = read();
        observe(data, url, failed, startupMs, playingMs, bufferingMs, System.currentTimeMillis());
        preferences.edit().putString("feeds", data.toString()).apply();
    }
    private JSONObject read() {
        try { return new JSONObject(preferences.getString("feeds", "{}")); }
        catch (JSONException invalid) { return new JSONObject(); }
    }
    public static void observe(JSONObject data, String url, boolean failed, long startup, long playing, long buffering, long now) {
        try {
            String key = Http.sha1(url);
            JSONObject old = data.optJSONObject(key);
            JSONObject sample = old == null ? new JSONObject() : old;
            int failures = failed ? Math.min(5, sample.optInt("failures") + 1) : 0;
            sample.put("failures", failures).put("updated", now);
            if (failed) sample.put("failed", now);
            else {
                double cost = Math.min(15000, Math.max(0, startup)) / 1000.0
                        + 30.0 * Math.max(0, buffering) / Math.max(1, playing + buffering);
                sample.put("cost", sample.has("cost") ? sample.optDouble("cost") * .6 + cost * .4 : cost);
                sample.put("success", now);
            }
            data.put(key, sample);
            while (data.length() > 256) {
                String oldest = null;
                long time = Long.MAX_VALUE;
                Iterator<String> keys = data.keys();
                while (keys.hasNext()) {
                    String next = keys.next();
                    if (data.getJSONObject(next).optLong("updated") < time) {
                        oldest = next; time = data.getJSONObject(next).optLong("updated");
                    }
                }
                if (oldest == null) break;
                data.remove(oldest);
            }
        } catch (JSONException invalid) { /* A malformed saved observation must not interrupt playback. */ }
    }
    public static List<String> rank(List<String> urls, JSONObject data, long now) {
        List<String> ordered = new ArrayList<>(urls);
        ordered.sort(Comparator.comparingDouble(url -> score(data.optJSONObject(Http.sha1(url)), now)));
        return ordered;
    }
    private static double score(JSONObject row, long now) {
        if (row == null || now - row.optLong("updated") > 7L * 86400000) return 10;
        double cost = row.has("cost") ? row.optDouble("cost", 10) : 10;
        long age = now - row.optLong("failed");
        if (row.optInt("failures") > 0 && age < 6L * 3600000)
            cost += row.optInt("failures") * (age < 600000 ? 100 : 20);
        return cost;
    }
}
