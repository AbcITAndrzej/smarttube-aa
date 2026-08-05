package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileError;

/** Callback boundary between existing SmartTube repositories and the native mobile UI. */
public interface MobileResultCallback<T> {
    void onSuccess(T value);
    void onError(MobileError error);
}
