package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central rollout gate for optional native-mobile functionality.
 *
 * <p>Feature code should always define a safe default in code and ask this class for the effective
 * value. An override can then disable a newly introduced mechanism without deleting/reverting the
 * implementation. User-facing feature preferences (for example Player or Radio settings) remain
 * separate and can be combined with this gate.</p>
 */
public final class MobileFeatureFlags {
    private static final String PREF_FILE = "smarttube_mobile_feature_flags";
    private static final String PREFIX_OVERRIDE = "override:";

    /** Stage 1: lightweight diagnostics capture/reporting. Safe and enabled by default. */
    public static final String DIAGNOSTICS_CAPTURE = "diagnostics_capture";
    /** Stage 1: include the in-memory recent event buffer in copied reports. */
    public static final String DIAGNOSTICS_RECENT_EVENTS = "diagnostics_recent_events";
    /** Stage 2: mobile-only Instant Play state/recovery coordinator. */
    public static final String INSTANT_PLAY = "instant_play";
    /** Stage 2: delayed fallback when the shared SmartTube 403 refresh did not recover quickly. */
    public static final String INSTANT_PLAY_FORBIDDEN_RECOVERY = "instant_play_forbidden_recovery";
    /** Stage 2: reload a VOD/Shorts startup that remains stuck before READY. */
    public static final String INSTANT_PLAY_STARTUP_WATCHDOG = "instant_play_startup_watchdog";
    /** Stage 3: load additional Search continuation pages near the end of the list. */
    public static final String PAGING_SEARCH = "paging_search";
    /** Stage 3: load additional Channel continuation pages near the end of the list. */
    public static final String PAGING_CHANNEL = "paging_channel";
    /** Stage 3: remove the historical 200-station Radio cap using server-side pages. */
    public static final String PAGING_RADIO_CATALOG = "paging_radio_catalog";
    /** Stage 4: mobile-only Smart Player UX (gestures, lock, timer, zoom memory, smart fit). */
    public static final String SMART_PLAYER_UX = "smart_player_ux";
    /** Stage 5: Radio 2.0 master gate (categories, recent, live UX and shared enhancements). */
    public static final String RADIO_2 = "radio_2";
    /** Stage 5: live Radio Browser search outside the locally cached pages. */
    public static final String RADIO_2_REMOTE_SEARCH = "radio_2_remote_search";
    /** Stage 5: resolve and try another stream after a radio stream failure. */
    public static final String RADIO_2_STREAM_FAILOVER = "radio_2_stream_failover";
    /** Stage 5: richer Radio browse folders/search in Android Auto. */
    public static final String RADIO_2_ANDROID_AUTO = "radio_2_android_auto";
    /** Stage 6: local metadata database, private audio store and storage policy for future offline audio. */
    public static final String OFFLINE_FOUNDATION = "offline_foundation";
    /** Stage 7: passive audio-only listen-and-save after a configurable listening threshold. */
    public static final String OFFLINE_LISTEN_SAVE = "offline_listen_save";
    /** Stage 8: explicit audio-only offline playlist queue and foreground downloader. */
    public static final String OFFLINE_PLAYLISTS = "offline_playlists";
    /** Stage 9: expose local offline audio in Android Auto and allow local fallback. */
    public static final String OFFLINE_ANDROID_AUTO = "offline_android_auto";
    /** Stage 10: smart trip reserve that maintains recent/favorite/playlist audio offline. */
    public static final String OFFLINE_TRIP_RESERVE = "offline_trip_reserve";
    /** Stage 11: reversible AndroidX Media3 playback-engine foundation. */
    public static final String MEDIA3_ENGINE = "media3_engine";
    /** Stage 11: use Media3 for direct Radio audio sources. */
    public static final String MEDIA3_RADIO = "media3_radio";
    /** Stage 11: use Media3 for local Offline audio files. */
    public static final String MEDIA3_OFFLINE = "media3_offline";
    /** Stage 11: fail open to the mature legacy engine when Media3 cannot play a direct source. */
    public static final String MEDIA3_LEGACY_FALLBACK = "media3_legacy_fallback";
    /** Stage 12: local performance counters/traces shown in Diagnostics only. */
    public static final String PERFORMANCE_MONITOR = "performance_monitor";
    /** Stage 12: lightweight Choreographer frame-gap sampling while the mobile activity is resumed. */
    public static final String PERFORMANCE_FRAME_SAMPLING = "performance_frame_sampling";

    private final SharedPreferences preferences;

