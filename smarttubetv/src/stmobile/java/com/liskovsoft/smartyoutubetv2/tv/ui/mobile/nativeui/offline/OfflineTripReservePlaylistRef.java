package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

/** Lightweight recent-playlist history used by Stage 10 trip reserve. */
public final class OfflineTripReservePlaylistRef {
    private final String playlistId;
    private final String title;
    private final String thumbnailUrl;
    private final long lastPlayedAtMs;

    OfflineTripReservePlaylistRef(String playlistId, String title, String thumbnailUrl,
                                  long lastPlayedAtMs) {
        this.playlistId = safe(playlistId);
        this.title = safe(title);
        this.thumbnailUrl = safe(thumbnailUrl);
        this.lastPlayedAtMs = Math.max(0L, lastPlayedAtMs);
    }

    public String getPlaylistId() { return playlistId; }
    public String getTitle() { return title; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public long getLastPlayedAtMs() { return lastPlayedAtMs; }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
