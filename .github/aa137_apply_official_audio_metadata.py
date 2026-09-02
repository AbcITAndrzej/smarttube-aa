#!/usr/bin/env python3
from pathlib import Path
import subprocess

CORE_SHA = "082e2e488cce739f224d0854773fc8d1cf14a48e"  # MediaServiceCore used by SmartTube 32.38
SMARTTUBE_SHA = "26076c93237172af8e09656d2cfe06ab0d9eb872"  # SmartTube 32.38s


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"aa1.37: anchor not found: {label}")
    return text.replace(old, new, 1)


def download(repo, ref, src, dst):
    url = f"https://raw.githubusercontent.com/{repo}/{ref}/{src}"
    subprocess.run(["curl", "-fsSL", url, "-o", dst], check=True)


# Start with the known-good AA auth preservation, official 32.38 recovery policy,
# and the direct timedtext subtitle fix from aa1.36.
subprocess.run(["python3", ".github/aa136_apply_direct_subtitles.py"], check=True)

# ---------------------------------------------------------------------------
# 1) Restore the exact MediaServiceCore multi-audio metadata path used by 32.38.
#    The old AA core parses WEB /player responses but its Innertube model simply
#    does not contain streamingFormat.audioTrack or xtags, so Gson drops them.
#    Copy only the playback-format files; sign-in/account services stay untouched.
# ---------------------------------------------------------------------------
core_files = [
    "mediaserviceinterfaces/src/main/java/com/liskovsoft/mediaserviceinterfaces/data/MediaFormat.java",
    "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/innertube/models/PlayerResult.kt",
    "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/innertube/impl/MediaFormatImpl.kt",
    "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/data/YouTubeMediaFormat.java",
    "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/videoinfo/models/formats/VideoFormat.java",
]
for rel in core_files:
    download("yuliskov/MediaServiceCore", CORE_SHA, rel, f"MediaServiceCore/{rel}")

# The old core already has exoNameFix(), but predates the official helper added in
# Aug 2026. Add exactly the final 32.38 language semantics: audioTrack.id prefix is
# the language, and isAutoDubbed distinguishes dubbed-auto from original.
helper_path = Path("MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/googlecommon/common/helpers/YouTubeHelper.java")
helper = helper_path.read_text()
if "getSabrLanguage(String audioTrackId" not in helper:
    helper = replace_once(
        helper,
        "    @NonNull\n    public static String generateCPNParameter2() {\n        return RandomStringFromAlphabetGenerator.generate2(16);\n    }\n}",
        "    @NonNull\n    public static String generateCPNParameter2() {\n        return RandomStringFromAlphabetGenerator.generate2(16);\n    }\n\n"
        "    @Nullable\n"
        "    public static String getSabrLanguage(String audioTrackId, boolean isAutoDubbed) {\n"
        "        if (audioTrackId == null) {\n"
        "            return null;\n"
        "        }\n\n"
        "        String lang = audioTrackId.split(\"\\\\.\")[0];\n"
        "        // original, descriptive, dubbed, dubbed-auto, secondary\n"
        "        String acont = isAutoDubbed ? \"dubbed-auto\" : \"original\";\n\n"
        "        return String.format(\"%s (%s)\", exoNameFix(lang), acont);\n"
        "    }\n"
        "}",
        "YouTubeHelper.getSabrLanguage")
helper_path.write_text(helper)

# ---------------------------------------------------------------------------
# 2) Restore the exact ExoPlayer SABR discriminator carrier used by 32.38.
#    audioTrackId must not be hidden in Format.language. 32.38 transports it as
#    Metadata.Entry and also transports xtags into FormatId, because multiple
#    logical audio tracks may reuse the same itag/codec.
# ---------------------------------------------------------------------------
metadata_rel = "exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/parser/models/SabrFormatMetadata.java"
download("yuliskov/SmartTube", SMARTTUBE_SHA, metadata_rel, metadata_rel)

selector_rel = "exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/parser/models/FormatSelector.java"
download("yuliskov/SmartTube", SMARTTUBE_SHA, selector_rel, selector_rel)

