package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.annotation.SuppressLint;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.BufferingDetector;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.common.misc.BufferingDetector.OnLongBuffering;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.List;

public class ErrorFixerController extends BasePlayerController implements OnLongBuffering {
    private static final String TAG = ErrorFixerController.class.getSimpleName();
    private static final long STREAM_END_THRESHOLD_MS = 180_000;
    // V13: recover a silent startup stall before the mobile-only 8s watchdog blindly reloads SABR.
    // This controller is shared by TV/mobile, so the policy applies globally rather than by language.
    private static final long STARTUP_PROGRESSIVE_FALLBACK_MS = 7_000;
    private final BufferingDetector mBufferingDetector = new BufferingDetector(this);
    private final Runnable mStartupProgressiveFallback = this::runStartupProgressiveFallback;
    private VideoLoaderController mVideoLoaderController;

    @Override
    public void onInit() {
        mVideoLoaderController = getController(VideoLoaderController.class);
    }

    @Override
    public void onEngineError(int type, int rendererIndex, Throwable error) {
        Log.e(TAG, "Player error occurred: %s. Trying to fix…", type);
        MobileDiagnostics.sessionError("EngineError",
                "type=" + type + " renderer=" + rendererIndex, error);

        runEngineErrorAction(type, rendererIndex, error);
    }

    @Override
    public void onLongBuffering() {
        if (getPlayer() == null) {
            return;
        }

        MobileDiagnostics.session("LongBuffer",
                "playable=" + mBufferingDetector.isPlayable()
                        + " offline=" + isOfflineVideo()
                        + " subtitles=" + isSubtitlesEnabled());

        if (isStreamEnded()) {
            getMainController().onPlayEnd();
        } else if (isOfflineVideo() && isSubtitlesEnabled()) {
            // Long loading subtitles cause hangs
            disableSubtitles();
            mVideoLoaderController.reloadVideo();
        } else if (!mBufferingDetector.isPlayable()
                && mVideoLoaderController.isProgressiveFallbackActiveForCurrentVideo()) {
            // The one-shot muxed recovery also stalled. Disable it before client rotation,
            // otherwise the sticky fallback flag would select the same URL again.
            Log.d(TAG, "V13_GLOBAL_FALLBACK progressive long-buffer failed; rotate client");
            MobileDiagnostics.session("V13_GLOBAL_FALLBACK", "progressive long-buffer failed");
            mVideoLoaderController.disableProgressiveFallbackForCurrentVideo();
            YouTubeServiceManager.instance().applyNoPlaybackFix();
            mVideoLoaderController.reloadVideo();
        } else if (!mBufferingDetector.isPlayable()
                && mVideoLoaderController.activateProgressiveFallbackForCurrentVideo("long-buffer")) {
            // V14: replace the stalled source from cached format info immediately.
            // Do not issue another /player request or start the same SABR twice.
            Log.d(TAG, "V14_FAST_START long buffering -> cached progressive");
            MobileDiagnostics.session("V14_FAST_START", "long-buffer -> cached-progressive");
        } else if (!mBufferingDetector.isPlayable()) {
            // No muxed survival URL exists: continue with upstream rotation.
            MessageHelpers.showLongMessage(getContext(), "Fixing stalled client...");
            YouTubeServiceManager.instance().applyNoPlaybackFix();
            mVideoLoaderController.reloadVideo();
        } else if (!getPlayerTweaksData().isNetworkErrorFixingDisabled()) {
            // Avoid reloading after lowering quality: restart the engine instead.
            lowerVideoQuality();
            mVideoLoaderController.restartEngine();
        }
    }

    @Override
    public void onBuffering() {
        mBufferingDetector.onStartBuffering();
        if (!mBufferingDetector.isPlayable()) {
            Utils.removeCallbacks(mStartupProgressiveFallback);
            Utils.postDelayed(mStartupProgressiveFallback, STARTUP_PROGRESSIVE_FALLBACK_MS);
        }
    }

    @Override
    public void onSeekEnd() {
        mBufferingDetector.reset();
        Utils.removeCallbacks(mStartupProgressiveFallback);
        // Detect a stall that starts immediately after a seek.
        mBufferingDetector.onStartBuffering();
    }

