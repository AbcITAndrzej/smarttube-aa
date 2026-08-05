package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

/** Optional capability implemented by repositories that can continue audio without a visible UI. */
public interface MobileBackgroundPlaybackRepository {
    void setHostVisible(boolean visible);
    boolean isBackgroundPlaybackEnabled();
}
