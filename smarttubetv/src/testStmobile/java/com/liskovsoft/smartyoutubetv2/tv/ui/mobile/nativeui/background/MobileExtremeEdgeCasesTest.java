package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.background;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Crash-test suite for the pure-JVM part of mobile background playback.
 *
 * <p>The production {@link MobileMediaSessionManager} serializes headset, notification and
 * Audio-Focus callbacks onto Android's main looper. Therefore events reported in the same
 * millisecond are verified in every meaningful serialized order instead of being invoked from
 * multiple JVM threads against a state machine that is intentionally single-thread confined.</p>
 *
 * <p>This class deliberately does not use Android framework classes, Robolectric or instrumentation.
 * It validates the deterministic policies and command coordinator introduced in Part 8.</p>
 */
public class MobileExtremeEdgeCasesTest {
    private static final int NOTIFICATION_PLAY_BURST = 20;
    private static final int FOCUS_FLAP_COUNT = 5;

    @Test
    public void unplugAndIncomingCallInSameMillisecond_neverAutoResume_inEitherOrder() {
        RaceResult unplugFirst = runUnplugAndCallRace(EventOrder.UNPLUG_THEN_CALL);
        RaceResult callFirst = runUnplugAndCallRace(EventOrder.CALL_THEN_UNPLUG);

        assertEquals("Initial user play should be the only play in unplug-first order",
                1, unplugFirst.playCalls);
        assertEquals("Initial user play should be the only play in call-first order",
                1, callFirst.playCalls);

        assertTrue("Unplug-first order must issue at least one pause",
                unplugFirst.pauseCalls >= 1);
        assertTrue("Call-first order must issue at least one pause",
                callFirst.pauseCalls >= 1);

        assertFalse("Headset removal is a user-safety pause and must clear play intent",
                unplugFirst.hasPlayIntent);
        assertFalse("A later headset removal must cancel call-resume intent",
                callFirst.hasPlayIntent);

        assertFalse("No pending resume may survive unplug-first ordering",
                unplugFirst.resumePending);
        assertFalse("No pending resume may survive call-first ordering",
                callFirst.resumePending);

        assertEquals(1.0f, unplugFirst.lastVolume, 0.001f);
        assertEquals(1.0f, callFirst.lastVolume, 0.001f);
    }

    @Test(timeout = 2_000L)
    public void notificationPlayPressedTwentyTimesPerSecond_keepsStateConsistent() {
        RecordingOutput output = new RecordingOutput();
        MobileAudioFocusPolicy policy = new MobileAudioFocusPolicy();
        MobileSessionCommandCoordinator coordinator =
                new MobileSessionCommandCoordinator(policy, output);

        for (int i = 0; i < NOTIFICATION_PLAY_BURST; i++) {
            coordinator.onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult.GRANTED);
        }

        // Part 8 forwards every accepted media-session play command. The important invariant is
        // that a burst cannot create pauses, ducking, a lost intent or an invalid service state.
        assertEquals(NOTIFICATION_PLAY_BURST, output.playCalls);
        assertEquals(0, output.pauseCalls);
        assertEquals(0, output.volumeChangeCalls);
        assertTrue(coordinator.hasPlayIntent());

