package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

/** Queue state for one playlist member. */
public enum OfflinePlaylistItemState {
    PENDING,
    DOWNLOADING,
    AVAILABLE,
    FAILED;

    static OfflinePlaylistItemState fromStorage(String value) {
        if (value == null) return FAILED;
        try { return valueOf(value); } catch (IllegalArgumentException ignored) { return FAILED; }
    }
}
