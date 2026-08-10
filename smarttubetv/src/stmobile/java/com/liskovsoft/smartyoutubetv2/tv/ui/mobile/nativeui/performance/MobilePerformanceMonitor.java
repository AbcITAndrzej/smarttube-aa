package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.performance;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import android.os.Trace;
import android.view.Choreographer;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;

import java.util.Locale;

/**
 * Stage 12 local-only performance sampler for the mobile UI.
 *
 * <p>The monitor intentionally avoids analytics and file/network output. It keeps a small set of
 * process-local counters that are rendered only in the user-visible Diagnostics screen. Frame
 * sampling can be disabled independently because even a lightweight Choreographer callback should
 * never be mandatory for normal playback.</p>
 */
public final class MobilePerformanceMonitor {
    private static volatile MobilePerformanceMonitor instance;

    private final Context app;
    private final MobileFeatureFlags flags;
    private final Choreographer.FrameCallback frameCallback = this::onFrame;

    private boolean frameCallbackPosted;
    private long activityCreateElapsedMs = -1L;
    private long firstFrameElapsedMs = -1L;
    private long firstHomeContentElapsedMs = -1L;
    private boolean fullyDrawnReported;
    private long lastFrameNanos;
    private long sampledFrames;
    private long slow24Frames;
    private long slow50Frames;
    private long slow100Frames;
    private double worstFrameMs;
    private long lastBrowseRenderMs = -1L;
    private long worstBrowseRenderMs = -1L;
    private int lastBrowseItems;
    private String lastBrowseSurface = "none";
    private long lastPlaybackRenderMs = -1L;
    private long worstPlaybackRenderMs = -1L;
    private int trimMemoryLevel = -1;

    private MobilePerformanceMonitor(Context context) {
        app = context.getApplicationContext();
        flags = new MobileFeatureFlags(app);
    }

    public static MobilePerformanceMonitor get(Context context) {
        MobilePerformanceMonitor current = instance;
        if (current == null) {
            synchronized (MobilePerformanceMonitor.class) {
                current = instance;
                if (current == null) {
                    current = new MobilePerformanceMonitor(context);
                    instance = current;
                }
            }
        }
        return current;
    }

    public void onActivityCreated() {
        if (!flags.isPerformanceMonitoringEnabled()) return;
        if (activityCreateElapsedMs < 0L) activityCreateElapsedMs = SystemClock.elapsedRealtime();
    }

