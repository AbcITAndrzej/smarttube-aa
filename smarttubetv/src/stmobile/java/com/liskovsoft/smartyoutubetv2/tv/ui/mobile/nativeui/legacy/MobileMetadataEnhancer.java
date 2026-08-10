package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import android.content.Context;
import android.os.SystemClock;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.DeArrowData;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.utils.ClickbaitRemover;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.MobileEnhancementPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileDiagnosticsStore;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;

/**
 * Shared asynchronous metadata pass for native mobile Browse/Search/Channel repositories.
 *
 * <p>{@link VideoGroup#from(MediaGroup)} creates fresh {@link Video} wrappers every time a group is
 * mapped. Therefore remote metadata is stored by video id in this class rather than only mutating a
 * transient Video object. {@link LegacyMediaMapper} calls {@link #applyCached(Video)} before it
 * creates each immutable mobile card.</p>
 */
public final class MobileMetadataEnhancer {
    private static final String TAG = "MobileMetadataEnhancer";
    private static final int UNLOCALIZED_MAX_CONCURRENCY = 4;
    private static final int MAX_METADATA_ENTRIES = 2_048;

    private final MediaItemService mediaItemService;
    private final com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData deArrowPreferences;
    private final MainUIData mainUiData;
    private final MobileEnhancementPreferences mobilePreferences;
    private final MobileDiagnosticsStore diagnostics;

    /** Raw enhancement data. Feature switches are evaluated only when a card is mapped. */
    private final ConcurrentHashMap<String, Metadata> metadata = new ConcurrentHashMap<>();
    private final Set<String> originalTitleFetched = Collections.newSetFromMap(
            new ConcurrentHashMap<String, Boolean>());
    private final Object metadataTrimLock = new Object();

    public MobileMetadataEnhancer(Context context) {
        Context app = context.getApplicationContext();
        mediaItemService = YouTubeServiceManager.instance().getMediaItemService();
        deArrowPreferences = com.liskovsoft.smartyoutubetv2.common.prefs.DeArrowData.instance(app);
        mainUiData = MainUIData.instance(app);
        mobilePreferences = new MobileEnhancementPreferences(app);
        diagnostics = MobileDiagnosticsStore.get(app);
    }

    /** Snapshot used by repository caches so a settings change never serves stale card strings. */
    public String preferenceSignature() {
        return (mobilePreferences.isDeArrowNativeListsEnabled() ? "1" : "0")
                + (deArrowPreferences.isReplaceTitlesEnabled() ? "1" : "0")
                + (deArrowPreferences.isReplaceThumbnailsEnabled() ? "1" : "0")
                + (mobilePreferences.isUnlocalizedTitlesNativeListsEnabled() ? "1" : "0")
                + (mainUiData.isUnlocalizedTitlesEnabled() ? "1" : "0")
                + (mobilePreferences.isFallbackThumbnailsNativeListsEnabled() ? "1" : "0")
                + ':' + mainUiData.getThumbQuality();
    }

