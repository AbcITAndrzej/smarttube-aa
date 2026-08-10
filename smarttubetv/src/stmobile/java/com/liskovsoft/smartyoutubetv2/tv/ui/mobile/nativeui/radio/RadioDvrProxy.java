package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio;

import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small localhost proxy that turns progressive radio streams into a rolling seek window.
 *
 * <p>It intentionally does not touch VOD. HLS playlists are passed through unchanged because
 * segment-aware DVR needs a different implementation; progressive MP3/AAC/OGG streams are cached
 * in a bounded in-memory ring and replayed from the requested point.</p>
 */
public final class RadioDvrProxy implements RadioTimeShiftController {
    private static final String TAG = "P17-RadioDVR";
    private static final int CHUNK_BYTES = 16 * 1024;
    private static final long MAX_BUFFER_BYTES = 24L * 1024L * 1024L;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final long MIN_SEEK_WINDOW_MS = 2_000L;
    private static final int UPSTREAM_RECONNECT_ATTEMPTS = 2;
    private static final long UPSTREAM_RECONNECT_BASE_DELAY_MS = 900L;
    private static final String USER_AGENT = "SmartTube-AA RadioDVR/1.0";

    private final RadioPreferences preferences;
    private final Object lock = new Object();
    private final Deque<Chunk> chunks = new ArrayDeque<>();
    private final ExecutorService workers = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "SmartTube-RadioDVR");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong requestNonce = new AtomicLong();

    private volatile boolean active;
    private volatile boolean failed;
    private volatile long generation;
    private volatile String directStreamUrl = "";
    private volatile String contentType = "audio/mpeg";
    private volatile ServerSocket server;
    private volatile HttpURLConnection upstreamConnection;
    private long nextSequence;
    private long nextLogicalMs;
    private long bufferedBytes;
    /** Absolute logical time represented by ExoPlayer position 0 for the currently opened source. */
    private volatile long playbackAnchorLogicalMs = -1L;
    private volatile int stationBitrateKbps = 128;

    public RadioDvrProxy(RadioPreferences preferences) {
        this.preferences = preferences;
    }

    @Override public String start(RadioStation station) {
        stop();
        if (station == null) return "";
        directStreamUrl = station.getStreamUrl() == null ? "" : station.getStreamUrl().trim();
        stationBitrateKbps = station.getBitrate() > 0 ? station.getBitrate() : 128;
        if (!preferences.isTimeShiftEnabled() || directStreamUrl.isEmpty()
                || looksLikeHls(directStreamUrl)) {
            MobileDiagnostics.info(TAG, "direct mode timeshift=" + preferences.isTimeShiftEnabled()
                    + " hls=" + looksLikeHls(directStreamUrl));
            return directStreamUrl;
        }

        long session = ++generation;
        failed = false;
        active = true;
        playbackAnchorLogicalMs = -1L;
        synchronized (lock) {
            chunks.clear();
            nextSequence = 0L;
            nextLogicalMs = 0L;
            bufferedBytes = 0L;
        }
        try {
            ServerSocket localServer = new ServerSocket(0, 8,
                    InetAddress.getByName("127.0.0.1"));
            server = localServer;
            workers.execute(() -> acceptClients(session, localServer));
            workers.execute(() -> pumpUpstream(session, station));
            String url = localUrl("live");
            MobileDiagnostics.info(TAG, "session start port=" + localServer.getLocalPort()
                    + " bitrate=" + stationBitrateKbps + "kbps window="
                    + preferences.getTimeShiftMinutes() + "min");
            return url;
        } catch (Throwable error) {
            MobileDiagnostics.warn(TAG, "localhost proxy unavailable: " + error.getMessage());
            failed = true;
            active = false;
            closeServer();
            return directStreamUrl;
        }
    }

    @Override public boolean isActive() {
        return active;
    }

    @Override public boolean canSeek() {
        return active && !failed && getWindowDurationMs() >= MIN_SEEK_WINDOW_MS;
    }

    @Override public boolean hasFailed() {
        return failed;
    }

    @Override public long getWindowDurationMs() {
        synchronized (lock) {
            if (chunks.isEmpty()) return 0L;
            return Math.max(0L, chunks.peekLast().endMs - chunks.peekFirst().startMs);
        }
    }

    @Override public long getBufferedBytes() {
        synchronized (lock) {
            return Math.max(0L, bufferedBytes);
        }
    }

    @Override public long positionForPlayer(long playerPositionMs) {
        synchronized (lock) {
            if (chunks.isEmpty()) return 0L;
            long oldest = chunks.peekFirst().startMs;
            long newest = chunks.peekLast().endMs;
            long anchor = playbackAnchorLogicalMs;
            if (anchor < 0L) return Math.max(0L, newest - oldest); // Initial /live request.
            long absolute = anchor + Math.max(0L, playerPositionMs);
            return clamp(absolute - oldest, 0L, Math.max(0L, newest - oldest));
        }
    }

    @Override public String seekTo(long virtualPositionMs) {
        synchronized (lock) {
            if (chunks.isEmpty()) return getDirectStreamUrl();
            long oldest = chunks.peekFirst().startMs;
            long newest = chunks.peekLast().endMs;
            long target = oldest + clamp(virtualPositionMs, 0L, Math.max(0L, newest - oldest));
            Chunk chunk = findChunkForTimeLocked(target);
            playbackAnchorLogicalMs = chunk == null ? target : chunk.startMs;
            return localUrl(Long.toString(playbackAnchorLogicalMs));
        }
    }

    @Override public String goLive() {
        synchronized (lock) {
            playbackAnchorLogicalMs = chunks.isEmpty() ? -1L : chunks.peekLast().startMs;
        }
        return localUrl("live");
    }

    @Override public String getDirectStreamUrl() {
        return directStreamUrl;
    }

    @Override public void stop() {
        active = false;
        ++generation;
        HttpURLConnection connection = upstreamConnection;
        upstreamConnection = null;
        if (connection != null) connection.disconnect();
        closeServer();
        synchronized (lock) {
            lock.notifyAll();
            chunks.clear();
            bufferedBytes = 0L;
            nextLogicalMs = 0L;
            nextSequence = 0L;
        }
        playbackAnchorLogicalMs = -1L;
        failed = false;
    }

    @Override public void close() {
        stop();
        workers.shutdownNow();
    }

    private void pumpUpstream(long session, RadioStation station) {
        int reconnectAttempt = 0;
        while (active && generation == session) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(directStreamUrl).openConnection();
                upstreamConnection = connection;
                connection.setInstanceFollowRedirects(true);
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Accept", "audio/*,*/*;q=0.8");
                // Some Icecast servers otherwise insert metadata bytes into the audio payload.
                connection.setRequestProperty("Icy-MetaData", "0");
                int response = connection.getResponseCode();
                if (response < 200 || response >= 300) {
                    throw new IOException("upstream HTTP " + response);
                }
                String mime = connection.getContentType();
                if (mime != null && !mime.trim().isEmpty()) {
                    contentType = mime.split(";", 2)[0].trim();
                }
                if (looksLikeHls(contentType) || looksLikeHls(connection.getURL().toString())) {
                    throw new UnsupportedOperationException("HLS stream uses native live playback");
                }
                if (reconnectAttempt > 0) {
                    MobileDiagnostics.info(TAG, "upstream recovered attempt=" + reconnectAttempt);
                }
                reconnectAttempt = 0;
                try (InputStream input = new BufferedInputStream(
                        connection.getInputStream(), CHUNK_BYTES * 2)) {
                    byte[] buffer = new byte[CHUNK_BYTES];
                    while (active && generation == session) {
                        int count = input.read(buffer);
                        if (count < 0) throw new IOException("upstream ended");
                        if (count == 0) continue;
                        byte[] payload = new byte[count];
                        System.arraycopy(buffer, 0, payload, 0, count);
                        appendChunk(payload);
                    }
                }
            } catch (UnsupportedOperationException unsupported) {
                if (active && generation == session) {
                    // Do not loop on HLS: the caller will immediately fall back to the native URL.
                    failed = true;
                    MobileDiagnostics.warn(TAG, "upstream unsupported: " + unsupported.getMessage());
                    synchronized (lock) { lock.notifyAll(); }
                }
                return;
            } catch (Throwable error) {
                if (!active || generation != session) return;
                reconnectAttempt++;
                if (reconnectAttempt > UPSTREAM_RECONNECT_ATTEMPTS) {
                    failed = true;
                    MobileDiagnostics.warn(TAG, "upstream failed after retries: "
                            + error.getMessage());
                    synchronized (lock) { lock.notifyAll(); }
                    return;
                }
                long delay = UPSTREAM_RECONNECT_BASE_DELAY_MS * reconnectAttempt;
                MobileDiagnostics.warn(TAG, "upstream interrupted; reconnect "
                        + reconnectAttempt + "/" + UPSTREAM_RECONNECT_ATTEMPTS
                        + " in " + delay + "ms: " + error.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } finally {
                if (connection != null) connection.disconnect();
                if (upstreamConnection == connection) upstreamConnection = null;
            }
        }
    }

    private void appendChunk(byte[] payload) {
        long durationMs = Math.max(1L, payload.length * 8L / Math.max(1, stationBitrateKbps));
        synchronized (lock) {
            long start = nextLogicalMs;
            long end = start + durationMs;
            chunks.addLast(new Chunk(nextSequence++, start, end, payload));
            nextLogicalMs = end;
            bufferedBytes += payload.length;
            long wantedWindow = preferences.getTimeShiftWindowMs();
            while (chunks.size() > 1) {
                Chunk first = chunks.peekFirst();
                Chunk last = chunks.peekLast();
                boolean overDuration = last.endMs - first.startMs > wantedWindow;
                boolean overBytes = bufferedBytes > MAX_BUFFER_BYTES;
                if (!overDuration && !overBytes) break;
                Chunk removed = chunks.removeFirst();
                bufferedBytes -= removed.data.length;
            }
            lock.notifyAll();
        }
    }

    private void acceptClients(long session, ServerSocket localServer) {
        while (active && generation == session && !localServer.isClosed()) {
            try {
                Socket socket = localServer.accept();
                workers.execute(() -> serveClient(session, socket));
            } catch (IOException error) {
                if (active && generation == session) {
                    MobileDiagnostics.warn(TAG, "accept failed: " + error.getMessage());
                }
                return;
            }
        }
    }

    private void serveClient(long session, Socket socket) {
        try (Socket client = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     client.getInputStream(), StandardCharsets.US_ASCII));
             OutputStream output = new BufferedOutputStream(client.getOutputStream(), CHUNK_BYTES * 2)) {
            client.setTcpNoDelay(true);
            String requestLine = reader.readLine();
            if (requestLine == null || !requestLine.startsWith("GET ")) return;
            String target = parseFromValue(requestLine);
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) { /* consume headers */ }

            Chunk start = waitForStartChunk(session, target);
            if (start == null) {
                writeStatus(output, failed ? 503 : 404, failed ? "DVR unavailable" : "No audio");
                return;
            }
            // The requested chunk can be evicted between seekTo() and this connection. Anchor
            // the exposed virtual position to the chunk that is actually served, not to the stale
            // requested timestamp. This keeps the phone and Android Auto seek bars consistent.
            playbackAnchorLogicalMs = start.startMs;
            output.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + safeContentType() + "\r\n"
                    + "Cache-Control: no-store\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.flush();

            long sequence = start.sequence;
            while (active && generation == session) {
                Chunk next = waitForSequence(session, sequence);
                if (next == null) break;
                output.write(next.data);
                output.flush();
                sequence = next.sequence + 1L;
            }
        } catch (Throwable ignored) {
            // ExoPlayer routinely closes the old HTTP connection when a user seeks.
        }
    }

    private Chunk waitForStartChunk(long session, String from) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000L;
        synchronized (lock) {
            while (active && generation == session && chunks.isEmpty() && !failed
                    && System.currentTimeMillis() < deadline) {
                lock.wait(250L);
            }
            if (chunks.isEmpty()) return null;
            if ("live".equals(from)) return chunks.peekLast();
            try {
                long absolute = Long.parseLong(from);
                Chunk found = findChunkForTimeLocked(absolute);
                return found == null ? chunks.peekFirst() : found;
            } catch (NumberFormatException ignored) {
                return chunks.peekLast();
            }
        }
    }

    private Chunk waitForSequence(long session, long sequence) throws InterruptedException {
        synchronized (lock) {
            while (active && generation == session) {
                if (!chunks.isEmpty()) {
                    Chunk first = chunks.peekFirst();
                    if (sequence < first.sequence) sequence = first.sequence; // Reader fell behind eviction.
                    for (Chunk chunk : chunks) {
                        if (chunk.sequence >= sequence) return chunk;
                    }
                }
                if (failed) return null;
                lock.wait(250L);
            }
            return null;
        }
    }

    private Chunk findChunkForTimeLocked(long absoluteMs) {
        Chunk fallback = chunks.peekLast();
        for (Chunk chunk : chunks) {
            if (absoluteMs < chunk.endMs) return chunk;
        }
        return fallback;
    }

    private String localUrl(String from) {
        ServerSocket localServer = server;
        if (localServer == null || localServer.isClosed()) return directStreamUrl;
        return "http://127.0.0.1:" + localServer.getLocalPort()
                + "/radio?from=" + from + "&n=" + requestNonce.incrementAndGet();
    }

    private static String parseFromValue(String requestLine) {
        int start = requestLine.indexOf("from=");
        if (start < 0) return "live";
        start += 5;
        int end = requestLine.indexOf('&', start);
        if (end < 0) end = requestLine.indexOf(' ', start);
        if (end < 0) end = requestLine.length();
        return requestLine.substring(start, end).trim();
    }

    private String safeContentType() {
        String mime = contentType == null ? "" : contentType.trim();
        return mime.isEmpty() ? "audio/mpeg" : mime;
    }

    private static void writeStatus(OutputStream output, int code, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + code + " " + message + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n";
        output.write(header.getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.flush();
    }

    private void closeServer() {
        ServerSocket localServer = server;
        server = null;
        if (localServer != null) {
            try { localServer.close(); } catch (IOException ignored) { }
        }
    }

    private static boolean looksLikeHls(String value) {
        if (value == null) return false;
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("mpegurl") || normalized.contains("m3u8");
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Chunk {
        final long sequence;
        final long startMs;
        final long endMs;
        final byte[] data;

        Chunk(long sequence, long startMs, long endMs, byte[] data) {
            this.sequence = sequence;
            this.startMs = startMs;
            this.endMs = endMs;
            this.data = data;
        }
    }
}
