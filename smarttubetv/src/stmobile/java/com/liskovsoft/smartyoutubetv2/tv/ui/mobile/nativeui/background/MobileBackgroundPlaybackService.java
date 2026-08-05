package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.background;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
    private static volatile WeakReference<MobileBackgroundPlaybackService> sRunning =
            new WeakReference<>(null);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
            Intent intent = new Intent(context, MobileBackgroundPlaybackService.class)
                    .setAction(MobileMediaSessionManager.ACTION_REFRESH);
            try {
                ContextCompat.startForegroundService(context, intent);
            } catch (RuntimeException error) {
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
            } catch (RuntimeException error) {
                MobileDiagnostics.error("MediaService", "startForeground failed", error);
                stopForegroundAndSelf(false);
            }
        } else {
            stopForegroundAndSelf(false);
        }
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
        stopSelf();
    }
}
