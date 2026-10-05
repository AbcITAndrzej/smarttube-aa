package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.background;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import java.lang.ref.WeakReference;

/**
 * Minimal foreground-service host. The player remains owned by the repository/session manager;
 * this service only gives active playback the lifecycle and notification required by Android.
 */
public final class MobileBackgroundPlaybackService extends Service {
    private static final String ACTION_SHUTDOWN =
            "app.smarttube.mobile.action.MEDIA_SERVICE_SHUTDOWN";
    private static final long BACKGROUND_START_RETRY_MS = 5_000L;
    private static final long LOCK_TIMEOUT_MS = 4L * 60L * 60L * 1000L;
    private static volatile WeakReference<MobileBackgroundPlaybackService> sRunning =
            new WeakReference<>(null);
    private static long nextBackgroundStartElapsed;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    @Override public void onCreate() {
        super.onCreate();
        sRunning = new WeakReference<>(this);
        MobileDiagnostics.info("MediaService", "background playback service created");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        MobileMediaSessionManager manager = MobileMediaSessionManager.getActive();
        if (ACTION_SHUTDOWN.equals(intent == null ? null : intent.getAction())) {
            stopForegroundAndSelf(true);
            return START_NOT_STICKY;
        }
        if (manager == null) {
            stopForegroundAndSelf(true);
            return START_NOT_STICKY;
        }
        String action = intent == null ? MobileMediaSessionManager.ACTION_REFRESH : intent.getAction();
        if (Intent.ACTION_MEDIA_BUTTON.equals(action)) manager.handleMediaButtonIntent(intent);
        else manager.handleServiceAction(action);
        synchronizeNow(manager);
        return START_NOT_STICKY;
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        releasePlaybackLocks();
        MobileBackgroundPlaybackService current = sRunning.get();
        if (current == this) sRunning = new WeakReference<>(null);
        MobileDiagnostics.info("MediaService", "background playback service destroyed");
        super.onDestroy();
    }

    static void synchronize(Context context) {
        MobileMediaSessionManager manager = MobileMediaSessionManager.getActive();
        MobileBackgroundPlaybackService service = sRunning.get();
        if (service != null) {
            service.mainHandler.post(() -> service.synchronizeNow(manager));
            return;
        }
        if (manager != null && manager.shouldRunForeground()) {
            long now = SystemClock.elapsedRealtime();
            if (now < nextBackgroundStartElapsed) return;
            Intent intent = new Intent(context, MobileBackgroundPlaybackService.class)
                    .setAction(MobileMediaSessionManager.ACTION_REFRESH);
            try {
                ContextCompat.startForegroundService(context, intent);
            } catch (RuntimeException error) {
                if (isBackgroundStartBlocked(error)) {
                    // The screen is off and this process is already backgrounded. Hammering
                    // startForegroundService keeps failing and wakes the CPU. Retry slowly.
                    nextBackgroundStartElapsed = now + BACKGROUND_START_RETRY_MS;
                }
                MobileDiagnostics.error("MediaService", "unable to start foreground service", error);
            }
        }
    }

    static void stop(Context context) {
        MobileBackgroundPlaybackService service = sRunning.get();
        if (service != null) {
            service.mainHandler.post(() -> service.stopForegroundAndSelf(true));
            return;
        }
        try {
            context.stopService(new Intent(context, MobileBackgroundPlaybackService.class));
        } catch (RuntimeException error) {
            MobileDiagnostics.error("MediaService", "unable to stop playback service", error);
        }
    }

    private void synchronizeNow(MobileMediaSessionManager manager) {
        if (manager == null) {
            stopForegroundAndSelf(true);
            return;
        }
        if (manager.shouldRunForeground()) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(MobileMediaSessionManager.NOTIFICATION_ID,
                            manager.buildNotification(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(MobileMediaSessionManager.NOTIFICATION_ID,
                            manager.buildNotification());
                }
                nextBackgroundStartElapsed = 0L;
                acquirePlaybackLocks();
            } catch (RuntimeException error) {
                MobileDiagnostics.error("MediaService", "startForeground failed", error);
                stopForegroundAndSelf(false);
            }
        } else {
            stopForegroundAndSelf(false);
        }
    }

    @SuppressWarnings("deprecation")
    private void acquirePlaybackLocks() {
        try {
            if (wakeLock == null) {
                PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
                if (power != null) {
                    wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                            "SmartTubeMobile:Playback");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(LOCK_TIMEOUT_MS);
            }
            if (wifiLock == null) {
                WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                if (wifi != null) {
                    // Same mode the bundled ExoPlayer uses. It keeps the Wi-Fi radio up after the
                    // screen turns off; a CPU-only wake lock does not.
                    wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                            "SmartTubeMobile:Playback");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
            }
        } catch (RuntimeException error) {
            MobileDiagnostics.error("MediaService", "playback lock acquire failed", error);
        }
    }

    private void releasePlaybackLocks() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (RuntimeException error) {
            MobileDiagnostics.error("MediaService", "wake lock release failed", error);
        }
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (RuntimeException error) {
            MobileDiagnostics.error("MediaService", "wifi lock release failed", error);
        }
    }

    private static boolean isBackgroundStartBlocked(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String name = current.getClass().getName();
            if (name.endsWith("ForegroundServiceStartNotAllowedException")) return true;
            Throwable cause = current.getCause();
            if (cause == current) break;
            current = cause;
        }
        return false;
    }

    private void stopForegroundAndSelf(boolean removeNotification) {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(removeNotification ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_DETACH);
            } else {
                //noinspection deprecation
                stopForeground(removeNotification);
            }
        } catch (RuntimeException error) {
            MobileDiagnostics.error("MediaService", "stopForeground failed", error);
        }
        releasePlaybackLocks();
        stopSelf();
    }
}
