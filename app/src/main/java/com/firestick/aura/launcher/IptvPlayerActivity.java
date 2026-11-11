package com.firestick.aura.launcher;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.AspectRatioFrameLayout;
import com.firestick.aura.launcher.manager.*;
import com.firestick.aura.launcher.model.IptvChannel;
import com.firestick.aura.launcher.ui.Glass;
import com.firestick.aura.launcher.ui.GlassMenu;
import com.firestick.aura.launcher.ui.LockPad;
import java.util.*;

/** Foreground live player with feed fallback and remote controls. */
@SuppressLint("UnsafeOptInUsageError")
public final class IptvPlayerActivity extends AppCompatActivity {
    public static final String EXTRA_CHANNEL_NAME = "iptv.channel_name";
    public static final String EXTRA_COUNTRY = "iptv.country";
    public static final String EXTRA_STREAM_URL = "iptv.stream_url";
    private PlayerView playerView;
    private View errorPanel, header;
    private TextView channelTitle, channelCountry;
    private ExoPlayer player;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private List<IptvChannel> channels = new ArrayList<>();
    private int channelIndex, feedIndex;
    private boolean recorded, controlsShowing;
    private EpgRepository guide;
    private PreferenceManager preferences;
    private StreamHealth health;
    private List<String> orderedFeeds = Collections.emptyList();
    private String observedUrl;
    private long startedAt, firstFrameAt, playingSince, playedMs, bufferingSince, bufferedMs;
    private final Runnable stalled = this::nextFeed;

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_iptv_player);
        preferences = new PreferenceManager(this);
        health = new StreamHealth(this);
        guide = new EpgRepository(this);
        channels = PlaybackSession.read(this);
        channelIndex = state == null ? getIntent().getIntExtra("queue_index", 0) : state.getInt("channel_index");
        long channelId = state == null ? getIntent().getLongExtra("channel_id", Long.MIN_VALUE)
                : state.getLong("channel_id", Long.MIN_VALUE);
        for (int i = 0; i < channels.size(); i++) if (channels.get(i).id == channelId) { channelIndex = i; break; }
        channelIndex = Math.max(0, Math.min(channelIndex, channels.size() - 1));
        if (channels.isEmpty() && getIntent().getStringExtra(EXTRA_STREAM_URL) != null) {
            channels.add(new IptvChannel(0, getIntent().getStringExtra(EXTRA_CHANNEL_NAME), "", "",
                    Collections.emptyList(), "", "", getIntent().getStringExtra(EXTRA_STREAM_URL),
                    false, "", "", "", ""));
        }
        playerView = findViewById(R.id.iptvPlayerView);
        errorPanel = findViewById(R.id.iptvPlayerError);
        header = findViewById(R.id.iptvPlayerHeader);
        channelTitle = findViewById(R.id.iptvPlayerChannelTitle);
        channelCountry = findViewById(R.id.iptvPlayerCountry);
        findViewById(R.id.iptvPlayerRetry).setOnClickListener(v -> {
            if (channels.isEmpty()) return;
            finishObservation(false);
            orderedFeeds = health.rank(current().streamUrls);
            feedIndex = 0;
            preparePlayback();
        });
        ((Button) findViewById(R.id.playerPrevious)).setOnClickListener(v -> switchChannel(-1));
        ((Button) findViewById(R.id.playerNext)).setOnClickListener(v -> switchChannel(1));
        findViewById(R.id.playerAudio).setOnClickListener(v -> chooseTrack(C.TRACK_TYPE_AUDIO));
        findViewById(R.id.playerSubtitles).setOnClickListener(v -> chooseTrack(C.TRACK_TYPE_TEXT));
        findViewById(R.id.playerPicture).setOnClickListener(v -> pictureSize());
        findViewById(R.id.playerGuide).setOnClickListener(v -> showGuide());
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                backFromPlayback();
            }
        });
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            controlsShowing = visibility == View.VISIBLE;
            header.setVisibility(visibility);
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("channel_index", channelIndex);
        if (!channels.isEmpty()) state.putLong("channel_id", current().id);
        super.onSaveInstanceState(state);
    }

    @Override protected void onStart() {
        super.onStart();
        hideSystemBars();
        if (channels.isEmpty()) { showPlaybackError(); return; }
        androidx.media3.datasource.DefaultHttpDataSource.Factory http = new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(8000).setReadTimeoutMs(12000);
        player = new ExoPlayer.Builder(this).setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                new androidx.media3.datasource.DefaultDataSource.Factory(this, http))).build();
        orderedFeeds = health.rank(current().streamUrls);
        feedIndex = 0;
        playerView.setPlayer(player);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setControllerShowTimeoutMs(5000);
        playerView.setControllerAutoShow(false);
        playerView.setControllerAnimationEnabled(!preferences.reducedMotion());
        playerView.hideController();
        controlsShowing = false;
        header.setVisibility(View.GONE);
        playerView.requestFocus();
        player.addListener(new Player.Listener() {
            @Override public void onRenderedFirstFrame() {
                if (observedUrl != null && firstFrameAt == 0) firstFrameAt = android.os.SystemClock.elapsedRealtime();
            }
            @Override public void onIsPlayingChanged(boolean playing) {
                long now = android.os.SystemClock.elapsedRealtime();
                if (playingSince > 0) playedMs += now - playingSince;
                playingSince = playing && observedUrl != null ? now : 0;
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (player == null || player.getPlaybackState() != state) return;
                long now = android.os.SystemClock.elapsedRealtime();
                if (bufferingSince > 0) bufferedMs += now - bufferingSince;
                bufferingSince = state == Player.STATE_BUFFERING && observedUrl != null ? now : 0;
                handler.removeCallbacks(stalled);
                if (state == Player.STATE_BUFFERING) handler.postDelayed(stalled, 18000);
                if (state == Player.STATE_READY) {
                    if (observedUrl != null && firstFrameAt == 0) firstFrameAt = now;
                    errorPanel.setVisibility(View.GONE);
                    if (!recorded) { preferences.recordChannel(current()); recorded = true; }
                }
                if (state == Player.STATE_ENDED) nextFeed();
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (player != null && player.getPlayerError() == error) nextFeed();
            }
        });
        preparePlayback();
    }

    @Override protected void onStop() {
        finishObservation(false);
        handler.removeCallbacksAndMessages(null);
        if (!channels.isEmpty()) PlaybackSession.persistWindow(this, channels, current());
        if (player != null) { playerView.setPlayer(null); player.release(); player = null; }
        super.onStop();
    }

    @Override protected void onDestroy() { guide.shutdown(); super.onDestroy(); }

    private IptvChannel current() { return channels.get(channelIndex); }

    private void preparePlayback() {
        if (player == null || channels.isEmpty()) return;
        errorPanel.setVisibility(View.GONE);
        channelTitle.setText(current().name);
        channelCountry.setText(current().country + " · " + (feedIndex + 1) + "/" + current().streamUrls.size());
        String url = orderedFeeds.get(feedIndex);
        player.stop();
        observedUrl = url;
        startedAt = android.os.SystemClock.elapsedRealtime();
        firstFrameAt = playingSince = playedMs = bufferingSince = bufferedMs = 0;
        MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(url));
        if (url.toLowerCase(Locale.ROOT).contains(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        androidx.media3.datasource.DefaultHttpDataSource.Factory http = new androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(8000).setReadTimeoutMs(12000)
                .setDefaultRequestProperties(current().streamHeaders.getOrDefault(url, Collections.emptyMap()));
        androidx.media3.exoplayer.source.MediaSource source;
        try {
            source = new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    new androidx.media3.datasource.DefaultDataSource.Factory(this, http)).createMediaSource(item.build());
        } catch (RuntimeException unsupportedFeed) {
            handler.post(this::nextFeed);
            return;
        }
        player.setMediaSource(source);
        player.prepare();
        player.play();
        handler.removeCallbacks(stalled);
        handler.postDelayed(stalled, 18000);
    }

    private void nextFeed() {
        handler.removeCallbacks(stalled);
        if (player == null || channels.isEmpty()) return;
        finishObservation(true);
        if (++feedIndex < orderedFeeds.size()) preparePlayback();
        else { feedIndex = 0; player.stop(); showPlaybackError(); }
    }

    private void finishObservation(boolean failed) {
        if (observedUrl == null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (playingSince > 0) playedMs += now - playingSince;
        if (bufferingSince > 0) bufferedMs += now - bufferingSince;
        health.record(observedUrl, failed, firstFrameAt == 0 ? now - startedAt : firstFrameAt - startedAt,
                playedMs, bufferedMs);
        observedUrl = null;
        playingSince = bufferingSince = 0;
    }

    private void switchChannel(int delta) {
        if (channels.size() < 2) return;
        int next = Math.floorMod(channelIndex + delta, channels.size());
        Runnable play = () -> { finishObservation(false); channelIndex = next; orderedFeeds = health.rank(current().streamUrls); feedIndex = 0; recorded = false; preparePlayback(); };
        if (channels.get(next).isAdult()) LockPad.verify(this, preferences, getString(R.string.iptv_adults_unlock), play::run);
        else play.run();
    }

    private void chooseTrack(int type) {
        if (player == null) return;
        List<GlassMenu.Item> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        items.add(new GlassMenu.Item(R.drawable.ic_tv, getString(R.string.track_auto)));
        actions.add(() -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(type).setTrackTypeDisabled(type, false).build()));
        if (type == C.TRACK_TYPE_TEXT) {
            items.add(new GlassMenu.Item(R.drawable.ic_tv, getString(R.string.track_off)));
            actions.add(() -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .clearOverridesOfType(type).setTrackTypeDisabled(type, true).build()));
        }
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                final int index = i;
                Format format = group.getTrackFormat(i);
                String label = format.label != null ? format.label : format.language != null ? format.language : "Track " + (i + 1);
                items.add(new GlassMenu.Item(R.drawable.ic_tv, label).checked(group.isTrackSelected(i)));
                actions.add(() -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(type, false).setOverrideForType(
                                new TrackSelectionOverride(group.getMediaTrackGroup(), index)).build()));
            }
        }
        GlassMenu.show(this, getString(type == C.TRACK_TYPE_AUDIO ? R.string.player_audio : R.string.player_subtitles),
                null, null, items, false, (index, item) -> { if (player != null) actions.get(index).run(); });
    }

    private void pictureSize() {
        int[] modes = {AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                AspectRatioFrameLayout.RESIZE_MODE_FILL};
        List<GlassMenu.Item> items = Arrays.asList(
                new GlassMenu.Item(R.drawable.ic_tv, getString(R.string.picture_fit)),
                new GlassMenu.Item(R.drawable.ic_tv, getString(R.string.picture_zoom)),
                new GlassMenu.Item(R.drawable.ic_tv, getString(R.string.picture_fill)));
        GlassMenu.show(this, getString(R.string.player_picture), null, null, items, false,
                (index, item) -> playerView.setResizeMode(modes[index]));
    }

    private void showGuide() {
        if (channels.isEmpty()) return;
        String id = current().channelId;
        android.app.Dialog dialog = Glass.info(this, current().name, getString(R.string.guide_loading), null, null, null);
        guide.load(id, text -> {
            if (!isDestroyed() && dialog.isShowing()) ((TextView) dialog.findViewById(R.id.infoMessage))
                    .setText(text.isEmpty() ? getString(R.string.guide_unavailable) : text);
        });
    }

    private void showPlaybackError() {
        errorPanel.setVisibility(View.VISIBLE);
        header.setVisibility(View.VISIBLE);
        errorPanel.findViewById(R.id.iptvPlayerRetry).requestFocus();
    }

    private void backFromPlayback() {
        if (controlsShowing || header.getVisibility() == View.VISIBLE) {
            playerView.hideController();
            controlsShowing = false;
            header.setVisibility(View.GONE);
            playerView.requestFocus();
        } else finish();
    }

    private void seekFromRemote(int direction) {
        if (player == null) return;
        if (player.isCurrentMediaItemSeekable()) {
            long target = Math.max(0L, player.getCurrentPosition() + direction * 10_000L);
            long duration = player.getDuration();
            if (duration != C.TIME_UNSET && duration >= 0) target = Math.min(target, duration);
            player.seekTo(target);
        }
        playerView.showController();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) backFromPlayback();
            return true;
        }
        boolean mediaSeek = code == KeyEvent.KEYCODE_MEDIA_REWIND || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
        boolean arrowSeek = !controlsShowing && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT);
        if (mediaSeek || arrowSeek) {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (mediaSeek || event.getRepeatCount() == 0)) {
                boolean forward = code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || code == KeyEvent.KEYCODE_DPAD_RIGHT;
                seekFromRemote(forward ? 1 : -1);
            }
            return true;
        }
        boolean channelKey = code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN;
        boolean playbackArrow = (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)
                && (!controlsShowing || errorPanel.getVisibility() == View.VISIBLE);
        if (channelKey || playbackArrow) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0)
                switchChannel(code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1);
            return true;
        }
        return super.dispatchKeyEvent(event);
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
