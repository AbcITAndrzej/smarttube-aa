package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileDiagnosticsStore;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Search repository with continuation paging that keeps each result shelf intact. */
public final class LegacySearchRepository implements MobileSearchRepository {
    private final ContentService content;
    private final LegacyMediaMapper mapper;
    private final LegacyErrorMapper errors;
    private final MobileMetadataEnhancer metadataEnhancer;
    private final MobileFeatureFlags featureFlags;
    private final MobileDiagnosticsStore diagnostics;
    private final ConcurrentHashMap<String, LegacyGroupPaginator> paginators = new ConcurrentHashMap<>();
    private static final int MAX_PAGING_SESSIONS = 8;
    private final ConcurrentHashMap<String, MobileSearchPayload> payloads = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> sessionOrder = new ConcurrentLinkedQueue<>();

    public LegacySearchRepository(ContentService content, LegacyMediaMapper mapper,
                                  LegacyErrorMapper errors) {
        this(content, mapper, errors, null, null, null);
    }

    public LegacySearchRepository(ContentService content, LegacyMediaMapper mapper,
                                  LegacyErrorMapper errors,
                                  MobileMetadataEnhancer metadataEnhancer) {
        this(content, mapper, errors, metadataEnhancer, null, null);
    }

    public LegacySearchRepository(ContentService content, LegacyMediaMapper mapper,
                                  LegacyErrorMapper errors,
                                  MobileMetadataEnhancer metadataEnhancer,
                                  MobileFeatureFlags featureFlags,
                                  MobileDiagnosticsStore diagnostics) {
        this.content = content;
        this.mapper = mapper;
        this.errors = errors;
        this.metadataEnhancer = metadataEnhancer;
        this.featureFlags = featureFlags;
        this.diagnostics = diagnostics;
    }

