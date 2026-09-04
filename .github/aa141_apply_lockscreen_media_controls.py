#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:120]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


SNAPSHOT = "smarttubetv/src/stmobile/java/com/liskovsoft/smartyoutubetv2/tv/ui/mobile/nativeui/model/MobilePlaybackSnapshot.java"
MANAGER = "smarttubetv/src/stmobile/java/com/liskovsoft/smartyoutubetv2/tv/ui/mobile/nativeui/background/MobileMediaSessionManager.java"
REPO = "smarttubetv/src/stmobile/java/com/liskovsoft/smartyoutubetv2/tv/ui/mobile/nativeui/legacy/LegacyMobilePlaybackRepository.java"

# Carry artwork URL in the immutable playback snapshot while preserving every existing constructor.
replace_once(
    SNAPSHOT,
    '    private final String subtitle;\n    private final boolean prepared;',
    '    private final String subtitle;\n    private final String artworkUrl;\n    private final boolean prepared;'
)

replace_once(
    SNAPSHOT,
    '''    public MobilePlaybackSnapshot(String mediaId, String title, String subtitle,\n                                  boolean prepared, boolean playing, boolean buffering,\n                                  boolean ended, long positionMs, long durationMs,\n                                  long bufferedPositionMs, float speed,\n                                  List<MobileTrack> videoTracks,\n                                  List<MobileTrack> audioTracks,\n                                  List<MobileTrack> subtitleTracks,\n                                  List<SeekBarSegment> seekBarSegments) {\n        this.mediaId = mediaId;\n        this.title = title == null ? "" : title;\n        this.subtitle = subtitle == null ? "" : subtitle;\n        this.prepared = prepared;\n        this.playing = playing;\n        this.buffering = buffering;\n        this.ended = ended;\n        this.positionMs = Math.max(0, positionMs);\n        this.durationMs = Math.max(0, durationMs);\n        this.bufferedPositionMs = Math.max(0, bufferedPositionMs);\n        this.speed = speed <= 0 ? 1f : speed;\n        this.videoTracks = immutable(videoTracks);\n        this.audioTracks = immutable(audioTracks);\n        this.subtitleTracks = immutable(subtitleTracks);\n        this.seekBarSegments = immutableSegments(seekBarSegments);\n    }''',
    '''    public MobilePlaybackSnapshot(String mediaId, String title, String subtitle,\n                                  boolean prepared, boolean playing, boolean buffering,\n                                  boolean ended, long positionMs, long durationMs,\n                                  long bufferedPositionMs, float speed,\n                                  List<MobileTrack> videoTracks,\n                                  List<MobileTrack> audioTracks,\n                                  List<MobileTrack> subtitleTracks,\n                                  List<SeekBarSegment> seekBarSegments) {\n        this(mediaId, title, subtitle, prepared, playing, buffering, ended,\n                positionMs, durationMs, bufferedPositionMs, speed, videoTracks, audioTracks,\n                subtitleTracks, seekBarSegments, "");\n    }\n\n    public MobilePlaybackSnapshot(String mediaId, String title, String subtitle,\n                                  boolean prepared, boolean playing, boolean buffering,\n                                  boolean ended, long positionMs, long durationMs,\n                                  long bufferedPositionMs, float speed,\n                                  List<MobileTrack> videoTracks,\n                                  List<MobileTrack> audioTracks,\n                                  List<MobileTrack> subtitleTracks,\n                                  List<SeekBarSegment> seekBarSegments,\n                                  String artworkUrl) {\n        this.mediaId = mediaId;\n        this.title = title == null ? "" : title;\n        this.subtitle = subtitle == null ? "" : subtitle;\n        this.artworkUrl = artworkUrl == null ? "" : artworkUrl;\n        this.prepared = prepared;\n        this.playing = playing;\n        this.buffering = buffering;\n        this.ended = ended;\n        this.positionMs = Math.max(0, positionMs);\n        this.durationMs = Math.max(0, durationMs);\n        this.bufferedPositionMs = Math.max(0, bufferedPositionMs);\n        this.speed = speed <= 0 ? 1f : speed;\n        this.videoTracks = immutable(videoTracks);\n        this.audioTracks = immutable(audioTracks);\n        this.subtitleTracks = immutable(subtitleTracks);\n        this.seekBarSegments = immutableSegments(seekBarSegments);\n    }'''
)

replace_once(
    SNAPSHOT,
    '    public String getSubtitle() { return subtitle; }\n    public boolean isPrepared()',
    '    public String getSubtitle() { return subtitle; }\n    public String getArtworkUrl() { return artworkUrl; }\n    public boolean isPrepared()'
)

# Expose previous/next as real MediaSession transport commands and primary lockscreen actions.
replace_once(
    MANAGER,
    '''        void pauseFromSystem();\n        void seekToFromSystem(long positionMs);''',
    '''        void pauseFromSystem();\n        void playPreviousFromSystem();\n        void playNextFromSystem();\n        void seekToFromSystem(long positionMs);'''
)

replace_once(
    MANAGER,
    '''    static final String ACTION_PAUSE = "app.smarttube.mobile.action.MEDIA_PAUSE";\n    static final String ACTION_REWIND = "app.smarttube.mobile.action.MEDIA_REWIND";''',
    '''    static final String ACTION_PAUSE = "app.smarttube.mobile.action.MEDIA_PAUSE";\n    static final String ACTION_PREVIOUS = "app.smarttube.mobile.action.MEDIA_PREVIOUS";\n    static final String ACTION_NEXT = "app.smarttube.mobile.action.MEDIA_NEXT";\n    static final String ACTION_REWIND = "app.smarttube.mobile.action.MEDIA_REWIND";'''
)

