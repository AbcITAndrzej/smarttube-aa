package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Durable queue/history facade for Stage 7 passive listen-and-save. */
public final class OfflineListenSaveRepository {
    private static volatile OfflineListenSaveRepository instance;

    private final Context app;
    private final OfflineListenSaveDatabase database;
    private final OfflineMediaRepository media;
    private final OfflineMediaPreferences preferences;
    private final MobileFeatureFlags flags;
    private final OfflinePlaylistDatabase playlists;

    private OfflineListenSaveRepository(Context context) {
        app = context.getApplicationContext();
        database = new OfflineListenSaveDatabase(app);
        media = OfflineMediaRepository.get(app);
        preferences = media.getPreferences();
        flags = new MobileFeatureFlags(app);
        playlists = new OfflinePlaylistDatabase(app);
        database.recoverInterrupted(System.currentTimeMillis());
    }

    public static OfflineListenSaveRepository get(Context context) {
        OfflineListenSaveRepository current = instance;
        if (current == null) {
            synchronized (OfflineListenSaveRepository.class) {
                current = instance;
                if (current == null) {
                    current = new OfflineListenSaveRepository(context);
                    instance = current;
                }
            }
        }
        return current;
    }

    public boolean isEnabled() {
        return media.isEnabled()
                && preferences.isListenSaveEnabled()
                && flags.isOfflineListenSaveEnabled();
    }

    public OfflineListenSaveEntry enqueue(OfflineMediaDescriptor descriptor) {
        if (!isEnabled() || descriptor == null || !descriptor.isValid()) return null;
        long now = System.currentTimeMillis();
        OfflineMediaRecord record = media.find(descriptor.getMediaId());
        boolean available = record != null && record.isAvailable()
                && media.resolveAvailableFile(descriptor.getMediaId()) != null;
        OfflineListenSaveEntry current = database.upsertRequested(descriptor,
                available ? OfflineListenSaveState.AVAILABLE : OfflineListenSaveState.PENDING,
                available ? record.getBytesDownloaded() : 0L,
                available ? record.getBytesTotal() : 0L, now);
        if (available) pruneToLimit();
        return current;
    }

    public OfflineListenSaveEntry nextPending() {
        if (!isEnabled()) return null;
        return database.nextPending();
    }

    public OfflineListenSaveEntry find(String mediaId) { return database.find(mediaId); }

    public void markDownloading(OfflineListenSaveEntry entry) {
        if (entry == null) return;
        OfflineMediaRecord record = media.find(entry.getMediaId());
        database.setState(entry.getMediaId(), OfflineListenSaveState.DOWNLOADING,
                record == null ? entry.getBytesDownloaded() : record.getBytesDownloaded(),
                record == null ? entry.getBytesTotal() : record.getBytesTotal(), "",
                entry.getAttempts() + 1, System.currentTimeMillis());
    }

    public void updateProgress(String mediaId, long downloaded, long total) {
        database.updateProgress(mediaId, downloaded, total, System.currentTimeMillis());
    }

    public void markAvailable(String mediaId) {
        OfflineMediaRecord record = media.find(mediaId);
        long bytes = record == null ? 0L : record.getBytesDownloaded();
        long total = record == null ? bytes : record.getBytesTotal();
        OfflineListenSaveEntry entry = database.find(mediaId);
        database.setState(mediaId, OfflineListenSaveState.AVAILABLE, bytes, total, "",
                entry == null ? 1 : entry.getAttempts(), System.currentTimeMillis());
        pruneToLimit();
    }

    public void requeue(String mediaId, String reason) {
        OfflineMediaRecord record = media.find(mediaId);
        OfflineListenSaveEntry entry = database.find(mediaId);
        if (entry == null) return;
        database.setState(mediaId, OfflineListenSaveState.PENDING,
                record == null ? entry.getBytesDownloaded() : record.getBytesDownloaded(),
                record == null ? entry.getBytesTotal() : record.getBytesTotal(), reason,
                entry.getAttempts(), System.currentTimeMillis());
    }

    public void markFailed(String mediaId, String reason) {
        OfflineMediaRecord record = media.find(mediaId);
        OfflineListenSaveEntry entry = database.find(mediaId);
        if (entry == null) return;
        database.setState(mediaId, OfflineListenSaveState.FAILED,
                record == null ? entry.getBytesDownloaded() : record.getBytesDownloaded(),
                record == null ? entry.getBytesTotal() : record.getBytesTotal(), reason,
                entry.getAttempts(), System.currentTimeMillis());
    }

    public List<OfflineListenSaveEntry> listRecent(int limit) {
        if (!media.isEnabled()) return Collections.emptyList();
        List<OfflineListenSaveEntry> entries = database.listRecent(limit);
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (OfflineListenSaveEntry entry : entries) {
            if (entry.getState() == OfflineListenSaveState.AVAILABLE
                    && media.resolveAvailableFile(entry.getMediaId()) == null) {
                database.setState(entry.getMediaId(), OfflineListenSaveState.FAILED,
                        0L, entry.getBytesTotal(), "evicted or local file missing",
                        entry.getAttempts(), now);
                changed = true;
            }
        }
        return changed ? database.listRecent(limit) : entries;
    }

    /** Returns recent completed passive items with a real local file. */
    public List<OfflineListenSaveEntry> listPlayable(int limit) {
        List<OfflineListenSaveEntry> out = new ArrayList<>();
        for (OfflineListenSaveEntry entry : database.listRecent(limit <= 0 ? 0 : limit * 2)) {
            if (entry.getState() != OfflineListenSaveState.AVAILABLE) continue;
            if (media.resolveAvailableFile(entry.getMediaId()) == null) continue;
            out.add(entry);
            if (limit > 0 && out.size() >= limit) break;
        }
        return out;
    }

    /** Remove old passive ownership. Playlist-owned audio is never deleted by this prune. */
    public synchronized long pruneToLimit() {
        int limit = preferences.getListenSaveRecentLimit();
        List<OfflineListenSaveEntry> available = database.listAvailable();
        long removedBytes = 0L;
        for (int i = limit; i < available.size(); i++) {
            OfflineListenSaveEntry entry = available.get(i);
            if (playlists.countReferences(entry.getMediaId()) == 0) {
                removedBytes += media.delete(entry.getMediaId());
            }
            database.delete(entry.getMediaId());
        }
        return removedBytes;
    }

    public synchronized long delete(String mediaId, boolean deleteAudioIfUnshared) {
        if (mediaId == null || mediaId.isEmpty()) return 0L;
        long removed = 0L;
        if (deleteAudioIfUnshared && playlists.countReferences(mediaId) == 0) {
            removed = media.delete(mediaId);
        }
        database.delete(mediaId);
        return removed;
    }

    public synchronized long clearAutoSaved() {
        long removed = 0L;
        for (OfflineListenSaveEntry entry : database.listAvailable()) {
            if (playlists.countReferences(entry.getMediaId()) == 0) {
                removed += media.delete(entry.getMediaId());
            }
        }
        database.clear();
        return removed;
    }

    public boolean hasReference(String mediaId) { return database.find(mediaId) != null; }

    public int pendingCount() { return database.countByState(OfflineListenSaveState.PENDING); }
    public int downloadingCount() { return database.countByState(OfflineListenSaveState.DOWNLOADING); }
    public int availableCount() { return database.countByState(OfflineListenSaveState.AVAILABLE); }
    public int failedCount() { return database.countByState(OfflineListenSaveState.FAILED); }
    public Context getApplicationContext() { return app; }
}
