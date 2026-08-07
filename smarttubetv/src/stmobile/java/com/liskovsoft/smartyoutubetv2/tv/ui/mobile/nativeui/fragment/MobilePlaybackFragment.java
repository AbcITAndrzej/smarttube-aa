package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.material.button.MaterialButton;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobilePlayerViewBinder;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileFragmentSupport;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileNativeDependencies;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileNativeViewModelFactory;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileLoadState;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobilePlaybackSnapshot;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileTrack;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel.MobilePlaybackViewModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Mobile-first controls layered over the original SmartTube playback engine. */
public final class MobilePlaybackFragment extends Fragment {
    private static final String ARG_SHORT_MODE = "short_mode";
    private static final String ARG_FORCED_FULLSCREEN = "forced_fullscreen";
    private static final long CONTROLS_TIMEOUT_MS = 4_000L;
    private static final float[] SPEED_VALUES = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable hideControls = () -> setControlsVisible(false);
    private MobilePlayerViewBinder.Binding binding;
    private MobilePlaybackViewModel viewModel;
    private MobilePlaybackSnapshot snapshot;
    private View controls;
    private SeekBar seekBar;
    private MaterialButton playButton;
    private MaterialButton qualityButton;
    private MaterialButton speedButton;
    private MaterialButton audioButton;
    private MaterialButton subtitlesButton;
    private ViewGroup videoSurface;
    private boolean userSeeking;
    private boolean radioAutoplayStarted;
    private boolean radioMode;
    private boolean shortMode;
    private String baseAudioTrackId;
    private String lastAlternativeAudioTrackId;
    private String lastSubtitleTrackId;
    private float shortGestureStartY;
    private float videoScale = 1f;
    private boolean scalingVideo;
    private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;

    public static MobilePlaybackFragment newInstance(String mediaId, long startMs) {
        MobilePlaybackFragment fragment = new MobilePlaybackFragment();
        Bundle arguments = new Bundle();
        arguments.putString("media_id", mediaId);
        arguments.putLong("start_position_ms", startMs);
        fragment.setArguments(arguments);
        return fragment;
    }

    public static MobilePlaybackFragment newRadioInstance(String mediaId) {
        MobilePlaybackFragment fragment = newInstance(mediaId, 0L);
        fragment.requireArguments().putBoolean("radio_mode", true);
        return fragment;
    }

