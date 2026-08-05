package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileMapper;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileResultCallback;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileError;

/** Reusable bridge for mapping callbacks from existing repositories into mobile models. */
public final class MobileMappedResultCallback<S, T> implements MobileResultCallback<S> {
    private final MobileMapper<S, T> mapper;
    private final MobileResultCallback<T> target;

    public MobileMappedResultCallback(MobileMapper<S, T> mapper, MobileResultCallback<T> target) {
        if (mapper == null) throw new IllegalArgumentException("mapper == null");
        if (target == null) throw new IllegalArgumentException("target == null");
        this.mapper = mapper;
        this.target = target;
    }

    @Override public void onSuccess(S value) {
        try {
            target.onSuccess(mapper.map(value));
        } catch (Exception error) {
            target.onError(new MobileError(MobileError.Kind.PARSING,
                    "Unable to map SmartTube data to the mobile model", error, false));
        }
    }

    @Override public void onError(MobileError error) {
        target.onError(error);
    }
}
