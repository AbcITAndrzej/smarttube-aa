package com.liskovsoft.smartyoutubetv2.tv.ui.playback;

import android.annotation.TargetApi;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.fragment.app.Fragment;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerEngine;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.common.misc.MobilePlayerLifecyclePolicy;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

/**
 * Loads PlaybackFragment and delegates input from a game controller.
 * <br>
 * For more information on game controller capabilities with leanback, review the
 * <a href="https://developer.android.com/training/game-controllers/controller-input.html">docs</href>.
 */
public class PlaybackActivity extends LeanbackActivity {
    private static final String TAG = PlaybackActivity.class.getSimpleName();
    private static final float GAMEPAD_TRIGGER_INTENSITY_ON = 0.5f;
    // Off-condition slightly smaller for button debouncing.
    private static final float GAMEPAD_TRIGGER_INTENSITY_OFF = 0.45f;
    private boolean gamepadTriggerPressed = false;
    private PlaybackFragment mPlaybackFragment;
    private boolean mIsBackPressed;
    private boolean mResumeAfterMobilePause;
    private boolean mKeepPlayingForScreenOff;
    private PowerManager.WakeLock mMobileScreenOffWakeLock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MobileDiagnostics.lifecycle(this, "onCreate", "saved=" + (savedInstanceState != null));
        setContentView(R.layout.fragment_playback);
        Fragment fragment =
                getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
        if (fragment instanceof PlaybackFragment) {
            mPlaybackFragment = (PlaybackFragment) fragment;
        }
    }

    @Override
    protected void initTheme() {
        int playerThemeResId = MainUIData.instance(this).getColorScheme().playerThemeResId;
        if (playerThemeResId > 0) {
            setTheme(playerThemeResId);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchKeyEvent(event);
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchTouchEvent(event);
        }

        boolean handled = super.dispatchTouchEvent(event);

        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchTouchEventFinished(event);
        }

        return handled;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (mPlaybackFragment != null) {
            mPlaybackFragment.onDispatchGenericMotionEvent(event);
        }

        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            mPlaybackFragment.skipToNext();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_L1) {
            mPlaybackFragment.skipToPrevious();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_L2) {
            mPlaybackFragment.rewind();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            mPlaybackFragment.fastForward();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // This method will handle gamepad events.
        if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > GAMEPAD_TRIGGER_INTENSITY_ON
                && !gamepadTriggerPressed) {
            mPlaybackFragment.rewind();
            gamepadTriggerPressed = true;
        } else if (event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > GAMEPAD_TRIGGER_INTENSITY_ON
                && !gamepadTriggerPressed) {
            mPlaybackFragment.fastForward();
            gamepadTriggerPressed = true;
        } else if (event.getAxisValue(MotionEvent.AXIS_LTRIGGER) < GAMEPAD_TRIGGER_INTENSITY_OFF
                && event.getAxisValue(MotionEvent.AXIS_RTRIGGER) < GAMEPAD_TRIGGER_INTENSITY_OFF) {
            gamepadTriggerPressed = false;
        } else if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0 && event.getAction() == MotionEvent.ACTION_SCROLL) {
            // mouse wheel handling
            Utils.volumeUp(this, getPlaybackView(), event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    // For N devices that support it, not "officially"
    // More: https://medium.com/s23nyc-tech/drop-in-android-video-exoplayer2-with-picture-in-picture-e2d4f8c1eb30
    @TargetApi(24)
    @SuppressWarnings("deprecation")
    private void enterPipMode() {
        // NOTE: When exiting PIP mode onPause is called immediately after onResume

        // Also, avoid enter pip on stop!
        // More info: https://developer.android.com/guide/topics/ui/picture-in-picture#continuing_playback

        if (Helpers.isPictureInPictureSupported(this)) {
            if (wannaEnterToPip()) {
                Log.d(TAG, "Entering PIP mode...");

                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        PictureInPictureParams.Builder params = new PictureInPictureParams.Builder();
                        enterPictureInPictureMode(params.build());
                    } else {
                        enterPictureInPictureMode();
                    }
                } catch (Exception e) {
                    // Device doesn't support picture-in-picture mode
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    /**
     * BACK pressed, PIP player's button pressed
     */
    @Override
    public void finish() {
        Log.d(TAG, "Finishing activity...");
        MobileDiagnostics.lifecycle(this, "finish", "fragment=" + (mPlaybackFragment != null));
        if (mPlaybackFragment == null) {
            super.finish();
            return;
        }

        //if (isBackgroundBackEnabled()) {
        //    mPlaybackFragment.blockEngine(true);
        //}

        // NOTE: When exiting PIP mode onPause is called immediately after onResume

        // Also, avoid enter pip on stop!
        // More info: https://developer.android.com/guide/topics/ui/picture-in-picture#continuing_playback

        // NOTE: block back button for PIP.
        // User pressed PIP button in the player.
        if (!skipPip()) {
            enterPipMode(); // NOTE: without this call app will hangs when pressing on PIP button
        }

        if (doNotDestroy() && !skipPip()) {
            mPlaybackFragment.blockEngine(true);
            // Ensure to opening this activity when the user is returning to the app
            getViewManager().blockTop(this);
            getViewManager().startParentView(this);
        } else {
            if (getPlayerTweaksData().isKeepFinishedActivityEnabled()) {
                //moveTaskToBack(true); // Don't do this or you'll have problems when player overlaps other apps (e.g. casting)
                getViewManager().startParentView(this);

                // Player with TextureView keeps running in background because onStop() fired with huge delay (~5sec).
                mPlaybackFragment.maybeReleasePlayer();
            } else {
                super.finish();
            }
        }
    }

    @Override
    public void finishReally() {
        super.finishReally();

        mPlaybackFragment.onFinish();
    }

    @Override
    protected void onPause() {
        mKeepPlayingForScreenOff = shouldKeepPlayingForScreenOff();

        MobilePlayerLifecyclePolicy.Action mobileAction = mKeepPlayingForScreenOff
                ? MobilePlayerLifecyclePolicy.Action.KEEP_PLAYING
                : resolveMobileLifecycleAction(false);
        MobileDiagnostics.lifecycle(this, "onPause",
                "action=" + mobileAction + ", screenOff=" + mKeepPlayingForScreenOff);

        if (isMobileTouchNavigationEnabled() && mPlaybackFragment != null
                && mobileAction == MobilePlayerLifecyclePolicy.Action.PAUSE
                && mPlaybackFragment.getPlayWhenReady()) {
            mResumeAfterMobilePause = true;
            mPlaybackFragment.setPlayWhenReady(false);
        }

        boolean hasDialogBug = AppDialogPresenter.instance(this).isDialogShown()
                && Build.VERSION.SDK_INT <= 23;
        boolean configuredBackground = getPlayerData().getBackgroundMode()
                != PlayerData.BACKGROUND_MODE_DEFAULT && Utils.isHardScreenOff(this);

        if (mPlaybackFragment != null
                && (hasDialogBug || configuredBackground || mKeepPlayingForScreenOff)) {
            mPlaybackFragment.blockEngine(true);
        }

        if (mKeepPlayingForScreenOff) {
            acquireMobileScreenOffWakeLock();
        }

        // Run the code before the contained fragment
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        mIsBackPressed = true;
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        mIsBackPressed = false;
        super.onResume();

        releaseMobileScreenOffWakeLock();
        mKeepPlayingForScreenOff = false;

        MobileDiagnostics.lifecycle(this, "onResume", "restore=" + mResumeAfterMobilePause);
        if (mResumeAfterMobilePause && mPlaybackFragment != null) {
            mPlaybackFragment.setPlayWhenReady(true);
            mResumeAfterMobilePause = false;
        }
    }

    @Override
    protected void onStop() {
        if (!mKeepPlayingForScreenOff && shouldKeepPlayingForScreenOff()) {
            mKeepPlayingForScreenOff = true;
            if (mPlaybackFragment != null) {
                mPlaybackFragment.blockEngine(true);
            }
            acquireMobileScreenOffWakeLock();
        }

        MobilePlayerLifecyclePolicy.Action action = mKeepPlayingForScreenOff
                ? MobilePlayerLifecyclePolicy.Action.KEEP_PLAYING
                : resolveMobileLifecycleAction(true);
        MobileDiagnostics.lifecycle(this, "onStop",
                "action=" + action + ", screenOff=" + mKeepPlayingForScreenOff);

        if (isMobileTouchNavigationEnabled() && mPlaybackFragment != null
                && action == MobilePlayerLifecyclePolicy.Action.RELEASE) {
            mPlaybackFragment.maybeReleasePlayer();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        MobileDiagnostics.lifecycle(this, "onDestroy",
                "finishing=" + isFinishing() + ", changingConfig=" + isChangingConfigurations());
        releaseMobileScreenOffWakeLock();
        if (mPlaybackFragment != null && isFinishing() && !isInPipMode()) {
            mPlaybackFragment.maybeReleasePlayer();
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        MobileDiagnostics.lifecycle(this, "onConfigurationChanged",
                "orientation=" + newConfig.orientation);
    }

    @SuppressWarnings("deprecation")
    private void enterBackgroundPlayMode() {
        if (Build.VERSION.SDK_INT >= 21 && Build.VERSION.SDK_INT < 26) {
            if (Build.VERSION.SDK_INT == 21) {
                // Playback pause fix?
                mPlaybackFragment.showOverlay(true);
            }

            if (mPlaybackFragment.isPlaying()) {
                // Argument equals true to notify the system that the activity
                // wishes to be visible behind other translucent activities
                if (!requestVisibleBehind(true)) {
                    // App-specific method to stop playback and release resources
                    // because call to requestVisibleBehind(true) failed
                    mPlaybackFragment.onDestroy();
                }
            } else {
                // Argument equals false because the activity is not playing
                requestVisibleBehind(false);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onVisibleBehindCanceled() {
        // App-specific method to stop playback and release resources
        mPlaybackFragment.onDestroy();
        super.onVisibleBehindCanceled();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        MobileDiagnostics.lifecycle(this, "onPIPChanged", "inPip=" + isInPictureInPictureMode);

        if (mPlaybackFragment != null) {
            mPlaybackFragment.onPIPChanged(isInPictureInPictureMode);
        }
    }

    /**
     * HOME or BACK pressed
     */
    @Override
    public void onUserLeaveHint() {
        // Check that user not open dialog/search activity instead of really leaving the activity
        // Activity may be overlapped by the dialog, back is pressed or new view started
        if (mIsBackPressed || isFinishing() || getViewManager().isNewViewPending() || getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK) {
            return;
        }

        switch (getPlayerData().getBackgroundMode()) {
            case PlayerData.BACKGROUND_MODE_PLAY_BEHIND:
                enterBackgroundPlayMode();
                // Do we need to do something additional when running Play Behind?
                break;
            case PlayerData.BACKGROUND_MODE_PIP:
                enterPipMode();
                if (doNotDestroy()) {
                    mPlaybackFragment.blockEngine(true);
                    // Ensure to opening this activity when the user will return to the app
                    getViewManager().blockTop(this);
                    // Enable collapse app to Home launcher
                    //getViewManager().enableMoveToBack(true);
                }
                break;
            case PlayerData.BACKGROUND_MODE_SOUND:
                if (doNotDestroy()) {
                    // Ensure to continue a playback
                    mPlaybackFragment.blockEngine(true);
                    getViewManager().blockTop(this);
                    //getViewManager().enableMoveToBack(true);
                }
                break;
        }
    }

    public boolean isInPipMode() {
        if (Build.VERSION.SDK_INT < 24) {
            return false;
        }

        return isInPictureInPictureMode();
    }

    public PlaybackView getPlaybackView() {
        return mPlaybackFragment;
    }

    private boolean skipPip() {
        return mIsBackPressed && getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME;
    }

    private boolean isEngineBlocked() {
        return mPlaybackFragment != null && mPlaybackFragment.isEngineBlocked();
    }

    @TargetApi(24)
    private boolean wannaEnterToPip() {
        //return mPlaybackFragment != null && mPlaybackFragment.getBackgroundMode() == PlayerEngine.BACKGROUND_MODE_PIP && !isInPictureInPictureMode();
        //return mPlaybackFragment != null && mPlaybackFragment.isEngineBlocked() && !isInPictureInPictureMode();
        boolean isPip = getPlayerData().getBackgroundMode() == PlayerData.BACKGROUND_MODE_PIP || isEngineBlocked();
        return isPip && !isInPictureInPictureMode();
    }

    private boolean doNotDestroy() {
        sIsInPipMode = isInPipMode();
        //return sIsInPipMode || mPlaybackFragment.getBackgroundMode() == PlayerEngine.BACKGROUND_MODE_SOUND;
        //return sIsInPipMode || mPlaybackFragment.isEngineBlocked();
        boolean isBackground = getPlayerData().getBackgroundMode() == PlayerEngine.BACKGROUND_MODE_SOUND || isEngineBlocked();
        return sIsInPipMode || isBackground;
    }

    private boolean shouldKeepPlayingForScreenOff() {
        return isMobileTouchNavigationEnabled()
                && mPlaybackFragment != null
                && mPlaybackFragment.getPlayWhenReady()
                && Utils.isHardScreenOff(this);
    }

    private void acquireMobileScreenOffWakeLock() {
        if (mMobileScreenOffWakeLock != null && mMobileScreenOffWakeLock.isHeld()) {
            return;
        }

        PowerManager powerManager =
                (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            return;
        }

        mMobileScreenOffWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SmartTubeMobile:ScreenOffPlayback");
        mMobileScreenOffWakeLock.setReferenceCounted(false);

        // Safety timeout. The lock is normally released immediately on resume
        // or activity destruction.
        mMobileScreenOffWakeLock.acquire(4L * 60L * 60L * 1000L);
    }

    private void releaseMobileScreenOffWakeLock() {
        if (mMobileScreenOffWakeLock != null) {
            if (mMobileScreenOffWakeLock.isHeld()) {
                mMobileScreenOffWakeLock.release();
            }
            mMobileScreenOffWakeLock = null;
        }
    }

    private MobilePlayerLifecyclePolicy.Action resolveMobileLifecycleAction(boolean stop) {
        MobilePlayerLifecyclePolicy.BackgroundMode mode = MobilePlayerLifecyclePolicy.BackgroundMode.DEFAULT;
        int configuredMode = getPlayerData().getBackgroundMode();
        if (configuredMode == PlayerData.BACKGROUND_MODE_PIP) {
            mode = MobilePlayerLifecyclePolicy.BackgroundMode.PICTURE_IN_PICTURE;
        } else if (configuredMode == PlayerData.BACKGROUND_MODE_SOUND
                || configuredMode == PlayerEngine.BACKGROUND_MODE_SOUND) {
            mode = MobilePlayerLifecyclePolicy.BackgroundMode.AUDIO_ONLY;
        }
        return stop
                ? MobilePlayerLifecyclePolicy.onStop(isChangingConfigurations(), isInPipMode(), mode, isFinishing())
                : MobilePlayerLifecyclePolicy.onPause(isChangingConfigurations(), isInPipMode(), mode, isFinishing());
    }

    //private boolean isBackgroundBackEnabled() {
    //    return getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_BACK ||
    //            getGeneralData().getBackgroundPlaybackShortcut() == GeneralData.BACKGROUND_PLAYBACK_SHORTCUT_HOME_BACK;
    //}
}
