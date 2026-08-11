package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.other.SubtitleManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobilePlaybackRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobilePlayerViewBinder;

public final class LegacyMobilePlayerViewBinder implements MobilePlayerViewBinder {
    @Override public Binding bind(ViewGroup container, MobilePlaybackRepository repository) {
        if (!(repository instanceof LegacyMobilePlaybackRepository)) {
            throw new IllegalArgumentException("Repository must be LegacyMobilePlaybackRepository");
        }
        LegacyMobilePlaybackRepository bridge = (LegacyMobilePlaybackRepository) repository;
        PlayerView playerView = new PlayerView(container.getContext());
        playerView.setUseController(false);
        // PlayerView owns the subtitle output. SubtitleManager applies the same persisted
        // colour, size and typeface settings that the classic player uses and observes live
        // changes made from the mobile in-player dialog.
        SubtitleManager subtitleManager = new SubtitleManager(playerView.getSubtitleView());
        container.removeAllViews();
        container.addView(playerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bridge.attachSurface(container.getContext(), playerView);
        return () -> {
            subtitleManager.release();
            bridge.detachSurface(playerView);
            if (playerView.getParent() == container) container.removeView(playerView);
        };
    }
}
