package com.firestick.aura.launcher.model;

/** One show airing today, as listed by TVmaze. */
public final class ShowItem {
    public final long id;
    public final String title;
    public final String channel;
    public final String airtime;
    public final String posterUrl;
    public final String summary;
    public final String episodeName;
    public final String genres;
    public final int season;
    public final int number;
    public final double rating;
    public final int weight;
    public final boolean streaming;

    public ShowItem(long id, String title, String channel, String airtime, String posterUrl, String summary,
                    String episodeName, String genres, int season, int number, double rating, int weight,
                    boolean streaming) {
        this.id = id;
        this.title = title;
        this.channel = channel;
        this.airtime = airtime;
        this.posterUrl = posterUrl;
        this.summary = summary;
        this.episodeName = episodeName;
        this.genres = genres;
        this.season = season;
        this.number = number;
        this.rating = rating;
        this.weight = weight;
        this.streaming = streaming;
    }
}
