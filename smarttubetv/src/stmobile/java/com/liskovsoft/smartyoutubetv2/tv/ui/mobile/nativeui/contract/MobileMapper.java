package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

/** Converts an existing SmartTube domain object into a Leanback-free mobile UI model. */
public interface MobileMapper<S, T> {
    T map(S source) throws Exception;
}