    @Override
    public void onPlay() {
        Utils.removeCallbacks(mStartupProgressiveFallback);
        mBufferingDetector.onStopBuffering();
    }

    @Override
    public void onPause() {
        // A user/app pause can happen while the source is still BUFFERING.
        // Keep the startup recovery armed until the engine is no longer loading.
        if (getPlayer() == null || !getPlayer().isLoading()) {
            Utils.removeCallbacks(mStartupProgressiveFallback);
        }
        mBufferingDetector.onStopBuffering();
    }

    @Override
    public void onNewVideo(Video item) {
        Utils.removeCallbacks(mStartupProgressiveFallback);
        mBufferingDetector.start();
        // Arm recovery from the media lifecycle itself, not only from a buffering
        // callback. Some legacy ExoPlayer/SABR stalls never deliver that callback
        // reliably, which is exactly what the aa1.28 diagnostic log exposed.
        Utils.postDelayed(mStartupProgressiveFallback, STARTUP_PROGRESSIVE_FALLBACK_MS);
    }

    @Override
    public void onFinish() {
        Utils.removeCallbacks(mStartupProgressiveFallback);
        mBufferingDetector.reset();
    }

    @Override
    public void onEngineReleased() {
        Utils.removeCallbacks(mStartupProgressiveFallback);
        mBufferingDetector.reset();
    }

    private void runStartupProgressiveFallback() {
        if (getPlayer() == null || !getPlayer().isLoading()
                || mVideoLoaderController == null
                || mVideoLoaderController.isProgressiveFallbackActiveForCurrentVideo()) {
            return;
        }

        if (mVideoLoaderController.activateProgressiveFallbackForCurrentVideo("startup-stall")) {
            Log.d(TAG, "V14_FAST_START startup stall -> cached progressive");
            MobileDiagnostics.session("V14_FAST_START", "startup-stall -> cached-progressive");
        }
    }

    private void runEngineErrorAction(int type, int rendererIndex, Throwable error) {
        // Hide begin errors in embed mode (e.g. wrong date/time: unable to connect to...)
        if (isEmbedPlayer() && getPlayer() != null && getPlayer().getPositionMs() == 0) {
            getPlayer().finish();
            return;
        }

        if (isStreamEnded()) {
            // Url no longer works (e.g. live stream ended)
            getMainController().onPlayEnd();
            return;
        }

        applyEngineErrorAction(type, rendererIndex, error);
    }

