package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileDiagnosticsStore;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Channel repository with continuation paging for long uploads/playlists shelves. */
public final class LegacyChannelRepository implements MobileChannelRepository {
    private final ContentService content;
    private final LegacyMediaIndex index;
    private final LegacyMediaMapper mapper;
    private final LegacyErrorMapper errors;
    private final MobileMetadataEnhancer metadataEnhancer;
    private final MobileFeatureFlags featureFlags;
    private final MobileDiagnosticsStore diagnostics;
    private final ConcurrentHashMap<String, LegacyGroupPaginator> paginators = new ConcurrentHashMap<>();
    private static final int MAX_PAGING_SESSIONS = 8;
    private final ConcurrentHashMap<String, MobileChannelPayload> payloads = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> sessionOrder = new ConcurrentLinkedQueue<>();

    public LegacyChannelRepository(ContentService content, LegacyMediaIndex index,
                                   LegacyMediaMapper mapper, LegacyErrorMapper errors) {
        this(content, index, mapper, errors, null, null, null);
    }

    public LegacyChannelRepository(ContentService content, LegacyMediaIndex index,
                                   LegacyMediaMapper mapper, LegacyErrorMapper errors,
                                   MobileMetadataEnhancer metadataEnhancer) {
        this(content, index, mapper, errors, metadataEnhancer, null, null);
    }

    public LegacyChannelRepository(ContentService content, LegacyMediaIndex index,
                                   LegacyMediaMapper mapper, LegacyErrorMapper errors,
                                   MobileMetadataEnhancer metadataEnhancer,
                                   MobileFeatureFlags featureFlags,
                                   MobileDiagnosticsStore diagnostics) {
        this.content = content;
        this.index = index;
        this.mapper = mapper;
        this.errors = errors;
        this.metadataEnhancer = metadataEnhancer;
        this.featureFlags = featureFlags;
        this.diagnostics = diagnostics;
    }

    @Override public MobileRequest loadChannel(String channelId,
                                               MobileResultCallback<MobileChannelPayload> callback) {
        if (callback == null) return MobileRequest.NONE;
        if (channelId == null || channelId.trim().isEmpty()) {
            callback.onError(new MobileError(MobileError.Kind.PARSING,
                    "Missing channel id", null, false));
            return MobileRequest.NONE;
        }
        String rawId = rawId(channelId);
        Video indexed = index.get(channelId);
        MobileDiagnostics.debug("DataChannel", "load channel=" + rawId
                + " indexed=" + (indexed != null));
        try {
            io.reactivex.Observable<List<MediaGroup>> source = indexed != null && indexed.mediaItem != null
                    ? content.getChannelObserve(indexed.mediaItem)
                    : content.getChannelObserve(rawId);
            CompositeDisposable request = new CompositeDisposable();
            Disposable d = source.subscribe(groups -> {
                        LegacyGroupPaginator paginator = new LegacyGroupPaginator(groups);
                        rememberSession(rawId, paginator);
                        MobileChannelPayload value = payload(rawId, paginator);
                        payloads.put(rawId, value);
                        callback.onSuccess(value);
                        if (metadataEnhancer != null && groups != null && !groups.isEmpty()) {
                            request.add(metadataEnhancer.enhance(groups, () -> {
                                MobileChannelPayload enhanced = payload(rawId, paginator);
                                payloads.put(rawId, enhanced);
                                if (!request.isDisposed()) callback.onSuccess(enhanced);
                            }));
                        }
                    }, e -> {
                        MobileDiagnostics.error("DataChannel", "channel failed: " + rawId, e);
                        callback.onError(errors.map(e));
                    });
            request.add(d);
            return new RxMobileRequest(request);
        } catch (Throwable error) {
            callback.onError(errors.map(error));
            return MobileRequest.NONE;
        }
    }