    /**
     * Applies data already fetched by {@link #enhance(List, Runnable)} to a fresh legacy Video.
     * Nothing is forced: global DeArrow/original-title switches and the mobile-only gates are both
     * respected on every map, so disabling an option immediately stops using cached replacements.
     */
    public void applyCached(Video video) {
        diagnostics.onMetadataCacheSize(metadata.size());
        if (!isEnhanceable(video)) return;
        Metadata cached = metadata.get(video.videoId);

        boolean communityTitles = mobilePreferences.isDeArrowNativeListsEnabled()
                && deArrowPreferences.isReplaceTitlesEnabled();
        boolean communityThumbnails = mobilePreferences.isDeArrowNativeListsEnabled()
                && deArrowPreferences.isReplaceThumbnailsEnabled();
        boolean originalTitles = mobilePreferences.isUnlocalizedTitlesNativeListsEnabled()
                && mainUiData.isUnlocalizedTitlesEnabled()
                && !communityTitles;

        if (cached != null && communityTitles && notEmpty(cached.communityTitle)) {
            video.deArrowTitle = cached.communityTitle;
        } else if (cached != null && originalTitles && notEmpty(cached.originalTitle)) {
            video.deArrowTitle = cached.originalTitle;
        }

        boolean communityThumbnailApplied = cached != null && communityThumbnails
                && notEmpty(cached.communityThumbnailUrl);
        if (communityThumbnailApplied) {
            video.altCardImageUrl = cached.communityThumbnailUrl;
        } else if (mobilePreferences.isFallbackThumbnailsNativeListsEnabled()
                && mainUiData.getThumbQuality() != ClickbaitRemover.THUMB_QUALITY_DEFAULT) {
            // Mirrors VideoCardPresenter: a chosen hq1/hq2/hq3 frame is only a fallback.
            // Live/upcoming/already-alternative thumbnails are left untouched by ClickbaitRemover.
            String fallback = ClickbaitRemover.updateThumbnail(video, mainUiData.getThumbQuality());
            if (notEmpty(fallback)) video.altCardImageUrl = fallback;
        }
    }

    /**
     * Starts optional background enrichment. The original YouTube payload is rendered first; when
     * visible metadata changes, {@code onChanged} asks the repository to remap/publish the page.
     */
    public Disposable enhance(List<MediaGroup> groups, Runnable onChanged) {
        List<String> videoIds = collectVideoIds(groups);
        if (videoIds.isEmpty()) return Disposables.disposed();

        boolean deArrowMobileEnabled = mobilePreferences.isDeArrowNativeListsEnabled();
        boolean communityTitles = deArrowMobileEnabled
                && deArrowPreferences.isReplaceTitlesEnabled();
        boolean communityThumbnails = deArrowMobileEnabled
                && deArrowPreferences.isReplaceThumbnailsEnabled();
        boolean originalTitles = mobilePreferences.isUnlocalizedTitlesNativeListsEnabled()
                && mainUiData.isUnlocalizedTitlesEnabled()
                && !communityTitles;

        List<Completable> tasks = new ArrayList<>();
        AtomicBoolean changed = new AtomicBoolean(false);

        if (communityTitles || communityThumbnails) {
            Completable deArrow = createDeArrowTask(videoIds, changed);
            if (deArrow != null) tasks.add(deArrow);
        }
        if (originalTitles) {
            Completable original = createUnlocalizedTitleTask(videoIds, changed);
            if (original != null) tasks.add(original);
        }
        if (tasks.isEmpty()) return Disposables.disposed();

        final long started = SystemClock.elapsedRealtime();
        final String taskName = (communityTitles || communityThumbnails ? "DeArrow" : "")
                + (originalTitles ? (communityTitles || communityThumbnails ? "+OriginalTitle"
                : "OriginalTitle") : "");
        return Completable.mergeDelayError(tasks)
                .subscribeOn(Schedulers.io())
                .subscribe(() -> {
                    diagnostics.onMetadataCacheSize(metadata.size());
                    diagnostics.onMetadataFetch(taskName, videoIds.size(),
                            SystemClock.elapsedRealtime() - started, true);
                    if (changed.get() && onChanged != null) onChanged.run();
                }, error -> {
                    diagnostics.onMetadataCacheSize(metadata.size());
                    diagnostics.onMetadataFetch(taskName, videoIds.size(),
                            SystemClock.elapsedRealtime() - started, false);
                    // Enhancement must never turn a valid YouTube page into an error screen.
                    MobileDiagnostics.error(TAG, "metadata enrichment failed", error);
                    if (changed.get() && onChanged != null) onChanged.run();
                });
    }

