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
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineListenSaveEntry;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineListenSaveRepository;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineListenSaveService;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineListenSaveState;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Recent Stage 7 passive saves. Available rows can be played with networking completely absent. */
public final class OfflineListenSavedFragment extends Fragment {
    private static final long REFRESH_MS = 1200L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private OfflineListenSaveRepository repository;
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

    public static OfflineListenSavedFragment newInstance() { return new OfflineListenSavedFragment(); }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                 @Nullable ViewGroup parent,
                                                 @Nullable Bundle state) {
        return inflater.inflate(R.layout.mobile_offline_listen_saved_fragment, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        destroyed = false;
        repository = OfflineListenSaveRepository.get(requireContext());
        container = view.findViewById(R.id.mobile_offline_listen_saved_container);
        empty = view.findViewById(R.id.mobile_offline_listen_saved_empty);
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
        OfflineListenSaveService.wake(requireContext());
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
            List<OfflineListenSaveEntry> entries = repository.listRecent(100);
            main.post(() -> {
                if (destroyed || !isAdded() || container == null) return;
                render(entries);
            });
        });
    }

    private void render(List<OfflineListenSaveEntry> entries) {
        container.removeAllViews();
        boolean noItems = entries == null || entries.isEmpty();
        empty.setVisibility(noItems ? View.VISIBLE : View.GONE);
        if (noItems) return;
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (OfflineListenSaveEntry entry : entries) {
            View row = inflater.inflate(R.layout.mobile_offline_listen_saved_item, container, false);
            TextView title = row.findViewById(R.id.mobile_offline_listen_saved_title);
            TextView status = row.findViewById(R.id.mobile_offline_listen_saved_status);
            ProgressBar progress = row.findViewById(R.id.mobile_offline_listen_saved_progress);
            MaterialButton play = row.findViewById(R.id.mobile_offline_listen_saved_play);
            MaterialButton delete = row.findViewById(R.id.mobile_offline_listen_saved_delete);
            title.setText(entry.getTitle().isEmpty() ? entry.getMediaId() : entry.getTitle());
            status.setText(buildStatus(entry));
            progress.setIndeterminate(entry.getBytesTotal() <= 0L
                    && entry.getState() == OfflineListenSaveState.DOWNLOADING);
            progress.setProgress(entry.getProgressPercent());
            boolean available = entry.getState() == OfflineListenSaveState.AVAILABLE;
            play.setVisibility(available ? View.VISIBLE : View.GONE);
            play.setOnClickListener(v -> playFrom(entry));
            delete.setOnClickListener(v -> confirmDelete(entry));
            container.addView(row);
        }
    }

    private void playFrom(OfflineListenSaveEntry selected) {
        io.execute(() -> {
            List<OfflineListenSaveEntry> playable = repository.listPlayable(100);
            ArrayList<String> queue = new ArrayList<>();
            int selectedIndex = -1;
            for (OfflineListenSaveEntry entry : playable) {
                String id = OfflineMediaRepository.playbackId(entry.getMediaId());
                if (id.isEmpty()) continue;
                if (entry.getMediaId().equals(selected.getMediaId())) selectedIndex = queue.size();
                queue.add(id);
            }
            final int index = selectedIndex;
            main.post(() -> {
                if (destroyed || !isAdded()) return;
                if (queue.isEmpty() || index < 0) {
                    Toast.makeText(requireContext(), R.string.mobile_offline_listen_no_playable,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                MobileFragmentSupport.navigator(this).openPlaybackQueue(queue.get(index), 0L, queue);
            });
        });
    }

    private void confirmDelete(OfflineListenSaveEntry entry) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_offline_listen_delete_title)
                .setMessage(R.string.mobile_offline_listen_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.mobile_offline_playlist_delete, (dialog, which) -> io.execute(() -> {
                    long bytes = repository.delete(entry.getMediaId(), true);
                    main.post(() -> {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), getString(
                                R.string.mobile_offline_listen_deleted, formatBytes(bytes)),
                                Toast.LENGTH_SHORT).show();
                        renderAsync();
                    });
                }))
                .show();
    }

    private String buildStatus(OfflineListenSaveEntry entry) {
        String state;
        switch (entry.getState()) {
            case PENDING: state = getString(R.string.mobile_offline_listen_state_pending); break;
            case DOWNLOADING: state = getString(R.string.mobile_offline_listen_state_downloading); break;
            case AVAILABLE: state = getString(R.string.mobile_offline_listen_state_available); break;
            case FAILED:
            default: state = getString(R.string.mobile_offline_listen_state_failed); break;
        }
        String author = entry.getAuthor().isEmpty() ? "" : " • " + entry.getAuthor();
        String bytes = entry.getBytesDownloaded() > 0L ? " • " + formatBytes(entry.getBytesDownloaded()) : "";
        return state + author + bytes;
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
