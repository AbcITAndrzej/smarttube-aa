package com.liskovsoft.smartyoutubetv2.common.misc;

/**
 * Pure-Java decisions for player lifecycle transitions on phones and tablets.
 * The policy is deliberately isolated from Android so it can be tested on JVM.
 */
public final class MobilePlayerLifecyclePolicy {
    public enum BackgroundMode {
        DEFAULT,
        PICTURE_IN_PICTURE,
        AUDIO_ONLY
    }

    public enum Action {
        KEEP_PLAYING,
        PAUSE,
        RELEASE
    }

    private MobilePlayerLifecyclePolicy() {
    }

    public static Action onPause(
            boolean changingConfiguration,
            boolean inPictureInPicture,
            BackgroundMode backgroundMode,
            boolean finishing) {
        if (finishing) {
            return Action.RELEASE;
        }
        if (changingConfiguration) {
            return Action.KEEP_PLAYING;
        }
        if (inPictureInPicture || backgroundMode == BackgroundMode.PICTURE_IN_PICTURE
                || backgroundMode == BackgroundMode.AUDIO_ONLY) {
            return Action.KEEP_PLAYING;
        }
        return Action.PAUSE;
    }

    public static Action onStop(
            boolean changingConfiguration,
            boolean inPictureInPicture,
            BackgroundMode backgroundMode,
            boolean finishing) {
        if (finishing) {
            return Action.RELEASE;
        }
        if (changingConfiguration || inPictureInPicture
                || backgroundMode == BackgroundMode.PICTURE_IN_PICTURE
                || backgroundMode == BackgroundMode.AUDIO_ONLY) {
            return Action.KEEP_PLAYING;
        }
        return Action.RELEASE;
    }
}