    private void applyEngineErrorAction(int type, int rendererIndex, Throwable error) {
        boolean restartEngine = true;
        boolean showMessage = true;
        String errorContent = error != null ? error.getMessage() : null;
        boolean forbiddenStream = Helpers.startsWithAny(
                errorContent, "Response code: 403");
        String errorTitle = getErrorTitle(type, rendererIndex);
        String errorMessage = errorTitle + "\n" + errorContent;

        if (Helpers.startsWithAny(errorContent, "Unable to connect to")) {
            // No internet connection or WRONG DATE on the device
            // Recently this message starting to show for other reasons
            //YouTubeServiceManager.instance().applyNoPlaybackFix(); // ?
            //switchNextEngine(); // ?
            //restartEngine = false;
            if (!getPlayerTweaksData().isNetworkErrorFixingDisabled()) {
                switchNextEngine();
            }
        } else if (error instanceof OutOfMemoryError || (error != null && error.getCause() instanceof OutOfMemoryError)) {
            if (getPlayerTweaksData().getPlayerDataSource() == PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP) {
                // OkHttp has memory leak problems
                enableFasterDataSource();
            } else if (getPlayerData().getVideoBufferType() == PlayerData.BUFFER_HIGH || getPlayerData().getVideoBufferType() == PlayerData.BUFFER_HIGHEST) {
                getPlayerData().setVideoBufferType(PlayerData.BUFFER_MEDIUM);
            } else {
                getPlayerTweaksData().setSectionPlaylistEnabled(false);
                restartEngine = false;
            }
        } else if (Helpers.containsAny(errorContent, "Exception in CronetUrlRequest") && !getPlayerTweaksData().isNetworkErrorFixingDisabled()) {
            if (getVideo() != null && !getVideo().isLive) { // Finished live stream may provoke errors in Cronet
                getPlayerTweaksData().setPlayerDataSource(PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT);
            } else {
                restartEngine = false;
            }
        } else if (type == PlayerEventListener.ERROR_TYPE_SOURCE && rendererIndex == PlayerEventListener.RENDERER_INDEX_UNKNOWN) {
            // NOTE: Starts with any (url deciphered incorrectly)
            // "Response code: 403" (poToken error, forbidden)
            // "Response code: 404" (not sure whether below helps)
            // "Response code: 503" (not sure whether below helps)
            // "Response code: 400" (not sure whether below helps)
            // "Response code: 429" (subtitle error, too many requests)
            // "Response code: 500" (subtitle error, generic server error)

            // NOTE: Fixing too many requests or network issues
            // NOTE: All these errors have unknown renderer (-1)
            // "Unable to connect to", "Invalid NAL length", "Response code: 421",
            // "Response code: 404", "Response code: 429", "Invalid integer size",
            // "Unexpected ArrayIndexOutOfBoundsException", "Unexpected IndexOutOfBoundsException"

            //if (Helpers.startsWithAny(errorContent, "Response code: 403")) {
            //    YouTubeServiceManager.instance().applyNoPlaybackFix();
            //} else if (isSubtitlesEnabled()) {
            //    disableSubtitles(); // Response code: 429
            //} else if (getPlayerTweaksData().isHighBitrateFormatsEnabled()) {
            //    getPlayerTweaksData().setHighBitrateFormatsEnabled(false); // Response code: 429
            //} else {
            //    YouTubeServiceManager.instance().applyNoPlaybackFix(); // Response code: 403
            //}

            restartEngine = false;
            showMessage = false;

            boolean isGeneralError = Helpers.startsWithAny(errorContent, "Response code: 429", "Response code: 500");
            if (isGeneralError && isSubtitlesEnabled()) {
                disableSubtitles(); // Response code: 429
            } else if (isGeneralError && getPlayerTweaksData().isHighBitrateFormatsEnabled()) {
                getPlayerTweaksData().setHighBitrateFormatsEnabled(false); // Response code: 429
            } else if (!mBufferingDetector.isPlayable()) {
                if (mVideoLoaderController.isProgressiveFallbackActiveForCurrentVideo()) {
                    // The muxed fallback itself failed before becoming playable. Do not loop it,
                    // regardless of whether the failure surfaced as 403, 404, parser error, etc.
                    Log.d(TAG, "V13_GLOBAL_FALLBACK progressive rejected before play; rotate client");
                    MobileDiagnostics.session("V13_GLOBAL_FALLBACK", "progressive rejected before play");
                    mVideoLoaderController.disableProgressiveFallbackForCurrentVideo();
                    YouTubeServiceManager.instance().applyNoPlaybackFix();
                } else if (mVideoLoaderController.activateProgressiveFallbackForCurrentVideo("source-error")) {
                    // Any startup source failure gets one direct/muxed attempt before engine/client
                    // rotation. Open it from cached metadata immediately and stop this error action
                    // so the generic reload below cannot undo the source switch.
                    Log.d(TAG, "V14_FAST_START startup source error -> cached progressive");
                    MobileDiagnostics.session("V14_FAST_START", "source-error -> cached-progressive");
                    return;
                } else {
                    // No direct survival URL exists: preserve upstream recovery.
                    switchNextEngine();
                    restartEngine = true;
                    showMessage = true;
                }
            } else if (forbiddenStream && mVideoLoaderController.isProgressiveFallbackActiveForCurrentVideo()) {
                // Progressive played but GVS later rejected it too. Disable the survival path and rotate client.
                Log.d(TAG, "V10_PROGRESSIVE fallback got mid-stream 403; disabling");
                MobileDiagnostics.session("V10_PROGRESSIVE", "fallback got mid-stream 403; disabling");
                mVideoLoaderController.disableProgressiveFallbackForCurrentVideo();
                YouTubeServiceManager.instance().applyNoPlaybackFix();
            } else if (forbiddenStream
                    && mVideoLoaderController.activateProgressiveFallbackForCurrentVideo("midstream-403")) {
                // The high-quality source played and then GVS rejected a later range.
                // Switch to the already-deciphered muxed URL without a second /player request.
                Log.d(TAG, "V14_FAST_START mid-stream 403 -> cached progressive");
                MobileDiagnostics.session("V14_FAST_START", "midstream-403 -> cached-progressive");
                return;
            } else {
                YouTubeServiceManager.instance().applyNoPlaybackFix(); // Response code: 403
            }
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER && rendererIndex == PlayerEventListener.RENDERER_INDEX_SUBTITLE) {
            // "Response code: 429" (subtitle error)
            // "Response code: 500" (subtitle error)
            disableSubtitles();
            restartEngine = false;
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER && rendererIndex == PlayerEventListener.RENDERER_INDEX_VIDEO) {
            getPlayerData().setFormat(FormatItem.VIDEO_FHD_AVC_30);
            if (getPlayerTweaksData().isSWDecoderForced()) {
                getPlayerTweaksData().setSWDecoderForced(false);
            } else {
                restartEngine = false;
            }
        } else if (type == PlayerEventListener.ERROR_TYPE_RENDERER && rendererIndex == PlayerEventListener.RENDERER_INDEX_AUDIO) {
            getPlayerData().setFormat(FormatItem.AUDIO_HQ_MP4A);
            restartEngine = false;
        } else if (type == PlayerEventListener.ERROR_TYPE_UNEXPECTED) {
            // IllegalStateException: Buffer too small (5242880 < 7208383)
            if (Helpers.startsWithAny(errorContent, "Buffer too small", "Invalid to call at Released state; only valid in executing state")) {
                lowerVideoQuality();
                //restartEngine = false;
            }
        }

        if (showMessage) {
            MessageHelpers.showLongMessage(getContext(), errorMessage);
            if (getPlayer() != null) {
                getPlayer().setTitle(errorContent);
            }
        }

        if (restartEngine) {
            mVideoLoaderController.restartEngine();
        } else {
            // Need at least to reload the video because the player becomes idle after error
            if (forbiddenStream) {
                // Cache invalidation above rotates the player response/client. Shorts often
                // expose a stale signed URL first, so don't leave a visible one-second pause.
                mVideoLoaderController.reloadVideoAfterStreamRefresh();
            } else {
                mVideoLoaderController.reloadVideo();
            }
        }
    }

