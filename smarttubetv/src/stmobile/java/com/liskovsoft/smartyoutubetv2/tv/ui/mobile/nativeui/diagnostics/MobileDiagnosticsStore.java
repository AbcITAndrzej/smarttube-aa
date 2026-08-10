package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.SystemClock;

import com.google.android.exoplayer2.Format;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.track.MediaTrack;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.SponsorBlockData;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive.AndroidAutoPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobilePlaybackSnapshot;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineListenSaveRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaStats;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistRecord;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistState;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineTripReserveRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.MobileEnhancementPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.MobileInstantPlayPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.MobilePlayerPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.performance.MobilePerformanceMonitor;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioTimeShiftController;
import com.liskovsoft.youtubeapi.block.SponsorBlockService;
import com.liskovsoft.youtubeapi.dearrow.DeArrowService;

import java.net.URI;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small process-local telemetry store used only for user-visible diagnostics.
 *
 * <p>No report is uploaded anywhere. Persistent fields contain counters only. Current media/source
 * details live in memory and disappear when the process exits.</p>
 */
public final class MobileDiagnosticsStore {
    private static final String PREF_FILE = "smarttube_mobile_diagnostics";
    private static final String KEY_TOTAL_PREPARES = "total_prepares";
    private static final String KEY_TOTAL_ERRORS = "total_errors";
    private static final String KEY_TOTAL_TRANSIENT_403 = "total_transient_403";
    private static final String KEY_TOTAL_RECOVERED_403 = "total_recovered_403";
    private static final String KEY_TOTAL_ENGINE_RESTARTS = "total_engine_restarts";
    private static final String KEY_TOTAL_PLAYBACK_RELOADS = "total_playback_reloads";
    private static final String KEY_TOTAL_DVR_FALLBACKS = "total_dvr_fallbacks";
    private static final String KEY_TOTAL_INSTANT_403_FALLBACKS = "total_instant_403_fallbacks";
    private static final String KEY_TOTAL_INSTANT_WATCHDOG_RELOADS = "total_instant_watchdog_reloads";
    private static final String KEY_TOTAL_INSTANT_TIMEOUTS = "total_instant_timeouts";
    private static final String KEY_TOTAL_PAGING_REQUESTS = "total_paging_requests";
    private static final String KEY_TOTAL_PAGING_ERRORS = "total_paging_errors";
    private static final String KEY_TOTAL_RADIO_PAGES = "total_radio_pages";
    private static final String KEY_TOTAL_RADIO_REMOTE_SEARCHES = "total_radio_remote_searches";
    private static final String KEY_TOTAL_RADIO_FAILOVER_ATTEMPTS = "total_radio_failover_attempts";
    private static final String KEY_TOTAL_RADIO_FAILOVER_SUCCESSES = "total_radio_failover_successes";
    private static final String KEY_TOTAL_MEDIA3_ACTIVATIONS = "total_media3_activations";
    private static final String KEY_TOTAL_MEDIA3_ERRORS = "total_media3_errors";
    private static final String KEY_TOTAL_MEDIA3_LEGACY_FALLBACKS = "total_media3_legacy_fallbacks";

    private static volatile MobileDiagnosticsStore instance;

    private final Context app;
    private final SharedPreferences preferences;
    private final MobileFeatureFlags featureFlags;
    private final AtomicLong reportSequence = new AtomicLong();

    private volatile String playbackOwner = "NONE";
    private volatile String state = "IDLE";
    private volatile String mediaId = "";
    private volatile String title = "";
    private volatile boolean radio;
    private volatile long prepareStartedElapsedMs;
    private volatile long readyElapsedMs;
    private volatile long firstPlayElapsedMs;
    private volatile long lastPrepareDurationMs = -1L;
    private volatile long lastFirstPlayDurationMs = -1L;
    private volatile String sourceKind = "unknown";
    private volatile String sourceHost = "unknown";
    private volatile String videoFormat = "none";
    private volatile String audioFormat = "none";
    private volatile String subtitleFormat = "none";
    private volatile String lastError = "none";
    private volatile long lastErrorWallMs;
    private volatile boolean forbiddenRecoveryPending;
    private volatile boolean forbiddenRetryCounted;
    private volatile long positionMs;
    private volatile long durationMs;
    private volatile long bufferedMs;
    private volatile float speed = 1f;
    private volatile int sponsorMarkers;
    private volatile boolean radioDvrActive;
    private volatile boolean radioDvrSeekable;
    private volatile long radioDvrWindowMs;
    private volatile long radioDvrBytes;
    private volatile int metadataCacheEntries;
    private volatile long lastMetadataFetchMs = -1L;
    private volatile String lastMetadataFetch = "none";
    private volatile String instantPlayState = "IDLE";
    private volatile int instantPlayForbiddenFallbacks;
    private volatile int instantPlayWatchdogReloads;
    private volatile long instantPlayLastReadyMs = -1L;
    private volatile long instantPlayLastTimeoutMs = -1L;
    private volatile String paginationSurface = "none";
    private volatile int paginationBeforeItems;
    private volatile int paginationAfterItems;
    private volatile boolean paginationHasMore;
    private volatile String paginationLastError = "none";
    private volatile int radioCatalogStations;
    private volatile int radioCatalogOffset;
    private volatile boolean radioCatalogEndReached;
    private volatile int radioRecentCount;
    private volatile String radioRemoteSearch = "none";
    private volatile int radioRemoteSearchResults;
    private volatile int radioFailoverCandidates;
    private volatile String radioFailoverState = "IDLE";
    private volatile String playbackEngine = "legacy ExoPlayer 2";
    private volatile String media3SourceKind = "none";
    private volatile String media3LastEvent = "none";

