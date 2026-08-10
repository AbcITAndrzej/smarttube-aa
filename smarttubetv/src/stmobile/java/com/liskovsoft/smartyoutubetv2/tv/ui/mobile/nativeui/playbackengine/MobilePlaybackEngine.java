package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.playbackengine;

/**
 * Small engine-neutral control surface used by the native mobile repository while SmartTube is
 * migrated from the bundled ExoPlayer 2 fork to AndroidX Media3 in reversible steps.
 *
 * <p>The interface intentionally contains only transport/state primitives. SmartTube-specific
 * format selection and YouTube metadata remain in the legacy controller until the later VOD
 * migration wave.</p>
 */
public interface MobilePlaybackEngine {
    enum State {
        IDLE,
        BUFFERING,
        READY,
        ENDED
    }

    interface Listener {
        void onEngineStateChanged(State state, boolean playWhenReady);
        void onEngineError(Throwable error);
    }

    String getEngineName();

    boolean isInitialized();

    /** Opens a direct URI (file/http/https/HLS) and replaces the previous item. */
    void open(String uri, long startPositionMs, boolean playWhenReady);

    void setPlayWhenReady(boolean playWhenReady);

    boolean getPlayWhenReady();

    boolean isPlaying();

    boolean isLoading();

    boolean containsMedia();

    State getState();

    long getPositionMs();

    long getDurationMs();

    long getBufferedPositionMs();

    void seekTo(long positionMs);

    void setSpeed(float speed);

    float getSpeed();

    void setPitch(float pitch);

    float getPitch();

    void setVolume(float volume);

    float getVolume();

    void release();
}