    @Override public MobileRequest search(String query, MobileResultCallback<MobileSearchPayload> callback) {
        if (callback == null) return MobileRequest.NONE;
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            callback.onSuccess(new MobileSearchPayload("", Collections.emptyList()));
            return MobileRequest.NONE;
        }
        MobileDiagnostics.debug("DataSearch", "search queryLength=" + normalized.length());
        try {
            CompositeDisposable request = new CompositeDisposable();
            Disposable d = content.getSearchObserve(normalized, 0)
                    .timeout(30, TimeUnit.SECONDS)
                    .subscribe(groups -> {
                        LegacyGroupPaginator paginator = new LegacyGroupPaginator(groups);
                        rememberSession(normalized, paginator);
                        MobileSearchPayload payload = payload(normalized, paginator);
                        payloads.put(normalized, payload);
                        MobileDiagnostics.debug("DataSearch", "search complete sections="
                                + payload.getSections().size() + " hasMore=" + payload.hasMore());
                        callback.onSuccess(payload);
                        if (metadataEnhancer != null && groups != null && !groups.isEmpty()) {
                            request.add(metadataEnhancer.enhance(groups, () -> {
                                MobileSearchPayload enhanced = payload(normalized, paginator);
                                payloads.put(normalized, enhanced);
                                if (!request.isDisposed()) callback.onSuccess(enhanced);
                            }));
                        }
                    }, e -> {
                        MobileDiagnostics.error("DataSearch", "search failed", e);
                        callback.onError(errors.map(e));
                    });
            request.add(d);
            return new RxMobileRequest(request);
        } catch (Throwable error) {
            callback.onError(errors.map(error));
            return MobileRequest.NONE;
        }
    }

    @Override public MobileRequest loadMoreSearch(
            String query, MobileResultCallback<MobileSearchPayload> callback) {
        if (callback == null) return MobileRequest.NONE;
        String normalized = normalize(query);
        LegacyGroupPaginator paginator = paginators.get(normalized);
        MobileSearchPayload current = payloads.get(normalized);
        if (!pagingEnabled() || paginator == null || current == null) {
            if (current != null) callback.onSuccess(withHasMore(current, false));
            else callback.onError(MobileError.unconfigured("Search continuation unavailable"));
            return MobileRequest.NONE;
        }
        int slot = paginator.nextSlotIndex();
        if (slot < 0) {
            MobileSearchPayload done = withHasMore(current, false);
            payloads.put(normalized, done);
            callback.onSuccess(done);
            return MobileRequest.NONE;
        }
        MediaGroup source = paginator.sourceAt(slot);
        if (source == null) {
            paginator.markFinished(slot);
            MobileSearchPayload done = payload(normalized, paginator);
            payloads.put(normalized, done);
            callback.onSuccess(done);
            return MobileRequest.NONE;
        }
        MobileDiagnostics.debug("DataSearch", "continue queryLength=" + normalized.length()
                + " slot=" + slot);
        if (diagnostics != null) diagnostics.onPaginationRequest("search");
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
                        MobileSearchPayload updated = payload(normalized, paginator);
                        payloads.put(normalized, updated);
                        if (diagnostics != null) diagnostics.onPaginationSuccess(
                                "search", countItems(current), countItems(updated), updated.hasMore());
                        callback.onSuccess(updated);
                        if (next != null && metadataEnhancer != null) {
                            request.add(metadataEnhancer.enhance(Collections.singletonList(next), () -> {
                                MobileSearchPayload enhanced = payload(normalized, paginator);
                                payloads.put(normalized, enhanced);
                                if (!request.isDisposed()) callback.onSuccess(enhanced);
                            }));
                        }
                    }, e -> {
                        if (diagnostics != null) diagnostics.onPaginationError("search", e);
                        MobileDiagnostics.error("DataSearch", "continuation failed", e);
                        callback.onError(errors.map(e));
                    }, () -> {
                        if (emitted.get()) return;
                        paginator.markFinished(slot);
                        MobileSearchPayload updated = payload(normalized, paginator);
                        payloads.put(normalized, updated);
                        if (diagnostics != null) diagnostics.onPaginationSuccess(
                                "search", countItems(current), countItems(updated), updated.hasMore());
                        callback.onSuccess(updated);
                    });
            request.add(d);
            return new RxMobileRequest(request);
        } catch (Throwable error) {
            if (diagnostics != null) diagnostics.onPaginationError("search", error);
            callback.onError(errors.map(error));
            return MobileRequest.NONE;
        }
    }

    @Override public MobileRequest suggest(String query, MobileResultCallback<List<String>> callback) {
        if (callback == null) return MobileRequest.NONE;
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            callback.onSuccess(Collections.emptyList());
            return MobileRequest.NONE;
        }
        MobileDiagnostics.debug("DataSearch", "suggest queryLength=" + normalized.length());
        try {
            Disposable d = content.getSearchTagsObserve(normalized).subscribe(
                    tags -> callback.onSuccess(tags == null ? Collections.emptyList() : tags),
                    e -> {
                        MobileDiagnostics.error("DataSearch", "suggest failed", e);
                        callback.onError(errors.map(e));
                    });
            return new RxMobileRequest(d);
        } catch (Throwable error) {
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

    private MobileSearchPayload payload(String query, LegacyGroupPaginator paginator) {
        boolean hasMore = pagingEnabled() && paginator != null && paginator.hasMore();
        return new MobileSearchPayload(query,
                paginator == null ? Collections.emptyList()
                        : LegacyPagedPayloadMapper.map(mapper, paginator),
                hasMore);
    }

    private boolean pagingEnabled() {
        return featureFlags == null || featureFlags.isSearchPagingEnabled();
    }

    private static MobileSearchPayload withHasMore(MobileSearchPayload payload, boolean hasMore) {
        return new MobileSearchPayload(payload.getQuery(), payload.getSections(), hasMore);
    }

    private static String normalize(String query) {
        return query == null ? "" : query.trim();
    }

    private static int countItems(MobileSearchPayload payload) {
        int count = 0;
        if (payload != null) for (MobileSection section : payload.getSections()) {
            count += section.getItems().size();
        }
        return count;
    }
}
