package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.background;

/** Pure foreground/notification policy, kept outside Android APIs for deterministic JVM tests. */
public final class MobileBackgroundPlaybackPolicy {
    private MobileBackgroundPlaybackPolicy() {}

    public static boolean shouldRunForeground(boolean released, boolean dismissed,
                                              boolean prepared, boolean playing,
                                              boolean hasPlayIntent) {
        if (released || dismissed || !prepared) return false;
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
