package com.liskovsoft.smartyoutubetv2.common.misc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Test;

/** Confirms the intended integration contract before wiring more player code. */
public class MobilePlayerPortMockitoTest {
    interface PlayerPort {
        void pause();
        void release();
    }

    @Test
    public void defaultBackgroundPausesAtPauseStage() {
        PlayerPort port = mock(PlayerPort.class);
        MobilePlayerLifecyclePolicy.Action action = MobilePlayerLifecyclePolicy.onPause(
                false, false, MobilePlayerLifecyclePolicy.BackgroundMode.DEFAULT, false);
        if (action == MobilePlayerLifecyclePolicy.Action.PAUSE) {
            port.pause();
        }
        verify(port).pause();
        verify(port, never()).release();
    }

    @Test
    public void rotationDoesNotTouchPlayer() {
        PlayerPort port = mock(PlayerPort.class);
        MobilePlayerLifecyclePolicy.Action action = MobilePlayerLifecyclePolicy.onStop(
                true, false, MobilePlayerLifecyclePolicy.BackgroundMode.DEFAULT, false);
        if (action == MobilePlayerLifecyclePolicy.Action.RELEASE) {
            port.release();
        }
        verify(port, never()).pause();
        verify(port, never()).release();
    }
}