    @SuppressLint("StringFormatMatches")
    private String getErrorTitle(int type, int rendererIndex) {
        String errorTitle;
        int msgResId;

        switch (type) {
            // Some ciphered data could be outdated.
            // Might happen when the app wasn't used quite a long time.
            case PlayerEventListener.ERROR_TYPE_SOURCE:
                switch (rendererIndex) {
                    case PlayerEventListener.RENDERER_INDEX_VIDEO:
                        msgResId = R.string.msg_player_error_video_source;
                        break;
                    case PlayerEventListener.RENDERER_INDEX_AUDIO:
                        msgResId = R.string.msg_player_error_audio_source;
                        break;
                    case PlayerEventListener.RENDERER_INDEX_SUBTITLE:
                        msgResId = R.string.msg_player_error_subtitle_source;
                        break;
                    default:
                        msgResId = R.string.unknown_source_error;
                }
                errorTitle = getContext().getString(msgResId);
                break;
            case PlayerEventListener.ERROR_TYPE_RENDERER:
                switch (rendererIndex) {
                    case PlayerEventListener.RENDERER_INDEX_VIDEO:
                        msgResId = R.string.msg_player_error_video_renderer;
                        break;
                    case PlayerEventListener.RENDERER_INDEX_AUDIO:
                        msgResId = R.string.msg_player_error_audio_renderer;
                        break;
                    case PlayerEventListener.RENDERER_INDEX_SUBTITLE:
                        msgResId = R.string.msg_player_error_subtitle_renderer;
                        break;
                    default:
                        msgResId = R.string.unknown_renderer_error;
                }
                errorTitle = getContext().getString(msgResId);
                break;
            case PlayerEventListener.ERROR_TYPE_UNEXPECTED:
                errorTitle = getContext().getString(R.string.player_unexpected_error);
                break;
            default:
                errorTitle = getContext().getString(R.string.msg_player_error, type);
                break;
        }

        return errorTitle;
    }

