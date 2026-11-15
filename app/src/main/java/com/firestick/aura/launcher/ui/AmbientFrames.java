package com.firestick.aura.launcher.ui;

/**
 * Shared 30 fps cadence for ambient decorations (focused-card effect, preview particles). A frame
 * is a draw frame for every caller or for none, so effects that run together redraw in the same
 * frames instead of alternating, which kept a Fire TV GPU busy at 60 fps. Main thread only.
 */
public final class AmbientFrames {
    /** Between one and two 60 Hz frames: every other frame draws, and a late frame draws at once. */
    static final long MIN_GAP_MS = 25L;
    private static final AmbientFrames SHARED = new AmbientFrames();

    private long decidedFrame;
    private long lastDrawFrame;
    private boolean decided;
    private boolean drawing;

    AmbientFrames() {
    }

    /**
     * Whether ambient views redraw in this frame. Call only from Choreographer callbacks (animator
     * updates, {@code postOnAnimation}) with {@code AnimationUtils.currentAnimationTimeMillis()},
     * which is the same frame time for every caller in one frame.
     */
    public static boolean isDrawFrame(long frameTimeMs) {
        return SHARED.decide(frameTimeMs);
    }

    boolean decide(long frameTimeMs) {
        if (decided && frameTimeMs == decidedFrame) return drawing;
        drawing = !decided || frameTimeMs < lastDrawFrame || frameTimeMs - lastDrawFrame >= MIN_GAP_MS;
        decided = true;
        decidedFrame = frameTimeMs;
        if (drawing) lastDrawFrame = frameTimeMs;
        return drawing;
    }
}
