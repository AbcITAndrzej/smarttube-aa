package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.background;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.session.MediaButtonReceiver;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.host.MobileNativeActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobilePlaybackSnapshot;
import java.lang.ref.WeakReference;

/**
 * Owns the mobile MediaSession, Audio Focus and MediaStyle notification.
 *
 * The class intentionally uses MediaSessionCompat because SmartTube currently ships ExoPlayer 2
 * and androidx.media. Moving this source set to Media3 should be done together with the player
 * engine migration instead of running two independent session stacks.
 */
public final class MobileMediaSessionManager {
    public interface PlaybackControl {
        void playFromSystem();
        void pauseFromSystem();
        void seekToFromSystem(long positionMs);
        void setVolumeMultiplier(float multiplier);
    }

    static final String ACTION_REFRESH = "app.smarttube.mobile.action.MEDIA_REFRESH";
    static final String ACTION_PLAY = "app.smarttube.mobile.action.MEDIA_PLAY";
    static final String ACTION_PAUSE = "app.smarttube.mobile.action.MEDIA_PAUSE";
    static final String ACTION_REWIND = "app.smarttube.mobile.action.MEDIA_REWIND";
    static final String ACTION_FORWARD = "app.smarttube.mobile.action.MEDIA_FORWARD";
    static final String ACTION_STOP = "app.smarttube.mobile.action.MEDIA_STOP";

    static final int NOTIFICATION_ID = 28041;
    private static final String CHANNEL_ID = "smarttube_mobile_playback";
    private static final long SEEK_STEP_MS = 10_000L;
    private static volatile WeakReference<MobileMediaSessionManager> sActive =
            new WeakReference<>(null);

    private final Context appContext;
    private final PlaybackControl playback;
    private final AudioManager audioManager;
    private final Handler mainHandler;
    private final MobileAudioFocusPolicy focusPolicy = new MobileAudioFocusPolicy();
    private final MobileSessionCommandCoordinator commandCoordinator;
    private final MediaSessionCompat mediaSession;
    private final AudioManager.OnAudioFocusChangeListener focusListener;
    private final BroadcastReceiver noisyReceiver;
    private AudioFocusRequest audioFocusRequest;
    private MobilePlaybackSnapshot snapshot;
    private boolean focusRequestOutstanding;
    private boolean focusGranted;
    private boolean released;
    private boolean hostVisible = true;
    private boolean notificationDismissed;
    private boolean noisyReceiverRegistered;
    private boolean playerHandlesAudioFocus;

