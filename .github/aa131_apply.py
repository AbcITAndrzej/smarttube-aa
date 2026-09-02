from pathlib import Path


def replace(path, old, new):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"missing expected block in {path}")
    p.write_text(s.replace(old, new, 1))

vf = 'MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/videoinfo/models/formats/VideoFormat.java'
replace(vf,
    '    @JsonPath("$.audioTrack.audioIsDefault")\n    private boolean mAudioTrackIsDefault;\n    private VideoUrlHolder mUrlHolder;',
    '    @JsonPath("$.audioTrack.audioIsDefault")\n    private boolean mAudioTrackIsDefault;\n    @JsonPath("$.audioTrack.isAutoDubbed")\n    private boolean mAudioTrackIsAutoDubbed;\n    private VideoUrlHolder mUrlHolder;')
replace(vf,
    '    public boolean isAudioTrackDefault() {\n        return mAudioTrackIsDefault;\n    }\n\n    public String getContentLength() {',
    '    public boolean isAudioTrackDefault() {\n        return mAudioTrackIsDefault;\n    }\n\n    public boolean isAudioTrackAutoDubbed() {\n        return mAudioTrackIsAutoDubbed;\n    }\n\n    public String getContentLength() {')

vib = 'MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/videoinfo/VideoInfoServiceBase.java'
replace(vib,
    '            Log.d(TAG, "V15_AUDIO_TRACK id=%s name=%s language=%s default=%s variants=[%s]",\n                    entry.getKey(),\n                    first != null ? valueOrDash(first.getAudioTrackDisplayName()) : "-",\n                    first != null ? valueOrDash(first.getLanguage()) : "-",\n                    first != null && first.isAudioTrackDefault(),\n                    details.toString());',
    '            Log.d(TAG, "V16_AUDIO_TRACK id=%s name=%s language=%s default=%s autoDubbed=%s variants=[%s]",\n                    entry.getKey(),\n                    first != null ? valueOrDash(first.getAudioTrackDisplayName()) : "-",\n                    first != null ? valueOrDash(first.getLanguage()) : "-",\n                    first != null && first.isAudioTrackDefault(),\n                    first != null && first.isAudioTrackAutoDubbed(),\n                    details.toString());')

vl = 'common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/VideoLoaderController.java'
replace(vl, 'import java.util.List;\n', 'import java.util.HashSet;\nimport java.util.List;\nimport java.util.Set;\n')
replace(vl,
    '    private String mProgressiveFallbackVideoId;\n    private long mProgressiveFallbackPositionMs;\n    private MediaItemFormatInfo mLastFormatInfo;',
    '    private String mProgressiveFallbackVideoId;\n    private long mProgressiveFallbackPositionMs;\n    private String mMultiAudioRecoveryVideoId;\n    private int mMultiAudioRecoveryAttempts;\n    private MediaItemFormatInfo mLastFormatInfo;')
anchor = '''    public void disableProgressiveFallbackForCurrentVideo() {
        if (mProgressiveFallbackVideoId != null) {
            Log.d(TAG, "V10_PROGRESSIVE disabled video=%s", mProgressiveFallbackVideoId);
            MobileDiagnostics.session("V10_PROGRESSIVE", "disabled after fallback rejection");
        }
        mProgressiveFallbackVideoId = null;
        mProgressiveFallbackPositionMs = 0;
    }
'''
insert = anchor + '''
    public int getLogicalAdaptiveAudioTrackCount() {
        MediaItemFormatInfo info = mLastFormatInfo;
        if (info == null || info.getAdaptiveFormats() == null) return 0;
        Set<String> tracks = new HashSet<>();
        for (MediaFormat format : info.getAdaptiveFormats()) {
            if (format == null || format.getMimeType() == null
                    || !format.getMimeType().toLowerCase().startsWith("audio/")) continue;
            String language = format.getLanguage();
            if (language != null && !language.trim().isEmpty()) tracks.add(language.trim());
        }
        return tracks.size();
    }

    public boolean tryPreserveMultiAudioRecovery(String reason) {
        Video video = getVideo();
        int tracks = getLogicalAdaptiveAudioTrackCount();
        if (video == null || video.isLive || tracks <= 1) return false;
        if (!Helpers.equals(mMultiAudioRecoveryVideoId, video.videoId)) {
            mMultiAudioRecoveryVideoId = video.videoId;
            mMultiAudioRecoveryAttempts = 0;
        }
        if (mMultiAudioRecoveryAttempts >= 1) return false;
        mMultiAudioRecoveryAttempts++;
        String why = reason != null ? reason : "unknown";
        Log.d(TAG, "V16_MULTI_AUDIO preserve tracks=%s attempt=%s reason=%s",
                tracks, mMultiAudioRecoveryAttempts, why);
        MobileDiagnostics.session("V16_MULTI_AUDIO", "preserve tracks=" + tracks
                + " attempt=" + mMultiAudioRecoveryAttempts + " reason=" + why);
        YouTubeServiceManager.instance().applyNoPlaybackFix();
        reloadVideo(250);
        return true;
    }
'''
replace(vl, anchor, insert)

ef = 'common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/ErrorFixerController.java'
replace(ef,
    '        } else if (!mBufferingDetector.isPlayable()\n                && mVideoLoaderController.activateProgressiveFallbackForCurrentVideo("long-buffer")) {',
    '        } else if (!mBufferingDetector.isPlayable()\n                && mVideoLoaderController.tryPreserveMultiAudioRecovery("long-buffer")) {\n            Log.d(TAG, "V16_MULTI_AUDIO long buffering -> preserve adaptive audio");\n            MobileDiagnostics.session("V16_MULTI_AUDIO", "long-buffer -> adaptive-client-rotation");\n        } else if (!mBufferingDetector.isPlayable()\n                && mVideoLoaderController.activateProgressiveFallbackForCurrentVideo("long-buffer")) {')
replace(ef,
    '        if (mVideoLoaderController.activateProgressiveFallbackForCurrentVideo("startup-stall")) {',
    '        if (mVideoLoaderController.tryPreserveMultiAudioRecovery("startup-stall")) {\n            Log.d(TAG, "V16_MULTI_AUDIO startup stall -> preserve adaptive audio");\n            MobileDiagnostics.session("V16_MULTI_AUDIO", "startup-stall -> adaptive-client-rotation");\n            return;\n        }\n\n        if (mVideoLoaderController.activateProgressiveFallbackForCurrentVideo("startup-stall")) {')

repo = 'smarttubetv/src/stmobile/java/com/liskovsoft/smartyoutubetv2/tv/ui/mobile/nativeui/legacy/LegacyMobilePlaybackRepository.java'
replace(repo,
    '                        if (loader.isProgressiveFallbackActiveForCurrentVideo()) return true;\n                        if (loader.activateProgressiveFallbackForCurrentVideo("mobile-watchdog")) {',
    '                        if (loader.isProgressiveFallbackActiveForCurrentVideo()) return true;\n                        if (loader.tryPreserveMultiAudioRecovery("mobile-watchdog")) {\n                            MobileDiagnostics.info("V16-MultiAudio",\n                                    "mobile watchdog preserved adaptive multi-audio");\n                            return true;\n                        }\n                        if (loader.activateProgressiveFallbackForCurrentVideo("mobile-watchdog")) {')
