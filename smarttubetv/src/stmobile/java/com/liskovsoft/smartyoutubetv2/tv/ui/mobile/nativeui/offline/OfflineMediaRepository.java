package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

/**
 * Stage 6 offline foundation.
 *
 * <p>This class owns metadata, files and storage policy but deliberately performs no network
 * request. Stage 7 passive listen-and-save and Stage 8 explicit playlist downloads both feed audio into
 * {@link #openPartialOutput(String, boolean)}. Signed playback URLs are
 * never persisted by this repository.</p>
 */
public final class OfflineMediaRepository {
    public static final String PLAYBACK_PREFIX = "offline:";
    private static volatile OfflineMediaRepository instance;

    private final OfflineMediaDatabase database;
    private final OfflineAudioStore store;
    private final OfflineMediaPreferences preferences;
    private final OfflineStorageManager storageManager;
    private final MobileFeatureFlags featureFlags;

    private OfflineMediaRepository(Context context) {
        Context app = context.getApplicationContext();
        database = new OfflineMediaDatabase(app);
        store = new OfflineAudioStore(app);
        preferences = new OfflineMediaPreferences(app);
        storageManager = new OfflineStorageManager(database, store, preferences,
                new OfflinePlaylistDatabase(app));
        featureFlags = new MobileFeatureFlags(app);
    }

    public static OfflineMediaRepository get(Context context) {
        OfflineMediaRepository current = instance;
        if (current == null) {
            synchronized (OfflineMediaRepository.class) {
                current = instance;
                if (current == null) {
                    current = new OfflineMediaRepository(context);
                    instance = current;
                }
            }
        }
        return current;
    }

    /** Releases the process singleton between isolated Robolectric application sandboxes. */
    static void resetForTests() {
        synchronized (OfflineMediaRepository.class) {
            OfflineMediaRepository current = instance;
            instance = null;
            if (current != null) current.database.close();
        }
    }

    public static String playbackId(String mediaId) {
        if (mediaId == null || mediaId.trim().isEmpty()) return "";
        return mediaId.startsWith(PLAYBACK_PREFIX) ? mediaId : PLAYBACK_PREFIX + mediaId;
    }

    public static boolean isOfflinePlaybackId(String mediaId) {
        return mediaId != null && mediaId.startsWith(PLAYBACK_PREFIX)
                && mediaId.length() > PLAYBACK_PREFIX.length();
    }

    public static String rawMediaId(String playbackId) {
        return isOfflinePlaybackId(playbackId)
                ? playbackId.substring(PLAYBACK_PREFIX.length()) : playbackId;
    }

    public boolean isEnabled() {
        return preferences.isFoundationEnabled() && featureFlags.isOfflineFoundationEnabled();
    }

    public OfflineMediaPreferences getPreferences() { return preferences; }

    /** Reserves metadata/storage before an offline producer starts writing audio bytes. */
    public synchronized OfflineMediaRecord beginDownload(OfflineMediaDescriptor descriptor,
                                                         long expectedBytes) throws IOException {
        if (!isEnabled()) throw new IOException("Offline foundation is disabled");
        if (descriptor == null || !descriptor.isValid()) throw new IOException("Invalid media descriptor");

        OfflineMediaRecord current = database.find(descriptor.getMediaId());
        if (current != null && current.isAvailable() && store.finalExists(current.getFileKey())) {
            database.touch(current.getMediaId(), System.currentTimeMillis());
            return database.find(current.getMediaId());
        }
        if (current != null && current.isAvailable()) {
            markExpired(current.getMediaId(), "offline file missing");
        }

        String fileKey = current != null && !current.getFileKey().isEmpty()
                ? current.getFileKey() : store.fileKey(descriptor.getMediaId());
        long existingBytes = store.bytesFor(fileKey);
        long requiredExtra = Math.max(0L, Math.max(0L, expectedBytes) - existingBytes);
        OfflineCleanupResult cleanup = storageManager.ensureCapacity(requiredExtra);
        if (!cleanup.isCapacityAvailable()) throw new IOException("Offline storage limit reached");

        long now = System.currentTimeMillis();
        database.upsertDownloading(descriptor, fileKey, existingBytes, expectedBytes, now);
        return database.find(descriptor.getMediaId());
    }

    /**
     * Revalidates the remaining storage budget once a producer knows the real response size.
     * This closes the gap where a format did not expose {@code clen} until the HTTP response.
     */
    public synchronized void ensureDownloadCapacity(String mediaId, long expectedTotalBytes)
            throws IOException {
        OfflineMediaRecord record = requireDownloading(mediaId);
        long existingBytes = store.bytesFor(record.getFileKey());
        long total = Math.max(0L, expectedTotalBytes);
        if (total <= 0L) throw new IOException("Offline audio size is unknown");
        long requiredExtra = Math.max(0L, total - existingBytes);
        OfflineCleanupResult cleanup = storageManager.ensureCapacity(requiredExtra);
        if (!cleanup.isCapacityAvailable()) throw new IOException("Offline storage limit reached");
        database.updateProgress(mediaId, existingBytes, total, System.currentTimeMillis());
    }

