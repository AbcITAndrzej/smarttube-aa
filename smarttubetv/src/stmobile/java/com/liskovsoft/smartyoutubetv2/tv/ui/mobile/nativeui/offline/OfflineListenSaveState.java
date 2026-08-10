package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

/** Durable state of one passive Stage 7 listen-and-save request. */
public enum OfflineListenSaveState {
    PENDING,
    DOWNLOADING,
    AVAILABLE,
    FAILED;

    static OfflineListenSaveState fromStorage(String value) {
        if (value == null) return FAILED;
        try { return valueOf(value); }
        catch (IllegalArgumentException ignored) { return FAILED; }
    }
}
