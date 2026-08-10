package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.playbackengine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * AndroidX Media3 engine used first for direct audio sources (Radio and Offline).
 *
 * <p>It intentionally owns audio focus and "becoming noisy" handling, matching the responsibility
 * of SmartTube's legacy ExoPlayer controller. No public MediaSession is created here; Android Auto
 * continues to own its existing stable MediaSessionCompat while the migration is staged.</p>
 */
@SuppressLint("UnsafeOptInUsageError")
public final class Media3PlaybackEngine implements MobilePlaybackEngine {
    public static final String ENGINE_NAME = "Media3 ExoPlayer";

    private final Context appContext;
    private final Listener listener;
    private ExoPlayer player;
    private float speed = 1f;
    private float pitch = 1f;
    private float volume = 1f;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onPlaybackStateChanged(int playbackState) {
            dispatchState(playbackState);
        }

        @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (listener != null) listener.onEngineStateChanged(mapState(), playWhenReady);
        }

        @Override public void onPlayerError(@NonNull PlaybackException error) {
            if (listener != null) listener.onEngineError(error);
        }
    };

    public Media3PlaybackEngine(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    @Override public String getEngineName() {
        return ENGINE_NAME;
    }

    @Override public boolean isInitialized() {
        return player != null;
    }

    @Override public void open(String uri, long startPositionMs, boolean playWhenReady) {
        if (uri == null || uri.trim().isEmpty()) {
            throw new IllegalArgumentException("Direct media URI is empty");
        }
        ensurePlayer();
        MediaItem item = new MediaItem.Builder().setUri(Uri.parse(uri)).build();
        player.setMediaItem(item, Math.max(0L, startPositionMs));
        player.setPlayWhenReady(playWhenReady);
        player.prepare();
    }

    @Override public void setPlayWhenReady(boolean playWhenReady) {
        if (player == null) return;
        player.setPlayWhenReady(playWhenReady);
    }

    @Override public boolean getPlayWhenReady() {
        return player != null && player.getPlayWhenReady();
    }

    @Override public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    @Override public boolean isLoading() {
        return player != null && player.isLoading();
    }

    @Override public boolean containsMedia() {
        return player != null && player.getMediaItemCount() > 0;
    }

    @Override public State getState() {
        return mapState();
    }

    @Override public long getPositionMs() {
        return player == null ? 0L : Math.max(0L, player.getCurrentPosition());
    }

    @Override public long getDurationMs() {
        if (player == null) return 0L;
        long value = player.getDuration();
        return value == C.TIME_UNSET || value < 0L ? 0L : value;
    }

    @Override public long getBufferedPositionMs() {
        return player == null ? 0L : Math.max(0L, player.getBufferedPosition());
    }

    @Override public void seekTo(long positionMs) {
        if (player != null) player.seekTo(Math.max(0L, positionMs));
    }

    @Override public void setSpeed(float value) {
        speed = sanitizeRate(value);
        applyPlaybackParameters();
    }

    @Override public float getSpeed() {
        return speed;
    }

    @Override public void setPitch(float value) {
        pitch = sanitizeRate(value);
        applyPlaybackParameters();
    }

    @Override public float getPitch() {
        return pitch;
    }

    @Override public void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
        if (player != null) player.setVolume(volume);
    }

    @Override public float getVolume() {
        return player == null ? volume : player.getVolume();
    }

    @Override public void release() {
        if (player == null) return;
        player.removeListener(playerListener);
        player.release();
        player = null;
    }

    private void ensurePlayer() {
        if (player != null) return;
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        player = new ExoPlayer.Builder(appContext)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build();
        player.setPlaybackParameters(new PlaybackParameters(speed, pitch));
        player.setVolume(volume);
        player.addListener(playerListener);
    }

    private void applyPlaybackParameters() {
        if (player != null) player.setPlaybackParameters(new PlaybackParameters(speed, pitch));
    }

    private State mapState() {
        if (player == null) return State.IDLE;
        switch (player.getPlaybackState()) {
            case Player.STATE_BUFFERING:
                return State.BUFFERING;
            case Player.STATE_READY:
                return State.READY;
            case Player.STATE_ENDED:
                return State.ENDED;
            case Player.STATE_IDLE:
            default:
                return State.IDLE;
        }
    }

    private void dispatchState(int ignoredState) {
        if (listener != null && player != null) {
            listener.onEngineStateChanged(mapState(), player.getPlayWhenReady());
        }
    }

    private static float sanitizeRate(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0f) return 1f;
        return Math.max(0.1f, Math.min(5f, value));
    }
}
