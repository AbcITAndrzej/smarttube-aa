package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;

import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Explicit, user-controlled playback diagnostic recorder.
 *
 * <p>While active it writes the app's own logcat, full MobileDiagnostics events and periodic
 * MobileDiagnosticsStore snapshots to a file. Nothing is uploaded automatically.</p>
 */
public final class DiagnosticSessionLogger implements MobileDiagnostics.SessionSink {
    private static final long SNAPSHOT_INTERVAL_SECONDS = 5L;
    private static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_STORED_SESSIONS = 5;
    private static final int COPY_BUFFER_SIZE = 32 * 1024;
    private static final int FLUSH_EVERY_LINES = 25;

    private static volatile DiagnosticSessionLogger instance;

    private final Context app;
    private final Object writerLock = new Object();

    private volatile boolean recording;
    private volatile long startedElapsedMs;
    private volatile long bytesWritten;
    private volatile String lastError = "";
    private volatile File activeFile;
    private volatile File lastFile;
    private volatile java.lang.Process logcatProcess;
    private volatile Thread logcatThread;
    private volatile ScheduledExecutorService snapshotExecutor;

    private BufferedWriter writer;
    private int pendingLines;
    private long lastFlushElapsedMs;
    private boolean sizeLimitMarkerWritten;

    private DiagnosticSessionLogger(Context context) {
        app = context.getApplicationContext();
        lastFile = findNewestSession();
    }

    public static DiagnosticSessionLogger get(Context context) {
        DiagnosticSessionLogger current = instance;
        if (current == null) {
            synchronized (DiagnosticSessionLogger.class) {
                current = instance;
                if (current == null) {
                    current = new DiagnosticSessionLogger(context);
                    instance = current;
                }
            }
        }
        return current;
    }

    public synchronized File start() throws IOException {
        if (recording) return activeFile;

        File directory = getLogDirectory();
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Cannot create diagnostics directory: " + directory);
        }

