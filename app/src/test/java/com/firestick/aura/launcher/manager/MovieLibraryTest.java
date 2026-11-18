package com.firestick.aura.launcher.manager;

import com.firestick.aura.launcher.model.Movie;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

public class MovieLibraryTest {
    private static Movie film(String id) {
        return new Movie(id, "Film " + id, "1950", 90, "", false);
    }

    @Test public void searchKeepsValidUniqueFilmsAndCleansTheirText() {
        String json = "{\"response\":{\"docs\":["
                + "{\"identifier\":\"night_of_the_living_dead\",\"title\":[\"Night of the\",\"Living Dead\"],"
                + "\"year\":\"1968-10-01\",\"runtime\":\"1:36:00\","
                + "\"description\":\"<p>Ghouls &amp; <b>zombies</b>&nbsp;attack</p>\"},"
                + "{\"identifier\":\"night_of_the_living_dead\",\"title\":\"Duplicate\"},"
                + "{\"identifier\":\"../etc\",\"title\":\"Bad id\"},"
                + "{\"identifier\":\"no_title\",\"title\":\"\"},"
                + "{\"identifier\":\"His.Girl_Friday-1940\",\"title\":\"His Girl Friday\",\"year\":1940,\"runtime\":\"92 min\"}"
                + "]}}";
        List<Movie> movies = MovieLibrary.parseSearch(json, true);
        assertEquals(2, movies.size());
        Movie first = movies.get(0);
        assertEquals("Night of the Living Dead", first.title);
        assertEquals("1968", first.year);
        assertEquals(96, first.minutes);
        assertEquals("Ghouls & zombies attack", first.description);
        assertTrue(first.mature);
        assertEquals("1940", movies.get(1).year);
        assertEquals(92, movies.get(1).minutes);
        assertTrue(MovieLibrary.parseSearch("not json", false).isEmpty());
    }

    @Test public void runningTimesBecomeMinutes() {
        assertEquals(95, MovieLibrary.minutes("95 min"));
        assertEquals(92, MovieLibrary.minutes("1:31:44"));
        assertEquals(72, MovieLibrary.minutes("72 min."));
        assertEquals(90, MovieLibrary.minutes("1h 30m"));
        assertEquals(62, MovieLibrary.minutes("1:02"));
        assertEquals(45, MovieLibrary.minutes("45:20"));
        assertEquals(95, MovieLibrary.minutes("95"));
        assertEquals(0, MovieLibrary.minutes("unknown"));
        assertEquals(0, MovieLibrary.minutes("9999 min"));
        assertEquals(0, MovieLibrary.minutes(null));
    }

    @Test public void playerGetsTheH264CopyThroughAnEncodedPath() {
        String files = "{\"result\":["
                + "{\"name\":\"film.ogv\",\"format\":\"Ogg Video\"},"
                + "{\"name\":\"film_512kb.mp4\",\"format\":\"512Kb MPEG4\"},"
                + "{\"name\":\"Big Film (1950).mp4\",\"format\":\"h.264\"},"
                + "{\"name\":\"../escape.mp4\",\"format\":\"h.264\"}]}";
        assertEquals("https://archive.org/download/big_film/Big%20Film%20%281950%29.mp4",
                MovieLibrary.pickVideo("big_film", files));
        assertEquals("https://archive.org/download/big_film/film_512kb.mp4", MovieLibrary.pickVideo("big_film",
                "{\"files\":[{\"name\":\"film_512kb.mp4\",\"format\":\"512Kb MPEG4\"}]}"));
        assertNull(MovieLibrary.pickVideo("../big_film", files));
        assertNull(MovieLibrary.pickVideo("big_film", "{\"result\":[{\"name\":\"film.ogv\",\"format\":\"Ogg Video\"}]}"));
    }

    @Test public void finishedAndRemovedFilmsLeaveTheShelves() {
        assertTrue(MovieLibrary.finished(5_300_000L, 5_400_000L));
        assertFalse(MovieLibrary.finished(600_000L, 5_400_000L));
        assertFalse("Unknown length is never finished", MovieLibrary.finished(600_000L, 0L));
        List<Movie> shown = MovieLibrary.visible(Arrays.asList(film("a"), film("b"), film("c")),
                new HashSet<>(Collections.singleton("b")));
        assertEquals(2, shown.size());
        assertEquals("c", shown.get(1).id);
    }

    @Test public void historyKeepsUnfinishedFilmsNewestFirst() {
        String json = MovieLibrary.remember("[]", film("a"), 30_000L, 5_400_000L);
        assertTrue("Under a minute is not worth resuming", MovieLibrary.parseHistory(json).isEmpty());
        json = MovieLibrary.remember(json, film("a"), 600_000L, 5_400_000L);
        json = MovieLibrary.remember(json, film("b"), 120_000L, 0L);
        json = MovieLibrary.remember(json, film("a"), 900_000L, 5_400_000L);
        List<MovieLibrary.Resume> rows = MovieLibrary.parseHistory(json);
        assertEquals(2, rows.size());
        assertEquals("a", rows.get(0).movie.id);
        assertEquals(900_000L, rows.get(0).position);
        assertEquals("b", rows.get(1).movie.id);

        json = MovieLibrary.remember(json, film("a"), 5_300_000L, 5_400_000L);
        assertEquals("The closing credits count as finished", 1, MovieLibrary.parseHistory(json).size());
        for (int i = 0; i < 20; i++) json = MovieLibrary.remember(json, film("f" + i), 120_000L, 5_400_000L);
        rows = MovieLibrary.parseHistory(json);
        assertEquals(12, rows.size());
        assertEquals("f19", rows.get(0).movie.id);
        assertEquals(1, MovieLibrary.parseHistory(MovieLibrary.remember("{broken", film("a"), 120_000L, 0L)).size());

        Movie mature = new Movie("after_dark", "After Dark", "", 0, "", true);
        assertTrue(MovieLibrary.fromJson(MovieLibrary.toJson(mature).toString()).mature);
    }

