package com.firestick.aura.launcher;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import com.firestick.aura.launcher.manager.MovieLibrary;
import com.firestick.aura.launcher.manager.PlutoSession;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.model.Movie;
import com.firestick.aura.launcher.ui.Glass;
import com.firestick.aura.launcher.ui.RatingBadge;
import com.firestick.aura.launcher.ui.SubtitleStyle;

/**
 * Plays one film from the Movies tab and remembers where it stopped. With the controls hidden,
 * Left and Right seek without opening them, and OK pauses. Menu opens the subtitle style.
 */
@SuppressLint("UnsafeOptInUsageError")
public final class MoviePlayerActivity extends AppCompatActivity {
    public static final String EXTRA_MOVIE = "com.firestick.aura.launcher.extra.MOVIE";
    public static final String EXTRA_URL = "com.firestick.aura.launcher.extra.MOVIE_URL";
    private static final String STATE_POSITION = "position";
    private static final long STEP_MS = 10_000L, LONG_STEP_MS = 30_000L, PILL_MS = 1500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PlayerView playerView;
    private View header, errorPanel;
    private TextView seekPill;
    private final Runnable hidePill = () -> seekPill.setVisibility(View.GONE);
    private ExoPlayer player;
    private PreferenceManager preferences;
    private SharedPreferences store;
    private Movie movie;
    private String url;
    /** Where the next start begins; unset means the saved resume point. */
    private long position = C.TIME_UNSET;
    private boolean controlsShowing;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        Glass.applyTheme(this);
        movie = MovieLibrary.fromJson(getIntent().getStringExtra(EXTRA_MOVIE));
        url = getIntent().getStringExtra(EXTRA_URL);
        // Only archive.org files and Pluto TV streams: the intent is not exported, but the URL is still checked.
        if (movie == null || url == null || !(url.startsWith("https://archive.org/")
                || (movie.isPluto() && PlutoSession.isPluto(url)))) {
            finish();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_movie_player);
        preferences = new PreferenceManager(this);
        store = getSharedPreferences(MovieLibrary.PREFS, MODE_PRIVATE);
        if (state != null) position = state.getLong(STATE_POSITION, C.TIME_UNSET);
        playerView = findViewById(R.id.moviePlayerView);
        header = findViewById(R.id.moviePlayerHeader);
        errorPanel = findViewById(R.id.moviePlayerError);
        seekPill = findViewById(R.id.moviePlayerSeek);
        ((TextView) findViewById(R.id.moviePlayerTitle)).setText(movie.title);
        String meta = movie.minutes > 0 ? getString(R.string.movies_minutes, movie.minutes) : "";
        meta = movie.year.isEmpty() ? meta : meta.isEmpty() ? movie.year : getString(R.string.show_channel_time, movie.year, meta);
        ((TextView) findViewById(R.id.moviePlayerMeta)).setText(RatingBadge.append(this, meta, movie.rating));
        findViewById(R.id.moviePlayerRetry).setOnClickListener(v -> {
            if (player == null) return;
            errorPanel.setVisibility(View.GONE);
            header.setVisibility(controlsShowing ? View.VISIBLE : View.GONE);
            playerView.requestFocus();
            player.prepare();
            player.play();
        });
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            controlsShowing = visibility == View.VISIBLE;
            if (errorPanel.getVisibility() != View.VISIBLE) header.setVisibility(visibility);
        });
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (controlsShowing) playerView.hideController();
                else finish();
            }
        });
    }

    @Override protected void onStart() {
        super.onStart();
        if (movie == null) return;
        hideSystemBars();
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(10_000).setReadTimeoutMs(20_000);
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(
                new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http))).build();
        playerView.setPlayer(player);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setControllerShowTimeoutMs(4000);
        playerView.setControllerAnimationEnabled(!preferences.reducedMotion());
        // Pluto films carry English subtitles; the button stays off for files without any.
        playerView.setShowSubtitleButton(true);
        SubtitleStyle.apply(playerView, preferences);
        playerView.requestFocus();
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) errorPanel.setVisibility(View.GONE);
                if (state != Player.STATE_ENDED) return;
                MovieLibrary.remember(store, movie, 0, 0);
                finish();
            }

            @Override public void onPlayerError(PlaybackException error) {
                playerView.hideController();
                header.setVisibility(View.VISIBLE);
                errorPanel.setVisibility(View.VISIBLE);
                findViewById(R.id.moviePlayerRetry).requestFocus();
            }
        });
        long start = position != C.TIME_UNSET ? position : MovieLibrary.resumeAt(store, movie.id);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)), Math.max(0L, start));
        player.prepare();
        player.play();
    }

    /** Saved here, not in onStop: the launcher resumes and reads the list before this activity stops. */
    @Override protected void onPause() {
        if (player != null && player.getPlaybackState() != Player.STATE_ENDED) {
            position = player.getCurrentPosition();
            long duration = player.getDuration();
            MovieLibrary.remember(store, movie, position, duration == C.TIME_UNSET ? 0 : duration);
        }
        super.onPause();
    }

    @Override protected void onStop() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            position = player.getCurrentPosition();
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong(STATE_POSITION, player != null ? player.getCurrentPosition() : position);
        super.onSaveInstanceState(state);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (player == null || errorPanel.getVisibility() == View.VISIBLE) return super.dispatchKeyEvent(event);
        boolean mediaSeek = code == KeyEvent.KEYCODE_MEDIA_REWIND || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
        boolean arrowSeek = !controlsShowing && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT);
        if (mediaSeek || arrowSeek) {
            if (down) {
                boolean forward = code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || code == KeyEvent.KEYCODE_DPAD_RIGHT;
                seek(forward, event.getRepeatCount() > 5 ? LONG_STEP_MS : STEP_MS);
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            if (down && event.getRepeatCount() == 0) SubtitleStyle.show(this, preferences, playerView);
            return true;
        }
        boolean select = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (select && !controlsShowing) {
            if (down && event.getRepeatCount() == 0) {
                if (player.isPlaying()) player.pause();
                else player.play();
                playerView.showController();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void seek(boolean forward, long step) {
        if (!player.isCurrentMediaItemSeekable()) return;
        long duration = player.getDuration();
        long target = Math.max(0L, player.getCurrentPosition() + (forward ? step : -step));
        if (duration != C.TIME_UNSET) target = Math.min(target, Math.max(0L, duration - 1000L));
        player.seekTo(target);
        String at = DateUtils.formatElapsedTime(target / 1000L);
        String total = duration == C.TIME_UNSET ? "" : DateUtils.formatElapsedTime(duration / 1000L);
        seekPill.setText(getString(forward ? R.string.movies_seek_forward : R.string.movies_seek_back,
                step / 1000L, total.isEmpty() ? at : getString(R.string.movies_seek_position, at, total)));
        seekPill.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hidePill);
        handler.postDelayed(hidePill, PILL_MS);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
