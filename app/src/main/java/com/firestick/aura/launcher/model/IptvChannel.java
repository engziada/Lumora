package com.firestick.aura.launcher.model;

import java.util.List;

public class IptvChannel {
    public final java.util.Map<String, java.util.Map<String, String>> streamHeaders;
    public final String channelId;
    public final List<String> streamUrls;
    public final List<Long> legacyIds;
    public final long id;
    public final String name;
    public final String logo;
    public final String category;
    public final List<String> genres;
    public final String country;
    public final String language;
    public final String url;
    public final boolean isFavorite;
    public final String currentProgram;
    public final String nextProgram;
    public final String currentTime;
    public final String nextTime;

    public IptvChannel(long id, String name, String logo, String category, List<String> genres,
                       String country, String language, String url, boolean isFavorite,
                       String currentProgram, String nextProgram, String currentTime, String nextTime) {
        this(id, name, logo, category, genres, country, language, url, isFavorite,
                currentProgram, nextProgram, currentTime, nextTime, "",
                java.util.Collections.singletonList(url), java.util.Collections.emptyList());
    }

    public IptvChannel(long id, String name, String logo, String category, List<String> genres,
            String country, String language, String url, boolean isFavorite, String currentProgram,
            String nextProgram, String currentTime, String nextTime, String channelId,
            List<String> streamUrls, List<Long> legacyIds) {
        this(id, name, logo, category, genres, country, language, url, isFavorite, currentProgram,
                nextProgram, currentTime, nextTime, channelId, streamUrls, legacyIds, java.util.Collections.emptyMap());
    }

    public IptvChannel(long id, String name, String logo, String category, List<String> genres,
            String country, String language, String url, boolean isFavorite, String currentProgram,
            String nextProgram, String currentTime, String nextTime, String channelId,
            List<String> streamUrls, List<Long> legacyIds,
            java.util.Map<String, java.util.Map<String, String>> headers) {
        java.util.Map<String, java.util.Map<String, String>> copy = new java.util.HashMap<>();
        for (String feed : headers.keySet()) copy.put(feed, java.util.Collections.unmodifiableMap(
                new java.util.HashMap<>(headers.get(feed))));
        this.streamHeaders = java.util.Collections.unmodifiableMap(copy);
        this.channelId = channelId;
        this.streamUrls = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(streamUrls));
        this.legacyIds = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(legacyIds));
        this.id = id;
        this.name = name;
        this.logo = logo;
        this.category = category;
        this.genres = genres;
        this.country = country;
        this.language = language;
        this.url = url;
        this.isFavorite = isFavorite;
        this.currentProgram = currentProgram;
        this.nextProgram = nextProgram;
        this.currentTime = currentTime;
        this.nextTime = nextTime;
    }
    public IptvChannel withFavorite(boolean favorite) {
        return new IptvChannel(id, name, logo, category, genres, country, language, url, favorite,
                currentProgram, nextProgram, currentTime, nextTime, channelId, streamUrls, legacyIds, streamHeaders);
    }

    public boolean isAdult() { return genres.contains("18+"); }

    public org.json.JSONObject toJson() {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("id", id).put("name", name).put("logo", logo).put("country", country)
                .put("category", category).put("genres", new org.json.JSONArray(genres))
                .put("headers", new org.json.JSONObject(streamHeaders)).put("channelId", channelId).put("urls", new org.json.JSONArray(streamUrls));
        } catch (org.json.JSONException error) { throw new IllegalStateException(error); }
        return json;
    }

    public static IptvChannel fromJson(org.json.JSONObject json) throws org.json.JSONException {
        java.util.ArrayList<String> urls = new java.util.ArrayList<>();
        org.json.JSONArray rawUrls = json.getJSONArray("urls");
        for (int i = 0; i < rawUrls.length() && i < 30; i++) {
            String url = rawUrls.getString(i);
            if (url.startsWith("http://") || url.startsWith("https://")) urls.add(url);
        }
        if (urls.isEmpty()) throw new org.json.JSONException("No stream URLs");
        java.util.ArrayList<String> genres = new java.util.ArrayList<>();
        org.json.JSONArray rawGenres = json.optJSONArray("genres");
        if (rawGenres != null) for (int i = 0; i < rawGenres.length(); i++) genres.add(rawGenres.getString(i));
        java.util.Map<String, java.util.Map<String, String>> headers = new java.util.HashMap<>();
        org.json.JSONObject rawHeaders = json.optJSONObject("headers");
        if (rawHeaders != null) for (String feed : urls) {
            org.json.JSONObject values = rawHeaders.optJSONObject(feed);
            if (values == null) continue;
            java.util.Map<String, String> entry = new java.util.HashMap<>();
            for (String key : new String[]{"Referer", "User-Agent"}) {
                String value = values.optString(key);
                if (!value.isEmpty() && !value.contains("\r") && !value.contains("\n")) entry.put(key, value);
            }
            headers.put(feed, entry);
        }
        return new IptvChannel(json.getLong("id"), json.getString("name"), json.optString("logo"),
                json.optString("category"), genres, json.optString("country"), "", urls.get(0), false,
                "", "", "", "", json.optString("channelId"), urls, java.util.Collections.emptyList(), headers);
    }
}
