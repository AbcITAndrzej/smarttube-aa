package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

/** Persistent state of an explicitly requested offline playlist download. */
public enum OfflinePlaylistState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    AVAILABLE,
    PARTIAL,
    FAILED;

    static OfflinePlaylistState fromStorage(String value) {
        if (value == null) return FAILED;
        try { return valueOf(value); } catch (IllegalArgumentException ignored) { return FAILED; }
    }
}
