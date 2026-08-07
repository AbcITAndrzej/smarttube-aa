package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.app.AlertDialog;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobilePlayerViewBinder;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel.MobilePlaybackViewModel;
import java.util.List;

public final class MobilePlaybackFragment extends Fragment {
    private MobilePlayerViewBinder.Binding binding;
    private MobilePlaybackViewModel viewModel;
    private MobilePlaybackSnapshot snapshot;
    private View controls;
    private SeekBar seekBar;
    private boolean userSeeking;
    private boolean radioAutoplayStarted;

    public static MobilePlaybackFragment newInstance(String mediaId, long startMs) {
        MobilePlaybackFragment f = new MobilePlaybackFragment();
        Bundle b = new Bundle();
        b.putString("media_id", mediaId);
        b.putLong("start_position_ms", startMs);
        f.setArguments(b);
        return f;
    }

    public static MobilePlaybackFragment newRadioInstance(String mediaId) {
        MobilePlaybackFragment fragment = newInstance(mediaId, 0L);
        fragment.requireArguments().putBoolean("radio_mode", true);
        return fragment;
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                 @Nullable ViewGroup container,
                                                 @Nullable Bundle state) {
        return inflater.inflate(R.layout.mobile_native_fragment_playback, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this,
                new MobileNativeViewModelFactory(MobileNativeDependencies.get(), getArguments()))
                .get(MobilePlaybackViewModel.class);
        ViewGroup surface = view.findViewById(R.id.mobile_player_surface);
        boolean radioMode = getArguments() != null
                && getArguments().getBoolean("radio_mode", false);
        if (radioMode) {
            surface.setContentDescription(getString(R.string.mobile_radio_title));
            surface.setBackgroundColor(0xff101010);
        }
        binding = MobileNativeDependencies.get().playerViewBinder().bind(surface, viewModel.getRepository());
        if (radioMode && !radioAutoplayStarted) {
            radioAutoplayStarted = true;
            // Binding the surface consumes the deferred station prepare. Start it immediately
            // so selecting a station is a single action on phone and tablet.
            view.post(() -> {
                if (isAdded() && viewModel != null) viewModel.play();
            });
        }
        controls = view.findViewById(R.id.mobile_player_controls);
        TextView title = view.findViewById(R.id.mobile_player_title);
        TextView time = view.findViewById(R.id.mobile_player_time);
        ProgressBar progress = view.findViewById(R.id.mobile_progress);
        MaterialButton play = view.findViewById(R.id.mobile_player_play);
        seekBar = view.findViewById(R.id.mobile_player_seek);
        play.setOnClickListener(v -> viewModel.togglePlayPause());
        view.findViewById(R.id.mobile_player_rewind).setOnClickListener(v -> viewModel.seekBy(-10_000));
        view.findViewById(R.id.mobile_player_forward).setOnClickListener(v -> viewModel.seekBy(10_000));
        view.findViewById(R.id.mobile_player_audio).setOnClickListener(v -> showTracks(true));
        view.findViewById(R.id.mobile_player_subtitles).setOnClickListener(v -> showTracks(false));
        view.findViewById(R.id.mobile_back_button)
                .setOnClickListener(v -> MobileFragmentSupport.navigator(this).goBack());
        seekBar.setMax(1000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar bar) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                MobilePlaybackSnapshot current = snapshot;
                if (current != null && current.getDurationMs() > 0) {
                    long position = (current.getDurationMs() * bar.getProgress()) / 1000L;
                    viewModel.seekTo(position);
                }
                userSeeking = false;
            }
        });
        GestureDetector detector = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(MotionEvent e) { return true; }
                    @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                        controls.setVisibility(controls.getVisibility() == View.VISIBLE
                                ? View.GONE : View.VISIBLE);
                        return true;
                    }
                    @Override public boolean onDoubleTap(MotionEvent e) {
                        float half = view.getWidth() / 2f;
                        viewModel.seekBy(e.getX() < half ? -10_000 : 10_000);
                        return true;
                    }
                    @Override public boolean onFling(MotionEvent e1, MotionEvent e2,
                                                     float velocityX, float velocityY) {
                        if (Math.abs(velocityX) > Math.abs(velocityY)
                                && Math.abs(velocityX) > 900) {
                            viewModel.seekBy(velocityX < 0 ? -10_000 : 10_000);
                            return true;
                        }
                        return false;
                    }
                });
        surface.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            progress.setVisibility(value.getStatus() == MobileLoadState.Status.LOADING
                    ? View.VISIBLE : View.GONE);
            if (value.getError() != null) {
                Toast.makeText(requireContext(), value.getError().getMessage(), Toast.LENGTH_LONG).show();
            }
            snapshot = value.getData();
            if (snapshot != null) {
                title.setText(snapshot.getTitle());
                play.setText(snapshot.isPlaying()
                        ? R.string.mobile_native_pause : R.string.mobile_native_play);
                time.setText(format(snapshot.getPositionMs()) + " / " + format(snapshot.getDurationMs()));
                if (!userSeeking && snapshot.getDurationMs() > 0) {
                    seekBar.setProgress((int) Math.min(1000,
                            (snapshot.getPositionMs() * 1000L) / snapshot.getDurationMs()));
                }
                seekBar.setSecondaryProgress(snapshot.getDurationMs() <= 0 ? 0 :
                        (int) Math.min(1000,
                                (snapshot.getBufferedPositionMs() * 1000L) / snapshot.getDurationMs()));
            }
        });
    }

    @Override public void onStart() {
        super.onStart();
        if (viewModel != null) viewModel.onHostStart();
    }

    @Override public void onStop() {
        boolean keepAlive = requireActivity().isChangingConfigurations();
        if (Build.VERSION.SDK_INT >= 24) keepAlive |= requireActivity().isInPictureInPictureMode();
        viewModel.onHostStop(keepAlive);
        super.onStop();
    }

    @Override public void onDestroyView() {
        if (binding != null) {
            binding.releaseView();
            binding = null;
        }
        controls = null;
        seekBar = null;
        super.onDestroyView();
    }

    private void showTracks(boolean audio) {
        if (snapshot == null) return;
        List<MobileTrack> tracks = audio ? snapshot.getAudioTracks() : snapshot.getSubtitleTracks();
        if (tracks.isEmpty()) return;
        String[] labels = new String[tracks.size()];
        int checked = -1;
        for (int i = 0; i < tracks.size(); i++) {
            labels[i] = tracks.get(i).getLabel();
            if (tracks.get(i).isSelected()) checked = i;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(audio ? R.string.mobile_native_audio : R.string.mobile_native_subtitles)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (audio) viewModel.selectAudioTrack(tracks.get(which).getId());
                    else viewModel.selectSubtitleTrack(tracks.get(which).getId());
                    dialog.dismiss();
                }).show();
    }

    private static String format(long ms) {
        long total = Math.max(0, ms / 1000);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0
                ? String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(java.util.Locale.US, "%d:%02d", m, s);
    }
}
