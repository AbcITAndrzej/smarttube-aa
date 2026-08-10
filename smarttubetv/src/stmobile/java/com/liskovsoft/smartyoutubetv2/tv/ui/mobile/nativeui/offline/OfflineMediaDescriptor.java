package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

/** Stable metadata required to reserve an audio-only offline item. No signed stream URL is stored. */
public final class OfflineMediaDescriptor {
    private final String mediaId;
    private final String title;
    private final String author;
    private final String thumbnailUrl;
    private final long durationMs;
    private final String mimeType;
    private final String codec;

    public OfflineMediaDescriptor(String mediaId, String title, String author, String thumbnailUrl,
                                  long durationMs, String mimeType, String codec) {
        this.mediaId = safe(mediaId);
        this.title = safe(title);
        this.author = safe(author);
        this.thumbnailUrl = safe(thumbnailUrl);
        this.durationMs = Math.max(0L, durationMs);
        this.mimeType = safe(mimeType);
        this.codec = safe(codec);
    }

    public String getMediaId() { return mediaId; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public long getDurationMs() { return durationMs; }
    public String getMimeType() { return mimeType; }
    public String getCodec() { return codec; }

    public boolean isValid() { return !mediaId.isEmpty(); }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