    @Override public MobileRequest loadMoreChannel(
            String channelId, MobileResultCallback<MobileChannelPayload> callback) {
        if (callback == null) return MobileRequest.NONE;
        String rawId = rawId(channelId);
        LegacyGroupPaginator paginator = paginators.get(rawId);
        MobileChannelPayload current = payloads.get(rawId);
        if (!pagingEnabled() || paginator == null || current == null) {
            if (current != null) callback.onSuccess(withHasMore(current, false));
            else callback.onError(MobileError.unconfigured("Channel continuation unavailable"));
            return MobileRequest.NONE;
        }
        int slot = paginator.nextSlotIndex();
        if (slot < 0) {
            MobileChannelPayload done = withHasMore(current, false);
            payloads.put(rawId, done);
            callback.onSuccess(done);
            return MobileRequest.NONE;
        }
        MediaGroup source = paginator.sourceAt(slot);
        if (source == null) {
            paginator.markFinished(slot);
            MobileChannelPayload updated = payload(rawId, paginator);
            payloads.put(rawId, updated);
            callback.onSuccess(updated);
            return MobileRequest.NONE;
        }
        MobileDiagnostics.debug("DataChannel", "continue channel=" + rawId + " slot=" + slot);
        if (diagnostics != null) diagnostics.onPaginationRequest("channel");
        try {
            CompositeDisposable request = new CompositeDisposable();
            AtomicBoolean emitted = new AtomicBoolean(false);
            Disposable d = content.continueGroupObserve(source)
                    .subscribeOn(Schedulers.io())
                    .timeout(30, TimeUnit.SECONDS)
                    .subscribe(next -> {
                        emitted.set(true);
                        if (next == null) paginator.markFinished(slot);
                        else paginator.append(slot, next);
                        MobileChannelPayload updated = payload(rawId, paginator);
                        payloads.put(rawId, updated);
                        if (diagnostics != null) diagnostics.onPaginationSuccess(
                                "channel", countItems(current), countItems(updated), updated.hasMore());
                        callback.onSuccess(updated);
                        if (next != null && metadataEnhancer != null) {
                            request.add(metadataEnhancer.enhance(Collections.singletonList(next), () -> {
                                MobileChannelPayload enhanced = payload(rawId, paginator);
                                payloads.put(rawId, enhanced);
                                if (!request.isDisposed()) callback.onSuccess(enhanced);
                            }));
                        }
                    }, e -> {
                        if (diagnostics != null) diagnostics.onPaginationError("channel", e);
                        MobileDiagnostics.error("DataChannel", "continuation failed: " + rawId, e);
                        callback.onError(errors.map(e));
                    }, () -> {
                        if (emitted.get()) return;
                        paginator.markFinished(slot);
                        MobileChannelPayload updated = payload(rawId, paginator);
                        payloads.put(rawId, updated);
                        if (diagnostics != null) diagnostics.onPaginationSuccess(
                                "channel", countItems(current), countItems(updated), updated.hasMore());
                        callback.onSuccess(updated);
                    });
            request.add(d);
            return new RxMobileRequest(request);
        } catch (Throwable error) {
            if (diagnostics != null) diagnostics.onPaginationError("channel", error);
            callback.onError(errors.map(error));
            return MobileRequest.NONE;
        }
    }

    private void rememberSession(String key, LegacyGroupPaginator paginator) {
        if (!paginators.containsKey(key)) sessionOrder.offer(key);
        paginators.put(key, paginator);
        while (sessionOrder.size() > MAX_PAGING_SESSIONS) {
            String oldest = sessionOrder.poll();
            if (oldest == null || oldest.equals(key)) continue;
            paginators.remove(oldest);
            payloads.remove(oldest);
        }
    }

    private MobileChannelPayload payload(String channelId, LegacyGroupPaginator paginator) {
        Video channel = index.get(channelId);
        if (channel == null) channel = index.get("channel:" + channelId);
        List<MobileSection> sections = paginator == null ? Collections.emptyList()
                : LegacyPagedPayloadMapper.map(mapper, paginator);
        String title = channel != null ? LegacyMediaMapper.safe(channel.getTitleFull()) : channelId;
        String description = channel != null ? LegacyMediaMapper.safe(channel.description) : "";
        String avatar = channel != null ? channel.getCardImageUrl() : null;
        String banner = channel != null ? channel.bgImageUrl : null;
        String subscribers = channel != null ? LegacyMediaMapper.safe(channel.subscriberCount) : "";
        boolean subscribed = channel != null && channel.isSubscribed;
        if ((title == null || title.isEmpty()) && !sections.isEmpty()) title = sections.get(0).getTitle();
        return new MobileChannelPayload(channelId, title, description, avatar, banner,
                subscribers, subscribed, sections,
                pagingEnabled() && paginator != null && paginator.hasMore());
    }

    private boolean pagingEnabled() {
        return featureFlags == null || featureFlags.isChannelPagingEnabled();
    }

    private static String rawId(String channelId) {
        if (channelId == null) return "";
        return channelId.startsWith("channel:") ? channelId.substring(8) : channelId;
    }

    private static MobileChannelPayload withHasMore(MobileChannelPayload payload, boolean hasMore) {
        return new MobileChannelPayload(payload.getChannelId(), payload.getTitle(),
                payload.getDescription(), payload.getAvatarUrl(), payload.getBannerUrl(),
                payload.getSubscriberText(), payload.isSubscribed(), payload.getSections(), hasMore);
    }

    private static int countItems(MobileChannelPayload payload) {
        int count = 0;
        if (payload != null) for (MobileSection section : payload.getSections()) {
            count += section.getItems().size();
        }
        return count;
    }
}
