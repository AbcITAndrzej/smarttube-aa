package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import java.util.List;

/** Enforces the configured cache budget and minimum free-space reserve using LRU eviction. */
final class OfflineStorageManager {
    private final OfflineMediaDatabase database;
    private final OfflineAudioStore store;
    private final OfflineMediaPreferences preferences;
    private final OfflinePlaylistDatabase playlistDatabase;

    OfflineStorageManager(OfflineMediaDatabase database, OfflineAudioStore store,
                          OfflineMediaPreferences preferences,
                          OfflinePlaylistDatabase playlistDatabase) {
        this.database = database;
        this.store = store;
        this.preferences = preferences;
        this.playlistDatabase = playlistDatabase;
    }

    synchronized OfflineCleanupResult ensureCapacity(long incomingBytes) {
        long required = Math.max(0L, incomingBytes);
        OfflineMediaDatabase.DbStats stats = database.stats();
        if (fits(stats.bytes, required)) return new OfflineCleanupResult(true, 0, 0L);
        if (!preferences.isAutoCleanupEnabled()) return new OfflineCleanupResult(false, 0, 0L);
        return evictUntilFits(required, false);
    }

    synchronized OfflineCleanupResult cleanupNow() {
        return evictUntilFits(0L, true);
    }

    synchronized void clearAll() {
        store.clearAllFiles();
        database.clear();
    }

    synchronized int reconcile() {
        int changed = 0;
        long now = System.currentTimeMillis();
        List<OfflineMediaRecord> records = database.listAll();
        for (OfflineMediaRecord record : records) {
            if (record.getState() == OfflineMediaState.AVAILABLE
                    && !store.finalExists(record.getFileKey())) {
                database.updateState(record.getMediaId(), OfflineMediaState.EXPIRED,
                        0L, record.getBytesTotal(), "offline file missing", now, now);
                changed++;
            }
        }
        return changed;
    }

    private OfflineCleanupResult evictUntilFits(long incomingBytes, boolean removeTerminalFirst) {
        int removed = 0;
        long removedBytes = 0L;
        List<OfflineMediaRecord> candidates = database.listEvictionCandidates();
        OfflineMediaDatabase.DbStats stats = database.stats();

        for (OfflineMediaRecord record : candidates) {
            // Explicit Stage 8 playlist membership is a pin. Storage cleanup may evict future
            // transient/auto-saved entries, but must never silently break an offline playlist.
            if (playlistDatabase != null && playlistDatabase.countReferences(record.getMediaId()) > 0) {
                continue;
            }
            boolean terminal = record.getState() == OfflineMediaState.EXPIRED
                    || record.getState() == OfflineMediaState.FAILED;
            if (!terminal && removeTerminalFirst && fits(stats.bytes, incomingBytes)) break;
            if (!terminal && fits(stats.bytes, incomingBytes)) break;

            long physical = store.delete(record.getFileKey());
            long logical = Math.max(record.getBytesDownloaded(), physical);
            database.delete(record.getMediaId());
            stats = new OfflineMediaDatabase.DbStats(
                    Math.max(0, stats.total - 1),
                    stats.downloading,
                    Math.max(0, stats.available - (record.getState() == OfflineMediaState.AVAILABLE ? 1 : 0)),
                    Math.max(0, stats.failed - (record.getState() == OfflineMediaState.FAILED ? 1 : 0)),
                    Math.max(0, stats.expired - (record.getState() == OfflineMediaState.EXPIRED ? 1 : 0)),
                    Math.max(0L, stats.bytes - logical));
            removed++;
            removedBytes += logical;
        }

        return new OfflineCleanupResult(fits(database.stats().bytes, incomingBytes), removed, removedBytes);
    }

    private boolean fits(long trackedBytes, long incomingBytes) {
        long limit = preferences.getStorageLimitBytes();
        long reserve = preferences.getReservedFreeBytes();
        long free = store.availableDeviceBytes();
        boolean withinBudget = trackedBytes + incomingBytes <= limit;
        boolean keepsReserve = free <= 0L || free - incomingBytes >= reserve;
        return withinBudget && keepsReserve;
    }
}
