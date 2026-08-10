package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileMediaItem;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stage 10 reserve planner. It does not download stream bytes itself: it materializes synthetic
 * Stage 8 playlists and lets the existing robust audio-only downloader handle signed URLs, Range,
 * 403/410 refresh and disk limits.
 */
public final class OfflineTripReserveService extends Service {
    public static final String ACTION_WAKE = "app.smarttube.mobile.offline.trip.WAKE";
    public static final String ACTION_FORCE = "app.smarttube.mobile.offline.trip.FORCE";

    private static final String CHANNEL_ID = "smarttube_trip_reserve";
    private static final int NOTIFICATION_ID = 0x531A;
    private static final long FAVORITES_REFRESH_MS = 6L * 60L * 60L * 1000L;
    private static final int MAX_PLAYLIST_PAGES = 40;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SmartTube-TripReserve");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private volatile boolean forceRequested;

    private OfflineTripReserveRepository reserve;
    private OfflinePlaylistRepository playlists;
    private OfflineMediaPreferences preferences;
    private ContentService content;

    @Override public void onCreate() {
        super.onCreate();
        reserve = OfflineTripReserveRepository.get(this);
        playlists = OfflinePlaylistRepository.get(this);
        preferences = OfflineMediaRepository.get(this).getPreferences();
        content = YouTubeServiceManager.instance().getContentService();
        ensureChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification(getString(R.string.mobile_offline_trip_preparing)));
        if (intent != null && ACTION_FORCE.equals(intent.getAction())) forceRequested = true;
        schedule();
        return START_NOT_STICKY;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    public static void wake(Context context) { start(context, ACTION_WAKE); }
    public static void force(Context context) { start(context, ACTION_FORCE); }

