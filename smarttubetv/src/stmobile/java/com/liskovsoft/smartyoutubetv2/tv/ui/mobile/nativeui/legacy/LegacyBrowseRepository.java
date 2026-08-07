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
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LegacyBrowseRepository implements MobileBrowseRepository {
    private static final long BROWSE_CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final long PLAYLIST_CACHE_TTL_MS = 30 * 60 * 1000L;
    private final ContentService content;
    private final NotificationsService notifications;
    private final LegacyMediaIndex index;
    private final LegacyMediaMapper mapper;
    private final LegacyErrorMapper errors;
    private final ConcurrentHashMap<String, MobileBrowsePayload> browseCache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> browseCacheTimes =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> browsePrefetches =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BrowseContinuation> browseContinuations =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> emptyReloadCounts =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MobileBrowsePayload> playlistCache =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> playlistCacheTimes =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> playlistBackgroundLoads =
            new ConcurrentHashMap<>();
    private volatile ItemUpdateListener itemUpdateListener;

    public LegacyBrowseRepository(ContentService content, NotificationsService notifications,
                                  LegacyMediaIndex index, LegacyMediaMapper mapper,
                                  LegacyErrorMapper errors) {
        this.content = content;
        this.notifications = notifications;
        this.index = index;
        this.mapper = mapper;
        this.errors = errors;
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
            if (page.source() == LegacyBrowsePage.Source.ROWS) {
                List<MediaGroup> accumulated = new ArrayList<>();
                Disposable d = rows(page).subscribe(
                        groups -> {
                            if (groups != null) accumulated.addAll(groups);
                            BrowseContinuation continuation = new BrowseContinuation(accumulated);
                            browseContinuations.put(page.id(), continuation);
                            MobileBrowsePayload payload = mapBrowsePayload(page, accumulated,
                                    continuation.hasMore());
                            cacheBrowse(page, payload);
                            callback.onSuccess(payload);
                        },
                        e -> { MobileDiagnostics.error("DataBrowse", "rows failed: " + page.id(), e); callback.onError(errors.map(e)); });
                return new RxMobileRequest(d);
            }
            List<MediaGroup> accumulated = new ArrayList<>();
            Disposable d = grid(page).subscribe(
                    group -> {
                        if (group != null) accumulated.add(group);
                        BrowseContinuation continuation = new BrowseContinuation(accumulated);
                        browseContinuations.put(page.id(), continuation);
                        MobileBrowsePayload payload = mapBrowsePayload(page, accumulated,
                                continuation.hasMore() || page == LegacyBrowsePage.SHORTS);
                        cacheBrowse(page, payload);
                        callback.onSuccess(payload);
                    },
                    e -> { MobileDiagnostics.error("DataBrowse", "grid failed: " + page.id(), e); callback.onError(errors.map(e)); });
            return new RxMobileRequest(d);
        } catch (Throwable error) {
            callback.onError(errors.map(error));
            return MobileRequest.NONE;
        }
    }

    @Override public void invalidateBrowse(String pageId) {
        LegacyBrowsePage page = LegacyBrowsePage.from(pageId);
        browseCache.remove(page.id());
        browseCacheTimes.remove(page.id());
        browseContinuations.remove(page.id());
    }

    @Override public MobileRequest loadMoreBrowse(
            String pageId, MobileResultCallback<MobileBrowsePayload> callback) {
        if (callback == null) return MobileRequest.NONE;
        LegacyBrowsePage page = LegacyBrowsePage.from(pageId);
        BrowseContinuation continuation = browseContinuations.get(page.id());
        MobileBrowsePayload current = browseCache.get(page.id());
        if (continuation == null || current == null) {
            return loadBrowse(page.id(), callback);
        }
        int groupIndex = continuation.nextGroupIndex();
        if (groupIndex < 0) {
            if (page == LegacyBrowsePage.SHORTS) {
                return reloadShorts(page, current, callback);
            }
            callback.onSuccess(withHasMore(current, false));
            return MobileRequest.NONE;
        }
        MediaGroup source = continuation.groupAt(groupIndex);
        MobileDiagnostics.debug("DataBrowse", "continue page=" + page.id()
                + " group=" + groupIndex);
        try {
            Disposable disposable = content.continueGroupObserve(source)
                    .subscribeOn(Schedulers.io())
                    .subscribe(nextPage -> {
                        if (nextPage == null) {
                            continuation.markFinished(groupIndex);
                            MobileBrowsePayload done = withHasMore(current,
                                    continuation.hasMore());
                            cacheBrowse(page, done);
                            callback.onSuccess(done);
                            return;
                        }
                        continuation.replace(groupIndex, nextPage);
                        MobileBrowsePayload combined = appendContinuation(
                                page, current, nextPage, groupIndex,
                                continuation.hasMore());
                        cacheBrowse(page, combined);
                        callback.onSuccess(combined);
                    }, error -> {
                        MobileDiagnostics.error("DataBrowse",
                                "continue failed page=" + page.id(), error);
                        callback.onError(errors.map(error));
                    });
            return new RxMobileRequest(disposable);
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
            Observable<MobileBrowsePayload> source = page.source() == LegacyBrowsePage.Source.ROWS
                    ? rows(page).map(groups -> {
                        BrowseContinuation continuation = new BrowseContinuation(groups);
                        browseContinuations.put(page.id(), continuation);
                        return mapBrowsePayload(page, groups, continuation.hasMore());
                    })
                    : grid(page).map(group -> {
                        List<MediaGroup> groups = Collections.singletonList(group);
                        BrowseContinuation continuation = new BrowseContinuation(groups);
                        browseContinuations.put(page.id(), continuation);
                        return mapBrowsePayload(page, groups, continuation.hasMore());
                    });
            source.subscribeOn(Schedulers.io()).take(1).subscribe(
                    payload -> {
                        cacheBrowse(page, payload);
                        browsePrefetches.remove(page.id());
                        MobileDiagnostics.debug("DataBrowse",
                                "prefetch ready page=" + page.id());
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
        if (cached != null && cachedAt != null
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
                        callback.onSuccess(firstPage);
                        loadPlaylistRemainderInBackground(itemId, resolvedItem, group);
                    },
                    e -> { MobileDiagnostics.error("DataBrowse", "item failed: " + itemId, e);
                        callback.onError(errors.map(e)); });
            return new RxMobileRequest(d);
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
        if (cachedAt == null || System.currentTimeMillis() - cachedAt >= BROWSE_CACHE_TTL_MS) {
            browseCache.remove(page.id());
            browseCacheTimes.remove(page.id());
            return null;
        }
        return browseCache.get(page.id());
    }

    private void cacheBrowse(LegacyBrowsePage page, MobileBrowsePayload payload) {
        if (payload == null) return;
        browseCache.put(page.id(), payload);
        browseCacheTimes.put(page.id(), System.currentTimeMillis());
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
            Disposable disposable = grid(page).toList().subscribeOn(Schedulers.io())
                    .subscribe(groups -> {
                        BrowseContinuation nextContinuation = new BrowseContinuation(groups);
                        browseContinuations.put(page.id(), nextContinuation);
                        MobileBrowsePayload fresh = mapBrowsePayload(page, groups, true);
                        MobileBrowsePayload combined = mergeGridPayload(current, fresh, true);
                        int oldCount = countItems(current);
                        int newCount = countItems(combined);
                        int emptyReloads = newCount > oldCount ? 0
                                : emptyReloadCounts.getOrDefault(page.id(), 0) + 1;
                        emptyReloadCounts.put(page.id(), emptyReloads);
                        boolean canRetry = nextContinuation.hasMore() || emptyReloads < 2;
                        combined = withHasMore(combined, canRetry);
                        cacheBrowse(page, combined);
                        MobileDiagnostics.debug("DataBrowse", "shorts reload items="
                                + oldCount + "->" + newCount + " retry=" + canRetry);
                        callback.onSuccess(combined);
                    }, error -> {
                        MobileDiagnostics.error("DataBrowse", "shorts reload failed", error);
                        callback.onError(errors.map(error));
                    });
            return new RxMobileRequest(disposable);
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
            // The legacy Trending endpoint may never emit on current YouTube accounts.
            // Use the reliable personalized Home feed and cache the result so category
            // switches don't rebuild the screen around a visible network wait.
            case TRENDING: return content.getHomeObserve();
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

    private static boolean hasNextPage(MediaGroup group) {
        if (group == null) return false;
        String key = group.getNextPageKey();
        return key != null && !key.trim().isEmpty();
    }

    private static final class BrowseContinuation {
        private final List<MediaGroup> groups = new ArrayList<>();
        private int cursor;

        BrowseContinuation(List<MediaGroup> initial) {
            if (initial != null) groups.addAll(initial);
        }

        synchronized int nextGroupIndex() {
            if (groups.isEmpty()) return -1;
            for (int offset = 0; offset < groups.size(); offset++) {
                int index = (cursor + offset) % groups.size();
                if (hasNextPage(groups.get(index))) {
                    cursor = (index + 1) % groups.size();
                    return index;
                }
            }
            return -1;
        }

        synchronized MediaGroup groupAt(int index) {
            return index < 0 || index >= groups.size() ? null : groups.get(index);
        }

        synchronized void replace(int index, MediaGroup group) {
            if (index >= 0 && index < groups.size()) groups.set(index, group);
        }

        synchronized void markFinished(int index) {
            if (index >= 0 && index < groups.size()) groups.set(index, null);
        }

        synchronized boolean hasMore() {
            for (MediaGroup group : groups) if (hasNextPage(group)) return true;
            return false;
        }
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

    private MobileBrowsePayload loadCompletePlaylist(String itemId, Video item, MediaGroup firstPage) {
        String title = LegacyMediaMapper.safe(item.getTitleFull());
        List<MobileMediaItem> tracks = new ArrayList<>();
        MediaGroup page = firstPage;
        int pageCount = 0;

        // A playlist is returned in pages (often the first page contains only 15 items).
        // Continue until YouTube reports the end, with a defensive cap for malformed tokens.
        while (page != null && pageCount < 100) {
            MobileSection mapped = mapper.map(page, pageCount);
            if (mapped.getItems() != null) {
                tracks.addAll(mapped.getItems());
            }
            pageCount++;
            String nextPageKey = page.getNextPageKey();
            if (nextPageKey == null || nextPageKey.trim().isEmpty()) {
                break;
            }
            page = content.continueGroup(page);
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
        return new MobileBrowsePayload(title, Collections.singletonList(section));
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
                MobileBrowsePayload complete = loadCompletePlaylist(itemId, item, firstPage);
                playlistCache.put(itemId, complete);
                playlistCacheTimes.put(itemId, System.currentTimeMillis());
                ItemUpdateListener listener = itemUpdateListener;
                if (listener != null) {
                    listener.onItemUpdated(itemId, complete);
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
