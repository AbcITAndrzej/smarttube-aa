package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.startup;

import android.content.Context;
import android.content.SharedPreferences;

/** Preferences that control the normal phone/tablet startup experience. */
public final class MobileStartupPreferences {
    private static final String PREF_FILE = "smarttube_mobile_startup";
    private static final String KEY_DISABLE_STARTUP_UPDATE_CHECK =
            "disable_startup_update_check";

    private final SharedPreferences preferences;

    public MobileStartupPreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    /** Update checks are enabled by default. */
    public boolean isStartupUpdateCheckDisabled() {
        return preferences.getBoolean(KEY_DISABLE_STARTUP_UPDATE_CHECK, false);
    }

    public void setStartupUpdateCheckDisabled(boolean disabled) {
        preferences.edit().putBoolean(KEY_DISABLE_STARTUP_UPDATE_CHECK, disabled).apply();
    }
}
