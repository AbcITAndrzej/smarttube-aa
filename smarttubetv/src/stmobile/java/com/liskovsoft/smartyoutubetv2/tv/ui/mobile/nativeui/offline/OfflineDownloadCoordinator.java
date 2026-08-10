package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

/**
 * Process-wide single offline-transfer slot shared by passive Stage 7 and explicit Stage 8.
 * Playback itself is not blocked. This only prevents two background offline writers competing
 * for disk/bandwidth and, more importantly, prevents concurrent writes to the same .part store.
 */
final class OfflineDownloadCoordinator {
    private static String owner = "";
    private static String mediaId = "";

    private OfflineDownloadCoordinator() {}

    static synchronized boolean tryAcquire(String requestedOwner, String requestedMediaId) {
        if (owner.isEmpty()) {
            owner = safe(requestedOwner);
            mediaId = safe(requestedMediaId);
            return true;
        }
        return owner.equals(safe(requestedOwner)) && mediaId.equals(safe(requestedMediaId));
    }

    static synchronized void release(String requestedOwner, String requestedMediaId) {
        if (owner.equals(safe(requestedOwner)) && mediaId.equals(safe(requestedMediaId))) {
            owner = "";
            mediaId = "";
        }
    }

    static synchronized boolean isBusy() { return !owner.isEmpty(); }
    static synchronized String currentOwner() { return owner; }
    static synchronized String currentMediaId() { return mediaId; }

    private static String safe(String value) { return value == null ? "" : value; }
}
