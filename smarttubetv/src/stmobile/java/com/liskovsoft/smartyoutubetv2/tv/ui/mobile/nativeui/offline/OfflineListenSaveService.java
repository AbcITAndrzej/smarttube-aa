package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Stage 7 passive audio-only downloader. The player schedules a request only after the configured
 * amount of actual listening time. The service then resolves a fresh finite audio URL just-in-time,
 * writes into Stage 6's private .part store and atomically promotes it to .audio.
 *
 * <p>Important: signed URLs are never persisted. A single process-wide transfer coordinator is
 * shared with Stage 8 so passive caching never writes concurrently with an explicit playlist job.</p>
 */
public final class OfflineListenSaveService extends Service {
    public static final String ACTION_CAPTURE = "app.smarttube.mobile.offline.LISTEN_CAPTURE";
    public static final String ACTION_WAKE = "app.smarttube.mobile.offline.LISTEN_WAKE";
    public static final String ACTION_CANCEL_ACTIVE = "app.smarttube.mobile.offline.LISTEN_CANCEL";

    private static final String EXTRA_MEDIA_ID = "media_id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_AUTHOR = "author";
    private static final String EXTRA_THUMBNAIL = "thumbnail";
    private static final String EXTRA_DURATION = "duration";
    private static final String CHANNEL_ID = "smarttube_offline_listen_save";
    private static final int NOTIFICATION_ID = 0x5317;
    private static final int MAX_URL_ATTEMPTS = 3;
    private static final long RETRY_BASE_MS = 900L;
    private static final long PROGRESS_UPDATE_MS = 700L;
    private static final String COORDINATOR_OWNER = "listen-save";

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SmartTube-ListenSave");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();

    private OfflineListenSaveRepository repository;
    private OfflineMediaRepository media;
    private OfflineMediaPreferences preferences;
    private MediaItemService mediaItemService;
    private MobilePlayerPreferences playerPreferences;
    private volatile Call activeCall;
    private volatile String activeMediaId = "";
    private volatile boolean cancelActiveRequested;

    @Override public void onCreate() {
        super.onCreate();
        repository = OfflineListenSaveRepository.get(this);
        media = OfflineMediaRepository.get(this);
        preferences = media.getPreferences();
        mediaItemService = YouTubeServiceManager.instance().getMediaItemService();
        playerPreferences = new MobilePlayerPreferences(this);
        ensureNotificationChannel();
        MobileDiagnostics.info("OfflineListen", "listen-and-save service created");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground(buildWaitingNotification());
        String action = intent == null ? ACTION_WAKE : intent.getAction();
        if (ACTION_CANCEL_ACTIVE.equals(action)) {
            String requestedMediaId = intent == null ? "" : safe(intent.getStringExtra(EXTRA_MEDIA_ID));
            if (!requestedMediaId.isEmpty() && requestedMediaId.equals(activeMediaId)) {
                cancelActiveRequested = true;
                Call call = activeCall;
                if (call != null) call.cancel();
            } else if (!requestedMediaId.isEmpty()) {
                media.delete(requestedMediaId);
                repository.delete(requestedMediaId, false);
            }
            scheduleDrain();
            return START_NOT_STICKY;
        }
        if (ACTION_CAPTURE.equals(action) && intent != null) {
            OfflineMediaDescriptor descriptor = descriptorFrom(intent);
            if (descriptor != null && repository.isEnabled()) {
                repository.enqueue(descriptor);
            }
        }
        scheduleDrain();
        return START_NOT_STICKY;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        stopping.set(true);
        String interruptedMediaId = activeMediaId;
        Call call = activeCall;
        if (call != null) call.cancel();
        // stopService() can destroy this Service without recreating the process-scoped repository.
        // Put an interrupted row back into PENDING here as well as in the worker catch path so the
        // durable queue never gets stranded in DOWNLOADING until a process restart.
        if (!interruptedMediaId.isEmpty() && !cancelActiveRequested && repository != null) {
            repository.requeue(interruptedMediaId, "service stopped; waiting to resume");
        }
        if (!interruptedMediaId.isEmpty()) {
            OfflineDownloadCoordinator.release(COORDINATOR_OWNER, interruptedMediaId);
        }
        worker.shutdownNow();
        MobileDiagnostics.info("OfflineListen", "listen-and-save service destroyed");
        super.onDestroy();
    }

    public static void capture(Context context, OfflineMediaDescriptor descriptor) {
        if (context == null || descriptor == null || !descriptor.isValid()) return;
        Context app = context.getApplicationContext();
        // Persist the request before asking Android to start a foreground data-sync service.
        // On recent Android versions an app can be temporarily forbidden from starting another
        // foreground service while backgrounded. In that case start() logs the refusal, but the
        // durable PENDING row survives and a later allowed wake can continue the save.
        OfflineListenSaveRepository repository = OfflineListenSaveRepository.get(app);
        if (!repository.isEnabled()) return;
        repository.enqueue(descriptor);
        start(app, new Intent(app, OfflineListenSaveService.class).setAction(ACTION_WAKE));
    }

