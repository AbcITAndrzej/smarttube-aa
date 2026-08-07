package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.os.Bundle;
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
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.adapter.RadioStationAdapter;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileFragmentSupport;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioStation;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioStationRepository;
import java.util.Date;
import java.util.List;

/** Experimental phone/tablet radio directory. Android Auto intentionally remains untouched. */
public final class RadioSettingsFragment extends Fragment {
    private RadioStationRepository repository;
    private RadioStationAdapter adapter;
    private ProgressBar progress;
    private MaterialButton sync;
    private TextView status;
    private TextView empty;
    private Spinner sort;
    private SwitchMaterial favoritesOnly;

    public static RadioSettingsFragment newInstance() { return new RadioSettingsFragment(); }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                 @Nullable ViewGroup container,
                                                 @Nullable Bundle state) {
        return inflater.inflate(R.layout.mobile_radio_settings_fragment, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repository = RadioStationRepository.get(requireContext());
        progress = view.findViewById(R.id.mobile_radio_progress);
        sync = view.findViewById(R.id.mobile_radio_sync);
        status = view.findViewById(R.id.mobile_radio_status);
        empty = view.findViewById(R.id.mobile_radio_empty);
        sort = view.findViewById(R.id.mobile_radio_sort);
        favoritesOnly = view.findViewById(R.id.mobile_radio_favorites_only);

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
        RecyclerView list = view.findViewById(R.id.mobile_radio_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setHasFixedSize(true);
        list.setAdapter(adapter);

        ArrayAdapter<CharSequence> sortAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.mobile_radio_sort_options, android.R.layout.simple_spinner_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sort.setAdapter(sortAdapter);
        sort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View selected,
                                                 int position, long id) {
                renderStations();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        favoritesOnly.setOnCheckedChangeListener((button, checked) -> renderStations());
        sync.setOnClickListener(v -> synchronize());

        renderStations();
        if (repository.isSyncing()) setSyncing(true);
        else if (!repository.hasStations()) synchronize();
    }

    @Override public void onResume() {
        super.onResume();
        if (adapter != null) renderStations();
    }

    @Override public void onDestroyView() {
        adapter = null;
        progress = null;
        sync = null;
        status = null;
        empty = null;
        sort = null;
        favoritesOnly = null;
        super.onDestroyView();
    }

    private void synchronize() {
        setSyncing(true);
        repository.sync(new RadioStationRepository.SyncCallback() {
            @Override public void onSuccess(int stationCount) {
                if (!isAdded() || getView() == null) return;
                setSyncing(false);
                renderStations();
                Toast.makeText(requireContext(), getString(
                        R.string.mobile_radio_sync_done, stationCount), Toast.LENGTH_SHORT).show();
            }

            @Override public void onError(String message) {
                if (!isAdded() || getView() == null) return;
                setSyncing(false);
                renderStations();
                Toast.makeText(requireContext(), getString(
                        R.string.mobile_radio_sync_error, message), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setSyncing(boolean value) {
        if (progress == null || sync == null || status == null) return;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        sync.setEnabled(!value);
        if (value) status.setText(R.string.mobile_radio_syncing);
    }

    private void renderStations() {
        if (adapter == null || sort == null || favoritesOnly == null) return;
        RadioStationRepository.SortMode[] modes = RadioStationRepository.SortMode.values();
        int selected = Math.max(0, Math.min(sort.getSelectedItemPosition(), modes.length - 1));
        List<RadioStation> visible = repository.getStations(modes[selected],
                favoritesOnly.isChecked());
        adapter.submit(visible);
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        List<RadioStation> all = repository.getStations(modes[selected], false);
        int favorites = 0;
        for (RadioStation station : all) if (station.isFavorite()) favorites++;
        long updated = repository.getLastSyncTimeMs();
        if (!repository.isSyncing()) {
            if (updated > 0) {
                String formatted = DateFormat.getMediumDateFormat(requireContext()).format(new Date(updated))
                        + " " + DateFormat.getTimeFormat(requireContext()).format(new Date(updated));
                status.setText(getString(R.string.mobile_radio_status_synced,
                        all.size(), favorites, formatted));
            } else {
                status.setText(getString(R.string.mobile_radio_status, all.size(), favorites));
            }
        }
    }
}
