package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive;

import android.app.Activity;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;

import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * P13-AA1.8 phone companion for the single SmartTubeAutoMusic MediaSession.
 *
 * This is intentionally a small music-only controller. It does not expose the normal
 * SmartTube video browser when the user taps the media notification.
 */
public final class SmartTubeAutoMusicPlayerActivity extends Activity {
    private static final String ACTION_LIKE =
            "com.liskovsoft.smarttube.mobile.auto.LIKE";
    private static final String ACTION_UNLIKE =
            "com.liskovsoft.smarttube.mobile.auto.UNLIKE";
    private static final String ACTION_RESTART =
            "com.liskovsoft.smarttube.mobile.auto.RESTART";
    private static final String ACTION_PLAY_LIKED =
            "com.liskovsoft.smarttube.mobile.auto.PLAY_LIKED";
    private static final String ACTION_TOGGLE_SHUFFLE =
            "com.liskovsoft.smarttube.mobile.auto.TOGGLE_SHUFFLE";
    private static final String ACTION_CYCLE_REPEAT =
            "com.liskovsoft.smarttube.mobile.auto.CYCLE_REPEAT";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();

    private MediaBrowserCompat mediaBrowser;
    private MediaControllerCompat mediaController;
    private ImageView artworkView;
    private TextView titleView;
    private TextView artistView;
    private TextView stateView;
    private TextView positionView;
    private SeekBar seekBar;
    private ProgressBar connectionProgress;
    private Button previousButton;
    private Button playPauseButton;
    private Button nextButton;
    private Button restartButton;
    private Button likeButton;
    private Button shuffleButton;
    private Button repeatButton;
    private Button likedMusicButton;
    private boolean userSeeking;
    private boolean liked;
    private long durationMs;
    private String loadedArtworkUri;

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            updatePositionOnly();
            mainHandler.postDelayed(this, 500L);
        }
    };

    private final MediaControllerCompat.Callback controllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    updatePlaybackState(state);
                }

                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    updateMetadata(metadata);
                }

                @Override
                public void onSessionDestroyed() {
                    stateView.setText("Sesja muzyczna została zamknięta");
                    setControlsEnabled(false);
                }
            };

    private final MediaBrowserCompat.ConnectionCallback connectionCallback =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    try {
                        mediaController = new MediaControllerCompat(
                                SmartTubeAutoMusicPlayerActivity.this,
                                mediaBrowser.getSessionToken());
                        mediaController.registerCallback(controllerCallback);
                        connectionProgress.setVisibility(View.GONE);
                        setControlsEnabled(true);
                        updateMetadata(mediaController.getMetadata());
                        updatePlaybackState(mediaController.getPlaybackState());
                        MobileDiagnostics.info("P13-AA-MobilePlayer",
                                "connected to SmartTubeAutoMusic");
                    } catch (Throwable error) {
                        showConnectionError(error);
                    }
                }

                @Override
                public void onConnectionSuspended() {
                    stateView.setText("Połączenie z odtwarzaczem zostało wstrzymane");
                    setControlsEnabled(false);
                }

                @Override
                public void onConnectionFailed() {
                    stateView.setText("Nie udało się połączyć z SmartTube Music");
                    setControlsEnabled(false);
                    connectionProgress.setVisibility(View.GONE);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        buildUi();
        wireControls();

        mediaBrowser = new MediaBrowserCompat(
                this,
                new ComponentName(this, SmartTubeAutoMusicService.class),
                connectionCallback,
                null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mediaBrowser != null && !mediaBrowser.isConnected()) {
            connectionProgress.setVisibility(View.VISIBLE);
            mediaBrowser.connect();
        }
        mainHandler.removeCallbacks(progressUpdater);
        mainHandler.post(progressUpdater);
    }

    @Override
    protected void onStop() {
        mainHandler.removeCallbacks(progressUpdater);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (mediaController != null) {
            mediaController.unregisterCallback(controllerCallback);
        }
        if (mediaBrowser != null) {
            try {
                mediaBrowser.disconnect();
            } catch (Throwable ignored) {
                // Ignore disconnect races during process shutdown.
            }
        }
        artworkExecutor.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Color.BLACK);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(18, 18, 18));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView header = text("SmartTube Music", 22, true);
        header.setGravity(Gravity.CENTER);
        root.addView(header, matchWrap());

        connectionProgress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams =
                new LinearLayout.LayoutParams(dp(36), dp(36));
        progressParams.setMargins(0, dp(12), 0, dp(12));
        root.addView(connectionProgress, progressParams);

        artworkView = new ImageView(this);
        artworkView.setImageResource(android.R.drawable.ic_media_play);
        artworkView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artworkView.setBackgroundColor(Color.rgb(35, 35, 35));
        LinearLayout.LayoutParams artworkParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(260));
        artworkParams.setMargins(0, dp(8), 0, dp(16));
        root.addView(artworkView, artworkParams);

        titleView = text("Brak aktywnego utworu", 22, true);
        titleView.setGravity(Gravity.CENTER);
        titleView.setMaxLines(2);
        root.addView(titleView, matchWrap());

        artistView = text("", 16, false);
        artistView.setTextColor(Color.LTGRAY);
        artistView.setGravity(Gravity.CENTER);
        artistView.setMaxLines(2);
        root.addView(artistView, matchWrap());

        stateView = text("Łączenie z odtwarzaczem…", 14, false);
        stateView.setTextColor(Color.GRAY);
        stateView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stateParams = matchWrap();
        stateParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(stateView, stateParams);

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        root.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        positionView = text("0:00 / 0:00", 14, false);
        positionView.setTextColor(Color.LTGRAY);
        positionView.setGravity(Gravity.CENTER);
        root.addView(positionView, matchWrap());

        LinearLayout mainControls = horizontalRow();
        previousButton = button("⏮");
        playPauseButton = button("▶");
        nextButton = button("⏭");
        mainControls.addView(previousButton, weighted());
        mainControls.addView(playPauseButton, weighted());
        mainControls.addView(nextButton, weighted());
        root.addView(mainControls, rowParams());

        LinearLayout actionRow = horizontalRow();
        restartButton = button("↺ Od początku");
        likeButton = button("♡ Lubię to");
        actionRow.addView(restartButton, weighted());
        actionRow.addView(likeButton, weighted());
        root.addView(actionRow, rowParams());

        LinearLayout modesRow = horizontalRow();
        shuffleButton = button("🔀 Losowo");
        repeatButton = button("🔁 Powtarzanie");
        modesRow.addView(shuffleButton, weighted());
        modesRow.addView(repeatButton, weighted());
        root.addView(modesRow, rowParams());

        likedMusicButton = button("♥ Odtwórz polubioną muzykę");
        LinearLayout.LayoutParams likedParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        likedParams.setMargins(0, dp(12), 0, 0);
        root.addView(likedMusicButton, likedParams);

        setContentView(scroll);
        setControlsEnabled(false);
    }

    private void wireControls() {
        previousButton.setOnClickListener(view -> {
            MediaControllerCompat.TransportControls controls = controls();
            if (controls != null) {
                controls.skipToPrevious();
            }
        });
        playPauseButton.setOnClickListener(view -> {
            PlaybackStateCompat state =
                    mediaController == null ? null : mediaController.getPlaybackState();
            MediaControllerCompat.TransportControls controls = controls();
            if (controls == null) {
                return;
            }
            if (isPlaying(state)) {
                controls.pause();
            } else {
                controls.play();
            }
        });
        nextButton.setOnClickListener(view -> {
            MediaControllerCompat.TransportControls controls = controls();
            if (controls != null) {
                controls.skipToNext();
            }
        });
        restartButton.setOnClickListener(view -> {
            MediaControllerCompat.TransportControls controls = controls();
            if (controls != null) {
                controls.sendCustomAction(ACTION_RESTART, null);
            }
        });
        likeButton.setOnClickListener(view -> {
            boolean target = !liked;
            setLikedUi(target);
            MediaControllerCompat.TransportControls controls = controls();
            if (controls != null) {
                controls.sendCustomAction(
                        target ? ACTION_LIKE : ACTION_UNLIKE, null);
            }
        });
        shuffleButton.setOnClickListener(view -> {
            MediaControllerCompat.TransportControls controls = controls();
            if (controls != null) {
                controls.sendCustomAction(ACTION_TOGGLE_SHUFFLE, null);
            }
        });
        repeatButton.setOnClickListener(view -> {
            MediaControllerCompat.TransportControls controls = controls();
            if (controls != null) {
                controls.sendCustomAction(ACTION_CYCLE_REPEAT, null);
            }
        });
        likedMusicButton.setOnClickListener(view -> {
            MediaControllerCompat.TransportControls controls = controls();
            if (controls != null) {
                controls.sendCustomAction(ACTION_PLAY_LIKED, null);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(
                    SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && durationMs > 0L) {
                    long position = durationMs * progress / 1000L;
                    positionView.setText(
                            formatTime(position) + " / " + formatTime(durationMs));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userSeeking = false;
                if (durationMs > 0L) {
                    MediaControllerCompat.TransportControls controls = controls();
                    if (controls != null) {
                        controls.seekTo(
                                durationMs * seekBar.getProgress() / 1000L);
                    }
                }
            }
        });
    }

    private MediaControllerCompat.TransportControls controls() {
        return mediaController == null
                ? null : mediaController.getTransportControls();
    }

    private void updateMetadata(MediaMetadataCompat metadata) {
        if (metadata == null) {
            titleView.setText("Brak aktywnego utworu");
            artistView.setText("");
            durationMs = 0L;
            loadArtwork(null);
            return;
        }

        MediaDescriptionCompat description = metadata.getDescription();
        CharSequence title = description == null ? null : description.getTitle();
        CharSequence subtitle = description == null ? null : description.getSubtitle();
        titleView.setText(title == null ? "Nieznany utwór" : title);
        artistView.setText(subtitle == null ? "" : subtitle);
        durationMs = Math.max(
                0L, metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));

        Uri artworkUri = description == null ? null : description.getIconUri();
        if (artworkUri == null) {
            String raw = metadata.getString(
                    MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI);
            if (raw != null && !raw.trim().isEmpty()) {
                artworkUri = Uri.parse(raw);
            }
        }
        loadArtwork(artworkUri == null ? null : artworkUri.toString());
    }

    private void updatePlaybackState(PlaybackStateCompat state) {
        boolean playing = isPlaying(state);
        playPauseButton.setText(playing ? "⏸" : "▶");
        stateView.setText(stateLabel(state));
        updateLikeFromActions(state);
        updateModeLabels();
        updatePositionOnly();
    }

    private void updateLikeFromActions(PlaybackStateCompat state) {
        if (state == null) {
            return;
        }
        List<PlaybackStateCompat.CustomAction> actions = state.getCustomActions();
        if (actions == null) {
            return;
        }
        for (PlaybackStateCompat.CustomAction action : actions) {
            if (ACTION_UNLIKE.equals(action.getAction())) {
                setLikedUi(true);
                return;
            }
            if (ACTION_LIKE.equals(action.getAction())) {
                setLikedUi(false);
                return;
            }
        }
    }

    private void updateModeLabels() {
        if (mediaController == null) {
            return;
        }
        int shuffle = mediaController.getShuffleMode();
        int repeat = mediaController.getRepeatMode();
        shuffleButton.setText(
                shuffle == PlaybackStateCompat.SHUFFLE_MODE_ALL
                        ? "🔀 Losowo: włączone" : "🔀 Losowo: wyłączone");
        if (repeat == PlaybackStateCompat.REPEAT_MODE_ONE) {
            repeatButton.setText("🔂 Powtarzaj jeden");
        } else if (repeat == PlaybackStateCompat.REPEAT_MODE_ALL) {
            repeatButton.setText("🔁 Powtarzaj wszystko");
        } else {
            repeatButton.setText("➡ Bez powtarzania");
        }
    }

    private void updatePositionOnly() {
        if (mediaController == null || userSeeking) {
            return;
        }
        PlaybackStateCompat state = mediaController.getPlaybackState();
        long position = estimatedPosition(state);
        position = Math.max(0L, durationMs > 0L
                ? Math.min(position, durationMs) : position);
        if (durationMs > 0L) {
            seekBar.setProgress((int) Math.min(
                    1000L, position * 1000L / durationMs));
        } else {
            seekBar.setProgress(0);
        }
        positionView.setText(
                formatTime(position) + " / " + formatTime(durationMs));
    }

    private long estimatedPosition(PlaybackStateCompat state) {
        if (state == null) {
            return 0L;
        }
        long position = Math.max(0L, state.getPosition());
        if (state.getState() == PlaybackStateCompat.STATE_PLAYING
                && state.getLastPositionUpdateTime() > 0L) {
            long elapsed = SystemClock.elapsedRealtime()
                    - state.getLastPositionUpdateTime();
            position += (long) (elapsed * state.getPlaybackSpeed());
        }
        return position;
    }

    private boolean isPlaying(PlaybackStateCompat state) {
        return state != null
                && (state.getState() == PlaybackStateCompat.STATE_PLAYING
                || state.getState() == PlaybackStateCompat.STATE_BUFFERING);
    }

    private String stateLabel(PlaybackStateCompat state) {
        if (state == null) {
            return "Brak stanu odtwarzania";
        }
        switch (state.getState()) {
            case PlaybackStateCompat.STATE_PLAYING:
                return "Odtwarzanie";
            case PlaybackStateCompat.STATE_PAUSED:
                return "Pauza";
            case PlaybackStateCompat.STATE_BUFFERING:
                return "Buforowanie…";
            case PlaybackStateCompat.STATE_ERROR:
                return state.getErrorMessage() == null
                        ? "Błąd odtwarzania"
                        : "Błąd: " + state.getErrorMessage();
            case PlaybackStateCompat.STATE_STOPPED:
                return "Zatrzymano";
            default:
                return "Gotowe";
        }
    }

    private void setLikedUi(boolean value) {
        liked = value;
        likeButton.setText(value ? "♥ Polubione" : "♡ Lubię to");
    }

    private void loadArtwork(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            loadedArtworkUri = null;
            artworkView.setImageResource(android.R.drawable.ic_media_play);
            return;
        }
        if (uri.equals(loadedArtworkUri)) {
            return;
        }
        loadedArtworkUri = uri;
        artworkExecutor.execute(() -> {
            Bitmap bitmap = null;
            try (InputStream input = new URL(uri).openStream()) {
                bitmap = BitmapFactory.decodeStream(input);
            } catch (Throwable error) {
                MobileDiagnostics.warn("P13-AA-MobilePlayer",
                        "artwork load failed: " + error.getMessage());
            }
            Bitmap ready = bitmap;
            mainHandler.post(() -> {
                if (uri.equals(loadedArtworkUri)) {
                    if (ready != null) {
                        artworkView.setImageBitmap(ready);
                    } else {
                        artworkView.setImageResource(
                                android.R.drawable.ic_media_play);
                    }
                }
            });
        });
    }

    private void setControlsEnabled(boolean enabled) {
        for (View view : new View[] {
                previousButton, playPauseButton, nextButton,
                restartButton, likeButton, shuffleButton,
                repeatButton, likedMusicButton, seekBar
        }) {
            view.setEnabled(enabled);
        }
    }

    private void showConnectionError(Throwable error) {
        connectionProgress.setVisibility(View.GONE);
        stateView.setText("Błąd połączenia z odtwarzaczem");
        setControlsEnabled(false);
        MobileDiagnostics.error(
                "P13-AA-MobilePlayer", "controller connection failed", error);
    }

    private TextView text(String value, int sizeSp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(Color.WHITE);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        view.setPadding(dp(4), dp(4), dp(4), dp(4));
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(15);
        return button;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, dp(54), 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(6), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String formatTime(long valueMs) {
        long totalSeconds = Math.max(0L, valueMs) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(
                    Locale.getDefault(), "%d:%02d:%02d",
                    hours, minutes, seconds);
        }
        return String.format(
                Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

}