    public static MobilePlaybackFragment newShortInstance(String mediaId, long startMs) {
        MobilePlaybackFragment fragment = newInstance(mediaId, startMs);
        fragment.requireArguments().putBoolean(ARG_SHORT_MODE, true);
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
        radioMode = getArguments() != null && getArguments().getBoolean("radio_mode", false);
        shortMode = getArguments() != null && getArguments().getBoolean(ARG_SHORT_MODE, false);
        ViewGroup surface = view.findViewById(R.id.mobile_player_surface);
        videoSurface = surface;
        if (radioMode) {
            surface.setContentDescription(getString(R.string.mobile_radio_title));
            surface.setBackgroundColor(0xff101010);
        }
        binding = MobileNativeDependencies.get().playerViewBinder().bind(
                surface, viewModel.getRepository());
        if (radioMode && !radioAutoplayStarted) {
            radioAutoplayStarted = true;
            view.post(() -> {
                if (isAdded() && viewModel != null) viewModel.play();
            });
        }

        controls = view.findViewById(R.id.mobile_player_controls);
        TextView title = view.findViewById(R.id.mobile_player_title);
        TextView subtitle = view.findViewById(R.id.mobile_player_subtitle);
        TextView time = view.findViewById(R.id.mobile_player_time);
        ProgressBar progress = view.findViewById(R.id.mobile_progress);
        playButton = view.findViewById(R.id.mobile_player_play);
        qualityButton = view.findViewById(R.id.mobile_player_quality);
        speedButton = view.findViewById(R.id.mobile_player_speed);
        audioButton = view.findViewById(R.id.mobile_player_audio_toggle);
        subtitlesButton = view.findViewById(R.id.mobile_player_subtitles_toggle);
        seekBar = view.findViewById(R.id.mobile_player_seek);

        playButton.setOnClickListener(v -> {
            viewModel.togglePlayPause();
            keepControlsVisible();
        });
        view.findViewById(R.id.mobile_player_previous).setOnClickListener(v -> {
            viewModel.playPrevious();
            keepControlsVisible();
        });
        view.findViewById(R.id.mobile_player_next).setOnClickListener(v -> {
            viewModel.playNext();
            keepControlsVisible();
        });
        view.findViewById(R.id.mobile_player_quick_options)
                .setOnClickListener(v -> showQuickOptions());
        subtitlesButton.setOnClickListener(v -> toggleSubtitles());
        subtitlesButton.setOnLongClickListener(v -> {
            showTracks(MobileTrack.Type.SUBTITLE);
            return true;
        });
        audioButton.setOnClickListener(v -> toggleAlternativeAudio());
        audioButton.setOnLongClickListener(v -> {
            showTracks(MobileTrack.Type.AUDIO);
            return true;
        });
        qualityButton.setOnClickListener(v -> showTracks(MobileTrack.Type.VIDEO));
        speedButton.setOnClickListener(v -> showSpeedMenu());
        view.findViewById(R.id.mobile_player_fit).setOnClickListener(v -> cycleResizeMode());
        view.findViewById(R.id.mobile_player_pip).setOnClickListener(v -> enterPictureInPicture());
        view.findViewById(R.id.mobile_player_fullscreen).setOnClickListener(v -> toggleFullscreen());
        view.findViewById(R.id.mobile_player_more).setOnClickListener(v -> showAdvancedMenu());
        view.findViewById(R.id.mobile_back_button)
                .setOnClickListener(v -> {
                    if (isForcedFullscreen()) exitFullscreen();
                    else MobileFragmentSupport.navigator(this).goBack();
                });

        qualityButton.setVisibility(radioMode ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.mobile_player_previous).setVisibility(radioMode ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.mobile_player_next).setVisibility(radioMode ? View.GONE : View.VISIBLE);
        audioButton.setVisibility(radioMode ? View.GONE : View.VISIBLE);
        subtitlesButton.setVisibility(radioMode ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.mobile_player_fit).setVisibility(radioMode ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.mobile_player_pip).setVisibility(radioMode ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.mobile_player_fullscreen).setVisibility(radioMode ? View.GONE : View.VISIBLE);

        seekBar.setMax(1000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
                ui.removeCallbacks(hideControls);
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                MobilePlaybackSnapshot current = snapshot;
                if (current != null && current.getDurationMs() > 0) {
                    viewModel.seekTo((current.getDurationMs() * bar.getProgress()) / 1000L);
                }
                userSeeking = false;
                keepControlsVisible();
            }
        });

        ScaleGestureDetector scaleDetector = new ScaleGestureDetector(requireContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                        if (radioMode) return false;
                        scalingVideo = true;
                        ui.removeCallbacks(hideControls);
                        return true;
                    }

                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        float nextScale = Math.max(1f,
                                Math.min(4f, videoScale * detector.getScaleFactor()));
                        if (videoSurface != null) {
                            videoSurface.setPivotX(detector.getFocusX());
                            videoSurface.setPivotY(detector.getFocusY());
                            videoSurface.setScaleX(nextScale);
                            videoSurface.setScaleY(nextScale);
                        }
                        videoScale = nextScale;
                        return true;
                    }

                    @Override public void onScaleEnd(ScaleGestureDetector detector) {
                        scalingVideo = false;
                        if (videoScale < 1.02f) resetVideoZoom();
                        keepControlsVisible();
                    }
                });
        GestureDetector detector = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onDown(MotionEvent event) { return true; }
                    @Override public boolean onSingleTapConfirmed(MotionEvent event) {
                        setControlsVisible(controls.getVisibility() != View.VISIBLE);
                        return true;
                    }
                    @Override public boolean onDoubleTap(MotionEvent event) {
                        viewModel.seekBy(event.getX() < view.getWidth() / 2f ? -10_000L : 10_000L);
                        setControlsVisible(true);
                        return true;
                    }
                    @Override public boolean onFling(MotionEvent first, MotionEvent second,
                                                     float velocityX, float velocityY) {
                        if (shortMode && Math.abs(velocityY) > Math.abs(velocityX)
                                && Math.abs(velocityY) > 900f) {
                            if (velocityY < 0) viewModel.playNext();
                            else viewModel.playPrevious();
                            setControlsVisible(false);
                            return true;
                        }
                        if (Math.abs(velocityX) > Math.abs(velocityY)
                                && Math.abs(velocityX) > 900f) {
                            viewModel.seekBy(velocityX < 0 ? -10_000L : 10_000L);
                            setControlsVisible(true);
                            return true;
                        }
                        return false;
                    }
        });
        View.OnTouchListener gestures = (target, event) -> {
            scaleDetector.onTouchEvent(event);
            if (scaleDetector.isInProgress() || scalingVideo || event.getPointerCount() > 1) {
                return true;
            }
            if (shortMode) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    shortGestureStartY = event.getY();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    float distance = event.getY() - shortGestureStartY;
                    if (Math.abs(distance) >= dp(120)) {
                        if (distance < 0) viewModel.playNext();
                        else viewModel.playPrevious();
                        setControlsVisible(false);
                        return true;
                    }
                }
            }
            return detector.onTouchEvent(event);
        };
        surface.setOnTouchListener(gestures);
        view.findViewById(R.id.mobile_player_gesture_surface).setOnTouchListener(gestures);
        view.findViewById(R.id.mobile_player_gesture_area).setOnTouchListener(gestures);

        viewModel.getState().observe(getViewLifecycleOwner(), value -> {
            MobilePlaybackSnapshot current = value.getData();
            boolean loading = value.getStatus() == MobileLoadState.Status.LOADING
                    || current != null && current.isBuffering();
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (value.getError() != null) {
                Toast.makeText(requireContext(), value.getError().getMessage(), Toast.LENGTH_LONG).show();
            }
            if (current == null) return;
            boolean startedPlaying = (snapshot == null || !snapshot.isPlaying()) && current.isPlaying();
            snapshot = current;
            title.setText(current.getTitle());
            subtitle.setText(current.getSubtitle());
            subtitle.setVisibility(current.getSubtitle().isEmpty() ? View.GONE : View.VISIBLE);
            playButton.setIconResource(current.isPlaying()
                    ? R.drawable.mobile_ic_pause : R.drawable.mobile_ic_play);
            playButton.setContentDescription(current.isPlaying()
                    ? getString(R.string.mobile_native_pause) : getString(R.string.mobile_native_play));
            time.setText(format(current.getPositionMs()) + " / " + format(current.getDurationMs()));
            speedButton.setText(speedLabel(current.getSpeed()));
            qualityButton.setText(selectedLabel(current.getVideoTracks(),
                    getString(R.string.mobile_native_quality)));
            updateTrackShortcuts(current);
            if (!userSeeking && current.getDurationMs() > 0) {
                seekBar.setProgress((int) Math.min(1000L,
                        current.getPositionMs() * 1000L / current.getDurationMs()));
            }
            seekBar.setSecondaryProgress(current.getDurationMs() <= 0 ? 0
                    : (int) Math.min(1000L,
                    current.getBufferedPositionMs() * 1000L / current.getDurationMs()));
            if (startedPlaying && controls.getVisibility() == View.VISIBLE) {
                ui.removeCallbacks(hideControls);
                ui.postDelayed(hideControls, CONTROLS_TIMEOUT_MS);
            }
        });
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() {
                        if (isForcedFullscreen()) exitFullscreen();
                        else MobileFragmentSupport.navigator(MobilePlaybackFragment.this).goBack();
                    }
                });
        setControlsVisible(true);
    }

    @Override public void onStart() {
        super.onStart();
        if (viewModel != null) viewModel.onHostStart();
    }

    @Override public void onStop() {
        boolean keepAlive = requireActivity().isChangingConfigurations();
        if (Build.VERSION.SDK_INT >= 24) keepAlive |= requireActivity().isInPictureInPictureMode();
        if (viewModel != null) viewModel.onHostStop(keepAlive);
        super.onStop();
    }

    @Override public void onDestroyView() {
        ui.removeCallbacks(hideControls);
        if (binding != null) {
            binding.releaseView();
            binding = null;
        }
        controls = null;
        seekBar = null;
        playButton = null;
        qualityButton = null;
        speedButton = null;
        audioButton = null;
        subtitlesButton = null;
        resetVideoZoom();
        videoSurface = null;
        super.onDestroyView();
    }

    @Override public void onDestroy() {
        if (isRemoving() && getActivity() != null) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        }
        super.onDestroy();
    }

    private void showQuickOptions() {
        if (snapshot == null) return;
        String audio = selectedLabel(snapshot.getAudioTracks(), getString(R.string.mobile_native_auto));
        String subtitles = selectedLabel(snapshot.getSubtitleTracks(), getString(R.string.mobile_native_off));
        String[] items = {
                getString(R.string.mobile_native_audio_current, audio),
                getString(R.string.mobile_native_subtitles_current, subtitles)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_native_quick_options)
                .setItems(items, (dialog, which) -> showTracks(which == 0
                        ? MobileTrack.Type.AUDIO : MobileTrack.Type.SUBTITLE))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showTracks(MobileTrack.Type type) {
        if (snapshot == null) return;
        List<MobileTrack> sourceTracks = type == MobileTrack.Type.VIDEO
                ? snapshot.getVideoTracks() : type == MobileTrack.Type.AUDIO
                ? snapshot.getAudioTracks() : snapshot.getSubtitleTracks();
        if (type == MobileTrack.Type.SUBTITLE && sourceTracks.size() > 1) {
            // YouTube exposes translated captions in an alphabetic list on some videos.
            // Enforce the phone-friendly order at the final UI boundary as well, because
            // these translated entries may appear after the initial repository snapshot.
            sourceTracks = new ArrayList<>(sourceTracks);
            Collections.sort(sourceTracks,
                    Comparator.comparingInt(MobilePlaybackFragment::subtitleUiRank));
        }
        final List<MobileTrack> tracks = sourceTracks;
        if (tracks.isEmpty()) {
            Toast.makeText(requireContext(), type == MobileTrack.Type.SUBTITLE
                    ? R.string.mobile_native_no_subtitles : R.string.mobile_native_no_tracks,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[tracks.size()];
        int checked = -1;
        for (int index = 0; index < tracks.size(); index++) {
            labels[index] = tracks.get(index).getLabel();
            if (tracks.get(index).isSelected()) checked = index;
        }
        int title = type == MobileTrack.Type.VIDEO ? R.string.mobile_native_quality
                : type == MobileTrack.Type.AUDIO ? R.string.mobile_native_audio
                : R.string.mobile_native_subtitles;
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    String id = tracks.get(which).getId();
                    if (type == MobileTrack.Type.VIDEO) viewModel.selectVideoTrack(id);
                    else if (type == MobileTrack.Type.AUDIO) {
                        MobileTrack selected = selectedTrack(tracks);
                        if (baseAudioTrackId == null && selected != null
                                && !isAudioPlaceholder(selected)) {
                            baseAudioTrackId = selected.getId();
                        }
                        if (!id.equals(baseAudioTrackId) && !isAudioPlaceholder(tracks.get(which))) {
                            lastAlternativeAudioTrackId = id;
                        }
                        viewModel.selectAudioTrack(id);
                    } else {
                        if (!isSubtitleOff(tracks.get(which))) lastSubtitleTrackId = id;
                        viewModel.selectSubtitleTrack(id);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateTrackShortcuts(MobilePlaybackSnapshot current) {
        List<MobileTrack> audioTracks = current.getAudioTracks();
        MobileTrack selectedAudio = selectedTrack(audioTracks);
        if (baseAudioTrackId == null && selectedAudio != null
                && !isAudioPlaceholder(selectedAudio)) {
            baseAudioTrackId = selectedAudio.getId();
        }
        if (audioButton != null) {
            boolean alternative = selectedAudio != null && baseAudioTrackId != null
                    && !selectedAudio.getId().equals(baseAudioTrackId)
                    && !isAudioPlaceholder(selectedAudio);
            audioButton.setSelected(alternative);
        }

        MobileTrack selectedSubtitle = selectedTrack(current.getSubtitleTracks());
        boolean subtitlesEnabled = selectedSubtitle != null && !isSubtitleOff(selectedSubtitle);
        if (subtitlesEnabled) lastSubtitleTrackId = selectedSubtitle.getId();
        if (subtitlesButton != null) subtitlesButton.setSelected(subtitlesEnabled);
    }

    private void toggleSubtitles() {
        if (snapshot == null) return;
        List<MobileTrack> tracks = snapshot.getSubtitleTracks();
        MobileTrack selected = selectedTrack(tracks);
        if (selected != null && !isSubtitleOff(selected)) {
            lastSubtitleTrackId = selected.getId();
            MobileTrack off = firstMatching(tracks, MobilePlaybackFragment::isSubtitleOff);
            if (off != null) viewModel.selectSubtitleTrack(off.getId());
            else Toast.makeText(requireContext(), R.string.mobile_native_no_subtitles,
                    Toast.LENGTH_SHORT).show();
        } else {
            MobileTrack target = findTrack(tracks, lastSubtitleTrackId);
            if (target == null || isSubtitleOff(target)) {
                target = firstMatching(tracks, track -> !isSubtitleOff(track));
            }
            if (target != null) {
                lastSubtitleTrackId = target.getId();
                viewModel.selectSubtitleTrack(target.getId());
            } else {
                Toast.makeText(requireContext(), R.string.mobile_native_no_subtitles,
                        Toast.LENGTH_SHORT).show();
            }
        }
        keepControlsVisible();
    }

    private void toggleAlternativeAudio() {
        if (snapshot == null) return;
        List<MobileTrack> tracks = snapshot.getAudioTracks();
        MobileTrack selected = selectedTrack(tracks);
        if (baseAudioTrackId == null && selected != null && !isAudioPlaceholder(selected)) {
            baseAudioTrackId = selected.getId();
        }
        MobileTrack base = findTrack(tracks, baseAudioTrackId);
        if (base == null) {
            base = firstMatching(tracks, track -> !isAudioPlaceholder(track));
            if (base != null) baseAudioTrackId = base.getId();
        }
        if (selected != null && base != null && !selected.getId().equals(base.getId())
                && !isAudioPlaceholder(selected)) {
            lastAlternativeAudioTrackId = selected.getId();
            viewModel.selectAudioTrack(base.getId());
        } else {
            MobileTrack alternative = findTrack(tracks, lastAlternativeAudioTrackId);
            if (alternative == null || isAudioPlaceholder(alternative)
                    || base != null && alternative.getId().equals(base.getId())) {
                final MobileTrack original = base;
                alternative = firstMatching(tracks, track -> !isAudioPlaceholder(track)
                        && (original == null || !track.getId().equals(original.getId())));
            }
            if (alternative != null) {
                lastAlternativeAudioTrackId = alternative.getId();
                viewModel.selectAudioTrack(alternative.getId());
            } else {
                Toast.makeText(requireContext(), R.string.mobile_native_no_tracks,
                        Toast.LENGTH_SHORT).show();
            }
        }
        keepControlsVisible();
    }

    private void showSpeedMenu() {
        String[] labels = new String[SPEED_VALUES.length];
        int selected = -1;
        float current = snapshot == null ? 1f : snapshot.getSpeed();
        for (int index = 0; index < SPEED_VALUES.length; index++) {
            labels[index] = speedLabel(SPEED_VALUES[index]);
            if (Math.abs(current - SPEED_VALUES[index]) < 0.01f) selected = index;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_native_speed)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    viewModel.setSpeed(SPEED_VALUES[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAdvancedMenu() {
        String[] items = radioMode
                ? new String[]{getString(R.string.mobile_native_quick_options),
                getString(R.string.mobile_native_speed)}
                : new String[]{getString(R.string.mobile_native_quick_options),
                getString(R.string.mobile_native_quality), getString(R.string.mobile_native_speed),
                getString(R.string.mobile_native_fit_mode), getString(R.string.mobile_native_pip),
                getString(R.string.mobile_native_fullscreen)};
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_native_more_options)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) showQuickOptions();
                    else if (which == 1 && radioMode) showSpeedMenu();
                    else if (which == 1) showTracks(MobileTrack.Type.VIDEO);
                    else if (which == 2) showSpeedMenu();
                    else if (which == 3) cycleResizeMode();
                    else if (which == 4) enterPictureInPicture();
                    else if (which == 5) toggleFullscreen();
                })
                .show();
    }

    private void cycleResizeMode() {
        if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
            Toast.makeText(requireContext(), R.string.mobile_native_fit_zoom, Toast.LENGTH_SHORT).show();
        } else if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
            Toast.makeText(requireContext(), R.string.mobile_native_fit_fill, Toast.LENGTH_SHORT).show();
        } else {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
            Toast.makeText(requireContext(), R.string.mobile_native_fit_normal, Toast.LENGTH_SHORT).show();
        }
        viewModel.setResizeMode(resizeMode);
    }

    private void enterPictureInPicture() {
        if (Build.VERSION.SDK_INT < 26 || radioMode) return;
        if (!requireContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(requireContext(), R.string.mobile_native_pip_unavailable,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9)).build();
            if (!requireActivity().enterPictureInPictureMode(params)) {
                Toast.makeText(requireContext(), R.string.mobile_native_pip_unavailable,
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable error) {
            Toast.makeText(requireContext(), R.string.mobile_native_pip_unavailable,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleFullscreen() {
        if (isForcedFullscreen()) {
            exitFullscreen();
        } else {
            requireArguments().putBoolean(ARG_FORCED_FULLSCREEN, true);
            requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }

    private boolean isForcedFullscreen() {
        return getArguments() != null && getArguments().getBoolean(ARG_FORCED_FULLSCREEN, false);
    }

    private void exitFullscreen() {
        if (getArguments() != null) getArguments().putBoolean(ARG_FORCED_FULLSCREEN, false);
        // UNSPECIFIED keeps Samsung's last landscape rotation. Portrait guarantees the same
        // predictable exit behaviour as YouTube; normal sensor rotation is restored on leaving.
        requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        keepControlsVisible();
    }

    private void setControlsVisible(boolean visible) {
        if (controls == null) return;
        controls.setVisibility(visible ? View.VISIBLE : View.GONE);
        ui.removeCallbacks(hideControls);
        if (visible && snapshot != null && snapshot.isPlaying()) {
            ui.postDelayed(hideControls, CONTROLS_TIMEOUT_MS);
        }
    }

    private void keepControlsVisible() {
        setControlsVisible(true);
    }

    private void resetVideoZoom() {
        videoScale = 1f;
        if (videoSurface != null) {
            videoSurface.setScaleX(1f);
            videoSurface.setScaleY(1f);
            videoSurface.setPivotX(videoSurface.getWidth() / 2f);
            videoSurface.setPivotY(videoSurface.getHeight() / 2f);
        }
    }

    private static String selectedLabel(List<MobileTrack> tracks, String fallback) {
        if (tracks != null) {
            for (MobileTrack track : tracks) if (track.isSelected()) return track.getLabel();
        }
        return fallback;
    }

    private static MobileTrack selectedTrack(List<MobileTrack> tracks) {
        if (tracks != null) {
            for (MobileTrack track : tracks) if (track.isSelected()) return track;
        }
        return null;
    }

    private static MobileTrack findTrack(List<MobileTrack> tracks, String id) {
        if (tracks != null && id != null) {
            for (MobileTrack track : tracks) if (id.equals(track.getId())) return track;
        }
        return null;
    }

    private interface TrackPredicate { boolean matches(MobileTrack track); }

    private static MobileTrack firstMatching(List<MobileTrack> tracks, TrackPredicate predicate) {
        if (tracks != null) {
            for (MobileTrack track : tracks) if (predicate.matches(track)) return track;
        }
        return null;
    }

    private static boolean isSubtitleOff(MobileTrack track) {
        return track != null && track.getLanguage().isEmpty()
                && "off".equalsIgnoreCase(track.getLabel().trim());
    }

    private static int subtitleUiRank(MobileTrack track) {
        String label = track.getLabel().toLowerCase(Locale.ROOT);
        String language = track.getLanguage().toLowerCase(Locale.ROOT);
        boolean polish = label.contains("polski") || label.contains("polish")
                || language.equals("pl") || language.startsWith("pl-")
                || language.startsWith("pl_") || language.contains("polski")
                || language.contains("polish");
        if (polish) {
            boolean automatic = label.contains("auto") || label.contains("automatycz")
                    || label.contains("wygenerowan");
            return automatic ? 1 : 0;
        }
        if (isSubtitleOff(track)) return 2;
        return 3;
    }

    private static boolean isAudioPlaceholder(MobileTrack track) {
        return track != null && track.getLanguage().isEmpty()
                && "auto".equalsIgnoreCase(track.getLabel().trim());
    }

    private static String speedLabel(float speed) {
        if (Math.abs(speed - 1f) < 0.01f) return "1×";
        return String.format(Locale.US, "%s×", Float.toString(speed));
    }

    private static String format(long ms) {
        long total = Math.max(0L, ms / 1000L);
        long hours = total / 3600L;
        long minutes = total % 3600L / 60L;
        long seconds = total % 60L;
        return hours > 0 ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
