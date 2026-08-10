package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileFragmentSupport;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistDownloadService;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistEntry;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistItemState;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistRecord;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflinePlaylistState;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineTripReserveRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Stage 8 queue manager: progress, pause/resume/retry and safe delete. */
public final class OfflinePlaylistsFragment extends Fragment {
    private static final long REFRESH_MS = 1200L;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private OfflinePlaylistRepository repository;
    private LinearLayout container;
    private TextView empty;
    private boolean destroyed;
    private final Runnable periodic = new Runnable() {
        @Override public void run() {
            if (destroyed || !isAdded()) return;
            renderAsync();
            main.postDelayed(this, REFRESH_MS);
        }
    };

    public static OfflinePlaylistsFragment newInstance() { return new OfflinePlaylistsFragment(); }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                 @Nullable ViewGroup parent,
                                                 @Nullable Bundle state) {
        return inflater.inflate(R.layout.mobile_offline_playlists_fragment, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        destroyed = false;
        repository = OfflinePlaylistRepository.get(requireContext());
        container = view.findViewById(R.id.mobile_offline_playlist_container);
        empty = view.findViewById(R.id.mobile_offline_playlist_empty);
        MaterialToolbar toolbar = view.findViewById(R.id.mobile_toolbar);
        toolbar.setNavigationIcon(R.drawable.mobile_ic_back_24);
        toolbar.setNavigationContentDescription(R.string.mobile_native_back);
        toolbar.setNavigationOnClickListener(v -> MobileFragmentSupport.navigator(this).goBack());
        renderAsync();
    }

    @Override public void onResume() {
        super.onResume();
        main.removeCallbacks(periodic);
        main.post(periodic);
    }

    @Override public void onPause() {
        main.removeCallbacks(periodic);
        super.onPause();
    }

    @Override public void onDestroyView() {
        destroyed = true;
        main.removeCallbacks(periodic);
        container = null;
        empty = null;
        super.onDestroyView();
    }

    @Override public void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void renderAsync() {
        if (repository == null) return;
        io.execute(() -> {
            List<OfflinePlaylistRecord> playlists = repository.list();
            main.post(() -> {
                if (destroyed || !isAdded() || container == null) return;
                render(playlists);
            });
        });
    }

    private void render(List<OfflinePlaylistRecord> playlists) {
        container.removeAllViews();
        boolean noItems = playlists == null || playlists.isEmpty();
        empty.setVisibility(noItems ? View.VISIBLE : View.GONE);
        if (noItems) return;
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (OfflinePlaylistRecord record : playlists) {
            View row = inflater.inflate(R.layout.mobile_offline_playlist_item, container, false);
            TextView title = row.findViewById(R.id.mobile_offline_playlist_title);
            TextView status = row.findViewById(R.id.mobile_offline_playlist_status);
            ProgressBar progress = row.findViewById(R.id.mobile_offline_playlist_progress);
            MaterialButton play = row.findViewById(R.id.mobile_offline_playlist_play);
            MaterialButton toggle = row.findViewById(R.id.mobile_offline_playlist_toggle);
            MaterialButton delete = row.findViewById(R.id.mobile_offline_playlist_delete);

            boolean tripReserve = OfflineTripReserveRepository.isTripReservePlaylistId(record.getPlaylistId());
            title.setText(record.getTitle().isEmpty() ? record.getPlaylistId() : record.getTitle());
            status.setText(buildStatus(record) + (tripReserve
                    ? " • " + getString(R.string.mobile_offline_trip_managed_label) : ""));
            progress.setProgress(record.getProgressPercent());
            progress.setIndeterminate(record.getTotalCount() <= 0);

            play.setVisibility(record.getCompletedCount() > 0 ? View.VISIBLE : View.GONE);
            play.setOnClickListener(v -> playAvailable(record));

            boolean paused = record.getState() == OfflinePlaylistState.PAUSED;
            boolean retryable = record.getState() == OfflinePlaylistState.PARTIAL
                    || record.getState() == OfflinePlaylistState.FAILED;
            boolean complete = record.getState() == OfflinePlaylistState.AVAILABLE;
            toggle.setVisibility(tripReserve || complete ? View.GONE : View.VISIBLE);
            toggle.setText(paused || retryable
                    ? R.string.mobile_offline_playlist_resume
                    : R.string.mobile_offline_playlist_pause);
            toggle.setOnClickListener(v -> {
                if (paused || retryable) {
                    repository.resume(record.getPlaylistId());
                    OfflinePlaylistDownloadService.resume(requireContext(), record.getPlaylistId());
                } else {
                    repository.pause(record.getPlaylistId());
                    OfflinePlaylistDownloadService.pause(requireContext(), record.getPlaylistId());
                }
                renderAsync();
            });
            delete.setVisibility(tripReserve ? View.GONE : View.VISIBLE);
            delete.setOnClickListener(v -> confirmDelete(record));
            container.addView(row);
        }
    }

    private void playAvailable(OfflinePlaylistRecord record) {
        if (record == null) return;
        io.execute(() -> {
            List<OfflinePlaylistEntry> entries = repository.entries(record.getPlaylistId());
            ArrayList<String> queue = new ArrayList<>();
            for (OfflinePlaylistEntry entry : entries) {
                if (entry.getState() != OfflinePlaylistItemState.AVAILABLE) continue;
                String playbackId = OfflineMediaRepository.playbackId(entry.getMediaId());
                if (!playbackId.isEmpty()) queue.add(playbackId);
            }
            main.post(() -> {
                if (destroyed || !isAdded()) return;
                if (queue.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.mobile_offline_playlist_no_playable,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                MobileFragmentSupport.navigator(this).openPlaybackQueue(queue.get(0), 0L, queue);
            });
        });
    }

    private void confirmDelete(OfflinePlaylistRecord record) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_offline_playlist_delete_title)
                .setMessage(R.string.mobile_offline_playlist_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.mobile_offline_playlist_delete, (dialog, which) -> {
                    OfflinePlaylistDownloadService.cancel(requireContext(), record.getPlaylistId());
                    io.execute(() -> {
                        long bytes = repository.delete(record.getPlaylistId(), true);
                        main.post(() -> {
                            if (!isAdded()) return;
                            Toast.makeText(requireContext(), getString(
                                    R.string.mobile_offline_playlist_deleted, formatBytes(bytes)),
                                    Toast.LENGTH_SHORT).show();
                            renderAsync();
                        });
                    });
                })
                .show();
    }

    private String buildStatus(OfflinePlaylistRecord record) {
        String state;
        switch (record.getState()) {
            case QUEUED: state = getString(R.string.mobile_offline_playlist_state_queued); break;
            case DOWNLOADING: state = getString(R.string.mobile_offline_playlist_state_downloading); break;
            case PAUSED: state = getString(R.string.mobile_offline_playlist_state_paused); break;
            case AVAILABLE: state = getString(R.string.mobile_offline_playlist_state_available); break;
            case PARTIAL: state = getString(R.string.mobile_offline_playlist_state_partial); break;
            case FAILED:
            default: state = getString(R.string.mobile_offline_playlist_state_failed); break;
        }
        return getString(R.string.mobile_offline_playlist_status_format, state,
                record.getCompletedCount(), record.getTotalCount(), record.getFailedCount(),
                formatBytes(record.getBytesDownloaded()));
    }

    private static String formatBytes(long bytes) {
        double value = Math.max(0L, bytes);
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024d && unit < units.length - 1) { value /= 1024d; unit++; }
        return unit == 0 ? String.format(Locale.US, "%.0f %s", value, units[unit])
                : String.format(Locale.US, "%.1f %s", value, units[unit]);
    }
}
