package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileSearchPayload;
import java.util.List;

public interface MobileSearchRepository {
    MobileRequest search(String query, MobileResultCallback<MobileSearchPayload> callback);
    MobileRequest suggest(String query, MobileResultCallback<List<String>> callback);
}
