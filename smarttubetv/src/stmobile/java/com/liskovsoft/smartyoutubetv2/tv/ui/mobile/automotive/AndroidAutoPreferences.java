package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive;

import android.content.Context;
import android.content.SharedPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileMediaItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Local-only Android Auto configuration. It never modifies YouTube account data. */
public final class AndroidAutoPreferences {
    public static final String PREF_FILE = "smarttube_auto_settings";
    public static final String KEY_PLAYLIST_ORDER = "playlist_order";
    public static final String KEY_HIDDEN_PLAYLISTS = "hidden_playlists";
    public static final String KEY_EXPERIMENTAL_DRIVING_VIDEO = "experimental_driving_video";
    private static final String KEY_DEVELOPER_CONFIRMED = "developer_mode_confirmed";
    private static final String KEY_UNKNOWN_SOURCES_CONFIRMED = "unknown_sources_confirmed";
    private static final String ORDER_SEPARATOR = "\u001f";

    private final SharedPreferences preferences;

    public AndroidAutoPreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public boolean isDeveloperModeConfirmed() {
        return preferences.getBoolean(KEY_DEVELOPER_CONFIRMED, false);
    }

    public void setDeveloperModeConfirmed(boolean confirmed) {
        preferences.edit().putBoolean(KEY_DEVELOPER_CONFIRMED, confirmed).apply();
    }

    public boolean isUnknownSourcesConfirmed() {
        return preferences.getBoolean(KEY_UNKNOWN_SOURCES_CONFIRMED, false);
    }

    public void setUnknownSourcesConfirmed(boolean confirmed) {
        preferences.edit().putBoolean(KEY_UNKNOWN_SOURCES_CONFIRMED, confirmed).apply();
    }

    /** Off by default: normal Android Auto playback remains audio-only. */
    public boolean isExperimentalDrivingVideoEnabled() {
        return preferences.getBoolean(KEY_EXPERIMENTAL_DRIVING_VIDEO, false);
    }

    public void setExperimentalDrivingVideoEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_EXPERIMENTAL_DRIVING_VIDEO, enabled).apply();
    }

    public List<String> getPlaylistOrder() {
        String encoded = preferences.getString(KEY_PLAYLIST_ORDER, "");
        if (encoded == null || encoded.isEmpty()) return Collections.emptyList();
        String[] parts = encoded.split(ORDER_SEPARATOR, -1);
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isEmpty() && !result.contains(part)) result.add(part);
        }
        return result;
    }

    public Set<String> getHiddenPlaylists() {
        Set<String> stored = preferences.getStringSet(
                KEY_HIDDEN_PLAYLISTS, Collections.<String>emptySet());
        return stored == null ? new LinkedHashSet<>() : new LinkedHashSet<>(stored);
    }

    public void savePlaylistLayout(List<String> orderedKeys, Set<String> hiddenKeys) {
        StringBuilder encoded = new StringBuilder();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (orderedKeys != null) unique.addAll(orderedKeys);
        for (String key : unique) {
            if (key == null || key.isEmpty()) continue;
            if (encoded.length() > 0) encoded.append(ORDER_SEPARATOR);
            encoded.append(key);
        }
        preferences.edit()
                .putString(KEY_PLAYLIST_ORDER, encoded.toString())
                .putStringSet(KEY_HIDDEN_PLAYLISTS, hiddenKeys == null
                        ? Collections.<String>emptySet() : new LinkedHashSet<>(hiddenKeys))
                .apply();
    }

    public void clearPlaylistLayout() {
        preferences.edit()
                .remove(KEY_PLAYLIST_ORDER)
                .remove(KEY_HIDDEN_PLAYLISTS)
                .apply();
    }

    public List<String> orderAvailableKeys(Collection<String> availableKeys) {
        LinkedHashSet<String> remaining = new LinkedHashSet<>();
        if (availableKeys != null) remaining.addAll(availableKeys);
        List<String> result = new ArrayList<>();
        for (String storedKey : getPlaylistOrder()) {
            if (remaining.remove(storedKey)) result.add(storedKey);
        }
        result.addAll(remaining);
        return result;
    }

    public void registerListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    public static boolean isPlaylistLayoutKey(String key) {
        return KEY_PLAYLIST_ORDER.equals(key) || KEY_HIDDEN_PLAYLISTS.equals(key);
    }

    public static String playlistKey(MobileMediaItem item) {
        if (item == null) return "";
        String playlistId = item.getPlaylistId();
        if (playlistId != null && !playlistId.trim().isEmpty()) {
            return playlistId.startsWith("playlist:") ? playlistId : "playlist:" + playlistId;
        }
        String id = item.getId();
        return id == null ? "" : id;
    }
}
