package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

import java.util.List;

public interface MobileNavigator {
    void openBrowse(String pageId);
    void openBrowseItem(String itemId);
    void openChannel(String channelId);
    void openSearch(String initialQuery);
    void openSettings();
    void openAndroidAutoSettings();
    void openPlayerSettings();
    void openDiagnostics();
    void openOfflineSettings();
    void openOfflinePlaylists();
    void openOfflineListenSaved();
    void openRadioSettings();
    void openRadioPlayback(String stationId);
    void openPlayback(String mediaId, long startPositionMs);
    void openPlaybackQueue(String mediaId, long startPositionMs, List<String> playbackQueue);
    void openShortPlayback(String mediaId, long startPositionMs, List<String> shortQueue);
    void goBack();
}
