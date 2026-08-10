package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BrightnessGesturePolicyTest {
    @Test public void bottomEdgeReturnsControlToSystem() {
        assertTrue(BrightnessGesturePolicy.usesSystemBrightness(0f));
        assertTrue(BrightnessGesturePolicy.usesSystemBrightness(-0.2f));
        assertFalse(BrightnessGesturePolicy.usesSystemBrightness(0.01f));
    }

    @Test public void manualBrightnessStaysInsideWindowRange() {
        assertEquals(0.01f, BrightnessGesturePolicy.clampManualBrightness(0f), 0f);
        assertEquals(0.4f, BrightnessGesturePolicy.clampManualBrightness(0.4f), 0f);
        assertEquals(1f, BrightnessGesturePolicy.clampManualBrightness(1.4f), 0f);
    }
}
