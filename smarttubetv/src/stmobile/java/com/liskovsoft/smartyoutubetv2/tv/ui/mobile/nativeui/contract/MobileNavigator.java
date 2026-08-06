package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

public interface MobileNavigator {
    void openBrowse(String pageId);
    void openBrowseItem(String itemId);
    void openChannel(String channelId);
    void openSearch(String initialQuery);
    void openSettings();
    void openPlayback(String mediaId, long startPositionMs);
    void goBack();
}
