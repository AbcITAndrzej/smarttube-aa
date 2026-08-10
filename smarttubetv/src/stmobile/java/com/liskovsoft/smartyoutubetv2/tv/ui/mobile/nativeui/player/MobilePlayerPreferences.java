package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Preferences owned exclusively by the touch/mobile player.
 *
 * <p>Android Auto deliberately never reads this file. Keep car-specific settings in
 * {@code AndroidAutoPreferences} so a phone UI customization cannot alter the stable AA service.</p>
 */
public final class MobilePlayerPreferences {
    private static final String PREF_FILE = "smarttube_mobile_player";

    private static final String KEY_AUTO_HIDE_CONTROLS = "auto_hide_controls";
    private static final String KEY_PINCH_ZOOM = "pinch_zoom";
    private static final String KEY_DOUBLE_TAP_SEEK = "double_tap_seek";
    private static final String KEY_DOUBLE_TAP_SEEK_SECONDS = "double_tap_seek_seconds";
    private static final String KEY_SWIPE_SEEK = "swipe_seek";
    private static final String KEY_BRIGHTNESS_GESTURE = "brightness_gesture";
    private static final String KEY_VOLUME_GESTURE = "volume_gesture";
    private static final String KEY_PLAYER_LOCK = "player_lock";
    private static final String KEY_SLEEP_TIMER = "sleep_timer";
    private static final String KEY_REMEMBER_ZOOM = "remember_zoom";
    private static final String KEY_SMART_FIT = "smart_fit";
    private static final String KEY_VOD_ZOOM_SCALE = "vod_zoom_scale";
    private static final String KEY_SHORT_ZOOM_SCALE = "short_zoom_scale";
    private static final String KEY_SHOW_PREVIOUS_NEXT = "show_previous_next";
    private static final String KEY_SHOW_QUICK_OPTIONS = "show_quick_options";
    private static final String KEY_SHOW_SUBTITLES = "show_subtitles";
    private static final String KEY_SHOW_AUDIO = "show_audio";
    private static final String KEY_SHOW_QUALITY = "show_quality";
    private static final String KEY_SHOW_SPEED = "show_speed";
    private static final String KEY_SHOW_FIT = "show_fit";
    private static final String KEY_SHOW_PIP = "show_pip";
    private static final String KEY_SHOW_FULLSCREEN = "show_fullscreen";
    private static final String KEY_SHOW_MORE = "show_more";
    private static final String KEY_PREFERRED_AUDIO_LANGUAGE = "preferred_audio_language";
    private static final String KEY_PREFERRED_SUBTITLE_LANGUAGE = "preferred_subtitle_language";

    public static final String LANGUAGE_SYSTEM = "system";
    public static final String LANGUAGE_NONE = "";

    private final SharedPreferences preferences;

    public MobilePlayerPreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public boolean isAutoHideControlsEnabled() {
        return preferences.getBoolean(KEY_AUTO_HIDE_CONTROLS, true);
    }

    public void setAutoHideControlsEnabled(boolean enabled) {
        setBoolean(KEY_AUTO_HIDE_CONTROLS, enabled);
    }

    public boolean isPinchZoomEnabled() {
        return preferences.getBoolean(KEY_PINCH_ZOOM, true);
    }

    public void setPinchZoomEnabled(boolean enabled) {
        setBoolean(KEY_PINCH_ZOOM, enabled);
    }

    public boolean isDoubleTapSeekEnabled() {
        return preferences.getBoolean(KEY_DOUBLE_TAP_SEEK, true);
    }

    public void setDoubleTapSeekEnabled(boolean enabled) {
        setBoolean(KEY_DOUBLE_TAP_SEEK, enabled);
    }

    public int getDoubleTapSeekSeconds() {
        int value = preferences.getInt(KEY_DOUBLE_TAP_SEEK_SECONDS, 10);
        return value == 5 || value == 10 || value == 15 || value == 30 ? value : 10;
    }

    public void setDoubleTapSeekSeconds(int seconds) {
        int safe = seconds == 5 || seconds == 10 || seconds == 15 || seconds == 30 ? seconds : 10;
        preferences.edit().putInt(KEY_DOUBLE_TAP_SEEK_SECONDS, safe).apply();
    }

    public boolean isSwipeSeekEnabled() {
        return preferences.getBoolean(KEY_SWIPE_SEEK, true);
    }

    public void setSwipeSeekEnabled(boolean enabled) {
        setBoolean(KEY_SWIPE_SEEK, enabled);
    }

    /** Vertical gesture on the left half. Deliberately ignored for Shorts and Radio. */
    public boolean isBrightnessGestureEnabled() {
        return preferences.getBoolean(KEY_BRIGHTNESS_GESTURE, true);
    }

    public void setBrightnessGestureEnabled(boolean enabled) {
        setBoolean(KEY_BRIGHTNESS_GESTURE, enabled);
    }

    /** Vertical gesture on the right half. Deliberately ignored for Shorts and Radio. */
    public boolean isVolumeGestureEnabled() {
        return preferences.getBoolean(KEY_VOLUME_GESTURE, true);
    }

    public void setVolumeGestureEnabled(boolean enabled) {
        setBoolean(KEY_VOLUME_GESTURE, enabled);
    }

    /** Enables the optional touch-lock button. The player never starts in a locked state. */
    public boolean isPlayerLockEnabled() {
        return preferences.getBoolean(KEY_PLAYER_LOCK, true);
    }

