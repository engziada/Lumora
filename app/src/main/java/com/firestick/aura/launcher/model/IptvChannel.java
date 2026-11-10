package com.firestick.aura.launcher.model;

import java.util.List;

public class IptvChannel {
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
}
