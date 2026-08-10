package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio;

import android.content.Context;
import android.content.SharedPreferences;

/** Radio-only settings shared by phone playback and Android Auto. */
public final class RadioPreferences {
    private static final String PREF_FILE = "smarttube_mobile_radio";
    private static final String KEY_TIME_SHIFT_ENABLED = "timeshift_enabled";
    private static final String KEY_TIME_SHIFT_MINUTES = "timeshift_minutes";
    private static final String KEY_SERVER_SEARCH_ENABLED = "radio2_server_search_enabled";
    private static final String KEY_RECENT_ENABLED = "radio2_recent_enabled";
    private static final String KEY_FAILOVER_ENABLED = "radio2_failover_enabled";
    private static final String KEY_CATEGORIES_ENABLED = "radio2_categories_enabled";
    private static final String KEY_AA_DIRECTORY_ENABLED = "radio2_aa_directory_enabled";
    private static final String KEY_LIVE_OFFSET_ENABLED = "radio2_live_offset_enabled";
    private static final int DEFAULT_TIME_SHIFT_MINUTES = 3;

    private final SharedPreferences preferences;

    public RadioPreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public boolean isTimeShiftEnabled() {
        return preferences.getBoolean(KEY_TIME_SHIFT_ENABLED, true);
    }

    public void setTimeShiftEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_TIME_SHIFT_ENABLED, enabled).apply();
    }

    public int getTimeShiftMinutes() {
        return sanitizeMinutes(preferences.getInt(KEY_TIME_SHIFT_MINUTES,
                DEFAULT_TIME_SHIFT_MINUTES));
    }

    public void setTimeShiftMinutes(int minutes) {
        preferences.edit().putInt(KEY_TIME_SHIFT_MINUTES, sanitizeMinutes(minutes)).apply();
    }

    public long getTimeShiftWindowMs() {
        return getTimeShiftMinutes() * 60_000L;
    }

    /** Search outside the locally cached Radio Browser pages. */
    public boolean isServerSearchEnabled() {
        return preferences.getBoolean(KEY_SERVER_SEARCH_ENABLED, true);
    }

    public void setServerSearchEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_SERVER_SEARCH_ENABLED, enabled).apply();
    }

    /** Store a short on-device list of recently played stations. */
    public boolean isRecentStationsEnabled() {
        return preferences.getBoolean(KEY_RECENT_ENABLED, true);
    }

    public void setRecentStationsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_RECENT_ENABLED, enabled).apply();
    }

    /** Try another stream for the same station after a direct-stream failure. */
    public boolean isStreamFailoverEnabled() {
        return preferences.getBoolean(KEY_FAILOVER_ENABLED, true);
    }

    public void setStreamFailoverEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_FAILOVER_ENABLED, enabled).apply();
    }

    /** Country/genre filters in the phone directory. */
    public boolean isCategoriesEnabled() {
        return preferences.getBoolean(KEY_CATEGORIES_ENABLED, true);
    }

    public void setCategoriesEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_CATEGORIES_ENABLED, enabled).apply();
    }

    /** Rich Radio 2.0 browse folders/search in Android Auto. */
    public boolean isEnhancedAndroidAutoDirectoryEnabled() {
        return preferences.getBoolean(KEY_AA_DIRECTORY_ENABLED, true);
    }

    public void setEnhancedAndroidAutoDirectoryEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_AA_DIRECTORY_ENABLED, enabled).apply();
    }

    /** Show LIVE / LIVE -mm:ss instead of a VOD-style elapsed/duration label. */
    public boolean isLiveOffsetLabelEnabled() {
        return preferences.getBoolean(KEY_LIVE_OFFSET_ENABLED, true);
    }

    public void setLiveOffsetLabelEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_LIVE_OFFSET_ENABLED, enabled).apply();
    }


    public static boolean isPlaybackSettingKey(String key) {
        return KEY_TIME_SHIFT_ENABLED.equals(key)
                || KEY_TIME_SHIFT_MINUTES.equals(key)
                || KEY_SERVER_SEARCH_ENABLED.equals(key)
                || KEY_RECENT_ENABLED.equals(key)
                || KEY_FAILOVER_ENABLED.equals(key)
                || KEY_CATEGORIES_ENABLED.equals(key)
                || KEY_AA_DIRECTORY_ENABLED.equals(key)
                || KEY_LIVE_OFFSET_ENABLED.equals(key);
    }

    public void registerListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        if (listener != null) preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        if (listener != null) preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private static int sanitizeMinutes(int value) {
        if (value <= 1) return 1;
        if (value <= 3) return 3;
        return 5;
    }
}
