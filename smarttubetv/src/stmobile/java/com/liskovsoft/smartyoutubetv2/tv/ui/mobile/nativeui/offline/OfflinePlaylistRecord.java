package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

/** Immutable projection of one offline playlist queue. */
public final class OfflinePlaylistRecord {
    private final String playlistId;
    private final String title;
    private final String thumbnailUrl;
    private final OfflinePlaylistState state;
    private final int totalCount;
    private final int completedCount;
    private final int failedCount;
    private final long bytesDownloaded;
    private final long bytesTotal;
    private final String failureReason;
    private final long createdAtMs;
    private final long updatedAtMs;

    OfflinePlaylistRecord(String playlistId, String title, String thumbnailUrl,
                          OfflinePlaylistState state, int totalCount, int completedCount,
                          int failedCount, long bytesDownloaded, long bytesTotal,
                          String failureReason, long createdAtMs, long updatedAtMs) {
        this.playlistId = safe(playlistId);
        this.title = safe(title);
        this.thumbnailUrl = safe(thumbnailUrl);
        this.state = state == null ? OfflinePlaylistState.FAILED : state;
        this.totalCount = Math.max(0, totalCount);
        this.completedCount = Math.max(0, completedCount);
        this.failedCount = Math.max(0, failedCount);
        this.bytesDownloaded = Math.max(0L, bytesDownloaded);
        this.bytesTotal = Math.max(0L, bytesTotal);
        this.failureReason = safe(failureReason);
        this.createdAtMs = Math.max(0L, createdAtMs);
        this.updatedAtMs = Math.max(0L, updatedAtMs);
    }

    public String getPlaylistId() { return playlistId; }
    public String getTitle() { return title; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public OfflinePlaylistState getState() { return state; }
    public int getTotalCount() { return totalCount; }
    public int getCompletedCount() { return completedCount; }
    public int getFailedCount() { return failedCount; }
    public long getBytesDownloaded() { return bytesDownloaded; }
    public long getBytesTotal() { return bytesTotal; }
    public String getFailureReason() { return failureReason; }
    public long getCreatedAtMs() { return createdAtMs; }
    public long getUpdatedAtMs() { return updatedAtMs; }
    public int getRemainingCount() { return Math.max(0, totalCount - completedCount - failedCount); }
    public int getProgressPercent() {
        if (totalCount <= 0) return 0;
        return Math.max(0, Math.min(100, (int) Math.round(completedCount * 100d / totalCount)));
    }
    public boolean isTerminal() {
        return state == OfflinePlaylistState.AVAILABLE
                || state == OfflinePlaylistState.PARTIAL
                || state == OfflinePlaylistState.FAILED;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
