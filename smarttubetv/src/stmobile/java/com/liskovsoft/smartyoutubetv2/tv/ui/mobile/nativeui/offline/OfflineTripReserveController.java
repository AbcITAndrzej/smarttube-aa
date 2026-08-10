package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.content.Context;
import android.os.SystemClock;

import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;

/** Records meaningful playback for Stage 10 without starting downloads on accidental taps. */
public final class OfflineTripReserveController {
    private static final long RECORD_AFTER_MS = 10_000L;

    private final Context app;
    private final OfflineTripReserveRepository repository;
    private String mediaId = "";
    private long listenedMs;
    private long lastTickElapsed;
    private boolean recorded;

    public OfflineTripReserveController(Context context) {
        app = context.getApplicationContext();
        repository = OfflineTripReserveRepository.get(app);
    }

    public void onPlayback(Video video, boolean playing, boolean radioPlayback,
                           boolean offlinePlayback) {
        if (!eligible(video, radioPlayback, offlinePlayback)) {
            reset();
            return;
        }
        String currentId = safe(video.videoId);
        long now = SystemClock.elapsedRealtime();
        if (!currentId.equals(mediaId)) {
            mediaId = currentId;
            listenedMs = 0L;
            lastTickElapsed = now;
            recorded = false;
        }
        if (recorded || !playing) {
            lastTickElapsed = now;
            return;
        }
        long delta = lastTickElapsed <= 0L ? 0L : Math.max(0L, now - lastTickElapsed);
        listenedMs += Math.min(delta, 2_000L);
        lastTickElapsed = now;
        if (listenedMs < RECORD_AFTER_MS) return;

        String author = safe(video.author);
        if (author.isEmpty()) author = safe(video.getSecondTitleFull());
        OfflineMediaDescriptor descriptor = new OfflineMediaDescriptor(currentId,
                safe(video.getTitleFull()), author, safe(video.getCardImageUrl()),
                Math.max(0L, video.getDurationMs()), "", "");
        String playlistId = safe(video.playlistId);
        String playlistTitle = "";
        PlaylistInfo info = video.playlistInfo;
        if (info != null) {
            if (!safe(info.getPlaylistId()).isEmpty()) playlistId = safe(info.getPlaylistId());
            playlistTitle = safe(info.getTitle());
        }
        repository.recordPlayback(descriptor, playlistId, playlistTitle,
                safe(video.getCardImageUrl()));
        recorded = true;
        MobileDiagnostics.info("P20-TripReserve", "recorded recent media=" + currentId
                + (playlistId.isEmpty() ? "" : " playlist=" + playlistId));
        OfflineTripReserveService.wake(app);
    }

    public void onMediaSwitch() { reset(); }
    public void reset() {
        mediaId = "";
        listenedMs = 0L;
        lastTickElapsed = 0L;
        recorded = false;
    }

    private boolean eligible(Video video, boolean radioPlayback, boolean offlinePlayback) {
        if (!repository.isEnabled() || radioPlayback || offlinePlayback || video == null) return false;
        if (video.isLive || video.isUpcoming || video.isShorts || video.isUnplayable) return false;
        return !safe(video.videoId).isEmpty();
    }

    private static String safe(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
