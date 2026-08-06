package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileBrowsePayload;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileError;

public interface MobileBrowseRepository {
    interface ItemUpdateListener {
        void onItemUpdated(String itemId, MobileBrowsePayload payload);
    }

    MobileRequest loadBrowse(String pageId, MobileResultCallback<MobileBrowsePayload> callback);

    default void invalidateBrowse(String pageId) {
    }

    /** Warms selected pages without changing the currently visible screen. */
    default void prefetchBrowse(String pageId) {
    }

    default MobileRequest loadItem(String itemId, MobileResultCallback<MobileBrowsePayload> callback) {
        if (callback != null) callback.onError(MobileError.unconfigured("MobileBrowseRepository.loadItem"));
        return MobileRequest.NONE;
    }

    default void setItemUpdateListener(ItemUpdateListener listener) {
    }
}
