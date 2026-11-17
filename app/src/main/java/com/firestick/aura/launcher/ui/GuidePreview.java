package com.firestick.aura.launcher.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;

import com.firestick.aura.launcher.R;
import com.firestick.aura.launcher.manager.FastGuide;
import com.firestick.aura.launcher.manager.PreferenceManager;
import com.firestick.aura.launcher.manager.RemoteImageLoader;
import com.firestick.aura.launcher.manager.StreamHealth;
import com.firestick.aura.launcher.model.IptvChannel;

import java.util.Collections;
import java.util.List;

/**
 * The Live TV preview window. It shows the focused channel's art at once and, after focus has
 * rested for a second, a muted stream of at most 480p. One player is reused; it is released
 * whenever the guide is not on screen so the full-screen player gets the decoder.
 */
@SuppressLint("UnsafeOptInUsageError")
public final class GuidePreview {
    private static final long ART_REST_MS = 150L;
    private static final long PLAY_REST_MS = 1000L;
    private static final long REVEAL_MS = 180L;
    private static final int WIDTH_DP = 242;
    private static final int LOGO_PADDING_DP = 28;

    private final Context context;
    private final PreferenceManager preferences;
    private final PlayerView playerView;
    private final ImageView art;
    private final TextView initials;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable loadArt = this::loadArt;
    private final Runnable autoplay = this::start;
    private ExoPlayer player;
    private IptvChannel channel;
    private boolean allowed, started, failed;

    public GuidePreview(View frame, PreferenceManager preferences) {
        context = frame.getContext();
        this.preferences = preferences;
        playerView = frame.findViewById(R.id.guidePlayer);
        art = frame.findViewById(R.id.guidePreviewArt);
        initials = frame.findViewById(R.id.guidePreviewInitials);
        // Rounds the video's corners; TextureView output follows the outline clip.
        frame.setClipToOutline(true);
    }

    /**
     * Shows a channel. A different channel replaces the art at once and plays after a rest;
     * the same channel keeps playing. Without {@code allowPlayback} only the art shows.
     */
    public void show(IptvChannel next, boolean allowPlayback) {
        allowed = allowPlayback;
        if (next == null) {
            release();
            art.setImageDrawable(null);
            art.setTag(null);
            initials.setText("");
            return;
        }
        if (channel != null && channel.id == next.id) {
            channel = next;
            if (!allowPlayback) stop();
            else if (!started && !failed) schedulePlayback();
            return;
        }
        channel = next;
        failed = false;
        stop();
        showArt();
        if (allowPlayback) schedulePlayback();
    }

    /** Stops the stream and returns to the art; the channel stays so it can play again. */
    public void stop() {
        handler.removeCallbacks(autoplay);
        started = false;
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        playerView.setVisibility(View.GONE);
        art.animate().cancel();
        initials.animate().cancel();
        art.setAlpha(1f);
        initials.setAlpha(1f);
        art.setVisibility(View.VISIBLE);
        initials.setVisibility(art.getDrawable() == null ? View.VISIBLE : View.GONE);
    }

    /** Frees the player and its decoder. The next {@link #show} starts over. */
    public void release() {
        handler.removeCallbacks(loadArt);
        stop();
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
        channel = null;
    }

    private void schedulePlayback() {
        handler.removeCallbacks(autoplay);
        handler.postDelayed(autoplay, PLAY_REST_MS);
    }

    private void showArt() {
        handler.removeCallbacks(loadArt);
        initials.setText(Monogram.initials(channel.name));
        FastGuide.Schedule schedule = FastGuide.schedule(channel.channelId);
        boolean poster = schedule != null && !schedule.art.isEmpty();
        String url = poster ? schedule.art : channel.logo;
        // Coming back to the same channel keeps its art instead of flashing the initials.
        if (url.equals(art.getTag()) && art.getDrawable() != null) {
            initials.setVisibility(View.GONE);
            return;
        }
        art.setImageDrawable(null);
        initials.setVisibility(View.VISIBLE);
        int padding = poster ? 0 : dp(LOGO_PADDING_DP);
        art.setScaleType(poster ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        art.setPadding(padding, padding, padding, padding);
        art.setTag(url);
        // Holding Down passes many channels; only the one focus rests on downloads its art.
        if (!url.isEmpty()) handler.postDelayed(loadArt, ART_REST_MS);
    }

    private void loadArt() {
        Object tag = art.getTag();
        if (!(tag instanceof String) || ((String) tag).isEmpty()) return;
        String url = (String) tag;
        RemoteImageLoader.get(context).load(url, dp(WIDTH_DP), bitmap -> {
            if (!url.equals(art.getTag())) return;
            art.setImageBitmap(bitmap);
            initials.setVisibility(View.GONE);
        });
    }

    private void start() {
        if (channel == null || !allowed || failed || started) return;
        List<String> feeds = new StreamHealth(context).rank(channel.streamUrls);
        if (feeds.isEmpty()) {
            failed = true;
            return;
        }
        String url = feeds.get(0);
        MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(url));
        if (FastGuide.isHls(url)) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(8000).setReadTimeoutMs(12000)
                .setDefaultRequestProperties(channel.streamHeaders.getOrDefault(url, Collections.emptyMap()));
        MediaSource source;
        try {
            source = new DefaultMediaSourceFactory(new DefaultDataSource.Factory(context, http))
                    .createMediaSource(item.build());
        } catch (RuntimeException unsupportedFeed) {
            failed = true;
            return;
        }
        ExoPlayer preview = player();
        started = true;
        playerView.setVisibility(View.VISIBLE);
        preview.setMediaSource(source);
        preview.prepare();
        preview.play();
    }

    private ExoPlayer player() {
        if (player != null) return player;
        player = new ExoPlayer.Builder(context).build();
        player.setVolume(0f);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setMaxVideoSize(854, 480)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build());
        player.addListener(new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
                reveal();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) fail();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                fail();
            }
        });
        playerView.setPlayer(player);
        return player;
    }

    /** The first frame is on screen: fade the art away to uncover it. */
    private void reveal() {
        if (!started) return;
        long duration = preferences.reducedMotion() ? 0L : REVEAL_MS;
        art.animate().alpha(0f).setDuration(duration).withEndAction(() -> art.setVisibility(View.INVISIBLE)).start();
        initials.animate().alpha(0f).setDuration(duration).withEndAction(() -> initials.setVisibility(View.INVISIBLE)).start();
    }

    private void fail() {
        failed = true;
        stop();
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
