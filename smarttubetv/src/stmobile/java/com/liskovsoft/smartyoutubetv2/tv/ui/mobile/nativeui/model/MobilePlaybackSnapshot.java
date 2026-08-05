package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MobilePlaybackSnapshot {
    private final String mediaId;
    private final String title;
    private final String subtitle;
    private final boolean prepared;
    private final boolean playing;
    private final boolean buffering;
    private final boolean ended;
    private final long positionMs;
    private final long durationMs;
    private final long bufferedPositionMs;
    private final float speed;
    private final List<MobileTrack> audioTracks;
    private final List<MobileTrack> subtitleTracks;

    public MobilePlaybackSnapshot(String mediaId, String title, String subtitle,
                                  boolean prepared, boolean playing, boolean buffering,
                                  long positionMs, long durationMs, long bufferedPositionMs,
                                  float speed, List<MobileTrack> audioTracks,
                                  List<MobileTrack> subtitleTracks) {
        this(mediaId, title, subtitle, prepared, playing, buffering, false,
                positionMs, durationMs, bufferedPositionMs, speed, audioTracks, subtitleTracks);
    }

    public MobilePlaybackSnapshot(String mediaId, String title, String subtitle,
                                  boolean prepared, boolean playing, boolean buffering,
                                  boolean ended, long positionMs, long durationMs,
                                  long bufferedPositionMs, float speed,
                                  List<MobileTrack> audioTracks,
                                  List<MobileTrack> subtitleTracks) {
        this.mediaId = mediaId;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.prepared = prepared;
        this.playing = playing;
        this.buffering = buffering;
        this.ended = ended;
        this.positionMs = Math.max(0, positionMs);
        this.durationMs = Math.max(0, durationMs);
        this.bufferedPositionMs = Math.max(0, bufferedPositionMs);
        this.speed = speed <= 0 ? 1f : speed;
        this.audioTracks = immutable(audioTracks);
        this.subtitleTracks = immutable(subtitleTracks);
    }

    private static List<MobileTrack> immutable(List<MobileTrack> value) {
        return Collections.unmodifiableList(new ArrayList<>(
                value == null ? Collections.<MobileTrack>emptyList() : value));
    }

    public String getMediaId() { return mediaId; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public boolean isPrepared() { return prepared; }
    public boolean isPlaying() { return playing; }
    public boolean isBuffering() { return buffering; }
    public boolean isEnded() { return ended; }
    public long getPositionMs() { return positionMs; }
    public long getDurationMs() { return durationMs; }
    public long getBufferedPositionMs() { return bufferedPositionMs; }
    public float getSpeed() { return speed; }
    public List<MobileTrack> getAudioTracks() { return audioTracks; }
    public List<MobileTrack> getSubtitleTracks() { return subtitleTracks; }
}
