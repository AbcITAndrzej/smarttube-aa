package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.NotificationsService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileBrowsePayload;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileError;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileMediaItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileSection;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LegacyBrowseRepository implements MobileBrowseRepository {
    private static final long BROWSE_CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final long PLAYLIST_CACHE_TTL_MS = 30 * 60 * 1000L;
    private final ContentService content;
    private final NotificationsService notifications;
    private final LegacyMediaIndex index;
    private final LegacyMediaMapper mapper;
    private final LegacyErrorMapper errors;
    private final MobileMetadataEnhancer metadataEnhancer;
    private final ConcurrentHashMap<String, MobileBrowsePayload> browseCache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> browseCacheTimes =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> browseCacheEnhancementSignatures =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> browsePrefetches =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LegacyGroupPaginator> browseContinuations =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> emptyReloadCounts =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MobileBrowsePayload> playlistCache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> playlistCacheTimes =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> playlistCacheEnhancementSignatures =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> playlistBackgroundLoads =
            new ConcurrentHashMap<>();
    private volatile ItemUpdateListener itemUpdateListener;

    public LegacyBrowseRepository(ContentService content, NotificationsService notifications,
                                  LegacyMediaIndex index, LegacyMediaMapper mapper,
                                  LegacyErrorMapper errors) {
        this(content, notifications, index, mapper, errors, null);
    }

    public LegacyBrowseRepository(ContentService content, NotificationsService notifications,
                                  LegacyMediaIndex index, LegacyMediaMapper mapper,
                                  LegacyErrorMapper errors,
                                  MobileMetadataEnhancer metadataEnhancer) {
        this.content = content;
        this.notifications = notifications;
        this.index = index;
        this.mapper = mapper;
        this.errors = errors;
        this.metadataEnhancer = metadataEnhancer;
    }

    @Override public MobileRequest loadBrowse(String pageId, MobileResultCallback<MobileBrowsePayload> callback) {
        if (callback == null) return MobileRequest.NONE;
        LegacyBrowsePage page = LegacyBrowsePage.from(pageId);
        MobileBrowsePayload cached = getCachedBrowse(page);
        if (cached != null) {
            MobileDiagnostics.debug("DataBrowse", "browse cache hit page=" + page.id());
            callback.onSuccess(cached);
            return MobileRequest.NONE;
        }
        MobileDiagnostics.debug("DataBrowse", "load page=" + page.id() + " source=" + page.source());
        try {
            CompositeDisposable request = new CompositeDisposable();
            if (page.source() == LegacyBrowsePage.Source.ROWS) {
                List<MediaGroup> accumulated = new ArrayList<>();
                Disposable d = rows(page).subscribe(
                        groups -> {
                            if (groups != null) accumulated.addAll(groups);
                            LegacyGroupPaginator continuation = new LegacyGroupPaginator(accumulated);
                            browseContinuations.put(page.id(), continuation);
                            publishBrowse(page, accumulated, continuation.hasMore(), callback, request);
                        },
                        e -> { MobileDiagnostics.error("DataBrowse", "rows failed: " + page.id(), e); callback.onError(errors.map(e)); });
                request.add(d);
                return new RxMobileRequest(request);
            }
            List<MediaGroup> accumulated = new ArrayList<>();
            Disposable d = grid(page).subscribe(
                    group -> {
                        if (group != null) accumulated.add(group);
                        LegacyGroupPaginator continuation = new LegacyGroupPaginator(accumulated);
                        browseContinuations.put(page.id(), continuation);
                        publishBrowse(page, accumulated,
                                continuation.hasMore() || page == LegacyBrowsePage.SHORTS,
                                callback, request);
                    },
                    e -> { MobileDiagnostics.error("DataBrowse", "grid failed: " + page.id(), e); callback.onError(errors.map(e)); });
            request.add(d);
            return new RxMobileRequest(request);
        } catch (Throwable error) {
            callback.onError(errors.map(error));
            return MobileRequest.NONE;
        }
    }

    @Override public void invalidateBrowse(String pageId) {
        LegacyBrowsePage page = LegacyBrowsePage.from(pageId);
        browseCache.remove(page.id());
        browseCacheTimes.remove(page.id());
        browseCacheEnhancementSignatures.remove(page.id());
        browseContinuations.remove(page.id());
        emptyReloadCounts.remove(page.id());
    }

    @Override public MobileRequest loadMoreBrowse(
            String pageId, MobileResultCallback<MobileBrowsePayload> callback) {
        if (callback == null) return MobileRequest.NONE;
        LegacyBrowsePage page = LegacyBrowsePage.from(pageId);
        LegacyGroupPaginator continuation = browseContinuations.get(page.id());
        MobileBrowsePayload current = browseCache.get(page.id());
        if (continuation == null || current == null) {
            return loadBrowse(page.id(), callback);
        }
        int groupIndex = continuation.nextSlotIndex();
        if (groupIndex < 0) {
            if (page == LegacyBrowsePage.SHORTS) {
                return reloadShorts(page, current, callback);
            }
            callback.onSuccess(withHasMore(current, false));
            return MobileRequest.NONE;
        }
        MediaGroup source = continuation.sourceAt(groupIndex);
        int beforeCount = countItems(current);
        long startedNanos = System.nanoTime();
        MobileDiagnostics.debug("DataBrowse", "continue page=" + page.id()
                + " group=" + groupIndex + " items=" + beforeCount);
        try {
            AtomicBoolean emitted = new AtomicBoolean(false);
            CompositeDisposable request = new CompositeDisposable();
            Disposable disposable = content.continueGroupObserve(source)
                    .subscribeOn(Schedulers.io())
                    .subscribe(nextPage -> {
                        emitted.set(true);
                        if (nextPage == null) {
                            continuation.markFinished(groupIndex);
                            MobileBrowsePayload done = withHasMore(current,
                                    continuation.hasMore());
                            cacheBrowse(page, done);
                            callback.onSuccess(done);
                            return;
                        }
                        continuation.append(groupIndex, nextPage);
                        MobileBrowsePayload combined = appendContinuation(page, current, nextPage,
                                groupIndex, continuation.hasMore());
                        MobileDiagnostics.info("DataBrowse", "continue ready page=" + page.id()
                                + " items=" + beforeCount + "->" + countItems(combined)
                                + " elapsedMs=" + ((System.nanoTime() - startedNanos) / 1_000_000L));
                        publishContinuation(page, current, nextPage, groupIndex,
                                continuation.hasMore(), callback, request);
                    }, error -> {
                        MobileDiagnostics.error("DataBrowse",
                                "continue failed page=" + page.id(), error);
                        if (page == LegacyBrowsePage.SHORTS) {
                            // Short continuations are more volatile than ordinary browse
                            // pages. A duplicate/expired token is reported by the media
                            // service as an error (often "fromNullable result is null").
                            // Re-fetch the Shorts root and merge only new IDs instead of
                            // terminating infinite scrolling on the first dead token.
                            continuation.markFinished(groupIndex);
                            reloadShorts(page, current, callback);
                            return;
                        }
                        callback.onError(errors.map(error));
                    }, () -> {
                        // Defensive path for ContentService implementations that complete
                        // without emitting a continuation. Recover Shorts by refreshing the
                        // root feed; ordinary pages simply mark the group as finished.
                        if (emitted.get()) return;
                        continuation.markFinished(groupIndex);
                        if (page == LegacyBrowsePage.SHORTS) {
                            MobileDiagnostics.debug("DataBrowse",
                                    "shorts continuation empty; reload feed");
                            reloadShorts(page, current, callback);
                        } else {
                            MobileBrowsePayload done = withHasMore(current,
                                    continuation.hasMore());
                            cacheBrowse(page, done);
                            callback.onSuccess(done);
                        }
                    });
            request.add(disposable);
            return new RxMobileRequest(request);
        } catch (Throwable error) {
            callback.onError(errors.map(error));
            return MobileRequest.NONE;
        }
    }

    @Override public void prefetchBrowse(String pageId) {
        LegacyBrowsePage page = LegacyBrowsePage.from(pageId);
        if (getCachedBrowse(page) != null) return;
        if (browsePrefetches.putIfAbsent(page.id(), Boolean.TRUE) != null) return;
        MobileDiagnostics.debug("DataBrowse", "prefetch page=" + page.id());
        try {
            Observable<List<MediaGroup>> source = page.source() == LegacyBrowsePage.Source.ROWS
                    ? rows(page)
                    : grid(page).map(group -> Collections.singletonList(group));
            source.subscribeOn(Schedulers.io()).take(1).subscribe(
                    groups -> {
                        LegacyGroupPaginator continuation = new LegacyGroupPaginator(groups);
                        browseContinuations.put(page.id(), continuation);
                        boolean hasMore = continuation.hasMore();
                        cacheBrowse(page, mapBrowsePayload(page, groups, hasMore));
                        browsePrefetches.remove(page.id());
                        MobileDiagnostics.debug("DataBrowse",
                                "prefetch ready page=" + page.id());
                        if (metadataEnhancer != null && groups != null && !groups.isEmpty()) {
                            List<MediaGroup> snapshot = new ArrayList<>(groups);
                            metadataEnhancer.enhance(snapshot, () ->
                                    cacheBrowse(page, mapBrowsePayload(page, snapshot, hasMore)));
                        }
                    },
                    error -> {
                        browsePrefetches.remove(page.id());
                        MobileDiagnostics.warn("DataBrowse",
                                "prefetch failed page=" + page.id()
                                        + ": " + error.getMessage());
                    });
        } catch (Throwable error) {
            browsePrefetches.remove(page.id());
            MobileDiagnostics.warn("DataBrowse",
                    "prefetch start failed page=" + page.id()
                            + ": " + error.getMessage());
        }
    }

    @Override public MobileRequest loadItem(String itemId, MobileResultCallback<MobileBrowsePayload> callback) {
        if (callback == null) return MobileRequest.NONE;
        MobileBrowsePayload cached = playlistCache.get(itemId);
        Long cachedAt = playlistCacheTimes.get(itemId);
        String enhancementSignature = metadataEnhancer == null
                ? "" : metadataEnhancer.preferenceSignature();
        String cachedSignature = playlistCacheEnhancementSignatures.get(itemId);
        if (cached != null && cachedAt != null
                && enhancementSignature.equals(cachedSignature)
                && System.currentTimeMillis() - cachedAt < PLAYLIST_CACHE_TTL_MS) {
            MobileDiagnostics.info("DataBrowse", "playlist cache hit id=" + itemId);
            callback.onSuccess(cached);
            return MobileRequest.NONE;
        }
        Video item = index.get(itemId);
        if (item == null && itemId.startsWith("playlist:")) {
            // Playlist cards are indexed in memory while browsing. Android Auto can,
            // however, recreate its service without opening the playlist catalog first.
            // Rebuild the lightweight playlist reference from its persistent YouTube ID
            // so a cold-start resume never depends on that transient index.
            String playlistId = itemId.substring("playlist:".length());
            if (!playlistId.trim().isEmpty()) {
                item = new Video();
                item.playlistId = playlistId;
                item.title = "Playlista";
                index.put(itemId, item);
                MobileDiagnostics.info("DataBrowse",
                        "restored playlist reference id=" + itemId);
            }
        }
        if (item == null) {
            callback.onError(new MobileError(MobileError.Kind.UNAVAILABLE,
                    "Playlist is no longer available", null, true));
            return MobileRequest.NONE;
        }
        final Video resolvedItem = item;
        try {
            CompositeDisposable request = new CompositeDisposable();
            Disposable d = Observable.fromCallable(() -> content.getGroup(
                            resolvedItem.mediaItem != null
                                    ? resolvedItem.mediaItem : resolvedItem.toMediaItem()))
                    .subscribeOn(Schedulers.io()).subscribe(
                    group -> {
                        if (group == null) {
                            callback.onError(new MobileError(MobileError.Kind.UNAVAILABLE,
                                    "Playlist content is empty", null, true));
                            return;
                        }

                        // Return the first page immediately. Completing a large playlist can
                        // require dozens of network requests and must not block Android Auto.
                        MobileBrowsePayload firstPage = mapPlaylistPage(
                                itemId, resolvedItem, group);
                        playlistCache.put(itemId, firstPage);
                        playlistCacheTimes.put(itemId, System.currentTimeMillis());
                        playlistCacheEnhancementSignatures.put(itemId, enhancementSignature);
                        callback.onSuccess(firstPage);

                        if (metadataEnhancer != null) {
                            // Keep metadata warming alive even if the UI leaves after the fast
                            // first-page callback. The cache is still refreshed, while a disposed
                            // request never receives a late UI callback.
                            metadataEnhancer.enhance(Collections.singletonList(group), () -> {
                                MobileBrowsePayload enhanced = mapPlaylistPage(
                                        itemId, resolvedItem, group);
                                playlistCache.put(itemId, enhanced);
                                playlistCacheTimes.put(itemId, System.currentTimeMillis());
                                playlistCacheEnhancementSignatures.put(
                                        itemId, enhancementSignature);
                                if (!request.isDisposed()) callback.onSuccess(enhanced);
                            });
                        }
                        loadPlaylistRemainderInBackground(itemId, resolvedItem, group);
                    },
                    e -> { MobileDiagnostics.error("DataBrowse", "item failed: " + itemId, e);
                        callback.onError(errors.map(e)); });
            request.add(d);
            return new RxMobileRequest(request);
        } catch (Throwable error) {
            callback.onError(errors.map(error));
            return MobileRequest.NONE;
        }
    }

    @Override public void setItemUpdateListener(ItemUpdateListener listener) {
        itemUpdateListener = listener;
    }

    private MobileBrowsePayload getCachedBrowse(LegacyBrowsePage page) {
        Long cachedAt = browseCacheTimes.get(page.id());
        String expectedSignature = metadataEnhancer == null
                ? "" : metadataEnhancer.preferenceSignature();
        String cachedSignature = browseCacheEnhancementSignatures.get(page.id());
        if (cachedAt == null || !expectedSignature.equals(cachedSignature)
                || System.currentTimeMillis() - cachedAt >= BROWSE_CACHE_TTL_MS) {
            browseCache.remove(page.id());
            browseCacheTimes.remove(page.id());
            browseCacheEnhancementSignatures.remove(page.id());
            return null;
        }
        return browseCache.get(page.id());
    }

    private void cacheBrowse(LegacyBrowsePage page, MobileBrowsePayload payload) {
        if (payload == null) return;
        browseCache.put(page.id(), payload);
        browseCacheTimes.put(page.id(), System.currentTimeMillis());
        browseCacheEnhancementSignatures.put(page.id(), metadataEnhancer == null
                ? "" : metadataEnhancer.preferenceSignature());
    }

    /** Render the YouTube payload first, then publish one optional metadata-enhanced refresh. */
    private void publishBrowse(LegacyBrowsePage page, List<MediaGroup> groups, boolean hasMore,
                               MobileResultCallback<MobileBrowsePayload> callback,
                               CompositeDisposable request) {
        List<MediaGroup> snapshot = groups == null
                ? Collections.emptyList() : new ArrayList<>(groups);
        MobileBrowsePayload payload = mapBrowsePayload(page, snapshot, hasMore);
        cacheBrowse(page, payload);
        callback.onSuccess(payload);

        if (metadataEnhancer == null || snapshot.isEmpty() || request == null) return;
        // Enhancement is a cache-warming background task rather than part of the screen request.
        // Cancelling navigation therefore cannot leave a permanently unenhanced cache entry.
        metadataEnhancer.enhance(snapshot, () -> {
            MobileBrowsePayload enhanced = mapBrowsePayload(page, snapshot, hasMore);
            cacheBrowse(page, enhanced);
            if (!request.isDisposed()) callback.onSuccess(enhanced);
        });
    }

    private void publishContinuation(LegacyBrowsePage page, MobileBrowsePayload current,
                                     MediaGroup nextPage, int groupIndex, boolean hasMore,
                                     MobileResultCallback<MobileBrowsePayload> callback,
                                     CompositeDisposable request) {
        MobileBrowsePayload combined = appendContinuation(
                page, current, nextPage, groupIndex, hasMore);
        cacheBrowse(page, combined);
        callback.onSuccess(combined);

        if (metadataEnhancer == null || nextPage == null || request == null) return;
        metadataEnhancer.enhance(Collections.singletonList(nextPage), () -> {
            MobileBrowsePayload enhanced = appendContinuation(
                    page, current, nextPage, groupIndex, hasMore);
            cacheBrowse(page, enhanced);
            if (!request.isDisposed()) callback.onSuccess(enhanced);
        });
    }

    private MobileBrowsePayload mapBrowsePayload(
            LegacyBrowsePage page, List<MediaGroup> groups, boolean hasMore) {
        List<MobileSection> sections = new ArrayList<>();
        if (groups != null) {
            for (int index = 0; index < groups.size(); index++) {
                MediaGroup group = groups.get(index);
                if (group == null) continue;
                MobileSection section = mapper.map(group, index,
                        page == LegacyBrowsePage.SHORTS
                                ? MobileMediaItem.Kind.SHORT : null);
                section = normalizeSection(page, section);
                if (section != null && !section.getItems().isEmpty()) sections.add(section);
            }
        }
        if (page.source() == LegacyBrowsePage.Source.GRID && sections.size() > 1) {
            List<MobileMediaItem> combined = new ArrayList<>();
            for (MobileSection section : sections) combined.addAll(section.getItems());
            MobileSection first = sections.get(0);
            sections = Collections.singletonList(new MobileSection(first.getId(),
                    first.getTitle(), mergeItems(Collections.emptyList(), combined)));
        }
        return new MobileBrowsePayload(page.title(), sections, hasMore);
    }

    private MobileRequest reloadShorts(LegacyBrowsePage page, MobileBrowsePayload current,
                                       MobileResultCallback<MobileBrowsePayload> callback) {
        MobileDiagnostics.debug("DataBrowse", "reload shorts without continuation token");
        try {
            CompositeDisposable request = new CompositeDisposable();
            Disposable disposable = grid(page).toList().subscribeOn(Schedulers.io())
                    .subscribe(groups -> {
                        LegacyGroupPaginator nextContinuation = new LegacyGroupPaginator(groups);
                        browseContinuations.put(page.id(), nextContinuation);
                        int oldCount = countItems(current);
                        MobileBrowsePayload fresh = mapBrowsePayload(page, groups, true);
                        MobileBrowsePayload combined = mergeGridPayload(current, fresh, true);
                        int newCount = countItems(combined);
                        int emptyReloads = newCount > oldCount ? 0
                                : emptyReloadCounts.getOrDefault(page.id(), 0) + 1;
                        emptyReloadCounts.put(page.id(), emptyReloads);
                        boolean canRetry = nextContinuation.hasMore() || emptyReloads < 2;
                        final boolean finalCanRetry = canRetry;
                        combined = withHasMore(combined, finalCanRetry);
                        cacheBrowse(page, combined);
                        MobileDiagnostics.debug("DataBrowse", "shorts reload items="
                                + oldCount + "->" + newCount + " retry=" + finalCanRetry);
                        callback.onSuccess(combined);

                        if (metadataEnhancer != null && groups != null && !groups.isEmpty()) {
                            List<MediaGroup> snapshot = new ArrayList<>(groups);
                            metadataEnhancer.enhance(snapshot, () -> {
                                MobileBrowsePayload enhancedFresh =
                                        mapBrowsePayload(page, snapshot, true);
                                MobileBrowsePayload enhanced = withHasMore(
                                        mergeGridPayload(current, enhancedFresh, true),
                                        finalCanRetry);
                                cacheBrowse(page, enhanced);
                                if (!request.isDisposed()) callback.onSuccess(enhanced);
                            });
                        }
                    }, error -> {
                        MobileDiagnostics.error("DataBrowse", "shorts reload failed", error);
                        callback.onError(errors.map(error));
                    });
            request.add(disposable);
            return new RxMobileRequest(request);
        } catch (Throwable error) {
            callback.onError(errors.map(error));
            return MobileRequest.NONE;
        }
    }

    private MobileBrowsePayload mergeGridPayload(MobileBrowsePayload current,
                                                  MobileBrowsePayload fresh,
                                                  boolean hasMore) {
        if (current == null || current.getSections().isEmpty()) return withHasMore(fresh, hasMore);
        if (fresh == null || fresh.getSections().isEmpty()) return withHasMore(current, hasMore);
        MobileSection first = current.getSections().get(0);
        List<MobileMediaItem> merged = new ArrayList<>(first.getItems());
        for (MobileSection section : fresh.getSections()) merged.addAll(section.getItems());
        MobileSection combined = new MobileSection(first.getId(), first.getTitle(),
                mergeItems(Collections.emptyList(), merged));
        return new MobileBrowsePayload(current.getTitle(),
                Collections.singletonList(combined), hasMore);
    }

    private int countItems(MobileBrowsePayload payload) {
        int count = 0;
        if (payload != null) {
            for (MobileSection section : payload.getSections()) count += section.getItems().size();
        }
        return count;
    }

    private MobileBrowsePayload appendContinuation(
            LegacyBrowsePage page, MobileBrowsePayload current, MediaGroup nextPage,
            int groupIndex, boolean hasMore) {
        MobileSection mapped = mapper.map(nextPage, groupIndex,
                page == LegacyBrowsePage.SHORTS ? MobileMediaItem.Kind.SHORT : null);
        mapped = normalizeSection(page, mapped);
        if (mapped == null || mapped.getItems().isEmpty()) {
            return withHasMore(current, hasMore);
        }
        List<MobileSection> sections = new ArrayList<>(current.getSections());
        if (page.source() == LegacyBrowsePage.Source.GRID && !sections.isEmpty()) {
            MobileSection first = sections.get(0);
            sections.set(0, new MobileSection(first.getId(), first.getTitle(),
                    mergeItems(first.getItems(), mapped.getItems())));
        } else {
            // Home is a vertical feed of shelves. A continuation is appended below the
            // existing feed so loading more never moves the item currently under the finger.
            sections.add(new MobileSection(mapped.getId() + ":more:" + sections.size(),
                    mapped.getTitle(), mapped.getItems()));
        }
        return new MobileBrowsePayload(current.getTitle(), sections, hasMore);
    }

    private MobileSection normalizeSection(LegacyBrowsePage page, MobileSection section) {
        if (section == null) return null;
        List<MobileMediaItem> filtered = new ArrayList<>();
        for (MobileMediaItem item : section.getItems()) {
            if (item == null) continue;
            // The dedicated Shorts screen owns vertical videos. "All" remains a normal
            // video feed and no longer injects a Short between full-length videos.
            if (page == LegacyBrowsePage.HOME
                    && item.getKind() == MobileMediaItem.Kind.SHORT) continue;
            if (page == LegacyBrowsePage.SHORTS
                    && item.getKind() != MobileMediaItem.Kind.SHORT) continue;
            filtered.add(item);
        }
        return new MobileSection(section.getId(), section.getTitle(), filtered);
    }

    private List<MobileMediaItem> mergeItems(
            List<MobileMediaItem> first, List<MobileMediaItem> second) {
        List<MobileMediaItem> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        List<List<MobileMediaItem>> sources = new ArrayList<>();
        sources.add(first);
        sources.add(second);
        for (List<MobileMediaItem> source : sources) {
            if (source == null) continue;
            for (MobileMediaItem item : source) {
                if (item == null || item.getId() == null || !ids.add(item.getId())) continue;
                result.add(item);
            }
        }
        return result;
    }

    private MobileBrowsePayload withHasMore(MobileBrowsePayload payload, boolean hasMore) {
        return new MobileBrowsePayload(payload.getTitle(), payload.getSections(), hasMore);
    }

    private Observable<List<MediaGroup>> rows(LegacyBrowsePage page) {
        switch (page) {
            // Keep this page independent from Home. Mapping Trending to Home made the
            // "New recommendations" chip display byte-for-byte the same feed as "All".
            // Some accounts currently return an empty/failed legacy Trending response;
            // fall back to one personalized recommendation shelf rather than Home itself.
            case TRENDING: return trendingRows();
            case LIVE: return content.getLiveObserve();
            case MUSIC: return content.getMusicObserve();
            case GAMING: return content.getGamingObserve();
            case NEWS: return content.getNewsObserve();
            case SPORTS: return content.getSportsObserve();
            case KIDS: return content.getKidsHomeObserve();
            case HOME:
            default: return content.getHomeObserve();
        }
    }

    private Observable<List<MediaGroup>> trendingRows() {
        Observable<List<MediaGroup>> fallback = content.getRecommendedObserve()
                .filter(group -> group != null)
                .map(Collections::singletonList);
        return content.getTrendingObserve()
                .filter(groups -> groups != null && !groups.isEmpty())
                .switchIfEmpty(fallback)
                .onErrorResumeNext(fallback);
    }

    private Observable<MediaGroup> grid(LegacyBrowsePage page) {
        switch (page) {
            case SHORTS: return content.getShortsObserve();
            case SUBSCRIPTIONS: return content.getSubscriptionsObserve();
            case HISTORY: return content.getHistoryObserve();
            case CHANNELS: return content.getSubscribedChannelsByNewContentObserve();
            case PLAYLISTS: return content.getPlaylistsObserve();
            case MY_VIDEOS: return content.getMyVideosObserve();
            case NOTIFICATIONS: return notifications.getNotificationItemsObserve();
            default: return Observable.error(new IllegalArgumentException("Unsupported grid page: " + page));
        }
    }

    private List<MediaGroup> loadCompletePlaylistGroups(MediaGroup firstPage) {
        List<MediaGroup> pages = new ArrayList<>();
        MediaGroup page = firstPage;
        int pageCount = 0;

        // A playlist is returned in pages (often the first page contains only 15 items).
        // Keep the raw pages so the asynchronous DeArrow/original-title pass can remap the
        // complete playlist instead of enhancing only page one.
        while (page != null && pageCount < 100) {
            pages.add(page);
            pageCount++;
            String nextPageKey = page.getNextPageKey();
            if (nextPageKey == null || nextPageKey.trim().isEmpty()) {
                break;
            }
            page = content.continueGroup(page);
        }
        return pages;
    }

    private MobileBrowsePayload mapCompletePlaylist(
            String itemId, Video item, List<MediaGroup> pages) {
        String title = LegacyMediaMapper.safe(item.getTitleFull());
        List<MobileMediaItem> tracks = new ArrayList<>();
        int pageCount = 0;
        if (pages != null) {
            for (MediaGroup page : pages) {
                if (page == null) continue;
                MobileSection mapped = mapper.map(page, pageCount);
                if (mapped != null && mapped.getItems() != null) {
                    tracks.addAll(mapped.getItems());
                }
                pageCount++;
            }
        }

        MobileDiagnostics.info("DataBrowse", "playlist loaded id=" + itemId
                + " title=" + title + " tracks=" + tracks.size() + " pages=" + pageCount);
        MobileSection section = new MobileSection("playlist:" + itemId, title, tracks);
        return new MobileBrowsePayload(title, Collections.singletonList(section));
    }

    private MobileBrowsePayload mapPlaylistPage(String itemId, Video item, MediaGroup page) {
        String title = LegacyMediaMapper.safe(item.getTitleFull());
        MobileSection mapped = mapper.map(page, 0);
        List<MobileMediaItem> tracks = mapped == null || mapped.getItems() == null
                ? Collections.emptyList() : new ArrayList<>(mapped.getItems());
        MobileDiagnostics.info("DataBrowse", "playlist first page id=" + itemId
                + " title=" + title + " tracks=" + tracks.size());
        MobileSection section = new MobileSection("playlist:" + itemId, title, tracks);
        boolean hasMore = page != null && page.getNextPageKey() != null
                && !page.getNextPageKey().trim().isEmpty();
        return new MobileBrowsePayload(title, Collections.singletonList(section), hasMore);
    }

    private void cachePlaylist(String itemId, MobileBrowsePayload payload, String signature) {
        if (payload == null) return;
        playlistCache.put(itemId, payload);
        playlistCacheTimes.put(itemId, System.currentTimeMillis());
        playlistCacheEnhancementSignatures.put(itemId, signature == null ? "" : signature);
    }

    private void notifyPlaylistUpdated(String itemId, MobileBrowsePayload payload) {
        ItemUpdateListener listener = itemUpdateListener;
        if (listener != null) listener.onItemUpdated(itemId, payload);
    }

    private void loadPlaylistRemainderInBackground(
            String itemId, Video item, MediaGroup firstPage) {
        String nextPageKey = firstPage.getNextPageKey();
        if (nextPageKey == null || nextPageKey.trim().isEmpty()) {
            return;
        }
        if (playlistBackgroundLoads.putIfAbsent(itemId, Boolean.TRUE) != null) {
            return;
        }

        Schedulers.io().scheduleDirect(() -> {
            try {
                List<MediaGroup> pages = loadCompletePlaylistGroups(firstPage);
                String enhancementSignature = metadataEnhancer == null
                        ? "" : metadataEnhancer.preferenceSignature();
                MobileBrowsePayload complete = mapCompletePlaylist(itemId, item, pages);
                cachePlaylist(itemId, complete, enhancementSignature);
                notifyPlaylistUpdated(itemId, complete);

                if (metadataEnhancer != null && !pages.isEmpty()) {
                    // The complete playlist is enhanced as one logical payload. This prevents
                    // the background remainder loader from overwriting the already-enhanced
                    // first page with raw titles/thumbnails.
                    metadataEnhancer.enhance(pages, () -> {
                        MobileBrowsePayload enhanced = mapCompletePlaylist(itemId, item, pages);
                        cachePlaylist(itemId, enhanced, enhancementSignature);
                        notifyPlaylistUpdated(itemId, enhanced);
                    });
                }
            } catch (Throwable error) {
                // The first page remains usable. A later open can retry the background fill.
                MobileDiagnostics.error("DataBrowse",
                        "playlist background load failed: " + itemId, error);
            } finally {
                playlistBackgroundLoads.remove(itemId);
            }
        });
    }
}