    private MobileDiagnosticsStore(Context context) {
        app = context.getApplicationContext();
        preferences = app.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        featureFlags = new MobileFeatureFlags(app);
        syncCaptureFlag();
    }

    public static MobileDiagnosticsStore get(Context context) {
        MobileDiagnosticsStore current = instance;
        if (current == null) {
            synchronized (MobileDiagnosticsStore.class) {
                current = instance;
                if (current == null) {
                    current = new MobileDiagnosticsStore(context);
                    instance = current;
                }
            }
        }
        return current;
    }

    public void syncCaptureFlag() {
        MobileDiagnostics.setCaptureEnabled(featureFlags.isDiagnosticsCaptureEnabled());
    }

    public void onPrepare(String owner, String id, boolean isRadio) {
        if (!capture()) return;
        playbackOwner = safe(owner, "UNKNOWN");
        mediaId = safe(id, "");
        title = "";
        radio = isRadio;
        state = "PREPARING";
        prepareStartedElapsedMs = SystemClock.elapsedRealtime();
        readyElapsedMs = 0L;
        firstPlayElapsedMs = 0L;
        lastPrepareDurationMs = -1L;
        lastFirstPlayDurationMs = -1L;
        sourceKind = "unknown";
        sourceHost = "unknown";
        videoFormat = "none";
        audioFormat = "none";
        subtitleFormat = "none";
        lastError = "none";
        lastErrorWallMs = 0L;
        forbiddenRecoveryPending = false;
        forbiddenRetryCounted = false;
        sponsorMarkers = 0;
        media3SourceKind = "none";
        media3LastEvent = "none";
        instantPlayState = "PREPARING";
        instantPlayForbiddenFallbacks = 0;
        instantPlayWatchdogReloads = 0;
        instantPlayLastReadyMs = -1L;
        instantPlayLastTimeoutMs = -1L;
        increment(KEY_TOTAL_PREPARES);
    }


    public void onPlaybackEngine(String engineName, String sourceKind) {
        if (!capture()) return;
        playbackEngine = safe(engineName, "unknown");
        media3SourceKind = safe(sourceKind, "none");
        if (playbackEngine.toLowerCase(Locale.ROOT).contains("media3")) {
            increment(KEY_TOTAL_MEDIA3_ACTIVATIONS);
        }
    }

    public void onMedia3Error(String sourceKind, Throwable error) {
        if (!capture()) return;
        media3SourceKind = safe(sourceKind, media3SourceKind);
        media3LastEvent = "ERROR " + compactError(error);
        increment(KEY_TOTAL_MEDIA3_ERRORS);
    }

    public void onMedia3LegacyFallback(String sourceKind, Throwable error) {
        if (!capture()) return;
        media3SourceKind = safe(sourceKind, media3SourceKind);
        media3LastEvent = "FALLBACK " + compactError(error);
        playbackEngine = "legacy ExoPlayer 2 (Media3 fallback)";
        increment(KEY_TOTAL_MEDIA3_LEGACY_FALLBACKS);
    }

    public void onMedia3Ready(String sourceKind) {
        if (!capture()) return;
        media3SourceKind = safe(sourceKind, media3SourceKind);
        media3LastEvent = "READY";
    }


    public void onPlayerReady(boolean playWhenReady) {
        if (!capture()) return;
        long now = SystemClock.elapsedRealtime();
        if (prepareStartedElapsedMs > 0L && readyElapsedMs == 0L) {
            readyElapsedMs = now;
            lastPrepareDurationMs = Math.max(0L, now - prepareStartedElapsedMs);
        }
        state = playWhenReady ? "READY" : "READY/PAUSED";
        if (forbiddenRecoveryPending) {
            forbiddenRecoveryPending = false;
            forbiddenRetryCounted = false;
            increment(KEY_TOTAL_RECOVERED_403);
        }
    }

    public void onSource(String kind, String url) {
        if (!capture()) return;
        sourceKind = safe(kind, "unknown");
        sourceHost = extractHost(url);
        if (forbiddenRecoveryPending && !forbiddenRetryCounted) {
            forbiddenRetryCounted = true;
            increment(KEY_TOTAL_PLAYBACK_RELOADS);
        }
    }

    public void onSourceKind(String kind) {
        if (!capture()) return;
        sourceKind = safe(kind, "unknown");
    }