        assertTrue("Foreground service must be eligible immediately after an accepted play burst",
                MobileBackgroundPlaybackPolicy.shouldRunForeground(
                        false, false, true, false, coordinator.hasPlayIntent()));
        assertTrue(MobileBackgroundPlaybackPolicy.shouldShowNotification(
                false, false, true));
    }

    @Test(timeout = 2_000L)
    public void audioFocusLostAndRegainedFiveTimes_eachCyclePausesAndResumesExactlyOnce() {
        RecordingOutput output = new RecordingOutput();
        MobileAudioFocusPolicy policy = new MobileAudioFocusPolicy();
        MobileSessionCommandCoordinator coordinator =
                new MobileSessionCommandCoordinator(policy, output);

        coordinator.onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult.GRANTED);

        for (int i = 0; i < FOCUS_FLAP_COUNT; i++) {
            coordinator.onFocusEvent(
                    MobileAudioFocusPolicy.FocusEvent.LOSS_TRANSIENT,
                    true);

            assertTrue("Cycle " + i + " must preserve the user's intent to resume",
                    policy.shouldResumeOnFocusGain());

            coordinator.onFocusEvent(
                    MobileAudioFocusPolicy.FocusEvent.GAIN,
                    false);

            assertFalse("Cycle " + i + " must consume its pending resume exactly once",
                    policy.shouldResumeOnFocusGain());
            assertTrue("Cycle " + i + " must retain play intent",
                    coordinator.hasPlayIntent());
        }

        assertEquals("One initial play plus one resume per focus cycle",
                1 + FOCUS_FLAP_COUNT, output.playCalls);
        assertEquals("Every transient loss must pause once",
                FOCUS_FLAP_COUNT, output.pauseCalls);
        assertEquals("Transient non-ducking focus changes must not touch volume",
                0, output.volumeChangeCalls);
        assertEquals(1.0f, output.lastVolume, 0.001f);
    }

    @Test
    public void brutalProcessDeathDuringBackgroundTransition_freshProcessStartsFailSafe() {
        // State immediately before the operating system kills the process: play was accepted,
        // ExoPlayer has not delivered its asynchronous playing callback yet, and the foreground
        // service is therefore kept alive by playIntent.
        MobileAudioFocusPolicy dyingProcessPolicy = new MobileAudioFocusPolicy();
        dyingProcessPolicy.onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult.GRANTED);

        assertTrue(MobileBackgroundPlaybackPolicy.shouldRunForeground(
                false, false, true, false, dyingProcessPolicy.hasPlayIntent()));

        // JVM approximation of process death: all in-memory objects disappear. Part 8 does not
        // persist queue/media/position yet, so a newly created process must not invent a stale
        // play request or resurrect an orphan notification. This proves fail-safe shutdown, not
        // automatic playback restoration after process death.
        MobileAudioFocusPolicy freshProcessPolicy = new MobileAudioFocusPolicy();

        assertFalse(freshProcessPolicy.hasPlayIntent());
        assertFalse(freshProcessPolicy.isWaitingForFocus());
        assertFalse(freshProcessPolicy.shouldResumeOnFocusGain());
        assertFalse(freshProcessPolicy.isDucked());

        assertFalse(MobileBackgroundPlaybackPolicy.shouldRunForeground(
                false, false, false, false, freshProcessPolicy.hasPlayIntent()));
        assertFalse(MobileBackgroundPlaybackPolicy.shouldShowNotification(
                false, false, false));

        // If Android finishes release/dismissal while the transition is in flight, those terminal
        // flags dominate even when a stale callback still claims that media was prepared/playing.
        assertFalse(MobileBackgroundPlaybackPolicy.shouldRunForeground(
                true, false, true, true, true));
        assertFalse(MobileBackgroundPlaybackPolicy.shouldRunForeground(
                false, true, true, true, true));
        assertFalse(MobileBackgroundPlaybackPolicy.shouldShowNotification(
                true, false, true));
        assertFalse(MobileBackgroundPlaybackPolicy.shouldShowNotification(
                false, true, true));
    }

    private static RaceResult runUnplugAndCallRace(EventOrder order) {
        RecordingOutput output = new RecordingOutput();
        MobileAudioFocusPolicy policy = new MobileAudioFocusPolicy();
        MobileSessionCommandCoordinator coordinator =
                new MobileSessionCommandCoordinator(policy, output);

        coordinator.onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult.GRANTED);

        if (order == EventOrder.UNPLUG_THEN_CALL) {
            // ACTION_AUDIO_BECOMING_NOISY is handled as pauseByUser()/onUserPause().
            coordinator.onUserPause();
            coordinator.onFocusEvent(
                    MobileAudioFocusPolicy.FocusEvent.LOSS_TRANSIENT,
                    false);
        } else {
            coordinator.onFocusEvent(
                    MobileAudioFocusPolicy.FocusEvent.LOSS_TRANSIENT,
                    true);
            coordinator.onUserPause();
        }

        // A call ending must not restart sound after headphones were removed.
        coordinator.onFocusEvent(MobileAudioFocusPolicy.FocusEvent.GAIN, false);

        return new RaceResult(
                output.playCalls,
                output.pauseCalls,
                output.lastVolume,
                coordinator.hasPlayIntent(),
                policy.shouldResumeOnFocusGain());
    }

    private enum EventOrder {
        UNPLUG_THEN_CALL,
        CALL_THEN_UNPLUG
    }

    private static final class RaceResult {
        final int playCalls;
        final int pauseCalls;
        final float lastVolume;
        final boolean hasPlayIntent;
        final boolean resumePending;

        RaceResult(int playCalls, int pauseCalls, float lastVolume,
                   boolean hasPlayIntent, boolean resumePending) {
            this.playCalls = playCalls;
            this.pauseCalls = pauseCalls;
            this.lastVolume = lastVolume;
            this.hasPlayIntent = hasPlayIntent;
            this.resumePending = resumePending;
        }
    }

    private static final class RecordingOutput
            implements MobileSessionCommandCoordinator.Output {
        int playCalls;
        int pauseCalls;
        int volumeChangeCalls;
        float lastVolume = 1.0f;

        @Override
        public void play() {
            playCalls++;
        }

        @Override
        public void pause() {
            pauseCalls++;
        }

        @Override
        public void setVolumeMultiplier(float multiplier) {
            volumeChangeCalls++;
            lastVolume = multiplier;
        }
    }
}
