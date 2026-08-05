package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.background;

/**
 * Pure Java state machine used by {@link MobileMediaSessionManager}.
 * It deliberately contains no Android classes, which makes audio-focus edge cases testable on JVM.
 */
public final class MobileAudioFocusPolicy {
    public enum FocusRequestResult { GRANTED, DELAYED, DENIED }
    public enum FocusEvent { GAIN, LOSS, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK }

    public static final class Decision {
        private final boolean play;
        private final boolean pause;
        private final boolean duck;
        private final boolean restoreVolume;

        private Decision(boolean play, boolean pause, boolean duck, boolean restoreVolume) {
            this.play = play;
            this.pause = pause;
            this.duck = duck;
            this.restoreVolume = restoreVolume;
        }

        public static Decision none() { return new Decision(false, false, false, false); }
        static Decision of(boolean play, boolean pause, boolean duck, boolean restoreVolume) {
            return new Decision(play, pause, duck, restoreVolume);
        }

        public boolean shouldPlay() { return play; }
        public boolean shouldPause() { return pause; }
        public boolean shouldDuck() { return duck; }
        public boolean shouldRestoreVolume() { return restoreVolume; }
    }

    private boolean playIntent;
    private boolean waitingForFocus;
    private boolean resumeOnFocusGain;
    private boolean ducked;

    public Decision onPlayRequested(FocusRequestResult result) {
        playIntent = true;
        if (result == FocusRequestResult.GRANTED) {
            waitingForFocus = false;
            resumeOnFocusGain = false;
            return Decision.of(true, false, false, ducked);
        }
        if (result == FocusRequestResult.DELAYED) {
            waitingForFocus = true;
            resumeOnFocusGain = true;
            return Decision.none();
        }
        playIntent = false;
        waitingForFocus = false;
        resumeOnFocusGain = false;
        return Decision.none();
    }

    /** Handles playback that was started by the legacy player before focus was requested. */
    public Decision onExternalPlaybackStarted(FocusRequestResult result) {
        playIntent = true;
        if (result == FocusRequestResult.GRANTED) {
            waitingForFocus = false;
            resumeOnFocusGain = false;
            return Decision.none();
        }
        if (result == FocusRequestResult.DELAYED) {
            waitingForFocus = true;
            resumeOnFocusGain = true;
            return Decision.of(false, true, false, ducked);
        }
        playIntent = false;
        waitingForFocus = false;
        resumeOnFocusGain = false;
        return Decision.of(false, true, false, ducked);
    }

    public Decision onUserPause() {
        playIntent = false;
        waitingForFocus = false;
        resumeOnFocusGain = false;
        boolean restore = ducked;
        ducked = false;
        return Decision.of(false, true, false, restore);
    }

    public Decision onStop() {
        return onUserPause();
    }

    public Decision onFocusEvent(FocusEvent event, boolean currentlyPlaying) {
        switch (event) {
            case GAIN: {
                boolean restore = ducked;
                ducked = false;
                boolean shouldPlay = playIntent && (waitingForFocus || resumeOnFocusGain);
                waitingForFocus = false;
                resumeOnFocusGain = false;
                return Decision.of(shouldPlay, false, false, restore);
            }
            case LOSS:
                playIntent = false;
                waitingForFocus = false;
                resumeOnFocusGain = false;
                boolean restoreAfterLoss = ducked;
                ducked = false;
                return Decision.of(false, currentlyPlaying, false, restoreAfterLoss);
            case LOSS_TRANSIENT:
                if (playIntent && currentlyPlaying) {
                    resumeOnFocusGain = true;
                }
                return Decision.of(false, currentlyPlaying, false, false);
            case LOSS_TRANSIENT_CAN_DUCK:
                if (currentlyPlaying) {
                    ducked = true;
                    return Decision.of(false, false, true, false);
                }
                return Decision.none();
            default:
                return Decision.none();
        }
    }

    public boolean hasPlayIntent() { return playIntent; }
    public boolean isWaitingForFocus() { return waitingForFocus; }
    public boolean shouldResumeOnFocusGain() { return resumeOnFocusGain; }
    public boolean isDucked() { return ducked; }
    public boolean shouldKeepForegroundService() {
        return playIntent && (waitingForFocus || resumeOnFocusGain);
    }
}
