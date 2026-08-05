package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import io.reactivex.disposables.Disposable;
import java.util.List;

public final class LegacyChannelRepository implements MobileChannelRepository {
    private final ContentService content;
    private final LegacyMediaIndex index;
    private final LegacyMediaMapper mapper;
    private final LegacyErrorMapper errors;

    public LegacyChannelRepository(ContentService content, LegacyMediaIndex index,
                                   LegacyMediaMapper mapper, LegacyErrorMapper errors) {
        this.content = content;
        this.index = index;
        this.mapper = mapper;
        this.errors = errors;
    }

    @Override public MobileRequest loadChannel(String channelId, MobileResultCallback<MobileChannelPayload> callback) {
        if (callback == null) return MobileRequest.NONE;
        if (channelId == null || channelId.trim().isEmpty()) {
            callback.onError(new MobileError(MobileError.Kind.PARSING, "Missing channel id", null, false));
            return MobileRequest.NONE;
        }
        String rawId = channelId.startsWith("channel:") ? channelId.substring(8) : channelId;
        Video indexed = index.get(channelId);
        MobileDiagnostics.debug("DataChannel", "load channel=" + rawId + " indexed=" + (indexed != null));
        try {
            io.reactivex.Observable<List<MediaGroup>> source = indexed != null && indexed.mediaItem != null
                    ? content.getChannelObserve(indexed.mediaItem)
                    : content.getChannelObserve(rawId);
            Disposable d = source.subscribe(
                    groups -> callback.onSuccess(payload(rawId, groups)),
                    e -> { MobileDiagnostics.error("DataChannel", "channel failed: " + rawId, e); callback.onError(errors.map(e)); });
            return new RxMobileRequest(d);
        } catch (Throwable error) {
            callback.onError(errors.map(error));
            return MobileRequest.NONE;
        }
    }

    private MobileChannelPayload payload(String channelId, List<MediaGroup> groups) {
        Video channel = index.get(channelId);
        List<MobileSection> sections = mapper.mapGroups(groups);
        String title = channel != null ? LegacyMediaMapper.safe(channel.getTitleFull()) : channelId;
        String description = channel != null ? LegacyMediaMapper.safe(channel.description) : "";
        String avatar = channel != null ? channel.getCardImageUrl() : null;
        String banner = channel != null ? channel.bgImageUrl : null;
        String subscribers = channel != null ? LegacyMediaMapper.safe(channel.subscriberCount) : "";
        boolean subscribed = channel != null && channel.isSubscribed;
        if ((title == null || title.isEmpty()) && !sections.isEmpty()) title = sections.get(0).getTitle();
        return new MobileChannelPayload(channelId, title, description, avatar, banner,
                subscribers, subscribed, sections);
    }
}
