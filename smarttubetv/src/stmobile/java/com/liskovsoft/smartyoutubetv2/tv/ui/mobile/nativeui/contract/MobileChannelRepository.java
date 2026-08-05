package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileChannelPayload;

public interface MobileChannelRepository {
    MobileRequest loadChannel(String channelId, MobileResultCallback<MobileChannelPayload> callback);
}
