package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

/** Immutable projection of the passive listen-and-save queue/history. No signed URL is stored. */
public final class OfflineListenSaveEntry {
    private final String mediaId;
    private final String title;
    private final String author;
    private final String thumbnailUrl;
    private final long durationMs;
    private final OfflineListenSaveState state;
    private final long bytesDownloaded;
    private final long bytesTotal;
    private final String failureReason;
    private final int attempts;
    private final long createdAtMs;
    private final long updatedAtMs;
    private final long lastListenedAtMs;

    OfflineListenSaveEntry(String mediaId, String title, String author, String thumbnailUrl,
                           long durationMs, OfflineListenSaveState state,
                           long bytesDownloaded, long bytesTotal, String failureReason,
                           int attempts, long createdAtMs, long updatedAtMs,
                           long lastListenedAtMs) {
        this.mediaId = safe(mediaId);
        this.title = safe(title);
        this.author = safe(author);
        this.thumbnailUrl = safe(thumbnailUrl);
        this.durationMs = Math.max(0L, durationMs);
        this.state = state == null ? OfflineListenSaveState.FAILED : state;
        this.bytesDownloaded = Math.max(0L, bytesDownloaded);
        this.bytesTotal = Math.max(0L, bytesTotal);
        this.failureReason = safe(failureReason);
        this.attempts = Math.max(0, attempts);
        this.createdAtMs = Math.max(0L, createdAtMs);
        this.updatedAtMs = Math.max(0L, updatedAtMs);
        this.lastListenedAtMs = Math.max(0L, lastListenedAtMs);
    }

    public String getMediaId() { return mediaId; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public long getDurationMs() { return durationMs; }
    public OfflineListenSaveState getState() { return state; }
    public long getBytesDownloaded() { return bytesDownloaded; }
    public long getBytesTotal() { return bytesTotal; }
    public String getFailureReason() { return failureReason; }
    public int getAttempts() { return attempts; }
    public long getCreatedAtMs() { return createdAtMs; }
    public long getUpdatedAtMs() { return updatedAtMs; }
    public long getLastListenedAtMs() { return lastListenedAtMs; }
    public int getProgressPercent() {
        if (bytesTotal <= 0L) return 0;
        return (int) Math.min(100L, bytesDownloaded * 100L / bytesTotal);
    }

    OfflineMediaDescriptor toDescriptor(String mimeType, String codec) {
        return new OfflineMediaDescriptor(mediaId, title, author, thumbnailUrl,
                durationMs, mimeType, codec);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
