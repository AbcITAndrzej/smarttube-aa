package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileMediaItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stage 8 explicit offline-playlist queue.
 *
 * <p>This remains separate from Stage 7's passive "listen and save" queue. Playlist
 * downloads are explicit user actions and may run in the foreground download service.</p>
 */
public final class OfflinePlaylistRepository {
    private static volatile OfflinePlaylistRepository instance;

    private final Context applicationContext;
    private final OfflinePlaylistDatabase database;
    private final OfflineMediaRepository mediaRepository;
    private final OfflineMediaPreferences preferences;
    private final MobileFeatureFlags featureFlags;

    private OfflinePlaylistRepository(Context context) {
        applicationContext = context.getApplicationContext();
        database = new OfflinePlaylistDatabase(applicationContext);
        mediaRepository = OfflineMediaRepository.get(applicationContext);
        preferences = mediaRepository.getPreferences();
        featureFlags = new MobileFeatureFlags(applicationContext);
        database.recoverInterrupted(System.currentTimeMillis());
    }

    public static OfflinePlaylistRepository get(Context context) {
        OfflinePlaylistRepository current = instance;
        if (current == null) {
            synchronized (OfflinePlaylistRepository.class) {
                current = instance;
                if (current == null) {
                    current = new OfflinePlaylistRepository(context);
                    instance = current;
                }
            }
        }
        return current;
    }

    /** Worker capability: explicit Stage 8 queues or Stage 10 trip-reserve queues may be active. */
    public boolean isEnabled() {
        return mediaRepository.isEnabled() && featureFlags.isOfflinePlaylistsEnabled()
                && (isUserDownloadsEnabled() || isTripReserveEnabled());
    }

    public boolean isUserDownloadsEnabled() {
        return mediaRepository.isEnabled() && featureFlags.isOfflinePlaylistsEnabled()
                && preferences.isPlaylistDownloadsEnabled();
    }

    public boolean isTripReserveEnabled() {
        return mediaRepository.isEnabled() && featureFlags.isOfflinePlaylistsEnabled()
                && featureFlags.isOfflineTripReserveEnabled()
                && preferences.isTripReserveEnabled();
    }

    public OfflinePlaylistRecord enqueue(String playlistId, String title, String thumbnailUrl,
                                         List<MobileMediaItem> items) {
        if (!isUserDownloadsEnabled() || playlistId == null || playlistId.trim().isEmpty()) return null;
        return enqueueInternal(playlistId, title, thumbnailUrl, items);
    }

    /** Stage 10 only: replace a synthetic trip-reserve playlist when the desired reserve changes. */
    public OfflinePlaylistRecord replaceTripReserve(String playlistId, String title, String thumbnailUrl,
                                                    List<MobileMediaItem> items) {
        if (!isTripReserveEnabled() || !OfflineTripReserveRepository.isTripReservePlaylistId(playlistId)) {
            return null;
        }
        // Never rewrite playlist membership while the shared playlist worker is writing an entry.
        // The next Stage 10 wake will retry the reconciliation.
        if (OfflineDownloadCoordinator.isBusy()
                && "playlist".equals(OfflineDownloadCoordinator.currentOwner())) {
            return database.findPlaylist(playlistId);
        }
        OfflinePlaylistRecord existing = database.findPlaylist(playlistId);
        if (existing != null && existing.getState() == OfflinePlaylistState.DOWNLOADING) {
            return existing;
        }
        if (existing != null) database.deletePlaylist(playlistId);
        return enqueueInternal(playlistId, title, thumbnailUrl, items);
    }

    public OfflinePlaylistRecord enqueueTripReserve(String playlistId, String title, String thumbnailUrl,
                                                     List<MobileMediaItem> items) {
        if (!isTripReserveEnabled() || !OfflineTripReserveRepository.isTripReservePlaylistId(playlistId)) {
            return null;
        }
        return enqueueInternal(playlistId, title, thumbnailUrl, items);
    }

