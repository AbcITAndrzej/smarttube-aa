package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
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
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaRecord;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistEntry;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistItemState;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistRecord;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineTripReserveService;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioStation;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioStationRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
    private static final String SUGGESTED_ROOT = "smarttube:auto:suggested";
    private static final String PAGE_PREFIX = "page:";
    private static final String SECTION_PREFIX = "section:";
    private static final String ITEM_PREFIX = "item:";
    private static final String MEDIA_PREFIX = "media:";
    private static final String MEDIA_ACTION_PLAY_LIKED = "action:liked:play_all";
    private static final String MEDIA_ACTION_SHUFFLE_LIKED = "action:liked:shuffle";
    private static final String LIKED_PAGE_ID = "liked_music";
    private static final String RADIO_HOME_PAGE_ID = "radio_home";
    private static final String RADIO_FAVORITES_PAGE_ID = "radio_favorites";
    private static final String RADIO_RECENT_PAGE_ID = "radio_recent";
    private static final String RADIO_COUNTRIES_PAGE_ID = "radio_countries";
    private static final String RADIO_GENRES_PAGE_ID = "radio_genres";
    private static final String RADIO_COUNTRY_PAGE_PREFIX = "radio_country:";
    private static final String RADIO_GENRE_PAGE_PREFIX = "radio_genre:";
    private static final String RADIO_SEARCH_CONTAINER_ID = SECTION_PREFIX + "radio:search";
    private static final String RADIO_ALL_PAGE_ID = "radio_all";
    private static final String OFFLINE_HOME_PAGE_ID = "offline_home";
    private static final String OFFLINE_RECENT_PAGE_ID = "offline_recent";
    private static final String OFFLINE_PLAYLISTS_PAGE_ID = "offline_playlists";
    private static final String OFFLINE_FAVORITES_PAGE_ID = "offline_favorites";
    private static final String OFFLINE_PLAYLIST_PAGE_PREFIX = "offline_playlist:";
    private static final String OFFLINE_ITEM_PREFIX = "offline:item:";
    private static final String OFFLINE_RECENT_CONTAINER_ID = SECTION_PREFIX + "offline:recent";
    private static final String OFFLINE_FAVORITES_CONTAINER_ID = SECTION_PREFIX + "offline:favorites";
    private static final String OFFLINE_PLAYLIST_CONTAINER_PREFIX = SECTION_PREFIX + "offline:playlist:";
    private static final int MAX_OFFLINE_AA_ITEMS = 160;
    private static final String MORE_PAGE_ID = "more";
    private static final String RADIO_FAVORITES_CONTAINER_ID = SECTION_PREFIX + "radio:favorites";
    private static final String RADIO_RECENT_CONTAINER_ID = SECTION_PREFIX + "radio:recent";
    private static final String RADIO_COUNTRY_CONTAINER_PREFIX = SECTION_PREFIX + "radio:country:";
    private static final String RADIO_GENRE_CONTAINER_PREFIX = SECTION_PREFIX + "radio:genre:";
    private static final String RADIO_ALL_CONTAINER_ID = SECTION_PREFIX + "radio:all";
    private static final String ROOT_HINT_SUGGESTED = "android.service.media.extra.SUGGESTED";
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
    private static final String ACTION_SWITCH_SOURCE = "com.liskovsoft.smarttube.mobile.auto.SWITCH_SOURCE";
    private static final String ACTION_RADIO_GO_LIVE = "com.liskovsoft.smarttube.mobile.auto.RADIO_GO_LIVE";
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
    private static final String PREF_PLAYBACK_ID = "last_playback_id";
    private static final String PREF_TITLE = "last_title";
    private static final String PREF_SUBTITLE = "last_subtitle";
    private static final String PREF_THUMB = "last_thumb";
    private static final String PREF_DURATION = "last_duration";
    private static final String PREF_POSITION = "last_position";
    private static final String PREF_QUEUE_INDEX = "last_queue_index";
    private static final String PREF_SOURCE_ID = "last_source_id";
    private static final String PREF_LIKE_PREFIX = "liked:";
    private static final String PREF_MODE_MUSIC_PREFIX = "mode_music:";
    private static final String PREF_MODE_RADIO_PREFIX = "mode_radio:";
    private static final long AUTO_ADVANCE_EARLY_MS = 1200L;
    private static final long RESUME_SAVE_INTERVAL_MS = 5000L;
    private static final long AUTO_RESUME_DELAY_MS = 150L;
    private static final long FORCE_ZERO_GUARD_MS = 1600L;
    private static final long FORCE_ZERO_THRESHOLD_MS = 5000L;
    private static final long DOUBLE_PREVIOUS_WINDOW_MS = 1600L;
    private static final long AUTO_ADVANCE_MIN_DURATION_MS = 10000L;
    private static final int MAX_RESUME_SOURCE_ATTEMPTS = 5;
    private static final long RESUME_RETRY_BASE_DELAY_MS = 1500L;

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
    private RadioStationRepository radioRepository;
    private RadioPreferences radioPreferences;
    private OfflineMediaRepository offlineMediaRepository;
    private OfflinePlaylistRepository offlinePlaylistRepository;
    private MobileFeatureFlags featureFlags;
    private MediaSessionCompat mediaSession;
    private SharedPreferences resumePrefs;
    private boolean destroyed;
    private AndroidAutoPreferences autoPreferences;
    private int lastPlaybackState = PlaybackStateCompat.STATE_NONE;
    private long lastPlaybackPositionMs;
    private float lastPlaybackSpeed;
    private final SharedPreferences.OnSharedPreferenceChangeListener autoSettingsListener =
            (sharedPreferences, key) -> {
                if (AndroidAutoPreferences.isPlaylistLayoutKey(key)) {
                    mainHandler.post(() -> {
                        if (!destroyed) notifyChildrenChanged(PAGE_PREFIX + "playlists");
                    });
                }
                if (AndroidAutoPreferences.isOfflinePlaybackKey(key)) {
                    mainHandler.post(() -> {
                        if (destroyed) return;
                        notifyChildrenChanged(ROOT);
                        notifyChildrenChanged(PAGE_PREFIX + OFFLINE_HOME_PAGE_ID);
                        notifyChildrenChanged(PAGE_PREFIX + OFFLINE_RECENT_PAGE_ID);
                        notifyChildrenChanged(PAGE_PREFIX + OFFLINE_PLAYLISTS_PAGE_ID);
                        notifyChildrenChanged(PAGE_PREFIX + OFFLINE_FAVORITES_PAGE_ID);
                    });
                }
            };
    private final SharedPreferences.OnSharedPreferenceChangeListener radioSettingsListener =
            (sharedPreferences, key) -> {
                if (!RadioPreferences.isPlaybackSettingKey(key)) return;
                mainHandler.post(() -> {
                    if (destroyed) return;
                    refreshRadioBrowseTree();
                    notifyChildrenChanged(ROOT);
                    updatePlaybackState(lastPlaybackState, lastPlaybackPositionMs, lastPlaybackSpeed);
                });
            };
    private final Runnable radioCatalogRefresh = this::refreshRadioBrowseTree;
    private final RadioStationRepository.ChangeListener radioCatalogListener = () -> {
        // A quick double tap should update the host once, after the final favorite state settles.
        mainHandler.removeCallbacks(radioCatalogRefresh);
        mainHandler.postDelayed(radioCatalogRefresh, 150L);
    };
    private String activeContainerId;
    private List<String> activeQueue = Collections.emptyList();
    private int activeQueueIndex = -1;
    private MobileMediaItem activeItem;
    /** Actual repository id. For Stage 9 local AA playback this is offline:<rawId>. */
    private String activePlaybackMediaId;
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
    private int resumeSourceAttempt;
    private String resumeSourcePageId;
    private String resumeReason;
    private boolean activeItemReachedReady;
    private boolean likedPlaybackLoadInProgress;
    private boolean pendingLikedShuffle;
    private boolean likedCatalogWarmStarted;
    private boolean likedCatalogReady;
    private String lastRecoveryMediaId;
    private int recoveryAttemptsForMediaId;
    @Override
    public void onCreate() {
        super.onCreate();
        destroyed = false;
        MobileDiagnostics.info("P13-AA-Service", "onCreate P13-AA1.8");

        resumePrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        migrateCurrentSelectionToModeHistory();
        autoPreferences = new AndroidAutoPreferences(this);
        autoPreferences.registerListener(autoSettingsListener);
        createNotificationChannel();

        provider = SmartTubeMobileNativeProvider.createForAutomotive(getApplicationContext());
        browseRepository = provider.browseRepository();
        browseRepository.setItemUpdateListener((itemId, payload) -> mainHandler.post(
                () -> applyBackgroundItemUpdate(itemId, payload)));
        playbackRepository = provider.automotivePlaybackRepository();
        radioRepository = RadioStationRepository.get(getApplicationContext());
        radioPreferences = new RadioPreferences(getApplicationContext());
        radioPreferences.registerListener(radioSettingsListener);
        offlineMediaRepository = OfflineMediaRepository.get(getApplicationContext());
        offlinePlaylistRepository = OfflinePlaylistRepository.get(getApplicationContext());
        featureFlags = new MobileFeatureFlags(getApplicationContext());
        radioRepository.addChangeListener(radioCatalogListener);
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
                boolean radio = activeItem != null
                        && RadioStationRepository.isRadioMediaId(activeItem.getId());
                if (radio) {
                    // In Radio DVR, position zero means the beginning of the rolling buffer, not
                    // "previous track". Keep the VOD double-previous gesture completely separate.
                    lastSeekToZeroCommandMs = 0L;
                    playbackRepository.seekTo(safePosition);
                    return;
                }
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
                } else if (ACTION_SWITCH_SOURCE.equals(action)) {
                    switchPlaybackSource();
                } else if (ACTION_RADIO_GO_LIVE.equals(action)) {
                    goLiveRadio();
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
                    if (!activeItemReachedReady) {
                        MobileDiagnostics.warn("P13-AA-Recovery",
                                "selected item failed before ready; retrying same item without auto-next");
                    }
                    // Stage 9: if the phone has actually lost connectivity, a complete local copy
                    // is a better first recovery than retrying a network URL that cannot work.
                    if (tryOfflineFallbackForActiveItem("network-unavailable", false)) {
                        return;
                    }
                    updatePlaybackState(PlaybackStateCompat.STATE_ERROR, 0L, 0f);
                    String mediaId = activePlaybackMediaId;
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
                    // A technically-connected mobile network can still be unusable in a tunnel,
                    // garage or handover. After the normal retry is exhausted, prefer the local
                    // audio copy before skipping the title.
                    if (tryOfflineFallbackForActiveItem("online-retry-exhausted", true)) {
                        return;
                    }
                    if (tryAdvanceToNextOffline("current-item-not-local")) {
                        return;
                    }
                    if (activeItemReachedReady && autoNextEnabled
                            && activeQueue != null && activeQueue.size() > 1) {
                        int nextIndex = resolveOfflineAutoAdvanceIndex(resolveAutoNextIndex());
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
        boolean suggested = rootHints != null
                && rootHints.getBoolean(ROOT_HINT_SUGGESTED, false);
        return new BrowserRoot(suggested ? SUGGESTED_ROOT : ROOT, null);
    }

    @Override
    public void onLoadChildren(String parentId,
                               Result<List<MediaBrowserCompat.MediaItem>> result) {
        if (ROOT.equals(parentId)) {
            result.sendResult(createAaRootItems());
            scheduleAutoResumeAfterOpen();
            return;
        }

        if (SUGGESTED_ROOT.equals(parentId)) {
            if (isOfflineAaEnabled() && !isNetworkAvailable() && hasOfflineMedia()) {
                result.sendResult(createOfflineRecentItems());
            } else {
                result.sendResult(createRadioItems(true));
            }
            return;
        }

        if ((PAGE_PREFIX + OFFLINE_HOME_PAGE_ID).equals(parentId)) {
            result.sendResult(createOfflineHomeItems());
            return;
        }

        if ((PAGE_PREFIX + OFFLINE_RECENT_PAGE_ID).equals(parentId)) {
            result.sendResult(createOfflineRecentItems());
            return;
        }

        if ((PAGE_PREFIX + OFFLINE_FAVORITES_PAGE_ID).equals(parentId)) {
            result.sendResult(createOfflineFavoriteItems());
            return;
        }

        if ((PAGE_PREFIX + OFFLINE_PLAYLISTS_PAGE_ID).equals(parentId)) {
            result.sendResult(createOfflinePlaylistFolders());
            return;
        }

        if (parentId != null && parentId.startsWith(PAGE_PREFIX + OFFLINE_PLAYLIST_PAGE_PREFIX)) {
            String playlistId = Uri.decode(parentId.substring(
                    (PAGE_PREFIX + OFFLINE_PLAYLIST_PAGE_PREFIX).length()));
            result.sendResult(createOfflinePlaylistItems(playlistId));
            return;
        }

        if ((PAGE_PREFIX + RADIO_HOME_PAGE_ID).equals(parentId)) {
            result.sendResult(createRadioHomeItems());
            return;
        }

        if ((PAGE_PREFIX + RADIO_FAVORITES_PAGE_ID).equals(parentId)) {
            result.sendResult(createRadioItems(true));
            return;
        }

        if ((PAGE_PREFIX + RADIO_RECENT_PAGE_ID).equals(parentId)) {
            result.sendResult(createRadioRecentItems());
            return;
        }

        if ((PAGE_PREFIX + RADIO_COUNTRIES_PAGE_ID).equals(parentId)) {
            result.sendResult(createRadioCountryFolders());
            return;
        }

        if ((PAGE_PREFIX + RADIO_GENRES_PAGE_ID).equals(parentId)) {
            result.sendResult(createRadioGenreFolders());
            return;
        }

        if (parentId != null && parentId.startsWith(PAGE_PREFIX + RADIO_COUNTRY_PAGE_PREFIX)) {
            String countryCode = Uri.decode(parentId.substring(
                    (PAGE_PREFIX + RADIO_COUNTRY_PAGE_PREFIX).length()));
            loadRadioFilteredItems(result, countryCode, "");
            return;
        }

        if (parentId != null && parentId.startsWith(PAGE_PREFIX + RADIO_GENRE_PAGE_PREFIX)) {
            String tag = Uri.decode(parentId.substring(
                    (PAGE_PREFIX + RADIO_GENRE_PAGE_PREFIX).length()));
            loadRadioFilteredItems(result, "", tag);
            return;
        }

        if ((PAGE_PREFIX + RADIO_ALL_PAGE_ID).equals(parentId)) {
            result.sendResult(createRadioItems(false));
            return;
        }

        if ((PAGE_PREFIX + MORE_PAGE_ID).equals(parentId)) {
            result.sendResult(createMoreItems());
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
    public void onSearch(String query, Bundle extras,
                         Result<List<MediaBrowserCompat.MediaItem>> result) {
        if (!isRadio2AaEnabled() || radioRepository == null) {
            result.sendResult(Collections.emptyList());
            return;
        }
        final String clean = query == null ? "" : query.trim();
        if (clean.length() < 2) {
            result.sendResult(Collections.emptyList());
            return;
        }
        List<RadioStation> local = radioRepository.getStations(
                RadioStationRepository.SortMode.POPULARITY, false, false,
                clean, "", "", 120);
        if (!radioPreferences.isServerSearchEnabled()
                || !featureFlags.isRadio2RemoteSearchEnabled()) {
            result.sendResult(createRadioItemsForList(local, RADIO_SEARCH_CONTAINER_ID,
                    "search", RADIO_HOME_PAGE_ID));
            return;
        }

        result.detach();
        radioRepository.searchRemote(clean, "", "", new RadioStationRepository.SearchCallback() {
            @Override public void onSuccess(List<RadioStation> stations, int addedToCache) {
                if (destroyed) return;
                List<RadioStation> merged = radioRepository.getStations(
                        RadioStationRepository.SortMode.POPULARITY, false, false,
                        clean, "", "", 120);
                result.sendResult(createRadioItemsForList(merged, RADIO_SEARCH_CONTAINER_ID,
                        "search", RADIO_HOME_PAGE_ID));
                refreshRadioBrowseTree();
            }

            @Override public void onError(String message) {
                if (destroyed) return;
                result.sendResult(createRadioItemsForList(local, RADIO_SEARCH_CONTAINER_ID,
                        "search", RADIO_HOME_PAGE_ID));
            }
        });
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        MobileDiagnostics.info("P13-AA-Service", "onDestroy");
        mainHandler.removeCallbacks(radioCatalogRefresh);
        if (radioRepository != null) {
            radioRepository.removeChangeListener(radioCatalogListener);
        }
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
        if (autoPreferences != null) {
            autoPreferences.unregisterListener(autoSettingsListener);
        }
        if (radioPreferences != null) {
            radioPreferences.unregisterListener(radioSettingsListener);
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

    private void refreshRadioBrowseTree() {
        if (destroyed || radioRepository == null) return;
        if (activeItem != null && RadioStationRepository.isRadioMediaId(activeItem.getId())) {
            RadioStation station = radioRepository.getStation(
                    RadioStationRepository.stationIdFromMediaId(activeItem.getId()));
            boolean favorite = station != null && station.isFavorite();
            if (favorite != activeLiked) {
                activeLiked = favorite;
                updateMetadata(activeItem, lastMetadataDurationMs);
                updatePlaybackState(lastPlaybackState, lastPlaybackPositionMs, lastPlaybackSpeed);
            }
        }
        notifyChildrenChanged(PAGE_PREFIX + RADIO_HOME_PAGE_ID);
        notifyChildrenChanged(PAGE_PREFIX + RADIO_FAVORITES_PAGE_ID);
        notifyChildrenChanged(PAGE_PREFIX + RADIO_RECENT_PAGE_ID);
        notifyChildrenChanged(PAGE_PREFIX + RADIO_COUNTRIES_PAGE_ID);
        notifyChildrenChanged(PAGE_PREFIX + RADIO_GENRES_PAGE_ID);
        notifyChildrenChanged(PAGE_PREFIX + RADIO_ALL_PAGE_ID);
        notifyChildrenChanged(SUGGESTED_ROOT);
        MobileDiagnostics.info("P14-Radio",
                "AA radio catalog refreshed favorites="
                        + radioRepository.getFavoriteCount());
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

    private List<MediaBrowserCompat.MediaItem> createAaRootItems() {
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        result.add(browsable(PAGE_PREFIX + "playlists", "Playlisty",
                "Wybierz playlistę z konta"));
        if (isOfflineAaEnabled()) {
            int ready = offlineMediaRepository == null ? 0
                    : offlineMediaRepository.getStats().getAvailableCount();
            result.add(browsable(PAGE_PREFIX + OFFLINE_HOME_PAGE_ID, "Offline",
                    ready > 0 ? ready + " utworów gotowych bez internetu"
                            : "Lokalne utwory i playlisty bez internetu"));
        }
        String radioPage = isRadio2AaEnabled()
                ? RADIO_HOME_PAGE_ID : RADIO_FAVORITES_PAGE_ID;
        result.add(browsable(PAGE_PREFIX + radioPage, "Radio",
                isRadio2AaEnabled()
                        ? "Ulubione, ostatnie, kraje i gatunki"
                        : "Ulubione stacje radiowe"));
        result.add(browsable(PAGE_PREFIX + "music", "Automatyczne",
                "Polecane i miksy z konta"));
        result.add(browsable(PAGE_PREFIX + MORE_PAGE_ID, "Więcej",
                "Wszystkie stacje i pozostałe sekcje"));
        return result;
    }

    private List<MediaBrowserCompat.MediaItem> createMoreItems() {
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        result.add(browsable(PAGE_PREFIX + RADIO_ALL_PAGE_ID, "Wszystkie stacje",
                "Najpopularniejsze stacje z lokalnie zsynchronizowanego katalogu"));
        result.add(browsable(PAGE_PREFIX + LIKED_PAGE_ID, "Polubiona muzyka",
                "Muzyka, która Ci się podoba"));
        result.add(browsable(PAGE_PREFIX + "history", "Ostatnio odtwarzane",
                "Historia oglądania i słuchania"));
        result.add(browsable(PAGE_PREFIX + "subscriptions", "Subskrypcje",
                "Najnowsze materiały z subskrypcji"));
        return result;
    }

    private List<MediaBrowserCompat.MediaItem> createOfflineHomeItems() {
        if (!isOfflineAaEnabled()) return Collections.emptyList();
        int ready = offlineMediaRepository == null ? 0
                : offlineMediaRepository.getStats().getAvailableCount();
        int playlists = 0;
        if (offlinePlaylistRepository != null) {
            for (OfflinePlaylistRecord playlist : offlinePlaylistRepository.list()) {
                if (countPlayableOfflineEntries(playlist.getPlaylistId()) > 0) playlists++;
            }
        }
        int favorites = countOfflineFavorites();
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        result.add(browsable(PAGE_PREFIX + OFFLINE_RECENT_PAGE_ID,
                "Ostatnio zapisane", ready + " lokalnych utworów"));
        result.add(browsable(PAGE_PREFIX + OFFLINE_PLAYLISTS_PAGE_ID,
                "Playlisty offline", playlists + " playlist"));
        result.add(browsable(PAGE_PREFIX + OFFLINE_FAVORITES_PAGE_ID,
                "Ulubione offline", favorites + " lokalnych ulubionych"));
        return result;
    }

    private List<MediaBrowserCompat.MediaItem> createOfflineRecentItems() {
        if (!isOfflineAaEnabled() || offlineMediaRepository == null) {
            return Collections.emptyList();
        }
        return createOfflineItemsForRecords(
                offlineMediaRepository.listAvailable(MAX_OFFLINE_AA_ITEMS),
                OFFLINE_RECENT_CONTAINER_ID, "recent");
    }

    private List<MediaBrowserCompat.MediaItem> createOfflineFavoriteItems() {
        if (!isOfflineAaEnabled() || offlineMediaRepository == null) {
            return Collections.emptyList();
        }
        List<OfflineMediaRecord> favorites = new ArrayList<>();
        for (OfflineMediaRecord record : offlineMediaRepository.listAvailable(0)) {
            if (record == null || !resolveKnownLike(record.getMediaId())) continue;
            if (!offlineMediaRepository.hasAvailableFile(record.getMediaId())) continue;
            favorites.add(record);
            if (favorites.size() >= MAX_OFFLINE_AA_ITEMS) break;
        }
        return createOfflineItemsForRecords(
                favorites, OFFLINE_FAVORITES_CONTAINER_ID, "favorites");
    }

    private List<MediaBrowserCompat.MediaItem> createOfflinePlaylistFolders() {
        if (!isOfflineAaEnabled() || offlinePlaylistRepository == null) {
            return Collections.emptyList();
        }
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        for (OfflinePlaylistRecord playlist : offlinePlaylistRepository.list()) {
            if (playlist == null) continue;
            int playable = countPlayableOfflineEntries(playlist.getPlaylistId());
            if (playable <= 0) continue;
            String subtitle = playable + "/" + playlist.getTotalCount() + " gotowych";
            if (playlist.getFailedCount() > 0) {
                subtitle += " • " + playlist.getFailedCount() + " błędów";
            }
            result.add(browsable(PAGE_PREFIX + OFFLINE_PLAYLIST_PAGE_PREFIX
                    + Uri.encode(playlist.getPlaylistId()),
                    playlist.getTitle().isEmpty() ? "Playlista offline" : playlist.getTitle(),
                    subtitle));
        }
        return result;
    }

    private List<MediaBrowserCompat.MediaItem> createOfflinePlaylistItems(String playlistId) {
        if (!isOfflineAaEnabled() || offlinePlaylistRepository == null
                || offlineMediaRepository == null || playlistId == null) {
            return Collections.emptyList();
        }
        List<OfflinePlaylistEntry> playable = new ArrayList<>();
        for (OfflinePlaylistEntry entry : offlinePlaylistRepository.entries(playlistId)) {
            if (entry == null || entry.getState() != OfflinePlaylistItemState.AVAILABLE) continue;
            if (!offlineMediaRepository.hasAvailableFile(entry.getMediaId())) continue;
            playable.add(entry);
            if (playable.size() >= MAX_OFFLINE_AA_ITEMS) break;
        }
        String containerId = OFFLINE_PLAYLIST_CONTAINER_PREFIX + playlistId;
        replaceOfflineQueue(containerId);
        List<String> browserIds = new ArrayList<>();
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        int index = 0;
        for (OfflinePlaylistEntry entry : playable) {
            MobileMediaItem item = offlineMobileItem(entry.getMediaId(), entry.getTitle(),
                    entry.getAuthor(), entry.getThumbnailUrl(), entry.getDurationMs());
            String browserId = offlineBrowserId(containerId, index++, entry.getMediaId());
            mediaByBrowserId.put(browserId, item);
            browserIds.add(browserId);
            result.add(playable(browserId, item));
        }
        queueByContainer.put(containerId, browserIds);
        sourceByContainer.put(containerId, PAGE_PREFIX + OFFLINE_PLAYLIST_PAGE_PREFIX
                + Uri.encode(playlistId));
        return result;
    }

    private List<MediaBrowserCompat.MediaItem> createOfflineItemsForRecords(
            List<OfflineMediaRecord> records, String containerId, String source) {
        if (records == null || records.isEmpty()) {
            replaceOfflineQueue(containerId);
            queueByContainer.put(containerId, new ArrayList<>());
            sourceByContainer.put(containerId, PAGE_PREFIX + OFFLINE_HOME_PAGE_ID);
            return Collections.emptyList();
        }
        replaceOfflineQueue(containerId);
        List<String> browserIds = new ArrayList<>();
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        int index = 0;
        for (OfflineMediaRecord record : records) {
            if (record == null || offlineMediaRepository.peekAvailableFile(record.getMediaId()) == null) {
                continue;
            }
            MobileMediaItem item = offlineMobileItem(record.getMediaId(), record.getTitle(),
                    record.getAuthor(), record.getThumbnailUrl(), record.getDurationMs());
            String browserId = offlineBrowserId(containerId, index++, record.getMediaId());
            mediaByBrowserId.put(browserId, item);
            browserIds.add(browserId);
            result.add(playable(browserId, item));
            if (browserIds.size() >= MAX_OFFLINE_AA_ITEMS) break;
        }
        queueByContainer.put(containerId, browserIds);
        sourceByContainer.put(containerId, PAGE_PREFIX + OFFLINE_HOME_PAGE_ID + ":" + source);
        MobileDiagnostics.info("P17-AA-Offline",
                "browse source=" + source + " items=" + browserIds.size());
        return result;
    }

    private MobileMediaItem offlineMobileItem(String mediaId, String title, String author,
                                              String thumbnailUrl, long durationMs) {
        String cleanAuthor = author == null ? "" : author.trim();
        String subtitle = cleanAuthor.isEmpty() ? "Offline" : cleanAuthor + " • Offline";
        return new MobileMediaItem(mediaId, MobileMediaItem.Kind.VIDEO,
                title == null || title.trim().isEmpty() ? "Utwór offline" : title,
                subtitle, thumbnailUrl, "", 0L, Math.max(0L, durationMs), true);
    }

    private String offlineBrowserId(String containerId, int index, String mediaId) {
        return OFFLINE_ITEM_PREFIX + Integer.toHexString(containerId == null ? 0 : containerId.hashCode())
                + ":" + index + ":" + Uri.encode(mediaId == null ? "" : mediaId);
    }

    private void replaceOfflineQueue(String containerId) {
        List<String> old = queueByContainer.remove(containerId);
        if (old != null) {
            for (String browserId : old) {
                if (browserId != null && browserId.startsWith(OFFLINE_ITEM_PREFIX)) {
                    mediaByBrowserId.remove(browserId);
                }
            }
        }
    }

    private int countPlayableOfflineEntries(String playlistId) {
        if (offlinePlaylistRepository == null || offlineMediaRepository == null
                || playlistId == null) return 0;
        int count = 0;
        for (OfflinePlaylistEntry entry : offlinePlaylistRepository.entries(playlistId)) {
            if (entry != null && entry.getState() == OfflinePlaylistItemState.AVAILABLE
                    && offlineMediaRepository.hasAvailableFile(entry.getMediaId())) {
                count++;
            }
        }
        return count;
    }

    private int countOfflineFavorites() {
        if (offlineMediaRepository == null) return 0;
        int count = 0;
        for (OfflineMediaRecord record : offlineMediaRepository.listAvailable(0)) {
            if (record != null && resolveKnownLike(record.getMediaId())
                    && offlineMediaRepository.hasAvailableFile(record.getMediaId())) {
                count++;
            }
        }
        return count;
    }

    private boolean hasOfflineMedia() {
        return offlineMediaRepository != null
                && offlineMediaRepository.getStats().getAvailableCount() > 0;
    }

    private boolean isOfflineAaEnabled() {
        return featureFlags != null && featureFlags.isOfflineAndroidAutoEnabled()
                && autoPreferences != null && autoPreferences.isOfflineLibraryEnabled()
                && offlineMediaRepository != null && offlineMediaRepository.isEnabled();
    }

    private boolean isOfflineAutoFallbackEnabled() {
        return isOfflineAaEnabled() && autoPreferences.isOfflineAutoFallbackEnabled();
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(
                    Context.CONNECTIVITY_SERVICE);
            if (manager == null) return false;
            if (Build.VERSION.SDK_INT >= 23) {
                Network network = manager.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                return capabilities != null
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
            //noinspection deprecation
            NetworkInfo info = manager.getActiveNetworkInfo();
            //noinspection deprecation
            return info != null && info.isConnected();
        } catch (Throwable error) {
            MobileDiagnostics.warn("P17-AA-Offline",
                    "connectivity check failed: " + error.getClass().getSimpleName());
            return false;
        }
    }

    private List<MediaBrowserCompat.MediaItem> createRadioHomeItems() {
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        result.add(browsable(PAGE_PREFIX + RADIO_FAVORITES_PAGE_ID, "Ulubione",
                radioRepository == null ? "" : radioRepository.getFavoriteCount() + " stacji"));
        if (radioPreferences != null && radioPreferences.isRecentStationsEnabled()) {
            result.add(browsable(PAGE_PREFIX + RADIO_RECENT_PAGE_ID, "Ostatnio słuchane",
                    radioRepository == null ? "" : radioRepository.getRecentCount() + " stacji"));
        }
        if (radioPreferences != null && radioPreferences.isCategoriesEnabled()) {
            result.add(browsable(PAGE_PREFIX + RADIO_COUNTRIES_PAGE_ID, "Kraje",
                    "Stacje pogrupowane według kraju"));
            result.add(browsable(PAGE_PREFIX + RADIO_GENRES_PAGE_ID, "Gatunki",
                    "Najpopularniejsze tagi i gatunki"));
        }
        result.add(browsable(PAGE_PREFIX + RADIO_ALL_PAGE_ID, "Wszystkie stacje",
                "Najpopularniejsze stacje z lokalnego katalogu"));
        return result;
    }

    private List<MediaBrowserCompat.MediaItem> createRadioCountryFolders() {
        if (radioRepository == null) return Collections.emptyList();
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        List<RadioStationRepository.FilterOption> options = radioRepository.getCountryOptions();
        int limit = Math.min(options.size(), 120);
        for (int i = 0; i < limit; i++) {
            RadioStationRepository.FilterOption option = options.get(i);
            result.add(browsable(PAGE_PREFIX + RADIO_COUNTRY_PAGE_PREFIX
                            + Uri.encode(option.getValue()),
                    option.getLabel(), option.getStationCount() + " stacji"));
        }
        return result;
    }

    private List<MediaBrowserCompat.MediaItem> createRadioGenreFolders() {
        if (radioRepository == null) return Collections.emptyList();
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        for (RadioStationRepository.FilterOption option : radioRepository.getTagOptions()) {
            result.add(browsable(PAGE_PREFIX + RADIO_GENRE_PAGE_PREFIX
                            + Uri.encode(option.getValue()),
                    option.getLabel(), option.getStationCount() + " stacji"));
        }
        return result;
    }

    private List<MediaBrowserCompat.MediaItem> createRadioRecentItems() {
        if (radioRepository == null) return Collections.emptyList();
        return createRadioItemsForList(radioRepository.getRecentStationsForAutomotive(),
                RADIO_RECENT_CONTAINER_ID, "recent", RADIO_RECENT_PAGE_ID);
    }

    private void loadRadioFilteredItems(Result<List<MediaBrowserCompat.MediaItem>> result,
                                        String countryCode, String tag) {
        String countryValue = countryCode == null ? "" : countryCode.trim();
        String tagValue = tag == null ? "" : tag.trim();
        List<RadioStation> local = !countryValue.isEmpty()
                ? radioRepository.getStationsForAutomotiveCountry(countryValue)
                : radioRepository.getStationsForAutomotiveTag(tagValue);
        if (!isRadio2AaEnabled() || radioPreferences == null
                || !radioPreferences.isServerSearchEnabled()
                || featureFlags == null || !featureFlags.isRadio2RemoteSearchEnabled()
                || local.size() >= 60) {
            result.sendResult(!countryValue.isEmpty()
                    ? createRadioCountryItems(countryValue) : createRadioGenreItems(tagValue));
            return;
        }
        result.detach();
        radioRepository.searchRemote("", countryValue, tagValue,
                new RadioStationRepository.SearchCallback() {
                    @Override public void onSuccess(List<RadioStation> stations, int addedToCache) {
                        if (destroyed) return;
                        result.sendResult(!countryValue.isEmpty()
                                ? createRadioCountryItems(countryValue)
                                : createRadioGenreItems(tagValue));
                    }

                    @Override public void onError(String message) {
                        if (destroyed) return;
                        result.sendResult(!countryValue.isEmpty()
                                ? createRadioCountryItems(countryValue)
                                : createRadioGenreItems(tagValue));
                    }
                });
    }

    private List<MediaBrowserCompat.MediaItem> createRadioCountryItems(String countryCode) {
        if (radioRepository == null) return Collections.emptyList();
        String clean = countryCode == null ? "" : countryCode.trim();
        return createRadioItemsForList(radioRepository.getStationsForAutomotiveCountry(clean),
                RADIO_COUNTRY_CONTAINER_PREFIX + clean, "country",
                RADIO_COUNTRY_PAGE_PREFIX + clean);
    }

    private List<MediaBrowserCompat.MediaItem> createRadioGenreItems(String tag) {
        if (radioRepository == null) return Collections.emptyList();
        String clean = tag == null ? "" : tag.trim();
        return createRadioItemsForList(radioRepository.getStationsForAutomotiveTag(clean),
                RADIO_GENRE_CONTAINER_PREFIX + clean, "genre",
                RADIO_GENRE_PAGE_PREFIX + clean);
    }

    private List<MediaBrowserCompat.MediaItem> createRadioItems(boolean favoritesOnly) {
        if (radioRepository == null) {
            radioRepository = RadioStationRepository.get(getApplicationContext());
        }
        List<RadioStation> stations = radioRepository.getStationsForAutomotive(favoritesOnly);
        String containerId = favoritesOnly
                ? RADIO_FAVORITES_CONTAINER_ID : RADIO_ALL_CONTAINER_ID;
        String sourceId = favoritesOnly
                ? RADIO_FAVORITES_PAGE_ID : RADIO_ALL_PAGE_ID;
        return createRadioItemsForList(stations, containerId,
                favoritesOnly ? "favorite" : "all", sourceId);
    }

    private List<MediaBrowserCompat.MediaItem> createRadioItemsForList(
            List<RadioStation> stations, String containerId, String browserKind, String sourceId) {
        List<String> browserIds = new ArrayList<>();
        int index = 0;
        for (RadioStation station : stations == null ? Collections.<RadioStation>emptyList() : stations) {
            String mediaId = RadioStationRepository.mediaId(station.getId());
            String browserId = MEDIA_PREFIX + "radio:" + browserKind + ":"
                    + Integer.toHexString(containerId.hashCode()) + ":"
                    + index++ + ":" + station.getId();
            String codec = station.getCodec() == null || station.getCodec().trim().isEmpty()
                    ? "Radio" : station.getCodec();
            String streamMeta = station.getBitrate() > 0
                    ? codec + " • " + station.getBitrate() + " kb/s" : codec;
            String country = station.getCountry().isEmpty()
                    ? station.getCountryCode() : station.getCountry();
            String subtitle = country.isEmpty() ? streamMeta : country + " • " + streamMeta;
            MobileMediaItem item = new MobileMediaItem(mediaId, MobileMediaItem.Kind.LIVE,
                    station.getName(), subtitle, station.getFaviconUrl(), "NA ŻYWO",
                    0L, 0L, true);
            mediaByBrowserId.put(browserId, item);
            browserIds.add(browserId);
        }
        queueByContainer.put(containerId, browserIds);
        sourceByContainer.put(containerId, PAGE_PREFIX + sourceId);
        MobileDiagnostics.info("P18-Radio2",
                "AA radio page kind=" + browserKind + " stations=" + browserIds.size());
        return mediaItems(browserIds);
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
        playBrowserMediaId(likedQueue.get(index), false, false, null);
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

        if (activeItem != null
                && sourceId.equals(sourceByContainer.get(activeContainerId))
                && attachHydratedQueue(sourceId, activeItem.getId())) {
            return;
        }

        if (activeContainerId != null && currentBrowserId != null) {
            List<String> completedQueue = queueByContainer.get(activeContainerId);
            int completedIndex = completedQueue == null
                    ? -1 : completedQueue.indexOf(currentBrowserId);
            if (completedIndex < 0 && completedQueue != null && activeItem != null) {
                completedIndex = findMediaIndex(completedQueue, activeItem.getId());
                if (completedIndex >= 0) {
                    currentBrowserId = completedQueue.get(completedIndex);
                }
            }
            if (completedIndex >= 0) {
                activeQueue = new ArrayList<>(completedQueue);
                activeQueueIndex = completedIndex;
                MobileMediaItem hydratedItem = mediaByBrowserId.get(currentBrowserId);
                if (hydratedItem != null) {
                    activeItem = hydratedItem;
                }
                publishQueue(activeQueue);
                updatePlaybackState(lastPlaybackState,
                        lastPlaybackPositionMs, lastPlaybackSpeed);
                savePlaybackSelection(currentBrowserId, activeItem, lastPlaybackPositionMs);
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
        Map<String, MediaBrowserCompat.MediaItem> cardsByKey = new LinkedHashMap<>();
        for (MobileSection section : payload.getSections()) {
            if (section == null || section.getItems() == null) continue;
            for (MobileMediaItem item : section.getItems()) {
                if (item == null) continue;
                MobileDiagnostics.debug("P13-AA-Playlist", "card title=" + item.getTitle()
                        + " kind=" + item.getKind() + " id=" + item.getId()
                        + " playlistId=" + item.getPlaylistId());
                if (item.getKind() != MobileMediaItem.Kind.PLAYLIST) continue;
                String playlistId = AndroidAutoPreferences.playlistKey(item);
                if (playlistId == null || playlistId.trim().isEmpty()) continue;
                if (cardsByKey.containsKey(playlistId)) continue;
                String subtitle = item.getSubtitle();
                if (subtitle == null || subtitle.trim().isEmpty()) subtitle = "Otwórz playlistę";
                cardsByKey.put(playlistId,
                        browsable(ITEM_PREFIX + playlistId, item.getTitle(), subtitle));
            }
        }
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        Set<String> hidden = autoPreferences == null
                ? Collections.emptySet() : autoPreferences.getHiddenPlaylists();
        List<String> order = autoPreferences == null
                ? new ArrayList<>(cardsByKey.keySet())
                : autoPreferences.orderAvailableKeys(cardsByKey.keySet());
        for (String key : order) {
            MediaBrowserCompat.MediaItem card = cardsByKey.get(key);
            if (card != null && !hidden.contains(key)) result.add(card);
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
        playBrowserMediaId(browserId, false, false, null);
    }

    private void playBrowserMediaId(String browserId, boolean fromAutoAdvance) {
        playBrowserMediaId(browserId, fromAutoAdvance, false, null);
    }

    private void playResumeBrowserId(String browserId) {
        playBrowserMediaId(browserId, false, true, null);
    }

    private void playResumeBrowserId(String browserId, String forcedPlaybackMediaId) {
        playBrowserMediaId(browserId, false, true, forcedPlaybackMediaId);
    }

    private void playBrowserMediaId(
            String browserId, boolean fromAutoAdvance, boolean fromResume,
            String forcedPlaybackMediaId) {
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
        activePlaybackMediaId = resolvePlaybackMediaId(browserId, item, forcedPlaybackMediaId);
        activeLiked = resolveKnownLike(item.getId());
        activeItemReachedReady = false;
        lastRecoveryMediaId = activePlaybackMediaId;
        recoveryAttemptsForMediaId = 0;
        if (!fromAutoAdvance) {
            lastAutoAdvancedFromBrowserId = null;
        }

        publishQueue(activeQueue);

        boolean radioSelection = RadioStationRepository.isRadioMediaId(item.getId());
        forceZeroUntilMs = radioSelection ? 0L : System.currentTimeMillis() + FORCE_ZERO_GUARD_MS;
        boolean localPlayback = OfflineMediaRepository.isOfflinePlaybackId(activePlaybackMediaId);
        String activeSourceId = activeContainerId == null
                ? null : sourceByContainer.get(activeContainerId);
        boolean playlistPlayback = isPlaylistPlaybackSource(item, activeSourceId);
        playbackRepository.setPlaybackContext(playlistPlayback);
        MobileDiagnostics.info("P13-AA-Playback",
                "selected browserId=" + browserId + " mediaId=" + item.getId()
                        + " playbackId=" + activePlaybackMediaId
                        + " queueIndex=" + activeQueueIndex
                        + " queueSize=" + activeQueue.size()
                        + " auto=" + fromAutoAdvance
                        + " resume=" + fromResume
                        + " local=" + localPlayback
                        + " playlist=" + playlistPlayback
                        + " source=" + activeSourceId
                        + " start=0 forceZero=" + !radioSelection);
        if (localPlayback) {
            MobileDiagnostics.info("P17-AA-Offline",
                    "local playback selected media=" + item.getId()
                            + " explicit=" + (browserId != null
                            && browserId.startsWith(OFFLINE_ITEM_PREFIX))
                            + " network=" + isNetworkAvailable());
        }
        updateMetadata(item, 0L);
        updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING, 0L, 0f);
        savePlaybackSelection(browserId, item, 0L);
        playbackRepository.prepare(activePlaybackMediaId, 0L);
        // prepareRadio() starts at the live edge. A follow-up seekTo(0) would immediately rewind
        // to the oldest buffered point, so only VOD gets the historic zero-position command.
        if (!radioSelection) playbackRepository.seekTo(0L);
        playbackRepository.play();
        if (radioSelection && radioRepository != null) {
            radioRepository.reportClick(
                    RadioStationRepository.stationIdFromMediaId(item.getId()));
        }
    }

    private boolean isPlaylistPlaybackSource(MobileMediaItem item, String sourceId) {
        String playlistId = item == null ? null : item.getPlaylistId();
        return (playlistId != null && !playlistId.trim().isEmpty())
                || (sourceId != null && sourceId.startsWith(ITEM_PREFIX + "playlist:"));
    }

    private boolean isOfflineSourceId(String sourceId) {
        return sourceId != null && sourceId.startsWith(PAGE_PREFIX + "offline");
    }

    private String validSavedOfflinePlaybackId(String savedPlaybackId, String sourceId) {
        if (!isOfflineAaEnabled() || offlineMediaRepository == null
                || !OfflineMediaRepository.isOfflinePlaybackId(savedPlaybackId)) {
            return null;
        }
        String raw = OfflineMediaRepository.rawMediaId(savedPlaybackId);
        boolean localAvailable = offlineMediaRepository.hasAvailableFile(raw);
        // Explicit Offline browsing should remain local across restarts. A local copy chosen only
        // as an automatic connectivity fallback should be forced again only while still offline;
        // after connectivity returns the normal online source is allowed to resume normally.
        return AndroidAutoOfflineRouting.shouldForceSavedOffline(
                isOfflineSourceId(sourceId), isOfflineAutoFallbackEnabled(),
                isNetworkAvailable(), localAvailable) ? savedPlaybackId : null;
    }

    private String resolvePlaybackMediaId(String browserId, MobileMediaItem item,
                                          String forcedPlaybackMediaId) {
        if (item == null || item.getId() == null) return forcedPlaybackMediaId;
        String rawId = item.getId();
        if (RadioStationRepository.isRadioMediaId(rawId) || !isOfflineAaEnabled()) return rawId;

        if (OfflineMediaRepository.isOfflinePlaybackId(forcedPlaybackMediaId)
                && offlineMediaRepository.hasAvailableFile(
                OfflineMediaRepository.rawMediaId(forcedPlaybackMediaId))) {
            return forcedPlaybackMediaId;
        }

        boolean explicit = browserId != null && browserId.startsWith(OFFLINE_ITEM_PREFIX);
        boolean local = offlineMediaRepository.hasAvailableFile(rawId);
        boolean useOffline = AndroidAutoOfflineRouting.shouldUseOffline(
                explicit, isOfflineAaEnabled(), isOfflineAutoFallbackEnabled(),
                isNetworkAvailable(), local);
        return useOffline ? OfflineMediaRepository.playbackId(rawId) : rawId;
    }

    private boolean tryOfflineFallbackForActiveItem(String reason,
                                                    boolean allowWhenNetworkAvailable) {
        if (!isOfflineAutoFallbackEnabled() || activeItem == null
                || activePlaybackMediaId == null
                || OfflineMediaRepository.isOfflinePlaybackId(activePlaybackMediaId)
                || RadioStationRepository.isRadioMediaId(activeItem.getId())) {
            return false;
        }
        if (!allowWhenNetworkAvailable && isNetworkAvailable()) return false;
        if (!offlineMediaRepository.hasAvailableFile(activeItem.getId())) return false;

        String localId = OfflineMediaRepository.playbackId(activeItem.getId());
        long position = Math.max(0L, lastPlaybackPositionMs);
        activePlaybackMediaId = localId;
        activeItemReachedReady = false;
        lastRecoveryMediaId = localId;
        recoveryAttemptsForMediaId = 0;
        updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING, position, 0f);
        String browserId = activeBrowserId();
        if (browserId != null) savePlaybackSelection(browserId, activeItem, position);
        MobileDiagnostics.warn("P17-AA-Offline",
                "fallback to local media=" + activeItem.getId()
                        + " reason=" + reason + " position=" + position
                        + " network=" + isNetworkAvailable());
        playbackRepository.prepare(localId, position);
        playbackRepository.play();
        return true;
    }

    private String activeBrowserId() {
        return activeQueue != null && activeQueueIndex >= 0 && activeQueueIndex < activeQueue.size()
                ? activeQueue.get(activeQueueIndex) : null;
    }

    private boolean hasLocalCopyForBrowserId(String browserId) {
        if (browserId == null || offlineMediaRepository == null) return false;
        MobileMediaItem item = mediaByBrowserId.get(browserId);
        return item != null && !RadioStationRepository.isRadioMediaId(item.getId())
                && offlineMediaRepository.hasAvailableFile(item.getId());
    }

    /**
     * When AA is already offline, skip over online-only entries instead of stalling an otherwise
     * locally playable queue. Manual queue-item selection remains exact and is never silently
     * redirected to a different title.
     */
    private int resolveOfflineAutoAdvanceIndex(int candidate) {
        if (!isOfflineAutoFallbackEnabled() || isNetworkAvailable()
                || activeQueue == null || activeQueue.isEmpty()) return candidate;
        if (candidate >= 0 && candidate < activeQueue.size()
                && hasLocalCopyForBrowserId(activeQueue.get(candidate))) return candidate;

        boolean allowWrap = repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL;
        int size = activeQueue.size();
        int start = candidate < 0 ? activeQueueIndex + 1 : candidate;
        boolean[] localAvailable = new boolean[size];
        for (int index = 0; index < size; index++) {
            localAvailable[index] = index != activeQueueIndex
                    && hasLocalCopyForBrowserId(activeQueue.get(index));
        }
        int resolved = AndroidAutoOfflineRouting.findNextLocalIndex(
                start, localAvailable, allowWrap);
        if (resolved >= 0 && resolved != candidate) {
            MobileDiagnostics.info("P17-AA-Offline",
                    "offline queue skip from candidate=" + candidate + " to=" + resolved);
        }
        return resolved;
    }

    private boolean tryAdvanceToNextOffline(String reason) {
        if (!isOfflineAutoFallbackEnabled() || isNetworkAvailable()
                || activeQueue == null || activeQueue.size() < 2) return false;
        int next = resolveOfflineAutoAdvanceIndex(activeQueueIndex + 1);
        if (next < 0 || next == activeQueueIndex) return false;
        MobileDiagnostics.warn("P17-AA-Offline",
                "advance to next local item index=" + next + " reason=" + reason);
        playQueueIndex(next, true);
        return true;
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
        String savedSourceId = resumePrefs.getString(PREF_SOURCE_ID, null);
        String sourceId = savedSourceId == null || savedSourceId.trim().isEmpty()
                ? "history" : savedSourceId;
        String savedPlaybackId = resumePrefs.getString(PREF_PLAYBACK_ID, savedVideoId);
        String forcedResumePlaybackId = validSavedOfflinePlaybackId(savedPlaybackId, sourceId);
        if (RadioStationRepository.isRadioMediaId(savedVideoId)) {
            // Radio is backed by the local persistent cache, so its queue can be restored
            // immediately without a YouTube browse request.
            boolean favorites = RADIO_FAVORITES_CONTAINER_ID.equals(
                    resumePrefs.getString(PREF_CONTAINER_ID, null));
            createRadioItems(favorites);
            // The station may have been removed from favorites while it was still playing.
            // Keep resume reliable by also exposing it through the full local catalog.
            if (findLoadedResumeBrowserId(savedBrowserId, savedVideoId) == null && favorites) {
                createRadioItems(false);
            }
            if (findLoadedResumeBrowserId(savedBrowserId, savedVideoId) == null) {
                String stationId = RadioStationRepository.stationIdFromMediaId(savedVideoId);
                RadioStation station = radioRepository.getStation(stationId);
                if (station != null) {
                    String resumeContainer = resumePrefs.getString(PREF_CONTAINER_ID, null);
                    if (resumeContainer == null || resumeContainer.trim().isEmpty()) {
                        resumeContainer = SECTION_PREFIX + "radio:resume";
                    }
                    createRadioItemsForList(Collections.singletonList(station), resumeContainer,
                            "resume", RADIO_HOME_PAGE_ID);
                }
            }
        }
        String loadedBrowserId = findLoadedResumeBrowserId(savedBrowserId, savedVideoId);
        if (loadedBrowserId != null) {
            resumeAutoplayConsumed = true;
            MobileDiagnostics.info("P13-AA-Resume",
                    "resume loaded reason=" + reason + " browserId=" + loadedBrowserId
                            + " fromZero=true");
            playResumeBrowserId(loadedBrowserId, forcedResumePlaybackId);
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

        // YouTube playback only needs the stable video ID. Start it immediately from
        // persisted metadata, then rebuild the owning queue independently in the
        // background. This removes the cold-start dependency on the transient browse
        // index and keeps the first audible result local and deterministic.
        if (!RadioStationRepository.isRadioMediaId(savedVideoId)
                && startImmediateSavedPlayback(savedBrowserId, savedVideoId, sourceId, reason,
                forcedResumePlaybackId)) {
            if (isNetworkAvailable() && !isOfflineSourceId(sourceId)) {
                hydrateResumeQueueInBackground(sourceId, savedVideoId);
            } else {
                MobileDiagnostics.info("P17-AA-Offline",
                        "resume queue hydration skipped source=" + sourceId
                                + " network=" + isNetworkAvailable());
            }
            return true;
        }

        resumeLoadInProgress = true;
        resumeSourceAttempt = 1;
        resumeSourcePageId = sourceId;
        resumeReason = reason;
        MobileDiagnostics.info("P13-AA-Resume",
                "resume loading source=" + sourceId + " reason=" + reason
                        + " attempt=" + resumeSourceAttempt + " videoId=" + savedVideoId);
        loadResumePageAndPlay(sourceId, reason, true);
        return true;
    }

    private boolean startImmediateSavedPlayback(
            String savedBrowserId, String savedVideoId, String sourceId, String reason,
            String forcedPlaybackMediaId) {
        String savedContainerId = resumePrefs.getString(PREF_CONTAINER_ID, null);
        if (savedContainerId == null || savedContainerId.trim().isEmpty()) {
            savedContainerId = SECTION_PREFIX + "resume:local";
        }
        if (savedBrowserId == null || savedBrowserId.trim().isEmpty()) {
            savedBrowserId = MEDIA_PREFIX + savedContainerId + ":resume:" + savedVideoId;
        }

        String playlistId = null;
        String playlistPrefix = ITEM_PREFIX + "playlist:";
        if (sourceId != null && sourceId.startsWith(playlistPrefix)) {
            playlistId = sourceId.substring(playlistPrefix.length());
        }
        MobileMediaItem savedItem = new MobileMediaItem(
                savedVideoId,
                MobileMediaItem.Kind.VIDEO,
                resumePrefs.getString(PREF_TITLE, "Ostatni utwór"),
                resumePrefs.getString(PREF_SUBTITLE, ""),
                resumePrefs.getString(PREF_THUMB, null),
                "",
                0L,
                resumePrefs.getLong(PREF_DURATION, 0L),
                true,
                playlistId);

        mediaByBrowserId.put(savedBrowserId, savedItem);
        queueByContainer.put(savedContainerId,
                new ArrayList<>(Collections.singletonList(savedBrowserId)));
        sourceByContainer.put(savedContainerId, sourceId);
        resumeAutoplayConsumed = true;
        MobileDiagnostics.info("P13-AA-Resume",
                "instant local resume reason=" + reason + " browserId=" + savedBrowserId
                        + " videoId=" + savedVideoId + " source=" + sourceId);
        playResumeBrowserId(savedBrowserId, forcedPlaybackMediaId);
        return activeItem != null;
    }

    private void hydrateResumeQueueInBackground(String sourceId, String savedVideoId) {
        hydrateResumeQueueInBackground(sourceId, savedVideoId, true);
    }

    private void hydrateResumeQueueInBackground(
            String sourceId, String savedVideoId, boolean allowPlaylistCatalogRecovery) {
        if (sourceId == null || sourceId.trim().isEmpty() || browseRepository == null) {
            return;
        }
        final MobileRequest[] requestHolder = { MobileRequest.NONE };
        MobileResultCallback<MobileBrowsePayload> callback =
                new MobileResultCallback<MobileBrowsePayload>() {
                    @Override public void onSuccess(MobileBrowsePayload payload) {
                        mainHandler.post(() -> completeResumeQueueHydration(
                                sourceId, savedVideoId, payload, requestHolder,
                                allowPlaylistCatalogRecovery));
                    }

                    @Override public void onError(MobileError error) {
                        MobileDiagnostics.warn("P13-AA-Resume",
                                "background queue hydration failed source=" + sourceId
                                        + " error=" + error);
                        mainHandler.post(() -> completeResumeQueueHydration(
                                sourceId, savedVideoId, null, requestHolder,
                                allowPlaylistCatalogRecovery));
                    }
                };
        try {
            MobileRequest request = sourceId.startsWith(ITEM_PREFIX)
                    ? browseRepository.loadItem(
                            sourceId.substring(ITEM_PREFIX.length()), callback)
                    : browseRepository.loadBrowse(sourceId, callback);
            requestHolder[0] = request == null ? MobileRequest.NONE : request;
            activeBrowseRequests.add(requestHolder[0]);
            MobileDiagnostics.info("P13-AA-Resume",
                    "background queue hydration started source=" + sourceId
                            + " videoId=" + savedVideoId);
        } catch (Throwable error) {
            MobileDiagnostics.error("P13-AA-Resume",
                    "background queue hydration threw source=" + sourceId, error);
            completeResumeQueueHydration(sourceId, savedVideoId, null, requestHolder,
                    allowPlaylistCatalogRecovery);
        }
    }

    private void completeResumeQueueHydration(
            String sourceId, String savedVideoId, MobileBrowsePayload payload,
            MobileRequest[] requestHolder, boolean allowPlaylistCatalogRecovery) {
        MobileRequest request = requestHolder[0];
        if (request != null) {
            request.cancel();
            activeBrowseRequests.remove(request);
        }
        if (destroyed || activeItem == null
                || !savedVideoId.equals(activeItem.getId())) {
            return;
        }
        if (payload == null) {
            if (allowPlaylistCatalogRecovery
                    && sourceId.startsWith(ITEM_PREFIX + "playlist:")) {
                loadPlaylistCatalogThenHydrate(sourceId, savedVideoId);
            }
            return;
        }
        try {
            convertPayload(sourceId, payload);
            attachHydratedQueue(sourceId, savedVideoId);
        } catch (Throwable error) {
            MobileDiagnostics.error("P13-AA-Resume",
                    "background queue conversion failed source=" + sourceId, error);
        }
    }

    private void loadPlaylistCatalogThenHydrate(String sourceId, String savedVideoId) {
        final MobileRequest[] requestHolder = { MobileRequest.NONE };
        MobileDiagnostics.info("P13-AA-Resume",
                "warming playlist catalog for queue source=" + sourceId);
        try {
            MobileRequest request = browseRepository.loadBrowse("playlists",
                    new MobileResultCallback<MobileBrowsePayload>() {
                        @Override public void onSuccess(MobileBrowsePayload payload) {
                            mainHandler.post(() -> {
                                finishBrowseRequest(requestHolder[0]);
                                if (destroyed || activeItem == null
                                        || !savedVideoId.equals(activeItem.getId())) {
                                    return;
                                }
                                MobileDiagnostics.info("P13-AA-Resume",
                                        "playlist catalog ready; retry queue source="
                                                + sourceId);
                                hydrateResumeQueueInBackground(
                                        sourceId, savedVideoId, false);
                            });
                        }

                        @Override public void onError(MobileError error) {
                            mainHandler.post(() -> {
                                finishBrowseRequest(requestHolder[0]);
                                MobileDiagnostics.warn("P13-AA-Resume",
                                        "playlist catalog recovery failed source="
                                                + sourceId + " error=" + error);
                            });
                        }
                    });
            requestHolder[0] = request == null ? MobileRequest.NONE : request;
            activeBrowseRequests.add(requestHolder[0]);
        } catch (Throwable error) {
            MobileDiagnostics.error("P13-AA-Resume",
                    "playlist catalog recovery threw source=" + sourceId, error);
        }
    }

    private boolean attachHydratedQueue(String sourceId, String mediaId) {
        for (Map.Entry<String, String> entry : sourceByContainer.entrySet()) {
            if (!sourceId.equals(entry.getValue())) {
                continue;
            }
            List<String> queue = queueByContainer.get(entry.getKey());
            int index = findMediaIndex(queue, mediaId);
            if (index < 0) {
                continue;
            }
            String browserId = queue.get(index);
            MobileMediaItem hydratedItem = mediaByBrowserId.get(browserId);
            activeContainerId = entry.getKey();
            activeQueue = new ArrayList<>(queue);
            activeQueueIndex = index;
            if (hydratedItem != null) {
                activeItem = hydratedItem;
            }
            publishQueue(activeQueue);
            updateMetadata(activeItem, Math.max(lastMetadataDurationMs,
                    activeItem.getDurationMs()));
            updatePlaybackState(lastPlaybackState,
                    lastPlaybackPositionMs, lastPlaybackSpeed);
            savePlaybackSelection(browserId, activeItem, lastPlaybackPositionMs);
            MobileDiagnostics.info("P13-AA-Resume",
                    "background queue attached source=" + sourceId
                            + " size=" + activeQueue.size() + " current=" + index);
            return true;
        }
        MobileDiagnostics.info("P13-AA-Resume",
                "background first page does not contain current item yet source="
                        + sourceId + " videoId=" + mediaId);
        return false;
    }

    private int findMediaIndex(List<String> queue, String mediaId) {
        if (queue == null || mediaId == null) {
            return -1;
        }
        for (int i = 0; i < queue.size(); i++) {
            MobileMediaItem item = mediaByBrowserId.get(queue.get(i));
            if (item != null && mediaId.equals(item.getId())) {
                return i;
            }
        }
        return -1;
    }

    private String findLoadedResumeBrowserId(String savedBrowserId, String savedVideoId) {
        if (savedBrowserId != null && mediaByBrowserId.containsKey(savedBrowserId)) {
            return savedBrowserId;
        }
        if (savedVideoId == null) {
            return null;
        }
        String savedSourceId = resumePrefs == null
                ? null : resumePrefs.getString(PREF_SOURCE_ID, null);
        String savedContainerId = resumePrefs == null
                ? null : resumePrefs.getString(PREF_CONTAINER_ID, null);
        for (Map.Entry<String, MobileMediaItem> entry : mediaByBrowserId.entrySet()) {
            MobileMediaItem item = entry.getValue();
            if (item == null || !savedVideoId.equals(item.getId())) {
                continue;
            }
            if (RadioStationRepository.isRadioMediaId(savedVideoId)) {
                return entry.getKey();
            }
            String candidateContainer = findContainer(entry.getKey());
            String candidateSource = sourceByContainer.get(candidateContainer);
            boolean sameContainer = savedContainerId != null
                    && savedContainerId.equals(candidateContainer);
            boolean sameSource = savedSourceId != null
                    && savedSourceId.equals(candidateSource);
            if (sameContainer || sameSource) {
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
            resumeSourceAttempt = 0;
            resumeSourcePageId = null;
            resumeReason = null;
            MobileDiagnostics.info("P13-AA-Resume",
                    "resume page matched page=" + pageId + " reason=" + reason
                            + " browserId=" + loadedBrowserId + " fromZero=true");
            playResumeBrowserId(loadedBrowserId);
            return;
        }

        if (tryMusicFallback && pageId.equals(resumeSourcePageId)
                && resumeSourceAttempt < MAX_RESUME_SOURCE_ATTEMPTS) {
            int nextAttempt = ++resumeSourceAttempt;
            long delayMs = Math.min(3000L, RESUME_RETRY_BASE_DELAY_MS * nextAttempt);
            MobileDiagnostics.info("P13-AA-Resume",
                    "resume source not ready; retry=" + nextAttempt + "/"
                            + MAX_RESUME_SOURCE_ATTEMPTS + " page=" + pageId
                            + " delay=" + delayMs);
            mainHandler.postDelayed(() -> {
                if (destroyed || resumeAutoplayConsumed || activeItem != null) {
                    resumeLoadInProgress = false;
                    return;
                }
                loadResumePageAndPlay(pageId,
                        resumeReason == null ? reason : resumeReason, true);
            }, delayMs);
            return;
        }

        if (tryMusicFallback) {
            MobileDiagnostics.info("P13-AA-Resume",
                    "resume not in history; trying music videoId=" + savedVideoId);
            loadResumePageAndPlay("music", reason, false);
            return;
        }

        resumeLoadInProgress = false;
        resumeSourceAttempt = 0;
        resumeSourcePageId = null;
        resumeReason = null;
        MobileDiagnostics.info("P13-AA-Resume",
                "resume item not found reason=" + reason + " videoId=" + savedVideoId);
    }

    private void savePlaybackSelection(String browserId, MobileMediaItem item, long positionMs) {
        if (resumePrefs == null || item == null || browserId == null) {
            return;
        }
        String modePrefix = modePrefixFor(item.getId());
        SharedPreferences.Editor editor = resumePrefs.edit()
                .putString(PREF_BROWSER_ID, browserId)
                .putString(PREF_CONTAINER_ID, activeContainerId)
                .putString(PREF_VIDEO_ID, item.getId())
                .putString(PREF_PLAYBACK_ID, activePlaybackMediaId == null
                        ? item.getId() : activePlaybackMediaId)
                .putString(PREF_TITLE, item.getTitle())
                .putString(PREF_SUBTITLE, item.getSubtitle() == null ? "" : item.getSubtitle().toString())
                .putString(PREF_THUMB, item.getThumbnailUrl())
                .putLong(PREF_DURATION, Math.max(0L, item.getDurationMs()))
                .putLong(PREF_POSITION, Math.max(0L, positionMs))
                .putInt(PREF_QUEUE_INDEX, activeQueueIndex)
                .putString(PREF_SOURCE_ID, sourceByContainer.get(activeContainerId));
        putModeSelection(editor, modePrefix, browserId, activeContainerId, item,
                activePlaybackMediaId == null ? item.getId() : activePlaybackMediaId,
                Math.max(0L, positionMs), activeQueueIndex,
                sourceByContainer.get(activeContainerId));
        editor.apply();
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
        String modePrefix = modePrefixFor(activeItem.getId());
        resumePrefs.edit()
                .putLong(PREF_POSITION, safePosition)
                .putLong(PREF_DURATION, Math.max(lastMetadataDurationMs, activeItem.getDurationMs()))
                .putInt(PREF_QUEUE_INDEX, activeQueueIndex)
                .putLong(modeKey(modePrefix, PREF_POSITION), safePosition)
                .putLong(modeKey(modePrefix, PREF_DURATION),
                        Math.max(lastMetadataDurationMs, activeItem.getDurationMs()))
                .putInt(modeKey(modePrefix, PREF_QUEUE_INDEX), activeQueueIndex)
                .apply();
        lastResumeSavePositionMs = safePosition;
    }

    private void migrateCurrentSelectionToModeHistory() {
        if (resumePrefs == null) return;
        String videoId = resumePrefs.getString(PREF_VIDEO_ID, null);
        if (videoId == null || videoId.trim().isEmpty()) return;
        String prefix = modePrefixFor(videoId);
        if (resumePrefs.contains(modeKey(prefix, PREF_VIDEO_ID))) return;
        copyCurrentSelectionToMode(prefix);
    }

    private String modePrefixFor(String mediaId) {
        return RadioStationRepository.isRadioMediaId(mediaId)
                ? PREF_MODE_RADIO_PREFIX : PREF_MODE_MUSIC_PREFIX;
    }

    private static String modeKey(String prefix, String key) {
        return prefix + key;
    }

    private void putModeSelection(SharedPreferences.Editor editor, String prefix,
                                  String browserId, String containerId,
                                  MobileMediaItem item, String playbackMediaId, long positionMs,
                                  int queueIndex, String sourceId) {
        editor.putString(modeKey(prefix, PREF_BROWSER_ID), browserId)
                .putString(modeKey(prefix, PREF_CONTAINER_ID), containerId)
                .putString(modeKey(prefix, PREF_VIDEO_ID), item.getId())
                .putString(modeKey(prefix, PREF_PLAYBACK_ID), playbackMediaId == null
                        ? item.getId() : playbackMediaId)
                .putString(modeKey(prefix, PREF_TITLE), item.getTitle())
                .putString(modeKey(prefix, PREF_SUBTITLE),
                        item.getSubtitle() == null ? "" : item.getSubtitle().toString())
                .putString(modeKey(prefix, PREF_THUMB), item.getThumbnailUrl())
                .putLong(modeKey(prefix, PREF_DURATION), Math.max(0L, item.getDurationMs()))
                .putLong(modeKey(prefix, PREF_POSITION), Math.max(0L, positionMs))
                .putInt(modeKey(prefix, PREF_QUEUE_INDEX), queueIndex)
                .putString(modeKey(prefix, PREF_SOURCE_ID), sourceId);
    }

    private void copyCurrentSelectionToMode(String prefix) {
        if (resumePrefs == null) return;
        SharedPreferences.Editor editor = resumePrefs.edit();
        copyStringPreference(editor, PREF_BROWSER_ID, modeKey(prefix, PREF_BROWSER_ID));
        copyStringPreference(editor, PREF_CONTAINER_ID, modeKey(prefix, PREF_CONTAINER_ID));
        copyStringPreference(editor, PREF_VIDEO_ID, modeKey(prefix, PREF_VIDEO_ID));
        copyStringPreference(editor, PREF_PLAYBACK_ID, modeKey(prefix, PREF_PLAYBACK_ID));
        copyStringPreference(editor, PREF_TITLE, modeKey(prefix, PREF_TITLE));
        copyStringPreference(editor, PREF_SUBTITLE, modeKey(prefix, PREF_SUBTITLE));
        copyStringPreference(editor, PREF_THUMB, modeKey(prefix, PREF_THUMB));
        copyStringPreference(editor, PREF_SOURCE_ID, modeKey(prefix, PREF_SOURCE_ID));
        editor.putLong(modeKey(prefix, PREF_DURATION),
                        resumePrefs.getLong(PREF_DURATION, 0L))
                .putLong(modeKey(prefix, PREF_POSITION),
                        resumePrefs.getLong(PREF_POSITION, 0L))
                .putInt(modeKey(prefix, PREF_QUEUE_INDEX),
                        resumePrefs.getInt(PREF_QUEUE_INDEX, -1))
                .apply();
    }

    private void copyModeSelectionToCurrent(String prefix) {
        SharedPreferences.Editor editor = resumePrefs.edit().remove(PREF_PLAYBACK_ID);
        copyStringPreference(editor, modeKey(prefix, PREF_BROWSER_ID), PREF_BROWSER_ID);
        copyStringPreference(editor, modeKey(prefix, PREF_CONTAINER_ID), PREF_CONTAINER_ID);
        copyStringPreference(editor, modeKey(prefix, PREF_VIDEO_ID), PREF_VIDEO_ID);
        copyStringPreference(editor, modeKey(prefix, PREF_PLAYBACK_ID), PREF_PLAYBACK_ID);
        copyStringPreference(editor, modeKey(prefix, PREF_TITLE), PREF_TITLE);
        copyStringPreference(editor, modeKey(prefix, PREF_SUBTITLE), PREF_SUBTITLE);
        copyStringPreference(editor, modeKey(prefix, PREF_THUMB), PREF_THUMB);
        copyStringPreference(editor, modeKey(prefix, PREF_SOURCE_ID), PREF_SOURCE_ID);
        editor.putLong(PREF_DURATION,
                        resumePrefs.getLong(modeKey(prefix, PREF_DURATION), 0L))
                .putLong(PREF_POSITION,
                        resumePrefs.getLong(modeKey(prefix, PREF_POSITION), 0L))
                .putInt(PREF_QUEUE_INDEX,
                        resumePrefs.getInt(modeKey(prefix, PREF_QUEUE_INDEX), -1))
                .commit();
    }

    private void copyStringPreference(SharedPreferences.Editor editor,
                                      String fromKey, String toKey) {
        editor.putString(toKey, resumePrefs.getString(fromKey, null));
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

        // Engine recreation is asynchronous. A released radio/video player can still post its
        // final snapshot after the user has selected a different source. Never let that stale
        // ENDED/error state advance the newly selected queue.
        if (activeItem != null && !AndroidAutoOfflineRouting.snapshotMatches(
                activePlaybackMediaId, activeItem.getId(), snapshot.getMediaId())) {
            MobileDiagnostics.debug("P13-AA-Playback",
                    "ignore stale snapshot active=" + activeItem.getId()
                            + " playback=" + activePlaybackMediaId
                            + " snapshot=" + snapshot.getMediaId());
            return;
        }

        if (snapshot.isPrepared()) {
            activeItemReachedReady = true;
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
        // Live radio DVR deliberately reports a rolling duration/position so AA can render a seek
        // bar. Being near that live edge must never be interpreted as end-of-track.
        if (activeItem != null && RadioStationRepository.isRadioMediaId(activeItem.getId())) {
            return false;
        }
        if (snapshot == null || activeQueue == null || activeQueue.isEmpty()
                || activeQueueIndex < 0 || activeQueueIndex >= activeQueue.size()
                || !activeItemReachedReady) {
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

        int nextIndex = resolveOfflineAutoAdvanceIndex(resolveAutoNextIndex());
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
        // Radio DVR has a rolling duration that grows continuously until its configured window is
        // full. Publishing brand-new metadata every ~500 ms is wasteful and can make some Android
        // Auto hosts redraw the media card. Quantize only the public radio duration; playback state
        // still carries the precise rolling position.
        long publishedDuration = resolvedDuration;
        if (RadioStationRepository.isRadioMediaId(item.getId()) && publishedDuration > 0L) {
            final long stepMs = 5_000L;
            publishedDuration = Math.max(stepMs, (publishedDuration / stepMs) * stepMs);
        }

        String metadataKey = item.getId() + "|" + item.getTitle() + "|"
                + item.getSubtitle() + "|" + item.getThumbnailUrl() + "|"
                + publishedDuration + "|liked=" + activeLiked;
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

        if (publishedDuration > 0L) {
            metadata.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, publishedDuration);
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
                + "|radio=" + (activeItem != null
                        && RadioStationRepository.isRadioMediaId(activeItem.getId()))
                + "|repeat=" + repeatMode
                + "|shuffle=" + shuffleMode
                + "|autoNext=" + autoNextEnabled
                + "|radioLive=" + radioLiveBucket();
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
                .addCustomAction(createSourceSwitchAction())
                .addCustomAction(createLikeAction());
        if (isActiveRadio() && isRadio2AaEnabled()) {
            builder.addCustomAction(createRadioGoLiveAction());
        }
        builder.addCustomAction(createRestartAction())
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
        boolean radio = RadioStationRepository.isRadioMediaId(activeItem.getId());
        builder.addAction(activeLiked
                        ? android.R.drawable.btn_star_big_on
                        : android.R.drawable.btn_star_big_off,
                radio
                        ? (activeLiked ? "Usuń z ulubionych" : "Dodaj do ulubionych")
                        : (activeLiked ? "Usuń polubienie" : "Lubię to"),
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
        boolean radio = activeItem != null
                && RadioStationRepository.isRadioMediaId(activeItem.getId());
        String label;
        if (radio) {
            label = activeLiked
                    ? "Usuń radio z ulubionych" : "Dodaj radio do ulubionych";
        } else {
            label = activeLiked
                    ? "Polubione — kliknij, aby usunąć" : "Lubię to";
        }
        return new PlaybackStateCompat.CustomAction.Builder(
                activeLiked ? ACTION_UNLIKE : ACTION_LIKE,
                label,
                activeLiked ? R.drawable.ic_auto_like_on : R.drawable.ic_auto_like_off)
                .build();
    }

    private PlaybackStateCompat.CustomAction createSourceSwitchAction() {
        boolean radio = activeItem != null
                && RadioStationRepository.isRadioMediaId(activeItem.getId());
        return new PlaybackStateCompat.CustomAction.Builder(
                ACTION_SWITCH_SOURCE,
                radio ? "Wróć do muzyki" : "Przełącz na radio",
                radio ? android.R.drawable.ic_media_play : android.R.drawable.ic_btn_speak_now)
                .build();
    }

    private PlaybackStateCompat.CustomAction createRadioGoLiveAction() {
        long behindMs = Math.max(0L, lastMetadataDurationMs - lastPlaybackPositionMs);
        boolean showOffset = radioPreferences != null
                && radioPreferences.isLiveOffsetLabelEnabled();
        String label = !showOffset || behindMs <= 2500L
                ? "LIVE" : "LIVE −" + formatShortDuration(behindMs);
        return new PlaybackStateCompat.CustomAction.Builder(
                ACTION_RADIO_GO_LIVE, label, android.R.drawable.ic_media_play)
                .build();
    }

    private void goLiveRadio() {
        if (!isActiveRadio() || playbackRepository == null) return;
        MobileDiagnostics.info("P18-Radio2", "AA go LIVE");
        playbackRepository.seekTo(Long.MAX_VALUE);
        playbackRepository.play();
    }

    private boolean isActiveRadio() {
        return activeItem != null && RadioStationRepository.isRadioMediaId(activeItem.getId());
    }

    private boolean isRadio2AaEnabled() {
        return featureFlags != null && featureFlags.isRadio2Enabled()
                && featureFlags.isRadio2AndroidAutoEnabled()
                && radioPreferences != null
                && radioPreferences.isEnhancedAndroidAutoDirectoryEnabled();
    }

    private int radioLiveBucket() {
        if (!isActiveRadio() || !isRadio2AaEnabled()) return -1;
        long behindMs = Math.max(0L, lastMetadataDurationMs - lastPlaybackPositionMs);
        if (behindMs <= 2500L) return 0;
        return (int) Math.min(99L, behindMs / 15_000L + 1L);
    }

    private static String formatShortDuration(long durationMs) {
        long seconds = Math.max(0L, durationMs) / 1000L;
        long minutes = seconds / 60L;
        long remaining = seconds % 60L;
        return String.format(java.util.Locale.US, "%d:%02d", minutes, remaining);
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

    private void switchPlaybackSource() {
        boolean currentRadio = activeItem != null
                ? RadioStationRepository.isRadioMediaId(activeItem.getId())
                : RadioStationRepository.isRadioMediaId(
                        resumePrefs == null ? null : resumePrefs.getString(PREF_VIDEO_ID, null));
        boolean targetRadio = !currentRadio;
        String targetPrefix = targetRadio ? PREF_MODE_RADIO_PREFIX : PREF_MODE_MUSIC_PREFIX;
        String savedTargetId = resumePrefs == null ? null
                : resumePrefs.getString(modeKey(targetPrefix, PREF_VIDEO_ID), null);

        MobileDiagnostics.info("P14-Radio",
                "switch source from=" + (currentRadio ? "radio" : "music")
                        + " to=" + (targetRadio ? "radio" : "music")
                        + " saved=" + savedTargetId);
        // Preserve the exact return position before replacing the shared current-selection keys.
        if (activeItem != null && activeQueueIndex >= 0) {
            savePlaybackProgress(lastPlaybackPositionMs);
        }
        if (playbackRepository != null) playbackRepository.pause();

        if (savedTargetId != null && !savedTargetId.trim().isEmpty()) {
            copyModeSelectionToCurrent(targetPrefix);
            resetActiveSelectionForSourceSwitch();
            requestResumePlayback("source-switch");
            return;
        }

        if (targetRadio) {
            List<MediaBrowserCompat.MediaItem> stations = createRadioItems(true);
            if (stations.isEmpty()) stations = createRadioItems(false);
            if (!stations.isEmpty()) {
                playBrowserMediaId(stations.get(0).getMediaId());
            } else {
                MobileDiagnostics.warn("P14-Radio",
                        "switch to radio ignored: station cache is empty");
            }
            return;
        }

        String loadedMusic = findFirstLoadedMusicBrowserId();
        if (loadedMusic != null) {
            playBrowserMediaId(loadedMusic);
        } else {
            loadFirstMusicForSourceSwitch();
        }
    }

    private void resetActiveSelectionForSourceSwitch() {
        activeItem = null;
        activePlaybackMediaId = null;
        activeContainerId = null;
        activeQueue = Collections.emptyList();
        activeQueueIndex = -1;
        activeItemReachedReady = false;
        resumeAutoplayConsumed = false;
        resumeLoadInProgress = false;
        resumeSourceAttempt = 0;
        resumeSourcePageId = null;
        resumeReason = null;
        lastAutoAdvancedFromBrowserId = null;
        lastPublishedQueueKey = null;
    }

    private String findFirstLoadedMusicBrowserId() {
        for (Map.Entry<String, List<String>> entry : queueByContainer.entrySet()) {
            String source = sourceByContainer.get(entry.getKey());
            if (!"music".equals(source) || entry.getValue() == null) continue;
            for (String browserId : entry.getValue()) {
                MobileMediaItem item = mediaByBrowserId.get(browserId);
                if (item != null && !RadioStationRepository.isRadioMediaId(item.getId())) {
                    return browserId;
                }
            }
        }
        return null;
    }

    private void loadFirstMusicForSourceSwitch() {
        final MobileRequest[] holder = { MobileRequest.NONE };
        try {
            MobileRequest request = browseRepository.loadBrowse("music",
                    new MobileResultCallback<MobileBrowsePayload>() {
                        @Override public void onSuccess(MobileBrowsePayload payload) {
                            mainHandler.post(() -> {
                                MobileRequest current = holder[0];
                                if (current != null) activeBrowseRequests.remove(current);
                                if (destroyed || payload == null) return;
                                convertPayload("music", payload);
                                String browserId = findFirstLoadedMusicBrowserId();
                                if (browserId != null) {
                                    playBrowserMediaId(browserId);
                                } else {
                                    MobileDiagnostics.warn("P13-AA-Playback",
                                            "source switch: music page has no playable item");
                                }
                            });
                        }

                        @Override public void onError(MobileError error) {
                            mainHandler.post(() -> {
                                MobileRequest current = holder[0];
                                if (current != null) activeBrowseRequests.remove(current);
                                MobileDiagnostics.warn("P13-AA-Playback",
                                        "source switch: music load failed " + error);
                            });
                        }
                    });
            holder[0] = request == null ? MobileRequest.NONE : request;
            activeBrowseRequests.add(holder[0]);
        } catch (Throwable error) {
            MobileDiagnostics.error("P13-AA-Playback",
                    "source switch: music load threw", error);
        }
    }

    private boolean resolveKnownLike(String videoId) {
        if (videoId == null || videoId.trim().isEmpty()) {
            return false;
        }
        if (RadioStationRepository.isRadioMediaId(videoId)) {
            RadioStation station = radioRepository == null ? null
                    : radioRepository.getStation(
                            RadioStationRepository.stationIdFromMediaId(videoId));
            return station != null && station.isFavorite();
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
        if (RadioStationRepository.isRadioMediaId(videoId)) {
            if (radioRepository == null) {
                radioRepository = RadioStationRepository.get(getApplicationContext());
            }
            RadioStation station = radioRepository.getStation(
                    RadioStationRepository.stationIdFromMediaId(videoId));
            boolean currentFavorite = station != null && station.isFavorite();
            if (currentFavorite != liked) {
                radioRepository.toggleFavorite(
                        RadioStationRepository.stationIdFromMediaId(videoId));
            }
            activeLiked = liked;
            updateMetadata(item, lastMetadataDurationMs);
            updatePlaybackState(lastPlaybackState, lastPlaybackPositionMs, lastPlaybackSpeed);
            MobileDiagnostics.info("P14-Radio",
                    "favorite " + (liked ? "added " : "removed ") + videoId);
            return;
        }
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
                // Stage 10: refresh the optional trip-reserve favorites after an account like
                // changes. The planner exits immediately when the feature/user option is disabled.
                OfflineTripReserveService.force(getApplicationContext());
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
