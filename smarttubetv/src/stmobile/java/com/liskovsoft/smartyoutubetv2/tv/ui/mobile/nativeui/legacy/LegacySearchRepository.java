package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import io.reactivex.disposables.Disposable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class LegacySearchRepository implements MobileSearchRepository {
    private final ContentService content;
    private final LegacyMediaMapper mapper;
    private final LegacyErrorMapper errors;

    public LegacySearchRepository(ContentService content, LegacyMediaMapper mapper, LegacyErrorMapper errors) {
        this.content = content;
        this.mapper = mapper;
        this.errors = errors;
    }

    @Override public MobileRequest search(String query, MobileResultCallback<MobileSearchPayload> callback) {
        if (callback == null) return MobileRequest.NONE;
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            callback.onSuccess(new MobileSearchPayload("", Collections.emptyList()));
            return MobileRequest.NONE;
        }
        MobileDiagnostics.debug("DataSearch", "search queryLength=" + normalized.length());
        try {
            Disposable d = content.getSearchObserve(normalized, 0)
                    .timeout(30, TimeUnit.SECONDS)
                    .subscribe(
                    groups -> {
                        List<MobileSection> sections = mapper.mapGroups(groups);
                        MobileDiagnostics.debug("DataSearch", "search complete sections=" + sections.size());
                        callback.onSuccess(new MobileSearchPayload(normalized, sections));
                    },
                    e -> { MobileDiagnostics.error("DataSearch", "search failed", e); callback.onError(errors.map(e)); });
            return new RxMobileRequest(d);
        } catch (Throwable error) {
            callback.onError(errors.map(error));
            return MobileRequest.NONE;
        }
    }

    @Override public MobileRequest suggest(String query, MobileResultCallback<List<String>> callback) {
        if (callback == null) return MobileRequest.NONE;
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            callback.onSuccess(Collections.emptyList());
            return MobileRequest.NONE;
        }
        MobileDiagnostics.debug("DataSearch", "suggest queryLength=" + normalized.length());
        try {
            Disposable d = content.getSearchTagsObserve(normalized).subscribe(
                    tags -> callback.onSuccess(tags == null ? Collections.emptyList() : tags),
                    e -> { MobileDiagnostics.error("DataSearch", "suggest failed", e); callback.onError(errors.map(e)); });
            return new RxMobileRequest(d);
        } catch (Throwable error) {
            callback.onError(errors.map(error));
            return MobileRequest.NONE;
        }
    }
}
