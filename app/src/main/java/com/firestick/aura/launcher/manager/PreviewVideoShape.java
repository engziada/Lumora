package com.firestick.aura.launcher.manager;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads dimensions only from public page metadata. Never extracts or plays stream URLs. */
public final class PreviewVideoShape {
    private PreviewVideoShape() { }
    public static boolean fits(String html) {
        try {
            Matcher marker = Pattern.compile("ytInitialPlayerResponse\\s*=\\s*").matcher(html);
            if (!marker.find()) return false;
            int start = marker.end(), depth = 0;
            boolean quoted = false, escape = false;
            for (int i = start; i < html.length(); i++) {
                char c = html.charAt(i);
                if (quoted) {
                    if (escape) escape = false;
                    else if (c == '\\') escape = true;
                    else if (c == '"') quoted = false;
                } else if (c == '"') quoted = true;
                else if (c == '{') depth++;
                else if (c == '}' && --depth == 0) {
                    JSONObject data = new JSONObject(html.substring(start, i + 1)).optJSONObject("streamingData");
                    if (data == null) return false;
                    for (String key : new String[]{"adaptiveFormats", "formats"}) {
                        JSONArray formats = data.optJSONArray(key);
                        if (formats == null) continue;
                        for (int j = 0; j < formats.length(); j++) {
                            JSONObject format = formats.getJSONObject(j);
                            int w = format.optInt("width"), h = format.optInt("height");
                            if (w > 0 && h > 0) return isWide(w, h);
                        }
                    }
                    return false;
                }
            }
        } catch (Exception ignored) { /* Unknown dimensions are excluded. */ }
        return false;
    }
    public static boolean isWide(int width, int height) {
        if (width <= 0 || height <= 0) return false;
        double ratio = (double) width / height;
        return ratio >= 1.65 && ratio <= 1.90;
    }
}
