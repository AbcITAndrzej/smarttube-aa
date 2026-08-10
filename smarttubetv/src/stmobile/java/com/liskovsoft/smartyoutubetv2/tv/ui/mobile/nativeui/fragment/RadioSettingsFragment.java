package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.adapter.RadioStationAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileFragmentSupport;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioStation;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioStationRepository;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Radio 2.0 directory. All new mechanisms are optional and shared safely with Android Auto. */
public final class RadioSettingsFragment extends Fragment {
    private static final int[] TIME_SHIFT_MINUTES = {1, 3, 5};
    private static final int STATION_WINDOW_STEP = 120;
    private static final long REMOTE_SEARCH_DEBOUNCE_MS = 450L;
    private static final int COLLECTION_ALL = 0;
    private static final int COLLECTION_FAVORITES = 1;
    private static final int COLLECTION_RECENT = 2;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private RadioStationRepository repository;
    private RadioPreferences radioPreferences;
    private MobileFeatureFlags featureFlags;
    private RadioStationAdapter adapter;
    private RecyclerView list;
    private ProgressBar progress;
    private MaterialButton sync;
    private TextView status;
    private TextView empty;
    private Spinner sort;
    private Spinner collection;
    private Spinner country;
    private Spinner tag;
    private Spinner timeShiftDuration;
    private SwitchMaterial timeShiftEnabled;
    private SwitchMaterial serverSearchEnabled;
    private SwitchMaterial recentEnabled;
    private SwitchMaterial failoverEnabled;
    private SwitchMaterial categoriesEnabled;
    private SwitchMaterial aaDirectoryEnabled;
    private SwitchMaterial liveOffsetEnabled;
    private TextInputEditText search;
    private View countryRow;
    private View tagRow;
    private int visibleLimit = STATION_WINDOW_STEP;
    private int remoteSearchSequence;
    private boolean rebuildingFilters;
    private boolean remoteSearchRunning;
    private String lastRemoteSearchSignature = "";
    private Runnable pendingRemoteSearch;
    private List<RadioStationRepository.FilterOption> countryOptions = new ArrayList<>();
    private List<RadioStationRepository.FilterOption> tagOptions = new ArrayList<>();

    private final RadioStationRepository.ChangeListener catalogListener = () -> {
        if (isAdded() && getView() != null) {
            setBusy(repository.isSyncing() || repository.isPageLoading() || remoteSearchRunning, false);
            refreshCategoryAdapters();
            renderStations();
        }
    };

