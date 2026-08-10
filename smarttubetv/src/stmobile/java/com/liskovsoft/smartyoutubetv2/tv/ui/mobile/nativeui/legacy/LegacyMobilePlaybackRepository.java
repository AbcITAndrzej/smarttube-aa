package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.view.View;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VideoLoaderController;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.background.MobileMediaSessionManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileBackgroundPlaybackRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobilePlaybackRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileDiagnosticsStore;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileError;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobilePlaybackSnapshot;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileTrack;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaRecord;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineListenSaveController;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineTripReserveController;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.playbackengine.Media3MigrationPolicy;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.playbackengine.Media3PlaybackEngine;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.playbackengine.MobilePlaybackEngine;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.MobileInstantPlayController;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.MobileInstantPlayPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioStation;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioDvrProxy;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioStationRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioTimeShiftController;
import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Native-mobile bridge to SmartTube's existing playback controllers. It implements PlaybackView
 * without importing Leanback, while the real video surface is an ExoPlayer PlayerView supplied by
 * LegacyMobilePlayerViewBinder.
 */
public final class LegacyMobilePlaybackRepository implements MobilePlaybackRepository,
        MobileBackgroundPlaybackRepository, PlaybackView,
        com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.PlayerView {
    private static final long SNAPSHOT_INTERVAL_MS = 500;
    private final Context applicationContext;
    private final LegacyMediaIndex index;
    private final LegacyErrorMapper errors;
    private final LegacyTrackMapper trackMapper = new LegacyTrackMapper();
    private final Handler main = new Handler(Looper.getMainLooper());
    /** Dedicated handler: Instant Play may cancel all of its delayed callbacks without touching ticker/UI work. */
    private final Handler instantPlayHandler = new Handler(Looper.getMainLooper());
    private final MobileMediaSessionManager mediaSessionManager;
    private final boolean headlessPlaybackAllowed;
    private final boolean applyLegacyPreferredAudio;
    private final RadioTimeShiftController radioTimeShift;
    private final RadioPreferences radioPreferences;
    private final MobileFeatureFlags featureFlags;
    private final MobileDiagnosticsStore diagnostics;
    private final MobileInstantPlayController instantPlay;
    private final OfflineListenSaveController listenSaveController;
    private final OfflineTripReserveController tripReserveController;
    private WeakReference<Context> hostContext = new WeakReference<>(null);
    private WeakReference<com.google.android.exoplayer2.ui.PlayerView> surface = new WeakReference<>(null);
    private Listener listener;
    private PlaybackPresenter presenter;
    private ExoPlayerController controller;
    private ExoPlayerInitializer initializer;
    private DefaultTrackSelector trackSelector;
    private SimpleExoPlayer player;
    /** Stage 11: transport-neutral facade over the existing ExoPlayer 2 controller. */
    private final MobilePlaybackEngine legacyEngine = new LegacyEngineBridge();
    /** Stage 11: first Media3 wave is intentionally audio-only (Radio + Offline). */
    private Media3PlaybackEngine media3Engine;
    private boolean media3DirectActive;
    private String media3DirectUri = "";
    private Media3MigrationPolicy.SourceKind media3SourceKind;
    private boolean media3FallbackUsed;
    private long media3Generation;
    private Video video;
    private boolean radioPlayback;
    private boolean offlinePlayback;
    private boolean playlistPlaybackContext;
    private String offlineMediaId;
    private String radioMediaId;
    private String radioTitle = "";
    private String radioSubtitle = "";
    /** Keeps a newly selected direct stream playing through ExoPlayer's asynchronous reset. */
    private boolean radioAutoplayPending;
    private String radioDirectStreamUrl = "";
    private boolean radioDirectFallbackUsed;
    private RadioStation radioStation;
    private final List<String> radioAttemptedStreams = new ArrayList<>();
    private boolean radioFailoverResolving;
    private Throwable radioPendingFailoverError;
    private int radioFailoverGeneration;
    private boolean radioFailoverAwaitingReady;
    /** Invalidates callbacks posted by an ExoPlayer instance after a source/engine switch. */
    private long engineGeneration;
    private boolean initialized;
    // When AA switches tracks, do not let PlaybackPresenter restore the previous YouTube item
    // while the headless engine is being recreated for the newly selected media id.
    private boolean suppressPresenterResume;
    private boolean engineBlocked;
    private boolean overlayShown;
    private boolean suggestionsShown;
    private boolean controlsShown = true;
    private boolean buffering;
    private boolean ended;
    private int resizeMode;
    private String qualityInfo = "";
    private List<SeekBarSegment> seekBarSegments = Collections.emptyList();
    private String pendingMediaId;
    private long pendingStartPositionMs;
    private final List<String> scopedPlaybackQueue = new ArrayList<>();
    private int scopedPlaybackIndex = -1;
    private String preferredAudioAppliedMediaId;
    private String preferredAudioAttemptSignature;

    private Player.EventListener stateListener;

    private Player.EventListener createStateListener(final long generation) {
        return new Player.EventListener() {
        @Override public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (generation != engineGeneration || player == null) {
                MobileDiagnostics.debug("P13-AA-Playback",
                        "ignore stale state generation=" + generation
                                + " active=" + engineGeneration + " state=" + playbackState);
                return;
            }
            buffering = playbackState == Player.STATE_BUFFERING;
            ended = playbackState == Player.STATE_ENDED;
            if (playbackState == Player.STATE_READY) {
                diagnostics.onPlayerReady(playWhenReady);
                instantPlay.onReady();
                if (radioPlayback && radioFailoverAwaitingReady) {
                    radioFailoverAwaitingReady = false;
                    diagnostics.onRadioFailoverSuccess(radioDirectStreamUrl);
                    MobileDiagnostics.info("P19-Radio2",
                            "alternate stream ready=" + radioDirectStreamUrl);
                }
            }
            if (radioPlayback && radioAutoplayPending && playbackState == Player.STATE_READY) {
                if (!playWhenReady) {
                    MobileDiagnostics.info("P14-Radio",
                            "restore autoplay after stream became ready");
                    setPlayWhenReady(true);
                } else {
                    radioAutoplayPending = false;
                    MobileDiagnostics.info("P14-Radio", "autoplay confirmed");
                }
            }
            if (ended && offlinePlayback && playScopedNextAtEnd()) return;
            if (ended && fallbackFromRadioDvr()) return;
            if (ended && headlessPlaybackAllowed) {
                MobileDiagnostics.info("P13-AA-Playback", "engine reached STATE_ENDED");
            }
            emitSnapshot();
        }

        @Override public void onPlayerError(ExoPlaybackException error) {
            if (generation != engineGeneration || player == null) {
                MobileDiagnostics.debug("P13-AA-Playback",
                        "ignore stale error generation=" + generation
                                + " active=" + engineGeneration);
                return;
            }
            if (headlessPlaybackAllowed) {
                Throwable cause = error == null ? null : error.getCause();
                MobileDiagnostics.warn("P13-AA-Playback",
                        "engine error; common recovery remains active: "
                                + (cause == null ? error : cause));
            }
            if (fallbackFromRadioDvr()) return;
            if (tryRadioStreamFailover(error)) return;
            if (isTransientForbiddenStream(error)) {
                // SmartTube's ErrorFixerController intentionally refreshes a forbidden
                // signed YouTube URL and retries it. The TV UI hides this transient 403,
                // but the mobile bridge used to surface the same recoverable error as a
                // toast before the retry succeeded a moment later.
                MobileDiagnostics.debug("DataPlayer",
                        "suppress transient 403; common recovery pipeline will retry");
                diagnostics.onTransient403(error);
                instantPlay.onTransient403();
                return;
            }
            diagnostics.onPlaybackError(error);
            Listener current = listener;
            if (current != null) current.onPlaybackError(errors.playback(error));
        }
        };
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            MobilePlaybackEngine engine = activeEngine();
            if (engine == null || !engine.isInitialized()) return;
            emitSnapshot();
            main.postDelayed(this, SNAPSHOT_INTERVAL_MS);
        }
    };

    public LegacyMobilePlaybackRepository(Context context, LegacyMediaIndex index, LegacyErrorMapper errors) {
        // The touch player only highlights its preferred language in the picker. It must never
        // auto-switch an audio track at startup.
        this(context, index, errors, false, false);
    }

    public LegacyMobilePlaybackRepository(Context context, LegacyMediaIndex index,
                                          LegacyErrorMapper errors,
                                          boolean headlessPlaybackAllowed) {
        // Preserve the legacy automatic language policy for the already-stable AA service.
        this(context, index, errors, headlessPlaybackAllowed, headlessPlaybackAllowed);
    }

    public LegacyMobilePlaybackRepository(Context context, LegacyMediaIndex index,
                                          LegacyErrorMapper errors,
                                          boolean headlessPlaybackAllowed,
                                          boolean applyLegacyPreferredAudio) {
        this.applicationContext = context.getApplicationContext();
        this.index = index;
        this.errors = errors;
        this.headlessPlaybackAllowed = headlessPlaybackAllowed;
        this.applyLegacyPreferredAudio = applyLegacyPreferredAudio;
        this.radioPreferences = new RadioPreferences(applicationContext);
        this.featureFlags = new MobileFeatureFlags(applicationContext);
        this.radioTimeShift = new RadioDvrProxy(radioPreferences);
        this.diagnostics = MobileDiagnosticsStore.get(applicationContext);
        this.instantPlay = new MobileInstantPlayController(
                instantPlayHandler,
                new MobileInstantPlayPreferences(applicationContext),
                new MobileFeatureFlags(applicationContext),
                diagnostics,
                new MobileInstantPlayController.Callback() {
                    @Override public boolean isReady() {
                        return player != null && player.getPlaybackState() == Player.STATE_READY;
                    }

                    @Override public void reloadAfterForbidden() {
                        VideoLoaderController loader = presenter == null ? null
                                : presenter.getController(VideoLoaderController.class);
                        if (loader != null) loader.reloadVideoAfterStreamRefresh();
                    }

                    @Override public void reloadForStartupWatchdog() {
                        VideoLoaderController loader = presenter == null ? null
                                : presenter.getController(VideoLoaderController.class);
                        if (loader != null) loader.reloadVideo();
                    }

                    @Override public void onStartupTimeout() {
                        Listener current = listener;
                        if (current != null) {
                            current.onPlaybackError(new MobileError(
                                    MobileError.Kind.TIMEOUT,
                                    applicationContext.getString(
                                            com.liskovsoft.smartyoutubetv2.tv.R.string.mobile_player_startup_timeout),
                                    null, true));
                        }
                    }
                });
        this.listenSaveController = new OfflineListenSaveController(applicationContext);
        this.tripReserveController = new OfflineTripReserveController(applicationContext);
        if (headlessPlaybackAllowed) {
            // Android Auto already owns the public MediaSession. Creating SmartTubeMobileSession here
            // gives one process two media sessions and two independent audio-focus clients that pause
            // each other. In automotive mode the active playback engine owns audio focus.
            this.mediaSessionManager = null;
            MobileDiagnostics.info("P13-AA-Playback",
                    "internal mobile session disabled; active engine owns audio focus");
        } else {
            this.mediaSessionManager = new MobileMediaSessionManager(applicationContext,
                    new MobileMediaSessionManager.PlaybackControl() {
                        @Override public void playFromSystem() { setPlayWhenReady(true); }
                        @Override public void pauseFromSystem() { setPlayWhenReady(false); }
                        @Override public void seekToFromSystem(long positionMs) {
                            seekTo(positionMs);
                        }
                        @Override public void setVolumeMultiplier(float multiplier) {
                            setVolume(Math.max(0f, Math.min(1f, multiplier)));
                        }
                    });
            // The active playback engine owns audio focus for YouTube and direct audio streams.
            // The companion MediaSession exposes metadata/controls only; a second focus request
            // would make the two clients in this process immediately pause each other.
            this.mediaSessionManager.setPlayerHandlesAudioFocus(true);
        }
    }

    void attachSurface(Context context, com.google.android.exoplayer2.ui.PlayerView playerView) {
        hostContext = new WeakReference<>(context);
        MobileDiagnostics.debug("DataPlayer", "attachSurface");
        surface = new WeakReference<>(playerView);
        playerView.setUseController(false);
        playerView.setResizeMode(resizeMode);
        // A phone can arrive here with a previous AA/video item still stored by the singleton
        // PlaybackPresenter. Mark a pending radio before creating ExoPlayer so it is configured
        // audio-only from the start and cannot restore that video during initialization.
        boolean pendingRadio = RadioStationRepository.isRadioMediaId(pendingMediaId);
        boolean pendingOffline = OfflineMediaRepository.isOfflinePlaybackId(pendingMediaId);
        if (pendingRadio) radioPlayback = true;
        boolean pendingMedia3Direct = (pendingRadio
                && shouldUseMedia3(Media3MigrationPolicy.SourceKind.RADIO))
                || (pendingOffline
                && shouldUseMedia3(Media3MigrationPolicy.SourceKind.OFFLINE));
        if (!media3DirectActive && !pendingMedia3Direct) ensureEngine();
        // Refresh the singleton presenter's context after Activity recreation.
        presenter = PlaybackPresenter.instance(context);
        presenter.setView(this);
        // Stage 11 keeps the existing ExoPlayer2 PlayerView for VOD. Media3 is initially
        // audio-only, so Radio/Offline intentionally leave the video surface detached.
        playerView.setPlayer(media3DirectActive ? null : player);
        if (pendingMediaId != null) {
            String mediaId = pendingMediaId;
            long startPositionMs = pendingStartPositionMs;
            pendingMediaId = null;
            pendingStartPositionMs = 0;
            if (RadioStationRepository.isRadioMediaId(mediaId)) {
                // Let the presenter finish its normal attach callbacks first, then replace any
                // stale restored item with the requested station on the next main-loop turn.
                main.post(() -> prepareNow(mediaId, startPositionMs));
            } else {
                prepareNow(mediaId, startPositionMs);
            }
        }
    }

    void detachSurface(com.google.android.exoplayer2.ui.PlayerView playerView) {
        if (playerView != null) playerView.setPlayer(null);
        if (surface.get() == playerView) surface.clear();
        hostContext.clear();
    }

    @Override public void setListener(Listener value) {
        listener = value;
        emitSnapshot();
    }

    @Override public void setPlaybackQueue(List<String> mediaIds, String currentMediaId) {
        scopedPlaybackQueue.clear();
        if (mediaIds != null) {
            for (String id : mediaIds) {
                if (id != null && !id.trim().isEmpty()
                        && !scopedPlaybackQueue.contains(id)) scopedPlaybackQueue.add(id);
            }
        }
        scopedPlaybackIndex = scopedPlaybackQueue.indexOf(currentMediaId);
        MobileDiagnostics.info("P16-Shorts", "scoped queue size="
                + scopedPlaybackQueue.size() + " current=" + scopedPlaybackIndex);
    }

    @Override public void prepare(String mediaId, long startPositionMs) {
        if (mediaId == null || mediaId.trim().isEmpty()) {
            notifyError(new IllegalArgumentException("Missing media id"));
            return;
        }
        boolean surfaceBound = surface.get() != null && hostContext.get() != null;
        // Normal phone playback still waits for Fragment.onViewCreated(). Android Auto explicitly
        // opts into headless playback and must not wait for an Activity or PlayerView that never exists.
        if (!surfaceBound && !headlessPlaybackAllowed) {
            pendingMediaId = mediaId;
            pendingStartPositionMs = Math.max(0, startPositionMs);
            MobileDiagnostics.debug("DataPlayer", "prepare deferred until surface bind: " + mediaId);
            return;
        }
        if (!surfaceBound) {
            pendingMediaId = null;
            pendingStartPositionMs = 0;
            MobileDiagnostics.info("P13-AA-Playback",
                    "prepare headless mediaId=" + mediaId + " start=" + startPositionMs);
        }
        prepareNow(mediaId, startPositionMs);
    }

    private void prepareNow(String mediaId, long startPositionMs) {
        try {
            String currentBefore = offlinePlayback ? offlineMediaId
                    : radioPlayback ? radioMediaId
                    : video == null ? "" : LegacyMediaMapper.stableId(video);
            if (currentBefore != null && !currentBefore.isEmpty() && !currentBefore.equals(mediaId)) {
                listenSaveController.onMediaSwitch();
                tripReserveController.onMediaSwitch();
            }
            boolean preparingRadio = RadioStationRepository.isRadioMediaId(mediaId);
            boolean preparingOffline = OfflineMediaRepository.isOfflinePlaybackId(mediaId);
            diagnostics.onPrepare(headlessPlaybackAllowed ? "ANDROID_AUTO" : "MOBILE", mediaId,
                    preparingRadio);
            if (!preparingOffline) instantPlay.begin(mediaId, preparingRadio, headlessPlaybackAllowed);
            else instantPlay.cancel();

            // Stage 11: when the next direct source is going to Media3, release the legacy
            // PlaybackPresenter engine before Radio/Offline metadata overwrites the previous VOD.
            // Otherwise presenter.onEngineReleased() could observe the new direct-source Video
            // object and attempt to restore/resume the wrong item during the migration boundary.
            boolean preparingMedia3Direct =
                    (preparingRadio && shouldUseMedia3(Media3MigrationPolicy.SourceKind.RADIO))
                            || (preparingOffline
                            && shouldUseMedia3(Media3MigrationPolicy.SourceKind.OFFLINE));
            if (preparingMedia3Direct && initialized) {
                MobileDiagnostics.info("P21-Media3",
                        "release legacy engine before direct metadata switch");
                releaseEngineOnly();
            }

            if (preparingOffline) {
                // Stage 9: the same private .audio file path is now valid in headless Android Auto.
                // The Automotive MediaSession remains owned by SmartTubeAutoMusicService; this
                // repository only feeds ExoPlayer audio and does not create a second public session.
                prepareOffline(mediaId, startPositionMs);
                return;
            }
            offlinePlayback = false;
            offlineMediaId = null;
            if (preparingRadio) {
                radioPlayback = true;
                prepareRadio(mediaId);
                return;
            }
            radioPlayback = false;
            radioTimeShift.stop();
            releaseMedia3Only("switch to VOD");
            diagnostics.onPlaybackEngine("legacy ExoPlayer 2", "vod");
            radioDirectStreamUrl = "";
            radioDirectFallbackUsed = false;
            preferredAudioAppliedMediaId = null;
            preferredAudioAttemptSignature = null;
            radioMediaId = null;
            radioAutoplayPending = false;
            radioTitle = "";
            radioSubtitle = "";
            if (headlessPlaybackAllowed && initialized && video != null
                    && !mediaId.equals(LegacyMediaMapper.stableId(video))) {
                MobileDiagnostics.info("P13-AA-Playback",
                        "recreate engine for media switch old="
                                + LegacyMediaMapper.stableId(video) + " new=" + mediaId);
                setPlayWhenReady(false);
                suppressPresenterResume = true;
                releaseEngineOnly();
            }
            ensureEngine();
            ended = false;
            MobileDiagnostics.info("DataPlayer", "prepare mediaId=" + mediaId + " start=" + startPositionMs);
            Video resolved = index.get(mediaId);
            if (resolved == null) {
                String raw = mediaId.startsWith("video:") ? mediaId.substring(6) : mediaId;
                resolved = Video.from(raw);
            }
            resolved.pendingPosMs = Math.max(0, startPositionMs);
            setVideo(resolved);
            presenter.onNewVideo(resolved); // Deliberately avoids openVideo(), which starts the TV activity.
            emitSnapshot();
        } catch (Throwable error) {
            notifyError(error);
        }
    }

    private void prepareOffline(String playbackId, long startPositionMs) {
        String rawId = OfflineMediaRepository.rawMediaId(playbackId);
        OfflineMediaRepository offline = OfflineMediaRepository.get(applicationContext);
        File file = offline.resolveAvailableFile(rawId);
        OfflineMediaRecord record = offline.find(rawId);
        if (file == null || record == null || !record.isAvailable()) {
            throw new IllegalStateException("Offline audio is no longer available");
        }

        radioPlayback = false;
        radioTimeShift.stop();
        radioMediaId = null;
        radioAutoplayPending = false;
        radioDirectStreamUrl = "";
        radioDirectFallbackUsed = false;
        radioStation = null;
        radioTitle = "";
        radioSubtitle = "";
        preferredAudioAppliedMediaId = null;
        preferredAudioAttemptSignature = null;
        offlinePlayback = true;
        offlineMediaId = playbackId;

        ended = false;
        Video metadata = Video.from(rawId);
        metadata.title = record.getTitle();
        metadata.secondTitle = record.getAuthor();
        video = metadata;
        String localUrl = Uri.fromFile(file).toString();
        diagnostics.onSource("offline-file", localUrl);
        MobileDiagnostics.info("OfflinePlaylist", "play local audio media=" + rawId);
        prepareDirectSource(Media3MigrationPolicy.SourceKind.OFFLINE, localUrl,
                Math.max(0L, startPositionMs), true);
        emitSnapshot();
    }

    private void prepareRadio(String mediaId) {
        String stationId = RadioStationRepository.stationIdFromMediaId(mediaId);
        RadioStationRepository radioRepository = RadioStationRepository.get(applicationContext);
        RadioStation station = radioRepository.getStation(stationId);
        if (station == null) throw new IllegalStateException("Nie znaleziono stacji w lokalnym cache");
        radioRepository.recordPlayed(stationId);
        radioStation = station;
        radioAttemptedStreams.clear();
        radioAttemptedStreams.add(station.getStreamUrl());
        radioFailoverResolving = false;
        radioPendingFailoverError = null;
        radioFailoverAwaitingReady = false;
        radioFailoverGeneration++;
        radioPlayback = true;
        radioMediaId = mediaId;
        radioAutoplayPending = true;
        radioDirectFallbackUsed = false;
        radioDirectStreamUrl = station.getStreamUrl();
        radioTitle = station.getName();
        String codec = station.getCodec().isEmpty() ? "Radio" : station.getCodec();
        radioSubtitle = station.getBitrate() > 0
                ? codec + " • " + station.getBitrate() + " kb/s" : codec;
        // SmartTube's higher-level metadata/session callbacks still expect a non-null Video object
        // even when the direct audio transport is Media3. Keep videoId null so Radio never enters
        // YouTube history/state paths.
        Video radioMetadata = new Video();
        radioMetadata.title = radioTitle;
        radioMetadata.secondTitle = radioSubtitle;
        radioMetadata.channelId = "";
        radioMetadata.isLive = true;
        video = radioMetadata;
        if (mediaSessionManager != null) {
            // The active direct engine (legacy or Media3) owns audio focus. Keep MediaSessionCompat
            // for controls/metadata without allowing it to compete for a second focus grant.
            mediaSessionManager.setPlayerHandlesAudioFocus(true);
        }
        ended = false;
        String playbackUrl = radioTimeShift.start(station);
        diagnostics.onSource("radio", station.getStreamUrl());
        MobileDiagnostics.info("P14-Radio", "prepare id=" + stationId
                + " timeshift=" + radioTimeShift.isActive() + " stream=" + station.getStreamUrl());
        prepareDirectSource(Media3MigrationPolicy.SourceKind.RADIO, playbackUrl, 0L, true);
        emitSnapshot();
    }

    @Override public void play() {
        MobilePlaybackEngine engine = activeEngine();
        if (radioPlayback && engine != null && engine.getState() != MobilePlaybackEngine.State.READY) {
            radioAutoplayPending = true;
        }
        if (mediaSessionManager != null) {
            mediaSessionManager.requestPlay();
        } else {
            MobileDiagnostics.info("P13-AA-Playback", "direct engine play=" + activeEngineName());
            setPlayWhenReady(true);
            emitSnapshot();
        }
    }

    @Override public void pause() {
        radioAutoplayPending = false;
        if (mediaSessionManager != null) {
            mediaSessionManager.pauseByUser();
        } else {
            MobileDiagnostics.info("P13-AA-Playback", "direct engine pause=" + activeEngineName());
            setPlayWhenReady(false);
            emitSnapshot();
        }
    }

    @Override public void playNext() {
        if (playScoped(1)) return;
        if (presenter != null) {
            MobileDiagnostics.info("P15-MobilePlayer", "next item requested");
            VideoLoaderController loader = presenter.getController(VideoLoaderController.class);
            if (loader != null) loader.loadNext();
            else presenter.onNextClicked();
        }
    }

    @Override public void playPrevious() {
        if (playScoped(-1)) return;
        if (presenter != null) {
            MobileDiagnostics.info("P15-MobilePlayer", "previous item requested");
            VideoLoaderController loader = presenter.getController(VideoLoaderController.class);
            if (loader != null) loader.loadPrevious();
            else presenter.onPreviousClicked();
        }
    }

    private boolean playScoped(int direction) {
        if (scopedPlaybackQueue.size() < 2) return false;
        String currentId = offlinePlayback ? offlineMediaId
                : video == null ? null : LegacyMediaMapper.stableId(video);
        int current = scopedPlaybackQueue.indexOf(currentId);
        if (current < 0) current = scopedPlaybackIndex;
        if (current < 0) return false;
        int next = (current + direction + scopedPlaybackQueue.size())
                % scopedPlaybackQueue.size();
        String nextId = scopedPlaybackQueue.get(next);
        scopedPlaybackIndex = next;
        MobileDiagnostics.info("P16-Shorts", "scoped switch " + current
                + " -> " + next + " id=" + nextId);
        prepareNow(nextId, 0L);
        play();
        return true;
    }

    private boolean playScopedNextAtEnd() {
        if (scopedPlaybackQueue.size() < 2) return false;
        int current = scopedPlaybackQueue.indexOf(offlineMediaId);
        if (current < 0) current = scopedPlaybackIndex;
        if (current < 0 || current + 1 >= scopedPlaybackQueue.size()) return false;
        scopedPlaybackIndex = current + 1;
        String nextId = scopedPlaybackQueue.get(scopedPlaybackIndex);
        MobileDiagnostics.info("OfflinePlaylist", "auto-next " + current + " -> "
                + scopedPlaybackIndex + " id=" + nextId);
        prepareNow(nextId, 0L);
        return true;
    }

    @Override public void seekTo(long positionMs) {
        long safe = Math.max(0L, positionMs);
        if (radioPlayback && radioTimeShift.isActive()) {
            if (!radioTimeShift.canSeek()) return;
            long duration = radioTimeShift.getWindowDurationMs();
            String url = safe >= Math.max(0L, duration - 750L)
                    ? radioTimeShift.goLive() : radioTimeShift.seekTo(safe);
            if (url == null || url.isEmpty()) return;
            radioAutoplayPending = true;
            replaceDirectSource(url, 0L, true);
            play();
            emitSnapshot();
            return;
        }
        setPositionMs(safe);
        emitSnapshot();
    }

    @Override public void seekBy(long deltaMs) {
        long current = radioPlayback && radioTimeShift.isActive()
                ? radioTimeShift.positionForPlayer(getPositionMs()) : getPositionMs();
        seekTo(Math.max(0L, current + deltaMs));
    }
    @Override public void setPlaybackSpeed(float speed) { setSpeed(speed); emitSnapshot(); }

    @Override public void selectVideoTrack(String trackId) {
        FormatItem item = trackMapper.find(getVideoFormats(), trackId);
        if (item != null) setFormat(item);
    }

    @Override public void setPlaybackContext(boolean playlistPlayback) {
        playlistPlaybackContext = playlistPlayback;
        MobileDiagnostics.debug("OfflineListen", "playback context androidAuto="
                + headlessPlaybackAllowed + " playlist=" + playlistPlayback);
    }

    @Override public void selectAudioTrack(String trackId) {
        FormatItem item = trackMapper.find(getAudioFormats(), trackId);
        if (item != null) {
            MobileDiagnostics.info("P15-MobilePlayer", "select audio id=" + trackId
                    + " language=" + item.getLanguage());
            setFormat(item);
        }
    }

    @Override public void selectSubtitleTrack(String trackId) {
        FormatItem item = trackMapper.find(getSubtitleFormats(), trackId);
        if (item != null) {
            MobileDiagnostics.info("P15-MobilePlayer", "select subtitles id=" + trackId
                    + " language=" + item.getLanguage());
            setFormat(item);
        }
    }

    @Override public void release() {
        MobileDiagnostics.debug("DataPlayer", "release");
        instantPlay.cancel();
        listenSaveController.reset();
        playlistPlaybackContext = false;
        tripReserveController.reset();
        radioTimeShift.close();
        engineGeneration++;
        if (mediaSessionManager != null) mediaSessionManager.release();
        main.removeCallbacks(ticker);
        releaseMedia3Only("repository release");
        if (presenter != null) {
            if (initialized) {
                presenter.onViewPaused();
                presenter.onEngineReleased();
                presenter.onViewDestroyed();
            }
            presenter.setView(null);
        }
        com.google.android.exoplayer2.ui.PlayerView view = surface.get();
        if (view != null) view.setPlayer(null);
        surface.clear();
        if (player != null) player.removeListener(stateListener);
        if (initializer != null) initializer.release();
        if (controller != null) controller.release();
        player = null;
        controller = null;
        initializer = null;
        trackSelector = null;
        media3DirectUri = "";
        media3SourceKind = null;
        media3FallbackUsed = false;
        presenter = null;
        video = null;
        offlinePlayback = false;
        offlineMediaId = null;
        radioPlayback = false;
        radioMediaId = null;
        radioAutoplayPending = false;
        radioDirectStreamUrl = "";
        radioDirectFallbackUsed = false;
        radioStation = null;
        radioAttemptedStreams.clear();
        radioFailoverResolving = false;
        radioPendingFailoverError = null;
        radioFailoverAwaitingReady = false;
        radioFailoverGeneration++;
        radioTitle = "";
        radioSubtitle = "";
        initialized = false;
        buffering = false;
        ended = false;
        listener = null;
        pendingMediaId = null;
        pendingStartPositionMs = 0;
        scopedPlaybackQueue.clear();
        scopedPlaybackIndex = -1;
        preferredAudioAppliedMediaId = null;
        preferredAudioAttemptSignature = null;
        hostContext.clear();
    }

    private void ensureEngine() {
        if (media3DirectActive) releaseMedia3Only("legacy engine requested");
        if (initialized && player != null) return;
        Context context = hostContext.get();
        boolean headless = context == null || surface.get() == null;
        if (headless && !headlessPlaybackAllowed) {
            throw new IllegalStateException("Headless playback was not enabled for this repository");
        }
        if (context == null) context = applicationContext;
        MobileDiagnostics.info(headless ? "P13-AA-Playback" : "DataPlayer",
                "initialize engine headless=" + headless);
        initializer = new ExoPlayerInitializer(context);
        trackSelector = new DefaultTrackSelector(new AdaptiveTrackSelection.Factory());
        if (headlessPlaybackAllowed || radioPlayback || offlinePlayback) {
            // Android Auto, Radio and Offline are audio-only in this source set. Do not allocate
            // video/subtitle decoders on the legacy rollback path.
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setRendererDisabled(TrackSelectorManager.RENDERER_INDEX_VIDEO, true)
                    .setRendererDisabled(TrackSelectorManager.RENDERER_INDEX_SUBTITLE, true));
            MobileDiagnostics.info(radioPlayback ? "P14-Radio"
                            : offlinePlayback ? "OfflinePlaylist" : "P13-AA-Playback",
                    "audio-only renderers selected");
        }
        player = initializer.createPlayer(context, new DefaultRenderersFactory(context), trackSelector);
        // Keep CPU and network awake while the mediaPlayback foreground service owns playback.
        // ExoPlayer 2.10.6 has no public wake-lock API; foreground service owns playback lifecycle.
        long generation = ++engineGeneration;
        stateListener = createStateListener(generation);
        player.addListener(stateListener);
        presenter = PlaybackPresenter.instance(context);
        presenter.setView(this);
        controller = new ExoPlayerController(context, presenter);
        controller.setTrackSelector(trackSelector);
        controller.setPlayer(player);
        controller.setPlayerView(this);
        presenter.onViewCreated();
        presenter.onViewInitialized();
        presenter.onEngineInitialized();
        // onViewResumed can restore the last YouTube item from PlaybackPresenter. Radio screens
        // deliberately start from their selected direct stream and must not restore that item.
        if (!radioPlayback && !suppressPresenterResume) presenter.onViewResumed();
        suppressPresenterResume = false;
        com.google.android.exoplayer2.ui.PlayerView view = surface.get();
        if (view != null) view.setPlayer(player);
        initialized = true;
        main.removeCallbacks(ticker);
        main.post(ticker);
    }

    private boolean shouldUseMedia3(Media3MigrationPolicy.SourceKind sourceKind) {
        return Media3MigrationPolicy.shouldUseMedia3(sourceKind,
                featureFlags.isMedia3EngineEnabled(),
                featureFlags.isMedia3RadioEnabled(),
                featureFlags.isMedia3OfflineEnabled());
    }

    private MobilePlaybackEngine activeEngine() {
        if (media3DirectActive && media3Engine != null && media3Engine.isInitialized()) {
            return media3Engine;
        }
        return legacyEngine.isInitialized() ? legacyEngine : null;
    }

    private boolean hasActiveEngine() {
        MobilePlaybackEngine engine = activeEngine();
        return engine != null && engine.isInitialized();
    }

    private String activeEngineName() {
        MobilePlaybackEngine engine = activeEngine();
        return engine == null ? "none" : engine.getEngineName();
    }

    private static String sourceKindLabel(Media3MigrationPolicy.SourceKind sourceKind) {
        return sourceKind == null ? "unknown" : sourceKind.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Stage 11 migration boundary. VOD remains on SmartTube's existing controller, while direct
     * Radio/Offline audio may opt into Media3. Every Media3 source can fail open to legacy.
     */
    private void prepareDirectSource(Media3MigrationPolicy.SourceKind sourceKind,
                                     String uri, long startPositionMs, boolean autoplay) {
        if (uri == null || uri.trim().isEmpty()) {
            throw new IllegalArgumentException("Direct source URI is empty");
        }
        media3SourceKind = sourceKind;
        media3DirectUri = uri;
        media3FallbackUsed = false;
        ended = false;
        buffering = true;

        if (shouldUseMedia3(sourceKind)) {
            com.google.android.exoplayer2.ui.PlayerView view = surface.get();
            if (view != null) view.setPlayer(null);
            releaseEngineOnly();
            ensureMedia3Engine();
            media3DirectActive = true;
            diagnostics.onPlaybackEngine(media3Engine.getEngineName(), sourceKindLabel(sourceKind));
            MobileDiagnostics.info("P21-Media3", "open " + sourceKindLabel(sourceKind)
                    + " using Media3 uri=" + safeHost(uri));
            media3Engine.open(uri, Math.max(0L, startPositionMs), autoplay);
        } else {
            releaseMedia3Only("feature disabled");
            if (sourceKind == Media3MigrationPolicy.SourceKind.OFFLINE) {
                // Offline is not owned by PlaybackPresenter; never restore the previous YouTube VOD
                // while constructing the fallback legacy engine.
                suppressPresenterResume = true;
            }
            ensureEngine();
            if (controller != null && video != null) controller.setVideo(video);
            diagnostics.onPlaybackEngine("legacy ExoPlayer 2", sourceKindLabel(sourceKind));
            legacyEngine.open(uri, Math.max(0L, startPositionMs), autoplay);
        }
        main.removeCallbacks(ticker);
        main.post(ticker);
    }

    /** Replaces the current Radio/Offline direct URI without changing the chosen engine. */
    private void replaceDirectSource(String uri, long startPositionMs, boolean autoplay) {
        if (uri == null || uri.trim().isEmpty()) return;
        media3DirectUri = uri;
        MobilePlaybackEngine engine = activeEngine();
        if (engine == null) {
            Media3MigrationPolicy.SourceKind kind = media3SourceKind != null ? media3SourceKind
                    : radioPlayback ? Media3MigrationPolicy.SourceKind.RADIO
                    : Media3MigrationPolicy.SourceKind.OFFLINE;
            prepareDirectSource(kind, uri, startPositionMs, autoplay);
            return;
        }
        buffering = true;
        ended = false;
        engine.open(uri, Math.max(0L, startPositionMs), autoplay);
    }

    private void ensureMedia3Engine() {
        if (media3Engine != null && media3Engine.isInitialized()) return;
        final long generation = ++media3Generation;
        media3Engine = new Media3PlaybackEngine(applicationContext,
                new MobilePlaybackEngine.Listener() {
                    @Override public void onEngineStateChanged(MobilePlaybackEngine.State state,
                                                                boolean playWhenReady) {
                        if (generation != media3Generation || !media3DirectActive) return;
                        onMedia3StateChanged(state, playWhenReady);
                    }

                    @Override public void onEngineError(Throwable error) {
                        if (generation != media3Generation || !media3DirectActive) return;
                        onMedia3Error(error);
                    }
                });
        // ExoPlayer is created lazily by open(); media3DirectActive is set before the first open.
    }

    private void onMedia3StateChanged(MobilePlaybackEngine.State state, boolean playWhenReady) {
        buffering = state == MobilePlaybackEngine.State.BUFFERING;
        ended = state == MobilePlaybackEngine.State.ENDED;
        if (state == MobilePlaybackEngine.State.READY) {
            diagnostics.onPlayerReady(playWhenReady);
            diagnostics.onMedia3Ready(sourceKindLabel(media3SourceKind));
            if (radioPlayback && radioFailoverAwaitingReady) {
                radioFailoverAwaitingReady = false;
                diagnostics.onRadioFailoverSuccess(radioDirectStreamUrl);
                MobileDiagnostics.info("P19-Radio2",
                        "alternate stream ready via Media3=" + radioDirectStreamUrl);
            }
            if (radioPlayback && radioAutoplayPending) {
                if (!playWhenReady) {
                    setPlayWhenReady(true);
                } else {
                    radioAutoplayPending = false;
                }
            }
        }
        if (ended && offlinePlayback && playScopedNextAtEnd()) return;
        if (ended && fallbackFromRadioDvr()) return;
        if (ended && headlessPlaybackAllowed) {
            MobileDiagnostics.info("P21-Media3", "direct engine reached ENDED");
        }
        emitSnapshot();
    }

    private void onMedia3Error(Throwable error) {
        diagnostics.onMedia3Error(sourceKindLabel(media3SourceKind), error);
        MobileDiagnostics.warn("P21-Media3", "direct engine error source="
                + sourceKindLabel(media3SourceKind) + " error=" + error);

        // This is the key rollback guarantee of Stage 11. Before changing higher-level Radio
        // failover policy, retry exactly the same URI with the mature bundled ExoPlayer2 engine.
        if (fallbackMedia3ToLegacy(error)) return;
        if (fallbackFromRadioDvr()) return;
        if (tryRadioStreamFailover(error)) return;
        diagnostics.onPlaybackError(error);
        Listener current = listener;
        if (current != null) current.onPlaybackError(errors.playback(error));
    }

    private boolean fallbackMedia3ToLegacy(Throwable originalError) {
        if (!media3DirectActive || media3Engine == null || media3FallbackUsed
                || !featureFlags.isMedia3LegacyFallbackEnabled()) {
            return false;
        }
        String uri = media3DirectUri;
        Media3MigrationPolicy.SourceKind sourceKind = media3SourceKind;
        long position = media3Engine.getPositionMs();
        boolean autoplay = media3Engine.getPlayWhenReady() || radioPlayback;
        media3FallbackUsed = true;
        releaseMedia3Only("fallback to legacy");
        try {
            if (sourceKind == Media3MigrationPolicy.SourceKind.OFFLINE) {
                suppressPresenterResume = true;
            }
            ensureEngine();
            if (controller == null) return false;
            if (video != null) controller.setVideo(video);
            media3SourceKind = sourceKind;
            media3DirectUri = uri;
            diagnostics.onMedia3LegacyFallback(sourceKindLabel(sourceKind), originalError);
            diagnostics.onPlaybackEngine("legacy ExoPlayer 2 (Media3 fallback)",
                    sourceKindLabel(sourceKind));
            MobileDiagnostics.warn("P21-Media3", "fallback to legacy source="
                    + sourceKindLabel(sourceKind) + " uri=" + safeHost(uri));
            legacyEngine.open(uri,
                    sourceKind == Media3MigrationPolicy.SourceKind.OFFLINE ? position : 0L,
                    autoplay);
            main.removeCallbacks(ticker);
            main.post(ticker);
            return true;
        } catch (Throwable legacyError) {
            MobileDiagnostics.error("P21-Media3", "legacy fallback failed", legacyError);
            return false;
        }
    }

    private void releaseMedia3Only(String reason) {
        media3Generation++;
        Media3PlaybackEngine engine = media3Engine;
        media3Engine = null;
        boolean wasActive = media3DirectActive;
        media3DirectActive = false;
        if (engine != null) {
            try {
                engine.release();
            } catch (Throwable error) {
                MobileDiagnostics.warn("P21-Media3", "release failed: " + error);
            }
        }
        if (wasActive) MobileDiagnostics.info("P21-Media3", "released: " + reason);
    }

    private void releaseActiveEngineOnly(String reason) {
        if (media3DirectActive || media3Engine != null) {
            releaseMedia3Only(reason);
        } else {
            releaseEngineOnly();
        }
    }

    private static String safeHost(String uri) {
        if (uri == null || uri.isEmpty()) return "unknown";
        try {
            Uri parsed = Uri.parse(uri);
            String host = parsed.getHost();
            if (host != null && !host.isEmpty()) return host;
            return parsed.getScheme() == null ? "local" : parsed.getScheme();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private boolean fallbackFromRadioDvr() {
        if (!radioPlayback || !radioTimeShift.hasFailed() || radioDirectFallbackUsed
                || radioDirectStreamUrl == null || radioDirectStreamUrl.isEmpty()
                || !hasActiveEngine()) {
            return false;
        }
        // Time-shift is deliberately fail-open: station playback is more important than DVR.
        radioDirectFallbackUsed = true;
        String fallback = radioDirectStreamUrl;
        radioTimeShift.stop();
        radioAutoplayPending = true;
        ended = false;
        buffering = true;
        MobileDiagnostics.warn("P17-RadioDVR", "fallback to direct radio stream");
        diagnostics.onRadioDvrFallback();
        replaceDirectSource(fallback, 0L, true);
        play();
        return true;
    }

    private boolean tryRadioStreamFailover(Throwable error) {
        if (!radioPlayback || !hasActiveEngine() || radioStation == null
                || !featureFlags.isRadio2Enabled()
                || !featureFlags.isRadio2StreamFailoverEnabled()
                || !radioPreferences.isStreamFailoverEnabled()) {
            return false;
        }
        if (radioFailoverResolving) {
            // The original player error is intentionally held while Radio Browser resolves
            // candidates. Do not surface duplicate errors during the same lookup.
            return true;
        }
        radioFailoverResolving = true;
        radioPendingFailoverError = error;
        final int generation = radioFailoverGeneration;
        final String mediaId = radioMediaId;
        MobileDiagnostics.warn("P19-Radio2", "stream failed; resolving alternate candidates");
        RadioStationRepository.get(applicationContext).resolveAlternativeStreams(
                radioStation, urls -> {
                    if (generation != radioFailoverGeneration || !radioPlayback
                            || mediaId == null || !mediaId.equals(radioMediaId)) {
                        return;
                    }
                    radioFailoverResolving = false;
                    String alternate = firstUntriedRadioUrl(urls);
                    if (alternate == null) {
                        Throwable pending = radioPendingFailoverError;
                        radioPendingFailoverError = null;
                        diagnostics.onRadioFailoverFailed();
                        MobileDiagnostics.warn("P19-Radio2", "no alternate radio stream remains");
                        if (pending != null) {
                            diagnostics.onPlaybackError(pending);
                            Listener current = listener;
                            if (current != null) current.onPlaybackError(errors.playback(pending));
                        }
                        return;
                    }
                    radioAttemptedStreams.add(alternate);
                    radioPendingFailoverError = null;
                    radioDirectStreamUrl = alternate;
                    radioDirectFallbackUsed = false;
                    radioAutoplayPending = true;
                    radioFailoverAwaitingReady = true;
                    ended = false;
                    buffering = true;
                    diagnostics.onRadioFailoverAttempt(alternate);
                    MobileDiagnostics.info("P19-Radio2",
                            "trying alternate stream " + radioAttemptedStreams.size()
                                    + " url=" + alternate);
                    radioTimeShift.stop();
                    RadioStation alternateStation = radioStation.withStreamUrl(alternate);
                    String playbackUrl = radioTimeShift.start(alternateStation);
                    replaceDirectSource(playbackUrl, 0L, true);
                    play();
                    emitSnapshot();
                });
        return true;
    }

    private String firstUntriedRadioUrl(List<String> values) {
        if (values == null) return null;
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            boolean tried = false;
            for (String previous : radioAttemptedStreams) {
                if (sameRadioUrl(previous, value)) {
                    tried = true;
                    break;
                }
            }
            if (!tried) return value;
        }
        return null;
    }

    private static boolean sameRadioUrl(String left, String right) {
        if (left == null || right == null) return false;
        String a = left.trim();
        String b = right.trim();
        while (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return a.equalsIgnoreCase(b);
    }

    private void notifyError(Throwable error) {
        MobileDiagnostics.error("DataPlayer", "player bridge failure", error);
        diagnostics.onPlaybackError(error);
        Listener current = listener;
        if (current != null) current.onPlaybackError(errors.playback(error));
    }

    private static boolean isTransientForbiddenStream(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.trim().startsWith("Response code: 403")) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) break;
            current = cause;
        }
        return false;
    }

    private void emitSnapshot() {
        MobilePlaybackEngine engine = activeEngine();
        long position = Math.max(0, getPositionMs());
        long duration = Math.max(0, getDurationMs());
        long buffered = engine == null ? 0 : Math.max(0, engine.getBufferedPositionMs());
        if (radioPlayback && radioTimeShift.isActive()) {
            position = radioTimeShift.positionForPlayer(position);
            duration = radioTimeShift.getWindowDurationMs();
            buffered = duration;
        }
        List<MobileTrack> videoTracks = controller == null ? Collections.emptyList()
                : trackMapper.map(controller.getVideoFormats(), MobileTrack.Type.VIDEO);
        List<FormatItem> audioFormats = controller == null ? Collections.emptyList()
                : controller.getAudioFormats();
        if (applyLegacyPreferredAudio) applyPreferredAudio(audioFormats);
        List<MobileTrack> audio = trackMapper.map(audioFormats, MobileTrack.Type.AUDIO);
        List<MobileTrack> subtitles = controller == null ? Collections.emptyList()
                : trackMapper.map(controller.getSubtitleFormats(), MobileTrack.Type.SUBTITLE);
        String id = radioPlayback ? radioMediaId
                : offlinePlayback ? offlineMediaId
                : video == null ? null : LegacyMediaMapper.stableId(video);
        MobilePlaybackSnapshot snapshot = new MobilePlaybackSnapshot(id,
                radioPlayback ? radioTitle
                        : video == null ? "" : LegacyMediaMapper.safe(video.getTitleFull()),
                radioPlayback ? radioSubtitle
                        : video == null ? "" : LegacyMediaMapper.safe(video.getSecondTitleFull()),
                containsMedia(), isPlaying(), buffering || (!radioPlayback && isLoading()),
                isPlaybackEnded(),
                position, duration, buffered, getSpeed(), videoTracks, audio, subtitles,
                seekBarSegments);
        diagnostics.onSnapshot(snapshot, getVideoFormat(), getAudioFormat(), getSubtitleFormat(),
                radioPlayback, radioTimeShift);
        listenSaveController.onPlayback(video, snapshot.isPlaying(), radioPlayback, offlinePlayback,
                headlessPlaybackAllowed, playlistPlaybackContext);
        tripReserveController.onPlayback(video, snapshot.isPlaying(), radioPlayback, offlinePlayback);
        if (mediaSessionManager != null) mediaSessionManager.updatePlayback(snapshot);
        Listener current = listener;
        if (current != null) current.onPlaybackSnapshot(snapshot);
    }

    private void applyPreferredAudio(List<FormatItem> formats) {
        if (radioPlayback || video == null || formats == null || formats.isEmpty()) return;
        String mediaId = LegacyMediaMapper.stableId(video);
        if (mediaId == null || mediaId.equals(preferredAudioAppliedMediaId)) return;
        String preferred = normalizeLanguage(
                PlayerData.instance(applicationContext).getAudioLanguage());
        if (preferred.isEmpty()) {
            preferredAudioAppliedMediaId = mediaId;
            return;
        }
        String attemptSignature = audioAttemptSignature(mediaId, formats);
        if (attemptSignature.equals(preferredAudioAttemptSignature)) return;
        preferredAudioAttemptSignature = attemptSignature;
        FormatItem selected = null;
        FormatItem candidate = null;
        for (FormatItem format : formats) {
            if (format == null) continue;
            if (format.isSelected()) selected = format;
            if (candidate == null && languageMatches(format, preferred)) candidate = format;
        }
        if (selected != null && languageMatches(selected, preferred)) {
            preferredAudioAppliedMediaId = mediaId;
            MobileDiagnostics.info("P16-Audio", "preferred already selected media="
                    + mediaId + " language=" + selected.getLanguage());
            return;
        }
        if (candidate != null) {
            preferredAudioAppliedMediaId = mediaId;
            MobileDiagnostics.info("P16-Audio", "enforce preferred media=" + mediaId
                    + " preference=" + preferred + " selected="
                    + (selected == null ? "none" : selected.getLanguage())
                    + " target=" + candidate.getLanguage());
            controller.selectFormat(candidate);
        } else {
            MobileDiagnostics.info("P16-Audio", "preferred unavailable media=" + mediaId
                    + " preference=" + preferred + " tracks=" + formats.size());
        }
    }

    private static String audioAttemptSignature(String mediaId, List<FormatItem> formats) {
        StringBuilder result = new StringBuilder(mediaId == null ? "" : mediaId);
        for (FormatItem format : formats) {
            if (format == null) continue;
            result.append('|').append(normalizeLanguage(format.getLanguage()))
                    .append(':').append(normalizeLanguage(format.getTitle() == null
                            ? "" : format.getTitle().toString()))
                    .append(':').append(format.isSelected());
        }
        return result.toString();
    }

    private static boolean languageMatches(FormatItem format, String preferred) {
        String language = normalizeLanguage(format.getLanguage());
        CharSequence rawTitle = format.getTitle();
        String title = normalizeLanguage(rawTitle == null ? "" : rawTitle.toString());
        return language.equals(preferred) || language.startsWith(preferred + "-")
                || language.startsWith(preferred + "_")
                || language.startsWith(preferred + ".")
                || "pl".equals(preferred) && (title.contains("polski")
                || title.contains("polish"));
    }

    private static String normalizeLanguage(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    @Override public void setHostVisible(boolean visible) {
        if (mediaSessionManager != null) mediaSessionManager.setHostVisible(visible);
    }

    @Override public boolean isBackgroundPlaybackEnabled() {
        return headlessPlaybackAllowed
                || mediaSessionManager != null && mediaSessionManager.isBackgroundPlaybackEnabled();
    }

    // PlaybackView / PlayerEngine delegation.
    @Override public void openSabr(MediaItemFormatInfo info) { diagnostics.onSourceKind("sabr"); if (controller != null) controller.openSabr(info); }
    @Override public void openDash(MediaItemFormatInfo info) { diagnostics.onSource("dash", info == null ? null : info.getDashManifestUrl()); if (controller != null) controller.openDash(info); }
    @Override public void openDash(InputStream manifest) { diagnostics.onSourceKind("dash-manifest"); if (controller != null) controller.openDash(manifest); }
    @Override public void openDashUrl(String url) { diagnostics.onSource("dash-url", url); if (controller != null) controller.openDashUrl(url); }
    @Override public void openHlsUrl(String url) { diagnostics.onSource("hls", url); if (controller != null) controller.openHlsUrl(url); }
    @Override public void openUrlList(List<String> urls) { diagnostics.onSource("url-list", urls == null || urls.isEmpty() ? null : urls.get(0)); if (controller != null) controller.openUrlList(urls); }
    @Override public void openMerged(MediaItemFormatInfo info, String hls) { diagnostics.onSource("merged", hls); if (controller != null) controller.openMerged(info, hls); }
    @Override public void openMerged(InputStream dash, String hls) { diagnostics.onSource("merged", hls); if (controller != null) controller.openMerged(dash, hls); }
    @Override public long getPositionMs() {
        MobilePlaybackEngine engine = activeEngine();
        return engine == null ? 0L : engine.getPositionMs();
    }
    @Override public void setPositionMs(long positionMs) {
        MobilePlaybackEngine engine = activeEngine();
        if (engine != null) engine.seekTo(positionMs);
    }
    @Override public long getDurationMs() {
        MobilePlaybackEngine engine = activeEngine();
        return engine == null ? 0L : engine.getDurationMs();
    }
    @Override public void setPlayWhenReady(boolean play) {
        MobilePlaybackEngine engine = activeEngine();
        if (engine != null) engine.setPlayWhenReady(play);
    }
    @Override public boolean getPlayWhenReady() {
        MobilePlaybackEngine engine = activeEngine();
        return engine != null && engine.getPlayWhenReady();
    }
    @Override public boolean isPlaying() {
        MobilePlaybackEngine engine = activeEngine();
        return engine != null && engine.isPlaying();
    }
    private boolean isPlaybackEnded() {
        MobilePlaybackEngine engine = activeEngine();
        return ended || engine != null && engine.getState() == MobilePlaybackEngine.State.ENDED;
    }
    @Override public boolean isLoading() {
        MobilePlaybackEngine engine = activeEngine();
        return engine != null && engine.isLoading();
    }
    @Override public List<FormatItem> getVideoFormats() { return controller == null ? Collections.emptyList() : controller.getVideoFormats(); }
    @Override public List<FormatItem> getAudioFormats() { return controller == null ? Collections.emptyList() : controller.getAudioFormats(); }
    @Override public List<FormatItem> getSubtitleFormats() { return controller == null ? Collections.emptyList() : controller.getSubtitleFormats(); }
    @Override public void setFormat(FormatItem option) { if (controller != null) controller.selectFormat(option); emitSnapshot(); }
    @Override public FormatItem getVideoFormat() { return controller == null ? null : controller.getVideoFormat(); }
    @Override public FormatItem getAudioFormat() { return controller == null ? null : controller.getAudioFormat(); }
    @Override public FormatItem getSubtitleFormat() { return controller == null ? null : controller.getSubtitleFormat(); }
    @Override public boolean isEngineInitialized() { return hasActiveEngine(); }
    @Override public void restartEngine() {
        diagnostics.onEngineRestart();
        Video current = video;
        String currentRadio = radioMediaId;
        String currentOffline = offlineMediaId;
        long position = getPositionMs();
        releaseActiveEngineOnly("restart");
        if (currentRadio != null) prepare(currentRadio, 0L);
        else if (currentOffline != null) prepare(currentOffline, position);
        else if (current != null) prepare(LegacyMediaMapper.stableId(current), position);
    }
    @Override public void reloadPlayback() {
        diagnostics.onPlaybackReload();
        if (radioMediaId != null) {
            String current = radioMediaId;
            releaseActiveEngineOnly("radio reload");
            prepare(current, 0L);
        } else if (offlineMediaId != null) {
            String current = offlineMediaId;
            long position = getPositionMs();
            releaseActiveEngineOnly("offline reload");
            prepare(current, position);
        } else if (presenter != null) {
            presenter.onEngineReleased();
            presenter.onEngineInitialized();
        }
    }
    @Override public void blockEngine(boolean block) { engineBlocked = block; }
    @Override public boolean isEngineBlocked() { return engineBlocked; }
    @Override public boolean isInPIPMode() { Context c = hostContext.get(); return Build.VERSION.SDK_INT >= 24 && c instanceof Activity && ((Activity)c).isInPictureInPictureMode(); }
    @Override public boolean containsMedia() {
        MobilePlaybackEngine engine = activeEngine();
        return engine != null && engine.containsMedia();
    }
    @Override public void setSpeed(float speed) {
        MobilePlaybackEngine engine = activeEngine();
        if (engine != null) engine.setSpeed(speed);
    }
    @Override public float getSpeed() {
        MobilePlaybackEngine engine = activeEngine();
        float value = engine == null ? 1f : engine.getSpeed();
        return value <= 0f ? 1f : value;
    }
    @Override public void setPitch(float pitch) {
        MobilePlaybackEngine engine = activeEngine();
        if (engine != null) engine.setPitch(pitch);
    }
    @Override public float getPitch() {
        MobilePlaybackEngine engine = activeEngine();
        return engine == null ? 1f : engine.getPitch();
    }
    @Override public void setVolume(float volume) {
        MobilePlaybackEngine engine = activeEngine();
        if (engine != null) engine.setVolume(volume);
    }
    @Override public float getVolume() {
        MobilePlaybackEngine engine = activeEngine();
        return engine == null ? 1f : engine.getVolume();
    }
    @Override public void setResizeMode(int mode) { resizeMode = mode; com.google.android.exoplayer2.ui.PlayerView v = surface.get(); if (v != null) v.setResizeMode(mode); }
    @Override public int getResizeMode() { return resizeMode; }
    @Override public void setZoomPercents(int percents) { }
    @Override public void setAspectRatio(float ratio) { }
    @Override public void setRotationAngle(int angle) { View v = surface.get(); if (v != null) v.setRotation(angle); }
    @Override public void setVideoFlipEnabled(boolean enabled) { View v = surface.get(); if (v != null) v.setScaleX(enabled ? -1f : 1f); }
    @Override public void setVideoGravity(int gravity) { View v = surface.get(); if (v != null && v.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) ((android.widget.FrameLayout.LayoutParams)v.getLayoutParams()).gravity = gravity; }

    // PlayerManager and PlayerUI state. Mobile controls own presentation, so TV-only operations are no-ops.
    @Override public void setVideo(Video item) { video = item; if (controller != null) controller.setVideo(item); emitSnapshot(); }
    @Override public Video getVideo() { return video; }
    @Override public void finish() { release(); }
    @Override public void finishReally() { release(); }
    @Override public void showBackground(String url) { }
    @Override public void showBackgroundColor(int colorResId) { }
    @Override public void resetPlayerState() { if (controller != null) controller.resetPlayerState(); }
    @Override public boolean isEmbed() {
        // Mobile needs the complete metadata and recovery pipeline. Embed mode suppresses
        // suggestions (so Next waits forever) and aborts early handling of errors such as 403.
        return false;
    }
    @Override public void updateSuggestions(VideoGroup group) { }
    @Override public void removeSuggestions(VideoGroup group) { }
    @Override public int getSuggestionsIndex(VideoGroup group) { return -1; }
    @Override public VideoGroup getSuggestionsByIndex(int index) { return null; }
    @Override public void focusSuggestedItem(int index) { }
    @Override public void focusSuggestedItem(Video video) { }
    @Override public void resetSuggestedPosition() { }
    @Override public boolean isSuggestionsEmpty() { return true; }
    @Override public void clearSuggestions() { }
    @Override public void showOverlay(boolean show) { overlayShown = show; }
    @Override public boolean isOverlayShown() { return overlayShown; }
    @Override public void showSuggestions(boolean show) { suggestionsShown = show; }
    @Override public boolean isSuggestionsShown() { return suggestionsShown; }
    @Override public void showControls(boolean show) { controlsShown = show; }
    @Override public boolean isControlsShown() { return controlsShown; }
    @Override public int getButtonState(int buttonId) { return BUTTON_OFF; }
    @Override public void setButtonState(int buttonId, int buttonState) { }
    @Override public void setChannelIcon(String iconUrl) { }
    @Override public void setSeekPreviewTitle(String title) { }
    @Override public void setNextTitle(Video nextVideo) { }
    @Override public void showDebugInfo(boolean show) { }
    @Override public void showSubtitles(boolean show) { }
    @Override public void loadStoryboard() { }
    @Override public void setTitle(String title) { }
    @Override public void showProgressBar(boolean show) { buffering = show; emitSnapshot(); }
    @Override public void setSeekBarSegments(List<SeekBarSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            seekBarSegments = Collections.emptyList();
        } else {
            List<SeekBarSegment> copy = new ArrayList<>(segments.size());
            for (SeekBarSegment source : segments) {
                if (source == null) continue;
                SeekBarSegment item = new SeekBarSegment();
                item.startProgress = source.startProgress;
                item.endProgress = source.endProgress;
                item.color = source.color;
                copy.add(item);
            }
            seekBarSegments = copy.isEmpty() ? Collections.emptyList() : copy;
        }
        emitSnapshot();
    }
    @Override public void updateEndingTime() { }
    @Override public void setChatReceiver(ChatReceiver receiver) { }
    @Override public void setQualityInfo(String info) { qualityInfo = info == null ? "" : info; }

    private void releaseEngineOnly() {
        main.removeCallbacks(ticker);
        // Invalidate already queued callbacks before releasing the old direct/video source.
        engineGeneration++;
        if (presenter != null && initialized) presenter.onEngineReleased();
        if (player != null) player.removeListener(stateListener);
        if (initializer != null) initializer.release();
        if (controller != null) controller.release();
        player = null; controller = null; initializer = null; trackSelector = null; initialized = false;
    }

    /** Adapter that lets transport/state code treat the bundled ExoPlayer 2 path like Media3. */
    private final class LegacyEngineBridge implements MobilePlaybackEngine {
        @Override public String getEngineName() { return "legacy ExoPlayer 2"; }

        @Override public boolean isInitialized() { return player != null && controller != null; }

        @Override public void open(String uri, long startPositionMs, boolean playWhenReady) {
            if (controller == null) throw new IllegalStateException("Legacy engine is not initialized");
            controller.openUrlList(Collections.singletonList(uri));
            if (startPositionMs > 0L) controller.setPositionMs(startPositionMs);
            controller.setPlayWhenReady(playWhenReady);
        }

        @Override public void setPlayWhenReady(boolean value) {
            if (controller != null) controller.setPlayWhenReady(value);
        }

        @Override public boolean getPlayWhenReady() {
            return controller != null && controller.getPlayWhenReady();
        }

        @Override public boolean isPlaying() {
            return controller != null && controller.isPlaying();
        }

        @Override public boolean isLoading() {
            return controller != null && controller.isLoading();
        }

        @Override public boolean containsMedia() {
            return controller != null && controller.containsMedia();
        }

        @Override public State getState() {
            if (player == null) return State.IDLE;
            switch (player.getPlaybackState()) {
                case Player.STATE_BUFFERING: return State.BUFFERING;
                case Player.STATE_READY: return State.READY;
                case Player.STATE_ENDED: return State.ENDED;
                case Player.STATE_IDLE:
                default: return State.IDLE;
            }
        }

        @Override public long getPositionMs() {
            return controller == null ? 0L : Math.max(0L, controller.getPositionMs());
        }

        @Override public long getDurationMs() {
            return controller == null ? 0L : Math.max(0L, controller.getDurationMs());
        }

        @Override public long getBufferedPositionMs() {
            return player == null ? 0L : Math.max(0L, player.getBufferedPosition());
        }

        @Override public void seekTo(long positionMs) {
            if (controller != null) controller.setPositionMs(Math.max(0L, positionMs));
        }

        @Override public void setSpeed(float speed) { if (controller != null) controller.setSpeed(speed); }
        @Override public float getSpeed() { return controller == null ? 1f : controller.getSpeed(); }
        @Override public void setPitch(float pitch) { if (controller != null) controller.setPitch(pitch); }
        @Override public float getPitch() { return controller == null ? 1f : controller.getPitch(); }
        @Override public void setVolume(float volume) { if (controller != null) controller.setVolume(volume); }
        @Override public float getVolume() { return controller == null ? 1f : controller.getVolume(); }
        @Override public void release() { releaseEngineOnly(); }
    }
}
