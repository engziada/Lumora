package com.firestick.aura.launcher;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.util.Locale;

/** Full-screen, remote-friendly playback for public IPTV streams. */
public final class IptvPlayerActivity extends AppCompatActivity {
    public static final String EXTRA_CHANNEL_NAME = "iptv.channel_name";
    public static final String EXTRA_COUNTRY = "iptv.country";
    public static final String EXTRA_STREAM_URL = "iptv.stream_url";

    private PlayerView playerView;
    private View errorPanel;
    private TextView channelTitle;
    private TextView channelCountry;
    private String streamUrl;
    private ExoPlayer player;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_iptv_player);

        playerView = findViewById(R.id.iptvPlayerView);
        errorPanel = findViewById(R.id.iptvPlayerError);
        channelTitle = findViewById(R.id.iptvPlayerChannelTitle);
        channelCountry = findViewById(R.id.iptvPlayerCountry);
        Button retry = findViewById(R.id.iptvPlayerRetry);
        retry.setOnClickListener(view -> preparePlayback());

        String name = getIntent().getStringExtra(EXTRA_CHANNEL_NAME);
        String country = getIntent().getStringExtra(EXTRA_COUNTRY);
        streamUrl = getIntent().getStringExtra(EXTRA_STREAM_URL);
        channelTitle.setText(name == null || name.trim().isEmpty() ? getString(R.string.shows_label) : name);
        channelCountry.setText(country == null ? "" : country.toUpperCase(Locale.ROOT));
        channelCountry.setVisibility(country == null || country.trim().isEmpty() ? View.GONE : View.VISIBLE);

        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            showPlaybackError();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        hideSystemBars();
        if (streamUrl != null && !streamUrl.trim().isEmpty()) initializePlayer();
    }

    @Override
    protected void onStop() {
        releasePlayer();
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void initializePlayer() {
        if (player != null) return;
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setControllerShowTimeoutMs(5000);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                    errorPanel.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                showPlaybackError();
            }
        });
        preparePlayback();
    }

    private void preparePlayback() {
        if (player == null || streamUrl == null || streamUrl.trim().isEmpty()) {
            showPlaybackError();
            return;
        }
        errorPanel.setVisibility(View.GONE);
        MediaItem.Builder item = new MediaItem.Builder().setUri(Uri.parse(streamUrl.trim()));
        if (streamUrl.toLowerCase(Locale.ROOT).contains(".m3u8")) {
            item.setMimeType(MimeTypes.APPLICATION_M3U8);
        }
        player.setMediaItem(item.build());
        player.prepare();
        player.play();
    }

    private void showPlaybackError() {
        errorPanel.setVisibility(View.VISIBLE);
        errorPanel.findViewById(R.id.iptvPlayerRetry).requestFocus();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void releasePlayer() {
        if (player != null) {
            playerView.setPlayer(null);
            player.release();
            player = null;
        }
    }
}