    public void onActivityResumed() {
        if (!flags.isPerformanceMonitoringEnabled() || !flags.isPerformanceFrameSamplingEnabled()) return;
        if (frameCallbackPosted) return;
        frameCallbackPosted = true;
        lastFrameNanos = 0L;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    public void onActivityPaused() {
        if (!frameCallbackPosted) return;
        frameCallbackPosted = false;
        lastFrameNanos = 0L;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
    }

    public void onTrimMemory(int level) {
        if (!flags.isPerformanceMonitoringEnabled()) return;
        trimMemoryLevel = level;
    }

    public long beginTrace(String section) {
        if (!flags.isPerformanceMonitoringEnabled()) return -1L;
        Trace.beginSection(section == null ? "ST:work" : section);
        return SystemClock.elapsedRealtimeNanos();
    }

    public void endBrowseTrace(long startedNanos, String surface, int itemCount) {
        if (startedNanos < 0L) return;
        long elapsed = elapsedMs(startedNanos);
        Trace.endSection();
        lastBrowseRenderMs = elapsed;
        if (elapsed > worstBrowseRenderMs) worstBrowseRenderMs = elapsed;
        lastBrowseItems = Math.max(0, itemCount);
        lastBrowseSurface = surface == null || surface.isEmpty() ? "unknown" : surface;
    }

    public void endPlaybackTrace(long startedNanos) {
        if (startedNanos < 0L) return;
        long elapsed = elapsedMs(startedNanos);
        Trace.endSection();
        lastPlaybackRenderMs = elapsed;
        if (elapsed > worstPlaybackRenderMs) worstPlaybackRenderMs = elapsed;
    }

    /** Marks the first populated Home page and supplies Android's TTFD signal exactly once. */
    public void onHomeContentReady(Activity activity, int itemCount) {
        if (!flags.isPerformanceMonitoringEnabled() || itemCount <= 0) return;
        if (firstHomeContentElapsedMs < 0L) firstHomeContentElapsedMs = SystemClock.elapsedRealtime();
        if (!fullyDrawnReported && activity != null) {
            fullyDrawnReported = true;
            if (Build.VERSION.SDK_INT >= 19) activity.reportFullyDrawn();
        }
    }

    public void appendReport(StringBuilder out) {
        if (out == null) return;
        out.append("\n[Stage 12 performance]\n");
        out.append("monitor_enabled: ").append(flags.isPerformanceMonitoringEnabled()).append('\n');
        out.append("frame_sampling: ").append(flags.isPerformanceFrameSamplingEnabled()).append('\n');
        if (!flags.isPerformanceMonitoringEnabled()) return;

        long processStart = processStartElapsedMs();
        out.append("process_to_activity_create: ")
                .append(formatDelta(processStart, activityCreateElapsedMs)).append('\n');
        out.append("process_to_first_frame: ")
                .append(formatDelta(processStart, firstFrameElapsedMs)).append('\n');
        out.append("process_to_home_content_TTFD: ")
                .append(formatDelta(processStart, firstHomeContentElapsedMs)).append('\n');
        out.append("fully_drawn_reported: ").append(fullyDrawnReported).append('\n');
        out.append("browse_render_last: ").append(formatMs(lastBrowseRenderMs))
                .append(" worst=").append(formatMs(worstBrowseRenderMs))
                .append(" surface=").append(lastBrowseSurface)
                .append(" items=").append(lastBrowseItems).append('\n');
        out.append("playback_ui_render_last: ").append(formatMs(lastPlaybackRenderMs))
                .append(" worst=").append(formatMs(worstPlaybackRenderMs)).append('\n');
        out.append("frames_sampled: ").append(sampledFrames)
                .append(" slow>24ms=").append(slow24Frames)
                .append(" slow>50ms=").append(slow50Frames)
                .append(" slow>100ms=").append(slow100Frames)
                .append(" worst=").append(String.format(Locale.US, "%.1fms", worstFrameMs)).append('\n');
        out.append("last_trim_memory_level: ").append(trimMemoryLevel < 0 ? "none" : trimMemoryLevel).append('\n');
        appendMemory(out);
    }

    public void resetSessionMetrics() {
        firstFrameElapsedMs = -1L;
        firstHomeContentElapsedMs = -1L;
        fullyDrawnReported = false;
        lastFrameNanos = 0L;
        sampledFrames = 0L;
        slow24Frames = 0L;
        slow50Frames = 0L;
        slow100Frames = 0L;
        worstFrameMs = 0d;
        lastBrowseRenderMs = -1L;
        worstBrowseRenderMs = -1L;
        lastBrowseItems = 0;
        lastBrowseSurface = "none";
        lastPlaybackRenderMs = -1L;
        worstPlaybackRenderMs = -1L;
        trimMemoryLevel = -1;
    }

    private void onFrame(long frameTimeNanos) {
        if (!frameCallbackPosted || !flags.isPerformanceMonitoringEnabled()
                || !flags.isPerformanceFrameSamplingEnabled()) {
            frameCallbackPosted = false;
            lastFrameNanos = 0L;
            return;
        }
        if (firstFrameElapsedMs < 0L) firstFrameElapsedMs = SystemClock.elapsedRealtime();
        if (lastFrameNanos > 0L) {
            double deltaMs = (frameTimeNanos - lastFrameNanos) / 1_000_000d;
            // Ignore impossible gaps caused by sleep/background transitions; those are not UI jank.
            if (deltaMs > 0d && deltaMs < 1000d) {
                sampledFrames++;
                if (deltaMs > 24d) slow24Frames++;
                if (deltaMs > 50d) slow50Frames++;
                if (deltaMs > 100d) slow100Frames++;
                if (deltaMs > worstFrameMs) worstFrameMs = deltaMs;
            }
        }
        lastFrameNanos = frameTimeNanos;
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    private void appendMemory(StringBuilder out) {
        Runtime runtime = Runtime.getRuntime();
        long javaUsed = runtime.totalMemory() - runtime.freeMemory();
        out.append("java_heap: used=").append(formatBytes(javaUsed))
                .append(" total=").append(formatBytes(runtime.totalMemory()))
                .append(" max=").append(formatBytes(runtime.maxMemory())).append('\n');
        out.append("native_heap_allocated: ").append(formatBytes(Debug.getNativeHeapAllocatedSize())).append('\n');
        ActivityManager manager = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            out.append("device_memory: avail=").append(formatBytes(info.availMem))
                    .append(" threshold=").append(formatBytes(info.threshold))
                    .append(" lowMemory=").append(info.lowMemory).append('\n');
        }
    }

    private static long processStartElapsedMs() {
        return Build.VERSION.SDK_INT >= 24 ? Process.getStartElapsedRealtime() : -1L;
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0L, (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000L);
    }

    private static String formatDelta(long from, long to) {
        return from < 0L || to < 0L || to < from ? "n/a" : (to - from) + "ms";
    }

    private static String formatMs(long value) {
        return value < 0L ? "n/a" : value + "ms";
    }

    private static String formatBytes(long bytes) {
        double value = Math.max(0L, bytes);
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024d && unit < units.length - 1) {
            value /= 1024d;
            unit++;
        }
        return unit == 0 ? String.format(Locale.US, "%.0f%s", value, units[unit])
                : String.format(Locale.US, "%.1f%s", value, units[unit]);
    }
}
