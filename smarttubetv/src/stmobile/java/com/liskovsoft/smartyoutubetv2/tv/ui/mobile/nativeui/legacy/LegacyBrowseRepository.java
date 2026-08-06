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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class LegacyBrowseRepository implements MobileBrowseRepository {
    private static final long PLAYLIST_CACHE_TTL_MS = 30 * 60 * 1000L;
    private final ContentService content;
    private final NotificationsService notifications;
    private final LegacyMediaIndex index;
    private final LegacyMediaMapper mapper;
    private final LegacyErrorMapper errors;
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
        MobileDiagnostics.debug("DataBrowse", "load page=" + page.id() + " source=" + page.source());
        try {
            if (page.source() == LegacyBrowsePage.Source.ROWS) {
                Disposable d = rows(page).subscribe(
                        groups -> callback.onSuccess(new MobileBrowsePayload(page.title(), mapper.mapGroups(groups))),
                        e -> { MobileDiagnostics.error("DataBrowse", "rows failed: " + page.id(), e); callback.onError(errors.map(e)); });
                return new RxMobileRequest(d);
            }
            Disposable d = grid(page).subscribe(
                    group -> callback.onSuccess(new MobileBrowsePayload(page.title(), mapper.mapSingle(group, page.title()))),
                    e -> { MobileDiagnostics.error("DataBrowse", "grid failed: " + page.id(), e); callback.onError(errors.map(e)); });
            return new RxMobileRequest(d);
        } catch (Throwable error) {
            callback.onError(errors.map(error));
            return MobileRequest.NONE;
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
        if (item == null) {
            callback.onError(new MobileError(MobileError.Kind.UNAVAILABLE,
                    "Playlist is no longer available", null, true));
            return MobileRequest.NONE;
        }
        try {
            Disposable d = Observable.fromCallable(() -> content.getGroup(
                            item.mediaItem != null ? item.mediaItem : item.toMediaItem()))
                    .subscribeOn(Schedulers.io()).subscribe(
                    group -> {
                        if (group == null) {
                            callback.onError(new MobileError(MobileError.Kind.UNAVAILABLE,
                                    "Playlist content is empty", null, true));
                            return;
                        }

                        // Return the first page immediately. Completing a large playlist can
                        // require dozens of network requests and must not block Android Auto.
                        MobileBrowsePayload firstPage = mapPlaylistPage(itemId, item, group);
                        playlistCache.put(itemId, firstPage);
                        playlistCacheTimes.put(itemId, System.currentTimeMillis());
                        callback.onSuccess(firstPage);
                        loadPlaylistRemainderInBackground(itemId, item, group);
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

    private Observable<List<MediaGroup>> rows(LegacyBrowsePage page) {
        switch (page) {
            case TRENDING: return content.getTrendingObserve();
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