    private static void start(Context context, String action) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        // Avoid even starting a foreground service (and flashing its notification) when the
        // user/master flag has Stage 10 disabled. This is especially important for AA Like/Unlike.
        if (!OfflineTripReserveRepository.get(app).isEnabled()) return;
        try {
            ContextCompat.startForegroundService(app,
                    new Intent(app, OfflineTripReserveService.class).setAction(action));
        } catch (RuntimeException error) {
            MobileDiagnostics.error("P20-TripReserve", "unable to start reserve planner", error);
        }
    }

    private void schedule() {
        if (!scheduled.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try { reconcile(forceRequested); }
            catch (Throwable error) {
                MobileDiagnostics.error("P20-TripReserve", "reserve reconcile failed", error);
            } finally {
                forceRequested = false;
                scheduled.set(false);
                stopForeground(true);
                stopSelf();
            }
        });
    }

    private void reconcile(boolean force) {
        if (!reserve.isEnabled()) {
            MobileDiagnostics.info("P20-TripReserve", "disabled; planner exits");
            return;
        }
        MobileDiagnostics.info("P20-TripReserve", "reconcile start force=" + force);

        boolean changed = syncRecent();
        changed |= syncRecentPlaylists();

        long now = System.currentTimeMillis();
        boolean favoriteDue = force || preferences.getTripReserveLastSyncMs() <= 0L
                || now - preferences.getTripReserveLastSyncMs() >= FAVORITES_REFRESH_MS;
        if (!preferences.isTripReserveFavoritesEnabled()) {
            changed |= removeTripPlaylist(OfflineTripReserveRepository.FAVORITES_PLAYLIST_ID);
        } else if (favoriteDue && OfflineNetworkPolicy.isAllowed(this,
                preferences.isTripReserveWifiOnly())) {
            changed |= syncFavorites();
        }

        pruneUndesiredTripPlaylistCopies();
        if (changed || playlists.nextRunnablePlaylist() != null) {
            OfflinePlaylistDownloadService.wake(this);
        }
        MobileDiagnostics.info("P20-TripReserve", "reconcile done changed=" + changed
                + " recentHistory=" + reserve.historyCount()
                + " playlistHistory=" + reserve.playlistHistoryCount());
    }

    private boolean syncRecent() {
        List<OfflineMediaDescriptor> recent = reserve.recentItems();
        List<MobileMediaItem> items = new ArrayList<>();
        for (OfflineMediaDescriptor item : recent) {
            items.add(new MobileMediaItem(item.getMediaId(), MobileMediaItem.Kind.VIDEO,
                    item.getTitle(), item.getAuthor(), item.getThumbnailUrl(), "", 0L,
                    item.getDurationMs(), true));
        }
        if (items.isEmpty()) return removeTripPlaylist(OfflineTripReserveRepository.RECENT_PLAYLIST_ID);
        OfflinePlaylistRecord before = playlists.find(OfflineTripReserveRepository.RECENT_PLAYLIST_ID);
        OfflinePlaylistRecord after = playlists.replaceTripReserve(
                OfflineTripReserveRepository.RECENT_PLAYLIST_ID,
                getString(R.string.mobile_offline_trip_recent_title), "", items);
        return changed(before, after, items.size());
    }

    private boolean syncFavorites() {
        int limit = preferences.getTripReserveFavoriteCount();
        List<MobileMediaItem> favorites = loadLikedMusic(limit);
        if (favorites.isEmpty()) {
            MobileDiagnostics.warn("P20-TripReserve", "liked playlist unavailable/empty; keeping previous reserve");
            return false;
        }
        OfflinePlaylistRecord before = playlists.find(OfflineTripReserveRepository.FAVORITES_PLAYLIST_ID);
        OfflinePlaylistRecord after = playlists.replaceTripReserve(
                OfflineTripReserveRepository.FAVORITES_PLAYLIST_ID,
                getString(R.string.mobile_offline_trip_favorites_title),
                favorites.get(0).getThumbnailUrl(), favorites);
        preferences.setTripReserveLastSyncMs(System.currentTimeMillis());
        MobileDiagnostics.info("P20-TripReserve", "favorites planned=" + favorites.size());
        return changed(before, after, favorites.size());
    }

    private boolean syncRecentPlaylists() {
        int count = preferences.getTripReservePlaylistCount();
        if (count <= 0) return false;
        int trackLimit = preferences.getTripReservePlaylistTrackLimit();
        boolean changed = false;
        for (OfflineTripReservePlaylistRef ref : reserve.recentPlaylists()) {
            String tripId = OfflineTripReserveRepository.copyPlaylistId(ref.getPlaylistId());
            if (tripId.isEmpty() || playlists.find(tripId) != null) continue;
            if (!OfflineNetworkPolicy.isAllowed(this, preferences.isTripReserveWifiOnly())) break;
            List<MobileMediaItem> items = loadPlaylist(ref.getPlaylistId(), trackLimit);
            if (items.isEmpty()) continue;
            String title = ref.getTitle().isEmpty()
                    ? getString(R.string.mobile_offline_trip_playlist_fallback)
                    : ref.getTitle();
            OfflinePlaylistRecord created = playlists.enqueueTripReserve(tripId,
                    getString(R.string.mobile_offline_trip_playlist_title, title),
                    ref.getThumbnailUrl(), items);
            if (created != null) {
                changed = true;
                MobileDiagnostics.info("P20-TripReserve", "planned playlist=" + ref.getPlaylistId()
                        + " tracks=" + items.size());
            }
        }
        return changed;
    }

    private void pruneUndesiredTripPlaylistCopies() {
        Set<String> desired = new HashSet<>();
        for (OfflineTripReservePlaylistRef ref : reserve.recentPlaylists()) {
            String id = OfflineTripReserveRepository.copyPlaylistId(ref.getPlaylistId());
            if (!id.isEmpty()) desired.add(id);
        }
        for (OfflinePlaylistRecord record : playlists.list()) {
            String id = record.getPlaylistId();
            if (id == null || !id.startsWith(OfflineTripReserveRepository.PLAYLIST_COPY_PREFIX)) continue;
            if (desired.contains(id)) continue;
            if (record.getState() == OfflinePlaylistState.DOWNLOADING) continue;
            playlists.delete(id, false);
            MobileDiagnostics.info("P20-TripReserve", "released old playlist reserve=" + id);
        }
    }

    private boolean removeTripPlaylist(String id) {
        OfflinePlaylistRecord existing = playlists.find(id);
        if (existing == null || existing.getState() == OfflinePlaylistState.DOWNLOADING) return false;
        playlists.delete(id, false);
        return true;
    }

    private List<MobileMediaItem> loadLikedMusic(int limit) {
        if (content == null || limit <= 0) return Collections.emptyList();
        try {
            // YouTubeContentService emits the account's Liked Music shelf as the first partial
            // MUSIC result. Reuse that public ContentService contract instead of hard-coding a
            // private browse endpoint/URL in Stage 10.
            List<MediaGroup> groups = content.getMusicObserve()
                    .blockingFirst(Collections.emptyList());
            if (groups == null || groups.isEmpty() || groups.get(0) == null) {
                return Collections.emptyList();
            }
            return loadGroup(groups.get(0), limit);
        } catch (Throwable error) {
            MobileDiagnostics.warn("P20-TripReserve", "liked music unavailable error="
                    + error.getMessage());
            return Collections.emptyList();
        }
    }

    private List<MobileMediaItem> loadPlaylist(String playlistId, int limit) {
        if (content == null || playlistId == null || playlistId.trim().isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        Video reference = new Video();
        reference.playlistId = playlistId;
        reference.title = "Offline reserve";
        MediaGroup group = content.getGroup(reference.toMediaItem());
        return loadGroup(group, limit);
    }

    private List<MobileMediaItem> loadGroup(MediaGroup group, int limit) {
        if (group == null || limit <= 0) return Collections.emptyList();
        ArrayList<MobileMediaItem> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        int pages = 0;
        MediaGroup page = group;
        while (page != null && pages++ < MAX_PLAYLIST_PAGES && out.size() < limit) {
            List<MediaItem> mediaItems = page.getMediaItems();
            if (mediaItems != null) {
                for (MediaItem item : mediaItems) {
                    MobileMediaItem mapped = map(item);
                    if (mapped == null || !seen.add(mapped.getId())) continue;
                    out.add(mapped);
                    if (out.size() >= limit) break;
                }
            }
            if (out.size() >= limit || page.getNextPageKey() == null
                    || page.getNextPageKey().trim().isEmpty()) break;
            page = content.continueGroup(page);
        }
        return out;
    }

    private static MobileMediaItem map(MediaItem item) {
        if (item == null || item.isLive() || item.isUpcoming() || item.isShorts()) return null;
        String id = safe(item.getVideoId());
        if (id.isEmpty()) return null;
        String subtitle = safe(item.getAuthor());
        if (subtitle.isEmpty() && item.getSecondTitle() != null) subtitle = item.getSecondTitle().toString();
        return new MobileMediaItem(id, MobileMediaItem.Kind.VIDEO, safe(item.getTitle()), subtitle,
                item.getCardImageUrl(), safe(item.getBadgeText()), 0L,
                Math.max(0L, item.getDurationMs()), true, item.getPlaylistId());
    }

    private static boolean changed(OfflinePlaylistRecord before, OfflinePlaylistRecord after, int count) {
        if (after == null) return false;
        return before == null || before.getTotalCount() != count;
    }

    private Notification notification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.mobile_ic_notification)
                .setContentTitle(getString(R.string.mobile_offline_trip_service_title))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.mobile_offline_trip_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.mobile_offline_trip_notification_channel_summary));
        manager.createNotificationChannel(channel);
    }

    private static String safe(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