        pruneOldSessions(directory);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        activeFile = new File(directory, "SmartTube-session-" + stamp + ".txt");
        writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(activeFile, false), StandardCharsets.UTF_8), 32 * 1024);
        pendingLines = 0;
        bytesWritten = 0L;
        sizeLimitMarkerWritten = false;
        lastError = "";
        startedElapsedMs = SystemClock.elapsedRealtime();
        lastFlushElapsedMs = startedElapsedMs;
        recording = true;

        writeHeader();
        MobileDiagnostics.setSessionSink(this);
        MobileDiagnostics.session("SessionLog", "START file=" + activeFile.getAbsolutePath());
        writeSnapshot("START");
        startSnapshotTimer();
        startLogcatReader();
        return activeFile;
    }

    public synchronized File stop() {
        if (!recording) return getLastFile();

        MobileDiagnostics.session("SessionLog", "STOP requested");
        writeSnapshot("STOP");
        recording = false;
        MobileDiagnostics.setSessionSink(null);

        ScheduledExecutorService executor = snapshotExecutor;
        snapshotExecutor = null;
        if (executor != null) executor.shutdownNow();

        java.lang.Process process = logcatProcess;
        logcatProcess = null;
        if (process != null) process.destroy();

        writeLine("===== SESSION END elapsedMs=" + getElapsedMsInternal() + " =====");
        synchronized (writerLock) {
            closeWriterLocked();
        }

        lastFile = activeFile;
        activeFile = null;
        logcatThread = null;
        pruneOldSessions(getLogDirectory());
        return lastFile;
    }

    public boolean isRecording() {
        return recording;
    }

    public long getElapsedMs() {
        return recording ? Math.max(0L, SystemClock.elapsedRealtime() - startedElapsedMs) : 0L;
    }

    public long getBytesWritten() {
        return bytesWritten;
    }

    public String getLastError() {
        return lastError;
    }

    public File getLastFile() {
        File file = lastFile;
        if (file != null && file.isFile()) return file;
        file = findNewestSession();
        lastFile = file;
        return file;
    }

    public void copyLastLog(OutputStream output) throws IOException {
        File file = getLastFile();
        if (file == null || !file.isFile()) throw new IOException("No diagnostic session file");
        if (output == null) throw new IOException("Destination stream is null");

        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        }
    }

    @Override public void onDiagnosticLine(String line) {
        if (!recording) return;
        writeLine("[ST-DIAG] " + (line == null ? "<null>" : line));
    }

    private void writeHeader() {
        writeLine("===== SMARTTUBE FULL DIAGNOSTIC SESSION =====");
        writeLine("started=" + new Date());
        writeLine("privacy=This file may contain video IDs, device details, server hosts and temporary signed media URLs/tokens. Review before sharing.");
        writeLine("package=" + app.getPackageName());

        try {
            PackageInfo info = app.getPackageManager().getPackageInfo(app.getPackageName(), 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : info.versionCode;
            writeLine("appVersion=" + info.versionName + " versionCode=" + versionCode);
        } catch (Throwable error) {
            writeLine("appVersion=<error: " + error + ">");
        }

        writeLine("android=" + Build.VERSION.RELEASE + " sdk=" + Build.VERSION.SDK_INT);
        writeLine("device=" + Build.MANUFACTURER + " " + Build.MODEL + " product=" + Build.PRODUCT);
        writeLine("fingerprint=" + Build.FINGERPRINT);
        writeLine("pid=" + Process.myPid() + " locale=" + Locale.getDefault());
        Runtime runtime = Runtime.getRuntime();
        writeLine("memoryBytes max=" + runtime.maxMemory() + " total=" + runtime.totalMemory()
                + " free=" + runtime.freeMemory());
        writeLine("network=" + describeNetwork());
        writeLine("logcat=attempt own-process VERBOSE/threadtime; structured snapshots every "
                + SNAPSHOT_INTERVAL_SECONDS + "s");
        writeLine("=============================================");
    }

    private void startSnapshotTimer() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ST-Diagnostic-Snapshot");
            thread.setDaemon(true);
            return thread;
        });
        snapshotExecutor = executor;
        executor.scheduleAtFixedRate(() -> {
            if (recording) writeSnapshot("PERIODIC");
        }, SNAPSHOT_INTERVAL_SECONDS, SNAPSHOT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void writeSnapshot(String reason) {
        try {
            String report = MobileDiagnosticsStore.get(app).buildReport(false);
            writeBlock("===== SNAPSHOT " + reason + " =====\n" + report
                    + "\nnetworkNow=" + describeNetwork() + "\n===== SNAPSHOT END =====");
        } catch (Throwable error) {
            recordInternalError("snapshot failed", error);
        }
    }

    private void startLogcatReader() {
        Thread thread = new Thread(this::readLogcatWithFallbacks, "ST-Diagnostic-Logcat");
        thread.setDaemon(true);
        logcatThread = thread;
        thread.start();
    }

    private void readLogcatWithFallbacks() {
        String[][] commands = new String[][]{
                {"logcat", "--pid=" + Process.myPid(), "-v", "threadtime", "-T", "1", "*:V"},
                {"logcat", "-v", "threadtime", "-T", "1", "*:V"},
                {"logcat", "-v", "threadtime", "*:V"}
        };

        for (String[] command : commands) {
            if (!recording) return;
            if (readLogcat(command)) return;
        }
        if (recording) writeLine("[LOGGER] logcat unavailable; structured diagnostics continue");
    }

    /** Returns true when the reader remained attached until the session ended. */
    private boolean readLogcat(String[] command) {
        writeLine("[LOGGER] starting: " + Arrays.toString(command));
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            logcatProcess = process;
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8), 16 * 1024)) {
                String line;
                while (recording && (line = input.readLine()) != null) {
                    writeLine("[LOGCAT] " + line);
                }
            }
            if (!recording) return true;
            int exit = process.waitFor();
            writeLine("[LOGGER] logcat command exited code=" + exit);
            return false;
        } catch (Throwable error) {
            if (recording) recordInternalError("logcat failed: " + Arrays.toString(command), error);
            return false;
        } finally {
            if (logcatProcess == process) logcatProcess = null;
            if (process != null) process.destroy();
        }
    }

    private String describeNetwork() {
        try {
            ConnectivityManager manager = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return "connectivity-manager-null";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network active = manager.getActiveNetwork();
                if (active == null) return "none";
                NetworkCapabilities caps = manager.getNetworkCapabilities(active);
                if (caps == null) return "active/no-capabilities";
                StringBuilder out = new StringBuilder();
                appendTransport(out, caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI), "WIFI");
                appendTransport(out, caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR), "CELLULAR");
                appendTransport(out, caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET), "ETHERNET");
                appendTransport(out, caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN), "VPN");
                out.append(" validated=").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                out.append(" internet=").append(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
                out.append(" metered=").append(manager.isActiveNetworkMetered());
                return out.toString().trim();
            }

            @SuppressWarnings("deprecation") NetworkInfo info = manager.getActiveNetworkInfo();
            if (info == null) return "none";
            return info.getTypeName() + " connected=" + info.isConnected()
                    + " metered=" + manager.isActiveNetworkMetered();
        } catch (Throwable error) {
            return "error:" + error.getClass().getSimpleName() + ":" + error.getMessage();
        }
    }

    private static void appendTransport(StringBuilder out, boolean present, String name) {
        if (!present) return;
        if (out.length() > 0) out.append('+');
        out.append(name);
    }

    private void writeBlock(String block) {
        if (block == null) return;
        String[] lines = block.replace("\r", "").split("\n", -1);
        for (String line : lines) writeLine(line);
    }

    private void writeLine(String line) {
        String value = (line == null ? "<null>" : line) + '\n';
        synchronized (writerLock) {
            if (writer == null) return;
            if (bytesWritten >= MAX_FILE_BYTES) {
                if (!sizeLimitMarkerWritten) {
                    sizeLimitMarkerWritten = true;
                    lastError = "Diagnostic log reached 64 MiB limit";
                    try {
                        String marker = "[LOGGER] FILE SIZE LIMIT REACHED - further lines suppressed\n";
                        writer.write(marker);
                        writer.flush();
                    } catch (IOException ignored) {
                    }
                }
                return;
            }

            try {
                writer.write(value);
                bytesWritten += value.getBytes(StandardCharsets.UTF_8).length;
                pendingLines++;
                long now = SystemClock.elapsedRealtime();
                if (pendingLines >= FLUSH_EVERY_LINES || now - lastFlushElapsedMs >= 1_000L) {
                    writer.flush();
                    pendingLines = 0;
                    lastFlushElapsedMs = now;
                }
            } catch (IOException error) {
                lastError = "write failed: " + error;
            }
        }
    }

    private void recordInternalError(String prefix, Throwable error) {
        String message = prefix + " | " + error.getClass().getSimpleName() + ": " + error.getMessage();
        lastError = message;
        writeLine("[LOGGER] " + message);
    }

    private void closeWriterLocked() {
        if (writer == null) return;
        try {
            writer.flush();
        } catch (IOException error) {
            lastError = "flush failed: " + error;
        }
        try {
            writer.close();
        } catch (IOException error) {
            lastError = "close failed: " + error;
        }
        writer = null;
    }

    private long getElapsedMsInternal() {
        return startedElapsedMs <= 0L ? 0L
                : Math.max(0L, SystemClock.elapsedRealtime() - startedElapsedMs);
    }

    private File getLogDirectory() {
        File directory = app.getExternalFilesDir("diagnostic-logs");
        if (directory != null) return directory;
        return new File(app.getFilesDir(), "diagnostic-logs");
    }

    private File findNewestSession() {
        File directory = getLogDirectory();
        File[] files = directory.listFiles((dir, name) -> name.startsWith("SmartTube-session-")
                && name.endsWith(".txt"));
        if (files == null || files.length == 0) return null;
        Arrays.sort(files, (first, second) -> Long.compare(second.lastModified(), first.lastModified()));
        return files[0];
    }

    private void pruneOldSessions(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.startsWith("SmartTube-session-")
                && name.endsWith(".txt"));
        if (files == null || files.length <= MAX_STORED_SESSIONS) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (int i = MAX_STORED_SESSIONS; i < files.length; i++) {
            if (!files[i].equals(activeFile)) {
                //noinspection ResultOfMethodCallIgnored
                files[i].delete();
            }
        }
    }
}