    public void onTransient403(Throwable error) {
        if (!capture()) return;
        forbiddenRecoveryPending = true;
        forbiddenRetryCounted = false;
        lastError = compactError(error);
        lastErrorWallMs = System.currentTimeMillis();
        increment(KEY_TOTAL_TRANSIENT_403);
    }

    public void onPlaybackError(Throwable error) {
        if (!capture()) return;
        state = "ERROR";
        lastError = compactError(error);
        lastErrorWallMs = System.currentTimeMillis();
        increment(KEY_TOTAL_ERRORS);
    }

    public void onEngineRestart() {
        if (!capture()) return;
        increment(KEY_TOTAL_ENGINE_RESTARTS);
    }

    public void onPlaybackReload() {
        if (!capture()) return;
        increment(KEY_TOTAL_PLAYBACK_RELOADS);
    }

    public void onRadioDvrFallback() {
        if (!capture()) return;
        increment(KEY_TOTAL_DVR_FALLBACKS);
    }

    public void onInstantPlayBegin(boolean enabled, String id) {
        if (!capture()) return;
        instantPlayState = enabled ? "ARMED" : "DISABLED";
        instantPlayForbiddenFallbacks = 0;
        instantPlayWatchdogReloads = 0;
        instantPlayLastReadyMs = -1L;
        instantPlayLastTimeoutMs = -1L;
    }

    public void onInstantPlayForbiddenFallback(int attempt) {
        if (!capture()) return;
        instantPlayState = "RECOVERING_403";
        instantPlayForbiddenFallbacks = Math.max(instantPlayForbiddenFallbacks, Math.max(0, attempt));
        increment(KEY_TOTAL_INSTANT_403_FALLBACKS);
    }

    public void onInstantPlayWatchdogReload() {
        if (!capture()) return;
        instantPlayState = "WATCHDOG_RELOAD";
        instantPlayWatchdogReloads++;
        increment(KEY_TOTAL_INSTANT_WATCHDOG_RELOADS);
    }

    public void onInstantPlayReady(long elapsedMs, int forbiddenFallbacks, int watchdogReloads) {
        if (!capture()) return;
        instantPlayState = "READY";
        instantPlayLastReadyMs = Math.max(0L, elapsedMs);
        instantPlayForbiddenFallbacks = Math.max(instantPlayForbiddenFallbacks, forbiddenFallbacks);
        instantPlayWatchdogReloads = Math.max(instantPlayWatchdogReloads, watchdogReloads);
    }

    public void onInstantPlayTimeout(long elapsedMs) {
        if (!capture()) return;
        instantPlayState = "TIMEOUT";
        instantPlayLastTimeoutMs = Math.max(0L, elapsedMs);
        increment(KEY_TOTAL_INSTANT_TIMEOUTS);
    }

    public void onPaginationRequest(String surface) {
        if (!capture()) return;
        paginationSurface = safe(surface, "unknown");
        paginationLastError = "none";
        increment(KEY_TOTAL_PAGING_REQUESTS);
    }

    public void onPaginationSuccess(String surface, int beforeItems, int afterItems, boolean hasMore) {
        if (!capture()) return;
        paginationSurface = safe(surface, "unknown");
        paginationBeforeItems = Math.max(0, beforeItems);
        paginationAfterItems = Math.max(0, afterItems);
        paginationHasMore = hasMore;
        paginationLastError = "none";
    }

    public void onPaginationError(String surface, Throwable error) {
        if (!capture()) return;
        paginationSurface = safe(surface, "unknown");
        paginationLastError = compactError(error);
        increment(KEY_TOTAL_PAGING_ERRORS);
    }

    public void onRadioCatalogState(int stationCount, int nextOffset, boolean endReached) {
        if (!capture()) return;
        radioCatalogStations = Math.max(0, stationCount);
        radioCatalogOffset = Math.max(0, nextOffset);
        radioCatalogEndReached = endReached;
    }

    public void onRadioCatalogPage(int stationCount, int nextOffset, boolean endReached) {
        onRadioCatalogState(stationCount, nextOffset, endReached);
        if (!capture()) return;
        increment(KEY_TOTAL_RADIO_PAGES);
    }

    public void onRadioRecentChanged(int count) {
        if (!capture()) return;
        radioRecentCount = Math.max(0, count);
    }

    public void onRadioRemoteSearchStarted(String query, String country, String tag) {
        if (!capture()) return;
        radioRemoteSearch = "query=" + trim(safe(query, ""), 40)
                + " country=" + safe(country, "") + " tag=" + trim(safe(tag, ""), 24);
        radioRemoteSearchResults = 0;
        increment(KEY_TOTAL_RADIO_REMOTE_SEARCHES);
    }

    public void onRadioRemoteSearchFinished(int results, int added, Throwable error) {
        if (!capture()) return;
        radioRemoteSearchResults = Math.max(0, results);
        radioRemoteSearch += error == null
                ? " results=" + Math.max(0, results) + " added=" + Math.max(0, added)
                : " error=" + compactError(error);
    }

    public void onRadioFailoverCandidates(int count) {
        if (!capture()) return;
        radioFailoverCandidates = Math.max(0, count);
    }

