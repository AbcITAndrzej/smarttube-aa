package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaRepository;

/** Pure routing decisions for Stage 9 Android Auto offline playback. */
final class AndroidAutoOfflineRouting {
    private AndroidAutoOfflineRouting() {}

    static boolean shouldUseOffline(boolean explicitOfflineSelection,
                                    boolean offlineAaEnabled,
                                    boolean automaticFallbackEnabled,
                                    boolean networkAvailable,
                                    boolean localCopyAvailable) {
        if (!offlineAaEnabled) return false;
        // A selection made from the Offline tree must never silently turn into network playback
        // if the local file disappears between browse and click. Route it as offline and let the
        // repository fail locally so the UI can report/refresh the stale entry.
        if (explicitOfflineSelection) return true;
        return localCopyAvailable && automaticFallbackEnabled && !networkAvailable;
    }

    static boolean shouldForceSavedOffline(boolean sourceIsOffline,
                                           boolean automaticFallbackEnabled,
                                           boolean networkAvailable,
                                           boolean localCopyAvailable) {
        if (!localCopyAvailable) return false;
        return sourceIsOffline || (automaticFallbackEnabled && !networkAvailable);
    }

    static boolean snapshotMatches(String activePlaybackMediaId,
                                   String activeRawMediaId,
                                   String snapshotMediaId) {
        if (snapshotMediaId == null) return false;
        if (activePlaybackMediaId != null && activePlaybackMediaId.equals(snapshotMediaId)) {
            return true;
        }
        if (activeRawMediaId != null && activeRawMediaId.equals(snapshotMediaId)) {
            return true;
        }
        return activeRawMediaId != null
                && OfflineMediaRepository.playbackId(activeRawMediaId).equals(snapshotMediaId);
    }

    static int findNextLocalIndex(int startIndex, boolean[] localAvailable,
                                  boolean allowWrap) {
        if (localAvailable == null || localAvailable.length == 0 || startIndex < 0) return -1;
        int size = localAvailable.length;
        int normalizedStart = startIndex;
        if (normalizedStart >= size) {
            if (!allowWrap) return -1;
            normalizedStart %= size;
        }
        for (int offset = 0; offset < size; offset++) {
            int index = normalizedStart + offset;
            if (index >= size) {
                if (!allowWrap) break;
                index %= size;
            }
            if (localAvailable[index]) return index;
        }
        return -1;
    }
}
