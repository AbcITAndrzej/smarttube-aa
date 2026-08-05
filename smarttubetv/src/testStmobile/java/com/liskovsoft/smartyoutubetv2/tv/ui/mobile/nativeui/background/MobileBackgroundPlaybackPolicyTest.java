package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.background;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileBackgroundPlaybackPolicyTest {
    @Test public void acceptedPlayIntentStartsForegroundBeforePlayerCallback() {
        assertTrue(MobileBackgroundPlaybackPolicy.shouldRunForeground(
                false, false, true, false, true));
    }

    @Test public void pausedPreparedPlayerKeepsNotificationButNotService() {
        assertFalse(MobileBackgroundPlaybackPolicy.shouldRunForeground(
                false, false, true, false, false));
        assertTrue(MobileBackgroundPlaybackPolicy.shouldShowNotification(
                false, false, true));
    }

    @Test public void dismissedOrReleasedSessionShowsNothing() {
        assertFalse(MobileBackgroundPlaybackPolicy.shouldRunForeground(
                false, true, true, true, true));
        assertFalse(MobileBackgroundPlaybackPolicy.shouldShowNotification(
                true, false, true));
    }
}