    public static void wake(Context context) {
        if (context == null) return;
        start(context, new Intent(context, OfflineListenSaveService.class).setAction(ACTION_WAKE));
    }

    public static void cancel(Context context, String mediaId) {
        if (context == null || mediaId == null || mediaId.trim().isEmpty()) return;
        start(context, new Intent(context, OfflineListenSaveService.class)
                .setAction(ACTION_CANCEL_ACTIVE)
                .putExtra(EXTRA_MEDIA_ID, mediaId));
    }

    private static void start(Context context, Intent intent) {
        try {
            ContextCompat.startForegroundService(context.getApplicationContext(), intent);
        } catch (RuntimeException error) {
            MobileDiagnostics.error("OfflineListen", "unable to start listen-and-save service", error);
        }
    }

    private void scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try { drain(); }
            finally { drainScheduled.set(false); }
        });
    }

    private void drain() {
        while (!stopping.get() && repository.isEnabled()) {
            if (!OfflineNetworkPolicy.isAllowed(this, preferences.isListenSaveWifiOnly())) {
                MobileDiagnostics.info("OfflineListen", "network policy blocks passive save");
                stopForegroundAndSelf();
                return;
            }
            OfflineListenSaveEntry entry = repository.nextPending();
            if (entry == null) {
                stopForegroundAndSelf();
                return;
            }
            if (!OfflineDownloadCoordinator.tryAcquire(COORDINATOR_OWNER, entry.getMediaId())) {
                // Explicit playlist work has priority simply because it already owns the single slot.
                // Keep the passive request durable and retry on the next playback/service wake.
                MobileDiagnostics.info("OfflineListen", "offline transfer busy owner="
                        + OfflineDownloadCoordinator.currentOwner());
                stopForegroundAndSelf();
                return;
            }
            activeMediaId = entry.getMediaId();
            cancelActiveRequested = false;
            repository.markDownloading(entry);
            try {
                download(entry);
                repository.markAvailable(entry.getMediaId());
                MobileDiagnostics.info("OfflineListen", "stored media=" + entry.getMediaId());
            } catch (InterruptedSave interrupted) {
                if (cancelActiveRequested) {
                    media.delete(entry.getMediaId());
                    repository.delete(entry.getMediaId(), false);
                    MobileDiagnostics.info("OfflineListen", "discard partial after media switch=" + entry.getMediaId());
                } else {
                    repository.requeue(entry.getMediaId(), "interrupted; waiting to resume");
                }
            } catch (Throwable error) {
                if (cancelActiveRequested) {
                    media.delete(entry.getMediaId());
                    repository.delete(entry.getMediaId(), false);
                    MobileDiagnostics.info("OfflineListen", "discard cancelled media=" + entry.getMediaId());
                } else {
                    MobileDiagnostics.error("OfflineListen", "passive save failed " + entry.getMediaId(), error);
                    media.markFailed(entry.getMediaId(), compact(error.getMessage()));
                    repository.markFailed(entry.getMediaId(), compact(error.getMessage()));
                }
            } finally {
                activeCall = null;
                cancelActiveRequested = false;
                OfflineDownloadCoordinator.release(COORDINATOR_OWNER, entry.getMediaId());
                activeMediaId = "";
            }
        }
        stopForegroundAndSelf();
    }

    private void download(OfflineListenSaveEntry entry) throws IOException, InterruptedSave {
        OfflineMediaRecord existing = media.find(entry.getMediaId());
        if (existing != null && existing.isAvailable()
                && media.resolveAvailableFile(entry.getMediaId()) != null) return;

        Throwable last = null;
        for (int attempt = 1; attempt <= MAX_URL_ATTEMPTS; attempt++) {
            checkCanContinue();
            MediaItemFormatInfo info = mediaItemService.getFormatInfo(entry.getMediaId());
            if (info == null || info.isLive() || info.isLiveContent() || info.isUnplayable()) {
                throw new IOException("Media cannot be stored as finite offline audio");
            }
            MediaFormat format = OfflineAudioFormatSelector.select(info,
                    playerPreferences.getPreferredAudioLanguage());
            if (format == null) throw new IOException("No direct audio-only stream available");

            long expectedBytes = OfflineAudioFormatSelector.expectedBytes(format);
            String codec = OfflineAudioFormatSelector.codec(format);
            OfflineMediaRecord prior = media.find(entry.getMediaId());
            if (prior != null && prior.getBytesDownloaded() > 0L
                    && !samePartialFormat(prior, format.getMimeType(), codec, expectedBytes)) {
                // Never append a refreshed/changed representation to old bytes.
                media.delete(entry.getMediaId());
            }
            OfflineMediaRecord reserved = media.beginDownload(
                    entry.toDescriptor(format.getMimeType(), codec), expectedBytes);
            if (reserved.isAvailable()) return;
            long existingBytes = Math.max(0L, reserved.getBytesDownloaded());
            if (expectedBytes > 0L && existingBytes >= expectedBytes) {
                media.markAvailable(entry.getMediaId());
                return;
            }
            try {
                transfer(entry, format.getUrl(), existingBytes, expectedBytes);
                media.markAvailable(entry.getMediaId());
                return;
            } catch (HttpStatusException status) {
                last = status;
                if ((status.code == 403 || status.code == 410) && attempt < MAX_URL_ATTEMPTS) {
                    MobileDiagnostics.info("OfflineListen", "refresh signed URL status=" + status.code
                            + " attempt=" + attempt + " media=" + entry.getMediaId());
                    sleepQuiet(RETRY_BASE_MS * attempt);
                    continue;
                }
                throw status;
            } catch (IOException error) {
                last = error;
                if (attempt < MAX_URL_ATTEMPTS) {
                    sleepQuiet(RETRY_BASE_MS * attempt);
                    continue;
                }
                throw error;
            }
        }
        if (last instanceof IOException) throw (IOException) last;
        throw new IOException("Listen-and-save download failed");
    }

    private void transfer(OfflineListenSaveEntry entry, String url, long existingBytes,
                          long expectedBytes) throws IOException, InterruptedSave {
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
            if (total <= 0L) throw new IOException("Audio response size is unknown");
            media.ensureDownloadCapacity(entry.getMediaId(), total);
            try (InputStream input = response.body().byteStream();
                 OutputStream output = media.openPartialOutput(entry.getMediaId(), append)) {
                byte[] buffer = new byte[64 * 1024];
                long lastUpdate = 0L;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) continue;
                    checkCanContinue();
                    output.write(buffer, 0, read);
                    downloaded += read;
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate >= PROGRESS_UPDATE_MS) {
                        lastUpdate = now;
                        media.updateProgress(entry.getMediaId(), downloaded, total);
                        repository.updateProgress(entry.getMediaId(), downloaded, total);
                        notifyState(buildProgressNotification(entry, downloaded, total));
                    }
                }
                output.flush();
                media.updateProgress(entry.getMediaId(), downloaded, total);
                repository.updateProgress(entry.getMediaId(), downloaded, total);
            }
        } finally {
            activeCall = null;
        }
    }

    private void checkCanContinue() throws InterruptedSave {
        if (stopping.get() || Thread.currentThread().isInterrupted() || !repository.isEnabled()) {
            throw new InterruptedSave();
        }
        if (!OfflineNetworkPolicy.isAllowed(this, preferences.isListenSaveWifiOnly())) {
            throw new InterruptedSave();
        }
    }

    private Notification buildWaitingNotification() {
        return baseNotification()
                .setContentTitle(getString(R.string.mobile_offline_listen_service_title))
                .setContentText(getString(R.string.mobile_offline_listen_preparing))
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build();
    }

    private Notification buildProgressNotification(OfflineListenSaveEntry entry,
                                                    long downloaded, long total) {
        int progress = total > 0L ? (int) Math.min(100L, downloaded * 100L / total) : 0;
        return baseNotification()
                .setContentTitle(getString(R.string.mobile_offline_listen_service_title))
                .setContentText(entry.getTitle().isEmpty() ? entry.getMediaId() : entry.getTitle())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, total <= 0L)
                .build();
    }

    private NotificationCompat.Builder baseNotification() {
        Intent open = new Intent(this, MobileNativeActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 78, open,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.mobile_ic_notification)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setShowWhen(false);
    }

    private void startAsForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void stopForegroundAndSelf() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    private void notifyState(Notification notification) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, notification);
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.mobile_offline_listen_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.mobile_offline_listen_notification_channel_summary));
        nm.createNotificationChannel(channel);
    }

    private static int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    private static OfflineMediaDescriptor descriptorFrom(Intent intent) {
        String mediaId = safe(intent.getStringExtra(EXTRA_MEDIA_ID));
        if (mediaId.isEmpty()) return null;
        return new OfflineMediaDescriptor(mediaId, safe(intent.getStringExtra(EXTRA_TITLE)),
                safe(intent.getStringExtra(EXTRA_AUTHOR)), safe(intent.getStringExtra(EXTRA_THUMBNAIL)),
                Math.max(0L, intent.getLongExtra(EXTRA_DURATION, 0L)), "", "");
    }

    private static boolean samePartialFormat(OfflineMediaRecord prior, String mime, String codec,
                                             long expectedBytes) {
        if (prior == null) return true;
        if (!safe(prior.getMimeType()).equalsIgnoreCase(safe(mime))) return false;
        if (!safe(prior.getCodec()).equalsIgnoreCase(safe(codec))) return false;
        long oldTotal = prior.getBytesTotal();
        return oldTotal <= 0L || expectedBytes <= 0L || oldTotal == expectedBytes;
    }

    private static String compact(String value) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= 220 ? safe : safe.substring(0, 220);
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static void sleepQuiet(long millis) {
        try { Thread.sleep(Math.max(0L, millis)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static final class HttpStatusException extends IOException {
        final int code;
        HttpStatusException(int code) { super("HTTP " + code); this.code = code; }
    }
    private static final class InterruptedSave extends Exception {}
}
