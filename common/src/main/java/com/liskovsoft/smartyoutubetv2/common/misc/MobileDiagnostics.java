package com.liskovsoft.smartyoutubetv2.common.misc;

import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Central Logcat helper for the mobile flavor plus a small process-local diagnostic ring buffer. */
public final class MobileDiagnostics {
    public interface SessionSink {
        void onDiagnosticLine(String line);
    }

    public static final String ROOT_TAG = "STMobile";
    private static final int MAX_RECENT_EVENTS = 200;
    private static final int MAX_SESSION_MESSAGE = 16_384;
    private static final ArrayDeque<String> RECENT_EVENTS = new ArrayDeque<>();
    private static volatile boolean captureEnabled = true;
    private static volatile SessionSink sessionSink;

    private MobileDiagnostics() {
    }

    public static void setCaptureEnabled(boolean enabled) {
        captureEnabled = enabled;
    }

    /** Installs a temporary full-session sink. Passing null detaches it. */
    public static void setSessionSink(SessionSink sink) {
        sessionSink = sink;
    }

    public static boolean isSessionCaptureActive() {
        return sessionSink != null;
    }

    /** Writes only to the active session recorder, leaving normal diagnostics behavior unchanged. */
    public static void session(String area, String message) {
        SessionSink sink = sessionSink;
        if (sink == null) return;
        sink.onDiagnosticLine(formatSessionLine("S", area, message));
    }

    /** Session-only error with the complete Java stack trace. */
    public static void sessionError(String area, String message, Throwable error) {
        SessionSink sink = sessionSink;
        if (sink == null) return;
        String safe = message != null ? message : "<null>";
        String stack = error == null ? "" : "\n" + stackTrace(error);
        sink.onDiagnosticLine(formatSessionLine("E", area, safe + stack));
        Log.e(tag(area), safe, error);
    }

    public static void debug(String area, String message) {
        String safe = message != null ? message : "<null>";
        capture("D", area, safe);
        Log.d(tag(area), safe);
    }

    public static void info(String area, String message) {
        String safe = message != null ? message : "<null>";
        capture("I", area, safe);
        Log.i(tag(area), safe);
    }

    public static void warn(String area, String message) {
        String safe = message != null ? message : "<null>";
        capture("W", area, safe);
        Log.w(tag(area), safe);
    }

    public static void error(String area, String message, Throwable error) {
        String safe = message != null ? message : "<null>";
        String suffix = error == null ? "" : " | " + compactThrowable(error);
        capture("E", area, safe + suffix);
        Log.e(tag(area), safe, error);
    }

    public static void lifecycle(Object owner, String event, String details) {
        String ownerName = owner != null ? owner.getClass().getSimpleName() : "unknown";
        debug("Lifecycle", ownerName + ": " + event + (details == null || details.isEmpty() ? "" : " | " + details));
    }

    /** Returns newest process events in chronological order. No logcat permission is required. */
    public static List<String> getRecentEvents(int maxItems) {
        int wanted = Math.max(0, maxItems);
        synchronized (RECENT_EVENTS) {
            ArrayList<String> all = new ArrayList<>(RECENT_EVENTS);
            if (wanted == 0 || all.size() <= wanted) return all;
            return new ArrayList<>(all.subList(all.size() - wanted, all.size()));
        }
    }

    public static void clearRecentEvents() {
        synchronized (RECENT_EVENTS) {
            RECENT_EVENTS.clear();
        }
    }

    private static void capture(String level, String area, String message) {
        SessionSink sink = sessionSink;
        if (!captureEnabled && sink == null) return;
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        String areaName = area == null ? "general" : area;

        if (captureEnabled) {
            String line = time + " " + level + "/" + areaName + " " + flatten(message);
            synchronized (RECENT_EVENTS) {
                RECENT_EVENTS.addLast(line);
                while (RECENT_EVENTS.size() > MAX_RECENT_EVENTS) RECENT_EVENTS.removeFirst();
            }
        }

        if (sink != null) {
            sink.onDiagnosticLine(time + " " + level + "/" + areaName + " " + flattenSession(message));
        }
    }

    private static String formatSessionLine(String level, String area, String message) {
        String time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        return time + " " + level + "/" + (area == null ? "general" : area) + " "
                + flattenSession(message);
    }

    private static String flatten(String value) {
        if (value == null) return "<null>";
        String result = value.replace('\n', ' ').replace('\r', ' ').trim();
        return result.length() > 500 ? result.substring(0, 500) + "…" : result;
    }

    private static String flattenSession(String value) {
        if (value == null) return "<null>";
        String result = value.replace('\r', ' ').trim();
        return result.length() > MAX_SESSION_MESSAGE
                ? result.substring(0, MAX_SESSION_MESSAGE) + "…<session message truncated>"
                : result;
    }

    private static String compactThrowable(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        return current.getClass().getSimpleName()
                + (message == null || message.trim().isEmpty() ? "" : ": " + flatten(message));
    }

    private static String stackTrace(Throwable error) {
        StringWriter out = new StringWriter(2048);
        PrintWriter writer = new PrintWriter(out);
        error.printStackTrace(writer);
        writer.flush();
        return out.toString();
    }

    private static String tag(String area) {
        if (area == null || area.trim().isEmpty()) {
            return ROOT_TAG;
        }
        String compact = area.replaceAll("[^A-Za-z0-9_]", "");
        if (compact.length() > 12) {
            compact = compact.substring(0, 12);
        }
        return ROOT_TAG + "/" + compact;
    }
}
