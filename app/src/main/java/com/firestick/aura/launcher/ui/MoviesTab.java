package com.firestick.aura.launcher.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firestick.aura.launcher.MoviePlayerActivity;
import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.adapter.MovieAdapter;
import com.firestick.aura.launcher.manager.MovieLibrary;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.SoundManager;
import com.firestick.aura.launcher.model.Movie;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * The Movies tab: a details header over shelves of free films, from Pluto TV On Demand or, where
 * Pluto TV is out of reach, public-domain films from the Internet Archive. Shelves load the first
 * time the tab opens and stay hidden until they have films. The 18+ shelf waits for the lock key and
 * locks again when the launcher is left, after a time, or when the screen goes off, as Settings
 * chooses. Films watched to the end, or removed with a long press of OK, leave every shelf.
 */
public final class MoviesTab {
    private static final class Section {
        final MovieLibrary.Shelf shelf;
        final String name;
        final LinearLayout root;
        final RecyclerView list;
        final MovieAdapter adapter;
        TextView pill;
        /** Everything the shelf loaded; the screen shows it without the hidden films. */
        List<Movie> movies = Collections.emptyList();
        Section(MovieLibrary.Shelf shelf, String name, LinearLayout root, RecyclerView list, MovieAdapter adapter) {
            this.shelf = shelf;
            this.name = name;
            this.root = root;
            this.list = list;
            this.adapter = adapter;
        }
    }

    private final Activity activity;
    private final PreferenceManager preferences;
    private final SharedPreferences store;
    private final MovieLibrary library;
    private final ScrollView scroll;
    private final LinearLayout shelves;
    private final TextView eyebrow, title, meta, synopsis, status;
    private final RecyclerView.RecycledViewPool pool = new RecyclerView.RecycledViewPool();
    private final List<Section> sections = new ArrayList<>();
    private Section resume, mature;
    private TextView credit;
    private Map<String, long[]> progress = new HashMap<>();
    private String resumePrint = "";
    private Dialog opening;
    private String describedShelf;
    private Movie describedMovie;
    /** Pluto TV first; the archive's shelves when no Pluto shelf loads. */
    private List<MovieLibrary.Shelf> source = MovieLibrary.PLUTO_SHELVES;
    private boolean built, loaded, unlocked, released, described, describedMature;
    private int generation, pending, openRequest;
    private long unlockedAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable expire = this::relock;
    private BroadcastReceiver screenOffReceiver;

    public MoviesTab(Activity activity, PreferenceManager preferences) {
        this.activity = activity;
        this.preferences = preferences;
        store = activity.getSharedPreferences(MovieLibrary.PREFS, Context.MODE_PRIVATE);
        library = new MovieLibrary(activity);
        scroll = activity.findViewById(R.id.movieScroll);
        shelves = activity.findViewById(R.id.movieShelves);
        eyebrow = activity.findViewById(R.id.movieEyebrow);
        title = activity.findViewById(R.id.movieTitle);
        meta = activity.findViewById(R.id.movieMeta);
        synopsis = activity.findViewById(R.id.movieSynopsis);
        status = activity.findViewById(R.id.txtMoviesStatus);
    }

    /** Fills the tab the first time and refreshes Continue watching after each film. */
    public void show() {
        if (released) return;
        if (unlocked && expired()) relock();
        if (!built) build();
        refreshResume();
        if (!described) intro();
    }

    public void scrollToTop() {
        scroll.smoothScrollTo(0, 0);
    }

    /** The shelf in focus moves to the top of the list, so its heading always shows above it. */
    private void align(View section) {
        int y = section.getTop();
        if (scroll.getScrollY() == y) return;
        if (preferences.reducedMotion()) scroll.scrollTo(0, y);
        else scroll.smoothScrollTo(0, y);
    }

    /**
     * Called from onStop: drops a film still being looked up. The 18+ shelf locks again unless
     * Settings keeps it open for a time, or until the device sleeps.
     */
    public void stop() {
        openRequest++;
        if (opening != null && opening.isShowing()) opening.dismiss();
        if (!unlocked) return;
        if (preferences.moviesSession() == 0 || expired()) relock();
    }

    private boolean expired() {
        int minutes = preferences.moviesSession();
        return minutes > 0 && SystemClock.elapsedRealtime() - unlockedAt >= minutes * 60_000L;
    }

