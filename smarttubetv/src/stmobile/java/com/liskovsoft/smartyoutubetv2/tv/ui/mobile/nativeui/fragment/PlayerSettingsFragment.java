package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileFragmentSupport;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.MobileEnhancementPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.MobileInstantPlayPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.MobilePlayerPreferences;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.PlayerLanguageCatalog;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.SubtitleAppearanceDialog;
import java.util.List;

/** Phone/tablet player settings. This fragment intentionally has no Android Auto dependency. */
public final class PlayerSettingsFragment extends Fragment {
    private MobilePlayerPreferences preferences;
    private MobileEnhancementPreferences enhancements;
    private MobileInstantPlayPreferences instantPlay;
    private MaterialButton preferredAudio;
    private MaterialButton preferredSubtitles;
    private MaterialButton doubleTapInterval;
    private MaterialButton subtitleAppearance;

    public static PlayerSettingsFragment newInstance() {
        return new PlayerSettingsFragment();
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                 @Nullable ViewGroup container,
                                                 @Nullable Bundle state) {
        return inflater.inflate(R.layout.mobile_player_settings_fragment, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        preferences = new MobilePlayerPreferences(requireContext());
        enhancements = new MobileEnhancementPreferences(requireContext());
        instantPlay = new MobileInstantPlayPreferences(requireContext());

        MaterialToolbar toolbar = view.findViewById(R.id.mobile_toolbar);
        toolbar.setNavigationIcon(R.drawable.mobile_ic_back_24);
        toolbar.setNavigationContentDescription(R.string.mobile_native_back);
        toolbar.setNavigationOnClickListener(v -> MobileFragmentSupport.navigator(this).goBack());

        bindSwitch(view, R.id.mobile_player_pref_auto_hide,
                preferences.isAutoHideControlsEnabled(), preferences::setAutoHideControlsEnabled);
        bindSwitch(view, R.id.mobile_player_pref_pinch_zoom,
                preferences.isPinchZoomEnabled(), preferences::setPinchZoomEnabled);
        bindSwitch(view, R.id.mobile_player_pref_double_tap,
                preferences.isDoubleTapSeekEnabled(), preferences::setDoubleTapSeekEnabled);
        bindSwitch(view, R.id.mobile_player_pref_swipe_seek,
                preferences.isSwipeSeekEnabled(), preferences::setSwipeSeekEnabled);
        bindSwitch(view, R.id.mobile_player_pref_brightness_gesture,
                preferences.isBrightnessGestureEnabled(), preferences::setBrightnessGestureEnabled);
        bindSwitch(view, R.id.mobile_player_pref_volume_gesture,
                preferences.isVolumeGestureEnabled(), preferences::setVolumeGestureEnabled);
        bindSwitch(view, R.id.mobile_player_pref_screen_lock,
                preferences.isPlayerLockEnabled(), preferences::setPlayerLockEnabled);
        bindSwitch(view, R.id.mobile_player_pref_sleep_timer,
                preferences.isSleepTimerEnabled(), preferences::setSleepTimerEnabled);
        bindSwitch(view, R.id.mobile_player_pref_remember_zoom,
                preferences.isRememberZoomEnabled(), preferences::setRememberZoomEnabled);
        bindSwitch(view, R.id.mobile_player_pref_smart_fit,
                preferences.isSmartFitEnabled(), preferences::setSmartFitEnabled);
        bindSwitch(view, R.id.mobile_player_pref_previous_next,
                preferences.isPreviousNextVisible(), preferences::setPreviousNextVisible);
        bindSwitch(view, R.id.mobile_player_pref_quick_options,
                preferences.isQuickOptionsVisible(), preferences::setQuickOptionsVisible);
        bindSwitch(view, R.id.mobile_player_pref_subtitles,
                preferences.isSubtitlesVisible(), preferences::setSubtitlesVisible);
        bindSwitch(view, R.id.mobile_player_pref_audio,
                preferences.isAudioVisible(), preferences::setAudioVisible);
        bindSwitch(view, R.id.mobile_player_pref_quality,
                preferences.isQualityVisible(), preferences::setQualityVisible);
        bindSwitch(view, R.id.mobile_player_pref_speed,
                preferences.isSpeedVisible(), preferences::setSpeedVisible);
        bindSwitch(view, R.id.mobile_player_pref_fit,
                preferences.isFitVisible(), preferences::setFitVisible);
        bindSwitch(view, R.id.mobile_player_pref_pip,
                preferences.isPipVisible(), preferences::setPipVisible);
        bindSwitch(view, R.id.mobile_player_pref_fullscreen,
                preferences.isFullscreenVisible(), preferences::setFullscreenVisible);
        bindSwitch(view, R.id.mobile_player_pref_more,
                preferences.isMoreVisible(), preferences::setMoreVisible);

        bindSwitch(view, R.id.mobile_player_pref_sponsorblock_markers,
                enhancements.isSponsorBlockSeekBarMarkersEnabled(),
                enhancements::setSponsorBlockSeekBarMarkersEnabled);
        bindSwitch(view, R.id.mobile_player_pref_dearrow_native_lists,
                enhancements.isDeArrowNativeListsEnabled(),
                enhancements::setDeArrowNativeListsEnabled);
        bindSwitch(view, R.id.mobile_player_pref_original_titles_native_lists,
                enhancements.isUnlocalizedTitlesNativeListsEnabled(),
                enhancements::setUnlocalizedTitlesNativeListsEnabled);
        bindSwitch(view, R.id.mobile_player_pref_fallback_thumbnails_native_lists,
                enhancements.isFallbackThumbnailsNativeListsEnabled(),
                enhancements::setFallbackThumbnailsNativeListsEnabled);

        bindSwitch(view, R.id.mobile_player_pref_instant_play,
                instantPlay.isEnabled(), instantPlay::setEnabled);
        bindSwitch(view, R.id.mobile_player_pref_instant_403_recovery,
                instantPlay.isForbiddenRecoveryEnabled(),
                instantPlay::setForbiddenRecoveryEnabled);
        bindSwitch(view, R.id.mobile_player_pref_startup_watchdog,
                instantPlay.isStartupWatchdogEnabled(),
                instantPlay::setStartupWatchdogEnabled);

        preferredAudio = view.findViewById(R.id.mobile_player_pref_audio_language);
        preferredSubtitles = view.findViewById(R.id.mobile_player_pref_subtitle_language);
        doubleTapInterval = view.findViewById(R.id.mobile_player_pref_double_tap_interval);
        subtitleAppearance = view.findViewById(R.id.mobile_player_pref_subtitle_appearance);
        preferredAudio.setOnClickListener(v -> chooseLanguage(true));
        preferredSubtitles.setOnClickListener(v -> chooseLanguage(false));
        doubleTapInterval.setOnClickListener(v -> chooseDoubleTapInterval());
        subtitleAppearance.setOnClickListener(v ->
                SubtitleAppearanceDialog.show(requireContext(), this::updateSubtitleAppearanceLabel));
        view.findViewById(R.id.mobile_player_pref_subtitle_reset).setOnClickListener(v ->
                SubtitleAppearanceDialog.showResetConfirmation(requireContext(),
                        this::updateSubtitleAppearanceLabel));
        view.findViewById(R.id.mobile_player_pref_reset).setOnClickListener(v -> confirmReset());
        updateLanguageLabels();
        updateDoubleTapIntervalLabel();
        updateSubtitleAppearanceLabel();
    }

    private void bindSwitch(View root, int id, boolean checked, BooleanSetter setter) {
        SwitchMaterial control = root.findViewById(id);
        control.setChecked(checked);
        control.setOnCheckedChangeListener((buttonView, isChecked) -> setter.set(isChecked));
    }

    private void chooseLanguage(boolean audio) {
        List<PlayerLanguageCatalog.Entry> entries = PlayerLanguageCatalog.build(requireContext());
        String current = audio ? preferences.getPreferredAudioLanguage()
                : preferences.getPreferredSubtitleLanguage();
        String[] labels = new String[entries.size()];
        int checked = 0;
        for (int i = 0; i < entries.size(); i++) {
            labels[i] = entries.get(i).getLabel();
            if (entries.get(i).getCode().equalsIgnoreCase(current == null ? "" : current)) checked = i;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(audio ? R.string.mobile_player_default_audio
                        : R.string.mobile_player_default_subtitles)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    String code = entries.get(which).getCode();
                    if (audio) preferences.setPreferredAudioLanguage(code);
                    else preferences.setPreferredSubtitleLanguage(code);
                    updateLanguageLabels();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void chooseDoubleTapInterval() {
        final int[] values = {5, 10, 15, 30};
        String[] labels = new String[values.length];
        int current = preferences.getDoubleTapSeekSeconds();
        int checked = 1;
        for (int i = 0; i < values.length; i++) {
            labels[i] = getString(R.string.mobile_player_double_tap_interval, values[i]);
            if (values[i] == current) checked = i;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_player_double_tap_interval_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    preferences.setDoubleTapSeekSeconds(values[which]);
                    updateDoubleTapIntervalLabel();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateDoubleTapIntervalLabel() {
        if (doubleTapInterval != null) {
            doubleTapInterval.setText(getString(R.string.mobile_player_double_tap_interval,
                    preferences.getDoubleTapSeekSeconds()));
        }
    }

    private void updateLanguageLabels() {
        if (preferredAudio != null) {
            preferredAudio.setText(getString(R.string.mobile_player_default_audio_value,
                    PlayerLanguageCatalog.labelFor(requireContext(),
                            preferences.getPreferredAudioLanguage())));
        }
        if (preferredSubtitles != null) {
            preferredSubtitles.setText(getString(R.string.mobile_player_default_subtitles_value,
                    PlayerLanguageCatalog.labelFor(requireContext(),
                            preferences.getPreferredSubtitleLanguage())));
        }
    }

    private void updateSubtitleAppearanceLabel() {
        if (subtitleAppearance != null && isAdded()) {
            subtitleAppearance.setText(SubtitleAppearanceDialog.summary(requireContext()));
        }
    }

    private void confirmReset() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mobile_player_reset_title)
                .setMessage(R.string.mobile_player_reset_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.mobile_player_reset_confirm, (dialog, which) -> {
                    preferences.reset();
                    enhancements.reset();
                    instantPlay.reset();
                    // Recreate once so every control is rebound from the default values.
                    if (isAdded()) requireActivity().recreate();
                })
                .show();
    }

    private interface BooleanSetter {
        void set(boolean value);
    }
}
