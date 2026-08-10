package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileChannelPayload;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileError;

public interface MobileChannelRepository {
    MobileRequest loadChannel(String channelId, MobileResultCallback<MobileChannelPayload> callback);

    default MobileRequest loadMoreChannel(String channelId,
                                          MobileResultCallback<MobileChannelPayload> callback) {
        if (callback != null) callback.onError(
                MobileError.unconfigured("MobileChannelRepository.loadMoreChannel"));
        return MobileRequest.NONE;
    }
}
