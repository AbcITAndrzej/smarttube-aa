package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileError;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobilePlaybackSnapshot;

/**
 * Player port. An adapter may delegate these calls to SmartTube's current playback manager
 * without exposing Leanback PlayerAdapter or TV presenters to the mobile fragment.
 */
public interface MobilePlaybackRepository {
    interface Listener {
        void onPlaybackSnapshot(MobilePlaybackSnapshot snapshot);
        void onPlaybackError(MobileError error);
    }

    void setListener(Listener listener);
    void prepare(String mediaId, long startPositionMs);
    void play();
    void pause();
    void playNext();
    void playPrevious();
    void seekTo(long positionMs);
    void seekBy(long deltaMs);
    void setPlaybackSpeed(float speed);
    void selectVideoTrack(String trackId);
    void selectAudioTrack(String trackId);
    void selectSubtitleTrack(String trackId);
    void setResizeMode(int resizeMode);
    void release();
}
