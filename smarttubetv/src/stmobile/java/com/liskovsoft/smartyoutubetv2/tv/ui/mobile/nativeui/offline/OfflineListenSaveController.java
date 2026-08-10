package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.content.Context;
import android.os.SystemClock;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;

/**
 * Mobile-player bridge for Stage 7. Counts actual playing time instead of wall-clock time, so an
 * accidental tap/paused item does not immediately consume bandwidth. Radio, live, Shorts, offline
 * playback are intentionally excluded. The selected policy additionally limits capture by player
 * and playlist context. The download service always resolves an audio-only format.
 */
public final class OfflineListenSaveController {
    private final Context app;
    private final OfflineMediaPreferences preferences;
    private final MobileFeatureFlags flags;
    private String mediaId = "";
    private long listenedMs;
    private long lastTickElapsed;
    private boolean triggered;

    public OfflineListenSaveController(Context context) {
        app = context.getApplicationContext();
        preferences = OfflineMediaRepository.get(app).getPreferences();
        flags = new MobileFeatureFlags(app);
    }

    public void onPlayback(Video video, boolean playing, boolean radioPlayback,
                           boolean offlinePlayback, boolean headlessPlayback,
                           boolean playlistPlayback) {
        if (!eligible(video, radioPlayback, offlinePlayback, headlessPlayback,
                playlistPlayback)) {
            reset();
            return;
        }
        String currentId = video.videoId == null ? "" : video.videoId.trim();
        long now = SystemClock.elapsedRealtime();
        if (!currentId.equals(mediaId)) {
            mediaId = currentId;
            listenedMs = 0L;
            triggered = false;
            lastTickElapsed = now;
        }
        if (triggered) {
            lastTickElapsed = now;
            return;
        }
        if (!playing) {
            lastTickElapsed = now;
            return;
        }
        long delta = lastTickElapsed <= 0L ? 0L : Math.max(0L, now - lastTickElapsed);
        // Ignore giant gaps caused by process/background suspension; only count normal player ticks.
        listenedMs += Math.min(delta, 2_000L);
        lastTickElapsed = now;
        long thresholdMs = preferences.getListenSaveThresholdSec() * 1_000L;
        if (listenedMs < thresholdMs) return;
        if (!OfflineNetworkPolicy.isAllowed(app, preferences.isListenSaveWifiOnly())) return;

        OfflineMediaDescriptor descriptor = new OfflineMediaDescriptor(currentId,
                safe(video.getTitleFull()), safe(video.author).isEmpty()
                        ? safe(video.getSecondTitleFull()) : safe(video.author),
                safe(video.getCardImageUrl()), Math.max(0L, video.getDurationMs()), "", "");
        triggered = true;
        MobileDiagnostics.info("OfflineListen", "threshold reached media=" + currentId
                + " listenedMs=" + listenedMs);
        OfflineListenSaveService.capture(app, descriptor);
    }

    public void onMediaSwitch() {
        if (!preferences.isListenSaveCompleteAfterSwitch() && !mediaId.isEmpty()) {
            OfflineListenSaveService.cancel(app, mediaId);
        }
        reset();
    }

    public void reset() {
        mediaId = "";
        listenedMs = 0L;
        lastTickElapsed = 0L;
        triggered = false;
    }

    private boolean eligible(Video video, boolean radioPlayback, boolean offlinePlayback,
                             boolean headlessPlayback, boolean playlistPlayback) {
        if (!flags.isOfflineListenSaveEnabled() || !preferences.isListenSaveEnabled()) return false;
        if (!OfflineMediaRepository.get(app).isEnabled()) return false;
        if (radioPlayback || offlinePlayback || video == null) return false;
        if (video.isLive || video.isUpcoming || video.isShorts || video.isUnplayable) return false;
        boolean resolvedPlaylist = playlistPlayback
                || (video.getPlaylistId() != null && !video.getPlaylistId().trim().isEmpty());
        if (!preferences.shouldListenSave(headlessPlayback, resolvedPlaylist)) return false;
        return video.videoId != null && !video.videoId.trim().isEmpty();
    }

    private static String safe(Object value) { return value == null ? "" : String.valueOf(value); }
}
