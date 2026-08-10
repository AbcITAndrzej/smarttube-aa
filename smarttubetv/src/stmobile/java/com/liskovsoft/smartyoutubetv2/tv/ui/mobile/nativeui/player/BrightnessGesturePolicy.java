package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player;

/** Pure boundary rules for the player brightness gesture. */
public final class BrightnessGesturePolicy {
    private BrightnessGesturePolicy() { }

    public static boolean usesSystemBrightness(float requestedBrightness) {
        return requestedBrightness <= 0f;
    }

    public static float clampManualBrightness(float requestedBrightness) {
        return Math.max(0.01f, Math.min(1f, requestedBrightness));
    }
}
