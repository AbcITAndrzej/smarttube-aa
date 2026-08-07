package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.controller.ExoPlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.ExoPlayerInitializer;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.TrackSelectorManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.background.MobileMediaSessionManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileBackgroundPlaybackRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobilePlaybackRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobilePlaybackSnapshot;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileTrack;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioStation;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioStationRepository;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

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
    private final MobileMediaSessionManager mediaSessionManager;
    private final boolean headlessPlaybackAllowed;
    private WeakReference<Context> hostContext = new WeakReference<>(null);
    private WeakReference<com.google.android.exoplayer2.ui.PlayerView> surface = new WeakReference<>(null);
    private Listener listener;
    private PlaybackPresenter presenter;
    private ExoPlayerController controller;
    private ExoPlayerInitializer initializer;
    private DefaultTrackSelector trackSelector;
    private SimpleExoPlayer player;
    private Video video;
    private boolean radioPlayback;
    private String radioMediaId;
    private String radioTitle = "";
    private String radioSubtitle = "";
    /** Keeps a newly selected direct stream playing through ExoPlayer's asynchronous reset. */
    private boolean radioAutoplayPending;
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
    private String pendingMediaId;
    private long pendingStartPositionMs;

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
            Listener current = listener;
            if (current != null) current.onPlaybackError(errors.playback(error));
        }
        };
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            emitSnapshot();
            main.postDelayed(this, SNAPSHOT_INTERVAL_MS);
        }
    };

    public LegacyMobilePlaybackRepository(Context context, LegacyMediaIndex index, LegacyErrorMapper errors) {
        this(context, index, errors, false);
    }

    public LegacyMobilePlaybackRepository(Context context, LegacyMediaIndex index,
                                          LegacyErrorMapper errors,
                                          boolean headlessPlaybackAllowed) {
        this.applicationContext = context.getApplicationContext();
        this.index = index;
        this.errors = errors;
        this.headlessPlaybackAllowed = headlessPlaybackAllowed;
        if (headlessPlaybackAllowed) {
            // Android Auto already owns the public MediaSession. Creating SmartTubeMobileSession here
            // gives one process two media sessions and two independent audio-focus clients that pause
            // each other. In automotive mode ExoPlayer is the only audio-focus owner.
            this.mediaSessionManager = null;
            MobileDiagnostics.info("P13-AA-Playback",
                    "internal mobile session disabled; ExoPlayer owns audio focus");
        } else {
            this.mediaSessionManager = new MobileMediaSessionManager(applicationContext,
                    new MobileMediaSessionManager.PlaybackControl() {
                        @Override public void playFromSystem() { setPlayWhenReady(true); }
                        @Override public void pauseFromSystem() { setPlayWhenReady(false); }
                        @Override public void seekToFromSystem(long positionMs) {
                            setPositionMs(Math.max(0L, positionMs));
                            emitSnapshot();
                        }
                        @Override public void setVolumeMultiplier(float multiplier) {
                            setVolume(Math.max(0f, Math.min(1f, multiplier)));
                        }
                    });
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
        if (pendingRadio) radioPlayback = true;
        ensureEngine();
        // Refresh the singleton presenter's context after Activity recreation.
        presenter = PlaybackPresenter.instance(context);
        presenter.setView(this);
        playerView.setPlayer(player);
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
            if (RadioStationRepository.isRadioMediaId(mediaId)) {
                radioPlayback = true;
                prepareRadio(mediaId);
                return;
            }
            radioPlayback = false;
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

    private void prepareRadio(String mediaId) {
        String stationId = RadioStationRepository.stationIdFromMediaId(mediaId);
        RadioStation station = RadioStationRepository.get(applicationContext).getStation(stationId);
        if (station == null) throw new IllegalStateException("Nie znaleziono stacji w lokalnym cache");
        radioPlayback = true;
        radioMediaId = mediaId;
        radioAutoplayPending = true;
        radioTitle = station.getName();
        String codec = station.getCodec().isEmpty() ? "Radio" : station.getCodec();
        radioSubtitle = station.getBitrate() > 0
                ? codec + " • " + station.getBitrate() + " kb/s" : codec;
        // ExoPlayerController shares SmartTube's format/state callbacks. They require a non-null
        // Video object even for a direct URL, so provide neutral metadata without a YouTube id.
        // Keeping videoId null prevents radio playback from entering YouTube history/state paths.
        Video radioMetadata = new Video();
        radioMetadata.title = radioTitle;
        radioMetadata.secondTitle = radioSubtitle;
        radioMetadata.channelId = "";
        radioMetadata.isLive = true;
        video = radioMetadata;
        if (mediaSessionManager != null) {
            // ExoPlayerController already manages focus for its direct stream. Keep the mobile
            // MediaSession for controls/metadata without letting it compete for the same focus.
            mediaSessionManager.setPlayerHandlesAudioFocus(true);
        }
        ensureEngine();
        controller.setVideo(radioMetadata);
        ended = false;
        MobileDiagnostics.info("P14-Radio", "prepare id=" + stationId
                + " stream=" + station.getStreamUrl());
        controller.openUrlList(Collections.singletonList(station.getStreamUrl()));
        play();
        emitSnapshot();
    }

    @Override public void play() {
        if (radioPlayback && player != null && player.getPlaybackState() != Player.STATE_READY) {
            radioAutoplayPending = true;
        }
        if (mediaSessionManager != null) {
            mediaSessionManager.requestPlay();
        } else {
            MobileDiagnostics.info("P13-AA-Playback", "direct ExoPlayer play");
            setPlayWhenReady(true);
            emitSnapshot();
        }
    }

    @Override public void pause() {
        radioAutoplayPending = false;
        if (mediaSessionManager != null) {
            mediaSessionManager.pauseByUser();
        } else {
            MobileDiagnostics.info("P13-AA-Playback", "direct ExoPlayer pause");
            setPlayWhenReady(false);
            emitSnapshot();
        }
    }
    @Override public void seekTo(long positionMs) { setPositionMs(Math.max(0, positionMs)); emitSnapshot(); }
    @Override public void seekBy(long deltaMs) { seekTo(Math.max(0, getPositionMs() + deltaMs)); }
    @Override public void setPlaybackSpeed(float speed) { setSpeed(speed); emitSnapshot(); }

    @Override public void selectAudioTrack(String trackId) {
        FormatItem item = trackMapper.find(getAudioFormats(), trackId);
        if (item != null) setFormat(item);
    }

    @Override public void selectSubtitleTrack(String trackId) {
        FormatItem item = trackMapper.find(getSubtitleFormats(), trackId);
        if (item != null) setFormat(item);
    }

    @Override public void release() {
        MobileDiagnostics.debug("DataPlayer", "release");
        engineGeneration++;
        if (mediaSessionManager != null) mediaSessionManager.release();
        main.removeCallbacks(ticker);
        if (presenter != null && initialized) {
            presenter.onViewPaused();
            presenter.onEngineReleased();
            presenter.onViewDestroyed();
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
        presenter = null;
        video = null;
        radioPlayback = false;
        radioMediaId = null;
        radioAutoplayPending = false;
        radioTitle = "";
        radioSubtitle = "";
        initialized = false;
        buffering = false;
        ended = false;
        listener = null;
        pendingMediaId = null;
        pendingStartPositionMs = 0;
        hostContext.clear();
    }

    private void ensureEngine() {
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
        if (headlessPlaybackAllowed || radioPlayback) {
            // Android Auto is an audio surface. Do not allocate video/subtitle decoders or a fake
            // SurfaceTexture; this also prevents a forbidden video representation from killing audio.
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setRendererDisabled(TrackSelectorManager.RENDERER_INDEX_VIDEO, true)
                    .setRendererDisabled(TrackSelectorManager.RENDERER_INDEX_SUBTITLE, true));
            MobileDiagnostics.info(radioPlayback ? "P14-Radio" : "P13-AA-Playback",
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

    private void notifyError(Throwable error) {
        MobileDiagnostics.error("DataPlayer", "player bridge failure", error);
        Listener current = listener;
        if (current != null) current.onPlaybackError(errors.playback(error));
    }

    private void emitSnapshot() {
        long position = Math.max(0, getPositionMs());
        long duration = Math.max(0, getDurationMs());
        long buffered = player == null ? 0 : Math.max(0, player.getBufferedPosition());
        List<MobileTrack> audio = controller == null ? Collections.emptyList()
                : trackMapper.map(controller.getAudioFormats(), MobileTrack.Type.AUDIO);
        List<MobileTrack> subtitles = controller == null ? Collections.emptyList()
                : trackMapper.map(controller.getSubtitleFormats(), MobileTrack.Type.SUBTITLE);
        String id = radioPlayback ? radioMediaId
                : video == null ? null : LegacyMediaMapper.stableId(video);
        MobilePlaybackSnapshot snapshot = new MobilePlaybackSnapshot(id,
                radioPlayback ? radioTitle
                        : video == null ? "" : LegacyMediaMapper.safe(video.getTitleFull()),
                radioPlayback ? radioSubtitle
                        : video == null ? "" : LegacyMediaMapper.safe(video.getSecondTitleFull()),
                containsMedia(), isPlaying(), buffering || (!radioPlayback && isLoading()),
                isPlaybackEnded(),
                position, duration, buffered, getSpeed(), audio, subtitles);
        if (mediaSessionManager != null) mediaSessionManager.updatePlayback(snapshot);
        Listener current = listener;
        if (current != null) current.onPlaybackSnapshot(snapshot);
    }

    @Override public void setHostVisible(boolean visible) {
        if (mediaSessionManager != null) mediaSessionManager.setHostVisible(visible);
    }

    @Override public boolean isBackgroundPlaybackEnabled() {
        return headlessPlaybackAllowed
                || mediaSessionManager != null && mediaSessionManager.isBackgroundPlaybackEnabled();
    }

    // PlaybackView / PlayerEngine delegation.
    @Override public void openSabr(MediaItemFormatInfo info) { if (controller != null) controller.openSabr(info); }
    @Override public void openDash(MediaItemFormatInfo info) { if (controller != null) controller.openDash(info); }
    @Override public void openDash(InputStream manifest) { if (controller != null) controller.openDash(manifest); }
    @Override public void openDashUrl(String url) { if (controller != null) controller.openDashUrl(url); }
    @Override public void openHlsUrl(String url) { if (controller != null) controller.openHlsUrl(url); }
    @Override public void openUrlList(List<String> urls) { if (controller != null) controller.openUrlList(urls); }
    @Override public void openMerged(MediaItemFormatInfo info, String hls) { if (controller != null) controller.openMerged(info, hls); }
    @Override public void openMerged(InputStream dash, String hls) { if (controller != null) controller.openMerged(dash, hls); }
    @Override public long getPositionMs() { return controller == null ? 0 : controller.getPositionMs(); }
    @Override public void setPositionMs(long positionMs) { if (controller != null) controller.setPositionMs(positionMs); }
    @Override public long getDurationMs() { return controller == null ? 0 : controller.getDurationMs(); }
    @Override public void setPlayWhenReady(boolean play) { if (controller != null) controller.setPlayWhenReady(play); }
    @Override public boolean getPlayWhenReady() { return controller != null && controller.getPlayWhenReady(); }
    @Override public boolean isPlaying() { return controller != null && controller.isPlaying(); }
    private boolean isPlaybackEnded() { return ended || player != null && player.getPlaybackState() == Player.STATE_ENDED; }
    @Override public boolean isLoading() { return controller != null && controller.isLoading(); }
    @Override public List<FormatItem> getVideoFormats() { return controller == null ? Collections.emptyList() : controller.getVideoFormats(); }
    @Override public List<FormatItem> getAudioFormats() { return controller == null ? Collections.emptyList() : controller.getAudioFormats(); }
    @Override public List<FormatItem> getSubtitleFormats() { return controller == null ? Collections.emptyList() : controller.getSubtitleFormats(); }
    @Override public void setFormat(FormatItem option) { if (controller != null) controller.selectFormat(option); emitSnapshot(); }
    @Override public FormatItem getVideoFormat() { return controller == null ? null : controller.getVideoFormat(); }
    @Override public FormatItem getAudioFormat() { return controller == null ? null : controller.getAudioFormat(); }
    @Override public FormatItem getSubtitleFormat() { return controller == null ? null : controller.getSubtitleFormat(); }
    @Override public boolean isEngineInitialized() { return player != null; }
    @Override public void restartEngine() {
        Video current = video;
        String currentRadio = radioMediaId;
        long position = getPositionMs();
        releaseEngineOnly();
        if (currentRadio != null) prepare(currentRadio, 0L);
        else if (current != null) prepare(LegacyMediaMapper.stableId(current), position);
    }
    @Override public void reloadPlayback() {
        if (radioMediaId != null) {
            String current = radioMediaId;
            releaseEngineOnly();
            prepare(current, 0L);
        } else if (presenter != null) {
            presenter.onEngineReleased();
            presenter.onEngineInitialized();
        }
    }
    @Override public void blockEngine(boolean block) { engineBlocked = block; }
    @Override public boolean isEngineBlocked() { return engineBlocked; }
    @Override public boolean isInPIPMode() { Context c = hostContext.get(); return Build.VERSION.SDK_INT >= 24 && c instanceof Activity && ((Activity)c).isInPictureInPictureMode(); }
    @Override public boolean containsMedia() { return controller != null && controller.containsMedia(); }
    @Override public void setSpeed(float speed) { if (controller != null) controller.setSpeed(speed); }
    @Override public float getSpeed() { float value = controller == null ? 1f : controller.getSpeed(); return value <= 0 ? 1f : value; }
    @Override public void setPitch(float pitch) { if (controller != null) controller.setPitch(pitch); }
    @Override public float getPitch() { return controller == null ? 1f : controller.getPitch(); }
    @Override public void setVolume(float volume) { if (controller != null) controller.setVolume(volume); }
    @Override public float getVolume() { return controller == null ? 1f : controller.getVolume(); }
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
        // The phone surface still behaves as the original embed player. Android Auto must use the
        // full error-recovery path (applyNoPlaybackFix + reloadVideo) instead of finish/release.
        return !headlessPlaybackAllowed;
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
    @Override public void setSeekBarSegments(List<SeekBarSegment> segments) { }
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
}
