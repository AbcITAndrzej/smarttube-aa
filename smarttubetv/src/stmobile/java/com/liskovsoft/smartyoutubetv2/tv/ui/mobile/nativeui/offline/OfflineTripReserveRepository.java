package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;

import java.util.Collections;
import java.util.List;

/** Stage 10 local history/settings facade. Actual audio transfer is delegated to Stage 8. */
public final class OfflineTripReserveRepository {
    public static final String TRIP_PREFIX = "trip:";
    public static final String RECENT_PLAYLIST_ID = TRIP_PREFIX + "recent";
    public static final String FAVORITES_PLAYLIST_ID = TRIP_PREFIX + "favorites";
    public static final String PLAYLIST_COPY_PREFIX = TRIP_PREFIX + "playlist:";

    private static volatile OfflineTripReserveRepository instance;

    private final Context app;
    private final OfflineTripReserveDatabase database;
    private final OfflineMediaRepository media;
    private final OfflineMediaPreferences preferences;
    private final MobileFeatureFlags flags;

    private OfflineTripReserveRepository(Context context) {
        app = context.getApplicationContext();
        database = new OfflineTripReserveDatabase(app);
        media = OfflineMediaRepository.get(app);
        preferences = media.getPreferences();
        flags = new MobileFeatureFlags(app);
    }

    public static OfflineTripReserveRepository get(Context context) {
        OfflineTripReserveRepository current = instance;
        if (current == null) {
            synchronized (OfflineTripReserveRepository.class) {
                current = instance;
                if (current == null) {
                    current = new OfflineTripReserveRepository(context);
                    instance = current;
                }
            }
        }
        return current;
    }

    public boolean isEnabled() {
        return media.isEnabled() && preferences.isTripReserveEnabled()
                && flags.isOfflineTripReserveEnabled()
                && flags.isOfflinePlaylistsEnabled();
    }

    public void recordPlayback(OfflineMediaDescriptor descriptor, String playlistId,
                               String playlistTitle, String playlistThumbnail) {
        if (!isEnabled() || descriptor == null || !descriptor.isValid()) return;
        long now = System.currentTimeMillis();
        database.recordPlayback(descriptor, now);
        if (playlistId != null && !playlistId.trim().isEmpty()) {
            database.recordPlaylist(playlistId, playlistTitle, playlistThumbnail, now);
        }
    }

    public List<OfflineMediaDescriptor> recentItems() {
        if (!isEnabled()) return Collections.emptyList();
        return database.listRecent(preferences.getTripReserveRecentCount());
    }

    public List<OfflineTripReservePlaylistRef> recentPlaylists() {
        if (!isEnabled() || preferences.getTripReservePlaylistCount() <= 0) {
            return Collections.emptyList();
        }
        return database.listRecentPlaylists(preferences.getTripReservePlaylistCount());
    }

    public int historyCount() { return database.recentCount(); }
    public int playlistHistoryCount() { return database.playlistHistoryCount(); }
    public OfflineMediaPreferences getPreferences() { return preferences; }
    public Context getApplicationContext() { return app; }

    public void clearHistory() { database.clear(); }

    /** Releases Stage 10 playlist pins while keeping playback history for a future re-enable. */
    public void releaseManagedPlaylists() {
        OfflinePlaylistRepository playlists = OfflinePlaylistRepository.get(app);
        for (OfflinePlaylistRecord record : playlists.list()) {
            if (isTripReservePlaylistId(record.getPlaylistId())
                    && record.getState() != OfflinePlaylistState.DOWNLOADING) {
                playlists.delete(record.getPlaylistId(), false);
            }
        }
    }

    public int managedPlaylistCount() {
        int count = 0;
        for (OfflinePlaylistRecord record : OfflinePlaylistRepository.get(app).list()) {
            if (isTripReservePlaylistId(record.getPlaylistId())) count++;
        }
        return count;
    }

    public static boolean isTripReservePlaylistId(String playlistId) {
        return playlistId != null && playlistId.startsWith(TRIP_PREFIX);
    }

    public static String copyPlaylistId(String originalPlaylistId) {
        String safe = originalPlaylistId == null ? "" : originalPlaylistId.trim();
        return safe.isEmpty() ? "" : PLAYLIST_COPY_PREFIX + safe;
    }

    public static String originalPlaylistId(String tripPlaylistId) {
        if (tripPlaylistId == null || !tripPlaylistId.startsWith(PLAYLIST_COPY_PREFIX)) return "";
        return tripPlaylistId.substring(PLAYLIST_COPY_PREFIX.length());
    }
}
