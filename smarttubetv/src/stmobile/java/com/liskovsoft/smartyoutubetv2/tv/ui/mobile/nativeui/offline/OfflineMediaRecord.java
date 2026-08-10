package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

/** Immutable database projection for one audio-only offline item. */
public final class OfflineMediaRecord {
    private final String mediaId;
    private final String title;
    private final String author;
    private final String thumbnailUrl;
    private final long durationMs;
    private final String mimeType;
    private final String codec;
    private final String fileKey;
    private final long bytesDownloaded;
    private final long bytesTotal;
    private final OfflineMediaState state;
    private final String failureReason;
    private final long createdAtMs;
    private final long updatedAtMs;
    private final long lastAccessAtMs;
    private final long expiresAtMs;

    OfflineMediaRecord(String mediaId, String title, String author, String thumbnailUrl,
                       long durationMs, String mimeType, String codec, String fileKey,
                       long bytesDownloaded, long bytesTotal, OfflineMediaState state,
                       String failureReason, long createdAtMs, long updatedAtMs,
                       long lastAccessAtMs, long expiresAtMs) {
        this.mediaId = safe(mediaId);
        this.title = safe(title);
        this.author = safe(author);
        this.thumbnailUrl = safe(thumbnailUrl);
        this.durationMs = Math.max(0L, durationMs);
        this.mimeType = safe(mimeType);
        this.codec = safe(codec);
        this.fileKey = safe(fileKey);
        this.bytesDownloaded = Math.max(0L, bytesDownloaded);
        this.bytesTotal = Math.max(0L, bytesTotal);
        this.state = state == null ? OfflineMediaState.FAILED : state;
        this.failureReason = safe(failureReason);
        this.createdAtMs = Math.max(0L, createdAtMs);
        this.updatedAtMs = Math.max(0L, updatedAtMs);
        this.lastAccessAtMs = Math.max(0L, lastAccessAtMs);
        this.expiresAtMs = Math.max(0L, expiresAtMs);
    }

    public String getMediaId() { return mediaId; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public long getDurationMs() { return durationMs; }
    public String getMimeType() { return mimeType; }
    public String getCodec() { return codec; }
    public String getFileKey() { return fileKey; }
    public long getBytesDownloaded() { return bytesDownloaded; }
    public long getBytesTotal() { return bytesTotal; }
    public OfflineMediaState getState() { return state; }
    public String getFailureReason() { return failureReason; }
    public long getCreatedAtMs() { return createdAtMs; }
    public long getUpdatedAtMs() { return updatedAtMs; }
    public long getLastAccessAtMs() { return lastAccessAtMs; }
    public long getExpiresAtMs() { return expiresAtMs; }
    public boolean isAvailable() { return state == OfflineMediaState.AVAILABLE; }
    public boolean isExpired(long nowMs) {
        return state == OfflineMediaState.EXPIRED || (expiresAtMs > 0L && nowMs >= expiresAtMs);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