    private Completable createDeArrowTask(List<String> videoIds, AtomicBoolean changed) {
        // Do not suppress a second visible page merely because another page is already fetching
        // the same video. The service layer has its own bounded cache, so duplicate callers are
        // cheap after the first response and every page still receives a completion/remap signal.
        return mediaItemService.getDeArrowDataObserve(videoIds)
                .doOnNext(data -> {
                    if (data == null || !notEmpty(data.getVideoId())) return;
                    Metadata item = metadataFor(data.getVideoId());
                    boolean itemChanged = false;
                    if (notEmpty(data.getTitle()) && !data.getTitle().equals(item.communityTitle)) {
                        item.communityTitle = data.getTitle();
                        itemChanged = true;
                    }
                    if (notEmpty(data.getThumbnailUrl())
                            && !data.getThumbnailUrl().equals(item.communityThumbnailUrl)) {
                        item.communityThumbnailUrl = data.getThumbnailUrl();
                        itemChanged = true;
                    }
                    if (itemChanged) changed.set(true);
                })
                .ignoreElements();
    }

    private Completable createUnlocalizedTitleTask(List<String> videoIds, AtomicBoolean changed) {
        trimOriginalTitleFetchedIfNeeded();
        List<String> pending = new ArrayList<>();
        for (String videoId : videoIds) {
            if (!originalTitleFetched.contains(videoId)) pending.add(videoId);
        }
        if (pending.isEmpty()) return null;

        return Observable.fromIterable(pending)
                .flatMap(videoId -> mediaItemService.getUnlocalizedTitleObserve(videoId)
                                .subscribeOn(Schedulers.io())
                                .doOnNext(title -> {
                                    if (notEmpty(title)) {
                                        Metadata item = metadataFor(videoId);
                                        if (!title.equals(item.originalTitle)) {
                                            item.originalTitle = title;
                                            changed.set(true);
                                        }
                                    }
                                })
                                .doOnComplete(() -> originalTitleFetched.add(videoId))
                                .onErrorResumeNext(error -> {
                                    // Do not mark a network failure as fetched; a later refresh may retry.
                                    return Observable.empty();
                                }),
                        false, UNLOCALIZED_MAX_CONCURRENCY)
                .ignoreElements();
    }

    private Metadata metadataFor(String videoId) {
        Metadata current = metadata.get(videoId);
        if (current != null) return current;
        trimMetadataIfNeeded();
        Metadata created = new Metadata();
        Metadata raced = metadata.putIfAbsent(videoId, created);
        return raced == null ? created : raced;
    }

    private void trimMetadataIfNeeded() {
        if (metadata.size() < MAX_METADATA_ENTRIES) return;
        synchronized (metadataTrimLock) {
            if (metadata.size() < MAX_METADATA_ENTRIES) return;
            // This cache is only a UI accelerator. A bounded reset is preferable to keeping every
            // video id seen during a multi-hour scrolling session for the life of the process.
            metadata.clear();
            originalTitleFetched.clear();
        }
    }

    private void trimOriginalTitleFetchedIfNeeded() {
        if (originalTitleFetched.size() < MAX_METADATA_ENTRIES) return;
        synchronized (metadataTrimLock) {
            if (originalTitleFetched.size() < MAX_METADATA_ENTRIES) return;
            // IDs that legitimately have no oEmbed/original title do not create a Metadata entry.
            // Bound that negative-result set independently so a long scroll cannot grow it forever.
            originalTitleFetched.clear();
        }
    }

    private static List<String> collectVideoIds(List<MediaGroup> groups) {
        if (groups == null || groups.isEmpty()) return Collections.emptyList();
        Set<String> unique = new LinkedHashSet<>();
        for (MediaGroup group : groups) {
            if (group == null) continue;
            VideoGroup videoGroup = VideoGroup.from(group);
            if (videoGroup == null || videoGroup.getVideos() == null) continue;
            for (Video video : videoGroup.getVideos()) {
                if (isEnhanceable(video)) unique.add(video.videoId);
            }
        }
        return unique.isEmpty() ? Collections.emptyList() : new ArrayList<>(unique);
    }

    private static boolean isEnhanceable(Video video) {
        return video != null && notEmpty(video.videoId);
    }

    private static boolean notEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class Metadata {
        volatile String communityTitle;
        volatile String communityThumbnailUrl;
        volatile String originalTitle;
    }
}
