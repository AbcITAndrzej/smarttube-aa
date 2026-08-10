package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player;

import android.os.Handler;
import android.os.SystemClock;

import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileDiagnosticsStore;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;

/**
 * Mobile-only startup watchdog that complements SmartTube's existing ErrorFixerController.
 *
 * <p>The shared SmartTube recovery remains authoritative and gets the first attempt. For a 403 it
 * invalidates the player response/client and schedules a quick reload. This controller only adds a
 * delayed safety net if the mobile player is still not READY. It never runs for Radio or Android
 * Auto and it never changes global YouTube/AA preferences.</p>
 */
public final class MobileInstantPlayController {
    public interface Callback {
        /** True once the currently prepared media reached ExoPlayer READY. */
        boolean isReady();
        /** Request another quick reload after the shared 403 refresh has already run. */
        void reloadAfterForbidden();
        /** Request a normal metadata/format reload for a startup that is stuck without an error. */
        void reloadForStartupWatchdog();
        /** Surface a retryable timeout only after the full startup recovery budget is exhausted. */
        void onStartupTimeout();
    }

    // The common ErrorFixerController retries a forbidden stream after 250 ms. These are deliberately
    // later fallback checks so the mobile layer does not race the primary recovery path.
    private static final long FORBIDDEN_FALLBACK_1_MS = 1_250L;
    private static final long FORBIDDEN_FALLBACK_2_MS = 3_250L;
    private static final long STARTUP_WATCHDOG_MS = 8_000L;
    private static final long STARTUP_TIMEOUT_MS = 22_000L;

    private final Handler handler;
    private final MobileInstantPlayPreferences preferences;
    private final MobileFeatureFlags flags;
    private final MobileDiagnosticsStore diagnostics;
    private final Callback callback;

    private long session;
    private String mediaId = "";
    private boolean active;
    private boolean ready;
    private int forbiddenFallbacks;
    private int watchdogReloads;
    private long startedElapsedMs;

    public MobileInstantPlayController(Handler handler,
                                       MobileInstantPlayPreferences preferences,
                                       MobileFeatureFlags flags,
                                       MobileDiagnosticsStore diagnostics,
                                       Callback callback) {
        this.handler = handler;
        this.preferences = preferences;
        this.flags = flags;
        this.diagnostics = diagnostics;
        this.callback = callback;
    }

    /** Start a new mobile VOD/Shorts startup session. */
    public void begin(String id, boolean radio, boolean androidAuto) {
        cancelCallbacks();
        session++;
        mediaId = id == null ? "" : id;
        ready = false;
        forbiddenFallbacks = 0;
        watchdogReloads = 0;
        startedElapsedMs = SystemClock.elapsedRealtime();
        active = !radio && !androidAuto
                && preferences.isEnabled()
                && flags.isInstantPlayEnabled();

        diagnostics.onInstantPlayBegin(active, mediaId);
        if (!active) return;

        final long token = session;
        if (preferences.isStartupWatchdogEnabled() && flags.isInstantPlayStartupWatchdogEnabled()) {
            handler.postDelayed(() -> runStartupWatchdog(token), STARTUP_WATCHDOG_MS);
            handler.postDelayed(() -> runStartupTimeout(token), STARTUP_TIMEOUT_MS);
        }
    }

    /** Called for a recoverable signed-stream 403 after the common recovery was already triggered. */
    public void onTransient403() {
        if (!active || ready
                || !preferences.isForbiddenRecoveryEnabled()
                || !flags.isInstantPlayForbiddenRecoveryEnabled()) {
            return;
        }
        final long token = session;
        handler.postDelayed(() -> runForbiddenFallback(token, 1), FORBIDDEN_FALLBACK_1_MS);
        handler.postDelayed(() -> runForbiddenFallback(token, 2), FORBIDDEN_FALLBACK_2_MS);
    }

    public void onReady() {
        if (!active) return;
        ready = true;
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - startedElapsedMs);
        diagnostics.onInstantPlayReady(elapsed, forbiddenFallbacks, watchdogReloads);
        cancelCallbacks();
    }

    public void cancel() {
        active = false;
        ready = false;
        session++;
        cancelCallbacks();
    }

    private void runForbiddenFallback(long token, int attempt) {
        if (!isCurrent(token) || callback.isReady()) {
            if (callback.isReady()) onReady();
            return;
        }
        // If the first delayed fallback already recovered playback, the second runnable exits above.
        // Otherwise allow at most two explicit mobile fallbacks per prepared item.
        if (attempt <= forbiddenFallbacks) return;
        forbiddenFallbacks = attempt;
        MobileDiagnostics.warn("P18-InstantPlay", "403 fallback retry=" + attempt
                + " media=" + shortId(mediaId));
        diagnostics.onInstantPlayForbiddenFallback(attempt);
        callback.reloadAfterForbidden();
    }

    private void runStartupWatchdog(long token) {
        if (!isCurrent(token) || callback.isReady()) {
            if (callback.isReady()) onReady();
            return;
        }
        // One general reload is enough. Repeated blind reloads can make a slow connection worse.
        if (watchdogReloads > 0) return;
        watchdogReloads++;
        MobileDiagnostics.warn("P18-InstantPlay", "startup watchdog reload media="
                + shortId(mediaId));
        diagnostics.onInstantPlayWatchdogReload();
        callback.reloadForStartupWatchdog();
    }

    private void runStartupTimeout(long token) {
        if (!isCurrent(token) || callback.isReady()) {
            if (callback.isReady()) onReady();
            return;
        }
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - startedElapsedMs);
        MobileDiagnostics.warn("P18-InstantPlay", "startup timeout after=" + elapsed
                + "ms media=" + shortId(mediaId));
        diagnostics.onInstantPlayTimeout(elapsed);
        // Do not release the engine. The common SmartTube pipeline may still recover later; this
        // simply stops showing an endless silent spinner and gives the user a retryable error.
        callback.onStartupTimeout();
    }

    private boolean isCurrent(long token) {
        return active && !ready && token == session;
    }

    private void cancelCallbacks() {
        // All runnables are lambdas, so removeCallbacksAndMessages is the reliable way to invalidate
        // this controller's delayed work. The repository owns a dedicated main Handler for it.
        handler.removeCallbacksAndMessages(null);
    }

    private static String shortId(String value) {
        if (value == null) return "";
        return value.length() <= 16 ? value : value.substring(0, 16) + "…";
    }
}
