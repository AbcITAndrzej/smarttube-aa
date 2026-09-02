from pathlib import Path

path = Path('common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/VideoLoaderController.java')
s = path.read_text()

old = '''    public boolean activateProgressiveFallbackForCurrentVideo(String reason) {
        if (getPlayer() == null || mLastFormatInfo == null
                || isProgressiveFallbackActiveForCurrentVideo()
                || !enableProgressiveFallbackForCurrentVideo()) {
            return false;
        }
'''
new = '''    public boolean activateProgressiveFallbackForCurrentVideo(String reason) {
        int tracks = getLogicalAdaptiveAudioTrackCount();
        if (tracks > 1) {
            String why = reason != null ? reason : "unknown";
            Log.d(TAG, "V17_MULTI_AUDIO progressive-blocked tracks=%s reason=%s", tracks, why);
            MobileDiagnostics.session("V17_MULTI_AUDIO", "progressive-blocked tracks=" + tracks
                    + " reason=" + why);
            return false;
        }
        if (getPlayer() == null || mLastFormatInfo == null
                || isProgressiveFallbackActiveForCurrentVideo()
                || !enableProgressiveFallbackForCurrentVideo()) {
            return false;
        }
'''
if old not in s:
    raise SystemExit('activateProgressiveFallbackForCurrentVideo anchor not found')
s = s.replace(old, new, 1)

old = '''        if (mMultiAudioRecoveryAttempts >= 1) return false;
        mMultiAudioRecoveryAttempts++;
        String why = reason != null ? reason : "unknown";
        Log.d(TAG, "V16_MULTI_AUDIO preserve tracks=%s attempt=%s reason=%s",
                tracks, mMultiAudioRecoveryAttempts, why);
        MobileDiagnostics.session("V16_MULTI_AUDIO", "preserve tracks=" + tracks
                + " attempt=" + mMultiAudioRecoveryAttempts + " reason=" + why);
        YouTubeServiceManager.instance().applyNoPlaybackFix();
        reloadVideo(250);
        return true;
'''
new = '''        if (mMultiAudioRecoveryAttempts >= 3) return false;
        mMultiAudioRecoveryAttempts++;
        String why = reason != null ? reason : "unknown";
        int delayMs = mMultiAudioRecoveryAttempts == 1 ? 250
                : (mMultiAudioRecoveryAttempts == 2 ? 1250 : 4250);
        Log.d(TAG, "V17_MULTI_AUDIO preserve tracks=%s attempt=%s delayMs=%s reason=%s",
                tracks, mMultiAudioRecoveryAttempts, delayMs, why);
        MobileDiagnostics.session("V17_MULTI_AUDIO", "preserve tracks=" + tracks
                + " attempt=" + mMultiAudioRecoveryAttempts + " delayMs=" + delayMs
                + " reason=" + why);
        // Rotate the player client instead of collapsing a real multi-audio catalogue
        // to a muxed single-audio URL. The last delay also gives newer SABR servers
        // time to satisfy NextRequestPolicy windows observed in field logs.
        YouTubeServiceManager.instance().applyNoPlaybackFix();
        reloadVideo(delayMs);
        return true;
'''
if old not in s:
    raise SystemExit('tryPreserveMultiAudioRecovery anchor not found')
s = s.replace(old, new, 1)

path.write_text(s)
print('Applied aa1.33 multi-audio safe fallback patch')