    public MobileFeatureFlags(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public boolean isEnabled(String key, boolean defaultValue) {
        if (key == null || key.trim().isEmpty()) return defaultValue;
        String overrideKey = PREFIX_OVERRIDE + key;
        return preferences.contains(overrideKey)
                ? preferences.getBoolean(overrideKey, defaultValue)
                : defaultValue;
    }

    public void setOverride(String key, boolean enabled) {
        if (key == null || key.trim().isEmpty()) return;
        preferences.edit().putBoolean(PREFIX_OVERRIDE + key, enabled).apply();
    }

    public void clearOverride(String key) {
        if (key == null || key.trim().isEmpty()) return;
        preferences.edit().remove(PREFIX_OVERRIDE + key).apply();
    }

    public void clearAllOverrides() {
        preferences.edit().clear().apply();
    }

    public boolean isDiagnosticsCaptureEnabled() {
        return isEnabled(DIAGNOSTICS_CAPTURE, true);
    }

    public void setDiagnosticsCaptureEnabled(boolean enabled) {
        setOverride(DIAGNOSTICS_CAPTURE, enabled);
    }

    public boolean isRecentEventsEnabled() {
        return isEnabled(DIAGNOSTICS_RECENT_EVENTS, true);
    }

    public void setRecentEventsEnabled(boolean enabled) {
        setOverride(DIAGNOSTICS_RECENT_EVENTS, enabled);
    }

    public boolean isInstantPlayEnabled() {
        return isEnabled(INSTANT_PLAY, true);
    }

    public boolean isInstantPlayForbiddenRecoveryEnabled() {
        return isEnabled(INSTANT_PLAY_FORBIDDEN_RECOVERY, true);
    }

    public boolean isInstantPlayStartupWatchdogEnabled() {
        return isEnabled(INSTANT_PLAY_STARTUP_WATCHDOG, true);
    }

    public boolean isSearchPagingEnabled() { return isEnabled(PAGING_SEARCH, true); }
    public boolean isChannelPagingEnabled() { return isEnabled(PAGING_CHANNEL, true); }
    public boolean isRadioCatalogPagingEnabled() { return isEnabled(PAGING_RADIO_CATALOG, true); }

    public boolean isSmartPlayerUxEnabled() { return isEnabled(SMART_PLAYER_UX, true); }
    public boolean isRadio2Enabled() { return isEnabled(RADIO_2, true); }
    public boolean isRadio2RemoteSearchEnabled() { return isEnabled(RADIO_2_REMOTE_SEARCH, true); }
    public boolean isRadio2StreamFailoverEnabled() { return isEnabled(RADIO_2_STREAM_FAILOVER, true); }
    public boolean isRadio2AndroidAutoEnabled() { return isEnabled(RADIO_2_ANDROID_AUTO, true); }
    public boolean isOfflineFoundationEnabled() { return isEnabled(OFFLINE_FOUNDATION, true); }
    public boolean isOfflineListenSaveEnabled() { return isEnabled(OFFLINE_LISTEN_SAVE, true); }
    public boolean isOfflinePlaylistsEnabled() { return isEnabled(OFFLINE_PLAYLISTS, true); }
    public boolean isOfflineAndroidAutoEnabled() { return isEnabled(OFFLINE_ANDROID_AUTO, true); }
    public boolean isOfflineTripReserveEnabled() { return isEnabled(OFFLINE_TRIP_RESERVE, true); }
    public boolean isMedia3EngineEnabled() { return isEnabled(MEDIA3_ENGINE, true); }
    public boolean isMedia3RadioEnabled() { return isEnabled(MEDIA3_RADIO, true); }
    public boolean isMedia3OfflineEnabled() { return isEnabled(MEDIA3_OFFLINE, true); }
    public boolean isMedia3LegacyFallbackEnabled() { return isEnabled(MEDIA3_LEGACY_FALLBACK, true); }
    public boolean isPerformanceMonitoringEnabled() { return isEnabled(PERFORMANCE_MONITOR, true); }
    public boolean isPerformanceFrameSamplingEnabled() { return isEnabled(PERFORMANCE_FRAME_SAMPLING, true); }

    public void setSearchPagingEnabled(boolean enabled) { setOverride(PAGING_SEARCH, enabled); }
    public void setChannelPagingEnabled(boolean enabled) { setOverride(PAGING_CHANNEL, enabled); }
    public void setRadioCatalogPagingEnabled(boolean enabled) {
        setOverride(PAGING_RADIO_CATALOG, enabled);
    }

    public void setSmartPlayerUxEnabled(boolean enabled) { setOverride(SMART_PLAYER_UX, enabled); }
    public void setRadio2Enabled(boolean enabled) { setOverride(RADIO_2, enabled); }
    public void setRadio2RemoteSearchEnabled(boolean enabled) { setOverride(RADIO_2_REMOTE_SEARCH, enabled); }
    public void setRadio2StreamFailoverEnabled(boolean enabled) { setOverride(RADIO_2_STREAM_FAILOVER, enabled); }
    public void setRadio2AndroidAutoEnabled(boolean enabled) { setOverride(RADIO_2_ANDROID_AUTO, enabled); }
    public void setOfflineFoundationEnabled(boolean enabled) { setOverride(OFFLINE_FOUNDATION, enabled); }
    public void setOfflineListenSaveEnabled(boolean enabled) { setOverride(OFFLINE_LISTEN_SAVE, enabled); }
    public void setOfflinePlaylistsEnabled(boolean enabled) { setOverride(OFFLINE_PLAYLISTS, enabled); }
    public void setOfflineAndroidAutoEnabled(boolean enabled) { setOverride(OFFLINE_ANDROID_AUTO, enabled); }
    public void setOfflineTripReserveEnabled(boolean enabled) { setOverride(OFFLINE_TRIP_RESERVE, enabled); }
    public void setMedia3EngineEnabled(boolean enabled) { setOverride(MEDIA3_ENGINE, enabled); }
    public void setMedia3RadioEnabled(boolean enabled) { setOverride(MEDIA3_RADIO, enabled); }
    public void setMedia3OfflineEnabled(boolean enabled) { setOverride(MEDIA3_OFFLINE, enabled); }
    public void setMedia3LegacyFallbackEnabled(boolean enabled) { setOverride(MEDIA3_LEGACY_FALLBACK, enabled); }
    public void setPerformanceMonitoringEnabled(boolean enabled) { setOverride(PERFORMANCE_MONITOR, enabled); }
    public void setPerformanceFrameSamplingEnabled(boolean enabled) { setOverride(PERFORMANCE_FRAME_SAMPLING, enabled); }

    /** Visible to the diagnostics screen; future stages can add their own defaults here. */
    public Map<String, Boolean> currentStageFlags() {
        LinkedHashMap<String, Boolean> result = new LinkedHashMap<>();
        result.put(DIAGNOSTICS_CAPTURE, isDiagnosticsCaptureEnabled());
        result.put(DIAGNOSTICS_RECENT_EVENTS, isRecentEventsEnabled());
        result.put(INSTANT_PLAY, isInstantPlayEnabled());
        result.put(INSTANT_PLAY_FORBIDDEN_RECOVERY, isInstantPlayForbiddenRecoveryEnabled());
        result.put(INSTANT_PLAY_STARTUP_WATCHDOG, isInstantPlayStartupWatchdogEnabled());
        result.put(PAGING_SEARCH, isSearchPagingEnabled());
        result.put(PAGING_CHANNEL, isChannelPagingEnabled());
        result.put(PAGING_RADIO_CATALOG, isRadioCatalogPagingEnabled());
        result.put(SMART_PLAYER_UX, isSmartPlayerUxEnabled());
        result.put(RADIO_2, isRadio2Enabled());
        result.put(RADIO_2_REMOTE_SEARCH, isRadio2RemoteSearchEnabled());
        result.put(RADIO_2_STREAM_FAILOVER, isRadio2StreamFailoverEnabled());
        result.put(RADIO_2_ANDROID_AUTO, isRadio2AndroidAutoEnabled());
        result.put(OFFLINE_FOUNDATION, isOfflineFoundationEnabled());
        result.put(OFFLINE_LISTEN_SAVE, isOfflineListenSaveEnabled());
        result.put(OFFLINE_PLAYLISTS, isOfflinePlaylistsEnabled());
        result.put(OFFLINE_ANDROID_AUTO, isOfflineAndroidAutoEnabled());
        result.put(OFFLINE_TRIP_RESERVE, isOfflineTripReserveEnabled());
        result.put(MEDIA3_ENGINE, isMedia3EngineEnabled());
        result.put(MEDIA3_RADIO, isMedia3RadioEnabled());
        result.put(MEDIA3_OFFLINE, isMedia3OfflineEnabled());
        result.put(MEDIA3_LEGACY_FALLBACK, isMedia3LegacyFallbackEnabled());
        result.put(PERFORMANCE_MONITOR, isPerformanceMonitoringEnabled());
        result.put(PERFORMANCE_FRAME_SAMPLING, isPerformanceFrameSamplingEnabled());
        return Collections.unmodifiableMap(result);
    }
}
