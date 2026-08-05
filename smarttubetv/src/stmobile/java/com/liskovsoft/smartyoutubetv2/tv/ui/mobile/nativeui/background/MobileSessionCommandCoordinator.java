package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.background;

/** Applies pure audio-focus decisions to the actual playback port. */
final class MobileSessionCommandCoordinator {
    interface Output {
        void play();
        void pause();
        void setVolumeMultiplier(float multiplier);
    }

    private static final float DUCK_VOLUME = 0.20f;
    private final MobileAudioFocusPolicy policy;
    private final Output output;

    MobileSessionCommandCoordinator(MobileAudioFocusPolicy policy, Output output) {
        if (policy == null) throw new IllegalArgumentException("policy == null");
        if (output == null) throw new IllegalArgumentException("output == null");
        this.policy = policy;
        this.output = output;
    }

    void onPlayRequested(MobileAudioFocusPolicy.FocusRequestResult result) {
        apply(policy.onPlayRequested(result));
    }

    void onExternalPlaybackStarted(MobileAudioFocusPolicy.FocusRequestResult result) {
        apply(policy.onExternalPlaybackStarted(result));
    }

    void onUserPause() { apply(policy.onUserPause()); }
    void onStop() { apply(policy.onStop()); }

    void onFocusEvent(MobileAudioFocusPolicy.FocusEvent event, boolean currentlyPlaying) {
        apply(policy.onFocusEvent(event, currentlyPlaying));
    }

    boolean hasPlayIntent() { return policy.hasPlayIntent(); }
    boolean shouldKeepForegroundService() { return policy.shouldKeepForegroundService(); }

    private void apply(MobileAudioFocusPolicy.Decision decision) {
        if (decision.shouldRestoreVolume()) output.setVolumeMultiplier(1f);
        if (decision.shouldDuck()) output.setVolumeMultiplier(DUCK_VOLUME);
        if (decision.shouldPause()) output.pause();
        if (decision.shouldPlay()) output.play();
    }
}
