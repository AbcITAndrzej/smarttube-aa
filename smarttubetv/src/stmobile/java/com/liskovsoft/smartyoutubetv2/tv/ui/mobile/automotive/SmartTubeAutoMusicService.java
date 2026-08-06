package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.media.MediaBrowserServiceCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileBrowseRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobilePlaybackRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileRequest;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileResultCallback;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy.SmartTubeMobileNativeProvider;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileBrowsePayload;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileError;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileMediaItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobilePlaybackSnapshot;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileSection;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * P13-AA1.8: Android Auto media browser backed by SmartTube's current account/data layer.
 *
 * Android Auto renders the car-safe interface. This service supplies the browse tree,
 * metadata, queue and transport controls.
 */
public final class SmartTubeAutoMusicService extends MediaBrowserServiceCompat {
    private static final String ROOT = "smarttube:auto:root";
    private static final String PAGE_PREFIX = "page:";
    private static final String SECTION_PREFIX = "section:";
    private static final String ITEM_PREFIX = "item:";
    private static final String MEDIA_PREFIX = "media:";
    private static final String MEDIA_ACTION_PLAY_LIKED = "action:liked:play_all";
    private static final String MEDIA_ACTION_SHUFFLE_LIKED = "action:liked:shuffle";
    private static final String LIKED_PAGE_ID = "liked_music";
    private static final String LIKED_CONTAINER_ID = SECTION_PREFIX + "liked_music:all";
    private static final String[] LIKED_BROWSE_ALIASES = {"VLLM", "LM", "liked", "liked_music"};
    private static final String ACTION_TOGGLE_LIKE = "com.liskovsoft.smarttube.mobile.auto.TOGGLE_LIKE";
    private static final String ACTION_LIKE = "com.liskovsoft.smarttube.mobile.auto.LIKE";
    private static final String ACTION_UNLIKE = "com.liskovsoft.smarttube.mobile.auto.UNLIKE";
    private static final String ACTION_RESTART = "com.liskovsoft.smarttube.mobile.auto.RESTART";
    private static final String ACTION_PLAY_LIKED = "com.liskovsoft.smarttube.mobile.auto.PLAY_LIKED";
    private static final String ACTION_TOGGLE_SHUFFLE = "com.liskovsoft.smarttube.mobile.auto.TOGGLE_SHUFFLE";
    private static final String ACTION_CYCLE_REPEAT = "com.liskovsoft.smarttube.mobile.auto.CYCLE_REPEAT";
    private static final String ACTION_TOGGLE_AUTO_NEXT = "com.liskovsoft.smarttube.mobile.auto.TOGGLE_AUTO_NEXT";
    private static final String NOTIFICATION_ACTION_PLAY = "com.liskovsoft.smarttube.mobile.auto.NOTIFICATION_PLAY";
    private static final String NOTIFICATION_ACTION_PAUSE = "com.liskovsoft.smarttube.mobile.auto.NOTIFICATION_PAUSE";
    private static final String NOTIFICATION_ACTION_NEXT = "com.liskovsoft.smarttube.mobile.auto.NOTIFICATION_NEXT";
    private static final String NOTIFICATION_ACTION_PREVIOUS = "com.liskovsoft.smarttube.mobile.auto.NOTIFICATION_PREVIOUS";
    private static final String NOTIFICATION_ACTION_RESTART = "com.liskovsoft.smarttube.mobile.auto.NOTIFICATION_RESTART";
    private static final String NOTIFICATION_ACTION_LIKE = "com.liskovsoft.smarttube.mobile.auto.NOTIFICATION_LIKE";
    private static final String NOTIFICATION_CHANNEL_ID = "smarttube_auto_music";
    private static final int NOTIFICATION_ID = 20260803;
    private static final String PREFS = "smarttube_auto_music";
    private static final String PREF_BROWSER_ID = "last_browser_id";
    private static final String PREF_CONTAINER_ID = "last_container_id";
    private static final String PREF_VIDEO_ID = "last_video_id";
    private static final String PREF_TITLE = "last_title";
    private static final String PREF_SUBTITLE = "last_subtitle";
    private static final String PREF_THUMB = "last_thumb";
    private static final String PREF_DURATION = "last_duration";
    private static final String PREF_POSITION = "last_position";
    private static final String PREF_QUEUE_INDEX = "last_queue_index";
    private static final String PREF_SOURCE_ID = "last_source_id";
    private static final String PREF_LIKE_PREFIX = "liked:";
    private static final long AUTO_ADVANCE_EARLY_MS = 1200L;
    private static final long RESUME_SAVE_INTERVAL_MS = 5000L;
    private static final long AUTO_RESUME_DELAY_MS = 900L;
    private static final long FORCE_ZERO_GUARD_MS = 1600L;
    private static final long FORCE_ZERO_THRESHOLD_MS = 5000L;
    private static final long DOUBLE_PREVIOUS_WINDOW_MS = 1600L;
    private static final long AUTO_ADVANCE_MIN_DURATION_MS = 10000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, MobileMediaItem> mediaByBrowserId = new LinkedHashMap<>();
    private final Map<String, List<String>> queueByContainer = new LinkedHashMap<>();
    private final Map<String, String> sourceByContainer = new LinkedHashMap<>();
    private final List<MobileRequest> activeBrowseRequests = new ArrayList<>();
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor();
    private final Random shuffleRandom = new Random();
    private final Map<String, Boolean> likeStateByVideoId = new LinkedHashMap<>();
    private final List<String> likedCatalogVideoIds = new ArrayList<>();

    private SmartTubeMobileNativeProvider provider;
    private MobileBrowseRepository browseRepository;
    private MobilePlaybackRepository playbackRepository;
    private MediaSessionCompat mediaSession;
    private SharedPreferences resumePrefs;
    private String activeContainerId;
    private List<String> activeQueue = Collections.emptyList();
    private int activeQueueIndex = -1;
    private MobileMediaItem activeItem;
    private boolean activeLiked;
    private boolean autoNextEnabled = true;
    private int repeatMode = PlaybackStateCompat.REPEAT_MODE_ALL;
    private int shuffleMode = PlaybackStateCompat.SHUFFLE_MODE_NONE;
    private String lastAutoAdvancedFromBrowserId;
    private String lastPublishedQueueKey;
    private String lastPublishedMetadataKey;
    private String lastPublishedPlaybackControlsKey;
    private int lastPublishedPlaybackState = Integer.MIN_VALUE;
    private long lastPublishedPlaybackPositionMs;
    private long lastPublishedPlaybackAtMs;
    private long lastPublishedActiveQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID;
    private float lastPublishedPlaybackSpeed;
    private long lastMetadataDurationMs;
    private long lastResumeSavePositionMs = -1L;
    private long forceZeroUntilMs;
    private long lastSeekToZeroCommandMs;
    private String lastPhoneNotificationKey;
    private boolean resumeRedirectInProgress;
    private boolean autoResumeScheduled;
    private boolean resumeLoadInProgress;
    private boolean resumeAutoplayConsumed;
    private boolean likedPlaybackLoadInProgress;
    private boolean pendingLikedShuffle;
    private boolean likedCatalogWarmStarted;
    private boolean likedCatalogReady;
    private String lastRecoveryMediaId;
    private int recoveryAttemptsForMediaId;
    private int lastPlaybackState = PlaybackStateCompat.STATE_NONE;
    private long lastPlaybackPositionMs;
    private float lastPlaybackSpeed;
    private boolean destroyed;

    @Override
    public void onCreate() {
        super.onCreate();
        destroyed = false;
        MobileDiagnostics.info("P13-AA-Service", "onCreate P13-AA1.8");

        resumePrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createNotificationChannel();

        provider = SmartTubeMobileNativeProvider.create(getApplicationContext());
        browseRepository = provider.browseRepository();
        browseRepository.setItemUpdateListener((itemId, payload) -> mainHandler.post(
                () -> applyBackgroundItemUpdate(itemId, payload)));
        playbackRepository = provider.automotivePlaybackRepository();
        MobileDiagnostics.info("P13-AA-Playback",
                "headless audio repository created; Auto session is the only public session");

        mediaSession = new MediaSessionCompat(this, "SmartTubeAutoMusic");
        mediaSession.setRatingType(RatingCompat.RATING_THUMB_UP_DOWN);
        mediaSession.setRepeatMode(repeatMode);
        mediaSession.setShuffleMode(shuffleMode);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() {
                MobileDiagnostics.info("P13-AA-Playback", "command play");
                playOrResume();
            }

            @Override public void onPause() {
                MobileDiagnostics.info("P13-AA-Playback", "command pause");
                playbackRepository.pause();
            }

            @Override public void onStop() {
                playbackRepository.pause();
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED, 0L, 0f);
            }

            @Override public void onSeekTo(long pos) {
                long safePosition = Math.max(0L, pos);
                forceZeroUntilMs = 0L;
                if (safePosition <= 1500L) {
                    long now = System.currentTimeMillis();
                    if (lastSeekToZeroCommandMs > 0L
                            && now - lastSeekToZeroCommandMs <= DOUBLE_PREVIOUS_WINDOW_MS) {
                        lastSeekToZeroCommandMs = 0L;
                        MobileDiagnostics.info("P13-AA-Queue",
                                "double previous via seekTo(0)");
                        skipBy(-1);
                    } else {
                        lastSeekToZeroCommandMs = now;
                        MobileDiagnostics.info("P13-AA-Queue",
                                "first previous restarts current track");
                        playbackRepository.seekTo(0L);
                    }
                    return;
                }
                lastSeekToZeroCommandMs = 0L;
                playbackRepository.seekTo(safePosition);
            }

            @Override public void onSkipToNext() {
                MobileDiagnostics.info("P13-AA-Queue", "command next");
                skipBy(1);
            }

            @Override public void onSkipToPrevious() {
                MobileDiagnostics.info("P13-AA-Queue", "command previous");
                skipBy(-1);
            }