    private OfflinePlaylistRecord enqueueInternal(String playlistId, String title, String thumbnailUrl,
                                                   List<MobileMediaItem> items) {

        // Do not destructively replace a durable queue while the foreground worker may still be
        // using one of its entries. A second tap on Download is therefore idempotent. Terminal
        // failed/partial queues are turned back into retryable work; complete queues remain pinned
        // until the user explicitly removes them. Playlist re-sync can be added later as a separate
        // operation without racing the active downloader.
        OfflinePlaylistRecord existingPlaylist = database.findPlaylist(playlistId);
        if (existingPlaylist != null) {
            if (existingPlaylist.getState() == OfflinePlaylistState.FAILED
                    || existingPlaylist.getState() == OfflinePlaylistState.PARTIAL) {
                resume(playlistId);
                return database.findPlaylist(playlistId);
            }
            return existingPlaylist;
        }

        List<OfflinePlaylistEntry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int position = 0;
        if (items != null) {
            for (MobileMediaItem item : items) {
                if (item == null || !item.isPlayable() || item.getKind() == MobileMediaItem.Kind.LIVE
                        || item.getId() == null || item.getId().trim().isEmpty()
                        || !seen.add(item.getId())) continue;
                OfflineMediaRecord media = mediaRepository.find(item.getId());
                boolean available = media != null && media.isAvailable()
                        && mediaRepository.resolveAvailableFile(item.getId()) != null;
                entries.add(new OfflinePlaylistEntry(playlistId, position++, item.getId(),
                        item.getTitle(), item.getSubtitle(), item.getThumbnailUrl(), item.getDurationMs(),
                        available ? OfflinePlaylistItemState.AVAILABLE : OfflinePlaylistItemState.PENDING,
                        available ? media.getBytesDownloaded() : 0L,
                        available ? media.getBytesTotal() : 0L, "", 0));
            }
        }
        if (entries.isEmpty()) return null;
        database.replacePlaylist(playlistId, title, thumbnailUrl, entries, System.currentTimeMillis());
        return database.findPlaylist(playlistId);
    }

    public OfflinePlaylistRecord find(String playlistId) { return database.findPlaylist(playlistId); }

    public List<OfflinePlaylistRecord> list() {
        if (!mediaRepository.isEnabled()) return Collections.emptyList();
        return database.listPlaylists();
    }

    public List<OfflinePlaylistEntry> entries(String playlistId) {
        if (playlistId == null) return Collections.emptyList();
        return database.listEntries(playlistId);
    }

    public OfflinePlaylistRecord nextRunnablePlaylist() {
        if (!isEnabled()) return null;
        for (OfflinePlaylistRecord record : database.listRunnablePlaylists()) {
            boolean trip = OfflineTripReserveRepository.isTripReservePlaylistId(record.getPlaylistId());
            if ((trip && isTripReserveEnabled()) || (!trip && isUserDownloadsEnabled())) return record;
        }
        return null;
    }

    public boolean isWifiOnly(OfflinePlaylistRecord record) {
        if (record != null && OfflineTripReserveRepository.isTripReservePlaylistId(record.getPlaylistId())) {
            return preferences.isTripReserveWifiOnly();
        }
        return preferences.isPlaylistWifiOnly();
    }

    public boolean isPlaylistAllowed(String playlistId) {
        return OfflineTripReserveRepository.isTripReservePlaylistId(playlistId)
                ? isTripReserveEnabled() : isUserDownloadsEnabled();
    }

    public OfflinePlaylistEntry nextPending(String playlistId) {
        return database.nextPendingEntry(playlistId);
    }

    public void markPlaylistDownloading(String playlistId) {
        database.setPlaylistState(playlistId, OfflinePlaylistState.DOWNLOADING, "",
                System.currentTimeMillis());
    }

    public void markEntryDownloading(OfflinePlaylistEntry entry) {
        if (entry == null) return;
        database.setEntryState(entry.getPlaylistId(), entry.getPosition(),
                OfflinePlaylistItemState.DOWNLOADING, entry.getBytesDownloaded(), entry.getBytesTotal(),
                "", entry.getAttempts() + 1, System.currentTimeMillis());
    }

    public void updateEntryProgress(OfflinePlaylistEntry entry, long downloaded, long total) {
        if (entry == null) return;
        database.updateEntryProgress(entry.getPlaylistId(), entry.getPosition(), downloaded, total,
                System.currentTimeMillis());
    }

