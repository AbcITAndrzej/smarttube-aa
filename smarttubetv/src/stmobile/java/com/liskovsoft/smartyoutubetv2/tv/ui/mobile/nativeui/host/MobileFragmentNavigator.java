package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.host;

import android.view.View;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileNavigator;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioStationRepository;

public final class MobileFragmentNavigator implements MobileNavigator {
    private final MobileNativeActivity host;
    private final FragmentManager manager;

    public MobileFragmentNavigator(MobileNativeActivity host) {
        this.host = host;
        this.manager = host.getSupportFragmentManager();
    }

    private void showTopLevel(Fragment fragment, int selectedItemId) {
        Fragment current = manager.findFragmentById(R.id.mobile_native_fragment_container);
        if (isSameBrowseDestination(current, fragment)) {
            host.updateChrome(selectedItemId, true);
            return;
        }
        manager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        manager.beginTransaction()
                .replace(R.id.mobile_native_fragment_container, fragment, fragment.getClass().getSimpleName())
                .commit();
        host.updateChrome(selectedItemId, true);
    }

    private boolean isSameBrowseDestination(Fragment current, Fragment requested) {
        if (!(current instanceof MobileBrowseFragment)
                || !(requested instanceof MobileBrowseFragment)) return false;
        MobileBrowseFragment currentBrowse = (MobileBrowseFragment) current;
        MobileBrowseFragment requestedBrowse = (MobileBrowseFragment) requested;
        return !currentBrowse.isItemDetail()
                && !requestedBrowse.isItemDetail()
                && currentBrowse.getPageId().equals(requestedBrowse.getPageId());
    }

    private void showDetail(Fragment fragment) {
        manager.beginTransaction()
                .replace(R.id.mobile_native_fragment_container, fragment, fragment.getClass().getSimpleName())
                .addToBackStack(fragment.getClass().getSimpleName())
                .commit();
        host.updateChrome(View.NO_ID, false);
    }

    @Override public void openBrowse(String pageId) {
        int destination = R.id.mobile_nav_home;
        if ("shorts".equals(pageId)) destination = R.id.mobile_nav_shorts;
        else if ("subscriptions".equals(pageId)) destination = R.id.mobile_nav_subscriptions;
        showTopLevel(MobileBrowseFragment.newInstance(pageId), destination);
    }

    @Override public void openBrowseItem(String itemId) {
        showDetail(MobileBrowseFragment.newItemInstance(itemId));
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

    @Override public void openAndroidAutoSettings() {
        showDetail(AndroidAutoSettingsFragment.newInstance());
    }

    @Override public void openRadioSettings() {
        showDetail(RadioSettingsFragment.newInstance());
    }

    @Override public void openRadioPlayback(String stationId) {
        showDetail(MobilePlaybackFragment.newRadioInstance(
                RadioStationRepository.mediaId(stationId)));
    }

    @Override public void openPlayback(String mediaId, long startPositionMs) {
        showDetail(MobilePlaybackFragment.newInstance(mediaId, startPositionMs));
    }

    @Override public void openShortPlayback(String mediaId, long startPositionMs) {
        showDetail(MobilePlaybackFragment.newShortInstance(mediaId, startPositionMs));
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
