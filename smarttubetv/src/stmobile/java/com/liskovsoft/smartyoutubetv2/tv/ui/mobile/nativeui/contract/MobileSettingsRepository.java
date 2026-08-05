package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileSettingItem;
import java.util.List;

public interface MobileSettingsRepository {
    MobileRequest loadSettings(MobileResultCallback<List<MobileSettingItem>> callback);
    MobileRequest updateSetting(String settingId, String value,
                                MobileResultCallback<MobileSettingItem> callback);
}