            @Override public void onSkipToQueueItem(long id) {
                MobileDiagnostics.info("P13-AA-Queue", "command queueItem id=" + id);
                playQueueIndex((int) id, false);
            }

            @Override public void onPlayFromMediaId(String mediaId, Bundle extras) {
                MobileDiagnostics.info("P13-AA-Playback",
                        "command playFromMediaId browserId=" + mediaId);
                if (MEDIA_ACTION_PLAY_LIKED.equals(mediaId)) {
                    requestPlayLiked(false, "aa-play-all");
                } else if (MEDIA_ACTION_SHUFFLE_LIKED.equals(mediaId)) {
                    requestPlayLiked(true, "aa-shuffle");
                } else {
                    playBrowserMediaId(mediaId);
                }
            }

            @Override public void onCustomAction(String action, Bundle extras) {
                if (ACTION_TOGGLE_LIKE.equals(action)) {
                    toggleLike();
                } else if (ACTION_LIKE.equals(action)) {
                    setLiked(true);
                } else if (ACTION_UNLIKE.equals(action)) {
                    setLiked(false);
                } else if (ACTION_RESTART.equals(action)) {
                    restartCurrentTrack("media-session");
                } else if (ACTION_PLAY_LIKED.equals(action)) {
                    requestPlayLiked(false, "mobile-player");
                } else if (ACTION_TOGGLE_SHUFFLE.equals(action)) {
                    toggleShuffle();
                } else if (ACTION_CYCLE_REPEAT.equals(action)) {
                    cycleRepeatMode();
                } else if (ACTION_TOGGLE_AUTO_NEXT.equals(action)) {
                    toggleAutoNext();
                }
            }

            @Override public void onSetRating(RatingCompat rating) {
                if (rating != null && rating.getRatingStyle() == RatingCompat.RATING_THUMB_UP_DOWN
                        && rating.isRated()) {
                    setLiked(rating.isThumbUp());
                }
            }

            @Override public void onSetRepeatMode(int repeatMode) {
                setRepeatModeFromCommand(repeatMode, "standard");
            }