    public void onRadioFailoverAttempt(String url) {
        if (!capture()) return;
        radioFailoverState = "ATTEMPT " + extractHost(url);
        increment(KEY_TOTAL_RADIO_FAILOVER_ATTEMPTS);
    }

    public void onRadioFailoverSuccess(String url) {
        if (!capture()) return;
        radioFailoverState = "OK " + extractHost(url);
        increment(KEY_TOTAL_RADIO_FAILOVER_SUCCESSES);
    }

    public void onRadioFailoverFailed() {
        if (!capture()) return;
        radioFailoverState = "FAILED";
    }

    public void onMetadataCacheSize(int entries) {
        if (!capture()) return;
        metadataCacheEntries = Math.max(0, entries);
    }

    public void onMetadataFetch(String type, int itemCount, long durationMs, boolean success) {
        if (!capture()) return;
        lastMetadataFetchMs = Math.max(0L, durationMs);
        lastMetadataFetch = safe(type, "metadata") + " items=" + Math.max(0, itemCount)
                + " result=" + (success ? "OK" : "ERROR");
    }

    public void onSnapshot(MobilePlaybackSnapshot snapshot,
                           FormatItem selectedVideo,
                           FormatItem selectedAudio,
                           FormatItem selectedSubtitle,
                           boolean isRadio,
                           RadioTimeShiftController dvr) {
        if (!capture() || snapshot == null) return;
        long now = SystemClock.elapsedRealtime();
        mediaId = safe(snapshot.getMediaId(), mediaId);
        title = safe(snapshot.getTitle(), "");
        radio = isRadio;
        positionMs = snapshot.getPositionMs();
        durationMs = snapshot.getDurationMs();
        bufferedMs = snapshot.getBufferedPositionMs();
        speed = snapshot.getSpeed();
        sponsorMarkers = snapshot.getSeekBarSegments().size();

        if (snapshot.isEnded()) state = "ENDED";
        else if (snapshot.isBuffering()) state = "BUFFERING";
        else if (snapshot.isPlaying()) state = "PLAYING";
        else if (snapshot.isPrepared()) state = "READY/PAUSED";
        else state = "IDLE";

        if (snapshot.isPlaying() && prepareStartedElapsedMs > 0L && firstPlayElapsedMs == 0L) {
            firstPlayElapsedMs = now;
            lastFirstPlayDurationMs = Math.max(0L, now - prepareStartedElapsedMs);
        }

        videoFormat = formatDetails(selectedVideo, true);
        audioFormat = formatDetails(selectedAudio, false);
        subtitleFormat = formatDetails(selectedSubtitle, false);

        radioDvrActive = dvr != null && dvr.isActive();
        radioDvrSeekable = dvr != null && dvr.canSeek();
        radioDvrWindowMs = dvr == null ? 0L : Math.max(0L, dvr.getWindowDurationMs());
        radioDvrBytes = dvr == null ? 0L : Math.max(0L, dvr.getBufferedBytes());
    }

    public void resetCounters() {
        preferences.edit().clear().apply();
        MobileDiagnostics.clearRecentEvents();
        lastError = "none";
        lastErrorWallMs = 0L;
        MobilePerformanceMonitor.get(app).resetSessionMetrics();
    }

    public String buildReport(boolean includeRecentEvents) {
        syncCaptureFlag();
        StringBuilder out = new StringBuilder(4096);
        long seq = reportSequence.incrementAndGet();
        out.append("SmartTube Mobile diagnostics #").append(seq).append('\n');
        out.append("Generated: ").append(DateFormat.getDateTimeInstance().format(new Date())).append('\n');
        appendApp(out);
        appendDevice(out);
        appendPlayback(out);
        appendNetworkAndRecovery(out);
        MobilePerformanceMonitor.get(app).appendReport(out);
        appendIntegrations(out);
        appendFeatureFlags(out);
        appendCounters(out);
        if (includeRecentEvents && featureFlags.isRecentEventsEnabled()) {
            out.append("\n[Recent events]\n");
            List<String> events = MobileDiagnostics.getRecentEvents(80);
            if (events.isEmpty()) out.append("none\n");
            else for (String event : events) out.append(event).append('\n');
        }
        return out.toString();
    }

    private void appendApp(StringBuilder out) {
        out.append("\n[App]\n");
        try {
            PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
            out.append("package: ").append(app.getPackageName()).append('\n');
            out.append("version: ").append(info.versionName).append(" (").append(info.versionCode).append(")\n");
        } catch (Throwable error) {
            out.append("package: ").append(app.getPackageName()).append('\n');
            out.append("version: unavailable\n");
        }
    }