    public void runFormatErrorAction(Throwable error) {
        if (getPlayer() == null) {
            return;
        }

        if (isEmbedPlayer()) {
            getPlayer().finish();
            return;
        }

        MobileDiagnostics.sessionError("FormatError", "loadFormatInfo failed", error);
        String message = error.getMessage();
        String className = error.getClass().getSimpleName();
        String fullMsg = String.format("loadFormatInfo error: %s: %s", className, Utils.getStackTraceAsString(error));
        Log.e(TAG, fullMsg);

        if (!Helpers.containsAny(message, "fromNullable result is null")) {
            MessageHelpers.showLongMessage(getContext(), fullMsg);
        }

        if (Helpers.containsAny(message, "Unexpected token", "Syntax error", "invalid argument") || // temporal fix
                Helpers.equalsAny(className, "PoTokenException", "BadWebViewException")) {
            YouTubeServiceManager.instance().applyNoPlaybackFix();
            mVideoLoaderController.reloadVideo();
        } else if (Helpers.containsAny(message, "is not defined")) {
            YouTubeServiceManager.instance().invalidateCache();
            mVideoLoaderController.reloadVideo();
        } else {
            Log.e(TAG, "Probably no internet connection");
            mVideoLoaderController.reloadVideo();
        }
    }

    /**
     * Bad idea. Faster source is different among devices
     */
    private void enableFasterDataSource() {
        if (isFasterDataSourceEnabled()) {
            return;
        }

        getPlayerTweaksData().setPlayerDataSource(getFasterDataSource());
    }

    private static int getFasterDataSource() {
        return Utils.skipCronet() ? PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT : PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET;
    }

    /**
     * Bad idea. Faster source is different among devices
     */
    private boolean isFasterDataSourceEnabled() {
        int fasterDataSource = getFasterDataSource();
        return getPlayerTweaksData().getPlayerDataSource() == fasterDataSource;
    }

    private void switchNextEngine() {
        getPlayerTweaksData().setPlayerDataSource(getNextEngine());
    }

    private int getNextEngine() {
        int currentEngine = getPlayerTweaksData().getPlayerDataSource();
        Integer[] engineList = Utils.skipCronet() ?
                new Integer[] { PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT, PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP } :
                new Integer[] { PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET, PlayerTweaksData.PLAYER_DATA_SOURCE_DEFAULT, PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP };
        return Helpers.getNextValue(engineList, currentEngine);
    }

    private boolean isSubtitlesEnabled() {
        return getPlayer() != null && !FormatItem.SUBTITLE_NONE.equals(getPlayer().getSubtitleFormat());
    }

    private void disableSubtitles() {
        getPlayerData().setSubtitlesPerChannelEnabled(false); // Important!
        getPlayerData().setFormat(FormatItem.SUBTITLE_NONE);
    }

    private boolean isStreamEnded() {
        if (getPlayer() == null || getVideo() == null) {
            return false;
        }

        return getVideo().isLiveEnd && getPlayer().getDurationMs() > 0
                && getPlayer().getDurationMs() - getPlayer().getPositionMs() < STREAM_END_THRESHOLD_MS;
    }

    private boolean isOfflineVideo() {
        if (getPlayer() == null || getVideo() == null) {
            return false;
        }

        return !getVideo().isLive && !getVideo().isLiveEnd;
    }

    private void lowerVideoQuality() {
        if (getPlayer() == null) {
            return;
        }

        List<FormatItem> videoFormats = getPlayer().getVideoFormats();

        if (videoFormats == null) {
            return;
        }

        int idx = videoFormats.indexOf(getPlayer().getVideoFormat());
        int nextIdx = idx + 1;

        if (videoFormats.size() > nextIdx) {
            FormatItem formatItem = videoFormats.get(nextIdx);
            getPlayer().setFormat(formatItem);
            // This helps to persist the format between engine restart
            getPlayerData().setTempVideoFormat(formatItem);
        }
    }
}
