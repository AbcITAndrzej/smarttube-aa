package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.background;

/** Pure foreground/notification policy, kept outside Android APIs for deterministic JVM tests. */
public final class MobileBackgroundPlaybackPolicy {
    private MobileBackgroundPlaybackPolicy() {}

    public static boolean shouldRunForeground(boolean released, boolean dismissed,
                                              boolean prepared, boolean playing,
                                              boolean hasPlayIntent) {
        return shouldRunForeground(released, dismissed, prepared, playing, hasPlayIntent, false);
    }

    public static boolean shouldRunForeground(boolean released, boolean dismissed,
                                              boolean prepared, boolean playing,
                                              boolean hasPlayIntent,
                                              boolean keepAliveWhileUnprepared) {
        if (released || dismissed) return false;
        // A recoverable network error and the gap between playlist tracks both drop ExoPlayer
        // through STATE_IDLE. Stopping the service there forces a new background start, which
        // Android 12+ rejects while the screen is off. Hold the service that is already running.
        if (keepAliveWhileUnprepared) return true;
        if (!prepared) return false;
        // Keep the service alive from the instant a play command is accepted, rather than waiting
        // for ExoPlayer's asynchronous STATE_READY/playing callback. This closes the Android O+
        // startForegroundService timing window for notification/headset play actions.
        return playing || hasPlayIntent;
    }

    public static boolean shouldShowNotification(boolean released, boolean dismissed,
                                                 boolean prepared) {
        return !released && !dismissed && prepared;
    }
}
