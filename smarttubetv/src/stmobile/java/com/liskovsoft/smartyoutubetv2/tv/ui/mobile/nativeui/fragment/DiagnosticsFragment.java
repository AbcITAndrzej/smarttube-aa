package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileFragmentSupport;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileDiagnosticsStore;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.performance.MobilePerformanceMonitor;

/** User-visible local diagnostics. Reports are copied to clipboard only; nothing is uploaded. */
public final class DiagnosticsFragment extends Fragment {
    private MobileDiagnosticsStore diagnostics;
    private MobileFeatureFlags featureFlags;
    private TextView report;
    private SwitchMaterial recentEvents;

    public static DiagnosticsFragment newInstance() {
        return new DiagnosticsFragment();
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                 @Nullable ViewGroup container,
                                                 @Nullable Bundle state) {
        return inflater.inflate(R.layout.mobile_diagnostics_fragment, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        diagnostics = MobileDiagnosticsStore.get(requireContext());
        featureFlags = new MobileFeatureFlags(requireContext());
        report = view.findViewById(R.id.mobile_diagnostics_report);

        MaterialToolbar toolbar = view.findViewById(R.id.mobile_toolbar);
        toolbar.setNavigationIcon(R.drawable.mobile_ic_back_24);
        toolbar.setNavigationContentDescription(R.string.mobile_native_back);
        toolbar.setNavigationOnClickListener(v -> MobileFragmentSupport.navigator(this).goBack());

        SwitchMaterial capture = view.findViewById(R.id.mobile_diagnostics_capture);
        recentEvents = view.findViewById(R.id.mobile_diagnostics_recent_events);
        SwitchMaterial searchPaging = view.findViewById(R.id.mobile_diagnostics_paging_search);
        SwitchMaterial channelPaging = view.findViewById(R.id.mobile_diagnostics_paging_channel);
        SwitchMaterial radioPaging = view.findViewById(R.id.mobile_diagnostics_paging_radio);
        SwitchMaterial smartPlayer = view.findViewById(R.id.mobile_diagnostics_smart_player);
        SwitchMaterial radio2Master = view.findViewById(R.id.mobile_diagnostics_radio2_master);
        SwitchMaterial radio2Search = view.findViewById(R.id.mobile_diagnostics_radio2_search);
        SwitchMaterial radio2Failover = view.findViewById(R.id.mobile_diagnostics_radio2_failover);
        SwitchMaterial radio2Aa = view.findViewById(R.id.mobile_diagnostics_radio2_aa);
        SwitchMaterial offlineFoundation = view.findViewById(R.id.mobile_diagnostics_offline_foundation);
        SwitchMaterial offlineListenSave = view.findViewById(R.id.mobile_diagnostics_offline_listen_save);
        SwitchMaterial offlinePlaylists = view.findViewById(R.id.mobile_diagnostics_offline_playlists);
        SwitchMaterial offlineAndroidAuto = view.findViewById(R.id.mobile_diagnostics_offline_android_auto);
        SwitchMaterial offlineTripReserve = view.findViewById(R.id.mobile_diagnostics_offline_trip_reserve);
        SwitchMaterial media3Master = view.findViewById(R.id.mobile_diagnostics_media3_master);
        SwitchMaterial media3Radio = view.findViewById(R.id.mobile_diagnostics_media3_radio);
        SwitchMaterial media3Offline = view.findViewById(R.id.mobile_diagnostics_media3_offline);
        SwitchMaterial media3Fallback = view.findViewById(R.id.mobile_diagnostics_media3_fallback);
        SwitchMaterial performanceMonitor = view.findViewById(R.id.mobile_diagnostics_performance_monitor);
        SwitchMaterial frameSampling = view.findViewById(R.id.mobile_diagnostics_performance_frames);
        capture.setChecked(featureFlags.isDiagnosticsCaptureEnabled());
        recentEvents.setChecked(featureFlags.isRecentEventsEnabled());
        searchPaging.setChecked(featureFlags.isSearchPagingEnabled());
        channelPaging.setChecked(featureFlags.isChannelPagingEnabled());
        radioPaging.setChecked(featureFlags.isRadioCatalogPagingEnabled());
        smartPlayer.setChecked(featureFlags.isSmartPlayerUxEnabled());
        radio2Master.setChecked(featureFlags.isRadio2Enabled());
        radio2Search.setChecked(featureFlags.isRadio2RemoteSearchEnabled());
        radio2Failover.setChecked(featureFlags.isRadio2StreamFailoverEnabled());
        radio2Aa.setChecked(featureFlags.isRadio2AndroidAutoEnabled());
        offlineFoundation.setChecked(featureFlags.isOfflineFoundationEnabled());
        offlineListenSave.setChecked(featureFlags.isOfflineListenSaveEnabled());
        offlinePlaylists.setChecked(featureFlags.isOfflinePlaylistsEnabled());
        offlineAndroidAuto.setChecked(featureFlags.isOfflineAndroidAutoEnabled());
        offlineTripReserve.setChecked(featureFlags.isOfflineTripReserveEnabled());
        media3Master.setChecked(featureFlags.isMedia3EngineEnabled());
        media3Radio.setChecked(featureFlags.isMedia3RadioEnabled());
        media3Offline.setChecked(featureFlags.isMedia3OfflineEnabled());
        media3Fallback.setChecked(featureFlags.isMedia3LegacyFallbackEnabled());
        performanceMonitor.setChecked(featureFlags.isPerformanceMonitoringEnabled());
        frameSampling.setChecked(featureFlags.isPerformanceFrameSamplingEnabled());

        capture.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setDiagnosticsCaptureEnabled(checked);
            diagnostics.syncCaptureFlag();
            render();
        });
        recentEvents.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setRecentEventsEnabled(checked);
            render();
        });
        searchPaging.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setSearchPagingEnabled(checked);
            render();
        });
        channelPaging.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setChannelPagingEnabled(checked);
            render();
        });
        radioPaging.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setRadioCatalogPagingEnabled(checked);
            render();
        });
        smartPlayer.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setSmartPlayerUxEnabled(checked);
            render();
        });
        radio2Master.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setRadio2Enabled(checked);
            render();
        });
        radio2Search.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setRadio2RemoteSearchEnabled(checked);
            render();
        });
        radio2Failover.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setRadio2StreamFailoverEnabled(checked);
            render();
        });
        radio2Aa.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setRadio2AndroidAutoEnabled(checked);
            render();
        });
        offlineFoundation.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setOfflineFoundationEnabled(checked);
            render();
        });
        offlineListenSave.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setOfflineListenSaveEnabled(checked);
            render();
        });
        offlinePlaylists.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setOfflinePlaylistsEnabled(checked);
            render();
        });
        offlineAndroidAuto.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setOfflineAndroidAutoEnabled(checked);
            render();
        });
        offlineTripReserve.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setOfflineTripReserveEnabled(checked);
            render();
        });
        media3Master.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setMedia3EngineEnabled(checked);
            render();
        });
        media3Radio.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setMedia3RadioEnabled(checked);
            render();
        });
        media3Offline.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setMedia3OfflineEnabled(checked);
            render();
        });
        media3Fallback.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setMedia3LegacyFallbackEnabled(checked);
            render();
        });
        performanceMonitor.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setPerformanceMonitoringEnabled(checked);
            MobilePerformanceMonitor monitor = MobilePerformanceMonitor.get(requireContext());
            if (checked) monitor.onActivityResumed();
            else monitor.onActivityPaused();
            render();
        });
        frameSampling.setOnCheckedChangeListener((button, checked) -> {
            featureFlags.setPerformanceFrameSamplingEnabled(checked);
            MobilePerformanceMonitor monitor = MobilePerformanceMonitor.get(requireContext());
            if (checked) monitor.onActivityResumed();
            else monitor.onActivityPaused();
            render();
        });

        view.findViewById(R.id.mobile_diagnostics_refresh).setOnClickListener(v -> render());
        view.findViewById(R.id.mobile_diagnostics_copy).setOnClickListener(v -> copyReport());
        view.findViewById(R.id.mobile_diagnostics_reset).setOnClickListener(v -> confirmReset());
        render();
    }

    @Override public void onResume() {
        super.onResume();
        if (report != null) render();
    }

    private void render() {
        if (report == null || diagnostics == null) return;
        boolean include = recentEvents == null || recentEvents.isChecked();
        report.setText(diagnostics.buildReport(include));
    }

    private void copyReport() {
        String text = report == null ? "" : report.getText().toString();
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("SmartTube diagnostics", text));
        Toast.makeText(requireContext(), R.string.mobile_diagnostics_copied, Toast.LENGTH_SHORT).show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_diagnostics_reset_title)
                .setMessage(R.string.mobile_diagnostics_reset_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.mobile_diagnostics_reset_confirm, (dialog, which) -> {
                    diagnostics.resetCounters();
                    render();
                })
                .show();
    }
}
