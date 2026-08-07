package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Radio Browser client plus a small persistent station/favorites cache. */
public final class RadioStationRepository {
    public enum SortMode { POPULARITY, NAME, BITRATE }

    /** Notifies connected surfaces when favorites or the cached station catalog change. */
    public interface ChangeListener {
        void onRadioCatalogChanged();
    }

    public interface SyncCallback {
        void onSuccess(int stationCount);
        void onError(String message);
    }

    private static final String TAG = "P14-Radio";
    private static final String PREFS = "smarttube_mobile_radio";
    private static final String KEY_STATIONS = "stations_json";
    private static final String KEY_FAVORITES = "favorite_station_ids";
    private static final String KEY_LAST_SYNC = "last_sync_ms";
    private static final String API_ROOT = "https://de1.api.radio-browser.info";
    private static final String STATIONS_PATH = "/json/stations/search?countrycode=PL"
            + "&hidebroken=true&order=clickcount&reverse=true&limit=200";
    private static final String USER_AGENT = "SmartTube-AA/32.04 "
            + "(experimental Radio Browser client; github.com/AbcITAndrzej/smarttube-aa)";
    private static volatile RadioStationRepository instance;

    private final SharedPreferences preferences;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SmartTube-RadioBrowser");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean syncing = new AtomicBoolean();
    private final Object lock = new Object();
    private final List<RadioStation> stations = new ArrayList<>();
    private final Set<String> favorites = new HashSet<>();
    private final Set<ChangeListener> changeListeners = new HashSet<>();

    public static RadioStationRepository get(Context context) {
        RadioStationRepository current = instance;
        if (current == null) {
            synchronized (RadioStationRepository.class) {
                current = instance;
                if (current == null) {
                    current = new RadioStationRepository(context.getApplicationContext());
                    instance = current;
                }
            }
        }
        return current;
    }

