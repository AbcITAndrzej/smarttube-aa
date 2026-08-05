package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model;

public final class MobileMediaItem {
    public enum Kind { VIDEO, SHORT, LIVE, PLAYLIST, CHANNEL, SECTION_LINK }

    private final String id;
    private final Kind kind;
    private final String title;
    private final String subtitle;
    private final String thumbnailUrl;
    private final String durationText;
    private final long progressMs;
    private final long durationMs;
    private final boolean playable;
    private final String playlistId;

    public MobileMediaItem(String id, Kind kind, String title, String subtitle,
                           String thumbnailUrl, String durationText, long progressMs,
                           long durationMs, boolean playable) {
        this(id, kind, title, subtitle, thumbnailUrl, durationText, progressMs,
                durationMs, playable, null);
    }

    public MobileMediaItem(String id, Kind kind, String title, String subtitle,
                           String thumbnailUrl, String durationText, long progressMs,
                           long durationMs, boolean playable, String playlistId) {
        this.id = id;
        this.kind = kind == null ? Kind.VIDEO : kind;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.thumbnailUrl = thumbnailUrl;
        this.durationText = durationText == null ? "" : durationText;
        this.progressMs = Math.max(0, progressMs);
        this.durationMs = Math.max(0, durationMs);
        this.playable = playable;
        this.playlistId = playlistId;
    }

    public String getId() { return id; }
    public Kind getKind() { return kind; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public String getDurationText() { return durationText; }
    public long getProgressMs() { return progressMs; }
    public long getDurationMs() { return durationMs; }
    public boolean isPlayable() { return playable; }
    public String getPlaylistId() { return playlistId; }
}
