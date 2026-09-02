#!/usr/bin/env python3
from pathlib import Path
import subprocess


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"aa1.38: anchor not found: {label}")
    return text.replace(old, new, 1)

# Start from aa1.37: auth preserved, official playback recovery, direct timedtext
# subtitles, and the official 32.38 audioTrack/xtags metadata pipeline.
subprocess.run(["python3", ".github/aa137_apply_official_audio_metadata.py"], check=True)

manifest_path = Path(
    "exoplayer-amzn-2.10.6/library/sabr/src/main/java/"
    "com/google/android/exoplayer2/source/sabr/manifest/SabrManifest.java"
)
manifest = manifest_path.read_text()

# aa1.15 introduced VIDEO_AND_AUDIO on each independent renderer lane. That makes
# YouTube legitimately send audio on the VIDEO SabrStream. The VIDEO selector then
# rejects a dubbed audio FormatId (e.g. itag 251 + xtags lang=pl), causing
# SabrStreamError and client fallback to the original language.
#
# Restore SmartTube 32.38 semantics: the video renderer asks for video only, the
# audio renderer asks for audio only, and audioTrackId is sent only on the audio
# request. This keeps the two SabrStream/FormatSelector instances type-safe.
old_selection = '''        // V15: both per-renderer requests describe the same full A/V session.\n        // Modern YouTube SABR no longer reliably serves AUDIO_ONLY/VIDEO_ONLY for\n        // multi-audio videos, so keep the video resolution identity even on the\n        // local audio lane. The companion format is still marked fully buffered\n        // later, which prevents the lane from consuming the other renderer's media.\n        int height = selectedVideoFormat != null ? selectedVideoFormat.height : -1;\n        int videoBitrate = selectedVideoFormat != null ? selectedVideoFormat.bitrate : 0;\n        int audioBitrate = selectedAudioFormat != null ? selectedAudioFormat.bitrate : 0;\n        int bandwidthEstimate = videoBitrate > 0 || audioBitrate > 0\n                ? Math.max(1, videoBitrate) + Math.max(0, audioBitrate) : -1;\n        // SmartTube 32.38: carry YouTube's exact audioTrack.id independently\n        // from the human-readable ExoPlayer language. This is essential when\n        // the same itag is used by original and dubbed tracks.\n        String audioTrackId = getAudioTrackId(selectedAudioFormat);\n        boolean drcEnabled = selectedAudioFormat != null && selectedAudioFormat.isDrc;\n'''
new_selection = '''        // SmartTube 32.38 per-renderer SABR contract: one media type per request.\n        // A VIDEO selector must never be asked to consume an AUDIO FormatId carrying\n        // language-specific xtags, and vice versa.\n        int height = trackType == C.TRACK_TYPE_VIDEO && selectedVideoFormat != null\n                ? selectedVideoFormat.height : -1;\n        int bandwidthEstimate = trackType == C.TRACK_TYPE_VIDEO && selectedVideoFormat != null\n                ? selectedVideoFormat.bitrate\n                : selectedAudioFormat != null ? selectedAudioFormat.bitrate : -1;\n        String audioTrackId = trackType == C.TRACK_TYPE_AUDIO\n                ? getAudioTrackId(selectedAudioFormat) : null;\n        boolean drcEnabled = trackType == C.TRACK_TYPE_AUDIO\n                && selectedAudioFormat != null && selectedAudioFormat.isDrc;\n'''
manifest = replace_once(manifest, old_selection, new_selection, "per-track selection state")

old_enabled = '''                .setDrcEnabled(drcEnabled)\n                // V15: modern SABR expects a complete A/V session. We still mark the\n                // companion renderer as fully buffered below, so this per-renderer\n                // ExoPlayer port receives only the format it actually consumes.\n                .setEnabledTrackTypesBitfield(EnabledTrackTypes.VIDEO_AND_AUDIO);\n'''
new_enabled = '''                .setDrcEnabled(drcEnabled)\n                .setEnabledTrackTypesBitfield(\n                        height != -1 ? EnabledTrackTypes.VIDEO_ONLY : EnabledTrackTypes.AUDIO_ONLY);\n'''
manifest = replace_once(manifest, old_enabled, new_enabled, "official enabled track types")

manifest_path.write_text(manifest)

# CI-time invariants. Fail immediately if an old full-A/V per-renderer request
# survives or if audioTrackId leaks onto the video request.
final_text = manifest_path.read_text()
if "EnabledTrackTypes.VIDEO_AND_AUDIO" in final_text:
    raise SystemExit("aa1.38: VIDEO_AND_AUDIO still present in SabrManifest")
if "EnabledTrackTypes.VIDEO_ONLY : EnabledTrackTypes.AUDIO_ONLY" not in final_text:
    raise SystemExit("aa1.38: official per-track SABR mode missing")
if "trackType == C.TRACK_TYPE_AUDIO\n                ? getAudioTrackId(selectedAudioFormat) : null" not in final_text:
    raise SystemExit("aa1.38: audioTrackId is not audio-lane scoped")

print("aa1.38 applied: SmartTube 32.38 VIDEO_ONLY/AUDIO_ONLY SABR lanes; official audio metadata, auth and direct subtitles preserved")