    @Test public void onlyTheAfterDarkShelfAsksForMatureFilms() {
        int mature = 0;
        for (MovieLibrary.Shelf shelf : MovieLibrary.ARCHIVE_SHELVES) {
            String url = MovieLibrary.searchUrl(shelf, 40);
            assertTrue(url.startsWith("https://archive.org/advancedsearch.php?q="));
            assertTrue(url.contains("licenseurl%3A*publicdomain*"));
            assertEquals(shelf.key, !shelf.mature, url.contains("+AND+NOT+"));
            if (shelf.mature) mature++;
        }
        assertEquals(1, mature);
    }

    private static final String PLUTO_ITEMS = "{\"items\":["
            + "{\"_id\":\"5f0000000000000000000001\",\"type\":\"series\",\"name\":\"A Show\",\"slug\":\"a-show-2022-1-1\"},"
            + "{\"_id\":\"5f0000000000000000000002\",\"type\":\"movie\",\"name\":\"Old Film\",\"slug\":\"old-film-1985-1-1\"},"
            + "{\"_id\":\"5f0000000000000000000003\",\"type\":\"movie\",\"name\":\"House of Gucci\","
            + "\"slug\":\"house-of-gucci-2021-1-1\",\"rating\":\"R\",\"originalContentDuration\":9480000,"
            + "\"summary\":\"Family &amp; fashion\",\"genre\":\"Drama\","
            + "\"poster16_9\":{\"path\":\"https://images.pluto.tv/episodes/g/poster16_9.jpg?w=100\"}},"
            + "{\"_id\":\"5f0000000000000000000004\",\"type\":\"movie\",\"name\":\"Mystery Film (2023)\","
            + "\"slug\":\"mystery-film\",\"rating\":\"Not Rated\","
            + "\"covers\":[{\"aspectRatio\":\"347:500\",\"url\":\"https://images.pluto.tv/p.jpg\"},"
            + "{\"aspectRatio\":\"16:9\",\"url\":\"https://images.pluto.tv/w.jpg\"}]},"
            + "{\"_id\":\"5f0000000000000000000005\",\"type\":\"movie\",\"name\":\"Late Film\","
            + "\"slug\":\"late-film-2019-1-1\",\"rating\":\"NC-17\"},"
            + "{\"_id\":\"5f0000000000000000000006\",\"type\":\"movie\",\"name\":\"Undated\",\"slug\":\"undated\","
            + "\"featuredImage\":{\"path\":\"https://example.com/x.jpg\"}},"
            + "{\"_id\":\"5f0000000000000000000003\",\"type\":\"movie\",\"name\":\"Duplicate\",\"slug\":\"dup-2024-1-1\"}"
            + "]}";

    @Test public void plutoShelvesKeepRecentFilmsNewestFirst() {
        List<Movie> movies = MovieLibrary.parsePluto(PLUTO_ITEMS,
                MovieLibrary.Shelf.pluto("drama", 0, "58c49d9d250dc501634e63d4", 200, true, false));
        assertEquals(3, movies.size());
        Movie mystery = movies.get(0), gucci = movies.get(1), undated = movies.get(2);
        assertEquals("2023", mystery.year);
        assertEquals("NR", mystery.rating);
        assertEquals("https://images.pluto.tv/w.jpg?fill=blur&fit=fill&fm=jpg&q=75&w=480&h=270", mystery.image);
        assertTrue(gucci.isPluto());
        assertEquals("2021", gucci.year);
        assertEquals(158, gucci.minutes);
        assertEquals("R", gucci.rating);
        assertEquals("Family & fashion", gucci.description);
        assertEquals("https://images.pluto.tv/episodes/g/poster16_9.jpg?fill=blur&fit=fill&fm=jpg&q=75&w=480&h=270",
                gucci.image);
        assertEquals("", undated.year);
        assertEquals("Only Pluto's own image host", "", undated.image);

        List<Movie> inOrder = MovieLibrary.parsePluto(PLUTO_ITEMS,
                MovieLibrary.Shelf.pluto("recent", 0, "65e203ca66eec80008a0641a", 60, false, false));
        assertEquals("House of Gucci", inOrder.get(0).title);

        List<Movie> late = MovieLibrary.parsePluto(PLUTO_ITEMS,
                MovieLibrary.Shelf.pluto("late", 0, "5bbe7c9d74fb2e5d05a92c10", 150, true, true));
        assertEquals(4, late.size());
        assertEquals("NC-17", late.get(2).rating);
        assertTrue(late.get(2).mature);
        assertTrue(MovieLibrary.parsePluto("not json",
                MovieLibrary.Shelf.pluto("x", 0, "c", 1, false, false)).isEmpty());
    }

    @Test public void plutoFilmsSurviveTheResumeList() {
        Movie film = new Movie(Movie.PLUTO, "5f0000000000000000000003", "House of Gucci", "2021", 158, "Family",
                false, "R", "Drama", "https://images.pluto.tv/g.jpg");
        Movie back = MovieLibrary.fromJson(MovieLibrary.toJson(film).toString());
        assertTrue(back.isPluto());
        assertEquals("R", back.rating);
        assertEquals("Drama", back.genre);
        assertEquals("https://images.pluto.tv/g.jpg", back.image);
        Movie old = MovieLibrary.fromJson("{\"id\":\"his_girl_friday\",\"title\":\"His Girl Friday\",\"image\":\"http://x/y.jpg\"}");
        assertFalse("Entries from before Pluto are archive films", old.isPluto());
        assertEquals("", old.image);
    }
}
