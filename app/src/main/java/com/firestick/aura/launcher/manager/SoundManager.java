package com.firestick.aura.launcher.manager;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.view.View;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny synthesised navigation sounds.
 *
 * <p>v3 reuses two static {@link AudioTrack} instances instead of allocating one per
 * key press. Building an AudioTrack costs several milliseconds and a native buffer,
 * which was visible as D-pad jitter on a Fire TV Stick when scrolling fast.
 */
public class SoundManager {
    private static final ExecutorService soundExecutor = Executors.newSingleThreadExecutor();
    private static volatile AudioTrack focusTrack;
    private static volatile AudioTrack clickTrack;
    private static volatile boolean initialized;

    public static synchronized void init(final android.content.Context context) {
        if (initialized) return;
        soundExecutor.execute(() -> {
            focusTrack = buildTrack(generateTone(880, 14, 0.12f));  // soft 880 Hz blip
            clickTrack = buildTrack(generateTone(1320, 26, 0.30f)); // 1320 Hz confirm pop
            initialized = true;
        });
    }

    public static void playFocus(View view, PreferenceManager prefs) {
        if (prefs != null && !prefs.isSoundEnabled()) return;
        playTrack(focusTrack);
    }

    public static void playClick(View view, PreferenceManager prefs) {
        if (prefs != null && !prefs.isSoundEnabled()) return;
        playTrack(clickTrack);
    }

    private static void playTrack(final AudioTrack track) {
        if (track == null) return;
        soundExecutor.execute(() -> {
            try {
                if (track.getState() != AudioTrack.STATE_INITIALIZED) return;
                track.stop();
                track.reloadStaticData();
                track.setPlaybackHeadPosition(0);
                track.play();
            } catch (Exception ignored) {
            }
        });
    }

    private static AudioTrack buildTrack(byte[] pcm) {
        if (pcm == null) return null;
        try {
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(44100)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(pcm.length)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            track.write(pcm, 0, pcm.length);
            return track;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] generateTone(int freqHz, int durationMs, float volume) {
        int sampleRate = 44100;
        int numSamples = (durationMs * sampleRate) / 1000;
        byte[] output = new byte[numSamples * 2];

        for (int i = 0; i < numSamples; i++) {
            double time = (double) i / sampleRate;
            // Quick exponential decay removes the click at the tail of the tone.
            double envelope = Math.exp(-time * 150);
            double sample = Math.sin(2 * Math.PI * freqHz * time) * envelope * volume;
            short val = (short) (sample * 32767);
            output[2 * i] = (byte) (val & 0x00ff);
            output[2 * i + 1] = (byte) ((val & 0xff00) >>> 8);
        }
        return output;
    }
}
