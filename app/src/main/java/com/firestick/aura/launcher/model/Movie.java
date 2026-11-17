package com.firestick.aura.launcher.model;

/**
 * A film for the Movies tab: a free Pluto TV On Demand title, or, where Pluto TV is not
 * available, a feature film from the Internet Archive that its uploader marked public domain.
 */
public final class Movie {
    public static final String ARCHIVE = "archive";
    public static final String PLUTO = "pluto";

    public final String source, id, title, year, description;
    /** Running time in minutes; 0 when the source gives none. */
    public final int minutes;
    /** Shown only after the lock key, like the IPTV Adults category. */
    public final boolean mature;
    /** Content rating such as PG-13 or TV-MA, genre, and a 16:9 still; empty when unknown. */
    public final String rating, genre, image;

    public Movie(String id, String title, String year, int minutes, String description, boolean mature) {
        this(ARCHIVE, id, title, year, minutes, description, mature, "", "", "");
    }

    public Movie(String source, String id, String title, String year, int minutes, String description,
                 boolean mature, String rating, String genre, String image) {
        this.source = PLUTO.equals(source) ? PLUTO : ARCHIVE;
        this.id = id;
        this.title = title;
        this.year = year;
        this.minutes = minutes;
        this.description = description;
        this.mature = mature;
        this.rating = rating == null ? "" : rating;
        this.genre = genre == null ? "" : genre;
        this.image = image == null ? "" : image;
    }

    public boolean isPluto() {
        return PLUTO.equals(source);
    }

    /** The source's still: Pluto's 16:9 key art, or the archive's item tile of about 180 px. */
    public String thumbnail() {
        return image.isEmpty() ? "https://archive.org/services/img/" + id : image;
    }
}
