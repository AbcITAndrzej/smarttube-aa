package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.background;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileSessionCommandCoordinatorTest {
    @Test public void transientCallPausesThenResumesPlayback() {
        FakeOutput output = new FakeOutput();
        MobileSessionCommandCoordinator coordinator = new MobileSessionCommandCoordinator(
                new MobileAudioFocusPolicy(), output);
        coordinator.onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult.GRANTED);
        coordinator.onFocusEvent(MobileAudioFocusPolicy.FocusEvent.LOSS_TRANSIENT, true);
        coordinator.onFocusEvent(MobileAudioFocusPolicy.FocusEvent.GAIN, false);
        assertEquals(2, output.playCalls);
        assertEquals(1, output.pauseCalls);
    }

    @Test public void duckingUsesTwentyPercentAndRestoresFullVolume() {
        FakeOutput output = new FakeOutput();
        MobileSessionCommandCoordinator coordinator = new MobileSessionCommandCoordinator(
                new MobileAudioFocusPolicy(), output);
        coordinator.onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult.GRANTED);
        coordinator.onFocusEvent(MobileAudioFocusPolicy.FocusEvent.LOSS_TRANSIENT_CAN_DUCK, true);
        assertEquals(0.20f, output.lastVolume, 0.001f);
        coordinator.onFocusEvent(MobileAudioFocusPolicy.FocusEvent.GAIN, true);
        assertEquals(1f, output.lastVolume, 0.001f);
    }

    @Test public void delayedFocusKeepsForegroundIntentUntilGain() {
        FakeOutput output = new FakeOutput();
        MobileSessionCommandCoordinator coordinator = new MobileSessionCommandCoordinator(
                new MobileAudioFocusPolicy(), output);
        coordinator.onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult.DELAYED);
        assertEquals(0, output.playCalls);
        assertTrue(coordinator.shouldKeepForegroundService());
        coordinator.onFocusEvent(MobileAudioFocusPolicy.FocusEvent.GAIN, false);
        assertEquals(1, output.playCalls);
    }

    @Test public void permanentLossDoesNotResume() {
        FakeOutput output = new FakeOutput();
        MobileSessionCommandCoordinator coordinator = new MobileSessionCommandCoordinator(
                new MobileAudioFocusPolicy(), output);
        coordinator.onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult.GRANTED);
        coordinator.onFocusEvent(MobileAudioFocusPolicy.FocusEvent.LOSS, true);
        coordinator.onFocusEvent(MobileAudioFocusPolicy.FocusEvent.GAIN, false);
        assertEquals(1, output.playCalls);
        assertEquals(1, output.pauseCalls);
        assertFalse(coordinator.hasPlayIntent());
    }

    private static final class FakeOutput implements MobileSessionCommandCoordinator.Output {
        int playCalls;
        int pauseCalls;
        float lastVolume = 1f;
        @Override public void play() { playCalls++; }
        @Override public void pause() { pauseCalls++; }
        @Override public void setVolumeMultiplier(float multiplier) { lastVolume = multiplier; }
    }
}
