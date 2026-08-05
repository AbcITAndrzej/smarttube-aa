package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core;

import androidx.fragment.app.Fragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileNavigator;

public final class MobileFragmentSupport {
    private MobileFragmentSupport() {}

    public static MobileNavigator navigator(Fragment fragment) {
        if (!(fragment.requireActivity() instanceof MobileNavigatorOwner)) {
            throw new IllegalStateException("Host must implement MobileNavigatorOwner");
        }
        return ((MobileNavigatorOwner) fragment.requireActivity()).getMobileNavigator();
    }
}
