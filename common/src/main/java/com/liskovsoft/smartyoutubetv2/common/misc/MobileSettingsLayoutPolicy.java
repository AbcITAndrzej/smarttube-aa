package com.liskovsoft.smartyoutubetv2.common.misc;

/** Pure-Java layout decisions for the mobile settings pane. */
public final class MobileSettingsLayoutPolicy {
    public static final int MATCH_PARENT = -1;

    private MobileSettingsLayoutPolicy() {
    }

    public static int resolvePaneWidth(
            int rootWidthPx,
            int shortestSideDp,
            int tabletThresholdDp,
            int tabletMaxWidthPx) {
        if (rootWidthPx <= 0) {
            return MATCH_PARENT;
        }
        if (shortestSideDp <= 0 || shortestSideDp < tabletThresholdDp) {
            return MATCH_PARENT;
        }
        int safeMax = tabletMaxWidthPx > 0 ? tabletMaxWidthPx : rootWidthPx;
        return Math.min(rootWidthPx, safeMax);
    }
}
