package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.content.Context;
import android.content.SharedPreferences;

/** User-facing storage policy for Stage 6 storage, Stage 7 passive saves and Stage 8 playlists. */
public final class OfflineMediaPreferences {
    private static final String PREF_FILE = "smarttube_mobile_offline";
    private static final String KEY_ENABLED = "foundation_enabled";
    private static final String KEY_AUTO_CLEANUP = "auto_cleanup";
    private static final String KEY_STORAGE_LIMIT_GB = "storage_limit_gb";
    private static final String KEY_RESERVED_FREE_MB = "reserved_free_mb";
    private static final String KEY_PLAYLIST_DOWNLOADS = "playlist_downloads_enabled";
    private static final String KEY_PLAYLIST_WIFI_ONLY = "playlist_wifi_only";
    private static final String KEY_LISTEN_SAVE = "listen_save_enabled";
    private static final String KEY_LISTEN_SAVE_WIFI_ONLY = "listen_save_wifi_only";
    private static final String KEY_LISTEN_SAVE_COMPLETE = "listen_save_complete_after_switch";
    private static final String KEY_LISTEN_SAVE_RECENT_LIMIT = "listen_save_recent_limit";
    private static final String KEY_LISTEN_SAVE_THRESHOLD_SEC = "listen_save_threshold_sec";
    private static final String KEY_TRIP_RESERVE_ENABLED = "trip_reserve_enabled";
    private static final String KEY_TRIP_RESERVE_WIFI_ONLY = "trip_reserve_wifi_only";
    private static final String KEY_TRIP_RESERVE_RECENT_COUNT = "trip_reserve_recent_count";
    private static final String KEY_TRIP_RESERVE_FAVORITES_ENABLED = "trip_reserve_favorites_enabled";
    private static final String KEY_TRIP_RESERVE_FAVORITE_COUNT = "trip_reserve_favorite_count";
    private static final String KEY_TRIP_RESERVE_PLAYLIST_COUNT = "trip_reserve_playlist_count";
    private static final String KEY_TRIP_RESERVE_PLAYLIST_TRACK_LIMIT = "trip_reserve_playlist_track_limit";
    private static final String KEY_TRIP_RESERVE_LAST_SYNC_MS = "trip_reserve_last_sync_ms";

    public static final int DEFAULT_STORAGE_LIMIT_GB = 2;
    public static final int DEFAULT_RESERVED_FREE_MB = 512;
    public static final int DEFAULT_LISTEN_SAVE_RECENT_LIMIT = 50;
    public static final int DEFAULT_LISTEN_SAVE_THRESHOLD_SEC = 15;
    public static final int DEFAULT_TRIP_RESERVE_RECENT_COUNT = 30;
    public static final int DEFAULT_TRIP_RESERVE_FAVORITE_COUNT = 20;
    public static final int DEFAULT_TRIP_RESERVE_PLAYLIST_COUNT = 2;
    public static final int DEFAULT_TRIP_RESERVE_PLAYLIST_TRACK_LIMIT = 50;

    private final SharedPreferences preferences;

    public OfflineMediaPreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public boolean isFoundationEnabled() {
        return preferences.getBoolean(KEY_ENABLED, true);
    }

    public void setFoundationEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public boolean isAutoCleanupEnabled() {
        return preferences.getBoolean(KEY_AUTO_CLEANUP, true);
    }