    /** Opens the private partial file. Network ownership remains outside this class. */
    public synchronized OutputStream openPartialOutput(String mediaId, boolean append) throws IOException {
        OfflineMediaRecord record = requireDownloading(mediaId);
        return store.openPartial(record.getFileKey(), append);
    }

    public synchronized void updateProgress(String mediaId, long bytesDownloaded, long bytesTotal) {
        OfflineMediaRecord record = database.find(mediaId);
        if (record == null || record.getState() != OfflineMediaState.DOWNLOADING) return;
        long actual = store.bytesFor(record.getFileKey());
        long safeDownloaded = Math.max(Math.max(0L, bytesDownloaded), actual);
        database.updateProgress(mediaId, safeDownloaded, Math.max(0L, bytesTotal),
                System.currentTimeMillis());
    }

    /** Atomically promotes the partial file to the playable offline file. */
    public synchronized OfflineMediaRecord markAvailable(String mediaId) throws IOException {
        OfflineMediaRecord record = requireDownloading(mediaId);
        long bytes = store.commit(record.getFileKey());
        long now = System.currentTimeMillis();
        long total = record.getBytesTotal() > 0L ? Math.max(record.getBytesTotal(), bytes) : bytes;
        database.updateState(mediaId, OfflineMediaState.AVAILABLE, bytes, total, "", 0L, now);
        return database.find(mediaId);
    }

    public synchronized void markFailed(String mediaId, String reason) {
        OfflineMediaRecord record = database.find(mediaId);
        if (record == null) return;
        long bytes = store.bytesFor(record.getFileKey());
        database.updateState(mediaId, OfflineMediaState.FAILED, bytes, record.getBytesTotal(),
                compact(reason), 0L, System.currentTimeMillis());
    }

    public synchronized void markExpired(String mediaId, String reason) {
        OfflineMediaRecord record = database.find(mediaId);
        if (record == null) return;
        long now = System.currentTimeMillis();
        database.updateState(mediaId, OfflineMediaState.EXPIRED,
                store.bytesFor(record.getFileKey()), record.getBytesTotal(), compact(reason), now, now);
    }

    public synchronized OfflineMediaRecord find(String mediaId) {
        return database.find(mediaId);
    }

    public synchronized List<OfflineMediaRecord> listAvailable(int limit) {
        if (!isEnabled()) return Collections.emptyList();
        return database.listAvailable(limit);
    }

    /** Returns a private file only when the record is AVAILABLE and physically present. */
    public synchronized File resolveAvailableFile(String mediaId) {
        File file = peekAvailableFile(mediaId);
        if (file != null) database.touch(mediaId, System.currentTimeMillis());
        return file;
    }

    /**
     * Stage 9: checks/returns an offline file without changing its LRU access time.
     * Android Auto uses this while building browse folders so simply opening the library does not
     * make every visible track look freshly played to the storage eviction policy.
     */
    public synchronized File peekAvailableFile(String mediaId) {
        OfflineMediaRecord record = database.find(mediaId);
        if (record == null || !record.isAvailable()) return null;
        if (!store.finalExists(record.getFileKey())) {
            markExpired(mediaId, "offline file missing");
            return null;
        }
        return store.finalFile(record.getFileKey());
    }

    public synchronized boolean hasAvailableFile(String mediaId) {
        return peekAvailableFile(mediaId) != null;
    }

    public synchronized InputStream openAvailableInput(String mediaId) throws IOException {
        OfflineMediaRecord record = database.find(mediaId);
        if (record == null || !record.isAvailable()) throw new IOException("Offline item is not available");
        if (!store.finalExists(record.getFileKey())) {
            markExpired(mediaId, "offline file missing");
            throw new IOException("Offline audio file is missing");
        }
        database.touch(mediaId, System.currentTimeMillis());
        return store.openFinal(record.getFileKey());
    }

    public synchronized long delete(String mediaId) {
        OfflineMediaRecord record = database.find(mediaId);
        if (record == null) return 0L;
        long removed = store.delete(record.getFileKey());
        database.delete(mediaId);
        return removed;
    }

    public synchronized OfflineCleanupResult cleanupNow() {
        storageManager.reconcile();
        return storageManager.cleanupNow();
    }

    public synchronized void clearAll() { storageManager.clearAll(); }

    public synchronized int reconcile() { return storageManager.reconcile(); }

    public synchronized OfflineMediaStats getStats() {
        OfflineMediaDatabase.DbStats db = database.stats();
        return new OfflineMediaStats(db.total, db.downloading, db.available, db.failed, db.expired,
                db.bytes, preferences.getStorageLimitBytes(), preferences.getReservedFreeBytes(),
                store.availableDeviceBytes());
    }

    private OfflineMediaRecord requireDownloading(String mediaId) throws IOException {
        OfflineMediaRecord record = database.find(mediaId);
        if (record == null) throw new IOException("Offline item is not reserved");
        if (record.getState() != OfflineMediaState.DOWNLOADING) {
            throw new IOException("Offline item is not in DOWNLOADING state");
        }
        return record;
    }

    private static String compact(String value) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= 240 ? safe : safe.substring(0, 240);
    }
}
