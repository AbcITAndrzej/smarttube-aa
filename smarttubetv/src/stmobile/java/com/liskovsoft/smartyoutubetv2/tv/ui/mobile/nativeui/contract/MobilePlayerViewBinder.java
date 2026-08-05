package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

import android.view.ViewGroup;

/**
 * Attaches the real ExoPlayer/SmartTube video surface to a mobile container.
 * Part 5 ships only the boundary; the legacy player adapter is a later migration step.
 */
public interface MobilePlayerViewBinder {
    interface Binding {
        void releaseView();
    }

    Binding bind(ViewGroup container, MobilePlaybackRepository repository);
}