parser_path = Path("exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/manifest/SabrManifestParser.java")
parser = parser_path.read_text()
parser = replace_once(
    parser,
    "import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;\n",
    "import com.google.android.exoplayer2.drm.DrmInitData.SchemeData;\n"
    "import com.google.android.exoplayer2.metadata.Metadata;\n",
    "SabrManifestParser Metadata import")
parser = replace_once(
    parser,
    "import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext.ClientName;\n",
    "import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext.ClientName;\n"
    "import com.google.android.exoplayer2.source.sabr.parser.models.SabrFormatMetadata;\n",
    "SabrManifestParser SabrFormatMetadata import")
parser = replace_once(
    parser,
    "                        isDrc,\n                        lastModified);\n\n        SegmentBase segmentBase = null;\n",
    "                        isDrc,\n                        lastModified);\n\n"
    "        if (!TextUtils.isEmpty(mediaFormat.getXtags())\n"
    "                || !TextUtils.isEmpty(mediaFormat.getAudioTrackId())) {\n"
    "            format = format.copyWithMetadata(\n"
    "                    new Metadata(new SabrFormatMetadata(\n"
    "                            mediaFormat.getXtags(), mediaFormat.getAudioTrackId())));\n"
    "        }\n\n"
    "        SegmentBase segmentBase = null;\n",
    "attach official SABR format metadata")
parser_path.write_text(parser)

# ---------------------------------------------------------------------------
# 3) Consume the metadata in the ABR request. Keep the AA V15 full A/V request
#    shape (required by this older renderer split), but obtain audioTrackId exactly
#    as official 32.38 does instead of the previous label/language workaround.
# ---------------------------------------------------------------------------
manifest_path = Path("exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/manifest/SabrManifest.java")
manifest = manifest_path.read_text()
manifest = replace_once(
    manifest,
    "import com.google.android.exoplayer2.source.sabr.parser.models.FormatSelector;\n",
    "import com.google.android.exoplayer2.source.sabr.parser.models.FormatSelector;\n"
    "import com.google.android.exoplayer2.source.sabr.parser.models.SabrFormatMetadata;\n",
    "SabrManifest SabrFormatMetadata import")
manifest = replace_once(
    manifest,
    "        // YouTube's audioTrack.id is carried in Format.language only when the\n"
    "        // corresponding audioTrack.displayName was present (stored as label).\n"
    "        // Ordinary language metadata must not be mistaken for a SABR track id.\n"
    "        String audioTrackId = selectedAudioFormat != null\n"
    "                && selectedAudioFormat.label != null\n"
    "                && !selectedAudioFormat.label.isEmpty()\n"
    "                ? selectedAudioFormat.language : null;\n",
    "        // SmartTube 32.38: carry YouTube's exact audioTrack.id independently\n"
    "        // from the human-readable ExoPlayer language. This is essential when\n"
    "        // the same itag is used by original and dubbed tracks.\n"
    "        String audioTrackId = getAudioTrackId(selectedAudioFormat);\n",
    "replace language-as-audioTrackId workaround")
manifest = replace_once(
    manifest,
    "    private @NonNull FormatSelector getFormatSelector(int trackType) {\n",
    "    private static @Nullable String getAudioTrackId(@Nullable Format format) {\n"
    "        if (format == null || format.metadata == null) {\n"
    "            return null;\n"
    "        }\n\n"
    "        for (int i = 0; i < format.metadata.length(); i++) {\n"
    "            if (format.metadata.get(i) instanceof SabrFormatMetadata) {\n"
    "                String audioTrackId = ((SabrFormatMetadata) format.metadata.get(i)).audioTrackId;\n"
    "                if (audioTrackId != null && !audioTrackId.isEmpty()) {\n"
    "                    return audioTrackId;\n"
    "                }\n"
    "            }\n"
    "        }\n\n"
    "        return null;\n"
    "    }\n\n"
    "    private @NonNull FormatSelector getFormatSelector(int trackType) {\n",
    "official audioTrackId metadata reader")
manifest_path.write_text(manifest)

print("aa1.37 applied: official 32.38 WEB audioTrack/xtags pipeline + SABR metadata; auth and direct subtitles preserved")