    private RadioStationRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> savedFavorites = preferences.getStringSet(KEY_FAVORITES,
                Collections.emptySet());
        if (savedFavorites != null) favorites.addAll(savedFavorites);
        restoreStations();
    }

    public boolean hasStations() {
        synchronized (lock) { return !stations.isEmpty(); }
    }

    public boolean isSyncing() { return syncing.get(); }

    public long getLastSyncTimeMs() { return preferences.getLong(KEY_LAST_SYNC, 0L); }

    public List<RadioStation> getStations(SortMode mode, boolean favoritesOnly) {
        List<RadioStation> result = new ArrayList<>();
        synchronized (lock) {
            for (RadioStation station : stations) {
                boolean favorite = favorites.contains(station.getId());
                if (!favoritesOnly || favorite) result.add(station.withFavorite(favorite));
            }
        }
        sort(result, mode == null ? SortMode.POPULARITY : mode);
        return result;
    }

    public RadioStation getStation(String stationId) {
        if (stationId == null) return null;
        synchronized (lock) {
            for (RadioStation station : stations) {
                if (stationId.equals(station.getId())) {
                    return station.withFavorite(favorites.contains(stationId));
                }
            }
        }
        return null;
    }

    public void addChangeListener(ChangeListener listener) {
        if (listener == null) return;
        synchronized (lock) { changeListeners.add(listener); }
    }

    public void removeChangeListener(ChangeListener listener) {
        if (listener == null) return;
        synchronized (lock) { changeListeners.remove(listener); }
    }

    public boolean toggleFavorite(String stationId) {
        if (stationId == null || stationId.trim().isEmpty()) return false;
        boolean favorite;
        synchronized (lock) {
            if (favorites.contains(stationId)) {
                favorites.remove(stationId);
                favorite = false;
            } else {
                favorites.add(stationId);
                favorite = true;
            }
            preferences.edit().putStringSet(KEY_FAVORITES, new HashSet<>(favorites)).apply();
        }
        MobileDiagnostics.debug(TAG, "favorite id=" + stationId + " enabled=" + favorite);
        notifyCatalogChanged();
        return favorite;
    }

    public void sync(SyncCallback callback) {
        if (!syncing.compareAndSet(false, true)) {
            if (callback != null) main.post(() -> callback.onError("Synchronizacja już trwa"));
            return;
        }
        MobileDiagnostics.info(TAG, "sync start");
        network.execute(() -> {
            try {
                List<RadioStation> loaded = downloadStations();
                if (loaded.isEmpty()) throw new IllegalStateException("Katalog nie zwrócił stacji");
                synchronized (lock) {
                    stations.clear();
                    stations.addAll(loaded);
                    persistStationsLocked();
                }
                preferences.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
                syncing.set(false);
                MobileDiagnostics.info(TAG, "sync ready stations=" + loaded.size());
                notifyCatalogChanged();
                if (callback != null) main.post(() -> callback.onSuccess(loaded.size()));
            } catch (Throwable error) {
                syncing.set(false);
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                MobileDiagnostics.warn(TAG, "sync failed: " + message);
                if (callback != null) main.post(() -> callback.onError(message));
            }
        });
    }

    public void reportClick(String stationId) {
        if (stationId == null || stationId.trim().isEmpty()) return;
        network.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String encoded = URLEncoder.encode(stationId, StandardCharsets.UTF_8.name());
                connection = open(API_ROOT + "/json/url/" + encoded);
                int code = connection.getResponseCode();
                closeQuietly(code >= 200 && code < 300
                        ? connection.getInputStream() : connection.getErrorStream());
                MobileDiagnostics.debug(TAG, "click id=" + stationId + " response=" + code);
            } catch (Throwable error) {
                MobileDiagnostics.warn(TAG, "click report failed: " + error.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public static String mediaId(String stationId) { return "radio:" + stationId; }

    public static boolean isRadioMediaId(String mediaId) {
        return mediaId != null && mediaId.startsWith("radio:") && mediaId.length() > 6;
    }

    public static String stationIdFromMediaId(String mediaId) {
        return isRadioMediaId(mediaId) ? mediaId.substring(6) : "";
    }

    private void notifyCatalogChanged() {
        final List<ChangeListener> listeners;
        synchronized (lock) {
            listeners = new ArrayList<>(changeListeners);
        }
        main.post(() -> {
            for (ChangeListener listener : listeners) {
                try {
                    listener.onRadioCatalogChanged();
                } catch (Throwable error) {
                    MobileDiagnostics.warn(TAG,
                            "catalog listener failed: " + error.getMessage());
                }
            }
        });
    }

    private List<RadioStation> downloadStations() throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = open(API_ROOT + STATIONS_PATH);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            String body = readAll(connection.getInputStream());
            JSONArray array = new JSONArray(body);
            Map<String, RadioStation> byStream = new LinkedHashMap<>();
            for (int index = 0; index < array.length(); index++) {
                JSONObject value = array.optJSONObject(index);
                if (value == null || value.optInt("lastcheckok", 0) != 1) continue;
                RadioStation station = RadioStation.fromJson(value);
                if (station == null) continue;
                String key = normalizedStreamKey(station.getStreamUrl());
                RadioStation previous = byStream.get(key);
                if (previous == null || station.getClickCount() > previous.getClickCount()) {
                    byStream.put(key, station);
                }
            }
            return new ArrayList<>(byStream.values());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private HttpURLConnection open(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        return connection;
    }

    private void restoreStations() {
        String saved = preferences.getString(KEY_STATIONS, "");
        if (saved == null || saved.trim().isEmpty()) return;
        try {
            JSONArray array = new JSONArray(saved);
            synchronized (lock) {
                stations.clear();
                for (int index = 0; index < array.length(); index++) {
                    RadioStation station = RadioStation.fromJson(array.optJSONObject(index));
                    if (station != null) stations.add(station);
                }
            }
            MobileDiagnostics.debug(TAG, "cache restored stations=" + stations.size());
        } catch (Throwable error) {
            MobileDiagnostics.warn(TAG, "cache restore failed: " + error.getMessage());
        }
    }

    private void persistStationsLocked() throws Exception {
        JSONArray array = new JSONArray();
        for (RadioStation station : stations) array.put(station.toJson());
        if (!preferences.edit().putString(KEY_STATIONS, array.toString()).commit()) {
            throw new IllegalStateException("Nie udało się zapisać cache stacji");
        }
    }

    private static void sort(List<RadioStation> values, SortMode mode) {
        Comparator<RadioStation> comparator;
        if (mode == SortMode.NAME) {
            Collator collator = Collator.getInstance(new Locale("pl", "PL"));
            collator.setStrength(Collator.PRIMARY);
            comparator = (left, right) -> collator.compare(left.getName(), right.getName());
        } else if (mode == SortMode.BITRATE) {
            comparator = (left, right) -> {
                int value = Integer.compare(right.getBitrate(), left.getBitrate());
                return value != 0 ? value : left.getName().compareToIgnoreCase(right.getName());
            };
        } else {
            comparator = (left, right) -> {
                int value = Integer.compare(right.getClickCount(), left.getClickCount());
                return value != 0 ? value : left.getName().compareToIgnoreCase(right.getName());
            };
        }
        Collections.sort(values, comparator);
    }

    private static String normalizedStreamKey(String stream) {
        String key = stream == null ? "" : stream.trim().toLowerCase(Locale.US);
        int fragment = key.indexOf('#');
        if (fragment >= 0) key = key.substring(0, fragment);
        while (key.endsWith("/")) key = key.substring(0, key.length() - 1);
        return key;
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private static void closeQuietly(InputStream input) {
        if (input == null) return;
        try { input.close(); } catch (Throwable ignored) { }
    }
}