replace_once(
    MANAGER,
    '''            @Override public void onPause() { pauseByUser(); }\n            @Override public void onStop() { stopAndDismiss(); }\n            @Override public void onSeekTo(long pos) { seekTo(pos); }''',
    '''            @Override public void onPause() { pauseByUser(); }\n            @Override public void onSkipToPrevious() { playback.playPreviousFromSystem(); }\n            @Override public void onSkipToNext() { playback.playNextFromSystem(); }\n            @Override public void onStop() { stopAndDismiss(); }\n            @Override public void onSeekTo(long pos) { seekTo(pos); }'''
)

replace_once(
    MANAGER,
    '''        if (ACTION_PLAY.equals(action)) requestPlay();\n        else if (ACTION_PAUSE.equals(action)) pauseByUser();\n        else if (ACTION_REWIND.equals(action)) seekBy(-SEEK_STEP_MS);''',
    '''        if (ACTION_PLAY.equals(action)) requestPlay();\n        else if (ACTION_PAUSE.equals(action)) pauseByUser();\n        else if (ACTION_PREVIOUS.equals(action)) playback.playPreviousFromSystem();\n        else if (ACTION_NEXT.equals(action)) playback.playNextFromSystem();\n        else if (ACTION_REWIND.equals(action)) seekBy(-SEEK_STEP_MS);'''
)

replace_once(
    MANAGER,
    '''                .addAction(R.drawable.mobile_ic_rewind,\n                        appContext.getString(R.string.mobile_background_rewind),\n                        servicePendingIntent(ACTION_REWIND, 2))\n                .addAction(playing ? R.drawable.mobile_ic_pause : R.drawable.mobile_ic_play,\n                        appContext.getString(playing\n                                ? R.string.mobile_background_pause : R.string.mobile_background_play),\n                        servicePendingIntent(playing ? ACTION_PAUSE : ACTION_PLAY, 3))\n                .addAction(R.drawable.mobile_ic_forward,\n                        appContext.getString(R.string.mobile_background_forward),\n                        servicePendingIntent(ACTION_FORWARD, 4))''',
    '''                .addAction(android.R.drawable.ic_media_previous,\n                        "Poprzedni",\n                        servicePendingIntent(ACTION_PREVIOUS, 2))\n                .addAction(playing ? R.drawable.mobile_ic_pause : R.drawable.mobile_ic_play,\n                        appContext.getString(playing\n                                ? R.string.mobile_background_pause : R.string.mobile_background_play),\n                        servicePendingIntent(playing ? ACTION_PAUSE : ACTION_PLAY, 3))\n                .addAction(android.R.drawable.ic_media_next,\n                        "Następny",\n                        servicePendingIntent(ACTION_NEXT, 4))'''
)

replace_once(
    MANAGER,
    '''                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snapshot.getTitle())\n                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, snapshot.getSubtitle())\n                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, snapshot.getDurationMs())''',
    '''                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snapshot.getTitle())\n                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, snapshot.getSubtitle())\n                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, snapshot.getArtworkUrl())\n                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, snapshot.getArtworkUrl())\n                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, snapshot.getArtworkUrl())\n                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, snapshot.getDurationMs())'''
)

replace_once(
    MANAGER,
    '''                | PlaybackStateCompat.ACTION_SEEK_TO\n                | PlaybackStateCompat.ACTION_REWIND''',
    '''                | PlaybackStateCompat.ACTION_SEEK_TO\n                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS\n                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT\n                | PlaybackStateCompat.ACTION_REWIND'''
)

# Wire system previous/next to the same queue-aware methods used by the mobile player's buttons.
replace_once(
    REPO,
    '''                        @Override public void pauseFromSystem() { setPlayWhenReady(false); }\n                        @Override public void seekToFromSystem(long positionMs) {''',
    '''                        @Override public void pauseFromSystem() { setPlayWhenReady(false); }\n                        @Override public void playPreviousFromSystem() {\n                            LegacyMobilePlaybackRepository.this.playPrevious();\n                        }\n                        @Override public void playNextFromSystem() {\n                            LegacyMobilePlaybackRepository.this.playNext();\n                        }\n                        @Override public void seekToFromSystem(long positionMs) {'''
)

replace_once(
    REPO,
    '''                position, duration, buffered, getSpeed(), videoTracks, audio, subtitles,\n                seekBarSegments);''',
    '''                position, duration, buffered, getSpeed(), videoTracks, audio, subtitles,\n                seekBarSegments, currentArtworkUrl());'''
)

replace_once(
    REPO,
    '''    private void logAudioCatalogIfChanged(List<FormatItem> rawFormats, List<MobileTrack> logicalTracks) {''',
    '''    private String currentArtworkUrl() {\n        if (video == null) return "";\n        String card = video.getCardImageUrl();\n        if (card != null && !card.trim().isEmpty()) return card.trim();\n        String background = video.bgImageUrl;\n        return background == null ? "" : background.trim();\n    }\n\n    private void logAudioCatalogIfChanged(List<FormatItem> rawFormats, List<MobileTrack> logicalTracks) {'''
)

print("aa1.41 lockscreen media controls patch applied")
