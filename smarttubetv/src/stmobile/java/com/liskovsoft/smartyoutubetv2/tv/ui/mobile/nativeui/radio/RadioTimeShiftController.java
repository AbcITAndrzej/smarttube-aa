package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio;

/** Rolling live-radio DVR used by both the phone radio player and Android Auto. */
public interface RadioTimeShiftController {
    /** Starts a new radio session and returns the URL ExoPlayer should open. */
    String start(RadioStation station);
    boolean isActive();
    boolean canSeek();
    boolean hasFailed();
    long getWindowDurationMs();
    /** Current payload bytes kept by the rolling buffer; zero when inactive/unsupported. */
    long getBufferedBytes();
    long positionForPlayer(long playerPositionMs);
    String seekTo(long virtualPositionMs);
    String goLive();
    String getDirectStreamUrl();
    void stop();
    void close();
}
