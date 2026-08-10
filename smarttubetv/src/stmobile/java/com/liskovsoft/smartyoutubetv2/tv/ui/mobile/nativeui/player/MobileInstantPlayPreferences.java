package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * User-facing switches for the mobile-only Instant Play startup/recovery layer.
 *
 * <p>These preferences are deliberately separate from Android Auto. The stable automotive
 * playback repository never reads this file.</p>
 */
public final class MobileInstantPlayPreferences {
    private static final String PREF_FILE = "smarttube_mobile_instant_play";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_FORBIDDEN_RECOVERY = "forbidden_recovery";
    private static final String KEY_STARTUP_WATCHDOG = "startup_watchdog";

    private final SharedPreferences preferences;

    public MobileInstantPlayPreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    /** Master switch. Enabled by default. */
    public boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /**
     * Adds a mobile fallback retry when the shared SmartTube 403 recovery did not make the player
     * ready quickly enough. The common recovery pipeline remains the first line of defence.
     */
    public boolean isForbiddenRecoveryEnabled() {
        return preferences.getBoolean(KEY_FORBIDDEN_RECOVERY, true);
    }

    public void setForbiddenRecoveryEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_FORBIDDEN_RECOVERY, enabled).apply();
    }

    /** Reload once when startup remains stuck for an unusually long time. */
    public boolean isStartupWatchdogEnabled() {
        return preferences.getBoolean(KEY_STARTUP_WATCHDOG, true);
    }

    public void setStartupWatchdogEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_STARTUP_WATCHDOG, enabled).apply();
    }

    public void reset() {
        preferences.edit().clear().apply();
    }
}