    public void setAutoCleanupEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_AUTO_CLEANUP, enabled).apply();
    }

    public int getStorageLimitGb() {
        int value = preferences.getInt(KEY_STORAGE_LIMIT_GB, DEFAULT_STORAGE_LIMIT_GB);
        return isAllowedStorageLimit(value) ? value : DEFAULT_STORAGE_LIMIT_GB;
    }

    public void setStorageLimitGb(int gb) {
        preferences.edit().putInt(KEY_STORAGE_LIMIT_GB,
                isAllowedStorageLimit(gb) ? gb : DEFAULT_STORAGE_LIMIT_GB).apply();
    }

    public long getStorageLimitBytes() {
        return getStorageLimitGb() * 1024L * 1024L * 1024L;
    }

    public int getReservedFreeMb() {
        int value = preferences.getInt(KEY_RESERVED_FREE_MB, DEFAULT_RESERVED_FREE_MB);
        return isAllowedReserve(value) ? value : DEFAULT_RESERVED_FREE_MB;
    }

    public void setReservedFreeMb(int mb) {
        preferences.edit().putInt(KEY_RESERVED_FREE_MB,
                isAllowedReserve(mb) ? mb : DEFAULT_RESERVED_FREE_MB).apply();
    }

    public long getReservedFreeBytes() {
        return getReservedFreeMb() * 1024L * 1024L;
    }


    /** Explicit Stage 8 playlist downloads. The user still has to tap Download on a playlist. */
    public boolean isPlaylistDownloadsEnabled() {
        return preferences.getBoolean(KEY_PLAYLIST_DOWNLOADS, true);
    }

    public void setPlaylistDownloadsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_PLAYLIST_DOWNLOADS, enabled).apply();
    }

    /** Safe default: background playlist transfers wait for Wi-Fi unless the user opts out. */
    public boolean isPlaylistWifiOnly() {
        return preferences.getBoolean(KEY_PLAYLIST_WIFI_ONLY, true);
    }

    public void setPlaylistWifiOnly(boolean wifiOnly) {
        preferences.edit().putBoolean(KEY_PLAYLIST_WIFI_ONLY, wifiOnly).apply();
    }


    /** Stage 7 passive listen-and-save. Disabled by default because it consumes bandwidth/storage. */
    public boolean isListenSaveEnabled() {
        return preferences.getBoolean(KEY_LISTEN_SAVE, false);
    }

    public void setListenSaveEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_LISTEN_SAVE, enabled).apply();
    }

    /** Safe default: passive saves only run on Wi-Fi/Ethernet. */
    public boolean isListenSaveWifiOnly() {
        return preferences.getBoolean(KEY_LISTEN_SAVE_WIFI_ONLY, true);
    }

    public void setListenSaveWifiOnly(boolean wifiOnly) {
        preferences.edit().putBoolean(KEY_LISTEN_SAVE_WIFI_ONLY, wifiOnly).apply();
    }

    /** Finish a started finite audio file even if the user switches to the next track. */
    public boolean isListenSaveCompleteAfterSwitch() {
        return preferences.getBoolean(KEY_LISTEN_SAVE_COMPLETE, true);
    }

    public void setListenSaveCompleteAfterSwitch(boolean complete) {
        preferences.edit().putBoolean(KEY_LISTEN_SAVE_COMPLETE, complete).apply();
    }

    public int getListenSaveRecentLimit() {
        int value = preferences.getInt(KEY_LISTEN_SAVE_RECENT_LIMIT, DEFAULT_LISTEN_SAVE_RECENT_LIMIT);
        return isAllowedRecentLimit(value) ? value : DEFAULT_LISTEN_SAVE_RECENT_LIMIT;
    }

    public void setListenSaveRecentLimit(int value) {
        preferences.edit().putInt(KEY_LISTEN_SAVE_RECENT_LIMIT,
                isAllowedRecentLimit(value) ? value : DEFAULT_LISTEN_SAVE_RECENT_LIMIT).apply();
    }

    public int getListenSaveThresholdSec() {
        int value = preferences.getInt(KEY_LISTEN_SAVE_THRESHOLD_SEC, DEFAULT_LISTEN_SAVE_THRESHOLD_SEC);
        return isAllowedListenThreshold(value) ? value : DEFAULT_LISTEN_SAVE_THRESHOLD_SEC;
    }

    public void setListenSaveThresholdSec(int seconds) {
        preferences.edit().putInt(KEY_LISTEN_SAVE_THRESHOLD_SEC,
                isAllowedListenThreshold(seconds) ? seconds : DEFAULT_LISTEN_SAVE_THRESHOLD_SEC).apply();
    }


    /** Stage 10 smart trip reserve. Disabled by default because it can trigger background downloads. */
    public boolean isTripReserveEnabled() {
        return preferences.getBoolean(KEY_TRIP_RESERVE_ENABLED, false);
    }

    public void setTripReserveEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_TRIP_RESERVE_ENABLED, enabled).apply();
    }

    public boolean isTripReserveWifiOnly() {
        return preferences.getBoolean(KEY_TRIP_RESERVE_WIFI_ONLY, true);
    }

    public void setTripReserveWifiOnly(boolean wifiOnly) {
        preferences.edit().putBoolean(KEY_TRIP_RESERVE_WIFI_ONLY, wifiOnly).apply();
    }

    public int getTripReserveRecentCount() {
        int value = preferences.getInt(KEY_TRIP_RESERVE_RECENT_COUNT, DEFAULT_TRIP_RESERVE_RECENT_COUNT);
        return isAllowedTripRecentCount(value) ? value : DEFAULT_TRIP_RESERVE_RECENT_COUNT;
    }

    public void setTripReserveRecentCount(int value) {
        preferences.edit().putInt(KEY_TRIP_RESERVE_RECENT_COUNT,
                isAllowedTripRecentCount(value) ? value : DEFAULT_TRIP_RESERVE_RECENT_COUNT).apply();
    }

    public boolean isTripReserveFavoritesEnabled() {
        return preferences.getBoolean(KEY_TRIP_RESERVE_FAVORITES_ENABLED, true);
    }

    public void setTripReserveFavoritesEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_TRIP_RESERVE_FAVORITES_ENABLED, enabled).apply();
    }

    public int getTripReserveFavoriteCount() {
        int value = preferences.getInt(KEY_TRIP_RESERVE_FAVORITE_COUNT, DEFAULT_TRIP_RESERVE_FAVORITE_COUNT);
        return isAllowedTripFavoriteCount(value) ? value : DEFAULT_TRIP_RESERVE_FAVORITE_COUNT;
    }

    public void setTripReserveFavoriteCount(int value) {
        preferences.edit().putInt(KEY_TRIP_RESERVE_FAVORITE_COUNT,
                isAllowedTripFavoriteCount(value) ? value : DEFAULT_TRIP_RESERVE_FAVORITE_COUNT).apply();
    }

    public int getTripReservePlaylistCount() {
        int value = preferences.getInt(KEY_TRIP_RESERVE_PLAYLIST_COUNT, DEFAULT_TRIP_RESERVE_PLAYLIST_COUNT);
        return isAllowedTripPlaylistCount(value) ? value : DEFAULT_TRIP_RESERVE_PLAYLIST_COUNT;
    }

    public void setTripReservePlaylistCount(int value) {
        preferences.edit().putInt(KEY_TRIP_RESERVE_PLAYLIST_COUNT,
                isAllowedTripPlaylistCount(value) ? value : DEFAULT_TRIP_RESERVE_PLAYLIST_COUNT).apply();
    }

    public int getTripReservePlaylistTrackLimit() {
        int value = preferences.getInt(KEY_TRIP_RESERVE_PLAYLIST_TRACK_LIMIT, DEFAULT_TRIP_RESERVE_PLAYLIST_TRACK_LIMIT);
        return isAllowedTripPlaylistTrackLimit(value) ? value : DEFAULT_TRIP_RESERVE_PLAYLIST_TRACK_LIMIT;
    }

    public void setTripReservePlaylistTrackLimit(int value) {
        preferences.edit().putInt(KEY_TRIP_RESERVE_PLAYLIST_TRACK_LIMIT,
                isAllowedTripPlaylistTrackLimit(value) ? value : DEFAULT_TRIP_RESERVE_PLAYLIST_TRACK_LIMIT).apply();
    }

    public long getTripReserveLastSyncMs() {
        return Math.max(0L, preferences.getLong(KEY_TRIP_RESERVE_LAST_SYNC_MS, 0L));
    }

    public void setTripReserveLastSyncMs(long value) {
        preferences.edit().putLong(KEY_TRIP_RESERVE_LAST_SYNC_MS, Math.max(0L, value)).apply();
    }

    public void reset() {
        preferences.edit().clear().apply();
    }

    private static boolean isAllowedStorageLimit(int value) {
        return value == 1 || value == 2 || value == 5 || value == 10;
    }

    private static boolean isAllowedReserve(int value) {
        return value == 256 || value == 512 || value == 1024;
    }

    private static boolean isAllowedRecentLimit(int value) {
        return value == 20 || value == 50 || value == 100;
    }

    private static boolean isAllowedListenThreshold(int value) {
        return value == 5 || value == 15 || value == 30 || value == 60;
    }

    private static boolean isAllowedTripRecentCount(int value) {
        return value == 10 || value == 20 || value == 30 || value == 50;
    }

    private static boolean isAllowedTripFavoriteCount(int value) {
        return value == 10 || value == 20 || value == 50 || value == 100;
    }

    private static boolean isAllowedTripPlaylistCount(int value) {
        return value >= 0 && value <= 3;
    }

    private static boolean isAllowedTripPlaylistTrackLimit(int value) {
        return value == 25 || value == 50 || value == 100;
    }
}
