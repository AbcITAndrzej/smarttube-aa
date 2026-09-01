package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.DiagnosticSessionLogger;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileDiagnosticsStore;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.performance.MobilePerformanceMonitor;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/** User-visible local diagnostics. Reports are copied to clipboard only; nothing is uploaded. */
public final class DiagnosticsFragment extends Fragment {
    private static final int REQUEST_EXPORT_SESSION_LOG = 702;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable sessionLogTick = this::tickSessionLogUi;

    private MobileDiagnosticsStore diagnostics;
    private MobileFeatureFlags featureFlags;
    private DiagnosticSessionLogger sessionLogger;
    private TextView report;
    private SwitchMaterial recentEvents;
    private TextView sessionLogStatus;
    private View sessionLogStart;
    private View sessionLogStop;
    private View sessionLogSave;

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
        sessionLogger = DiagnosticSessionLogger.get(requireContext());
        report = view.findViewById(R.id.mobile_diagnostics_report);
        attachSessionLogSection();

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
        updateSessionLogUi();
    }

    @Override public void onResume() {
        super.onResume();
        if (report != null) render();
        scheduleSessionLogTick();
    }

    @Override public void onPause() {
        ui.removeCallbacks(sessionLogTick);
        super.onPause();
    }

    @Override public void onDestroyView() {
        ui.removeCallbacks(sessionLogTick);
        sessionLogStatus = null;
        sessionLogStart = null;
        sessionLogStop = null;
        sessionLogSave = null;
        report = null;
        super.onDestroyView();
    }

    @Override public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT_SESSION_LOG || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null || sessionLogger == null) {
            return;
        }

        Uri destination = data.getData();
        try (OutputStream output = requireContext().getContentResolver().openOutputStream(destination)) {
            if (output == null) throw new IOException("Cannot open destination file");
            sessionLogger.copyLastLog(output);
            Toast.makeText(requireContext(), R.string.mobile_diagnostic_log_saved,
                    Toast.LENGTH_LONG).show();
        } catch (IOException error) {
            Toast.makeText(requireContext(),
                    getString(R.string.mobile_diagnostic_log_save_error, error.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void attachSessionLogSection() {
        if (report == null || !(report.getParent() instanceof ViewGroup)) return;
        ViewGroup reportCard = (ViewGroup) report.getParent();
        if (!(reportCard.getParent() instanceof ViewGroup)) return;
        ViewGroup content = (ViewGroup) reportCard.getParent();
        View section = getLayoutInflater().inflate(
                R.layout.mobile_diagnostic_log_section, content, false);
        content.addView(section, Math.min(1, content.getChildCount()));

        sessionLogStatus = section.findViewById(R.id.mobile_diagnostic_log_status);
        sessionLogStart = section.findViewById(R.id.mobile_diagnostic_log_start);
        sessionLogStop = section.findViewById(R.id.mobile_diagnostic_log_stop);
        sessionLogSave = section.findViewById(R.id.mobile_diagnostic_log_save);

        sessionLogStart.setOnClickListener(v -> startSessionLog());
        sessionLogStop.setOnClickListener(v -> stopSessionLog());
        sessionLogSave.setOnClickListener(v -> exportSessionLog());
    }

    private void startSessionLog() {
        if (sessionLogger == null) return;
        try {
            File file = sessionLogger.start();
            Toast.makeText(requireContext(),
                    getString(R.string.mobile_diagnostic_log_started, file.getName()),
                    Toast.LENGTH_LONG).show();
            updateSessionLogUi();
            scheduleSessionLogTick();
        } catch (IOException error) {
            Toast.makeText(requireContext(),
                    getString(R.string.mobile_diagnostic_log_start_error, error.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void stopSessionLog() {
        if (sessionLogger == null) return;
        File file = sessionLogger.stop();
        ui.removeCallbacks(sessionLogTick);
        updateSessionLogUi();
        if (file != null) {
            Toast.makeText(requireContext(),
                    getString(R.string.mobile_diagnostic_log_stopped, file.getName()),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void exportSessionLog() {
        if (sessionLogger == null || sessionLogger.isRecording()) return;
        File file = sessionLogger.getLastFile();
        if (file == null || !file.isFile()) {
            Toast.makeText(requireContext(), R.string.mobile_diagnostic_log_no_file,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TITLE, file.getName());
        startActivityForResult(intent, REQUEST_EXPORT_SESSION_LOG);
    }

    private void scheduleSessionLogTick() {
        ui.removeCallbacks(sessionLogTick);
        if (sessionLogger != null && sessionLogger.isRecording()) {
            ui.postDelayed(sessionLogTick, 1_000L);
        }
    }

    private void tickSessionLogUi() {
        updateSessionLogUi();
        scheduleSessionLogTick();
    }

    private void updateSessionLogUi() {
        if (sessionLogger == null || sessionLogStatus == null) return;
        boolean active = sessionLogger.isRecording();
        File last = sessionLogger.getLastFile();
        String error = sessionLogger.getLastError();

        if (active) {
            sessionLogStatus.setText(getString(R.string.mobile_diagnostic_log_status_recording,
                    formatDuration(sessionLogger.getElapsedMs()),
                    formatBytes(sessionLogger.getBytesWritten())));
        } else if (last != null && last.isFile()) {
            String status = getString(R.string.mobile_diagnostic_log_status_ready,
                    last.getName(), formatBytes(last.length()));
            if (error != null && !error.isEmpty()) {
                status += "\n" + getString(R.string.mobile_diagnostic_log_status_warning, error);
            }
            sessionLogStatus.setText(status);
        } else {
            sessionLogStatus.setText(R.string.mobile_diagnostic_log_status_idle);
        }

        if (sessionLogStart != null) sessionLogStart.setEnabled(!active);
        if (sessionLogStop != null) sessionLogStop.setEnabled(active);
        if (sessionLogSave != null) sessionLogSave.setEnabled(!active && last != null && last.isFile());
    }

    private static String formatDuration(long elapsedMs) {
        long totalSeconds = Math.max(0L, elapsedMs) / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MiB", bytes / (1024f * 1024f));
        }
        if (bytes >= 1024L) return String.format(Locale.US, "%.1f KiB", bytes / 1024f);
        return bytes + " B";
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
