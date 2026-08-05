package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.host;

import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileNavigator;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment.*;

public final class MobileFragmentNavigator implements MobileNavigator {
    private final MobileNativeActivity host;
    private final FragmentManager manager;

    public MobileFragmentNavigator(MobileNativeActivity host) {
        this.host = host;
        this.manager = host.getSupportFragmentManager();
    }

    private void showTopLevel(Fragment fragment, int selectedItemId) {
        manager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        manager.beginTransaction()
                .replace(R.id.mobile_native_fragment_container, fragment, fragment.getClass().getSimpleName())
                .commit();
        host.updateChrome(selectedItemId, true);
    }

    private void showDetail(Fragment fragment) {
        manager.beginTransaction()
                .replace(R.id.mobile_native_fragment_container, fragment, fragment.getClass().getSimpleName())
                .addToBackStack(fragment.getClass().getSimpleName())
                .commit();
        host.updateChrome(View.NO_ID, false);
    }

    @Override public void openBrowse(String pageId) {
        showTopLevel(MobileBrowseFragment.newInstance(pageId), R.id.mobile_nav_home);
    }

    @Override public void openChannel(String channelId) {
        showDetail(MobileChannelFragment.newInstance(channelId));
    }

    @Override public void openSearch(String initialQuery) {
        showTopLevel(MobileSearchFragment.newInstance(initialQuery), R.id.mobile_nav_search);
    }

    @Override public void openSettings() {
        showTopLevel(MobileSettingsFragment.newInstance(), R.id.mobile_nav_settings);
    }

    @Override public void openPlayback(String mediaId, long startPositionMs) {
        showDetail(MobilePlaybackFragment.newInstance(mediaId, startPositionMs));
    }

    @Override public void goBack() {
        if (!manager.popBackStackImmediate()) host.finish();
        else syncChromeWithCurrentFragment();
    }

    void syncChromeWithCurrentFragment() {
        Fragment fragment = manager.findFragmentById(R.id.mobile_native_fragment_container);
        int destination = host.destinationFor(fragment);
        host.updateChrome(destination, destination != View.NO_ID);
    }
}
