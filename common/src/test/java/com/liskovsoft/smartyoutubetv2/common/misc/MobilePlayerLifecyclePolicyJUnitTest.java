package com.liskovsoft.smartyoutubetv2.common.misc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MobilePlayerLifecyclePolicyJUnitTest {
    @Test
    public void rotationKeepsPlayer() {
        assertEquals(MobilePlayerLifecyclePolicy.Action.KEEP_PLAYING,
                MobilePlayerLifecyclePolicy.onPause(true, false,
                        MobilePlayerLifecyclePolicy.BackgroundMode.DEFAULT, false));
        assertEquals(MobilePlayerLifecyclePolicy.Action.KEEP_PLAYING,
                MobilePlayerLifecyclePolicy.onStop(true, false,
                        MobilePlayerLifecyclePolicy.BackgroundMode.DEFAULT, false));
    }

    @Test
    public void normalBackgroundPausesThenReleases() {
        assertEquals(MobilePlayerLifecyclePolicy.Action.PAUSE,
                MobilePlayerLifecyclePolicy.onPause(false, false,
                        MobilePlayerLifecyclePolicy.BackgroundMode.DEFAULT, false));
        assertEquals(MobilePlayerLifecyclePolicy.Action.RELEASE,
                MobilePlayerLifecyclePolicy.onStop(false, false,
                        MobilePlayerLifecyclePolicy.BackgroundMode.DEFAULT, false));
    }

    @Test
    public void pipAndAudioRemainActive() {
        assertEquals(MobilePlayerLifecyclePolicy.Action.KEEP_PLAYING,
                MobilePlayerLifecyclePolicy.onStop(false, true,
                        MobilePlayerLifecyclePolicy.BackgroundMode.DEFAULT, false));
        assertEquals(MobilePlayerLifecyclePolicy.Action.KEEP_PLAYING,
                MobilePlayerLifecyclePolicy.onStop(false, false,
                        MobilePlayerLifecyclePolicy.BackgroundMode.AUDIO_ONLY, false));
    }

    @Test
    public void finishingAlwaysReleases() {
        assertEquals(MobilePlayerLifecyclePolicy.Action.RELEASE,
                MobilePlayerLifecyclePolicy.onPause(false, true,
                        MobilePlayerLifecyclePolicy.BackgroundMode.PICTURE_IN_PICTURE, true));
    }
}
