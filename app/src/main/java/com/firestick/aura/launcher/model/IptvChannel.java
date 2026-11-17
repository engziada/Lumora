package com.firestick.aura.launcher.model;

import java.util.List;

public class IptvChannel {
    /** Genre for channels the source left uncategorised (iptv-org calls them "Undefined"). */
    public static final String OTHER = "Other";

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
        this.genres = withoutOther(genres);
        // A merged row that gained a real genre should not still read as uncategorised.
        this.category = OTHER.equalsIgnoreCase(category) && this.genres != genres ? this.genres.get(0) : category;
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

    /** Drops {@link #OTHER} (and blanks) once a merged row also carries a real genre; otherwise returns the list as is. */
    private static List<String> withoutOther(List<String> genres) {
        if (genres == null || genres.size() < 2) return genres;
        boolean other = false, real = false;
        for (String genre : genres) {
            String name = genre == null ? "" : genre.trim();
            if (OTHER.equalsIgnoreCase(name)) other = true;
            else if (!name.isEmpty()) real = true;
        }
        if (!other || !real) return genres;
        List<String> kept = new java.util.ArrayList<>(genres.size() - 1);
        for (String genre : genres) {
            String name = genre == null ? "" : genre.trim();
            if (!name.isEmpty() && !OTHER.equalsIgnoreCase(name)) kept.add(genre);
        }
        return kept;
    }

    public boolean isAdult() {
        for (String genre : genres) if (isAdultGroup(genre)) return true;
        return false;
    }

    /** This row also filed under 18+, so it stays behind the Adults PIN. */
    public IptvChannel asAdult() {
        if (genres.contains("18+")) return this;
        List<String> more = new java.util.ArrayList<>(genres);
        more.add("18+");
        return new IptvChannel(id, name, logo, category == null || category.isEmpty() ? "18+" : category, more,
                country, language, url, isFavorite, currentProgram, nextProgram, currentTime, nextTime, channelId,
                streamUrls, legacyIds, streamHeaders);
    }

    private static final java.util.Set<String> ADULT_WORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "adult", "adults", "adulto", "adultos", "adulte", "adultes", "xxx", "nsfw", "porn", "porno", "18+", "+18",
            "erotic", "erotica", "erotico", "erotik", "erotique", "sinnliche"));
    /** Name words: explicit terms and adult TV brands. Not "adult", which also names cartoon and music channels. */
    private static final java.util.Set<String> ADULT_NAME_WORDS = new java.util.HashSet<>(java.util.Arrays.asList(
            "xxx", "nsfw", "porn", "porno", "18+", "+18", "erotic", "erotica", "erotico", "erotik", "erotique",
            "sinnliche", "playboy", "hustler", "penthouse", "dorcel", "brazzers", "babestation", "redlight"));
    /** Softcore FAST channels (Pluto TV in the UK, Canada, Germany and Italy) whose names have no explicit word. */
    private static final String[] ADULT_NAMES = {"pluto tv spicy", "gigolos", "liebe sunde", "liebe suende",
            "notti di passione", "midnight secrets", "fashiontv midnight", "ftv midnight", "heisse nachte",
            "heisse naechte", "emmanuelle", "red shoe diaries", "sanfte beruhrungen", "sanfte beruehrungen",
            "stimulierende dokus"};
    /** Labels that use "adult" for something else: a cartoon block, animation for grown-ups and radio formats. */
    private static final java.util.regex.Pattern NOT_ADULT = java.util.regex.Pattern.compile(
            "(^| )(adult swim|adult animation|adult alternative|adult contemporary|pop adult)(?= |$)");
    private static final java.util.regex.Pattern WORD_BREAK = java.util.regex.Pattern.compile("[^\\p{L}\\p{N}+]+");

    /** Whole-word match, so "Adult Movies", "FOR ADULTS (18+)" and "Érotique" count but "XXXL Shop" and "Adult Swim" do not. */
    public static boolean isAdultGroup(String group) {
        return hasWord(group, ADULT_WORDS);
    }

    /** True for names with an explicit word, an adult TV brand or a known softcore FAST channel name. */
    public static boolean isAdultName(String name) {
        if (hasWord(name, ADULT_NAME_WORDS)) return true;
        String text = " " + words(name) + " ";
        for (String known : ADULT_NAMES) if (text.contains(" " + known + " ")) return true;
        return false;
    }

    private static boolean hasWord(String text, java.util.Set<String> vocabulary) {
        String kept = NOT_ADULT.matcher(words(text)).replaceAll(" ");
        for (String word : kept.split(" ")) if (vocabulary.contains(word)) return true;
        return false;
    }

    /** Lower case without accents, one space between words: "Heiße Nächte (18+)" becomes "heisse nachte 18+". */
    private static String words(String text) {
        String folded = java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT).replace("ß", "ss");
        return WORD_BREAK.matcher(folded).replaceAll(" ").trim();
    }

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
