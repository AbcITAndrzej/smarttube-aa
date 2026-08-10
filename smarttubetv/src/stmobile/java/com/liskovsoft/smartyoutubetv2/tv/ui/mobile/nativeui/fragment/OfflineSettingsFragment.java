package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileFragmentSupport;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineCleanupResult;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineListenSaveRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineListenSaveService;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaStats;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistDownloadService;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineTripReserveRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineTripReserveService;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline storage policy/status plus Stage 7 passive saves and Stage 8 explicit playlists. */
public final class OfflineSettingsFragment extends Fragment {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private OfflineMediaRepository repository;
    private OfflineMediaPreferences preferences;
    private TextView status;
    private MaterialButton storageLimit;
    private MaterialButton freeReserve;
    private MaterialButton listenThreshold;
    private MaterialButton listenRecentLimit;
    private MaterialButton tripRecentCount;
    private MaterialButton tripFavoriteCount;
    private MaterialButton tripPlaylistCount;
    private MaterialButton tripPlaylistTrackLimit;
    private volatile boolean viewDestroyed;

    public static OfflineSettingsFragment newInstance() { return new OfflineSettingsFragment(); }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                 @Nullable ViewGroup container,
                                                 @Nullable Bundle state) {
        return inflater.inflate(R.layout.mobile_offline_settings_fragment, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewDestroyed = false;
        repository = OfflineMediaRepository.get(requireContext());
        preferences = repository.getPreferences();

        MaterialToolbar toolbar = view.findViewById(R.id.mobile_toolbar);
        toolbar.setNavigationIcon(R.drawable.mobile_ic_back_24);
        toolbar.setNavigationContentDescription(R.string.mobile_native_back);
        toolbar.setNavigationOnClickListener(v -> MobileFragmentSupport.navigator(this).goBack());

        status = view.findViewById(R.id.mobile_offline_status);
        storageLimit = view.findViewById(R.id.mobile_offline_storage_limit);
        freeReserve = view.findViewById(R.id.mobile_offline_free_reserve);
        listenThreshold = view.findViewById(R.id.mobile_offline_listen_threshold);
        listenRecentLimit = view.findViewById(R.id.mobile_offline_listen_recent_limit);
        tripRecentCount = view.findViewById(R.id.mobile_offline_trip_recent_count);
        tripFavoriteCount = view.findViewById(R.id.mobile_offline_trip_favorite_count);
        tripPlaylistCount = view.findViewById(R.id.mobile_offline_trip_playlist_count);
        tripPlaylistTrackLimit = view.findViewById(R.id.mobile_offline_trip_playlist_track_limit);
        SwitchMaterial enabled = view.findViewById(R.id.mobile_offline_foundation_enabled);
        SwitchMaterial autoCleanup = view.findViewById(R.id.mobile_offline_auto_cleanup);
        SwitchMaterial listenEnabled = view.findViewById(R.id.mobile_offline_listen_enabled);
        SwitchMaterial listenWifiOnly = view.findViewById(R.id.mobile_offline_listen_wifi_only);
        SwitchMaterial listenComplete = view.findViewById(R.id.mobile_offline_listen_complete_after_switch);
        SwitchMaterial playlistDownloads = view.findViewById(R.id.mobile_offline_playlist_downloads_enabled);
        SwitchMaterial playlistWifiOnly = view.findViewById(R.id.mobile_offline_playlist_wifi_only);
        SwitchMaterial tripEnabled = view.findViewById(R.id.mobile_offline_trip_enabled);
        SwitchMaterial tripWifiOnly = view.findViewById(R.id.mobile_offline_trip_wifi_only);
        SwitchMaterial tripFavorites = view.findViewById(R.id.mobile_offline_trip_favorites_enabled);

        enabled.setChecked(preferences.isFoundationEnabled());
        autoCleanup.setChecked(preferences.isAutoCleanupEnabled());
        listenEnabled.setChecked(preferences.isListenSaveEnabled());
        listenWifiOnly.setChecked(preferences.isListenSaveWifiOnly());
        listenComplete.setChecked(preferences.isListenSaveCompleteAfterSwitch());
        playlistDownloads.setChecked(preferences.isPlaylistDownloadsEnabled());
        playlistWifiOnly.setChecked(preferences.isPlaylistWifiOnly());
        tripEnabled.setChecked(preferences.isTripReserveEnabled());
        tripWifiOnly.setChecked(preferences.isTripReserveWifiOnly());
        tripFavorites.setChecked(preferences.isTripReserveFavoritesEnabled());
        enabled.setOnCheckedChangeListener((button, checked) -> {
            preferences.setFoundationEnabled(checked);
            refreshStats();
        });
        autoCleanup.setOnCheckedChangeListener((button, checked) -> {
            preferences.setAutoCleanupEnabled(checked);
            refreshStats();
        });
        listenEnabled.setOnCheckedChangeListener((button, checked) -> {
            preferences.setListenSaveEnabled(checked);
            if (checked) OfflineListenSaveService.wake(requireContext());
            else requireContext().stopService(new Intent(requireContext(), OfflineListenSaveService.class));
            refreshStats();
        });
        listenWifiOnly.setOnCheckedChangeListener((button, checked) -> {
            preferences.setListenSaveWifiOnly(checked);
            if (preferences.isListenSaveEnabled()) OfflineListenSaveService.wake(requireContext());
        });
        listenComplete.setOnCheckedChangeListener((button, checked) ->
                preferences.setListenSaveCompleteAfterSwitch(checked));
        playlistDownloads.setOnCheckedChangeListener((button, checked) -> {
            preferences.setPlaylistDownloadsEnabled(checked);
            if (!checked) requireContext().stopService(new Intent(requireContext(), OfflinePlaylistDownloadService.class));
            refreshStats();
        });
        playlistWifiOnly.setOnCheckedChangeListener((button, checked) -> {
            preferences.setPlaylistWifiOnly(checked);
            if (preferences.isPlaylistDownloadsEnabled()) OfflinePlaylistDownloadService.wake(requireContext());
        });
        tripEnabled.setOnCheckedChangeListener((button, checked) -> {
            preferences.setTripReserveEnabled(checked);
            Context appContext = requireContext().getApplicationContext();
            if (checked) {
                OfflineTripReserveService.force(appContext);
            } else {
                appContext.stopService(new Intent(appContext, OfflineTripReserveService.class));
                io.execute(() -> {
                    OfflineTripReserveRepository.get(appContext).releaseManagedPlaylists();
                    post(this::refreshStats);
                });
            }
            refreshStats();
        });
        tripWifiOnly.setOnCheckedChangeListener((button, checked) -> {
            preferences.setTripReserveWifiOnly(checked);
            if (preferences.isTripReserveEnabled()) OfflineTripReserveService.force(requireContext());
        });
        tripFavorites.setOnCheckedChangeListener((button, checked) -> {
            preferences.setTripReserveFavoritesEnabled(checked);
            if (preferences.isTripReserveEnabled()) OfflineTripReserveService.force(requireContext());
        });

        storageLimit.setOnClickListener(v -> chooseStorageLimit());
        freeReserve.setOnClickListener(v -> chooseFreeReserve());
        listenThreshold.setOnClickListener(v -> chooseListenThreshold());
        listenRecentLimit.setOnClickListener(v -> chooseListenRecentLimit());
        tripRecentCount.setOnClickListener(v -> chooseTripRecentCount());
        tripFavoriteCount.setOnClickListener(v -> chooseTripFavoriteCount());
        tripPlaylistCount.setOnClickListener(v -> chooseTripPlaylistCount());
        tripPlaylistTrackLimit.setOnClickListener(v -> chooseTripPlaylistTrackLimit());
        view.findViewById(R.id.mobile_offline_trip_prepare_now).setOnClickListener(v -> {
            if (!preferences.isTripReserveEnabled()) {
                Toast.makeText(requireContext(), R.string.mobile_offline_trip_enable_first, Toast.LENGTH_SHORT).show();
                return;
            }
            OfflineTripReserveService.force(requireContext());
            Toast.makeText(requireContext(), R.string.mobile_offline_trip_prepare_started, Toast.LENGTH_SHORT).show();
        });
        view.findViewById(R.id.mobile_offline_manage_listen_saved).setOnClickListener(v ->
                MobileFragmentSupport.navigator(this).openOfflineListenSaved());
        view.findViewById(R.id.mobile_offline_manage_playlists).setOnClickListener(v ->
                MobileFragmentSupport.navigator(this).openOfflinePlaylists());
        view.findViewById(R.id.mobile_offline_cleanup_now).setOnClickListener(v -> cleanupNow());
        view.findViewById(R.id.mobile_offline_clear_all).setOnClickListener(v -> confirmClearAll());
        updatePolicyLabels();
        refreshStats();
    }

    @Override public void onResume() {
        super.onResume();
        if (status != null) refreshStats();
    }

    @Override public void onDestroyView() {
        viewDestroyed = true;
        super.onDestroyView();
    }

    @Override public void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void chooseStorageLimit() {
        final int[] values = {1, 2, 5, 10};
        String[] labels = new String[values.length];
        int current = preferences.getStorageLimitGb();
        int checked = 1;
        for (int i = 0; i < values.length; i++) {
            labels[i] = getString(R.string.mobile_offline_gb_value, values[i]);
            if (values[i] == current) checked = i;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_offline_storage_limit_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    preferences.setStorageLimitGb(values[which]);
                    updatePolicyLabels();
                    refreshStats();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void chooseFreeReserve() {
        final int[] values = {256, 512, 1024};
        String[] labels = new String[values.length];
        int current = preferences.getReservedFreeMb();
        int checked = 1;
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i] >= 1024
                    ? getString(R.string.mobile_offline_gb_value, values[i] / 1024)
                    : getString(R.string.mobile_offline_mb_value, values[i]);
            if (values[i] == current) checked = i;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_offline_free_reserve_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    preferences.setReservedFreeMb(values[which]);
                    updatePolicyLabels();
                    refreshStats();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void chooseListenThreshold() {
        final int[] values = {5, 15, 30, 60};
        String[] labels = new String[values.length];
        int current = preferences.getListenSaveThresholdSec();
        int checked = 1;
        for (int i = 0; i < values.length; i++) {
            labels[i] = getString(R.string.mobile_offline_listen_seconds, values[i]);
            if (values[i] == current) checked = i;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_offline_listen_threshold_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    preferences.setListenSaveThresholdSec(values[which]);
                    updatePolicyLabels();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void chooseListenRecentLimit() {
        final int[] values = {20, 50, 100};
        String[] labels = new String[values.length];
        int current = preferences.getListenSaveRecentLimit();
        int checked = 1;
        for (int i = 0; i < values.length; i++) {
            labels[i] = getString(R.string.mobile_offline_listen_recent_count, values[i]);
            if (values[i] == current) checked = i;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_offline_listen_recent_limit_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    preferences.setListenSaveRecentLimit(values[which]);
                    updatePolicyLabels();
                    dialog.dismiss();
                    Context appContext = requireContext().getApplicationContext();
                    io.execute(() -> {
                        OfflineListenSaveRepository.get(appContext).pruneToLimit();
                        post(this::refreshStats);
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void chooseTripRecentCount() {
        final int[] values = {10, 20, 30, 50};
        chooseInt(R.string.mobile_offline_trip_recent_count_title, values,
                preferences.getTripReserveRecentCount(), value -> preferences.setTripReserveRecentCount(value));
    }

    private void chooseTripFavoriteCount() {
        final int[] values = {10, 20, 50, 100};
        chooseInt(R.string.mobile_offline_trip_favorite_count_title, values,
                preferences.getTripReserveFavoriteCount(), value -> preferences.setTripReserveFavoriteCount(value));
    }

    private void chooseTripPlaylistCount() {
        final int[] values = {0, 1, 2, 3};
        chooseInt(R.string.mobile_offline_trip_playlist_count_title, values,
                preferences.getTripReservePlaylistCount(), value -> preferences.setTripReservePlaylistCount(value));
    }

    private void chooseTripPlaylistTrackLimit() {
        final int[] values = {25, 50, 100};
        chooseInt(R.string.mobile_offline_trip_playlist_track_limit_title, values,
                preferences.getTripReservePlaylistTrackLimit(), value -> preferences.setTripReservePlaylistTrackLimit(value));
    }

    private void chooseInt(int titleRes, int[] values, int current, IntSetter setter) {
        String[] labels = new String[values.length];
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            labels[i] = Integer.toString(values[i]);
            if (values[i] == current) checked = i;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(titleRes)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    setter.set(values[which]);
                    updatePolicyLabels();
                    if (preferences.isTripReserveEnabled()) OfflineTripReserveService.force(requireContext());
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private interface IntSetter { void set(int value); }

    private void cleanupNow() {
        setStatus(getString(R.string.mobile_offline_working));
        io.execute(() -> {
            OfflineCleanupResult result = repository.cleanupNow();
            post(() -> {
                Toast.makeText(requireContext(), getString(R.string.mobile_offline_cleanup_result,
                        result.getRemovedItems(), formatBytes(result.getRemovedBytes())), Toast.LENGTH_SHORT).show();
                refreshStats();
            });
        });
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_offline_clear_title)
                .setMessage(R.string.mobile_offline_clear_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.mobile_offline_clear_confirm, (dialog, which) -> {
                    setStatus(getString(R.string.mobile_offline_working));
                    Context appContext = requireContext().getApplicationContext();
                    io.execute(() -> {
                        appContext.stopService(new Intent(appContext, OfflinePlaylistDownloadService.class));
                        appContext.stopService(new Intent(appContext, OfflineListenSaveService.class));
                        appContext.stopService(new Intent(appContext, OfflineTripReserveService.class));
                        OfflineTripReserveRepository.get(appContext).clearHistory();
                        OfflinePlaylistRepository.get(appContext).clearAll(false);
                        OfflineListenSaveRepository.get(appContext).clearAutoSaved();
                        repository.clearAll();
                        post(() -> {
                            Toast.makeText(requireContext(), R.string.mobile_offline_cleared,
                                    Toast.LENGTH_SHORT).show();
                            refreshStats();
                        });
                    });
                })
                .show();
    }

    private void refreshStats() {
        if (repository == null || !isAdded()) return;
        Context appContext = requireContext().getApplicationContext();
        io.execute(() -> {
            OfflineMediaStats stats = repository.getStats();
            boolean enabled = repository.isEnabled();
            int playlists = OfflinePlaylistRepository.get(appContext).playlistCount();
            int activePlaylists = OfflinePlaylistRepository.get(appContext).activeCount();
            OfflineListenSaveRepository listen = OfflineListenSaveRepository.get(appContext);
            int listenPending = listen.pendingCount();
            int listenDownloading = listen.downloadingCount();
            int listenAvailable = listen.availableCount();
            int listenFailed = listen.failedCount();
            OfflineTripReserveRepository trip = OfflineTripReserveRepository.get(appContext);
            int tripHistory = trip.historyCount();
            int tripPlaylistHistory = trip.playlistHistoryCount();
            int tripQueues = 0;
            for (com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistRecord record
                    : OfflinePlaylistRepository.get(appContext).list()) {
                if (OfflineTripReserveRepository.isTripReservePlaylistId(record.getPlaylistId())) tripQueues++;
            }
            final int tripQueueCount = tripQueues;
            post(() -> {
                String text = getString(R.string.mobile_offline_status_format,
                        enabled ? getString(R.string.mobile_offline_state_on)
                                : getString(R.string.mobile_offline_state_off),
                        stats.getTotalCount(), stats.getAvailableCount(), stats.getDownloadingCount(),
                        stats.getFailedCount(), stats.getExpiredCount(), formatBytes(stats.getTrackedBytes()),
                        formatBytes(stats.getStorageLimitBytes()), formatBytes(stats.getAvailableDeviceBytes()))
                        + "\n" + getString(R.string.mobile_offline_listen_summary_status,
                                listenAvailable, listenPending, listenDownloading, listenFailed)
                        + "\n" + getString(R.string.mobile_offline_playlist_summary_status, playlists, activePlaylists)
                        + "\n" + getString(R.string.mobile_offline_trip_status,
                                preferences.isTripReserveEnabled() ? getString(R.string.mobile_offline_state_on)
                                        : getString(R.string.mobile_offline_state_off),
                                tripHistory, tripPlaylistHistory, tripQueueCount);
                setStatus(text);
            });
        });
    }

    private void updatePolicyLabels() {
        if (storageLimit != null) {
            storageLimit.setText(getString(R.string.mobile_offline_storage_limit_button,
                    preferences.getStorageLimitGb()));
        }
        if (listenThreshold != null) {
            listenThreshold.setText(getString(R.string.mobile_offline_listen_threshold_button,
                    preferences.getListenSaveThresholdSec()));
        }
        if (listenRecentLimit != null) {
            listenRecentLimit.setText(getString(R.string.mobile_offline_listen_recent_limit_button,
                    preferences.getListenSaveRecentLimit()));
        }
        if (tripRecentCount != null) {
            tripRecentCount.setText(getString(R.string.mobile_offline_trip_recent_count_button,
                    preferences.getTripReserveRecentCount()));
        }
        if (tripFavoriteCount != null) {
            tripFavoriteCount.setText(getString(R.string.mobile_offline_trip_favorite_count_button,
                    preferences.getTripReserveFavoriteCount()));
        }
        if (tripPlaylistCount != null) {
            tripPlaylistCount.setText(getString(R.string.mobile_offline_trip_playlist_count_button,
                    preferences.getTripReservePlaylistCount()));
        }
        if (tripPlaylistTrackLimit != null) {
            tripPlaylistTrackLimit.setText(getString(R.string.mobile_offline_trip_playlist_track_limit_button,
                    preferences.getTripReservePlaylistTrackLimit()));
        }
        if (freeReserve != null) {
            int mb = preferences.getReservedFreeMb();
            String value = mb >= 1024 ? getString(R.string.mobile_offline_gb_value, mb / 1024)
                    : getString(R.string.mobile_offline_mb_value, mb);
            freeReserve.setText(getString(R.string.mobile_offline_free_reserve_button, value));
        }
    }

    private void setStatus(String value) {
        if (status != null) status.setText(value);
    }

    private void post(Runnable action) {
        Activity activity = getActivity();
        if (activity == null || viewDestroyed) return;
        activity.runOnUiThread(() -> {
            if (!viewDestroyed && isAdded()) action.run();
        });
    }

    private static String formatBytes(long bytes) {
        double value = Math.max(0L, bytes);
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024d && unit < units.length - 1) {
            value /= 1024d;
            unit++;
        }
        return unit == 0 ? String.format(Locale.US, "%.0f %s", value, units[unit])
                : String.format(Locale.US, "%.1f %s", value, units[unit]);
    }
}
