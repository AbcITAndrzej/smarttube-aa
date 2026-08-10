package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.host.MobileNativeActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player.MobilePlayerPreferences;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Stage 8 foreground worker for explicit audio-only playlist downloads.
 *
 * <p>Only one transfer runs at a time. This intentionally trades throughput for fewer signed-URL
 * races, lower memory pressure and friendlier behavior on mobile networks. Signed stream URLs are
 * resolved just-in-time and are never persisted.</p>
 */
public final class OfflinePlaylistDownloadService extends Service {
    public static final String ACTION_WAKE = "app.smarttube.mobile.offline.WAKE";
    public static final String ACTION_PAUSE = "app.smarttube.mobile.offline.PAUSE";
    public static final String ACTION_RESUME = "app.smarttube.mobile.offline.RESUME";
    public static final String ACTION_CANCEL = "app.smarttube.mobile.offline.CANCEL";
    public static final String EXTRA_PLAYLIST_ID = "playlist_id";

    private static final String CHANNEL_ID = "smarttube_offline_downloads";
    private static final int NOTIFICATION_ID = 0x5318;
    private static final int MAX_URL_ATTEMPTS = 3;
    private static final long PROGRESS_UPDATE_MS = 700L;
    private static final long RETRY_BASE_MS = 900L;
    private static final String COORDINATOR_OWNER = "playlist";

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "SmartTube-OfflinePlaylist");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private volatile String activePlaylistId = "";
    private volatile String activeMediaId = "";
    private volatile Call activeCall;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    private OfflinePlaylistRepository playlists;
    private OfflineMediaRepository media;
    private OfflineMediaPreferences preferences;
    private MediaItemService mediaItemService;
    private MobilePlayerPreferences playerPreferences;

    @Override public void onCreate() {
        super.onCreate();
        playlists = OfflinePlaylistRepository.get(this);
        media = OfflineMediaRepository.get(this);
        preferences = media.getPreferences();
        playerPreferences = new MobilePlayerPreferences(this);
        mediaItemService = YouTubeServiceManager.instance().getMediaItemService();
        ensureNotificationChannel();
        registerNetworkCallback();
        MobileDiagnostics.info("OfflinePlaylist", "download service created");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Every public entry point uses startForegroundService(). Enter foreground immediately,
        // including Pause/Cancel actions fired from a notification while the app is backgrounded.
        startAsForeground(buildWaitingNotification());
        String action = intent == null ? ACTION_WAKE : intent.getAction();
        String playlistId = intent == null ? "" : safe(intent.getStringExtra(EXTRA_PLAYLIST_ID));
        if (ACTION_PAUSE.equals(action)) {
            if (!playlistId.isEmpty()) playlists.pause(playlistId);
            cancelActiveIfMatches(playlistId);
            scheduleDrain();
            return START_STICKY;
        }
        if (ACTION_CANCEL.equals(action)) {
            cancelActiveIfMatches(playlistId);
            if (!playlistId.isEmpty()) playlists.delete(playlistId, true);
            scheduleDrain();
            return START_STICKY;
        }
        if (ACTION_RESUME.equals(action) && !playlistId.isEmpty()) {
            playlists.resume(playlistId);
        }
        scheduleDrain();
        return START_STICKY;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        stopping.set(true);
        Call call = activeCall;
        if (call != null) call.cancel();
        unregisterNetworkCallback();
        worker.shutdownNow();
        MobileDiagnostics.info("OfflinePlaylist", "download service destroyed");
        super.onDestroy();
    }

    public static void wake(Context context) { start(context, ACTION_WAKE, ""); }
    public static void pause(Context context, String playlistId) { start(context, ACTION_PAUSE, playlistId); }
    public static void resume(Context context, String playlistId) { start(context, ACTION_RESUME, playlistId); }
    public static void cancel(Context context, String playlistId) { start(context, ACTION_CANCEL, playlistId); }

    private static void start(Context context, String action, String playlistId) {
        Intent intent = new Intent(context, OfflinePlaylistDownloadService.class).setAction(action);
        if (playlistId != null && !playlistId.isEmpty()) intent.putExtra(EXTRA_PLAYLIST_ID, playlistId);
        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (RuntimeException error) {
            MobileDiagnostics.error("OfflinePlaylist", "unable to start download service", error);
        }
    }

    private void scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try { drainQueue(); }
            finally {
                drainScheduled.set(false);
                if (!stopping.get() && playlists.nextRunnablePlaylist() != null
                        && networkAllowed()) scheduleDrain();
            }
        });
    }

    private void drainQueue() {
        while (!stopping.get()) {
            if (!playlists.isEnabled()) {
                stopForegroundAndSelf();
                return;
            }
            OfflinePlaylistRecord playlist = playlists.nextRunnablePlaylist();
            if (playlist == null) {
                stopForegroundAndSelf();
                return;
            }
            if (!networkAllowed()) {
                // Keep the foreground service alive while a queued playlist is waiting for Wi-Fi
                // or connectivity. The NetworkCallback below wakes the drain as soon as policy is met.
                MobileDiagnostics.info("OfflinePlaylist", "waiting for allowed network");
                notifyState(buildNetworkWaitingNotification(playlist));
                return;
            }
            activePlaylistId = playlist.getPlaylistId();
            OfflinePlaylistRecord current = playlists.find(activePlaylistId);
            if (current == null || current.getState() == OfflinePlaylistState.PAUSED) continue;
            playlists.markPlaylistDownloading(activePlaylistId);
            OfflinePlaylistEntry entry = playlists.nextPending(activePlaylistId);
            if (entry == null) {
                playlists.refresh(activePlaylistId);
                activePlaylistId = "";
                continue;
            }
            activeMediaId = entry.getMediaId();
            if (!OfflineDownloadCoordinator.tryAcquire(COORDINATOR_OWNER, entry.getMediaId())) {
                activeMediaId = "";
                try {
                    sleepQuiet(500L);
                } catch (PausedException ignored) {
                    return;
                }
                continue;
            }
            playlists.markEntryDownloading(entry);
            try {
                downloadEntry(playlist, entry);
            } catch (PausedException ignored) {
                // Pause/network loss/cancel may interrupt a transfer after the row was marked
                // DOWNLOADING. Put the item back in PENDING while preserving the playlist PAUSED
                // state when the user explicitly paused it. This also preserves the .part file.
                playlists.requeueEntry(entry, "interrupted; waiting to resume");
            } catch (Throwable error) {
                MobileDiagnostics.error("OfflinePlaylist", "entry failed " + entry.getMediaId(), error);
                media.markFailed(entry.getMediaId(), error.getMessage());
                playlists.markEntryFailed(entry, compact(error.getMessage()));
            } finally {
                activeCall = null;
                OfflineDownloadCoordinator.release(COORDINATOR_OWNER, entry.getMediaId());
                activeMediaId = "";
                playlists.refresh(activePlaylistId);
                activePlaylistId = "";
            }
        }
    }

    private void downloadEntry(OfflinePlaylistRecord playlist, OfflinePlaylistEntry entry)
            throws IOException, PausedException {
        OfflineMediaRecord existing = media.find(entry.getMediaId());
        if (existing != null && existing.isAvailable()
                && media.resolveAvailableFile(entry.getMediaId()) != null) {
            playlists.markEntryAvailable(entry, existing);
            return;
        }

        Throwable lastError = null;
        for (int attempt = 1; attempt <= MAX_URL_ATTEMPTS; attempt++) {
            checkCanContinue(entry.getPlaylistId());
            MediaItemFormatInfo info = mediaItemService.getFormatInfo(entry.getMediaId());
            if (info == null || info.isLive() || info.isLiveContent() || info.isUnplayable()) {
                throw new IOException("Media cannot be stored as finite offline audio");
            }
            MediaFormat format = OfflineAudioFormatSelector.select(info,
                    playerPreferences.getPreferredAudioLanguage());
            if (format == null) throw new IOException("No direct audio-only stream available");

            long expectedBytes = OfflineAudioFormatSelector.expectedBytes(format);
            String selectedCodec = OfflineAudioFormatSelector.codec(format);
            OfflineMediaRecord prior = media.find(entry.getMediaId());
            if (prior != null && prior.getBytesDownloaded() > 0L
                    && !samePartialFormat(prior, format.getMimeType(), selectedCodec, expectedBytes)) {
                // A refreshed player response may theoretically select a different itag/encoding.
                // Never append bytes from another representation to an old .part file. URLs remain
                // ephemeral; MIME/codec/finite length are sufficient guards for safe resume here.
                media.delete(entry.getMediaId());
                playlists.updateEntryProgress(entry, 0L, expectedBytes);
                MobileDiagnostics.info("OfflinePlaylist", "partial reset after format change media="
                        + entry.getMediaId());
            }
            OfflineMediaDescriptor descriptor = entry.toDescriptor(format.getMimeType(), selectedCodec);
            OfflineMediaRecord reserved = media.beginDownload(descriptor, expectedBytes);
            if (reserved.isAvailable()) {
                playlists.markEntryAvailable(entry, reserved);
                return;
            }
            long existingBytes = Math.max(0L, reserved.getBytesDownloaded());
            // Process death can happen after the last byte was flushed but before .part was
            // promoted to .audio. If the complete finite length is already present, finish the
            // atomic commit locally instead of issuing an invalid Range request at EOF.
            if (expectedBytes > 0L && existingBytes >= expectedBytes) {
                OfflineMediaRecord available = media.markAvailable(entry.getMediaId());
                playlists.markEntryAvailable(entry, available);
                return;
            }
            try {
                transfer(playlist, entry, format.getUrl(), existingBytes, expectedBytes);
                OfflineMediaRecord available = media.markAvailable(entry.getMediaId());
                playlists.markEntryAvailable(entry, available);
                MobileDiagnostics.info("OfflinePlaylist", "stored " + entry.getMediaId()
                        + " bytes=" + available.getBytesDownloaded());
                return;
            } catch (HttpStatusException status) {
                lastError = status;
                if ((status.code == 403 || status.code == 410) && attempt < MAX_URL_ATTEMPTS) {
                    MobileDiagnostics.info("OfflinePlaylist", "signed URL expired/status=" + status.code
                            + " retry=" + attempt + " media=" + entry.getMediaId());
                    sleepQuiet(RETRY_BASE_MS * attempt);
                    continue; // refetch fresh format info / signed URL
                }
                throw status;
            } catch (IOException error) {
                lastError = error;
                if (attempt < MAX_URL_ATTEMPTS) {
                    sleepQuiet(RETRY_BASE_MS * attempt);
                    continue;
                }
                throw error;
            }
        }
        if (lastError instanceof IOException) throw (IOException) lastError;
        throw new IOException("Offline audio download failed");
    }

    private void transfer(OfflinePlaylistRecord playlist, OfflinePlaylistEntry entry, String url,
                          long existingBytes, long expectedBytes) throws IOException, PausedException {
        if (url == null || url.trim().isEmpty()) throw new IOException("Empty audio URL");
        Request.Builder builder = new Request.Builder().url(url).get();
        if (existingBytes > 0L) builder.header("Range", "bytes=" + existingBytes + "-");
        Call call = OkHttpManager.instance().getClient().newCall(builder.build());
        activeCall = call;
        try (Response response = call.execute()) {
            int code = response.code();
            if (code != 200 && code != 206) throw new HttpStatusException(code);
            if (response.body() == null) throw new IOException("Empty audio response body");
            boolean append = existingBytes > 0L && code == 206;
            long downloaded = append ? existingBytes : 0L;
            long responseLength = response.body().contentLength();
            long total = expectedBytes > 0L ? expectedBytes
                    : responseLength > 0L ? downloaded + responseLength : 0L;
            // Never allow an unbounded stream to bypass the Stage 6 storage limit. Most direct
            // finite audio formats expose clen; when they do not, Content-Length from this GET is
            // the fallback. If both are unknown, fail safely instead of consuming arbitrary disk.
            if (total <= 0L) throw new IOException("Audio response size is unknown");
            media.ensureDownloadCapacity(entry.getMediaId(), total);
            try (InputStream input = response.body().byteStream();
                 OutputStream output = media.openPartialOutput(entry.getMediaId(), append)) {
                byte[] buffer = new byte[64 * 1024];
                long lastUpdate = 0L;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    checkCanContinue(entry.getPlaylistId());
                    output.write(buffer, 0, read);
                    downloaded += read;
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate >= PROGRESS_UPDATE_MS) {
                        lastUpdate = now;
                        media.updateProgress(entry.getMediaId(), downloaded, total);
                        playlists.updateEntryProgress(entry, downloaded, total);
                        notifyState(buildProgressNotification(playlist, entry, downloaded, total));
                    }
                }
                output.flush();
                media.updateProgress(entry.getMediaId(), downloaded, total);
                playlists.updateEntryProgress(entry, downloaded, total);
            }
        } finally {
            activeCall = null;
        }
    }

    private void checkCanContinue(String playlistId) throws PausedException {
        if (stopping.get() || Thread.currentThread().isInterrupted() || !playlists.isEnabled()
                || !playlists.isPlaylistAllowed(playlistId)) {
            throw new PausedException();
        }
        OfflinePlaylistRecord playlist = playlists.find(playlistId);
        if (playlist == null || playlist.getState() == OfflinePlaylistState.PAUSED) throw new PausedException();
        if (!networkAllowed()) throw new PausedException();
    }

    private boolean networkAllowed() {
        OfflinePlaylistRecord record = activePlaylistId == null || activePlaylistId.isEmpty()
                ? playlists.nextRunnablePlaylist() : playlists.find(activePlaylistId);
        if (record == null) return false;
        return OfflineNetworkPolicy.isAllowed(this, playlists.isWifiOnly(record));
    }

    private void cancelActiveIfMatches(String playlistId) {
        if (playlistId == null || playlistId.isEmpty() || playlistId.equals(activePlaylistId)) {
            Call call = activeCall;
            if (call != null) call.cancel();
        }
    }

    private Notification buildProgressNotification(OfflinePlaylistRecord playlist,
                                                   OfflinePlaylistEntry entry,
                                                   long downloaded, long total) {
        int progress = total > 0L ? (int) Math.min(100L, downloaded * 100L / total) : 0;
        NotificationCompat.Builder b = baseNotification()
                .setContentTitle(playlist.getTitle().isEmpty()
                        ? getString(R.string.mobile_offline_playlist_downloading) : playlist.getTitle())
                .setContentText(getString(R.string.mobile_offline_playlist_item_progress,
                        entry.getPosition() + 1, Math.max(1, playlist.getTotalCount()), entry.getTitle()))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, total <= 0L);
        b.addAction(R.drawable.mobile_ic_pause, getString(R.string.mobile_offline_playlist_pause),
                servicePendingIntent(ACTION_PAUSE, playlist.getPlaylistId(), 10));
        return b.build();
    }

    private Notification buildWaitingNotification() {
        return baseNotification()
                .setContentTitle(getString(R.string.mobile_offline_playlist_service_title))
                .setContentText(getString(R.string.mobile_offline_playlist_preparing))
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build();
    }

    private Notification buildNetworkWaitingNotification(OfflinePlaylistRecord playlist) {
        return baseNotification()
                .setContentTitle(playlist == null ? getString(R.string.mobile_offline_playlist_service_title)
                        : playlist.getTitle())
                .setContentText(preferences.isPlaylistWifiOnly()
                        ? getString(R.string.mobile_offline_playlist_waiting_wifi)
                        : getString(R.string.mobile_offline_playlist_waiting_network))
                .setOngoing(true)
                .addAction(R.drawable.mobile_ic_pause, getString(R.string.mobile_offline_playlist_pause),
                        servicePendingIntent(ACTION_PAUSE,
                                playlist == null ? "" : playlist.getPlaylistId(), 12))
                .build();
    }

    private NotificationCompat.Builder baseNotification() {
        Intent open = new Intent(this, MobileNativeActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 77, open,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.mobile_ic_notification)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setShowWhen(false);
    }

    private PendingIntent servicePendingIntent(String action, String playlistId, int requestCode) {
        Intent intent = new Intent(this, OfflinePlaylistDownloadService.class).setAction(action)
                .putExtra(EXTRA_PLAYLIST_ID, playlistId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag();
        return Build.VERSION.SDK_INT >= 26
                ? PendingIntent.getForegroundService(this, requestCode + playlistId.hashCode(), intent, flags)
                : PendingIntent.getService(this, requestCode + playlistId.hashCode(), intent, flags);
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private void registerNetworkCallback() {
        if (Build.VERSION.SDK_INT < 21) return;
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { scheduleDrain(); }

            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                if (networkAllowed()) scheduleDrain();
                else cancelActiveForNetworkPolicy();
            }

            @Override public void onLost(Network network) {
                if (!networkAllowed()) cancelActiveForNetworkPolicy();
            }
        };
        try {
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(request, networkCallback);
        } catch (RuntimeException error) {
            MobileDiagnostics.error("OfflinePlaylist", "network callback unavailable", error);
            networkCallback = null;
        }
    }

    private void cancelActiveForNetworkPolicy() {
        Call call = activeCall;
        if (call != null) call.cancel();
    }

    private void unregisterNetworkCallback() {
        ConnectivityManager manager = connectivityManager;
        ConnectivityManager.NetworkCallback callback = networkCallback;
        connectivityManager = null;
        networkCallback = null;
        if (manager == null || callback == null || Build.VERSION.SDK_INT < 21) return;
        try { manager.unregisterNetworkCallback(callback); }
        catch (RuntimeException ignored) {}
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.mobile_offline_playlist_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.mobile_offline_playlist_notification_channel_summary));
        manager.createNotificationChannel(channel);
    }

    private void startAsForeground(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException error) {
            MobileDiagnostics.error("OfflinePlaylist", "startForeground failed", error);
        }
    }

    private void notifyState(Notification notification) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification);
    }

    private void stopForegroundAndSelf() {
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
            else {
                //noinspection deprecation
                stopForeground(true);
            }
        } catch (RuntimeException ignored) {}
        stopSelf();
    }

    private static void sleepQuiet(long ms) throws PausedException {
        try { Thread.sleep(Math.max(0L, ms)); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new PausedException();
        }
    }

    private static boolean samePartialFormat(OfflineMediaRecord prior, String mimeType,
                                             String codec, long expectedBytes) {
        if (prior == null) return true;
        if (!safe(prior.getMimeType()).equals(safe(mimeType))) return false;
        if (!safe(prior.getCodec()).equals(safe(codec))) return false;
        return expectedBytes <= 0L || prior.getBytesTotal() <= 0L
                || expectedBytes == prior.getBytesTotal();
    }

    private static String compact(String value) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= 180 ? safe : safe.substring(0, 180);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static final class PausedException extends Exception {}
    private static final class HttpStatusException extends IOException {
        final int code;
        HttpStatusException(int code) { super(String.format(Locale.US, "HTTP %d", code)); this.code = code; }
    }
}
