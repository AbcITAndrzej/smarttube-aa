package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

/** Persistent queue item belonging to an offline playlist. */
public final class OfflinePlaylistEntry {
    private final String playlistId;
    private final int position;
    private final String mediaId;
    private final String title;
    private final String author;
    private final String thumbnailUrl;
    private final long durationMs;
    private final OfflinePlaylistItemState state;
    private final long bytesDownloaded;
    private final long bytesTotal;
    private final String failureReason;
    private final int attempts;

    OfflinePlaylistEntry(String playlistId, int position, String mediaId, String title,
                         String author, String thumbnailUrl, long durationMs,
                         OfflinePlaylistItemState state, long bytesDownloaded, long bytesTotal,
                         String failureReason, int attempts) {
        this.playlistId = safe(playlistId);
        this.position = Math.max(0, position);
        this.mediaId = safe(mediaId);
        this.title = safe(title);
        this.author = safe(author);
        this.thumbnailUrl = safe(thumbnailUrl);
        this.durationMs = Math.max(0L, durationMs);
        this.state = state == null ? OfflinePlaylistItemState.FAILED : state;
        this.bytesDownloaded = Math.max(0L, bytesDownloaded);
        this.bytesTotal = Math.max(0L, bytesTotal);
        this.failureReason = safe(failureReason);
        this.attempts = Math.max(0, attempts);
    }

    public String getPlaylistId() { return playlistId; }
    public int getPosition() { return position; }
    public String getMediaId() { return mediaId; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public long getDurationMs() { return durationMs; }
    public OfflinePlaylistItemState getState() { return state; }
    public long getBytesDownloaded() { return bytesDownloaded; }
    public long getBytesTotal() { return bytesTotal; }
    public String getFailureReason() { return failureReason; }
    public int getAttempts() { return attempts; }

    OfflineMediaDescriptor toDescriptor(String mimeType, String codec) {
        return new OfflineMediaDescriptor(mediaId, title, author, thumbnailUrl,
                durationMs, mimeType, codec);
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