    public void markEntryAvailable(OfflinePlaylistEntry entry, OfflineMediaRecord media) {
        if (entry == null) return;
        long bytes = media == null ? entry.getBytesDownloaded() : media.getBytesDownloaded();
        long total = media == null ? entry.getBytesTotal() : media.getBytesTotal();
        database.setEntryState(entry.getPlaylistId(), entry.getPosition(),
                OfflinePlaylistItemState.AVAILABLE, bytes, total, "", entry.getAttempts() + 1,
                System.currentTimeMillis());
    }

    /** Return an interrupted item to the queue while preserving any downloaded .part bytes. */
    public void requeueEntry(OfflinePlaylistEntry entry, String reason) {
        if (entry == null) return;
        OfflineMediaRecord media = mediaRepository.find(entry.getMediaId());
        long bytes = media == null ? entry.getBytesDownloaded() : media.getBytesDownloaded();
        long total = media == null ? entry.getBytesTotal() : media.getBytesTotal();
        // markEntryDownloading() already consumed one attempt before the interruption. Preserve
        // that counter so diagnostics/retry policy never moves backwards after a pause/network loss.
        database.setEntryState(entry.getPlaylistId(), entry.getPosition(),
                OfflinePlaylistItemState.PENDING, bytes, total, reason, entry.getAttempts() + 1,
                System.currentTimeMillis());
    }

    public void markEntryFailed(OfflinePlaylistEntry entry, String reason) {
        if (entry == null) return;
        OfflineMediaRecord media = mediaRepository.find(entry.getMediaId());
        long bytes = media == null ? entry.getBytesDownloaded() : media.getBytesDownloaded();
        long total = media == null ? entry.getBytesTotal() : media.getBytesTotal();
        database.setEntryState(entry.getPlaylistId(), entry.getPosition(),
                OfflinePlaylistItemState.FAILED, bytes, total, reason, entry.getAttempts() + 1,
                System.currentTimeMillis());
    }

    public void pause(String playlistId) {
        if (playlistId == null) return;
        database.setPlaylistState(playlistId, OfflinePlaylistState.PAUSED, "",
                System.currentTimeMillis());
    }

    /** Resume also retries entries that previously failed. */
    public void resume(String playlistId) {
        if (playlistId == null) return;
        database.resetFailedToPending(playlistId, System.currentTimeMillis());
    }

    public void refresh(String playlistId) {
        database.refreshAggregates(playlistId, System.currentTimeMillis());
    }

    /**
     * Removes playlist membership. Audio referenced only by this playlist is also removed; shared
     * media is retained so deleting one playlist cannot break another playlist.
     */
    public long delete(String playlistId, boolean deleteUnsharedAudio) {
        if (playlistId == null) return 0L;
        List<OfflinePlaylistEntry> entries = database.listEntries(playlistId);
        long removed = 0L;
        if (deleteUnsharedAudio) {
            for (OfflinePlaylistEntry entry : entries) {
                if (database.countOtherReferences(entry.getMediaId(), playlistId) == 0
                        && !OfflineListenSaveRepository.get(applicationContext).hasReference(entry.getMediaId())) {
                    removed += mediaRepository.delete(entry.getMediaId());
                }
            }
        }
        database.deletePlaylist(playlistId);
        return removed;
    }

    public void clearAll(boolean deleteAudio) {
        List<OfflinePlaylistRecord> playlists = database.listPlaylists();
        if (deleteAudio) {
            for (OfflinePlaylistRecord playlist : playlists) delete(playlist.getPlaylistId(), true);
        }
        database.clear();
    }

    public int playlistCount() { return database.listPlaylists().size(); }

    public int activeCount() {
        int count = 0;
        for (OfflinePlaylistRecord record : database.listPlaylists()) {
            if (record.getState() == OfflinePlaylistState.QUEUED
                    || record.getState() == OfflinePlaylistState.DOWNLOADING) count++;
        }
        return count;
    }

    public Context getApplicationContext() { return applicationContext; }
}
