package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.pm.ActivityInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
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
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileLoadState;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobilePlaybackSnapshot;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileTrack;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.BrightnessGesturePolicy;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.MobileEnhancementPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.MobilePlayerPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.performance.MobilePerformanceMonitor;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.MobileSegmentSeekBar;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.PreferredTrackResolver;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.TrackPickerBottomSheet;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio.RadioPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel.MobilePlaybackViewModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Mobile-first controls layered over the original SmartTube playback engine. */
public final class MobilePlaybackFragment extends Fragment implements TrackPickerBottomSheet.Listener {
    private static final String ARG_SHORT_MODE = "short_mode";
    private static final String ARG_PLAYBACK_QUEUE = "playback_queue";
    private static final String ARG_FORCED_FULLSCREEN = "forced_fullscreen";
    private static final long CONTROLS_TIMEOUT_MS = 4_000L;
    private static final long UNLOCK_PROMPT_TIMEOUT_MS = 2_500L;
    private static final long GESTURE_FEEDBACK_TIMEOUT_MS = 900L;
    private static final String STATE_SLEEP_TIMER_END_ELAPSED = "sleep_timer_end_elapsed";
    private static final String STATE_SLEEP_TIMER_END_MEDIA_ID = "sleep_timer_end_media_id";
    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_BRIGHTNESS = 1;
    private static final int GESTURE_VOLUME = 2;
    private static final float SMART_FIT_TOLERANCE = 0.20f;
    private static final float[] SPEED_VALUES = {0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private MaterialButton unlockButton;
    private View gestureFeedback;
    private boolean controlsLocked;
    private final Runnable hideControls = () -> setControlsVisible(false);
    private final Runnable hideGestureFeedback = () -> {
        if (gestureFeedback != null) gestureFeedback.setVisibility(View.GONE);
    };
    private final Runnable hideUnlockPrompt = () -> {
        if (unlockButton != null && controlsLocked) unlockButton.setVisibility(View.GONE);
    };
    private final Runnable sleepTimerTick = this::handleSleepTimerTick;
    private final Runnable restoreAutoOrientation = () -> {
        if (isAdded() && !isForcedFullscreen()) {
            requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
            MobileDiagnostics.info("P16-Rotation", "automatic orientation restored");
        }
    };
    private MobilePlayerViewBinder.Binding binding;
    private MobilePlaybackViewModel viewModel;
    private MobilePlayerPreferences playerPreferences;
    private MobileEnhancementPreferences enhancementPreferences;
    private MobileFeatureFlags featureFlags;
    private RadioPreferences radioPreferences;
    private AudioManager audioManager;
    private MobilePlaybackSnapshot snapshot;
    private View controls;
    private MobileSegmentSeekBar seekBar;
    private MaterialButton playButton;
    private MaterialButton qualityButton;
    private MaterialButton speedButton;
    private MaterialButton audioButton;
    private MaterialButton subtitlesButton;
    private MaterialButton goLiveButton;
    private MaterialButton lockButton;
    private TextView sleepBadge;
    private TextView gestureFeedbackText;
    private ProgressBar gestureFeedbackProgress;
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
    private boolean panningVideo;
    private boolean panMoved;
    private float panDownX;
    private float panDownY;
    private float panStartTranslationX;
    private float panStartTranslationY;
    private float videoTranslationX;
    private float videoTranslationY;
    private int touchSlop;
    private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private String activeMediaId;
    private String activeVideoTrackId;
    private boolean manualResizeOverride;
    private float gestureDownX;
    private float gestureDownY;
    private int verticalGestureMode = GESTURE_NONE;
    private boolean verticalGestureActive;
    private float gestureStartBrightness;
    private float originalWindowBrightness = Float.NaN;
    private boolean brightnessAdjusted;
    private int gestureStartVolume;
    private int maxMusicVolume = 1;
    private long sleepTimerEndElapsedMs = -1L;
    private String sleepTimerEndMediaId;

    public static MobilePlaybackFragment newInstance(String mediaId, long startMs) {
        MobilePlaybackFragment fragment = new MobilePlaybackFragment();
        Bundle arguments = new Bundle();
        arguments.putString("media_id", mediaId);
        arguments.putLong("start_position_ms", startMs);
        fragment.setArguments(arguments);
        return fragment;
    }

    public static MobilePlaybackFragment newQueueInstance(String mediaId, long startMs,
                                                            List<String> playbackQueue) {
        MobilePlaybackFragment fragment = newInstance(mediaId, startMs);
        fragment.requireArguments().putStringArrayList(ARG_PLAYBACK_QUEUE,
                playbackQueue == null ? new ArrayList<>() : new ArrayList<>(playbackQueue));
        return fragment;
    }

    public static MobilePlaybackFragment newRadioInstance(String mediaId) {
        MobilePlaybackFragment fragment = newInstance(mediaId, 0L);
        fragment.requireArguments().putBoolean("radio_mode", true);
        return fragment;
    }

    public static MobilePlaybackFragment newShortInstance(String mediaId, long startMs,
                                                           List<String> shortQueue) {
        MobilePlaybackFragment fragment = newInstance(mediaId, startMs);
        fragment.requireArguments().putBoolean(ARG_SHORT_MODE, true);
        fragment.requireArguments().putStringArrayList(ARG_PLAYBACK_QUEUE,
                shortQueue == null ? new ArrayList<>() : new ArrayList<>(shortQueue));
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
        playerPreferences = new MobilePlayerPreferences(requireContext());
        enhancementPreferences = new MobileEnhancementPreferences(requireContext());
        featureFlags = new MobileFeatureFlags(requireContext());
        radioPreferences = new RadioPreferences(requireContext());
        audioManager = (AudioManager) requireContext().getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            maxMusicVolume = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        }
        try {
            originalWindowBrightness = requireActivity().getWindow().getAttributes().screenBrightness;
        } catch (Throwable ignored) {
            originalWindowBrightness = Float.NaN;
        }
        if (state != null) {
            sleepTimerEndElapsedMs = state.getLong(STATE_SLEEP_TIMER_END_ELAPSED, -1L);
            sleepTimerEndMediaId = state.getString(STATE_SLEEP_TIMER_END_MEDIA_ID);
            if (sleepTimerEndElapsedMs <= SystemClock.elapsedRealtime()) sleepTimerEndElapsedMs = -1L;
        }
        radioMode = getArguments() != null && getArguments().getBoolean("radio_mode", false);
        shortMode = getArguments() != null && getArguments().getBoolean(ARG_SHORT_MODE, false);
        touchSlop = ViewConfiguration.get(requireContext()).getScaledTouchSlop();
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
        goLiveButton = view.findViewById(R.id.mobile_player_go_live);
        lockButton = view.findViewById(R.id.mobile_player_lock);
        unlockButton = view.findViewById(R.id.mobile_player_unlock_button);
        sleepBadge = view.findViewById(R.id.mobile_player_sleep_badge);
        gestureFeedback = view.findViewById(R.id.mobile_player_gesture_feedback);
        gestureFeedbackText = view.findViewById(R.id.mobile_player_gesture_feedback_text);
        gestureFeedbackProgress = view.findViewById(R.id.mobile_player_gesture_feedback_progress);
        seekBar = view.findViewById(R.id.mobile_player_seek);
        boolean smartUx = featureFlags.isSmartPlayerUxEnabled();
        lockButton.setVisibility(!radioMode && smartUx && playerPreferences.isPlayerLockEnabled()
                ? View.VISIBLE : View.GONE);
        lockButton.setOnClickListener(v -> setControlsLocked(true));
        unlockButton.setOnClickListener(v -> setControlsLocked(false));
        updateSleepTimerBadge();
        if (sleepTimerEndElapsedMs > 0L) scheduleSleepTimerTick();
        goLiveButton.setOnClickListener(v -> {
            MobilePlaybackSnapshot current = snapshot;
            if (current != null && current.getDurationMs() > 0L) {
                viewModel.seekTo(current.getDurationMs());
                keepControlsVisible();
            }
        });

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
        // A normal tap opens the modern picker so the preferred language is immediately visible.
        // Long-press keeps the old fast toggle for users who still want a one-gesture shortcut.
        subtitlesButton.setOnClickListener(v -> showTracks(MobileTrack.Type.SUBTITLE));
        subtitlesButton.setOnLongClickListener(v -> {
            toggleSubtitles();
            return true;
        });
        audioButton.setOnClickListener(v -> showTracks(MobileTrack.Type.AUDIO));
        audioButton.setOnLongClickListener(v -> {
            toggleAlternativeAudio();
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

        // Player customization is deliberately phone/tablet-only and applies to VOD/Shorts.
        // Radio keeps its dedicated controls and Android Auto never reads MobilePlayerPreferences.
        qualityButton.setVisibility(radioMode || !playerPreferences.isQualityVisible()
                ? View.GONE : View.VISIBLE);
        int previousNextVisibility = radioMode || !playerPreferences.isPreviousNextVisible()
                ? View.GONE : View.VISIBLE;
        view.findViewById(R.id.mobile_player_previous).setVisibility(previousNextVisibility);
        view.findViewById(R.id.mobile_player_next).setVisibility(previousNextVisibility);
        audioButton.setVisibility(radioMode || !playerPreferences.isAudioVisible()
                ? View.GONE : View.VISIBLE);
        subtitlesButton.setVisibility(radioMode || !playerPreferences.isSubtitlesVisible()
                ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.mobile_player_quick_options).setVisibility(
                !radioMode && !playerPreferences.isQuickOptionsVisible() ? View.GONE : View.VISIBLE);
        speedButton.setVisibility(!radioMode && !playerPreferences.isSpeedVisible()
                ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.mobile_player_fit).setVisibility(
                radioMode || !playerPreferences.isFitVisible() ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.mobile_player_pip).setVisibility(
                radioMode || !playerPreferences.isPipVisible() ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.mobile_player_fullscreen).setVisibility(
                radioMode || !playerPreferences.isFullscreenVisible() ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.mobile_player_more).setVisibility(
                !radioMode && !playerPreferences.isMoreVisible() ? View.GONE : View.VISIBLE);

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
                        if (radioMode || !playerPreferences.isPinchZoomEnabled()) return false;
                        scalingVideo = true;
                        ui.removeCallbacks(hideControls);
                        if (videoSurface != null) {
                            videoSurface.setPivotX(videoSurface.getWidth() / 2f);
                            videoSurface.setPivotY(videoSurface.getHeight() / 2f);
                        }
                        return true;
                    }

                    @Override public boolean onScale(ScaleGestureDetector detector) {
                        float nextScale = Math.max(1f,
                                Math.min(4f, videoScale * detector.getScaleFactor()));
                        if (videoSurface != null) {
                            videoSurface.setScaleX(nextScale);
                            videoSurface.setScaleY(nextScale);
                        }
                        videoScale = nextScale;
                        clampVideoTranslation();
                        return true;
                    }

                    @Override public void onScaleEnd(ScaleGestureDetector detector) {
                        scalingVideo = false;
                        if (videoScale < 1.02f) resetVideoZoom();
                        persistRememberedZoom();
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
                        if (!radioMode && !playerPreferences.isDoubleTapSeekEnabled()) return false;
                        int seconds = !radioMode && isSmartPlayerUxEnabled()
                                ? playerPreferences.getDoubleTapSeekSeconds() : 10;
                        long deltaMs = seconds * 1_000L;
                        viewModel.seekBy(event.getX() < view.getWidth() / 2f ? -deltaMs : deltaMs);
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
                        if ((radioMode || playerPreferences.isSwipeSeekEnabled())
                                && Math.abs(velocityX) > Math.abs(velocityY)
                                && Math.abs(velocityX) > 900f) {
                            viewModel.seekBy(velocityX < 0 ? -10_000L : 10_000L);
                            setControlsVisible(true);
                            return true;
                        }
                        return false;
                    }
        });
        View.OnTouchListener gestures = (target, event) -> {
            if (controlsLocked) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) showUnlockPrompt();
                return true;
            }
            scaleDetector.onTouchEvent(event);
            if (scaleDetector.isInProgress() || scalingVideo || event.getPointerCount() > 1) {
                return true;
            }
            if (videoScale > 1.02f) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    panningVideo = true;
                    panMoved = false;
                    panDownX = event.getX();
                    panDownY = event.getY();
                    panStartTranslationX = videoTranslationX;
                    panStartTranslationY = videoTranslationY;
                    ui.removeCallbacks(hideControls);
                    // A zoomed video still has to receive a normal tap. Previously every
                    // ACTION_DOWN was consumed as a pan, so taps could never hide controls.
                    return detector.onTouchEvent(event);
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE && panningVideo) {
                    float dx = event.getX() - panDownX;
                    float dy = event.getY() - panDownY;
                    if (!panMoved && Math.hypot(dx, dy) > touchSlop) panMoved = true;
                    if (panMoved) {
                        videoTranslationX = panStartTranslationX + dx;
                        videoTranslationY = panStartTranslationY + dy;
                        clampVideoTranslation();
                        return true;
                    }
                    return detector.onTouchEvent(event);
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    boolean moved = panMoved;
                    panningVideo = false;
                    panMoved = false;
                    if (moved) {
                        keepControlsVisible();
                        return true;
                    }
                    return detector.onTouchEvent(event);
                }
            }
            // Stage 4 reserves vertical half-screen gestures for ordinary VOD only. Shorts keep
            // their up/down navigation and Radio keeps its dedicated seek/DVR interaction.
            if (handleVerticalPlayerGesture(view, event)) return true;
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
            MobilePerformanceMonitor performance = MobilePerformanceMonitor.get(requireContext());
            long renderStarted = performance.beginTrace("ST:PlaybackRender");
            boolean mediaChanged = activeMediaId == null || !activeMediaId.equals(current.getMediaId());
            if (mediaChanged) {
                activeMediaId = current.getMediaId();
                activeVideoTrackId = null;
                manualResizeOverride = false;
                baseAudioTrackId = null;
                lastAlternativeAudioTrackId = null;
                lastSubtitleTrackId = null;
                restoreRememberedZoom();
            }
            boolean startedPlaying = (snapshot == null || !snapshot.isPlaying()) && current.isPlaying();
            snapshot = current;
            handleEndOfMediaSleepTimer(current);
            title.setText(current.getTitle());
            subtitle.setText(current.getSubtitle());
            subtitle.setVisibility(current.getSubtitle().isEmpty() ? View.GONE : View.VISIBLE);
            playButton.setIconResource(current.isPlaying()
                    ? R.drawable.mobile_ic_pause : R.drawable.mobile_ic_play);
            playButton.setContentDescription(current.isPlaying()
                    ? getString(R.string.mobile_native_pause) : getString(R.string.mobile_native_play));
            long behindLive = Math.max(0L, current.getDurationMs() - current.getPositionMs());
            if (radioMode && featureFlags.isRadio2Enabled()
                    && radioPreferences != null && radioPreferences.isLiveOffsetLabelEnabled()) {
                time.setText(behindLive <= 2_500L
                        ? getString(R.string.mobile_radio_live_label)
                        : getString(R.string.mobile_radio_live_offset_label, format(behindLive)));
            } else {
                time.setText(format(current.getPositionMs()) + " / " + format(current.getDurationMs()));
            }
            if (goLiveButton != null) {
                boolean dvrAvailable = radioMode && current.getDurationMs() >= 2_000L;
                goLiveButton.setVisibility(dvrAvailable ? View.VISIBLE : View.GONE);
                boolean showLiveOffset = featureFlags.isRadio2Enabled()
                        && radioPreferences != null && radioPreferences.isLiveOffsetLabelEnabled();
                goLiveButton.setText(!showLiveOffset || behindLive <= 2_500L
                        ? getString(R.string.mobile_radio_go_live)
                        : getString(R.string.mobile_radio_behind_live, format(behindLive)));
                goLiveButton.setEnabled(behindLive > 1_000L);
            }
            speedButton.setText(speedLabel(current.getSpeed()));
            qualityButton.setText(selectedLabel(current.getVideoTracks(),
                    getString(R.string.mobile_native_quality)));
            MobileTrack selectedVideo = selectedTrack(current.getVideoTracks());
            String selectedVideoId = videoGeometrySignature(current.getVideoTracks(), selectedVideo);
            boolean videoTrackChanged = activeVideoTrackId == null
                    || !activeVideoTrackId.equals(selectedVideoId);
            if (videoTrackChanged) activeVideoTrackId = selectedVideoId;
            if ((mediaChanged || videoTrackChanged) && isSmartPlayerUxEnabled()
                    && playerPreferences.isSmartFitEnabled() && !manualResizeOverride && !radioMode) {
                view.post(() -> applySmartFit(current));
            }
            updateTrackShortcuts(current);
            if (!userSeeking && current.getDurationMs() > 0) {
                seekBar.setProgress((int) Math.min(1000L,
                        current.getPositionMs() * 1000L / current.getDurationMs()));
            }
            seekBar.setSecondaryProgress(current.getDurationMs() <= 0 ? 0
                    : (int) Math.min(1000L,
                    current.getBufferedPositionMs() * 1000L / current.getDurationMs()));
            seekBar.setSegments(!radioMode
                    && enhancementPreferences.isSponsorBlockSeekBarMarkersEnabled()
                    ? current.getSeekBarSegments() : Collections.emptyList());
            if (startedPlaying && controls.getVisibility() == View.VISIBLE
                    && (radioMode || playerPreferences.isAutoHideControlsEnabled())) {
                ui.removeCallbacks(hideControls);
                ui.postDelayed(hideControls, CONTROLS_TIMEOUT_MS);
            }
            performance.endPlaybackTrace(renderStarted);
        });
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() {
                        if (controlsLocked) setControlsLocked(false);
                        else if (isForcedFullscreen()) exitFullscreen();
                        else MobileFragmentSupport.navigator(MobilePlaybackFragment.this).goBack();
                    }
                });
        setControlsVisible(true);
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_SLEEP_TIMER_END_ELAPSED, sleepTimerEndElapsedMs);
        outState.putString(STATE_SLEEP_TIMER_END_MEDIA_ID, sleepTimerEndMediaId);
    }

    @Override public void onStart() {
        super.onStart();
        if (viewModel != null) viewModel.onHostStart();
    }

    @Override public void onResume() {
        super.onResume();
        if (!isForcedFullscreen()) {
            requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        }
    }

    @Override public void onStop() {
        boolean keepAlive = requireActivity().isChangingConfigurations();
        if (Build.VERSION.SDK_INT >= 24) keepAlive |= requireActivity().isInPictureInPictureMode();
        if (viewModel != null) viewModel.onHostStop(keepAlive);
        super.onStop();
    }

    @Override public void onDestroyView() {
        ui.removeCallbacks(hideControls);
        ui.removeCallbacks(hideGestureFeedback);
        ui.removeCallbacks(hideUnlockPrompt);
        ui.removeCallbacks(sleepTimerTick);
        ui.removeCallbacks(restoreAutoOrientation);
        restoreWindowBrightnessOverride();
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
        goLiveButton = null;
        lockButton = null;
        unlockButton = null;
        sleepBadge = null;
        gestureFeedback = null;
        gestureFeedbackText = null;
        gestureFeedbackProgress = null;
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
            // Translated captions may arrive after the first snapshot. Sort at the UI boundary too.
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
        String preferredLanguage = "";
        if (type == MobileTrack.Type.AUDIO) {
            preferredLanguage = playerPreferences.getPreferredAudioLanguage();
        } else if (type == MobileTrack.Type.SUBTITLE) {
            preferredLanguage = playerPreferences.getPreferredSubtitleLanguage();
        }
        TrackPickerBottomSheet.show(this, type, tracks, preferredLanguage);
    }

    @Override public void onTrackSelected(MobileTrack.Type type, MobileTrack track) {
        if (track == null || viewModel == null) return;
        String id = track.getId();
        if (type == MobileTrack.Type.VIDEO) {
            viewModel.selectVideoTrack(id);
        } else if (type == MobileTrack.Type.AUDIO) {
            List<MobileTrack> tracks = snapshot == null
                    ? Collections.emptyList() : snapshot.getAudioTracks();
            MobileTrack selected = selectedTrack(tracks);
            if (baseAudioTrackId == null && selected != null && !isAudioPlaceholder(selected)) {
                baseAudioTrackId = selected.getId();
            }
            if (!id.equals(baseAudioTrackId) && !isAudioPlaceholder(track)) {
                lastAlternativeAudioTrackId = id;
            }
            viewModel.selectAudioTrack(id);
        } else {
            if (!isSubtitleOff(track)) lastSubtitleTrackId = id;
            viewModel.selectSubtitleTrack(id);
        }
        MobileDiagnostics.info("P16-Tracks", "picker selected type=" + type
                + " id=" + id + " language=" + track.getLanguage());
        keepControlsVisible();
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
            MobileTrack target = PreferredTrackResolver.preferredTrack(tracks,
                    playerPreferences.getPreferredSubtitleLanguage(), null);
            if (target != null && isSubtitleOff(target)) target = null;
            if (target == null) target = findTrack(tracks, lastSubtitleTrackId);
            if (target == null || isSubtitleOff(target)) {
                target = firstMatching(tracks, track -> !isSubtitleOff(track));
            }
            if (target != null) {
                lastSubtitleTrackId = target.getId();
                MobileDiagnostics.info("P16-Tracks", "subtitle toggle preference="
                        + playerPreferences.getPreferredSubtitleLanguage() + " available="
                        + trackLanguages(tracks) + " selected=" + target.getLanguage());
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
            MobileTrack alternative = PreferredTrackResolver.preferredTrack(tracks,
                    playerPreferences.getPreferredAudioLanguage(),
                    base == null ? null : base.getId());
            if (alternative == null) {
                alternative = findTrack(tracks, lastAlternativeAudioTrackId);
            }
            if (alternative == null || isAudioPlaceholder(alternative)
                    || base != null && alternative.getId().equals(base.getId())) {
                final MobileTrack original = base;
                alternative = firstMatching(tracks, track -> !isAudioPlaceholder(track)
                        && (original == null || !track.getId().equals(original.getId())));
            }
            if (alternative != null) {
                lastAlternativeAudioTrackId = alternative.getId();
                MobileDiagnostics.info("P16-Tracks", "audio toggle preference="
                        + playerPreferences.getPreferredAudioLanguage() + " available="
                        + trackLanguages(tracks) + " selected=" + alternative.getLanguage());
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
        List<String> labels = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        labels.add(getString(R.string.mobile_native_quick_options));
        actions.add(0);
        if (radioMode) {
            labels.add(getString(R.string.mobile_native_speed));
            actions.add(2);
        } else {
            labels.add(getString(R.string.mobile_native_quality));
            actions.add(1);
            labels.add(getString(R.string.mobile_native_speed));
            actions.add(2);
            labels.add(getString(R.string.mobile_native_fit_mode));
            actions.add(3);
            labels.add(getString(R.string.mobile_native_pip));
            actions.add(4);
            labels.add(getString(R.string.mobile_native_fullscreen));
            actions.add(5);
            if (isSmartPlayerUxEnabled() && playerPreferences.isSleepTimerEnabled()) {
                labels.add(getString(R.string.mobile_player_sleep_timer_menu));
                actions.add(6);
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_native_more_options)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    int action = actions.get(which);
                    if (action == 0) showQuickOptions();
                    else if (action == 1) showTracks(MobileTrack.Type.VIDEO);
                    else if (action == 2) showSpeedMenu();
                    else if (action == 3) cycleResizeMode();
                    else if (action == 4) enterPictureInPicture();
                    else if (action == 5) toggleFullscreen();
                    else if (action == 6) showSleepTimerMenu();
                })
                .show();
    }

    private void cycleResizeMode() {
        manualResizeOverride = true;
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
        ui.removeCallbacks(restoreAutoOrientation);
        ui.postDelayed(restoreAutoOrientation, 450L);
        keepControlsVisible();
    }

    private static String trackLanguages(List<MobileTrack> tracks) {
        StringBuilder out = new StringBuilder();
        if (tracks != null) {
            for (MobileTrack track : tracks) {
                if (track == null || isSubtitleOff(track) || isAudioPlaceholder(track)) continue;
                if (out.length() > 0) out.append(',');
                out.append(track.getLanguage().isEmpty() ? "?" : track.getLanguage());
            }
        }
        return out.toString();
    }

    private void setControlsVisible(boolean visible) {
        if (controls == null) return;
        if (controlsLocked) {
            controls.setVisibility(View.GONE);
            ui.removeCallbacks(hideControls);
            return;
        }
        controls.setVisibility(visible ? View.VISIBLE : View.GONE);
        ui.removeCallbacks(hideControls);
        if (visible && snapshot != null && snapshot.isPlaying()
                && (radioMode || playerPreferences == null
                || playerPreferences.isAutoHideControlsEnabled())) {
            ui.postDelayed(hideControls, CONTROLS_TIMEOUT_MS);
        }
    }

    private void keepControlsVisible() {
        setControlsVisible(true);
    }

    private boolean isSmartPlayerUxEnabled() {
        return featureFlags != null && featureFlags.isSmartPlayerUxEnabled();
    }

    private boolean handleVerticalPlayerGesture(View root, MotionEvent event) {
        if (!isSmartPlayerUxEnabled() || radioMode || shortMode || playerPreferences == null) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            gestureDownX = event.getX();
            gestureDownY = event.getY();
            verticalGestureMode = GESTURE_NONE;
            verticalGestureActive = false;
            gestureStartBrightness = currentWindowBrightness();
            gestureStartVolume = audioManager == null ? 0
                    : audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - gestureDownX;
            float dy = event.getY() - gestureDownY;
            if (!verticalGestureActive) {
                float absX = Math.abs(dx);
                float absY = Math.abs(dy);
                if (absY < touchSlop * 1.5f || absY <= absX * 1.25f) return false;
                boolean left = gestureDownX < root.getWidth() / 2f;
                if (left && playerPreferences.isBrightnessGestureEnabled()) {
                    verticalGestureMode = GESTURE_BRIGHTNESS;
                } else if (!left && playerPreferences.isVolumeGestureEnabled()) {
                    verticalGestureMode = GESTURE_VOLUME;
                } else {
                    return false;
                }
                verticalGestureActive = true;
                ui.removeCallbacks(hideControls);
                setControlsVisible(false);
            }
            float fraction = (gestureDownY - event.getY()) / Math.max(1f, root.getHeight());
            if (verticalGestureMode == GESTURE_BRIGHTNESS) {
                float requestedBrightness = gestureStartBrightness + fraction * 1.35f;
                if (BrightnessGesturePolicy.usesSystemBrightness(requestedBrightness)) {
                    setWindowBrightnessAutomatic();
                    showAutomaticBrightnessFeedback();
                } else {
                    float brightness = clamp01(requestedBrightness);
                    setWindowBrightness(brightness);
                    showGestureFeedback(R.string.mobile_player_gesture_brightness,
                            Math.round(brightness * 100f));
                }
            } else if (verticalGestureMode == GESTURE_VOLUME && audioManager != null) {
                int volume = Math.round(gestureStartVolume + fraction * 1.35f * maxMusicVolume);
                volume = Math.max(0, Math.min(maxMusicVolume, volume));
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0);
                int percent = Math.round(volume * 100f / Math.max(1, maxMusicVolume));
                showGestureFeedback(R.string.mobile_player_gesture_volume, percent);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean consumed = verticalGestureActive;
            verticalGestureActive = false;
            verticalGestureMode = GESTURE_NONE;
            if (consumed) {
                ui.removeCallbacks(hideGestureFeedback);
                ui.postDelayed(hideGestureFeedback, GESTURE_FEEDBACK_TIMEOUT_MS);
                return true;
            }
        }
        return false;
    }

    private float currentWindowBrightness() {
        if (!isAdded()) return 0.5f;
        try {
            WindowManager.LayoutParams params = requireActivity().getWindow().getAttributes();
            if (params.screenBrightness >= 0f) return clamp01(params.screenBrightness);
            int system = Settings.System.getInt(requireContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 128);
            return clamp01(system / 255f);
        } catch (Throwable ignored) {
            return 0.5f;
        }
    }

    private void setWindowBrightness(float value) {
        if (!isAdded()) return;
        try {
            WindowManager.LayoutParams params = requireActivity().getWindow().getAttributes();
            params.screenBrightness = BrightnessGesturePolicy.clampManualBrightness(value);
            requireActivity().getWindow().setAttributes(params);
            brightnessAdjusted = true;
        } catch (Throwable ignored) {
        }
    }

    private void setWindowBrightnessAutomatic() {
        if (!isAdded()) return;
        try {
            WindowManager.LayoutParams params = requireActivity().getWindow().getAttributes();
            // Remove the app override and follow Android's current brightness policy.
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            requireActivity().getWindow().setAttributes(params);
            brightnessAdjusted = true;
        } catch (Throwable ignored) {
        }
    }

    private void restoreWindowBrightnessOverride() {
        if (!brightnessAdjusted || getActivity() == null || Float.isNaN(originalWindowBrightness)) return;
        try {
            WindowManager.LayoutParams params = getActivity().getWindow().getAttributes();
            params.screenBrightness = originalWindowBrightness;
            getActivity().getWindow().setAttributes(params);
        } catch (Throwable ignored) {
        }
        brightnessAdjusted = false;
    }

    private void showGestureFeedback(int labelRes, int percent) {
        if (gestureFeedback == null || gestureFeedbackText == null
                || gestureFeedbackProgress == null) return;
        int safe = Math.max(0, Math.min(100, percent));
        gestureFeedbackText.setText(getString(labelRes, safe));
        gestureFeedbackProgress.setProgress(safe);
        gestureFeedback.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideGestureFeedback);
    }

    private void showAutomaticBrightnessFeedback() {
        if (gestureFeedback == null || gestureFeedbackText == null
                || gestureFeedbackProgress == null) return;
        gestureFeedbackText.setText(R.string.mobile_player_gesture_brightness_automatic);
        gestureFeedbackProgress.setProgress(0);
        gestureFeedback.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideGestureFeedback);
    }

    private void setControlsLocked(boolean locked) {
        if (locked && (radioMode || !isSmartPlayerUxEnabled()
                || playerPreferences == null || !playerPreferences.isPlayerLockEnabled())) {
            return;
        }
        controlsLocked = locked;
        ui.removeCallbacks(hideControls);
        ui.removeCallbacks(hideUnlockPrompt);
        if (locked) {
            if (controls != null) controls.setVisibility(View.GONE);
            showUnlockPrompt();
            Toast.makeText(requireContext(), R.string.mobile_player_locked_hint,
                    Toast.LENGTH_SHORT).show();
        } else {
            if (unlockButton != null) unlockButton.setVisibility(View.GONE);
            setControlsVisible(true);
        }
    }

    private void showUnlockPrompt() {
        if (!controlsLocked || unlockButton == null) return;
        unlockButton.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideUnlockPrompt);
        ui.postDelayed(hideUnlockPrompt, UNLOCK_PROMPT_TIMEOUT_MS);
    }

    private void showSleepTimerMenu() {
        if (!isSmartPlayerUxEnabled() || playerPreferences == null
                || !playerPreferences.isSleepTimerEnabled() || radioMode) return;
        String[] labels = {
                getString(R.string.mobile_player_sleep_timer_off),
                getString(R.string.mobile_player_sleep_timer_15),
                getString(R.string.mobile_player_sleep_timer_30),
                getString(R.string.mobile_player_sleep_timer_45),
                getString(R.string.mobile_player_sleep_timer_60),
                getString(R.string.mobile_player_sleep_timer_end_video)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_player_sleep_timer_menu)
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        cancelSleepTimer(true);
                    } else if (which >= 1 && which <= 4) {
                        int[] minutes = {15, 30, 45, 60};
                        setSleepTimerDuration(minutes[which - 1] * 60_000L);
                    } else if (which == 5) {
                        MobilePlaybackSnapshot current = snapshot;
                        if (current == null || current.getMediaId() == null || current.getMediaId().isEmpty()) {
                            Toast.makeText(requireContext(), R.string.mobile_player_sleep_timer_unavailable,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            setSleepTimerEndOfCurrentVideo(current.getMediaId());
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setSleepTimerEndOfCurrentVideo(String mediaId) {
        sleepTimerEndElapsedMs = -1L;
        sleepTimerEndMediaId = mediaId;
        ui.removeCallbacks(sleepTimerTick);
        updateSleepTimerBadge();
        Toast.makeText(requireContext(), R.string.mobile_player_sleep_timer_set_end_video,
                Toast.LENGTH_SHORT).show();
    }

    private void handleEndOfMediaSleepTimer(MobilePlaybackSnapshot current) {
        if (sleepTimerEndMediaId == null || sleepTimerEndMediaId.isEmpty() || current == null) return;
        if (!isSmartPlayerUxEnabled() || playerPreferences == null
                || !playerPreferences.isSleepTimerEnabled()) {
            cancelSleepTimer(false);
            return;
        }
        boolean targetEnded = sleepTimerEndMediaId.equals(current.getMediaId()) && current.isEnded();
        boolean autoAdvanced = current.getMediaId() != null
                && !sleepTimerEndMediaId.equals(current.getMediaId());
        if (targetEnded || autoAdvanced) finishSleepTimer();
    }

    private void finishSleepTimer() {
        sleepTimerEndElapsedMs = -1L;
        sleepTimerEndMediaId = null;
        ui.removeCallbacks(sleepTimerTick);
        updateSleepTimerBadge();
        if (viewModel != null) viewModel.pause();
        if (isAdded()) {
            Toast.makeText(requireContext(), R.string.mobile_player_sleep_timer_finished,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void setSleepTimerDuration(long durationMs) {
        long safe = Math.max(1_000L, durationMs);
        sleepTimerEndMediaId = null;
        sleepTimerEndElapsedMs = SystemClock.elapsedRealtime() + safe;
        updateSleepTimerBadge();
        scheduleSleepTimerTick();
        int minutes = (int) Math.max(1L, (safe + 59_999L) / 60_000L);
        Toast.makeText(requireContext(), getString(R.string.mobile_player_sleep_timer_set, minutes),
                Toast.LENGTH_SHORT).show();
    }

    private void cancelSleepTimer(boolean notify) {
        sleepTimerEndElapsedMs = -1L;
        sleepTimerEndMediaId = null;
        ui.removeCallbacks(sleepTimerTick);
        updateSleepTimerBadge();
        if (notify && isAdded()) {
            Toast.makeText(requireContext(), R.string.mobile_player_sleep_timer_cancelled,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void scheduleSleepTimerTick() {
        ui.removeCallbacks(sleepTimerTick);
        if (sleepTimerEndElapsedMs > 0L) ui.postDelayed(sleepTimerTick, 1_000L);
    }

    private void handleSleepTimerTick() {
        if (sleepTimerEndElapsedMs <= 0L) return;
        if (!isSmartPlayerUxEnabled() || playerPreferences == null
                || !playerPreferences.isSleepTimerEnabled()) {
            cancelSleepTimer(false);
            return;
        }
        long remaining = sleepTimerEndElapsedMs - SystemClock.elapsedRealtime();
        if (remaining <= 0L) {
            finishSleepTimer();
            return;
        }
        updateSleepTimerBadge();
        scheduleSleepTimerTick();
    }

    private void updateSleepTimerBadge() {
        if (sleepBadge == null) return;
        if ((sleepTimerEndElapsedMs <= 0L && (sleepTimerEndMediaId == null || sleepTimerEndMediaId.isEmpty()))
                || !isSmartPlayerUxEnabled() || playerPreferences == null
                || !playerPreferences.isSleepTimerEnabled()) {
            sleepBadge.setVisibility(View.GONE);
            return;
        }
        if (sleepTimerEndMediaId != null && !sleepTimerEndMediaId.isEmpty()) {
            sleepBadge.setText(R.string.mobile_player_sleep_timer_active_end_video);
        } else {
            long remaining = Math.max(0L, sleepTimerEndElapsedMs - SystemClock.elapsedRealtime());
            int minutes = (int) Math.max(1L, (remaining + 59_999L) / 60_000L);
            sleepBadge.setText(getString(R.string.mobile_player_sleep_timer_active, minutes));
        }
        sleepBadge.setVisibility(View.VISIBLE);
    }

    private void persistRememberedZoom() {
        if (radioMode || !isSmartPlayerUxEnabled() || playerPreferences == null
                || !playerPreferences.isRememberZoomEnabled()) return;
        playerPreferences.setRememberedZoomScale(shortMode, videoScale);
    }

    private void restoreRememberedZoom() {
        float scale = !radioMode && isSmartPlayerUxEnabled() && playerPreferences != null
                && playerPreferences.isRememberZoomEnabled()
                ? playerPreferences.getRememberedZoomScale(shortMode) : 1f;
        videoScale = Math.max(1f, Math.min(4f, scale));
        videoTranslationX = 0f;
        videoTranslationY = 0f;
        panningVideo = false;
        panMoved = false;
        if (videoSurface != null) {
            videoSurface.post(() -> {
                if (videoSurface == null) return;
                videoSurface.setPivotX(videoSurface.getWidth() / 2f);
                videoSurface.setPivotY(videoSurface.getHeight() / 2f);
                videoSurface.setScaleX(videoScale);
                videoSurface.setScaleY(videoScale);
                videoSurface.setTranslationX(0f);
                videoSurface.setTranslationY(0f);
                clampVideoTranslation();
            });
        }
    }

    private static String videoGeometrySignature(List<MobileTrack> tracks, MobileTrack selected) {
        StringBuilder out = new StringBuilder();
        if (selected != null) {
            out.append(selected.getId()).append(':').append(selected.getWidth())
                    .append('x').append(selected.getHeight());
        }
        if ((selected == null || selected.getAspectRatio() <= 0f) && tracks != null) {
            for (MobileTrack candidate : tracks) {
                if (candidate != null && candidate.getAspectRatio() > 0f) {
                    out.append("|fallback:").append(candidate.getId()).append(':')
                            .append(candidate.getWidth()).append('x').append(candidate.getHeight());
                    break;
                }
            }
        }
        return out.toString();
    }

    private void applySmartFit(MobilePlaybackSnapshot current) {
        if (current == null || videoSurface == null || viewModel == null || manualResizeOverride
                || radioMode || !isSmartPlayerUxEnabled() || playerPreferences == null
                || !playerPreferences.isSmartFitEnabled()) return;
        int surfaceWidth = videoSurface.getWidth();
        int surfaceHeight = videoSurface.getHeight();
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return;
        MobileTrack format = selectedTrack(current.getVideoTracks());
        if (format == null || format.getAspectRatio() <= 0f) {
            for (MobileTrack candidate : current.getVideoTracks()) {
                if (candidate != null && candidate.getAspectRatio() > 0f) {
                    format = candidate;
                    break;
                }
            }
        }
        if (format == null || format.getAspectRatio() <= 0f) return;
        float videoRatio = format.getAspectRatio();
        float surfaceRatio = (float) surfaceWidth / surfaceHeight;
        float mismatch = Math.abs(videoRatio - surfaceRatio) / Math.max(videoRatio, surfaceRatio);
        int smartMode = mismatch <= SMART_FIT_TOLERANCE
                ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : AspectRatioFrameLayout.RESIZE_MODE_FIT;
        if (resizeMode != smartMode) {
            resizeMode = smartMode;
            viewModel.setResizeMode(smartMode);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void resetVideoZoom() {
        videoScale = 1f;
        videoTranslationX = 0f;
        videoTranslationY = 0f;
        panningVideo = false;
        panMoved = false;
        if (videoSurface != null) {
            videoSurface.setScaleX(1f);
            videoSurface.setScaleY(1f);
            videoSurface.setTranslationX(0f);
            videoSurface.setTranslationY(0f);
            videoSurface.setPivotX(videoSurface.getWidth() / 2f);
            videoSurface.setPivotY(videoSurface.getHeight() / 2f);
        }
    }

    private void clampVideoTranslation() {
        if (videoSurface == null) return;
        float maxX = Math.max(0f, videoSurface.getWidth() * (videoScale - 1f) / 2f);
        float maxY = Math.max(0f, videoSurface.getHeight() * (videoScale - 1f) / 2f);
        videoTranslationX = Math.max(-maxX, Math.min(maxX, videoTranslationX));
        videoTranslationY = Math.max(-maxY, Math.min(maxY, videoTranslationY));
        videoSurface.setTranslationX(videoTranslationX);
        videoSurface.setTranslationY(videoTranslationY);
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
