package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileDiagnosticsStore;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Radio Browser client with lazy server-side paging and a persistent full-catalog cache. */
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

    public interface PageCallback {
        void onSuccess(int addedCount, int totalCount, boolean endReached);
        void onError(String message);
    }

    public interface SearchCallback {
        void onSuccess(List<RadioStation> stations, int addedToCache);
        void onError(String message);
    }

    public interface AlternativeStreamsCallback {
        void onResolved(List<String> streamUrls);
    }

    /** Small immutable country/tag entry for phone and Android Auto browse filters. */
    public static final class FilterOption {
        private final String value;
        private final String label;
        private final int stationCount;

        FilterOption(String value, String label, int stationCount) {
            this.value = value == null ? "" : value;
            this.label = label == null ? this.value : label;
            this.stationCount = Math.max(0, stationCount);
        }

        public String getValue() { return value; }
        public String getLabel() { return label; }
        public int getStationCount() { return stationCount; }

        @Override public String toString() {
            return stationCount > 0 ? label + " (" + stationCount + ")" : label;
        }
    }

    private static final String TAG = "P14-Radio";
    private static final String PREFS = "smarttube_mobile_radio";
    private static final String KEY_STATIONS = "stations_json"; // Stage <=2 migration only.
    private static final String KEY_FAVORITES = "favorite_station_ids";
    private static final String KEY_LAST_SYNC = "last_sync_ms";
    private static final String KEY_NEXT_OFFSET = "catalog_next_offset";
    private static final String KEY_END_REACHED = "catalog_end_reached";
    private static final String KEY_RECENT = "recent_station_ids_v2";
    private static final String API_ROOT = "https://de1.api.radio-browser.info";
    private static final String STATIONS_PATH = "/json/stations/search?hidebroken=true"
            + "&order=clickcount&reverse=true";
    private static final int PAGE_SIZE = 250;
    private static final int LEGACY_FIRST_PAGE_SIZE = 200;
    private static final int AUTOMOTIVE_DIRECTORY_LIMIT = 200;
    private static final int AUTOMOTIVE_FILTER_LIMIT = 160;
    private static final int REMOTE_SEARCH_LIMIT = 80;
    private static final int MAX_RECENT_STATIONS = 50;
    private static final int MAX_TAG_OPTIONS = 60;
    private static final String CACHE_FILE = "radio_catalog_v3.ndjson";
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
    private final AtomicBoolean pageLoading = new AtomicBoolean();
    private final Object lock = new Object();
    private final List<RadioStation> stations = new ArrayList<>();
    private final Set<String> favorites = new HashSet<>();
    private final List<String> recentStationIds = new ArrayList<>();
    private final Set<ChangeListener> changeListeners = new HashSet<>();
    private final File catalogFile;
    private final MobileFeatureFlags featureFlags;
    private final MobileDiagnosticsStore diagnostics;
    private final RadioPreferences radioPreferences;
    private volatile int nextOffset;
    private volatile boolean endReached;

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
        featureFlags = new MobileFeatureFlags(context);
        diagnostics = MobileDiagnosticsStore.get(context);
        radioPreferences = new RadioPreferences(context);
        catalogFile = new File(context.getFilesDir(), CACHE_FILE);
        Set<String> savedFavorites = preferences.getStringSet(KEY_FAVORITES,
                Collections.emptySet());
        if (savedFavorites != null) favorites.addAll(savedFavorites);
        restoreRecentStations();
        restoreStations();
        // Server offset counts catalog rows, not locally merged remote-search results.
        // Keep the persisted Stage-3 cursor authoritative once it exists; otherwise use the
        // restored catalog size only for migration from older caches.
        int savedOffset = preferences.getInt(KEY_NEXT_OFFSET, -1);
        nextOffset = savedOffset >= 0 ? savedOffset : stations.size();
        endReached = preferences.getBoolean(KEY_END_REACHED, false);
        diagnostics.onRadioCatalogState(getLoadedStationCount(), nextOffset, endReached);
        diagnostics.onRadioRecentChanged(getRecentCount());
    }

    public boolean hasStations() {
        synchronized (lock) { return !stations.isEmpty(); }
    }

    public boolean isSyncing() { return syncing.get(); }
    public boolean isPageLoading() { return pageLoading.get(); }
    public boolean isCatalogEndReached() { return endReached; }
    public boolean isFullCatalogPagingEnabled() { return featureFlags.isRadioCatalogPagingEnabled(); }
    public int getNextOffset() { return nextOffset; }

    public long getLastSyncTimeMs() { return preferences.getLong(KEY_LAST_SYNC, 0L); }

    public int getLoadedStationCount() {
        synchronized (lock) { return stations.size(); }
    }

    public int getFavoriteCount() {
        synchronized (lock) { return favorites.size(); }
    }

    public List<RadioStation> getStations(SortMode mode, boolean favoritesOnly) {
        return getStations(mode, favoritesOnly, false, "", "", "", Integer.MAX_VALUE);
    }

    /** Local search remains instant and works over every catalog page already cached on disk. */
    public List<RadioStation> getStations(SortMode mode, boolean favoritesOnly, String query) {
        return getStations(mode, favoritesOnly, false, query, "", "", Integer.MAX_VALUE);
    }

    /** Backward-compatible visible-window overload used by Stage 3. */
    public List<RadioStation> getStations(SortMode mode, boolean favoritesOnly,
                                          String query, int limit) {
        return getStations(mode, favoritesOnly, false, query, "", "", limit);
    }

    /** Radio 2.0 local query supporting Favorites/Recent, country and genre filters. */
    public List<RadioStation> getStations(SortMode mode, boolean favoritesOnly, boolean recentOnly,
                                          String query, String countryCode, String tag, int limit) {
        String needle = normalizeQuery(query);
        String wantedCountry = normalizeCountryCode(countryCode);
        String wantedTag = normalizeTag(tag);
        List<RadioStation> result = new ArrayList<>();
        Set<String> recentSet;
        synchronized (lock) {
            recentSet = recentOnly ? new LinkedHashSet<>(recentStationIds) : Collections.emptySet();
            for (RadioStation station : stations) {
                boolean favorite = favorites.contains(station.getId());
                if (favoritesOnly && !favorite) continue;
                if (recentOnly && !recentSet.contains(station.getId())) continue;
                if (!wantedCountry.isEmpty()
                        && !wantedCountry.equals(normalizeCountryCode(station.getCountryCode()))) continue;
                if (!wantedTag.isEmpty() && !hasTag(station, wantedTag)) continue;
                if (!matches(station, needle)) continue;
                result.add(station.withFavorite(favorite));
            }
        }
        if (recentOnly) {
            sortRecent(result);
        } else {
            sort(result, mode == null ? SortMode.POPULARITY : mode);
        }
        if (limit >= 0 && result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }
        return result;
    }

    /** Android Auto bounded list; older call kept for compatibility. */
    public List<RadioStation> getStationsForAutomotive(boolean favoritesOnly) {
        return getStations(SortMode.POPULARITY, favoritesOnly, false, "", "", "",
                AUTOMOTIVE_DIRECTORY_LIMIT);
    }

    public List<RadioStation> getRecentStationsForAutomotive() {
        return getStations(SortMode.POPULARITY, false, true, "", "", "",
                AUTOMOTIVE_FILTER_LIMIT);
    }

    public List<RadioStation> getStationsForAutomotiveCountry(String countryCode) {
        return getStations(SortMode.POPULARITY, false, false, "", countryCode, "",
                AUTOMOTIVE_FILTER_LIMIT);
    }

    public List<RadioStation> getStationsForAutomotiveTag(String tag) {
        return getStations(SortMode.POPULARITY, false, false, "", "", tag,
                AUTOMOTIVE_FILTER_LIMIT);
    }

    public int getMatchingStationCount(boolean favoritesOnly, String query) {
        return getMatchingStationCount(favoritesOnly, false, query, "", "");
    }

    public int getMatchingStationCount(boolean favoritesOnly, boolean recentOnly, String query,
                                       String countryCode, String tag) {
        String needle = normalizeQuery(query);
        String wantedCountry = normalizeCountryCode(countryCode);
        String wantedTag = normalizeTag(tag);
        int count = 0;
        synchronized (lock) {
            Set<String> recentSet = recentOnly
                    ? new HashSet<>(recentStationIds) : Collections.emptySet();
            for (RadioStation station : stations) {
                if (favoritesOnly && !favorites.contains(station.getId())) continue;
                if (recentOnly && !recentSet.contains(station.getId())) continue;
                if (!wantedCountry.isEmpty()
                        && !wantedCountry.equals(normalizeCountryCode(station.getCountryCode()))) continue;
                if (!wantedTag.isEmpty() && !hasTag(station, wantedTag)) continue;
                if (matches(station, needle)) count++;
            }
        }
        return count;
    }

    public int getRecentCount() {
        synchronized (lock) { return recentStationIds.size(); }
    }

    public List<FilterOption> getCountryOptions() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        synchronized (lock) {
            for (RadioStation station : stations) {
                String code = normalizeCountryCode(station.getCountryCode());
                if (code.isEmpty()) continue;
                counts.put(code, counts.containsKey(code) ? counts.get(code) + 1 : 1);
                String label = station.getCountry().trim();
                if (label.isEmpty()) label = code;
                if (!labels.containsKey(code) || labels.get(code).equals(code)) labels.put(code, label);
            }
        }
        List<FilterOption> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            result.add(new FilterOption(entry.getKey(), labels.get(entry.getKey()), entry.getValue()));
        }
        Collections.sort(result, (left, right) -> left.label.compareToIgnoreCase(right.label));
        return result;
    }

    public List<FilterOption> getTagOptions() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        synchronized (lock) {
            for (RadioStation station : stations) {
                for (String raw : station.getTags().split(",")) {
                    String tag = normalizeTag(raw);
                    if (tag.length() < 2 || tag.length() > 32) continue;
                    counts.put(tag, counts.containsKey(tag) ? counts.get(tag) + 1 : 1);
                }
            }
        }
        List<FilterOption> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            result.add(new FilterOption(entry.getKey(), entry.getKey(), entry.getValue()));
        }
        Collections.sort(result, (left, right) -> {
            int count = Integer.compare(right.stationCount, left.stationCount);
            return count != 0 ? count : left.label.compareToIgnoreCase(right.label);
        });
        if (result.size() > MAX_TAG_OPTIONS) {
            return new ArrayList<>(result.subList(0, MAX_TAG_OPTIONS));
        }
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

    /** Records a successful user selection. The list never leaves the device. */
    public void recordPlayed(String stationId) {
        if (!featureFlags.isRadio2Enabled() || !radioPreferences.isRecentStationsEnabled()
                || stationId == null || stationId.trim().isEmpty()) return;
        synchronized (lock) {
            recentStationIds.remove(stationId);
            recentStationIds.add(0, stationId);
            while (recentStationIds.size() > MAX_RECENT_STATIONS) {
                recentStationIds.remove(recentStationIds.size() - 1);
            }
            saveRecentStationsLocked();
        }
        diagnostics.onRadioRecentChanged(getRecentCount());
        notifyCatalogChanged();
    }

    public void clearRecentStations() {
        synchronized (lock) {
            recentStationIds.clear();
            saveRecentStationsLocked();
        }
        diagnostics.onRadioRecentChanged(0);
        notifyCatalogChanged();
    }

    /**
     * Searches the live Radio Browser database even when the full catalog has not been cached yet.
     * Results are merged into the local cache so they can immediately be favorited or played.
     */
    public void searchRemote(String query, String countryCode, String tag, SearchCallback callback) {
        String cleanQuery = query == null ? "" : query.trim();
        String cleanCountry = normalizeCountryCode(countryCode);
        String cleanTag = normalizeTag(tag);
        if (cleanQuery.length() < 2 && cleanCountry.isEmpty() && cleanTag.isEmpty()) {
            if (callback != null) main.post(() -> callback.onSuccess(Collections.emptyList(), 0));
            return;
        }
        diagnostics.onRadioRemoteSearchStarted(cleanQuery, cleanCountry, cleanTag);
        network.execute(() -> {
            try {
                LinkedHashMap<String, RadioStation> merged = new LinkedHashMap<>();
                if (!cleanQuery.isEmpty()) {
                    mergeByStream(merged, downloadSearch("name", cleanQuery, cleanCountry, cleanTag, false));
                    // When a genre filter is already active, adding another `tag=` parameter
                    // for the free-text query is ambiguous on Radio Browser. In that case search
                    // the station name inside the selected genre instead of emitting duplicate tags.
                    if (cleanTag.isEmpty()) {
                        mergeByStream(merged, downloadSearch("tag", cleanQuery, cleanCountry, "", false));
                    }
                    if (cleanCountry.isEmpty() && cleanTag.isEmpty()) {
                        mergeByStream(merged, downloadSearch("country", cleanQuery, "", "", false));
                    }
                } else {
                    mergeByStream(merged, downloadSearch("", "", cleanCountry, cleanTag, false));
                }
                List<RadioStation> values = new ArrayList<>(merged.values());
                int before;
                int after;
                synchronized (lock) {
                    before = stations.size();
                    Set<String> existingStreams = new HashSet<>();
                    for (RadioStation existing : stations) {
                        existingStreams.add(normalizedStreamKey(existing.getStreamUrl()));
                    }
                    mergePageLocked(values);
                    List<RadioStation> newlyDiscovered = new ArrayList<>();
                    for (RadioStation value : values) {
                        if (value != null && existingStreams.add(
                                normalizedStreamKey(value.getStreamUrl()))) {
                            newlyDiscovered.add(value);
                        }
                    }
                    appendCatalogFileLocked(newlyDiscovered);
                    after = stations.size();
                }
                int added = Math.max(0, after - before);
                diagnostics.onRadioRemoteSearchFinished(values.size(), added, null);
                if (added > 0) notifyCatalogChanged();
                List<RadioStation> response = applyFavoriteState(values);
                if (callback != null) main.post(() -> callback.onSuccess(response, added));
            } catch (Throwable error) {
                String message = message(error);
                diagnostics.onRadioRemoteSearchFinished(0, 0, error);
                MobileDiagnostics.warn(TAG, "remote search failed: " + message);
                if (callback != null) main.post(() -> callback.onError(message));
            }
        });
    }

    /** Resolves a bounded list of alternate stream URLs for the same logical station. */
    public void resolveAlternativeStreams(RadioStation station, AlternativeStreamsCallback callback) {
        if (station == null) {
            if (callback != null) main.post(() -> callback.onResolved(Collections.emptyList()));
            return;
        }
        List<String> local = findLocalAlternativeStreams(station, 6);
        if (local.size() >= 2) {
            diagnostics.onRadioFailoverCandidates(local.size());
            if (callback != null) main.post(() -> callback.onResolved(local));
            return;
        }
        network.execute(() -> {
            LinkedHashSet<String> urls = new LinkedHashSet<>(local);
            try {
                List<RadioStation> remote = downloadSearch("name", station.getName(),
                        normalizeCountryCode(station.getCountryCode()), "", true);
                for (RadioStation candidate : remote) {
                    if (!sameLogicalStation(station, candidate)) continue;
                    String url = candidate.getStreamUrl();
                    if (RadioStation.isSupportedStream(url)) urls.add(url);
                    if (urls.size() >= 6) break;
                }
            } catch (Throwable error) {
                MobileDiagnostics.debug(TAG, "alternate stream lookup failed: " + message(error));
            }
            List<String> response = new ArrayList<>(urls);
            diagnostics.onRadioFailoverCandidates(response.size());
            if (callback != null) main.post(() -> callback.onResolved(response));
        });
    }

    /** Refreshes page zero and resets the server cursor. Additional pages are fetched on scroll. */
    public void sync(SyncCallback callback) {
        if (pageLoading.get()) {
            if (callback != null) main.post(() -> callback.onError("Pobieranie strony katalogu już trwa"));
            return;
        }
        if (!syncing.compareAndSet(false, true)) {
            if (callback != null) main.post(() -> callback.onError("Synchronizacja już trwa"));
            return;
        }
        MobileDiagnostics.info(TAG, "sync start fullPaging=" + isFullCatalogPagingEnabled());
        network.execute(() -> {
            try {
                int limit = isFullCatalogPagingEnabled() ? PAGE_SIZE : LEGACY_FIRST_PAGE_SIZE;
                RadioPage page = downloadStations(0, limit);
                if (page.stations.isEmpty()) throw new IllegalStateException("Katalog nie zwrócił stacji");
                synchronized (lock) {
                    // Keep personalized entries from deeper pages/remote search across a manual
                    // catalog refresh. Otherwise a favorite found online could disappear until
                    // the user paged back to the same server offset again. These preserved rows
                    // do not change the server cursor (nextOffset is based on page.rawCount).
                    List<RadioStation> preserved = personalizedStationsLocked();
                    stations.clear();
                    mergePageLocked(page.stations);
                    mergePageLocked(preserved);
                    replaceCatalogFileLocked(new ArrayList<>(stations));
                }
                nextOffset = page.rawCount;
                endReached = !isFullCatalogPagingEnabled() || page.rawCount < limit;
                savePagingState();
                preferences.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
                syncing.set(false);
                int total = getLoadedStationCount();
                diagnostics.onRadioCatalogPage(total, nextOffset, endReached);
                MobileDiagnostics.info(TAG, "sync ready stations=" + total
                        + " nextOffset=" + nextOffset + " end=" + endReached);
                notifyCatalogChanged();
                if (callback != null) main.post(() -> callback.onSuccess(total));
            } catch (Throwable error) {
                syncing.set(false);
                String message = message(error);
                MobileDiagnostics.warn(TAG, "sync failed: " + message);
                if (callback != null) main.post(() -> callback.onError(message));
            }
        });
    }

    /** Loads the next Radio Browser page. No hard station-count cap remains when the flag is on. */
    public void loadMore(PageCallback callback) {
        if (syncing.get()) return;
        if (!isFullCatalogPagingEnabled()) {
            if (callback != null) main.post(() -> callback.onSuccess(0,
                    getLoadedStationCount(), true));
            return;
        }
        if (endReached) {
            // A legacy/disabled sync intentionally stopped at exactly 200 items. If the user
            // enables full-catalog paging later, continue from that old cursor without forcing
            // them to wipe favorites/cache. A genuine paged end remains final.
            if (nextOffset == LEGACY_FIRST_PAGE_SIZE) {
                endReached = false;
                savePagingState();
            } else {
                if (callback != null) main.post(() -> callback.onSuccess(0,
                        getLoadedStationCount(), true));
                return;
            }
        }
        if (!pageLoading.compareAndSet(false, true)) return;
        final int requestedOffset = nextOffset;
        diagnostics.onPaginationRequest("radio");
        MobileDiagnostics.debug(TAG, "page start offset=" + requestedOffset);
        network.execute(() -> {
            try {
                RadioPage page = downloadStations(requestedOffset, PAGE_SIZE);
                int before;
                int after;
                synchronized (lock) {
                    before = stations.size();
                    mergePageLocked(page.stations);
                    appendCatalogFileLocked(page.stations);
                    after = stations.size();
                }
                nextOffset = requestedOffset + page.rawCount;
                endReached = page.rawCount < PAGE_SIZE || page.rawCount == 0;
                savePagingState();
                pageLoading.set(false);
                diagnostics.onPaginationSuccess("radio", before, after, !endReached);
                diagnostics.onRadioCatalogPage(after, nextOffset, endReached);
                MobileDiagnostics.info(TAG, "page ready offset=" + requestedOffset
                        + " raw=" + page.rawCount + " stations=" + before + "->" + after
                        + " end=" + endReached);
                notifyCatalogChanged();
                if (callback != null) {
                    int added = Math.max(0, after - before);
                    main.post(() -> callback.onSuccess(added, after, endReached));
                }
            } catch (Throwable error) {
                pageLoading.set(false);
                diagnostics.onPaginationError("radio", error);
                String message = message(error);
                MobileDiagnostics.warn(TAG, "page failed offset=" + requestedOffset + ": " + message);
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

    private RadioPage downloadStations(int offset, int limit) throws Exception {
        HttpURLConnection connection = null;
        try {
            String address = API_ROOT + STATIONS_PATH + "&limit=" + Math.max(1, limit)
                    + "&offset=" + Math.max(0, offset);
            connection = open(address);
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
            return new RadioPage(new ArrayList<>(byStream.values()), array.length());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private int mergePageLocked(List<RadioStation> page) {
        LinkedHashMap<String, RadioStation> merged = new LinkedHashMap<>();
        for (RadioStation station : stations) merged.put(normalizedStreamKey(station.getStreamUrl()), station);
        int before = merged.size();
        if (page != null) {
            for (RadioStation station : page) {
                if (station == null) continue;
                String key = normalizedStreamKey(station.getStreamUrl());
                RadioStation previous = merged.get(key);
                if (previous == null || station.getClickCount() > previous.getClickCount()) {
                    merged.put(key, station);
                }
            }
        }
        stations.clear();
        stations.addAll(merged.values());
        return Math.max(0, merged.size() - before);
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
        if (catalogFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(catalogFile), StandardCharsets.UTF_8))) {
                LinkedHashMap<String, RadioStation> restored = new LinkedHashMap<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    String clean = line.trim();
                    if (clean.isEmpty()) continue;
                    RadioStation station = RadioStation.fromJson(new JSONObject(clean));
                    if (station == null) continue;
                    String key = normalizedStreamKey(station.getStreamUrl());
                    RadioStation previous = restored.get(key);
                    if (previous == null || station.getClickCount() > previous.getClickCount()) {
                        restored.put(key, station);
                    }
                }
                synchronized (lock) {
                    stations.clear();
                    stations.addAll(restored.values());
                }
                MobileDiagnostics.debug(TAG, "paged cache restored stations=" + stations.size());
                return;
            } catch (Throwable error) {
                MobileDiagnostics.warn(TAG, "paged cache restore failed: " + error.getMessage());
            }
        }
        restoreLegacyStations();
    }

    /** One-time migration from the Stage <=2 SharedPreferences JSON array. */
    private void restoreLegacyStations() {
        String saved = preferences.getString(KEY_STATIONS, "");
        if (saved == null || saved.trim().isEmpty()) return;
        try {
            JSONArray array = new JSONArray(saved);
            List<RadioStation> restored = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                RadioStation station = RadioStation.fromJson(array.optJSONObject(index));
                if (station != null) restored.add(station);
            }
            synchronized (lock) {
                stations.clear();
                mergePageLocked(restored);
                replaceCatalogFileLocked(stations);
            }
            preferences.edit().remove(KEY_STATIONS).apply();
            MobileDiagnostics.info(TAG, "migrated legacy radio cache stations=" + stations.size());
        } catch (Throwable error) {
            MobileDiagnostics.warn(TAG, "legacy cache restore failed: " + error.getMessage());
        }
    }

    private void replaceCatalogFileLocked(List<RadioStation> values) throws Exception {
        File temp = new File(catalogFile.getParentFile(), catalogFile.getName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(temp, false), StandardCharsets.UTF_8))) {
            if (values != null) for (RadioStation station : values) {
                if (station == null) continue;
                writer.write(station.toJson().toString());
                writer.newLine();
            }
        }
        if (catalogFile.exists() && !catalogFile.delete()) {
            throw new IllegalStateException("Nie udało się zastąpić cache stacji");
        }
        if (!temp.renameTo(catalogFile)) {
            throw new IllegalStateException("Nie udało się zapisać cache stacji");
        }
    }

    private void appendCatalogFileLocked(List<RadioStation> values) throws Exception {
        if (values == null || values.isEmpty()) return;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(catalogFile, true), StandardCharsets.UTF_8))) {
            for (RadioStation station : values) {
                if (station == null) continue;
                writer.write(station.toJson().toString());
                writer.newLine();
            }
        }
    }

    private List<RadioStation> personalizedStationsLocked() {
        List<RadioStation> result = new ArrayList<>();
        Set<String> recent = new HashSet<>(recentStationIds);
        for (RadioStation station : stations) {
            if (station != null && (favorites.contains(station.getId())
                    || recent.contains(station.getId()))) {
                result.add(station);
            }
        }
        return result;
    }

    private void savePagingState() {
        preferences.edit()
                .putInt(KEY_NEXT_OFFSET, nextOffset)
                .putBoolean(KEY_END_REACHED, endReached)
                .apply();
    }

    private void restoreRecentStations() {
        String raw = preferences.getString(KEY_RECENT, "");
        if (raw == null || raw.trim().isEmpty()) return;
        try {
            JSONArray array = new JSONArray(raw);
            synchronized (lock) {
                recentStationIds.clear();
                for (int i = 0; i < array.length() && recentStationIds.size() < MAX_RECENT_STATIONS; i++) {
                    String id = array.optString(i, "").trim();
                    if (!id.isEmpty() && !recentStationIds.contains(id)) recentStationIds.add(id);
                }
            }
        } catch (Throwable error) {
            MobileDiagnostics.warn(TAG, "recent restore failed: " + message(error));
        }
    }

    private void saveRecentStationsLocked() {
        JSONArray array = new JSONArray();
        for (String id : recentStationIds) array.put(id);
        preferences.edit().putString(KEY_RECENT, array.toString()).apply();
    }

    private List<RadioStation> downloadSearch(String field, String value, String countryCode,
                                              String tag, boolean exactName) throws Exception {
        StringBuilder address = new StringBuilder(API_ROOT).append(STATIONS_PATH)
                .append("&limit=").append(REMOTE_SEARCH_LIMIT)
                .append("&offset=0");
        String cleanField = field == null ? "" : field.trim();
        String cleanValue = value == null ? "" : value.trim();
        if (!cleanField.isEmpty() && !cleanValue.isEmpty()) {
            address.append('&').append(cleanField).append('=')
                    .append(urlEncode(cleanValue));
            if ("name".equals(cleanField) && exactName) address.append("&nameExact=true");
            if ("country".equals(cleanField) && exactName) address.append("&countryExact=true");
            if ("tag".equals(cleanField) && exactName) address.append("&tagExact=true");
        }
        String code = normalizeCountryCode(countryCode);
        if (!code.isEmpty()) address.append("&countrycode=").append(urlEncode(code));
        String wantedTag = normalizeTag(tag);
        if (!wantedTag.isEmpty() && !("tag".equals(cleanField)
                && wantedTag.equals(normalizeTag(cleanValue)))) {
            address.append("&tag=").append(urlEncode(wantedTag)).append("&tagExact=true");
        }
        HttpURLConnection connection = null;
        try {
            connection = open(address.toString());
            int codeResponse = connection.getResponseCode();
            if (codeResponse < 200 || codeResponse >= 300) {
                throw new IllegalStateException("HTTP " + codeResponse);
            }
            JSONArray array = new JSONArray(readAll(connection.getInputStream()));
            List<RadioStation> result = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject json = array.optJSONObject(i);
                if (json == null || json.optInt("lastcheckok", 0) != 1) continue;
                RadioStation station = RadioStation.fromJson(json);
                if (station != null) result.add(station);
            }
            return result;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private List<RadioStation> applyFavoriteState(List<RadioStation> values) {
        List<RadioStation> result = new ArrayList<>();
        synchronized (lock) {
            for (RadioStation station : values) {
                if (station != null) result.add(station.withFavorite(favorites.contains(station.getId())));
            }
        }
        return result;
    }

    private static void mergeByStream(Map<String, RadioStation> target, List<RadioStation> values) {
        if (values == null) return;
        for (RadioStation station : values) {
            if (station == null) continue;
            String key = normalizedStreamKey(station.getStreamUrl());
            RadioStation previous = target.get(key);
            if (previous == null || station.getClickCount() > previous.getClickCount()) {
                target.put(key, station);
            }
        }
    }

    private List<String> findLocalAlternativeStreams(RadioStation station, int limit) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (station == null) return new ArrayList<>();
        urls.add(station.getStreamUrl());
        List<RadioStation> candidates = new ArrayList<>();
        synchronized (lock) {
            for (RadioStation candidate : stations) {
                if (candidate == null || !sameLogicalStation(station, candidate)) continue;
                candidates.add(candidate);
            }
        }
        sort(candidates, SortMode.POPULARITY);
        for (RadioStation candidate : candidates) {
            if (RadioStation.isSupportedStream(candidate.getStreamUrl())) urls.add(candidate.getStreamUrl());
            if (urls.size() >= Math.max(1, limit)) break;
        }
        return new ArrayList<>(urls);
    }

    private void sortRecent(List<RadioStation> values) {
        final Map<String, Integer> order = new LinkedHashMap<>();
        synchronized (lock) {
            for (int i = 0; i < recentStationIds.size(); i++) order.put(recentStationIds.get(i), i);
        }
        Collections.sort(values, (left, right) -> Integer.compare(
                order.containsKey(left.getId()) ? order.get(left.getId()) : Integer.MAX_VALUE,
                order.containsKey(right.getId()) ? order.get(right.getId()) : Integer.MAX_VALUE));
    }

    private static boolean sameLogicalStation(RadioStation left, RadioStation right) {
        if (left == null || right == null) return false;
        String leftName = normalizeStationName(left.getName());
        String rightName = normalizeStationName(right.getName());
        if (leftName.isEmpty() || !leftName.equals(rightName)) return false;
        String lc = normalizeCountryCode(left.getCountryCode());
        String rc = normalizeCountryCode(right.getCountryCode());
        return lc.isEmpty() || rc.isEmpty() || lc.equals(rc);
    }

    private static boolean hasTag(RadioStation station, String wantedTag) {
        if (station == null || wantedTag == null || wantedTag.isEmpty()) return true;
        for (String raw : station.getTags().split(",")) {
            if (wantedTag.equals(normalizeTag(raw))) return true;
        }
        return false;
    }

    private static String normalizeQuery(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCountryCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.US);
    }

    private static String normalizeTag(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeStationName(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    private static String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
    }

    private static boolean matches(RadioStation station, String needle) {
        if (needle == null || needle.isEmpty()) return true;
        String searchable = (station.getName() + " " + station.getCountry() + " "
                + station.getCountryCode() + " " + station.getCodec() + " "
                + station.getTags()).toLowerCase(Locale.ROOT);
        return searchable.contains(needle);
    }

    private static void sort(List<RadioStation> values, SortMode mode) {
        Comparator<RadioStation> comparator;
        if (mode == SortMode.NAME) {
            Collator collator = Collator.getInstance(Locale.getDefault());
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

    private static String message(Throwable error) {
        if (error == null) return "unknown error";
        String value = error.getMessage();
        return value == null || value.trim().isEmpty()
                ? error.getClass().getSimpleName() : value;
    }

    private static final class RadioPage {
        final List<RadioStation> stations;
        final int rawCount;

        RadioPage(List<RadioStation> stations, int rawCount) {
            this.stations = stations == null ? Collections.emptyList() : stations;
            this.rawCount = Math.max(0, rawCount);
        }
    }
}