    public void setPlayerLockEnabled(boolean enabled) {
        setBoolean(KEY_PLAYER_LOCK, enabled);
    }

    /** Enables the local sleep-timer menu. No timer is scheduled automatically. */
    public boolean isSleepTimerEnabled() {
        return preferences.getBoolean(KEY_SLEEP_TIMER, true);
    }

    public void setSleepTimerEnabled(boolean enabled) {
        setBoolean(KEY_SLEEP_TIMER, enabled);
    }

    /** Remembers only scale, not pan translation, separately for VOD and Shorts. */
    public boolean isRememberZoomEnabled() {
        return preferences.getBoolean(KEY_REMEMBER_ZOOM, true);
    }

    public void setRememberZoomEnabled(boolean enabled) {
        setBoolean(KEY_REMEMBER_ZOOM, enabled);
    }

    public float getRememberedZoomScale(boolean shortMode) {
        float value = preferences.getFloat(shortMode ? KEY_SHORT_ZOOM_SCALE : KEY_VOD_ZOOM_SCALE, 1f);
        return clampScale(value);
    }

    public void setRememberedZoomScale(boolean shortMode, float scale) {
        preferences.edit().putFloat(shortMode ? KEY_SHORT_ZOOM_SCALE : KEY_VOD_ZOOM_SCALE,
                clampScale(scale)).apply();
    }

    /** Chooses FIT or ZOOM from the video/surface aspect ratio until the user manually changes it. */
    public boolean isSmartFitEnabled() {
        return preferences.getBoolean(KEY_SMART_FIT, true);
    }

    public void setSmartFitEnabled(boolean enabled) {
        setBoolean(KEY_SMART_FIT, enabled);
    }

    public boolean isPreviousNextVisible() {
        return preferences.getBoolean(KEY_SHOW_PREVIOUS_NEXT, true);
    }

    public void setPreviousNextVisible(boolean visible) {
        setBoolean(KEY_SHOW_PREVIOUS_NEXT, visible);
    }

    public boolean isQuickOptionsVisible() {
        return preferences.getBoolean(KEY_SHOW_QUICK_OPTIONS, true);
    }

    public void setQuickOptionsVisible(boolean visible) {
        setBoolean(KEY_SHOW_QUICK_OPTIONS, visible);
    }

    public boolean isSubtitlesVisible() {
        return preferences.getBoolean(KEY_SHOW_SUBTITLES, true);
    }

    public void setSubtitlesVisible(boolean visible) {
        setBoolean(KEY_SHOW_SUBTITLES, visible);
    }

    public boolean isAudioVisible() {
        return preferences.getBoolean(KEY_SHOW_AUDIO, true);
    }

    public void setAudioVisible(boolean visible) {
        setBoolean(KEY_SHOW_AUDIO, visible);
    }

    public boolean isQualityVisible() {
        return preferences.getBoolean(KEY_SHOW_QUALITY, true);
    }

    public void setQualityVisible(boolean visible) {
        setBoolean(KEY_SHOW_QUALITY, visible);
    }

    public boolean isSpeedVisible() {
        return preferences.getBoolean(KEY_SHOW_SPEED, true);
    }

    public void setSpeedVisible(boolean visible) {
        setBoolean(KEY_SHOW_SPEED, visible);
    }

    public boolean isFitVisible() {
        return preferences.getBoolean(KEY_SHOW_FIT, true);
    }

    public void setFitVisible(boolean visible) {
        setBoolean(KEY_SHOW_FIT, visible);
    }

    public boolean isPipVisible() {
        return preferences.getBoolean(KEY_SHOW_PIP, true);
    }

    public void setPipVisible(boolean visible) {
        setBoolean(KEY_SHOW_PIP, visible);
    }

    public boolean isFullscreenVisible() {
        return preferences.getBoolean(KEY_SHOW_FULLSCREEN, true);
    }

    public void setFullscreenVisible(boolean visible) {
        setBoolean(KEY_SHOW_FULLSCREEN, visible);
    }

    public boolean isMoreVisible() {
        return preferences.getBoolean(KEY_SHOW_MORE, true);
    }

    public void setMoreVisible(boolean visible) {
        setBoolean(KEY_SHOW_MORE, visible);
    }

    public String getPreferredAudioLanguage() {
        return preferences.getString(KEY_PREFERRED_AUDIO_LANGUAGE, LANGUAGE_SYSTEM);
    }

    public void setPreferredAudioLanguage(String languageCode) {
        preferences.edit().putString(KEY_PREFERRED_AUDIO_LANGUAGE,
                sanitizeLanguage(languageCode)).apply();
    }

    public String getPreferredSubtitleLanguage() {
        return preferences.getString(KEY_PREFERRED_SUBTITLE_LANGUAGE, LANGUAGE_SYSTEM);
    }

    public void setPreferredSubtitleLanguage(String languageCode) {
        preferences.edit().putString(KEY_PREFERRED_SUBTITLE_LANGUAGE,
                sanitizeLanguage(languageCode)).apply();
    }

    public void reset() {
        preferences.edit().clear().apply();
    }

    private void setBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    private static String sanitizeLanguage(String value) {
        if (value == null) return LANGUAGE_SYSTEM;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? LANGUAGE_NONE : trimmed;
    }

    private static float clampScale(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 1f;
        return Math.max(1f, Math.min(4f, value));
    }
}