    /** Hides the 18+ shelf again. */
    private void relock() {
        handler.removeCallbacks(expire);
        unregisterScreenOff();
        if (!unlocked) return;
        boolean hadFocus = mature != null && mature.list.hasFocus();
        unlocked = false;
        if (mature != null) {
            mature.movies = Collections.emptyList();
            mature.adapter.setItems(Collections.emptyList(), null);
            mature.list.setVisibility(View.GONE);
            mature.pill.setText(R.string.movies_unlock_pill);
            mature.pill.setVisibility(View.VISIBLE);
        }
        if (describedMature) intro();
        refreshResume();
        if (hadFocus) mature.pill.requestFocus();
    }

    private void startSession() {
        unlockedAt = SystemClock.elapsedRealtime();
        handler.removeCallbacks(expire);
        int minutes = preferences.moviesSession();
        if (minutes > 0) handler.postDelayed(expire, minutes * 60_000L);
        if (screenOffReceiver != null) return;
        // Whatever the session setting, the screen going off locks the shelf again.
        screenOffReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                relock();
            }
        };
        ContextCompat.registerReceiver(activity, screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterScreenOff() {
        if (screenOffReceiver == null) return;
        try {
            activity.unregisterReceiver(screenOffReceiver);
        } catch (IllegalArgumentException alreadyGone) {
            // Nothing left to unregister.
        }
        screenOffReceiver = null;
    }

    public void release() {
        released = true;
        handler.removeCallbacks(expire);
        unregisterScreenOff();
        openRequest++;
        if (opening != null && opening.isShowing()) opening.dismiss();
        library.shutdown();
    }

    // ------------------------------------------------------------------ shelves

    private void build() {
        built = true;
        loaded = false;
        int request = ++generation;
        shelves.removeAllViews();
        sections.clear();
        resume = section(null, activity.getString(R.string.movies_continue));
        List<Section> load = new ArrayList<>();
        mature = null;
        for (MovieLibrary.Shelf shelf : source) {
            Section section = section(shelf, activity.getString(shelf.title));
            if (shelf.mature) {
                mature = section;
                addPill(section);
            } else {
                load.add(section);
            }
        }
        credit = new TextView(activity);
        credit.setText(source == MovieLibrary.PLUTO_SHELVES ? R.string.movies_attribution_pluto
                : R.string.movies_attribution);
        credit.setTextColor(ContextCompat.getColor(activity, R.color.nova_text_muted));
        credit.setTextSize(9);
        credit.setPadding(dp(40), dp(10), dp(40), dp(6));
        credit.setVisibility(View.GONE);
        shelves.addView(credit);
        setStatus(R.string.movies_loading);
        // Every view exists first: a shelf already in memory answers at once.
        pending = load.size();
        for (Section section : load) library.load(section.shelf, movies -> filled(request, section, movies));
    }

    private void filled(int request, Section section, List<Movie> movies) {
        if (released || request != generation) return;
        pending--;
        if (!movies.isEmpty()) {
            section.movies = movies;
            shelf(section, MovieLibrary.hidden(store));
            if (!loaded) {
                loaded = true;
                status.setVisibility(View.GONE);
                credit.setVisibility(View.VISIBLE);
                mature.root.setVisibility(View.VISIBLE);
                resumePrint = "";
                refreshResume();
            }
        }
        if (pending == 0 && !loaded) {
            if (source == MovieLibrary.PLUTO_SHELVES) {
                // Pluto TV is out of reach or does not serve this region: show the archive's films instead.
                source = MovieLibrary.ARCHIVE_SHELVES;
                build();
                return;
            }
            setStatus(R.string.movies_offline);
            // Opening the tab again tries again, Pluto TV first.
            source = MovieLibrary.PLUTO_SHELVES;
            built = false;
        }
    }

    private Section section(MovieLibrary.Shelf shelf, String name) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setVisibility(View.GONE);
        TextView heading = new TextView(activity);
        heading.setText(name);
        heading.setTextSize(14);
        heading.setTextColor(ContextCompat.getColor(activity, R.color.nova_text));
        heading.setPadding(dp(40), dp(8), dp(40), dp(2));
        root.addView(heading);
        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false));
        list.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(146)));
        list.setPadding(dp(32), dp(2), dp(32), dp(2));
        list.setClipToPadding(false);
        list.setNestedScrollingEnabled(false);
        list.setItemAnimator(null);
        list.setRecycledViewPool(pool);
        Section[] self = new Section[1];
        MovieAdapter adapter = new MovieAdapter(preferences, new MovieAdapter.Listener() {
            @Override public void onFocus(Movie movie) {
                describe(self[0].name, movie);
                align(root);
            }

            @Override public void onPlay(Movie movie) {
                play(movie);
            }

            @Override public void onRemove(Movie movie) {
                Glass.info(activity, movie.title, activity.getString(R.string.movies_remove_body), null,
                        activity.getString(R.string.movies_remove), () -> remove(self[0], movie));
            }
        });
        list.setAdapter(adapter);
        root.addView(list);
        shelves.addView(root);
        self[0] = new Section(shelf, name, root, list, adapter);
        sections.add(self[0]);
        return self[0];
    }

    private void addPill(Section section) {
        section.list.setVisibility(View.GONE);
        TextView pill = new TextView(activity, null, 0, R.style.Lumora_Eyebrow);
        pill.setText(R.string.movies_unlock_pill);
        pill.setTextColor(ContextCompat.getColor(activity, R.color.nova_text));
        pill.setBackgroundResource(R.drawable.bg_glass_pill);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(16), 0, dp(16), 0);
        pill.setFocusable(true);
        pill.setClickable(true);
        pill.setOnFocusChangeListener((view, focused) -> {
            view.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f)
                    .setDuration(preferences.reducedMotion() ? 0 : 120).start();
            if (!focused) return;
            SoundManager.playFocus(view, preferences);
            align(section.root);
            described = true;
            describedMature = false;
            describedMovie = null;
            eyebrow.setText(section.name);
            title.setText(R.string.movies_mature_title);
            meta.setText(R.string.movies_mature_meta);
            synopsis.setText(R.string.movies_mature_body);
        });
        pill.setOnClickListener(view -> {
            SoundManager.playClick(view, preferences);
            LockPad.verify(activity, preferences, activity.getString(R.string.movies_unlock), this::unlock);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
        params.setMarginStart(dp(40));
        params.topMargin = dp(6);
        params.bottomMargin = dp(10);
        section.root.addView(pill, params);
        section.pill = pill;
    }

    private void unlock() {
        if (released || mature == null) return;
        unlocked = true;
        startSession();
        refreshResume();
        Section section = mature;
        int request = generation;
        section.pill.setText(R.string.movies_loading);
        library.load(section.shelf, movies -> {
            if (released || request != generation || !unlocked) return;
            if (movies.isEmpty()) {
                section.pill.setText(R.string.movies_shelf_failed);
                return;
            }
            section.movies = movies;
            List<Movie> shown = MovieLibrary.visible(movies, MovieLibrary.hidden(store));
            if (shown.isEmpty()) {
                section.pill.setText(R.string.movies_shelf_cleared);
                return;
            }
            section.adapter.setItems(shown, progress);
            section.list.setVisibility(View.VISIBLE);
            // Hide the pill only once a film can take its focus, so focus never falls out of the tab.
            section.list.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    section.list.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    View first = section.list.getChildAt(0);
                    if (first != null && (section.pill.isFocused() || activity.getCurrentFocus() == null)) {
                        first.requestFocus();
                    }
                    section.pill.setVisibility(View.GONE);
                }
            });
        });
    }

    private void refreshResume() {
        if (resume == null) return;
        Set<String> hidden = MovieLibrary.hidden(store);
        String print = MovieLibrary.historyText(store) + hidden + unlocked + loaded;
        if (print.equals(resumePrint)) return;
        resumePrint = print;
        Map<String, long[]> next = new HashMap<>();
        List<Movie> movies = new ArrayList<>();
        for (MovieLibrary.Resume entry : MovieLibrary.history(store)) {
            if (entry.movie.mature && !unlocked) continue;
            movies.add(entry.movie);
            next.put(entry.movie.id, new long[]{entry.position, entry.duration});
        }
        progress = next;
        resume.adapter.setItems(movies, next);
        resume.root.setVisibility(loaded && !movies.isEmpty() ? View.VISIBLE : View.GONE);
        for (Section section : sections) if (section != resume) shelf(section, hidden);
        // The header shows the time left too; after a film it is out of date.
        if (describedMovie != null) describe(describedShelf, describedMovie);
    }

    /** Shows a shelf's films without the hidden and finished ones, each with how far it was watched. */
    private void shelf(Section section, Set<String> hidden) {
        if (section.movies.isEmpty() || section == mature && !unlocked) return;
        List<Movie> shown = MovieLibrary.visible(section.movies, hidden);
        section.adapter.setItems(shown, progress);
        if (section == mature) {
            section.list.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
            section.pill.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
            if (shown.isEmpty()) section.pill.setText(R.string.movies_shelf_cleared);
        } else {
            section.root.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void remove(Section section, Movie movie) {
        int at = Math.max(0, section.adapter.positionOf(movie.id));
        if (describedMovie != null && describedMovie.id.equals(movie.id)) intro();
        MovieLibrary.hide(store, movie);
        refreshResume();
        // The next film takes focus, or the next shelf when this one is now empty.
        section.list.post(() -> {
            if (released) return;
            if (section.adapter.getItemCount() > 0) {
                focusAt(section.list, Math.min(at, section.adapter.getItemCount() - 1));
                return;
            }
            for (Section other : sections) {
                if (other.root.getVisibility() == View.VISIBLE && other.list.getVisibility() == View.VISIBLE
                        && other.adapter.getItemCount() > 0) {
                    focusAt(other.list, 0);
                    return;
                }
            }
            if (section == mature) mature.pill.requestFocus();
        });
    }

    private void focusAt(RecyclerView list, int index) {
        list.scrollToPosition(index);
        list.post(() -> {
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(index);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    // ------------------------------------------------------------------ header

    private void intro() {
        described = false;
        describedMature = false;
        describedMovie = null;
        eyebrow.setText(R.string.nav_movies_tab);
        title.setText(R.string.movies_intro_title);
        meta.setText(R.string.movies_intro_meta);
        synopsis.setText(R.string.movies_intro_body);
    }

    private void describe(String shelf, Movie movie) {
        described = true;
        describedMature = movie.mature;
        describedShelf = shelf;
        describedMovie = movie;
        eyebrow.setText(shelf);
        title.setText(movie.title);
        List<String> parts = new ArrayList<>();
        if (!movie.year.isEmpty()) parts.add(movie.year);
        if (movie.minutes > 0) parts.add(activity.getString(R.string.movies_minutes, movie.minutes));
        if (!movie.genre.isEmpty()) parts.add(movie.genre);
        long[] at = progress.get(movie.id);
        if (at != null && at[1] > 0) {
            parts.add(activity.getString(R.string.movies_left, (int) Math.max(1L, (at[1] - at[0]) / 60_000L)));
        }
        meta.setText(RatingBadge.append(activity, TextUtils.join(" · ", parts), movie.rating));
        synopsis.setText(movie.description.isEmpty() ? activity.getString(R.string.movies_no_description)
                : movie.description);
    }

    private void setStatus(int text) {
        status.setText(text);
        status.setVisibility(View.VISIBLE);
    }

    // ------------------------------------------------------------------ playback

    private void play(Movie movie) {
        if (movie.mature && !unlocked) {
            LockPad.verify(activity, preferences, activity.getString(R.string.movies_unlock), () -> open(movie));
        } else {
            open(movie);
        }
    }

    private void open(Movie movie) {
        int request = ++openRequest;
        Dialog dialog = Glass.info(activity, movie.title, activity.getString(R.string.movies_opening), null, null, null);
        opening = dialog;
        library.resolve(movie, url -> {
            // Closing the dialog cancels the film.
            if (released || request != openRequest || !dialog.isShowing()) return;
            dialog.dismiss();
            if (url == null) {
                Glass.info(activity, movie.title, activity.getString(R.string.movies_unavailable), null, null, null);
                return;
            }
            activity.startActivity(new Intent(activity, MoviePlayerActivity.class)
                    .putExtra(MoviePlayerActivity.EXTRA_MOVIE, MovieLibrary.toJson(movie).toString())
                    .putExtra(MoviePlayerActivity.EXTRA_URL, url));
        });
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
