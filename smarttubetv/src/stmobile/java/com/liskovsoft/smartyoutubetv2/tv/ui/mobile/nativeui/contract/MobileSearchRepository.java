package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileError;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileSearchPayload;
import java.util.List;

public interface MobileSearchRepository {
    MobileRequest search(String query, MobileResultCallback<MobileSearchPayload> callback);

    default MobileRequest loadMoreSearch(String query,
                                         MobileResultCallback<MobileSearchPayload> callback) {
        if (callback != null) callback.onError(
                MobileError.unconfigured("MobileSearchRepository.loadMoreSearch"));
        return MobileRequest.NONE;
    }

    MobileRequest suggest(String query, MobileResultCallback<List<String>> callback);
}