            @Override public void onSetShuffleMode(int shuffleMode) {
                setShuffleModeFromCommand(shuffleMode, "standard");
            }
        });

        playbackRepository.setListener(new MobilePlaybackRepository.Listener() {
            @Override
            public void onPlaybackSnapshot(MobilePlaybackSnapshot snapshot) {
                mainHandler.post(() -> applySnapshot(snapshot));
            }

            @Override
            public void onPlaybackError(MobileError error) {
                MobileDiagnostics.warn("P13-AA-Playback",
                        "repository error kind=" + (error == null ? "unknown" : error.getKind())
                                + " retryable=" + (error != null && error.isRetryable())
                                + " message=" + (error == null ? "unknown" : error.getMessage()));
                mainHandler.post(() -> {
                    updatePlaybackState(PlaybackStateCompat.STATE_ERROR, 0L, 0f);
                    String mediaId = activeItem == null ? null : activeItem.getId();
                    if (mediaId != null
                            && mediaId.equals(lastRecoveryMediaId)
                            && recoveryAttemptsForMediaId < 1) {
                        recoveryAttemptsForMediaId++;
                        MobileDiagnostics.warn("P13-AA-Recovery",
                                "retry current once mediaId=" + mediaId
                                        + " position=" + lastPlaybackPositionMs);
                        playbackRepository.prepare(
                                mediaId, Math.max(0L, lastPlaybackPositionMs));
                        playbackRepository.play();
                        return;
                    }
                    if (autoNextEnabled && activeQueue != null && activeQueue.size() > 1) {
                        int nextIndex = resolveAutoNextIndex();
                        if (nextIndex >= 0 && nextIndex < activeQueue.size()
                                && nextIndex != activeQueueIndex) {
                            MobileDiagnostics.warn("P13-AA-Recovery",
                                    "retry exhausted; skipping to next index=" + nextIndex);
                            playQueueIndex(nextIndex, true);
                        }
                    }
                });
            }
        });

        mediaSession.setActive(true);
        setSessionToken(mediaSession.getSessionToken());
        updatePlaybackState(PlaybackStateCompat.STATE_NONE, 0L, 0f);
        warmLikedStateCache(0);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            return START_STICKY;
        }

        MobileDiagnostics.info("P13-AA-Phone", "notification action=" + action);
        if (NOTIFICATION_ACTION_PLAY.equals(action)) {
            playOrResume();
        } else if (NOTIFICATION_ACTION_PAUSE.equals(action)) {
            if (playbackRepository != null) {
                playbackRepository.pause();
            }
        } else if (NOTIFICATION_ACTION_NEXT.equals(action)) {
            skipBy(1);
        } else if (NOTIFICATION_ACTION_PREVIOUS.equals(action)) {
            skipBy(-1);
        } else if (NOTIFICATION_ACTION_RESTART.equals(action)) {
            restartCurrentTrack("notification");
        } else if (NOTIFICATION_ACTION_LIKE.equals(action)) {
            toggleLike();
        }
        updatePhoneNotification();
        return START_STICKY;
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        MobileDiagnostics.info("P13-AA-Resume",
                "onGetRoot client=" + clientPackageName + " uid=" + clientUid);
        return new BrowserRoot(ROOT, null);
    }

    @Override
    public void onLoadChildren(String parentId,
                               Result<List<MediaBrowserCompat.MediaItem>> result) {
        if (ROOT.equals(parentId)) {
            result.sendResult(createRootItems());
            scheduleAutoResumeAfterOpen();
            return;
        }

        if ((PAGE_PREFIX + LIKED_PAGE_ID).equals(parentId)) {
            result.detach();
            loadLikedMusicPage(result);
            return;
        }

        if (parentId != null && parentId.startsWith(PAGE_PREFIX)) {
            result.detach();
            loadPage(parentId.substring(PAGE_PREFIX.length()), result);
            return;
        }

        if (parentId != null && parentId.startsWith(ITEM_PREFIX)) {
            result.detach();
            loadItem(parentId.substring(ITEM_PREFIX.length()), result);
            return;
        }

        if (parentId != null && parentId.startsWith(SECTION_PREFIX)) {
            List<String> ids = queueByContainer.get(parentId);
            result.sendResult(ids == null ? Collections.emptyList() : mediaItems(ids));
            return;
        }

        result.sendResult(Collections.emptyList());
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        MobileDiagnostics.info("P13-AA-Service", "onDestroy");
        for (MobileRequest request : new ArrayList<>(activeBrowseRequests)) {
            if (request != null) {
                request.cancel();
            }
        }
        activeBrowseRequests.clear();
        lastPublishedQueueKey = null;
        if (browseRepository != null) {
            browseRepository.setItemUpdateListener(null);
        }
        actionExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        if (playbackRepository != null) {
            playbackRepository.release();
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        mediaByBrowserId.clear();
        queueByContainer.clear();
        super.onDestroy();
    }

    private List<MediaBrowserCompat.MediaItem> createRootItems() {
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        result.add(browsable(PAGE_PREFIX + "playlists", "Playlisty", "Wybierz playlistę z konta"));
        result.add(browsable(PAGE_PREFIX + "music", "Automatyczne miksy", "Polecane i miksy z konta"));
        result.add(browsable(PAGE_PREFIX + LIKED_PAGE_ID,
                "♥ Polubiona muzyka", "Playlista Muzyka, która Ci się podoba"));
        result.add(browsable(PAGE_PREFIX + "history", "Ostatnio odtwarzane", "Historia oglądania i słuchania"));
        result.add(browsable(PAGE_PREFIX + "subscriptions", "Subskrypcje", "Najnowsze materiały z subskrypcji"));
        return result;
    }

    private void warmLikedStateCache(int aliasIndex) {
        if (destroyed || likedCatalogReady) {
            return;
        }
        if (aliasIndex == 0) {
            if (likedCatalogWarmStarted) {
                return;
            }
            likedCatalogWarmStarted = true;
            MobileDiagnostics.info("P13-AA-Liked",
                    "warming account like state");
        }
        if (aliasIndex >= LIKED_BROWSE_ALIASES.length) {
            MobileDiagnostics.warn("P13-AA-Liked",
                    "account like state unavailable after aliases");
            return;
        }

        String alias = LIKED_BROWSE_ALIASES[aliasIndex];
        final MobileRequest[] requestHolder = { MobileRequest.NONE };
        try {
            MobileRequest request = browseRepository.loadBrowse(alias,
                    new MobileResultCallback<MobileBrowsePayload>() {
                        @Override
                        public void onSuccess(MobileBrowsePayload payload) {
                            mainHandler.post(() -> {
                                finishBrowseRequest(requestHolder[0]);
                                List<MediaBrowserCompat.MediaItem> items =
                                        convertLikedPayload(alias, payload);
                                if (!items.isEmpty()) {
                                    MobileDiagnostics.info("P13-AA-Liked",
                                            "account like state ready alias=" + alias
                                                    + " tracks="
                                                    + likedCatalogVideoIds.size());
                                } else {
                                    warmLikedStateCache(aliasIndex + 1);
                                }
                            });
                        }

                        @Override
                        public void onError(MobileError error) {
                            MobileDiagnostics.warn("P13-AA-Liked",
                                    "like cache alias failed alias=" + alias
                                            + " error=" + error);
                            mainHandler.post(() -> {
                                finishBrowseRequest(requestHolder[0]);
                                warmLikedStateCache(aliasIndex + 1);
                            });
                        }
                    });
            requestHolder[0] = request == null ? MobileRequest.NONE : request;
            activeBrowseRequests.add(requestHolder[0]);
        } catch (Throwable error) {
            MobileDiagnostics.error("P13-AA-Liked",
                    "like cache alias threw alias=" + alias, error);
            mainHandler.post(() -> warmLikedStateCache(aliasIndex + 1));
        }
    }

    private void loadLikedMusicPage(
            Result<List<MediaBrowserCompat.MediaItem>> result) {
        AtomicBoolean delivered = new AtomicBoolean(false);
        MobileDiagnostics.info("P13-AA-Liked",
                "load liked music aliases=" + LIKED_BROWSE_ALIASES.length);
        tryLoadLikedAlias(0, result, delivered);
    }

    private void tryLoadLikedAlias(
            int aliasIndex,
            Result<List<MediaBrowserCompat.MediaItem>> result,
            AtomicBoolean delivered) {
        if (destroyed || delivered.get()) {
            return;
        }
        if (aliasIndex >= LIKED_BROWSE_ALIASES.length) {
            deliverLikedBrowseResult(
                    result, delivered, Collections.emptyList(), "none");
            return;
        }

        String alias = LIKED_BROWSE_ALIASES[aliasIndex];
        final MobileRequest[] requestHolder = { MobileRequest.NONE };
        MobileDiagnostics.info("P13-AA-Liked",
                "try liked browse alias=" + alias + " index=" + aliasIndex);
        try {
            MobileRequest request = browseRepository.loadBrowse(alias,
                    new MobileResultCallback<MobileBrowsePayload>() {
                        @Override
                        public void onSuccess(MobileBrowsePayload payload) {
                            mainHandler.post(() -> {
                                finishBrowseRequest(requestHolder[0]);
                                List<MediaBrowserCompat.MediaItem> items =
                                        convertLikedPayload(alias, payload);
                                if (!items.isEmpty()) {
                                    deliverLikedBrowseResult(
                                            result, delivered, items, alias);
                                } else {
                                    tryLoadLikedAlias(
                                            aliasIndex + 1, result, delivered);
                                }
                            });
                        }

                        @Override
                        public void onError(MobileError error) {
                            MobileDiagnostics.warn("P13-AA-Liked",
                                    "liked alias failed alias=" + alias
                                            + " error=" + error);
                            mainHandler.post(() -> {
                                finishBrowseRequest(requestHolder[0]);
                                tryLoadLikedAlias(
                                        aliasIndex + 1, result, delivered);
                            });
                        }
                    });
            requestHolder[0] = request == null ? MobileRequest.NONE : request;
            activeBrowseRequests.add(requestHolder[0]);
        } catch (Throwable error) {
            MobileDiagnostics.error("P13-AA-Liked",
                    "liked alias threw alias=" + alias, error);
            mainHandler.post(() -> tryLoadLikedAlias(
                    aliasIndex + 1, result, delivered));
        }
    }

    private void deliverLikedBrowseResult(
            Result<List<MediaBrowserCompat.MediaItem>> result,
            AtomicBoolean delivered,
            List<MediaBrowserCompat.MediaItem> items,
            String alias) {
        if (!delivered.compareAndSet(false, true) || destroyed) {
            return;
        }
        try {
            result.sendResult(items);
            MobileDiagnostics.info("P13-AA-Liked",
                    "liked page delivered alias=" + alias
                            + " items=" + items.size());
        } catch (Throwable error) {
            MobileDiagnostics.error("P13-AA-Liked",
                    "liked result delivery failed", error);
        }
    }

    private void finishBrowseRequest(MobileRequest request) {
        if (request == null) {
            return;
        }
        request.cancel();
        activeBrowseRequests.remove(request);
    }

    private List<MediaBrowserCompat.MediaItem> convertLikedPayload(
            String alias, MobileBrowsePayload payload) {
        if (payload == null || payload.getSections() == null) {
            return Collections.emptyList();
        }
        List<String> playableIds = new ArrayList<>();
        SharedPreferences.Editor likeEditor =
                resumePrefs == null ? null : resumePrefs.edit();

        for (MobileSection section : payload.getSections()) {
            if (section == null || section.getItems() == null) {
                continue;
            }
            for (MobileMediaItem item : section.getItems()) {
                if (item == null || !item.isPlayable() || item.getId() == null
                        || item.getId().trim().isEmpty()) {
                    continue;
                }
                String browserId = MEDIA_PREFIX + item.getId();
                mediaByBrowserId.put(browserId, item);
                if (!playableIds.contains(browserId)) {
                    playableIds.add(browserId);
                }
                likeStateByVideoId.put(item.getId(), true);
                if (likeEditor != null) {
                    likeEditor.putBoolean(PREF_LIKE_PREFIX + item.getId(), true);
                }
            }
        }

        if (likeEditor != null) {
            likeEditor.apply();
        }
        if (playableIds.isEmpty()) {
            MobileDiagnostics.info("P13-AA-Liked",
                    "liked payload empty alias=" + alias);
            return Collections.emptyList();
        }

        likedCatalogVideoIds.clear();
        for (String browserId : playableIds) {
            if (browserId.startsWith(MEDIA_PREFIX)) {
                likedCatalogVideoIds.add(
                        browserId.substring(MEDIA_PREFIX.length()));
            }
        }
        likedCatalogReady = true;
        queueByContainer.put(LIKED_CONTAINER_ID, playableIds);
        if (activeItem != null && playableIds.contains(MEDIA_PREFIX + activeItem.getId())
                && !activeLiked) {
            activeLiked = true;
            updateMetadata(activeItem, lastMetadataDurationMs);
            updatePlaybackState(
                    lastPlaybackState, lastPlaybackPositionMs, lastPlaybackSpeed);
        }

        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        result.add(playableAction(
                MEDIA_ACTION_PLAY_LIKED,
                "▶ Odtwórz wszystkie",
                playableIds.size() + " polubionych utworów"));
        result.add(playableAction(
                MEDIA_ACTION_SHUFFLE_LIKED,
                "🔀 Odtwórz losowo",
                "Losowa kolejność polubionej muzyki"));
        result.addAll(mediaItems(playableIds));
        MobileDiagnostics.info("P13-AA-Liked",
                "liked payload alias=" + alias
                        + " tracks=" + playableIds.size());
        return result;
    }

    private void requestPlayLiked(boolean shuffle, String reason) {
        List<String> likedQueue = queueByContainer.get(LIKED_CONTAINER_ID);
        if (likedQueue != null && !likedQueue.isEmpty()) {
            startLikedQueue(shuffle, reason + "-cached");
            return;
        }

        pendingLikedShuffle = shuffle;
        if (likedPlaybackLoadInProgress) {
            MobileDiagnostics.info("P13-AA-Liked",
                    "liked playback load already running reason=" + reason);
            return;
        }

        likedPlaybackLoadInProgress = true;
        MobileDiagnostics.info("P13-AA-Liked",
                "load liked queue for playback reason=" + reason
                        + " shuffle=" + shuffle);
        tryLoadLikedForPlayback(0, reason);
    }

    private void tryLoadLikedForPlayback(int aliasIndex, String reason) {
        if (destroyed) {
            likedPlaybackLoadInProgress = false;
            return;
        }
        if (aliasIndex >= LIKED_BROWSE_ALIASES.length) {
            likedPlaybackLoadInProgress = false;
            MobileDiagnostics.warn("P13-AA-Liked",
                    "liked playback unavailable reason=" + reason);
            return;
        }

        String alias = LIKED_BROWSE_ALIASES[aliasIndex];
        final MobileRequest[] requestHolder = { MobileRequest.NONE };
        try {
            MobileRequest request = browseRepository.loadBrowse(alias,
                    new MobileResultCallback<MobileBrowsePayload>() {
                        @Override
                        public void onSuccess(MobileBrowsePayload payload) {
                            mainHandler.post(() -> {
                                finishBrowseRequest(requestHolder[0]);
                                List<MediaBrowserCompat.MediaItem> items =
                                        convertLikedPayload(alias, payload);
                                if (!items.isEmpty()) {
                                    likedPlaybackLoadInProgress = false;
                                    startLikedQueue(
                                            pendingLikedShuffle,
                                            reason + "-" + alias);
                                } else {
                                    tryLoadLikedForPlayback(
                                            aliasIndex + 1, reason);
                                }
                            });
                        }

                        @Override
                        public void onError(MobileError error) {
                            MobileDiagnostics.warn("P13-AA-Liked",
                                    "liked playback alias failed alias=" + alias
                                            + " error=" + error);
                            mainHandler.post(() -> {
                                finishBrowseRequest(requestHolder[0]);
                                tryLoadLikedForPlayback(
                                        aliasIndex + 1, reason);
                            });
                        }
                    });
            requestHolder[0] = request == null ? MobileRequest.NONE : request;
            activeBrowseRequests.add(requestHolder[0]);
        } catch (Throwable error) {
            MobileDiagnostics.error("P13-AA-Liked",
                    "liked playback alias threw alias=" + alias, error);
            mainHandler.post(() -> tryLoadLikedForPlayback(
                    aliasIndex + 1, reason));
        }
    }

    private void startLikedQueue(boolean shuffle, String reason) {
        List<String> likedQueue = queueByContainer.get(LIKED_CONTAINER_ID);
        if (likedQueue == null || likedQueue.isEmpty()) {
            MobileDiagnostics.warn("P13-AA-Liked",
                    "start liked ignored: empty queue reason=" + reason);
            return;
        }

        int index = shuffle && likedQueue.size() > 1
                ? shuffleRandom.nextInt(likedQueue.size()) : 0;
        if (shuffle) {
            setShuffleModeFromCommand(
                    PlaybackStateCompat.SHUFFLE_MODE_ALL, "liked-music");
        }
        MobileDiagnostics.info("P13-AA-Liked",
                "start liked reason=" + reason
                        + " index=" + index
                        + " size=" + likedQueue.size()
                        + " shuffle=" + shuffle);
        playBrowserMediaId(likedQueue.get(index), false, false);
    }

    private void loadPage(String pageId,
                          Result<List<MediaBrowserCompat.MediaItem>> result) {
        final AtomicBoolean completed = new AtomicBoolean(false);
        final MobileRequest[] requestHolder = { MobileRequest.NONE };

        MobileDiagnostics.debug("P13-AA-Browse", "load page=" + pageId);

        try {
            MobileRequest request = browseRepository.loadBrowse(pageId,
                    new MobileResultCallback<MobileBrowsePayload>() {
                        @Override
                        public void onSuccess(MobileBrowsePayload payload) {
                            mainHandler.post(() -> completeBrowseRequest(
                                    pageId, payload, result, completed, requestHolder));
                        }

                        @Override
                        public void onError(MobileError error) {
                            MobileDiagnostics.warn("P13-AA-Browse",
                                    "load failed page=" + pageId + " error=" + error);
                            mainHandler.post(() -> completeBrowseRequest(
                                    pageId, null, result, completed, requestHolder));
                        }
                    });
            requestHolder[0] = request == null ? MobileRequest.NONE : request;
            activeBrowseRequests.add(requestHolder[0]);
        } catch (Throwable error) {
            MobileDiagnostics.error("P13-AA-Browse",
                    "load threw page=" + pageId, error);
            mainHandler.post(() -> completeBrowseRequest(
                    pageId, null, result, completed, requestHolder));
        }
    }

    private void loadItem(String itemId,
                          Result<List<MediaBrowserCompat.MediaItem>> result) {
        final AtomicBoolean completed = new AtomicBoolean(false);
        final MobileRequest[] requestHolder = { MobileRequest.NONE };
        final String sourceId = ITEM_PREFIX + itemId;

        MobileDiagnostics.debug("P13-AA-Playlist", "open playlist=" + itemId);
        try {
            MobileRequest request = browseRepository.loadItem(itemId,
                    new MobileResultCallback<MobileBrowsePayload>() {
                        @Override public void onSuccess(MobileBrowsePayload payload) {
                            mainHandler.post(() -> completeBrowseRequest(
                                    sourceId, payload, result, completed, requestHolder));
                        }

                        @Override public void onError(MobileError error) {
                            MobileDiagnostics.warn("P13-AA-Playlist",
                                    "open failed playlist=" + itemId + " error=" + error);
                            mainHandler.post(() -> completeBrowseRequest(
                                    sourceId, null, result, completed, requestHolder));
                        }
                    });
            requestHolder[0] = request == null ? MobileRequest.NONE : request;
            activeBrowseRequests.add(requestHolder[0]);
        } catch (Throwable error) {
            MobileDiagnostics.error("P13-AA-Playlist", "open threw playlist=" + itemId, error);
            mainHandler.post(() -> completeBrowseRequest(
                    sourceId, null, result, completed, requestHolder));
        }
    }

    private void completeBrowseRequest(
            String pageId,
            MobileBrowsePayload payload,
            Result<List<MediaBrowserCompat.MediaItem>> result,
            AtomicBoolean completed,
            MobileRequest[] requestHolder) {
        if (!completed.compareAndSet(false, true)) {
            MobileDiagnostics.debug("P13-AA-Browse",
                    "duplicate result ignored page=" + pageId);
            return;
        }

        MobileRequest request = requestHolder[0];
        if (request != null) {
            request.cancel();
            activeBrowseRequests.remove(request);
        }

        if (destroyed) {
            MobileDiagnostics.debug("P13-AA-Browse",
                    "result ignored after destroy page=" + pageId);
            return;
        }

        List<MediaBrowserCompat.MediaItem> items = Collections.emptyList();
        if (payload != null) {
            try {
                items = convertPayload(pageId, payload);
            } catch (Throwable error) {
                MobileDiagnostics.error("P13-AA-Browse",
                        "payload conversion failed page=" + pageId, error);
            }
        }

        try {
            result.sendResult(items);
            MobileDiagnostics.info("P13-AA-Browse",
                    "sent page=" + pageId + " items=" + items.size());
        } catch (Throwable error) {
            // Result is a one-shot completion object. Never retry sendResult/sendError here.
            MobileDiagnostics.error("P13-AA-Browse",
                    "result delivery failed page=" + pageId, error);
        }
    }

    private List<MediaBrowserCompat.MediaItem> convertPayload(
            String pageId, MobileBrowsePayload payload) {
        if (payload == null || payload.getSections() == null) {
            return Collections.emptyList();
        }
        if ("playlists".equals(pageId)) {
            return convertPlaylistPayload(payload);
        }

        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        int sectionIndex = 0;

        for (MobileSection section : payload.getSections()) {
            if (section == null || section.getItems() == null || section.getItems().isEmpty()) {
                continue;
            }

            String sectionId = SECTION_PREFIX + pageId + ":" + sectionIndex++;
            List<String> playableIds = new ArrayList<>();
            int itemIndex = 0;

            for (MobileMediaItem item : section.getItems()) {
                if (item == null || !item.isPlayable() || item.getId() == null
                        || item.getId().trim().isEmpty()) {
                    continue;
                }

                // Browser IDs must identify both the song and its owning queue. The same
                // YouTube video can occur in history, mixes and several playlists.
                String browserId = MEDIA_PREFIX + sectionId + ":" + itemIndex++
                        + ":" + item.getId();
                mediaByBrowserId.put(browserId, item);
                playableIds.add(browserId);
            }

            if (playableIds.isEmpty()) {
                continue;
            }

            queueByContainer.put(sectionId, playableIds);
            sourceByContainer.put(sectionId, pageId);
            String title = section.getTitle();
            if (title == null || title.trim().isEmpty()) {
                title = payload.getTitle();
            }
            result.add(browsable(sectionId, title, playableIds.size() + " pozycji"));
        }

        // Grid pages contain one logical section. Playlist names must remain visible even
        // when the account currently exposes only one playlist.
        if (result.size() == 1 && !"playlists".equals(pageId)) {
            String onlySection = result.get(0).getDescription().getMediaId();
            List<String> ids = queueByContainer.get(onlySection);
            return ids == null ? result : mediaItems(ids);
        }

        return result;
    }

    private void applyBackgroundItemUpdate(String itemId, MobileBrowsePayload payload) {
        if (destroyed || itemId == null || payload == null) {
            return;
        }

        String sourceId = ITEM_PREFIX + itemId;
        String currentBrowserId = activeQueueIndex >= 0
                && activeQueue != null && activeQueueIndex < activeQueue.size()
                ? activeQueue.get(activeQueueIndex) : null;
        try {
            convertPayload(sourceId, payload);
        } catch (Throwable error) {
            MobileDiagnostics.error("P13-AA-Playlist",
                    "background payload conversion failed source=" + sourceId, error);
            return;
        }

        if (activeContainerId != null && currentBrowserId != null) {
            List<String> completedQueue = queueByContainer.get(activeContainerId);
            int completedIndex = completedQueue == null
                    ? -1 : completedQueue.indexOf(currentBrowserId);
            if (completedIndex >= 0) {
                activeQueue = new ArrayList<>(completedQueue);
                activeQueueIndex = completedIndex;
                publishQueue(activeQueue);
                updatePlaybackState(lastPlaybackState,
                        lastPlaybackPositionMs, lastPlaybackSpeed);
                MobileDiagnostics.info("P13-AA-Queue",
                        "background queue expanded source=" + sourceId
                                + " size=" + activeQueue.size()
                                + " current=" + activeQueueIndex);
            }
        }

        notifyChildrenChanged(sourceId);
        MobileDiagnostics.info("P13-AA-Playlist",
                "background playlist ready source=" + sourceId);
    }

    private List<MediaBrowserCompat.MediaItem> convertPlaylistPayload(MobileBrowsePayload payload) {
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (MobileSection section : payload.getSections()) {
            if (section == null || section.getItems() == null) continue;
            for (MobileMediaItem item : section.getItems()) {
                if (item == null) continue;
                MobileDiagnostics.debug("P13-AA-Playlist", "card title=" + item.getTitle()
                        + " kind=" + item.getKind() + " id=" + item.getId()
                        + " playlistId=" + item.getPlaylistId());
                if (item.getKind() != MobileMediaItem.Kind.PLAYLIST) continue;
                String playlistId = item.getPlaylistId() == null
                        || item.getPlaylistId().trim().isEmpty()
                        ? item.getId() : "playlist:" + item.getPlaylistId();
                if (playlistId == null || playlistId.trim().isEmpty()) continue;
                if (seen.contains(playlistId)) continue;
                seen.add(playlistId);
                String subtitle = item.getSubtitle();
                if (subtitle == null || subtitle.trim().isEmpty()) subtitle = "Otwórz playlistę";
                result.add(browsable(ITEM_PREFIX + playlistId, item.getTitle(), subtitle));
            }
        }
        MobileDiagnostics.info("P13-AA-Playlist", "playlist cards=" + result.size());
        return result;
    }

    private List<MediaBrowserCompat.MediaItem> mediaItems(List<String> browserIds) {
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        for (String browserId : browserIds) {
            MobileMediaItem item = mediaByBrowserId.get(browserId);
            if (item != null) {
                result.add(playable(browserId, item));
            }
        }
        return result;
    }

    private MediaBrowserCompat.MediaItem browsable(
            String id, String title, String subtitle) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build();
        return new MediaBrowserCompat.MediaItem(
                description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    private MediaBrowserCompat.MediaItem playableAction(
            String id, String title, String subtitle) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build();
        return new MediaBrowserCompat.MediaItem(
                description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }

    private MediaBrowserCompat.MediaItem playable(
            String browserId, MobileMediaItem item) {
        MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder()
                .setMediaId(browserId)
                .setTitle(item.getTitle())
                .setSubtitle(item.getSubtitle());

        if (item.getThumbnailUrl() != null && !item.getThumbnailUrl().trim().isEmpty()) {
            builder.setIconUri(Uri.parse(item.getThumbnailUrl()));
        }

        return new MediaBrowserCompat.MediaItem(
                builder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
    }

    private void playBrowserMediaId(String browserId) {
        playBrowserMediaId(browserId, false, false);
    }

    private void playBrowserMediaId(String browserId, boolean fromAutoAdvance) {
        playBrowserMediaId(browserId, fromAutoAdvance, false);
    }

    private void playResumeBrowserId(String browserId) {
        playBrowserMediaId(browserId, false, true);
    }

    private void playBrowserMediaId(
            String browserId, boolean fromAutoAdvance, boolean fromResume) {
        MobileMediaItem item = mediaByBrowserId.get(browserId);
        if (item == null) {
            MobileDiagnostics.warn("P13-AA-Playback", "missing item browserId=" + browserId);
            return;
        }

        if (!fromAutoAdvance && !fromResume) {
            // A manual selection must always play exactly the item the user clicked.
            resumeAutoplayConsumed = true;
            autoResumeScheduled = false;
        }

        activeContainerId = findContainer(browserId);
        activeQueue = activeContainerId == null
                ? Collections.singletonList(browserId)
                : new ArrayList<>(queueByContainer.get(activeContainerId));
        activeQueueIndex = activeQueue.indexOf(browserId);
        activeItem = item;
        activeLiked = resolveKnownLike(item.getId());
        lastRecoveryMediaId = item.getId();
        recoveryAttemptsForMediaId = 0;
        if (!fromAutoAdvance) {
            lastAutoAdvancedFromBrowserId = null;
        }

        publishQueue(activeQueue);

        forceZeroUntilMs = System.currentTimeMillis() + FORCE_ZERO_GUARD_MS;
        MobileDiagnostics.info("P13-AA-Playback",
                "selected browserId=" + browserId + " mediaId=" + item.getId()
                        + " queueIndex=" + activeQueueIndex
                        + " queueSize=" + activeQueue.size()
                        + " auto=" + fromAutoAdvance
                        + " resume=" + fromResume
                        + " start=0 forceZero=true");
        updateMetadata(item, 0L);
        updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING, 0L, 0f);
        savePlaybackSelection(browserId, item, 0L);
        playbackRepository.prepare(item.getId(), 0L);
        playbackRepository.seekTo(0L);
        playbackRepository.play();
    }

    private void publishQueue(List<String> browserIds) {
        String key = (browserIds == null ? "" : browserIds.toString())
                + "|current=" + activeQueueIndex;
        if (key.equals(lastPublishedQueueKey)) {
            return;
        }
        lastPublishedQueueKey = key;
        mediaSession.setQueue(buildSessionQueue(browserIds));
        mediaSession.setQueueTitle("SmartTube Music");
        MobileDiagnostics.info("P13-AA-Queue",
                "published queue size=" + (browserIds == null ? 0 : browserIds.size())
                        + " current=" + activeQueueIndex + " presentedFirst=true");
    }

    private String maybeRedirectToLastPlaylistItem(String requestedBrowserId) {
        if (resumePrefs == null || requestedBrowserId == null) {
            return null;
        }

        String containerId = findContainer(requestedBrowserId);
        if (containerId == null) {
            return null;
        }
        List<String> queue = queueByContainer.get(containerId);
        if (queue == null || queue.isEmpty() || !requestedBrowserId.equals(queue.get(0))) {
            return null;
        }

        String savedContainer = resumePrefs.getString(PREF_CONTAINER_ID, null);
        if (!containerId.equals(savedContainer)) {
            return null;
        }

        String savedBrowserId = resumePrefs.getString(PREF_BROWSER_ID, null);
        if (savedBrowserId != null && queue.contains(savedBrowserId)) {
            return savedBrowserId;
        }

        String savedVideoId = resumePrefs.getString(PREF_VIDEO_ID, null);
        if (savedVideoId == null || savedVideoId.trim().isEmpty()) {
            return null;
        }
        for (String browserId : queue) {
            MobileMediaItem item = mediaByBrowserId.get(browserId);
            if (item != null && savedVideoId.equals(item.getId())) {
                return browserId;
            }
        }
        return null;
    }

    private boolean playOrResume() {
        if (activeItem != null) {
            playbackRepository.play();
            return true;
        }
        return requestResumePlayback("play-command");
    }

    private void scheduleAutoResumeAfterOpen() {
        if (autoResumeScheduled || resumeAutoplayConsumed || activeItem != null
                || resumePrefs == null) {
            return;
        }
        String savedVideoId = resumePrefs.getString(PREF_VIDEO_ID, null);
        if (savedVideoId == null || savedVideoId.trim().isEmpty()) {
            return;
        }

        autoResumeScheduled = true;
        MobileDiagnostics.info("P13-AA-Resume",
                "auto-resume scheduled videoId=" + savedVideoId);
        mainHandler.postDelayed(() -> {
            autoResumeScheduled = false;
            if (destroyed || resumeAutoplayConsumed || activeItem != null) {
                return;
            }
            requestResumePlayback("auto-open");
        }, AUTO_RESUME_DELAY_MS);
    }

    private boolean requestResumePlayback(String reason) {
        if (destroyed || playbackRepository == null || resumePrefs == null) {
            return false;
        }
        if (activeItem != null) {
            playbackRepository.play();
            return true;
        }

        String savedBrowserId = resumePrefs.getString(PREF_BROWSER_ID, null);
        String savedVideoId = resumePrefs.getString(PREF_VIDEO_ID, null);
        String loadedBrowserId = findLoadedResumeBrowserId(savedBrowserId, savedVideoId);
        if (loadedBrowserId != null) {
            resumeAutoplayConsumed = true;
            MobileDiagnostics.info("P13-AA-Resume",
                    "resume loaded reason=" + reason + " browserId=" + loadedBrowserId
                            + " fromZero=true");
            playResumeBrowserId(loadedBrowserId);
            return true;
        }

        if (resumeLoadInProgress) {
            MobileDiagnostics.info("P13-AA-Resume",
                    "resume load already in progress reason=" + reason);
            return true;
        }

        if (savedVideoId == null || savedVideoId.trim().isEmpty()) {
            MobileDiagnostics.info("P13-AA-Resume",
                    "resume ignored: no saved video reason=" + reason);
            return false;
        }

        resumeLoadInProgress = true;
        String savedSourceId = resumePrefs.getString(PREF_SOURCE_ID, null);
        String sourceId = savedSourceId == null || savedSourceId.trim().isEmpty()
                ? "history" : savedSourceId;
        MobileDiagnostics.info("P13-AA-Resume",
                "resume loading source=" + sourceId + " reason=" + reason + " videoId=" + savedVideoId);
        loadResumePageAndPlay(sourceId, reason, true);
        return true;
    }

    private String findLoadedResumeBrowserId(String savedBrowserId, String savedVideoId) {
        if (savedBrowserId != null && mediaByBrowserId.containsKey(savedBrowserId)) {
            return savedBrowserId;
        }
        if (savedVideoId == null) {
            return null;
        }
        for (Map.Entry<String, MobileMediaItem> entry : mediaByBrowserId.entrySet()) {
            MobileMediaItem item = entry.getValue();
            if (item != null && savedVideoId.equals(item.getId())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void loadResumePageAndPlay(
            String pageId, String reason, boolean tryMusicFallback) {
        if (pageId.startsWith(ITEM_PREFIX)) {
            loadResumeItemAndPlay(pageId, reason, tryMusicFallback);
            return;
        }
        final MobileRequest[] requestHolder = { MobileRequest.NONE };
        try {
            MobileRequest request = browseRepository.loadBrowse(pageId,
                    new MobileResultCallback<MobileBrowsePayload>() {
                        @Override
                        public void onSuccess(MobileBrowsePayload payload) {
                            mainHandler.post(() -> completeResumePageLoad(
                                    pageId, reason, tryMusicFallback,
                                    payload, requestHolder));
                        }

                        @Override
                        public void onError(MobileError error) {
                            MobileDiagnostics.warn("P13-AA-Resume",
                                    "resume page failed page=" + pageId + " error=" + error);
                            mainHandler.post(() -> completeResumePageLoad(
                                    pageId, reason, tryMusicFallback,
                                    null, requestHolder));
                        }
                    });
            requestHolder[0] = request == null ? MobileRequest.NONE : request;
            activeBrowseRequests.add(requestHolder[0]);
        } catch (Throwable error) {
            MobileDiagnostics.error("P13-AA-Resume",
                    "resume page threw page=" + pageId, error);
            mainHandler.post(() -> completeResumePageLoad(
                    pageId, reason, tryMusicFallback, null, requestHolder));
        }
    }

    private void loadResumeItemAndPlay(
            String sourceId, String reason, boolean tryMusicFallback) {
        final MobileRequest[] requestHolder = { MobileRequest.NONE };
        String itemId = sourceId.substring(ITEM_PREFIX.length());
        try {
            MobileRequest request = browseRepository.loadItem(itemId,
                    new MobileResultCallback<MobileBrowsePayload>() {
                        @Override public void onSuccess(MobileBrowsePayload payload) {
                            mainHandler.post(() -> completeResumePageLoad(sourceId, reason,
                                    tryMusicFallback, payload, requestHolder));
                        }

                        @Override public void onError(MobileError error) {
                            MobileDiagnostics.warn("P13-AA-Resume",
                                    "resume playlist failed source=" + sourceId + " error=" + error);
                            mainHandler.post(() -> completeResumePageLoad(sourceId, reason,
                                    tryMusicFallback, null, requestHolder));
                        }
                    });
            requestHolder[0] = request == null ? MobileRequest.NONE : request;
            activeBrowseRequests.add(requestHolder[0]);
        } catch (Throwable error) {
            MobileDiagnostics.error("P13-AA-Resume", "resume playlist threw source=" + sourceId, error);
            mainHandler.post(() -> completeResumePageLoad(sourceId, reason,
                    tryMusicFallback, null, requestHolder));
        }
    }

    private void completeResumePageLoad(
            String pageId,
            String reason,
            boolean tryMusicFallback,
            MobileBrowsePayload payload,
            MobileRequest[] requestHolder) {
        MobileRequest request = requestHolder[0];
        if (request != null) {
            request.cancel();
            activeBrowseRequests.remove(request);
        }

        if (destroyed || resumeAutoplayConsumed || activeItem != null) {
            resumeLoadInProgress = false;
            return;
        }

        if (payload != null) {
            try {
                convertPayload(pageId, payload);
            } catch (Throwable error) {
                MobileDiagnostics.error("P13-AA-Resume",
                        "resume payload conversion failed page=" + pageId, error);
            }
        }

        String savedBrowserId = resumePrefs.getString(PREF_BROWSER_ID, null);
        String savedVideoId = resumePrefs.getString(PREF_VIDEO_ID, null);
        String loadedBrowserId = findLoadedResumeBrowserId(savedBrowserId, savedVideoId);
        if (loadedBrowserId != null) {
            resumeLoadInProgress = false;
            resumeAutoplayConsumed = true;
            MobileDiagnostics.info("P13-AA-Resume",
                    "resume page matched page=" + pageId + " reason=" + reason
                            + " browserId=" + loadedBrowserId + " fromZero=true");
            playResumeBrowserId(loadedBrowserId);
            return;
        }

        if (tryMusicFallback) {
            MobileDiagnostics.info("P13-AA-Resume",
                    "resume not in history; trying music videoId=" + savedVideoId);
            loadResumePageAndPlay("music", reason, false);
            return;
        }

        resumeLoadInProgress = false;
        MobileDiagnostics.info("P13-AA-Resume",
                "resume item not found reason=" + reason + " videoId=" + savedVideoId);
    }

    private void savePlaybackSelection(String browserId, MobileMediaItem item, long positionMs) {
        if (resumePrefs == null || item == null || browserId == null) {
            return;
        }
        resumePrefs.edit()
                .putString(PREF_BROWSER_ID, browserId)
                .putString(PREF_CONTAINER_ID, activeContainerId)
                .putString(PREF_VIDEO_ID, item.getId())
                .putString(PREF_TITLE, item.getTitle())
                .putString(PREF_SUBTITLE, item.getSubtitle() == null ? "" : item.getSubtitle().toString())
                .putString(PREF_THUMB, item.getThumbnailUrl())
                .putLong(PREF_DURATION, Math.max(0L, item.getDurationMs()))
                .putLong(PREF_POSITION, Math.max(0L, positionMs))
                .putInt(PREF_QUEUE_INDEX, activeQueueIndex)
                .putString(PREF_SOURCE_ID, sourceByContainer.get(activeContainerId))
                .apply();
        lastResumeSavePositionMs = Math.max(0L, positionMs);
        MobileDiagnostics.info("P13-AA-Resume",
                "saved selection browserId=" + browserId + " index=" + activeQueueIndex
                        + " position=" + Math.max(0L, positionMs));
    }

    private void savePlaybackProgress(long positionMs) {
        if (resumePrefs == null || activeItem == null || activeQueueIndex < 0) {
            return;
        }
        long safePosition = Math.max(0L, positionMs);
        if (lastResumeSavePositionMs >= 0L
                && Math.abs(safePosition - lastResumeSavePositionMs) < RESUME_SAVE_INTERVAL_MS
                && lastPlaybackState != PlaybackStateCompat.STATE_PAUSED) {
            return;
        }
        resumePrefs.edit()
                .putLong(PREF_POSITION, safePosition)
                .putLong(PREF_DURATION, Math.max(lastMetadataDurationMs, activeItem.getDurationMs()))
                .putInt(PREF_QUEUE_INDEX, activeQueueIndex)
                .apply();
        lastResumeSavePositionMs = safePosition;
    }

    private List<MediaSessionCompat.QueueItem> buildSessionQueue(List<String> browserIds) {
        if (browserIds == null || browserIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<MediaSessionCompat.QueueItem> result = new ArrayList<>();
        int startIndex = activeQueueIndex >= 0 && activeQueueIndex < browserIds.size()
                ? activeQueueIndex : 0;
        for (int offset = 0; offset < browserIds.size(); offset++) {
            // Android Auto doesn't consistently scroll its queue UI to activeQueueItemId.
            // Rotate presentation so the active item is first, followed by what plays next.
            int i = (startIndex + offset) % browserIds.size();
            String browserId = browserIds.get(i);
            MobileMediaItem item = mediaByBrowserId.get(browserId);
            if (item == null) {
                continue;
            }
            MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder()
                    .setMediaId(browserId)
                    .setTitle(item.getTitle())
                    .setSubtitle(item.getSubtitle());
            if (item.getThumbnailUrl() != null && !item.getThumbnailUrl().trim().isEmpty()) {
                builder.setIconUri(Uri.parse(item.getThumbnailUrl()));
            }
            result.add(new MediaSessionCompat.QueueItem(builder.build(), i));
        }
        return result;
    }

    private String findContainer(String browserId) {
        for (Map.Entry<String, List<String>> entry : queueByContainer.entrySet()) {
            if (entry.getValue().contains(browserId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void skipBy(int delta) {
        if (activeQueue == null || activeQueue.isEmpty()) {
            return;
        }

        int target = resolveManualTargetIndex(delta);
        if (target < 0 || target >= activeQueue.size()) {
            MobileDiagnostics.info("P13-AA-Queue",
                    "skip ignored delta=" + delta + " target=" + target
                            + " index=" + activeQueueIndex + " queueSize=" + activeQueue.size()
                            + " repeat=" + repeatMode + " shuffle=" + shuffleMode);
            return;
        }

        MobileDiagnostics.info("P13-AA-Queue",
                "skip delta=" + delta + " from=" + activeQueueIndex + " to=" + target
                        + " repeat=" + repeatMode + " shuffle=" + shuffleMode);
        playQueueIndex(target, false);
    }

    private void playQueueIndex(int index, boolean fromAutoAdvance) {
        if (activeQueue == null || index < 0 || index >= activeQueue.size()) {
            MobileDiagnostics.warn("P13-AA-Queue",
                    "playQueueIndex ignored index=" + index + " queueSize="
                            + (activeQueue == null ? 0 : activeQueue.size()));
            return;
        }
        playBrowserMediaId(activeQueue.get(index), fromAutoAdvance);
    }

    private int resolveManualTargetIndex(int delta) {
        if (activeQueue == null || activeQueue.isEmpty()) {
            return -1;
        }
        if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL && delta > 0) {
            return randomQueueIndex(true);
        }

        int target = activeQueueIndex + delta;
        if (target >= 0 && target < activeQueue.size()) {
            return target;
        }
        if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) {
            if (target < 0) {
                return activeQueue.size() - 1;
            }
            if (target >= activeQueue.size()) {
                return 0;
            }
        }
        return -1;
    }

    private int randomQueueIndex(boolean avoidCurrent) {
        if (activeQueue == null || activeQueue.isEmpty()) {
            return -1;
        }
        if (activeQueue.size() == 1) {
            return 0;
        }
        int candidate = activeQueueIndex;
        for (int i = 0; i < 8 && avoidCurrent && candidate == activeQueueIndex; i++) {
            candidate = shuffleRandom.nextInt(activeQueue.size());
        }
        if (candidate == activeQueueIndex) {
            candidate = (activeQueueIndex + 1) % activeQueue.size();
        }
        return candidate;
    }

    private void applySnapshot(MobilePlaybackSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        if (activeItem != null) {
            updateMetadata(activeItem, snapshot.getDurationMs());
            if (maybeForceStartFromZero(snapshot)) {
                return;
            }
            savePlaybackProgress(snapshot.getPositionMs());
        }

        if (maybeAutoAdvance(snapshot)) {
            return;
        }

        int state;
        if (snapshot.isPlaying()) {
            state = PlaybackStateCompat.STATE_PLAYING;
        } else if (snapshot.isBuffering() || (activeItem != null && !snapshot.isPrepared())) {
            state = PlaybackStateCompat.STATE_BUFFERING;
        } else if (snapshot.isPrepared()) {
            state = PlaybackStateCompat.STATE_PAUSED;
        } else {
            state = PlaybackStateCompat.STATE_NONE;
        }

        updatePlaybackState(state, snapshot.getPositionMs(), snapshot.isPlaying() ? 1f : 0f);
    }

    private boolean maybeForceStartFromZero(MobilePlaybackSnapshot snapshot) {
        if (snapshot == null || activeItem == null || forceZeroUntilMs <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() > forceZeroUntilMs) {
            forceZeroUntilMs = 0L;
            return false;
        }
        if (!snapshot.isPrepared()) {
            return false;
        }
        long positionMs = snapshot.getPositionMs();
        if (positionMs >= FORCE_ZERO_THRESHOLD_MS) {
            forceZeroUntilMs = 0L;
            MobileDiagnostics.warn("P13-AA-Resume",
                    "force zero once mediaId=" + activeItem.getId()
                            + " observedPosition=" + positionMs);
            playbackRepository.seekTo(0L);
            updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING, 0L, 0f);
            return true;
        }
        return false;
    }

    private boolean maybeAutoAdvance(MobilePlaybackSnapshot snapshot) {
        if (snapshot == null || activeQueue == null || activeQueue.isEmpty()
                || activeQueueIndex < 0 || activeQueueIndex >= activeQueue.size()) {
            return false;
        }
        if (snapshot.isBuffering() || !snapshot.isPrepared()) {
            return false;
        }

        long durationMs = snapshot.getDurationMs();
        long positionMs = snapshot.getPositionMs();
        boolean ended = snapshot.isEnded();
        boolean nearEndWhilePlaying = snapshot.isPlaying()
                && durationMs >= AUTO_ADVANCE_MIN_DURATION_MS
                && positionMs >= Math.max(0L, durationMs - AUTO_ADVANCE_EARLY_MS);
        if (!ended && !nearEndWhilePlaying) {
            return false;
        }

        String currentBrowserId = activeQueue.get(activeQueueIndex);
        if (currentBrowserId != null && currentBrowserId.equals(lastAutoAdvancedFromBrowserId)) {
            return true;
        }
        lastAutoAdvancedFromBrowserId = currentBrowserId;

        if (!autoNextEnabled) {
            MobileDiagnostics.info("P13-AA-Queue",
                    "auto-next disabled at index=" + activeQueueIndex
                            + " duration=" + durationMs + " position=" + positionMs);
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED,
                    durationMs > 0L ? durationMs : positionMs, 0f);
            return true;
        }

        int nextIndex = resolveAutoNextIndex();
        if (nextIndex < 0 || nextIndex >= activeQueue.size()) {
            MobileDiagnostics.info("P13-AA-Queue",
                    "auto-next reached end of queue at index=" + activeQueueIndex
                            + " repeat=" + repeatMode + " shuffle=" + shuffleMode
                            + " duration=" + durationMs + " position=" + positionMs);
            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED,
                    durationMs > 0L ? durationMs : positionMs, 0f);
            return true;
        }

        MobileDiagnostics.info("P13-AA-Queue",
                "auto-next from=" + activeQueueIndex + " to=" + nextIndex
                        + " ended=" + ended + " position=" + positionMs
                        + " duration=" + durationMs
                        + " repeat=" + repeatMode + " shuffle=" + shuffleMode);
        playQueueIndex(nextIndex, true);
        return true;
    }

    private int resolveAutoNextIndex() {
        if (activeQueue == null || activeQueue.isEmpty()) {
            return -1;
        }
        if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) {
            return activeQueueIndex;
        }
        if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL) {
            return randomQueueIndex(true);
        }

        int nextIndex = activeQueueIndex + 1;
        if (nextIndex < activeQueue.size()) {
            return nextIndex;
        }
        if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) {
            return 0;
        }
        return -1;
    }

    private void updateMetadata(MobileMediaItem item, long durationMs) {
        long resolvedDuration = durationMs > 0L ? durationMs : item.getDurationMs();
        lastMetadataDurationMs = resolvedDuration;

        String metadataKey = item.getId() + "|" + item.getTitle() + "|"
                + item.getSubtitle() + "|" + item.getThumbnailUrl() + "|"
                + resolvedDuration + "|liked=" + activeLiked;
        if (metadataKey.equals(lastPublishedMetadataKey)) {
            return;
        }
        lastPublishedMetadataKey = metadataKey;

        MediaMetadataCompat.Builder metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, item.getId())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.getSubtitle())
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, item.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                        item.getSubtitle() == null ? "" : item.getSubtitle().toString())
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, item.getThumbnailUrl())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, item.getThumbnailUrl())
                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, item.getThumbnailUrl())
                .putRating(MediaMetadataCompat.METADATA_KEY_USER_RATING,
                        activeLiked
                                ? RatingCompat.newThumbRating(true)
                                : RatingCompat.newUnratedRating(RatingCompat.RATING_THUMB_UP_DOWN));

        if (resolvedDuration > 0L) {
            metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, resolvedDuration);
        }

        mediaSession.setMetadata(metadata.build());
        updatePhoneNotification();
    }

    private void updatePlaybackState(int state, long positionMs, float speed) {
        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_SEEK_TO
                | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
                | PlaybackStateCompat.ACTION_SET_RATING
                | PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;

        lastPlaybackState = state;
        lastPlaybackPositionMs = Math.max(0L, positionMs);
        lastPlaybackSpeed = speed;

        long activeQueueItemId = activeQueueIndex >= 0
                && activeQueue != null && activeQueueIndex < activeQueue.size()
                ? activeQueueIndex : MediaSessionCompat.QueueItem.UNKNOWN_ID;
        String controlsKey = "liked=" + activeLiked
                + "|repeat=" + repeatMode
                + "|shuffle=" + shuffleMode
                + "|autoNext=" + autoNextEnabled;
        long now = System.currentTimeMillis();
        boolean publish = lastPublishedPlaybackAtMs == 0L
                || state != lastPublishedPlaybackState
                || Float.compare(speed, lastPublishedPlaybackSpeed) != 0
                || activeQueueItemId != lastPublishedActiveQueueItemId
                || !controlsKey.equals(lastPublishedPlaybackControlsKey);

        if (!publish) {
            long expectedPosition = lastPublishedPlaybackPositionMs;
            if (lastPublishedPlaybackState == PlaybackStateCompat.STATE_PLAYING
                    && lastPublishedPlaybackSpeed > 0f) {
                expectedPosition += (long) ((now - lastPublishedPlaybackAtMs)
                        * lastPublishedPlaybackSpeed);
            }
            // Media controllers extrapolate a playing position from the last state. Avoid
            // replacing PlaybackState on every snapshot because Android Auto then re-anchors
            // its queue to the active row. A real seek/drift still gets published immediately.
            long allowedDriftMs = state == PlaybackStateCompat.STATE_PLAYING ? 2500L : 500L;
            publish = Math.abs(lastPlaybackPositionMs - expectedPosition) > allowedDriftMs;
        }

        if (!publish) {
            if (state == PlaybackStateCompat.STATE_PAUSED
                    || state == PlaybackStateCompat.STATE_PLAYING) {
                savePlaybackProgress(lastPlaybackPositionMs);
            }
            updatePhoneNotification();
            return;
        }

        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, lastPlaybackPositionMs, speed)
                .addCustomAction(createLikeAction())
                .addCustomAction(createRestartAction())
                .addCustomAction(createShuffleAction())
                .addCustomAction(createRepeatAction())
                .addCustomAction(createAutoNextAction());

        if (activeQueueItemId != MediaSessionCompat.QueueItem.UNKNOWN_ID) {
            // Restores Android Auto's animated/current-track indicator. Position-only
            // snapshots are filtered above, so the marker no longer forces a scroll reset.
            builder.setActiveQueueItemId(activeQueueItemId);
        }

        mediaSession.setPlaybackState(builder.build());
        lastPublishedPlaybackState = state;
        lastPublishedPlaybackPositionMs = lastPlaybackPositionMs;
        lastPublishedPlaybackSpeed = speed;
        lastPublishedPlaybackAtMs = now;
        lastPublishedActiveQueueItemId = activeQueueItemId;
        lastPublishedPlaybackControlsKey = controlsKey;
        if (state == PlaybackStateCompat.STATE_PAUSED || state == PlaybackStateCompat.STATE_PLAYING) {
            savePlaybackProgress(lastPlaybackPositionMs);
        }
        updatePhoneNotification();
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "SmartTube Music",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Sterowanie SmartTube Music w Android Auto");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private void updatePhoneNotification() {
        if (mediaSession == null || activeItem == null) {
            return;
        }

        String key = activeItem.getId() + "|" + lastPlaybackState + "|"
                + activeLiked + "|" + activeQueueIndex;
        if (key.equals(lastPhoneNotificationKey)) {
            return;
        }

        try {
            Notification notification = buildPhoneNotification();
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, notification);
                lastPhoneNotificationKey = key;
                MobileDiagnostics.info("P13-AA-Phone",
                        "media notification posted state=" + lastPlaybackState
                                + " mediaId=" + activeItem.getId());
            }
        } catch (Throwable error) {
            MobileDiagnostics.warn("P13-AA-Phone",
                    "notification update failed: " + error.getClass().getSimpleName()
                            + ": " + error.getMessage());
        }
    }

    private Notification buildPhoneNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                : new Notification.Builder(this);

        boolean playing = lastPlaybackState == PlaybackStateCompat.STATE_PLAYING
                || lastPlaybackState == PlaybackStateCompat.STATE_BUFFERING;
        Intent playerIntent = new Intent(this, SmartTubeAutoMusicPlayerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 40, playerIntent, pendingIntentFlags());
        mediaSession.setSessionActivity(contentIntent);

        builder.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(activeItem.getTitle())
                .setContentText(activeItem.getSubtitle())
                .setSubText("SmartTube Music")
                .setShowWhen(false)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_TRANSPORT);

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent);
        }

        builder.addAction(android.R.drawable.ic_media_previous,
                "Poprzedni", notificationIntent(NOTIFICATION_ACTION_PREVIOUS, 41));
        builder.addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "Pauza" : "Play",
                notificationIntent(playing ? NOTIFICATION_ACTION_PAUSE : NOTIFICATION_ACTION_PLAY, 42));
        builder.addAction(android.R.drawable.ic_media_next,
                "Następny", notificationIntent(NOTIFICATION_ACTION_NEXT, 43));
        builder.addAction(android.R.drawable.ic_menu_revert,
                "Od początku", notificationIntent(NOTIFICATION_ACTION_RESTART, 44));
        builder.addAction(activeLiked
                        ? android.R.drawable.btn_star_big_on
                        : android.R.drawable.btn_star_big_off,
                activeLiked ? "Usuń polubienie" : "Lubię to",
                notificationIntent(NOTIFICATION_ACTION_LIKE, 45));

        if (Build.VERSION.SDK_INT >= 21) {
            Notification.MediaStyle style = new Notification.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2);
            Object token = mediaSession.getSessionToken().getToken();
            if (token instanceof android.media.session.MediaSession.Token) {
                style.setMediaSession((android.media.session.MediaSession.Token) token);
            }
            builder.setStyle(style);
        }

        return builder.build();
    }

    private PendingIntent notificationIntent(String action, int requestCode) {
        Intent intent = new Intent(this, SmartTubeAutoMusicService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, requestCode, intent, pendingIntentFlags());
    }

    private int pendingIntentFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private void cancelPhoneNotification() {
        try {
            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.cancel(NOTIFICATION_ID);
            }
        } catch (Throwable error) {
            MobileDiagnostics.warn("P13-AA-Phone",
                    "notification cancel failed: " + error.getMessage());
        }
    }

    private PlaybackStateCompat.CustomAction createShuffleAction() {
        boolean enabled = shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL;
        return new PlaybackStateCompat.CustomAction.Builder(
                ACTION_TOGGLE_SHUFFLE,
                enabled ? "Losowo: włączone" : "Losowo: wyłączone",
                enabled ? android.R.drawable.ic_menu_sort_by_size : android.R.drawable.ic_menu_sort_by_size)
                .build();
    }

    private PlaybackStateCompat.CustomAction createRepeatAction() {
        String label;
        int icon;
        if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) {
            label = "Powtarzanie: jeden";
            icon = android.R.drawable.ic_menu_revert;
        } else if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) {
            label = "Powtarzanie: wszystko";
            icon = android.R.drawable.ic_menu_rotate;
        } else {
            label = "Powtarzanie: wyłączone";
            icon = android.R.drawable.ic_menu_close_clear_cancel;
        }
        return new PlaybackStateCompat.CustomAction.Builder(
                ACTION_CYCLE_REPEAT, label, icon).build();
    }

    private PlaybackStateCompat.CustomAction createAutoNextAction() {
        return new PlaybackStateCompat.CustomAction.Builder(
                ACTION_TOGGLE_AUTO_NEXT,
                autoNextEnabled ? "Kolejka: następny automatycznie" : "Kolejka: stop po utworze",
                autoNextEnabled ? android.R.drawable.ic_media_next : android.R.drawable.ic_media_pause)
                .build();
    }

    private PlaybackStateCompat.CustomAction createLikeAction() {
        return new PlaybackStateCompat.CustomAction.Builder(
                activeLiked ? ACTION_UNLIKE : ACTION_LIKE,
                activeLiked ? "♥ Polubione — kliknij, aby usunąć" : "♡ Lubię to",
                activeLiked ? R.drawable.ic_auto_like_on : R.drawable.ic_auto_like_off)
                .build();
    }

    private PlaybackStateCompat.CustomAction createRestartAction() {
        return new PlaybackStateCompat.CustomAction.Builder(
                ACTION_RESTART,
                "↺ Od początku",
                android.R.drawable.ic_menu_revert)
                .build();
    }

    private void restartCurrentTrack(String source) {
        if (activeItem == null || playbackRepository == null) {
            MobileDiagnostics.warn("P13-AA-Playback",
                    "restart ignored: no active item source=" + source);
            return;
        }
        forceZeroUntilMs = 0L;
        MobileDiagnostics.info("P13-AA-Playback",
                "restart current fromZero source=" + source
                        + " mediaId=" + activeItem.getId());
        playbackRepository.seekTo(0L);
        updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING, 0L, 0f);
        playbackRepository.play();
    }

    private void toggleShuffle() {
        int target = shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL
                ? PlaybackStateCompat.SHUFFLE_MODE_NONE
                : PlaybackStateCompat.SHUFFLE_MODE_ALL;
        setShuffleModeFromCommand(target, "custom");
    }

    private void setShuffleModeFromCommand(int requestedMode, String source) {
        if (requestedMode == PlaybackStateCompat.SHUFFLE_MODE_ALL) {
            shuffleMode = PlaybackStateCompat.SHUFFLE_MODE_ALL;
        } else {
            shuffleMode = PlaybackStateCompat.SHUFFLE_MODE_NONE;
        }
        mediaSession.setShuffleMode(shuffleMode);
        MobileDiagnostics.info("P13-AA-Queue",
                "shuffle mode=" + shuffleMode + " source=" + source);
        updatePlaybackState(lastPlaybackState, lastPlaybackPositionMs, lastPlaybackSpeed);
    }

    private void cycleRepeatMode() {
        int target;
        if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) {
            target = PlaybackStateCompat.REPEAT_MODE_ONE;
        } else if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) {
            target = PlaybackStateCompat.REPEAT_MODE_NONE;
        } else {
            target = PlaybackStateCompat.REPEAT_MODE_ALL;
        }
        setRepeatModeFromCommand(target, "custom");
    }

    private void setRepeatModeFromCommand(int requestedMode, String source) {
        if (requestedMode == PlaybackStateCompat.REPEAT_MODE_ONE) {
            repeatMode = PlaybackStateCompat.REPEAT_MODE_ONE;
        } else if (requestedMode == PlaybackStateCompat.REPEAT_MODE_ALL) {
            repeatMode = PlaybackStateCompat.REPEAT_MODE_ALL;
        } else {
            repeatMode = PlaybackStateCompat.REPEAT_MODE_NONE;
        }
        mediaSession.setRepeatMode(repeatMode);
        MobileDiagnostics.info("P13-AA-Queue",
                "repeat mode=" + repeatMode + " source=" + source);
        updatePlaybackState(lastPlaybackState, lastPlaybackPositionMs, lastPlaybackSpeed);
    }

    private void toggleAutoNext() {
        autoNextEnabled = !autoNextEnabled;
        MobileDiagnostics.info("P13-AA-Queue", "autoNext=" + autoNextEnabled);
        updatePlaybackState(lastPlaybackState, lastPlaybackPositionMs, lastPlaybackSpeed);
    }

    private boolean resolveKnownLike(String videoId) {
        if (videoId == null || videoId.trim().isEmpty()) {
            return false;
        }
        if (likedCatalogReady) {
            boolean accountState = likedCatalogVideoIds.contains(videoId);
            likeStateByVideoId.put(videoId, accountState);
            return accountState;
        }
        Boolean memory = likeStateByVideoId.get(videoId);
        if (memory != null) {
            return memory;
        }
        boolean stored = resumePrefs != null
                && resumePrefs.getBoolean(PREF_LIKE_PREFIX + videoId, false);
        likeStateByVideoId.put(videoId, stored);
        return stored;
    }

    private void rememberLikeState(String videoId, boolean liked) {
        if (videoId == null || videoId.trim().isEmpty()) {
            return;
        }
        likeStateByVideoId.put(videoId, liked);
        if (likedCatalogReady) {
            if (liked && !likedCatalogVideoIds.contains(videoId)) {
                likedCatalogVideoIds.add(videoId);
            } else if (!liked) {
                likedCatalogVideoIds.remove(videoId);
            }
        }
        if (resumePrefs != null) {
            resumePrefs.edit().putBoolean(PREF_LIKE_PREFIX + videoId, liked).apply();
        }
    }

    private void toggleLike() {
        setLiked(!activeLiked);
    }

    private void setLiked(boolean liked) {
        MobileMediaItem item = activeItem;
        if (item == null || item.getId() == null || item.getId().trim().isEmpty()) {
            MobileDiagnostics.warn("P13-AA-Like", "like ignored: no active item");
            return;
        }

        final String videoId = item.getId();
        final boolean previousLiked = activeLiked;
        activeLiked = liked;
        rememberLikeState(videoId, liked);
        updateMetadata(item, lastMetadataDurationMs);
        updatePlaybackState(lastPlaybackState, lastPlaybackPositionMs, lastPlaybackSpeed);
        MobileDiagnostics.info("P13-AA-Like",
                "optimistic " + (liked ? "liked " : "unliked ") + videoId
                        + " uiState=" + (activeLiked ? "liked" : "unliked")
                        + " title=" + item.getTitle());

        final String title = item.getTitle();
        final boolean targetLiked = liked;
        actionExecutor.execute(() -> {
            try {
                MediaItemService service = YouTubeServiceManager.instance().getMediaItemService();
                MediaItem mediaItem = new AutoMediaItem(videoId, title, item.getSubtitle(),
                        item.getThumbnailUrl(), item.getDurationMs());
                if (targetLiked) {
                    service.setLike(mediaItem);
                } else {
                    service.removeLike(mediaItem);
                }
                MobileDiagnostics.info("P13-AA-Like",
                        (targetLiked ? "confirmed liked " : "confirmed unliked ")
                                + videoId + " title=" + title);
            } catch (Throwable error) {
                MobileDiagnostics.error("P13-AA-Like",
                        "like action failed videoId=" + videoId + " target=" + targetLiked, error);
                mainHandler.post(() -> {
                    if (activeItem != null && videoId.equals(activeItem.getId())) {
                        activeLiked = previousLiked;
                        rememberLikeState(videoId, previousLiked);
                        updateMetadata(activeItem, lastMetadataDurationMs);
                        updatePlaybackState(lastPlaybackState, lastPlaybackPositionMs, lastPlaybackSpeed);
                    }
                });
            }
        });
    }

    private static final class AutoMediaItem implements MediaItem {
        private final String videoId;
        private final String title;
        private final CharSequence subtitle;
        private final String thumbnailUrl;
        private final long durationMs;

        AutoMediaItem(String videoId, String title, CharSequence subtitle,
                      String thumbnailUrl, long durationMs) {
            this.videoId = videoId;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.thumbnailUrl = thumbnailUrl;
            this.durationMs = Math.max(0L, durationMs);
        }

        @Override public int getType() { return TYPE_VIDEO; }
        @Override public boolean isLive() { return false; }
        @Override public boolean isUpcoming() { return false; }
        @Override public boolean isShorts() { return false; }
        @Override public int getPercentWatched() { return 0; }
        @Override public int getStartTimeSeconds() { return 0; }
        @Override public String getAuthor() { return ""; }
        @Override public String getFeedbackToken() { return null; }
        @Override public String getFeedbackToken2() { return null; }
        @Override public String getPlaylistId() { return null; }
        @Override public int getPlaylistIndex() { return -1; }
        @Override public String getParams() { return null; }
        @Override public String getReloadPageKey() { return null; }
        @Override public boolean hasNewContent() { return false; }
        @Override public int getId() { return videoId == null ? 0 : videoId.hashCode(); }
        @Override public String getTitle() { return title; }
        @Override public CharSequence getSecondTitle() { return subtitle; }
        @Override public String getVideoId() { return videoId; }
        @Override public String getContentType() { return "video/mp4"; }
        @Override public long getDurationMs() { return durationMs; }
        @Override public String getBadgeText() { return ""; }
        @Override public String getProductionDate() { return null; }
        @Override public long getPublishedDate() { return 0; }
        @Override public String getCardImageUrl() { return thumbnailUrl; }
        @Override public String getBackgroundImageUrl() { return thumbnailUrl; }
        @Override public int getWidth() { return 0; }
        @Override public int getHeight() { return 0; }
        @Override public String getChannelId() { return null; }
        @Override public String getVideoPreviewUrl() { return null; }
        @Override public String getAudioChannelConfig() { return null; }
        @Override public String getPurchasePrice() { return null; }
        @Override public String getRentalPrice() { return null; }
        @Override public int getRatingStyle() { return android.media.Rating.RATING_THUMB_UP_DOWN; }
        @Override public double getRatingScore() { return 0; }
        @Override public boolean isMovie() { return false; }
        @Override public boolean hasUploads() { return false; }
        @Override public String getClickTrackingParams() { return null; }
        @Override public String getSearchQuery() { return null; }
    }
}
