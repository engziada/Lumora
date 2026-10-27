package com.firestick.aura.launcher.manager;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.view.View;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reuses synthesized static audio tracks to avoid D-pad navigation jitter. */
public final class SoundManager {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static volatile AudioTrack focusTrack;
    private static volatile AudioTrack clickTrack;
    private static volatile boolean initialized;

    private SoundManager() { }

    public static synchronized void init(android.content.Context context) {
        if (initialized) return;
        initialized = true;
        EXECUTOR.execute(() -> {
            focusTrack = buildTrack(generateTone(880, 14, .12f));
            clickTrack = buildTrack(generateTone(1320, 26, .30f));
        });
    }

    public static void playFocus(View view, PreferenceManager preferences) {
        if (preferences == null || preferences.isSoundEnabled()) playTrack(focusTrack);
    }

    public static void playClick(View view, PreferenceManager preferences) {
        if (preferences == null || preferences.isSoundEnabled()) playTrack(clickTrack);
    }

    private static void playTrack(AudioTrack track) {
        if (track == null) return;
        EXECUTOR.execute(() -> {
            try {
                if (track.getState() != AudioTrack.STATE_INITIALIZED) return;
                track.stop(); track.reloadStaticData(); track.setPlaybackHeadPosition(0); track.play();
            } catch (Exception ignored) { }
        });
    }

    private static AudioTrack buildTrack(byte[] pcm) {
        try {
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.length).setTransferMode(AudioTrack.MODE_STATIC).build();
            track.write(pcm, 0, pcm.length);
            return track;
        } catch (Exception exception) { return null; }
    }

    private static byte[] generateTone(int frequency, int durationMs, float volume) {
        int samples = durationMs * 44100 / 1000;
        byte[] output = new byte[samples * 2];
        for (int i = 0; i < samples; i++) {
            double time = (double) i / 44100;
            short value = (short) (Math.sin(2 * Math.PI * frequency * time) * Math.exp(-time * 150) * volume * 32767);
            output[i * 2] = (byte) (value & 0xff);
            output[i * 2 + 1] = (byte) ((value >>> 8) & 0xff);
        }
        return output;
    }
}