    private void appendDevice(StringBuilder out) {
        out.append("\n[Device]\n");
        out.append("android: ").append(Build.VERSION.RELEASE).append(" / API ")
                .append(Build.VERSION.SDK_INT).append('\n');
        out.append("device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n');
        out.append("abi: ").append(Build.SUPPORTED_ABIS == null || Build.SUPPORTED_ABIS.length == 0
                ? "unknown" : Build.SUPPORTED_ABIS[0]).append('\n');
    }

    private void appendPlayback(StringBuilder out) {
        out.append("\n[Playback]\n");
        out.append("owner: ").append(playbackOwner).append('\n');
        out.append("engine: ").append(playbackEngine).append('\n');
        out.append("state: ").append(state).append('\n');
        out.append("media: ").append(maskMediaId(mediaId)).append('\n');
        out.append("title: ").append(trim(title, 120)).append('\n');
        out.append("radio: ").append(radio).append('\n');
        out.append("position: ").append(formatDuration(positionMs)).append(" / ")
                .append(formatDuration(durationMs)).append('\n');
        out.append("buffered: ").append(formatDuration(bufferedMs)).append('\n');
        out.append("speed: ").append(String.format(Locale.US, "%.2fx", speed)).append('\n');
        out.append("prepare_to_ready: ").append(formatMetric(lastPrepareDurationMs)).append('\n');
        out.append("prepare_to_play: ").append(formatMetric(lastFirstPlayDurationMs)).append('\n');
        out.append("video: ").append(videoFormat).append('\n');
        out.append("audio: ").append(audioFormat).append('\n');
        out.append("subtitles: ").append(subtitleFormat).append('\n');
        out.append("timeline_markers: ").append(sponsorMarkers).append('\n');
    }

    private void appendNetworkAndRecovery(StringBuilder out) {
        out.append("\n[Source / recovery]\n");
        out.append("source_kind: ").append(sourceKind).append('\n');
        out.append("source_host: ").append(sourceHost).append('\n');
        out.append("last_error: ").append(lastError).append('\n');
        if (lastErrorWallMs > 0L) {
            out.append("last_error_time: ")
                    .append(DateFormat.getDateTimeInstance().format(new Date(lastErrorWallMs)))
                    .append('\n');
        }
        out.append("403_recovery_pending: ").append(forbiddenRecoveryPending).append('\n');
        out.append("instant_play_state: ").append(instantPlayState).append('\n');
        out.append("instant_play_403_fallbacks: ").append(instantPlayForbiddenFallbacks).append('\n');
        out.append("instant_play_watchdog_reloads: ").append(instantPlayWatchdogReloads).append('\n');
        out.append("instant_play_ready_after: ").append(formatMetric(instantPlayLastReadyMs)).append('\n');
        out.append("instant_play_timeout_after: ").append(formatMetric(instantPlayLastTimeoutMs)).append('\n');
    }

    private void appendIntegrations(StringBuilder out) {
        SponsorBlockData sponsor = SponsorBlockData.instance(app);
        DeArrowData deArrow = DeArrowData.instance(app);
        MobileEnhancementPreferences mobileEnhancements = new MobileEnhancementPreferences(app);
        MobileInstantPlayPreferences instantPlay = new MobileInstantPlayPreferences(app);
        MobilePlayerPreferences playerPreferences = new MobilePlayerPreferences(app);
        RadioPreferences radioPreferences = new RadioPreferences(app);
        OfflineMediaRepository offlineRepository = OfflineMediaRepository.get(app);
        OfflineMediaPreferences offlinePreferences = offlineRepository.getPreferences();
        OfflineMediaStats offlineStats = offlineRepository.getStats();
        OfflinePlaylistRepository offlinePlaylists = OfflinePlaylistRepository.get(app);
        OfflineListenSaveRepository listenSave = OfflineListenSaveRepository.get(app);
        OfflineTripReserveRepository tripReserve = OfflineTripReserveRepository.get(app);
        int playlistQueued = 0, playlistDownloading = 0, playlistPaused = 0;
        int playlistAvailable = 0, playlistPartial = 0, playlistFailed = 0;
        for (OfflinePlaylistRecord playlist : offlinePlaylists.list()) {
            OfflinePlaylistState playlistState = playlist.getState();
            if (playlistState == OfflinePlaylistState.QUEUED) playlistQueued++;
            else if (playlistState == OfflinePlaylistState.DOWNLOADING) playlistDownloading++;
            else if (playlistState == OfflinePlaylistState.PAUSED) playlistPaused++;
            else if (playlistState == OfflinePlaylistState.AVAILABLE) playlistAvailable++;
            else if (playlistState == OfflinePlaylistState.PARTIAL) playlistPartial++;
            else if (playlistState == OfflinePlaylistState.FAILED) playlistFailed++;
        }
        AndroidAutoPreferences aa = new AndroidAutoPreferences(app);
        out.append("\n[Integrations]\n");
        out.append("SponsorBlock: enabled=").append(sponsor.isSponsorBlockEnabled())
                .append(" categories=").append(sponsor.getEnabledCategories().size())
                .append(" colorMarkers=").append(sponsor.isColorMarkersEnabled())
                .append(" mobileTimeline=")
                .append(mobileEnhancements.isSponsorBlockSeekBarMarkersEnabled()).append('\n');
        out.append("DeArrow: enabled=").append(deArrow.isDeArrowEnabled())
                .append(" titles=").append(deArrow.isReplaceTitlesEnabled())
                .append(" thumbnails=").append(deArrow.isReplaceThumbnailsEnabled())
                .append(" mobileLists=").append(mobileEnhancements.isDeArrowNativeListsEnabled())
                .append(" originalTitlesMobile=")
                .append(mobileEnhancements.isUnlocalizedTitlesNativeListsEnabled()).append('\n');
        SponsorBlockService sponsorService = SponsorBlockService.instance();
        out.append("SponsorBlockCache: entries=").append(sponsorService.getCacheEntryCount())
                .append(" inFlight=").append(sponsorService.getInFlightCount())
                .append(" hits=").append(sponsorService.getCacheHits())
                .append(" misses=").append(sponsorService.getCacheMisses())
                .append(" joins=").append(sponsorService.getSingleFlightJoins()).append('\n');
        out.append("DeArrowCache: entries=").append(DeArrowService.getCacheEntryCount())
                .append(" inFlight=").append(DeArrowService.getInFlightCount())
                .append(" hits=").append(DeArrowService.getCacheHits())
                .append(" misses=").append(DeArrowService.getCacheMisses())
                .append(" joins=").append(DeArrowService.getSingleFlightJoins()).append('\n');
        out.append("InstantPlay: enabled=").append(instantPlay.isEnabled())
                .append(" forbiddenFallback=").append(instantPlay.isForbiddenRecoveryEnabled())
                .append(" startupWatchdog=").append(instantPlay.isStartupWatchdogEnabled())
                .append('\n');
        out.append("SmartPlayerUX: master=").append(featureFlags.isSmartPlayerUxEnabled())
                .append(" brightnessGesture=").append(playerPreferences.isBrightnessGestureEnabled())
                .append(" volumeGesture=").append(playerPreferences.isVolumeGestureEnabled())
                .append(" doubleTap=").append(playerPreferences.getDoubleTapSeekSeconds()).append("s")
                .append(" lock=").append(playerPreferences.isPlayerLockEnabled())
                .append(" sleepTimer=").append(playerPreferences.isSleepTimerEnabled())
                .append(" rememberZoom=").append(playerPreferences.isRememberZoomEnabled())
                .append(" smartFit=").append(playerPreferences.isSmartFitEnabled())
                .append('\n');
        out.append("Media3Migration: master=").append(featureFlags.isMedia3EngineEnabled())
                .append(" radio=").append(featureFlags.isMedia3RadioEnabled())
                .append(" offline=").append(featureFlags.isMedia3OfflineEnabled())
                .append(" legacyFallback=").append(featureFlags.isMedia3LegacyFallbackEnabled())
                .append(" activeEngine=").append(playbackEngine)
                .append(" source=").append(media3SourceKind)
                .append(" last=").append(media3LastEvent).append('\n');
        out.append("metadata_cache_entries: ").append(metadataCacheEntries).append('\n');
        out.append("last_metadata_fetch: ").append(lastMetadataFetch)
                .append(" duration=").append(formatMetric(lastMetadataFetchMs)).append('\n');
        out.append("RadioDVR: setting=").append(radioPreferences.isTimeShiftEnabled())
                .append(" windowSetting=").append(radioPreferences.getTimeShiftMinutes()).append("min")
                .append(" active=").append(radioDvrActive)
                .append(" seekable=").append(radioDvrSeekable)
                .append(" liveWindow=").append(formatDuration(radioDvrWindowMs))
                .append(" bytes=").append(radioDvrBytes).append('\n');
        out.append("Radio2: remoteSearch=").append(radioPreferences.isServerSearchEnabled())
                .append(" recent=").append(radioPreferences.isRecentStationsEnabled())
                .append(" failover=").append(radioPreferences.isStreamFailoverEnabled())
                .append(" categories=").append(radioPreferences.isCategoriesEnabled())
                .append(" aaDirectory=").append(radioPreferences.isEnhancedAndroidAutoDirectoryEnabled())
                .append(" liveOffset=").append(radioPreferences.isLiveOffsetLabelEnabled()).append('\n');
        out.append("Radio2State: recent=").append(radioRecentCount)
                .append(" search=").append(radioRemoteSearch)
                .append(" searchResults=").append(radioRemoteSearchResults)
                .append(" failoverCandidates=").append(radioFailoverCandidates)
                .append(" failover=").append(radioFailoverState).append('\n');
        out.append("OfflineFoundation: effective=").append(offlineRepository.isEnabled())
                .append(" preference=").append(offlinePreferences.isFoundationEnabled())
                .append(" autoCleanup=").append(offlinePreferences.isAutoCleanupEnabled())
                .append(" records=").append(offlineStats.getTotalCount())
                .append(" available=").append(offlineStats.getAvailableCount())
                .append(" downloading=").append(offlineStats.getDownloadingCount())
                .append(" failed=").append(offlineStats.getFailedCount())
                .append(" expired=").append(offlineStats.getExpiredCount()).append('\n');
        out.append("OfflineStorage: tracked=").append(formatBytes(offlineStats.getTrackedBytes()))
                .append(" limit=").append(formatBytes(offlineStats.getStorageLimitBytes()))
                .append(" reserve=").append(formatBytes(offlineStats.getReservedFreeBytes()))
                .append(" deviceFree=").append(formatBytes(offlineStats.getAvailableDeviceBytes())).append('\n');
        out.append("OfflineListenSave: effective=").append(listenSave.isEnabled())
                .append(" preference=").append(offlinePreferences.isListenSaveEnabled())
                .append(" wifiOnly=").append(offlinePreferences.isListenSaveWifiOnly())
                .append(" completeAfterSwitch=").append(offlinePreferences.isListenSaveCompleteAfterSwitch())
                .append(" threshold=").append(offlinePreferences.getListenSaveThresholdSec()).append("s")
                .append(" recentLimit=").append(offlinePreferences.getListenSaveRecentLimit())
                .append(" pending=").append(listenSave.pendingCount())
                .append(" downloading=").append(listenSave.downloadingCount())
                .append(" available=").append(listenSave.availableCount())
                .append(" failed=").append(listenSave.failedCount()).append('\n');
        out.append("OfflinePlaylists: effective=").append(offlinePlaylists.isEnabled())
                .append(" preference=").append(offlinePreferences.isPlaylistDownloadsEnabled())
                .append(" wifiOnly=").append(offlinePreferences.isPlaylistWifiOnly())
                .append(" total=").append(offlinePlaylists.playlistCount())
                .append(" queued=").append(playlistQueued)
                .append(" downloading=").append(playlistDownloading)
                .append(" paused=").append(playlistPaused)
                .append(" available=").append(playlistAvailable)
                .append(" partial=").append(playlistPartial)
                .append(" failed=").append(playlistFailed).append('\n');
        out.append("OfflineAndroidAuto: flag=").append(featureFlags.isOfflineAndroidAutoEnabled())
                .append(" library=").append(aa.isOfflineLibraryEnabled())
                .append(" autoFallback=").append(aa.isOfflineAutoFallbackEnabled())
                .append(" readyAudio=").append(offlineStats.getAvailableCount())
                .append(" readyPlaylists=").append(playlistAvailable + playlistPartial).append('\n');
        int tripQueues = 0;
        for (OfflinePlaylistRecord playlist : offlinePlaylists.list()) {
            if (OfflineTripReserveRepository.isTripReservePlaylistId(playlist.getPlaylistId())) tripQueues++;
        }
        out.append("OfflineTripReserve: effective=").append(tripReserve.isEnabled())
                .append(" preference=").append(offlinePreferences.isTripReserveEnabled())
                .append(" wifiOnly=").append(offlinePreferences.isTripReserveWifiOnly())
                .append(" recentTarget=").append(offlinePreferences.getTripReserveRecentCount())
                .append(" favorites=").append(offlinePreferences.isTripReserveFavoritesEnabled())
                .append(" favoriteTarget=").append(offlinePreferences.getTripReserveFavoriteCount())
                .append(" playlistsTarget=").append(offlinePreferences.getTripReservePlaylistCount())
                .append(" tracksPerPlaylist=").append(offlinePreferences.getTripReservePlaylistTrackLimit())
                .append(" history=").append(tripReserve.historyCount())
                .append(" playlistHistory=").append(tripReserve.playlistHistoryCount())
                .append(" reserveQueues=").append(tripQueues)
                .append(" lastFavoriteSync=").append(offlinePreferences.getTripReserveLastSyncMs()).append('\n');
        out.append("AA_experimental_parked_video: ")
                .append(aa.isExperimentalParkedVideoEnabled()).append('\n');
        out.append("Paging: surface=").append(paginationSurface)
                .append(" items=").append(paginationBeforeItems).append("->")
                .append(paginationAfterItems).append(" hasMore=").append(paginationHasMore)
                .append(" lastError=").append(paginationLastError).append('\n');
        out.append("RadioCatalog: stations=").append(radioCatalogStations)
                .append(" nextOffset=").append(radioCatalogOffset)
                .append(" endReached=").append(radioCatalogEndReached).append('\n');
        out.append("original_title_global: ")
                .append(MainUIData.instance(app).isUnlocalizedTitlesEnabled()).append('\n');
    }

    private void appendFeatureFlags(StringBuilder out) {
        out.append("\n[Feature flags]\n");
        for (java.util.Map.Entry<String, Boolean> entry : featureFlags.currentStageFlags().entrySet()) {
            out.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
    }

    private void appendCounters(StringBuilder out) {
        out.append("\n[Persistent counters]\n");
        out.append("prepares: ").append(read(KEY_TOTAL_PREPARES)).append('\n');
        out.append("errors: ").append(read(KEY_TOTAL_ERRORS)).append('\n');
        out.append("transient_403: ").append(read(KEY_TOTAL_TRANSIENT_403)).append('\n');
        out.append("recovered_403: ").append(read(KEY_TOTAL_RECOVERED_403)).append('\n');
        out.append("engine_restarts: ").append(read(KEY_TOTAL_ENGINE_RESTARTS)).append('\n');
        out.append("playback_reloads_or_retries: ").append(read(KEY_TOTAL_PLAYBACK_RELOADS)).append('\n');
        out.append("radio_dvr_fallbacks: ").append(read(KEY_TOTAL_DVR_FALLBACKS)).append('\n');
        out.append("instant_play_403_fallbacks: ")
                .append(read(KEY_TOTAL_INSTANT_403_FALLBACKS)).append('\n');
        out.append("instant_play_watchdog_reloads: ")
                .append(read(KEY_TOTAL_INSTANT_WATCHDOG_RELOADS)).append('\n');
        out.append("instant_play_timeouts: ")
                .append(read(KEY_TOTAL_INSTANT_TIMEOUTS)).append('\n');
        out.append("paging_requests: ").append(read(KEY_TOTAL_PAGING_REQUESTS)).append('\n');
        out.append("paging_errors: ").append(read(KEY_TOTAL_PAGING_ERRORS)).append('\n');
        out.append("radio_catalog_pages: ").append(read(KEY_TOTAL_RADIO_PAGES)).append('\n');
        out.append("radio_remote_searches: ").append(read(KEY_TOTAL_RADIO_REMOTE_SEARCHES)).append('\n');
        out.append("radio_failover_attempts: ").append(read(KEY_TOTAL_RADIO_FAILOVER_ATTEMPTS)).append('\n');
        out.append("radio_failover_successes: ").append(read(KEY_TOTAL_RADIO_FAILOVER_SUCCESSES)).append('\n');
        out.append("media3_activations: ").append(read(KEY_TOTAL_MEDIA3_ACTIVATIONS)).append('\n');
        out.append("media3_errors: ").append(read(KEY_TOTAL_MEDIA3_ERRORS)).append('\n');
        out.append("media3_legacy_fallbacks: ").append(read(KEY_TOTAL_MEDIA3_LEGACY_FALLBACKS)).append('\n');
    }

    private boolean capture() {
        return featureFlags.isDiagnosticsCaptureEnabled();
    }

    private synchronized void increment(String key) {
        long next = preferences.getLong(key, 0L) + 1L;
        preferences.edit().putLong(key, next).apply();
    }

    private long read(String key) {
        return preferences.getLong(key, 0L);
    }

    private static String formatDetails(FormatItem item, boolean video) {
        if (item == null) return "none";
        StringBuilder out = new StringBuilder();
        CharSequence title = item.getTitle();
        if (title != null && title.length() > 0) out.append(title);
        MediaTrack track = item.getTrack();
        Format format = track == null ? null : track.format;
        if (format != null) {
            if (video && format.width > 0 && format.height > 0) {
                appendPart(out, format.width + "x" + format.height);
            }
            if (video && format.frameRate > 0f) {
                appendPart(out, String.format(Locale.US, "%.2ffps", format.frameRate));
            }
            if (format.codecs != null && !format.codecs.isEmpty()) appendPart(out, format.codecs);
            if (format.bitrate > 0) appendPart(out, (format.bitrate / 1000) + "kbps");
            if (format.sampleRate > 0 && !video) appendPart(out, format.sampleRate + "Hz");
            if (format.channelCount > 0 && !video) appendPart(out, format.channelCount + "ch");
        }
        if (item.getLanguage() != null && !item.getLanguage().isEmpty()) {
            appendPart(out, "lang=" + item.getLanguage());
        }
        return out.length() == 0 ? "selected" : out.toString();
    }

    private static void appendPart(StringBuilder out, String part) {
        if (part == null || part.isEmpty()) return;
        if (out.length() > 0) out.append(" • ");
        out.append(part);
    }

    private static String extractHost(String url) {
        if (url == null || url.trim().isEmpty()) return "unknown";
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host != null && !host.isEmpty()) return host;
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    private static String compactError(Throwable error) {
        if (error == null) return "unknown error";
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        if (message == null || message.trim().isEmpty()) message = current.getClass().getSimpleName();
        return trim(message, 220);
    }

    private static String maskMediaId(String value) {
        if (value == null || value.isEmpty()) return "none";
        if (value.startsWith("radio:")) return value;
        String raw = value.startsWith("video:") ? value.substring(6) : value;
        if (raw.length() <= 4) return "***";
        return "…" + raw.substring(raw.length() - 4);
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        String flat = value.replace('\n', ' ').replace('\r', ' ').trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String formatMetric(long value) {
        return value < 0L ? "n/a" : value + "ms";
    }

    private static String formatBytes(long bytes) {
        double value = Math.max(0L, bytes);
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024d && unit < units.length - 1) {
            value /= 1024d;
            unit++;
        }
        return unit == 0 ? String.format(Locale.US, "%.0f%s", value, units[unit])
                : String.format(Locale.US, "%.1f%s", value, units[unit]);
    }

    private static String formatDuration(long ms) {
        long total = Math.max(0L, ms) / 1000L;
        long hours = total / 3600L;
        long minutes = total / 60L % 60L;
        long seconds = total % 60L;
        return hours > 0L
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.US, "%d:%02d", minutes, seconds);
    }
}
