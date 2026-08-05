package com.liskovsoft.smartyoutubetv2.common.misc;

import android.util.Log;

/** Central Logcat helper for the mobile flavor. */
public final class MobileDiagnostics {
    public static final String ROOT_TAG = "STMobile";

    private MobileDiagnostics() {
    }

    public static void debug(String area, String message) {
        Log.d(tag(area), message != null ? message : "<null>");
    }

    public static void info(String area, String message) {
        Log.i(tag(area), message != null ? message : "<null>");
    }

    public static void warn(String area, String message) {
        Log.w(tag(area), message != null ? message : "<null>");
    }

    public static void error(String area, String message, Throwable error) {
        Log.e(tag(area), message != null ? message : "<null>", error);
    }

    public static void lifecycle(Object owner, String event, String details) {
        String ownerName = owner != null ? owner.getClass().getSimpleName() : "unknown";
        debug("Lifecycle", ownerName + ": " + event + (details == null || details.isEmpty() ? "" : " | " + details));
    }

    private static String tag(String area) {
        if (area == null || area.trim().isEmpty()) {
            return ROOT_TAG;
        }
        String compact = area.replaceAll("[^A-Za-z0-9_]", "");
        if (compact.length() > 12) {
            compact = compact.substring(0, 12);
        }
        return ROOT_TAG + "/" + compact;
    }
}