    public static RadioSettingsFragment newInstance() { return new RadioSettingsFragment(); }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                 @Nullable ViewGroup container,
                                                 @Nullable Bundle state) {
        return inflater.inflate(R.layout.mobile_radio_settings_fragment, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repository = RadioStationRepository.get(requireContext());
        repository.addChangeListener(catalogListener);
        radioPreferences = new RadioPreferences(requireContext());
        featureFlags = new MobileFeatureFlags(requireContext());
        progress = view.findViewById(R.id.mobile_radio_progress);
        sync = view.findViewById(R.id.mobile_radio_sync);
        status = view.findViewById(R.id.mobile_radio_status);
        empty = view.findViewById(R.id.mobile_radio_empty);
        sort = view.findViewById(R.id.mobile_radio_sort);
        collection = view.findViewById(R.id.mobile_radio_collection);
        country = view.findViewById(R.id.mobile_radio_country);
        tag = view.findViewById(R.id.mobile_radio_tag);
        countryRow = view.findViewById(R.id.mobile_radio_country_row);
        tagRow = view.findViewById(R.id.mobile_radio_tag_row);
        search = view.findViewById(R.id.mobile_radio_search);
        timeShiftEnabled = view.findViewById(R.id.mobile_radio_timeshift_enabled);
        timeShiftDuration = view.findViewById(R.id.mobile_radio_timeshift_duration);
        serverSearchEnabled = view.findViewById(R.id.mobile_radio_server_search_enabled);
        recentEnabled = view.findViewById(R.id.mobile_radio_recent_enabled);
        failoverEnabled = view.findViewById(R.id.mobile_radio_failover_enabled);
        categoriesEnabled = view.findViewById(R.id.mobile_radio_categories_enabled);
        aaDirectoryEnabled = view.findViewById(R.id.mobile_radio_aa_directory_enabled);
        liveOffsetEnabled = view.findViewById(R.id.mobile_radio_live_offset_enabled);
        View advancedOptions = view.findViewById(R.id.mobile_radio_advanced_options);
        MaterialButton advancedToggle = view.findViewById(R.id.mobile_radio_advanced_toggle);
        advancedToggle.setOnClickListener(v -> {
            boolean show = advancedOptions.getVisibility() != View.VISIBLE;
            advancedOptions.setVisibility(show ? View.VISIBLE : View.GONE);
            advancedToggle.setText(show
                    ? R.string.mobile_radio_advanced_options_hide
                    : R.string.mobile_radio_advanced_options);
        });

        MaterialToolbar toolbar = view.findViewById(R.id.mobile_toolbar);
        toolbar.setNavigationIcon(R.drawable.mobile_ic_back_24);
        toolbar.setNavigationContentDescription(R.string.mobile_native_back);
        toolbar.setNavigationOnClickListener(v -> MobileFragmentSupport.navigator(this).goBack());

        adapter = new RadioStationAdapter(new RadioStationAdapter.Listener() {
            @Override public void onPlay(RadioStation station) {
                repository.reportClick(station.getId());
                MobileFragmentSupport.navigator(RadioSettingsFragment.this)
                        .openRadioPlayback(station.getId());
            }

            @Override public void onFavorite(RadioStation station) {
                repository.toggleFavorite(station.getId());
                renderStations();
            }
        });
        list = view.findViewById(R.id.mobile_radio_list);
        LinearLayoutManager radioLayout = new LinearLayoutManager(requireContext());
        list.setLayoutManager(radioLayout);
        list.setHasFixedSize(true);
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || adapter == null) return;
                int last = radioLayout.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 8) requestMoreStations();
            }
        });

        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.mobile_radio_sort_options, android.R.layout.simple_spinner_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sort.setAdapter(sortAdapter);
        sort.setOnItemSelectedListener(simpleSelection(this::resetWindowAndRender));

        ArrayAdapter<CharSequence> collectionAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.mobile_radio_collection_options, android.R.layout.simple_spinner_item);
        collectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        collection.setAdapter(collectionAdapter);
        collection.setOnItemSelectedListener(simpleSelection(this::resetWindowAndRender));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                resetWindowAndRender();
                scheduleRemoteSearch();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        ArrayAdapter<CharSequence> timeShiftAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.mobile_radio_timeshift_options, android.R.layout.simple_spinner_item);
        timeShiftAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timeShiftDuration.setAdapter(timeShiftAdapter);
        int currentMinutes = radioPreferences.getTimeShiftMinutes();
        int durationIndex = currentMinutes <= 1 ? 0 : currentMinutes <= 3 ? 1 : 2;
        timeShiftDuration.setSelection(durationIndex, false);
        timeShiftDuration.setEnabled(radioPreferences.isTimeShiftEnabled());
        timeShiftDuration.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View selected,
                                                 int position, long id) {
                int safe = Math.max(0, Math.min(position, TIME_SHIFT_MINUTES.length - 1));
                radioPreferences.setTimeShiftMinutes(TIME_SHIFT_MINUTES[safe]);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        timeShiftEnabled.setChecked(radioPreferences.isTimeShiftEnabled());
        timeShiftEnabled.setOnCheckedChangeListener((button, checked) -> {
            radioPreferences.setTimeShiftEnabled(checked);
            timeShiftDuration.setEnabled(checked);
        });

        bindRadio2Switches();
        sync.setOnClickListener(v -> synchronize());
        refreshCategoryAdapters();
        updateRadio2Visibility();
        renderStations();
        if (repository.isSyncing() || repository.isPageLoading()) setBusy(true, false);
        else if (!repository.hasStations()) synchronize();
    }

    private android.widget.AdapterView.OnItemSelectedListener simpleSelection(Runnable action) {
        return new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View selected,
                                                 int position, long id) {
                if (!rebuildingFilters && action != null) action.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
    }

    private void bindRadio2Switches() {
        boolean radio2 = featureFlags.isRadio2Enabled();
        serverSearchEnabled.setChecked(radioPreferences.isServerSearchEnabled());
        recentEnabled.setChecked(radioPreferences.isRecentStationsEnabled());
        failoverEnabled.setChecked(radioPreferences.isStreamFailoverEnabled());
        categoriesEnabled.setChecked(radioPreferences.isCategoriesEnabled());
        aaDirectoryEnabled.setChecked(radioPreferences.isEnhancedAndroidAutoDirectoryEnabled());
        liveOffsetEnabled.setChecked(radioPreferences.isLiveOffsetLabelEnabled());
        serverSearchEnabled.setEnabled(radio2 && featureFlags.isRadio2RemoteSearchEnabled());
        recentEnabled.setEnabled(radio2);
        failoverEnabled.setEnabled(radio2 && featureFlags.isRadio2StreamFailoverEnabled());
        categoriesEnabled.setEnabled(radio2);
        aaDirectoryEnabled.setEnabled(radio2 && featureFlags.isRadio2AndroidAutoEnabled());
        liveOffsetEnabled.setEnabled(radio2);

        serverSearchEnabled.setOnCheckedChangeListener((button, checked) -> {
            radioPreferences.setServerSearchEnabled(checked);
            if (checked) scheduleRemoteSearch();
        });
        recentEnabled.setOnCheckedChangeListener((button, checked) -> {
            radioPreferences.setRecentStationsEnabled(checked);
            if (!checked) {
                repository.clearRecentStations();
                if (collection != null && collection.getSelectedItemPosition() == COLLECTION_RECENT) {
                    collection.setSelection(COLLECTION_ALL);
                }
            }
            renderStations();
        });
        failoverEnabled.setOnCheckedChangeListener((button, checked) ->
                radioPreferences.setStreamFailoverEnabled(checked));
        categoriesEnabled.setOnCheckedChangeListener((button, checked) -> {
            radioPreferences.setCategoriesEnabled(checked);
            updateRadio2Visibility();
            resetWindowAndRender();
        });
        aaDirectoryEnabled.setOnCheckedChangeListener((button, checked) ->
                radioPreferences.setEnhancedAndroidAutoDirectoryEnabled(checked));
        liveOffsetEnabled.setOnCheckedChangeListener((button, checked) ->
                radioPreferences.setLiveOffsetLabelEnabled(checked));
    }

    private void updateRadio2Visibility() {
        boolean radio2 = featureFlags.isRadio2Enabled();
        boolean visible = radio2 && radioPreferences.isCategoriesEnabled();
        if (countryRow != null) countryRow.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (tagRow != null) tagRow.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (collection != null && collection.getSelectedItemPosition() == COLLECTION_RECENT
                && (!radio2 || !radioPreferences.isRecentStationsEnabled())) {
            collection.setSelection(COLLECTION_ALL);
        }
    }

    @Override public void onResume() {
        super.onResume();
        if (adapter != null) {
            refreshCategoryAdapters();
            renderStations();
        }
    }

    @Override public void onDestroyView() {
        if (repository != null) repository.removeChangeListener(catalogListener);
        if (pendingRemoteSearch != null) ui.removeCallbacks(pendingRemoteSearch);
        remoteSearchSequence++;
        adapter = null;
        list = null;
        progress = null;
        sync = null;
        status = null;
        empty = null;
        sort = null;
        collection = null;
        country = null;
        tag = null;
        timeShiftDuration = null;
        timeShiftEnabled = null;
        search = null;
        super.onDestroyView();
    }

    private void synchronize() {
        setBusy(true, true);
        repository.sync(new RadioStationRepository.SyncCallback() {
            @Override public void onSuccess(int stationCount) {
                if (!isAdded() || getView() == null) return;
                visibleLimit = STATION_WINDOW_STEP;
                setBusy(false, true);
                refreshCategoryAdapters();
                renderStations();
                Toast.makeText(requireContext(), getString(
                        R.string.mobile_radio_sync_done, stationCount), Toast.LENGTH_SHORT).show();
            }

            @Override public void onError(String message) {
                if (!isAdded() || getView() == null) return;
                setBusy(false, true);
                renderStations();
                Toast.makeText(requireContext(), getString(
                        R.string.mobile_radio_sync_error, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void scheduleRemoteSearch() {
        if (pendingRemoteSearch != null) ui.removeCallbacks(pendingRemoteSearch);
        pendingRemoteSearch = () -> runRemoteSearch(false);
        ui.postDelayed(pendingRemoteSearch, REMOTE_SEARCH_DEBOUNCE_MS);
    }

    private void runRemoteSearch(boolean force) {
        if (!isAdded() || repository == null || search == null) return;
        if (!featureFlags.isRadio2Enabled() || !featureFlags.isRadio2RemoteSearchEnabled()
                || !radioPreferences.isServerSearchEnabled()) return;
        String query = currentQuery().trim();
        if (query.length() < 2 && currentCountry().isEmpty() && currentTag().isEmpty()) return;
        String signature = query + "|" + currentCountry() + "|" + currentTag();
        if (!force && signature.equals(lastRemoteSearchSignature)) return;
        lastRemoteSearchSignature = signature;
        int sequence = ++remoteSearchSequence;
        remoteSearchRunning = true;
        setBusy(true, false);
        repository.searchRemote(query, currentCountry(), currentTag(),
                new RadioStationRepository.SearchCallback() {
                    @Override public void onSuccess(List<RadioStation> stations, int addedToCache) {
                        if (!isAdded() || getView() == null || sequence != remoteSearchSequence) return;
                        remoteSearchRunning = false;
                        setBusy(false, false);
                        refreshCategoryAdapters();
                        renderStations();
                    }

                    @Override public void onError(String message) {
                        if (!isAdded() || getView() == null || sequence != remoteSearchSequence) return;
                        remoteSearchRunning = false;
                        setBusy(false, false);
                        Toast.makeText(requireContext(), getString(
                                R.string.mobile_radio_server_search_error, message), Toast.LENGTH_SHORT).show();
                        renderStations();
                    }
                });
    }

    private void requestMoreStations() {
        if (repository == null || adapter == null) return;
        boolean favoritesOnly = currentCollection() == COLLECTION_FAVORITES;
        boolean recentOnly = currentCollection() == COLLECTION_RECENT;
        String query = currentQuery();
        int matching = repository.getMatchingStationCount(favoritesOnly, recentOnly, query,
                currentCountry(), currentTag());
        if (visibleLimit < matching) {
            visibleLimit = Math.min(matching, visibleLimit + STATION_WINDOW_STEP);
            renderStations();
            return;
        }
        if (favoritesOnly || recentOnly || !repository.isFullCatalogPagingEnabled()
                || repository.isCatalogEndReached() || repository.isPageLoading()
                || repository.isSyncing()) return;
        setBusy(true, false);
        repository.loadMore(new RadioStationRepository.PageCallback() {
            @Override public void onSuccess(int addedCount, int totalCount, boolean endReached) {
                if (!isAdded() || getView() == null) return;
                setBusy(false, false);
                if (addedCount > 0) visibleLimit += STATION_WINDOW_STEP;
                refreshCategoryAdapters();
                renderStations();
            }

            @Override public void onError(String message) {
                if (!isAdded() || getView() == null) return;
                setBusy(false, false);
                Toast.makeText(requireContext(), getString(
                        R.string.mobile_radio_page_error, message), Toast.LENGTH_LONG).show();
                renderStations();
            }
        });
    }

    private void setBusy(boolean value, boolean fullSync) {
        if (progress == null || sync == null || status == null) return;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        sync.setEnabled(!value && !repository.isSyncing() && !repository.isPageLoading());
        if (value) status.setText(fullSync
                ? R.string.mobile_radio_syncing
                : remoteSearchRunning ? R.string.mobile_radio_searching_server
                : R.string.mobile_radio_loading_more);
    }

    private void resetWindowAndRender() {
        visibleLimit = STATION_WINDOW_STEP;
        renderStations();
    }

    private String currentQuery() {
        return search == null || search.getText() == null ? "" : search.getText().toString();
    }

    private int currentCollection() {
        if (collection == null) return COLLECTION_ALL;
        return Math.max(COLLECTION_ALL, Math.min(COLLECTION_RECENT, collection.getSelectedItemPosition()));
    }

    private String currentCountry() {
        if (!featureFlags.isRadio2Enabled() || !radioPreferences.isCategoriesEnabled()
                || country == null) return "";
        int index = country.getSelectedItemPosition();
        return index <= 0 || index - 1 >= countryOptions.size()
                ? "" : countryOptions.get(index - 1).getValue();
    }

    private String currentTag() {
        if (!featureFlags.isRadio2Enabled() || !radioPreferences.isCategoriesEnabled()
                || tag == null) return "";
        int index = tag.getSelectedItemPosition();
        return index <= 0 || index - 1 >= tagOptions.size()
                ? "" : tagOptions.get(index - 1).getValue();
    }

    private void refreshCategoryAdapters() {
        if (country == null || tag == null || repository == null) return;
        String selectedCountry = currentCountry();
        String selectedTag = currentTag();
        rebuildingFilters = true;
        try {
            countryOptions = repository.getCountryOptions();
            List<Object> countries = new ArrayList<>();
            countries.add(getString(R.string.mobile_radio_all_countries));
            countries.addAll(countryOptions);
            ArrayAdapter<Object> countryAdapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, countries);
            countryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            country.setAdapter(countryAdapter);
            country.setSelection(findOption(countryOptions, selectedCountry) + 1, false);
            country.setOnItemSelectedListener(simpleSelection(() -> {
                resetWindowAndRender();
                scheduleRemoteSearch();
            }));

            tagOptions = repository.getTagOptions();
            List<Object> tags = new ArrayList<>();
            tags.add(getString(R.string.mobile_radio_all_genres));
            tags.addAll(tagOptions);
            ArrayAdapter<Object> tagAdapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, tags);
            tagAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            tag.setAdapter(tagAdapter);
            tag.setSelection(findOption(tagOptions, selectedTag) + 1, false);
            tag.setOnItemSelectedListener(simpleSelection(() -> {
                resetWindowAndRender();
                scheduleRemoteSearch();
            }));
        } finally {
            rebuildingFilters = false;
        }
    }

    private static int findOption(List<RadioStationRepository.FilterOption> values, String wanted) {
        if (wanted == null || wanted.isEmpty()) return -1;
        for (int i = 0; i < values.size(); i++) {
            if (wanted.equalsIgnoreCase(values.get(i).getValue())) return i;
        }
        return -1;
    }

    private void renderStations() {
        if (adapter == null || sort == null || collection == null) return;
        RadioStationRepository.SortMode[] modes = RadioStationRepository.SortMode.values();
        int selected = Math.max(0, Math.min(sort.getSelectedItemPosition(), modes.length - 1));
        int selectedCollection = currentCollection();
        boolean favoritesOnly = selectedCollection == COLLECTION_FAVORITES;
        boolean recentOnly = selectedCollection == COLLECTION_RECENT;
        String query = currentQuery();
        List<RadioStation> visible = repository.getStations(modes[selected], favoritesOnly, recentOnly,
                query, currentCountry(), currentTag(), visibleLimit);
        adapter.submit(visible);
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        int loaded = repository.getLoadedStationCount();
        int favorites = repository.getFavoriteCount();
        int recent = repository.getRecentCount();
        int matching = repository.getMatchingStationCount(favoritesOnly, recentOnly, query,
                currentCountry(), currentTag());
        long updated = repository.getLastSyncTimeMs();
        if (!repository.isSyncing() && !repository.isPageLoading() && !remoteSearchRunning) {
            String pagingState = repository.isFullCatalogPagingEnabled()
                    ? (repository.isCatalogEndReached()
                    ? getString(R.string.mobile_radio_catalog_complete)
                    : getString(R.string.mobile_radio_catalog_more_available))
                    : getString(R.string.mobile_radio_catalog_legacy_limit);
            if (updated > 0) {
                String formatted = DateFormat.getMediumDateFormat(requireContext()).format(new Date(updated))
                        + " " + DateFormat.getTimeFormat(requireContext()).format(new Date(updated));
                status.setText(getString(R.string.mobile_radio_status_radio2,
                        visible.size(), matching, loaded, favorites, recent, formatted, pagingState));
            } else {
                status.setText(getString(R.string.mobile_radio_status_radio2_no_date,
                        visible.size(), matching, loaded, favorites, recent, pagingState));
            }
        }
        if (list != null && query.trim().isEmpty() && !favoritesOnly && !recentOnly
                && repository.isFullCatalogPagingEnabled()
                && !repository.isCatalogEndReached() && !repository.isPageLoading()) {
            list.post(() -> {
                if (isAdded() && list != null && !list.canScrollVertically(1)) requestMoreStations();
            });
        }
    }
}
