package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

/** Persistent lifecycle state of an audio item managed by the offline subsystem. */
public enum OfflineMediaState {
    DOWNLOADING,
    AVAILABLE,
    FAILED,
    EXPIRED;

    static OfflineMediaState fromStorage(String value) {
        if (value == null) return FAILED;
        try {
            return OfflineMediaState.valueOf(value);
        } catch (IllegalArgumentException error) {
            return FAILED;
        }
    }
}
