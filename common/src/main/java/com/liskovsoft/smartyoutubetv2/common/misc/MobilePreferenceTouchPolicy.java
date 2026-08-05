package com.liskovsoft.smartyoutubetv2.common.misc;

/** Pure-Java rules for touch behavior inside preference rows. */
public final class MobilePreferenceTouchPolicy {
    private MobilePreferenceTouchPolicy() {
    }

    /**
     * Leanback preference rows attach their change listener to the row
     * container. Directly clicking the nested compound button may only toggle
     * its visual state, so the mobile controller promotes such a target to the
     * nearest clickable parent.
     */
    public static boolean shouldPromoteToClickableParent(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        return className.contains("RadioButton")
                || className.contains("CheckBox")
                || className.contains("Switch")
                || className.contains("CompoundButton");
    }
}