    public MobileMediaSessionManager(Context context, PlaybackControl playback) {
        if (context == null) throw new IllegalArgumentException("context == null");
        if (playback == null) throw new IllegalArgumentException("playback == null");
        appContext = context.getApplicationContext();
        this.playback = playback;
        commandCoordinator = new MobileSessionCommandCoordinator(focusPolicy,
                new MobileSessionCommandCoordinator.Output() {
                    @Override public void play() { playback.playFromSystem(); }
                    @Override public void pause() { playback.pauseFromSystem(); }
                    @Override public void setVolumeMultiplier(float multiplier) {
                        playback.setVolumeMultiplier(multiplier);
                    }
                });
        audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());
        focusListener = change -> mainHandler.post(() -> handleAudioFocusChange(change));
        noisyReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                    MobileDiagnostics.info("AudioFocus", "audio becoming noisy; pausing playback");
                    pauseByUser();
                }
            }
        };
        createNotificationChannel();
        mediaSession = new MediaSessionCompat(appContext, "SmartTubeMobileSession");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay() { requestPlay(); }
            @Override public void onPause() { pauseByUser(); }
            @Override public void onStop() { stopAndDismiss(); }
            @Override public void onSeekTo(long pos) { seekTo(pos); }
            @Override public void onRewind() { seekBy(-SEEK_STEP_MS); }
            @Override public void onFastForward() { seekBy(SEEK_STEP_MS); }
        }, mainHandler);
        mediaSession.setSessionActivity(createContentIntent());
        mediaSession.setMediaButtonReceiver(createMediaButtonPendingIntent());
        mediaSession.setActive(true);
        sActive = new WeakReference<>(this);
        updateSessionState();
        MobileDiagnostics.info("MediaSession", "mobile media session created");
    }

    /** User/UI play request. Playback starts only after focus is granted. */
    public void requestPlay() {
        runOnMain(() -> {
            if (released) return;
            notificationDismissed = false;
            if (playerHandlesAudioFocus) {
                playback.playFromSystem();
            } else {
                commandCoordinator.onPlayRequested(requestAudioFocus());
            }
            synchronizeSystemSurface();
        });
    }

    /** User/UI pause request. Transient focus pauses use the focus callback instead. */
    public void pauseByUser() {
        runOnMain(() -> {
            if (released) return;
            if (playerHandlesAudioFocus) {
                playback.pauseFromSystem();
            } else {
                commandCoordinator.onUserPause();
                abandonAudioFocus();
            }
            synchronizeSystemSurface();
        });
    }

    /**
     * Direct streams are opened by the existing ExoPlayer controller, whose own audio-focus
     * manager must remain the single owner. The MediaSession still exposes metadata, transport
     * controls and the notification, but does not request a second focus grant for the process.
     */
    public void setPlayerHandlesAudioFocus(boolean value) {
        runOnMain(() -> {
            if (released || playerHandlesAudioFocus == value) return;
            playerHandlesAudioFocus = value;
            if (value) abandonAudioFocus();
        });
    }

    public void seekTo(long positionMs) {
        runOnMain(() -> playback.seekToFromSystem(Math.max(0L, positionMs)));
    }

    public void seekBy(long deltaMs) {
        runOnMain(() -> {
            long current = snapshot == null ? 0L : snapshot.getPositionMs();
            long duration = snapshot == null ? 0L : snapshot.getDurationMs();
            long target = Math.max(0L, current + deltaMs);
            if (duration > 0L) target = Math.min(duration, target);
            playback.seekToFromSystem(target);
        });
    }

    /** Must be called for every player state/metadata change, even when no UI listener exists. */
    public void updatePlayback(MobilePlaybackSnapshot value) {
        runOnMain(() -> {
            if (released) return;
            snapshot = value;
            if (!playerHandlesAudioFocus && value != null && value.isPlaying()
                    && !focusRequestOutstanding) {
                notificationDismissed = false;
                commandCoordinator.onExternalPlaybackStarted(requestAudioFocus());
            }
            updateSessionMetadata();
            mediaSession.setSessionActivity(createContentIntent());
            updateSessionState();
            synchronizeSystemSurface();
        });
    }

    public void setHostVisible(boolean visible) {
        runOnMain(() -> {
            hostVisible = visible;
            synchronizeSystemSurface();
        });
    }

    public boolean isBackgroundPlaybackEnabled() { return true; }
    public boolean isHostVisible() { return hostVisible; }

    public void release() {
        runOnMain(() -> {
            if (released) return;
            released = true;
            commandCoordinator.onStop();
            abandonAudioFocus();
            unregisterNoisyReceiver();
            mediaSession.setActive(false);
            mediaSession.setCallback(null);
            mediaSession.release();
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID);
            MobileBackgroundPlaybackService.stop(appContext);
            MobileMediaSessionManager current = getActive();
            if (current == this) sActive = new WeakReference<>(null);
            snapshot = null;
            MobileDiagnostics.info("MediaSession", "mobile media session released");
        });
    }

    void handleServiceAction(String action) {
        if (ACTION_PLAY.equals(action)) requestPlay();
        else if (ACTION_PAUSE.equals(action)) pauseByUser();
        else if (ACTION_REWIND.equals(action)) seekBy(-SEEK_STEP_MS);
        else if (ACTION_FORWARD.equals(action)) seekBy(SEEK_STEP_MS);
        else if (ACTION_STOP.equals(action)) stopAndDismiss();
        else synchronizeSystemSurface();
    }

    void handleMediaButtonIntent(Intent intent) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
    }

    Notification buildNotification() {
        MobilePlaybackSnapshot current = snapshot;
        boolean playing = current != null && current.isPlaying();
        String title = current == null || current.getTitle().trim().isEmpty()
                ? appContext.getString(R.string.app_name) : current.getTitle();
        String subtitle = current == null ? "" : current.getSubtitle();
        PendingIntent stopIntent = servicePendingIntent(ACTION_STOP, 6);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.mobile_ic_notification)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setContentIntent(createContentIntent())
                .setDeleteIntent(stopIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setOngoing(playing || commandCoordinator.shouldKeepForegroundService())
                .setShowWhen(false)
                .addAction(R.drawable.mobile_ic_rewind,
                        appContext.getString(R.string.mobile_background_rewind),
                        servicePendingIntent(ACTION_REWIND, 2))
                .addAction(playing ? R.drawable.mobile_ic_pause : R.drawable.mobile_ic_play,
                        appContext.getString(playing
                                ? R.string.mobile_background_pause : R.string.mobile_background_play),
                        servicePendingIntent(playing ? ACTION_PAUSE : ACTION_PLAY, 3))
                .addAction(R.drawable.mobile_ic_forward,
                        appContext.getString(R.string.mobile_background_forward),
                        servicePendingIntent(ACTION_FORWARD, 4))
                .addAction(R.drawable.mobile_ic_stop,
                        appContext.getString(R.string.mobile_background_stop),
                        stopIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(stopIntent));
        return builder.build();
    }

    boolean shouldRunForeground() {
        return MobileBackgroundPlaybackPolicy.shouldRunForeground(
                released, notificationDismissed, snapshot != null && snapshot.isPrepared(),
                snapshot != null && snapshot.isPlaying(), commandCoordinator.hasPlayIntent());
    }

    boolean shouldShowNotification() {
        return MobileBackgroundPlaybackPolicy.shouldShowNotification(
                released, notificationDismissed, snapshot != null && snapshot.isPrepared());
    }

    MediaSessionCompat.Token getSessionToken() { return mediaSession.getSessionToken(); }

    static MobileMediaSessionManager getActive() { return sActive.get(); }

    private void stopAndDismiss() {
        runOnMain(() -> {
            if (released) return;
            notificationDismissed = true;
            commandCoordinator.onStop();
            abandonAudioFocus();
            synchronizeSystemSurface();
        });
    }

    private void handleAudioFocusChange(int focusChange) {
        if (released) return;
        MobileAudioFocusPolicy.FocusEvent event;
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                focusGranted = true;
                focusRequestOutstanding = true;
                event = MobileAudioFocusPolicy.FocusEvent.GAIN;
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                focusGranted = false;
                focusRequestOutstanding = false;
                event = MobileAudioFocusPolicy.FocusEvent.LOSS;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                focusGranted = false;
                event = MobileAudioFocusPolicy.FocusEvent.LOSS_TRANSIENT;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                event = MobileAudioFocusPolicy.FocusEvent.LOSS_TRANSIENT_CAN_DUCK;
                break;
            default:
                return;
        }
        boolean playing = snapshot != null && snapshot.isPlaying();
        MobileDiagnostics.debug("AudioFocus", "focus change=" + focusChange + " playing=" + playing);
        commandCoordinator.onFocusEvent(event, playing);
        updateSessionState();
        synchronizeSystemSurface();
    }

    private MobileAudioFocusPolicy.FocusRequestResult requestAudioFocus() {
        if (audioManager == null) {
            MobileDiagnostics.error("AudioFocus", "AudioManager unavailable", null);
            return MobileAudioFocusPolicy.FocusRequestResult.DENIED;
        }
        if (focusGranted) return MobileAudioFocusPolicy.FocusRequestResult.GRANTED;
        int result;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (audioFocusRequest == null) {
                    AudioAttributes attributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build();
                    audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(attributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setWillPauseWhenDucked(false)
                            .setOnAudioFocusChangeListener(focusListener, mainHandler)
                            .build();
                }
                result = audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                result = audioManager.requestAudioFocus(focusListener,
                        AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (RuntimeException error) {
            MobileDiagnostics.error("AudioFocus", "requestAudioFocus failed", error);
            result = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        }
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusRequestOutstanding = true;
            focusGranted = true;
            return MobileAudioFocusPolicy.FocusRequestResult.GRANTED;
        }
        if (Build.VERSION.SDK_INT >= 26 && result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
            focusRequestOutstanding = true;
            focusGranted = false;
            return MobileAudioFocusPolicy.FocusRequestResult.DELAYED;
        }
        focusRequestOutstanding = false;
        focusGranted = false;
        return MobileAudioFocusPolicy.FocusRequestResult.DENIED;
    }

    private void abandonAudioFocus() {
        if (audioManager == null || !focusRequestOutstanding) return;
        try {
            if (Build.VERSION.SDK_INT >= 26 && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(focusListener);
            }
        } catch (RuntimeException error) {
            MobileDiagnostics.error("AudioFocus", "abandonAudioFocus failed", error);
        }
        focusRequestOutstanding = false;
        focusGranted = false;
    }

    private void updateSessionMetadata() {
        if (snapshot == null) return;
        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, snapshot.getMediaId())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snapshot.getTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, snapshot.getSubtitle())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, snapshot.getDurationMs())
                .build();
        mediaSession.setMetadata(metadata);
    }

    private void updateSessionState() {
        int state = PlaybackStateCompat.STATE_NONE;
        long position = 0L;
        float speed = 0f;
        long buffered = 0L;
        if (snapshot != null) {
            position = snapshot.getPositionMs();
            buffered = snapshot.getBufferedPositionMs();
            speed = snapshot.isPlaying() ? snapshot.getSpeed() : 0f;
            if (snapshot.isBuffering()) state = PlaybackStateCompat.STATE_BUFFERING;
            else if (snapshot.isPlaying()) state = PlaybackStateCompat.STATE_PLAYING;
            else if (snapshot.isPrepared()) state = PlaybackStateCompat.STATE_PAUSED;
        }
        long actions = PlaybackStateCompat.ACTION_PLAY
                | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE
                | PlaybackStateCompat.ACTION_SEEK_TO
                | PlaybackStateCompat.ACTION_REWIND
                | PlaybackStateCompat.ACTION_FAST_FORWARD
                | PlaybackStateCompat.ACTION_STOP;
        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(actions)
                .setBufferedPosition(buffered)
                .setState(state, position, speed, SystemClock.elapsedRealtime())
                .build();
        mediaSession.setPlaybackState(playbackState);
    }

    private void synchronizeSystemSurface() {
        updateSessionState();
        if (shouldShowNotification()) {
            try {
                NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, buildNotification());
            } catch (SecurityException error) {
                MobileDiagnostics.error("MediaSession", "notification permission denied", error);
            }
        } else {
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID);
        }
        synchronizeNoisyReceiver();
        MobileBackgroundPlaybackService.synchronize(appContext);
    }

    private void synchronizeNoisyReceiver() {
        boolean needed = !released && !notificationDismissed
                && (commandCoordinator.hasPlayIntent()
                || snapshot != null && snapshot.isPlaying());
        if (needed && !noisyReceiverRegistered) {
            IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    appContext.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    appContext.registerReceiver(noisyReceiver, filter);
                }
                noisyReceiverRegistered = true;
            } catch (RuntimeException error) {
                MobileDiagnostics.error("AudioFocus", "register noisy receiver failed", error);
            }
        } else if (!needed) {
            unregisterNoisyReceiver();
        }
    }

    private void unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return;
        try {
            appContext.unregisterReceiver(noisyReceiver);
        } catch (RuntimeException error) {
            MobileDiagnostics.error("AudioFocus", "unregister noisy receiver failed", error);
        } finally {
            noisyReceiverRegistered = false;
        }
    }

    private PendingIntent createContentIntent() {
        Intent intent = new Intent(appContext, MobileNativeActivity.class)
                .setAction(MobileNativeActivity.ACTION_OPEN_BACKGROUND_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (snapshot != null) {
            intent.putExtra(MobileNativeActivity.EXTRA_MEDIA_ID, snapshot.getMediaId());
            intent.putExtra(MobileNativeActivity.EXTRA_POSITION_MS, snapshot.getPositionMs());
        }
        return PendingIntent.getActivity(appContext, 1, intent, immutableUpdateCurrent());
    }

    private PendingIntent createMediaButtonPendingIntent() {
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON, null, appContext,
                MobileBackgroundPlaybackService.class);
        return PendingIntent.getService(appContext, 7, intent, immutableUpdateCurrent());
    }

    private PendingIntent servicePendingIntent(String action, int requestCode) {
        Intent intent = new Intent(appContext, MobileBackgroundPlaybackService.class).setAction(action);
        return PendingIntent.getService(appContext, requestCode, intent, immutableUpdateCurrent());
    }

    private static int immutableUpdateCurrent() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager)
                appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                appContext.getString(R.string.mobile_background_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(appContext.getString(R.string.mobile_background_channel_description));
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else mainHandler.post(action);
    }
}
