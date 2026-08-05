package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.background;

import org.junit.Test;
import static org.junit.Assert.*;

public class MobileAudioFocusPolicyTest {
    @Test public void grantedPlayStartsImmediately() {
        MobileAudioFocusPolicy policy = new MobileAudioFocusPolicy();
        MobileAudioFocusPolicy.Decision decision = policy.onPlayRequested(
                MobileAudioFocusPolicy.FocusRequestResult.GRANTED);
        assertTrue(decision.shouldPlay());
        assertTrue(policy.hasPlayIntent());
        assertFalse(policy.isWaitingForFocus());
    }

    @Test public void delayedFocusWaitsWithoutStarting() {
        MobileAudioFocusPolicy policy = new MobileAudioFocusPolicy();
        MobileAudioFocusPolicy.Decision decision = policy.onPlayRequested(
                MobileAudioFocusPolicy.FocusRequestResult.DELAYED);
        assertFalse(decision.shouldPlay());
        assertTrue(policy.isWaitingForFocus());
        assertTrue(policy.shouldKeepForegroundService());
    }

    @Test public void deniedFocusClearsPlayIntent() {
        MobileAudioFocusPolicy policy = new MobileAudioFocusPolicy();
        policy.onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult.DENIED);
        assertFalse(policy.hasPlayIntent());
        assertFalse(policy.isWaitingForFocus());
    }

    @Test public void transientLossPausesAndGainResumes() {
        MobileAudioFocusPolicy policy = new MobileAudioFocusPolicy();
        policy.onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult.GRANTED);
        MobileAudioFocusPolicy.Decision loss = policy.onFocusEvent(
                MobileAudioFocusPolicy.FocusEvent.LOSS_TRANSIENT, true);
        assertTrue(loss.shouldPause());
        assertTrue(policy.shouldResumeOnFocusGain());
        MobileAudioFocusPolicy.Decision gain = policy.onFocusEvent(
                MobileAudioFocusPolicy.FocusEvent.GAIN, false);
        assertTrue(gain.shouldPlay());
        assertFalse(policy.shouldResumeOnFocusGain());
    }

    @Test public void permanentLossNeverAutoResumes() {
        MobileAudioFocusPolicy policy = new MobileAudioFocusPolicy();
        policy.onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult.GRANTED);
        MobileAudioFocusPolicy.Decision loss = policy.onFocusEvent(
                MobileAudioFocusPolicy.FocusEvent.LOSS, true);
        assertTrue(loss.shouldPause());
        assertFalse(policy.hasPlayIntent());
        MobileAudioFocusPolicy.Decision gain = policy.onFocusEvent(
                MobileAudioFocusPolicy.FocusEvent.GAIN, false);
        assertFalse(gain.shouldPlay());
    }

    @Test public void duckAndGainRestoreVolumeWithoutPausing() {
        MobileAudioFocusPolicy policy = new MobileAudioFocusPolicy();
        policy.onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult.GRANTED);
        MobileAudioFocusPolicy.Decision duck = policy.onFocusEvent(
                MobileAudioFocusPolicy.FocusEvent.LOSS_TRANSIENT_CAN_DUCK, true);
        assertTrue(duck.shouldDuck());
        assertFalse(duck.shouldPause());
        MobileAudioFocusPolicy.Decision gain = policy.onFocusEvent(
                MobileAudioFocusPolicy.FocusEvent.GAIN, true);
        assertTrue(gain.shouldRestoreVolume());
        assertFalse(gain.shouldPlay());
    }

    @Test public void userPauseCancelsPendingResume() {
        MobileAudioFocusPolicy policy = new MobileAudioFocusPolicy();
        policy.onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult.GRANTED);
        policy.onFocusEvent(MobileAudioFocusPolicy.FocusEvent.LOSS_TRANSIENT, true);
        policy.onUserPause();
        MobileAudioFocusPolicy.Decision gain = policy.onFocusEvent(
                MobileAudioFocusPolicy.FocusEvent.GAIN, false);
        assertFalse(gain.shouldPlay());
        assertFalse(policy.hasPlayIntent());
    }

    @Test public void externalPlaybackIsStoppedWhenFocusDenied() {
        MobileAudioFocusPolicy policy = new MobileAudioFocusPolicy();
        MobileAudioFocusPolicy.Decision decision = policy.onExternalPlaybackStarted(
                MobileAudioFocusPolicy.FocusRequestResult.DENIED);
        assertTrue(decision.shouldPause());
        assertFalse(policy.hasPlayIntent());
    }
}
