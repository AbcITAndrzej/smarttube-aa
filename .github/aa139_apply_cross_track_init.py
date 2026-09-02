#!/usr/bin/env python3
from pathlib import Path
import subprocess


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"aa1.39: anchor not found: {label}")
    return text.replace(old, new, 1)

# Keep aa1.38: auth-preserving recovery, direct subtitles, official audioTrack/xtags,
# and per-renderer VIDEO_ONLY/AUDIO_ONLY SABR requests.
subprocess.run(["python3", ".github/aa138_apply_official_sabr_lanes.py"], check=True)

processor_path = Path(
    "exoplayer-amzn-2.10.6/library/sabr/src/main/java/"
    "com/google/android/exoplayer2/source/sabr/parser/SabrProcessor.java"
)
processor = processor_path.read_text()

# Port the complete cross-track handling from SmartTube's Aug-2026
# "Fix SABR playback and CDN routing" change. YouTube is allowed to include
# initialization data for the companion enabled track even when this renderer
# requested only AUDIO_ONLY or VIDEO_ONLY.
old_match = '''    private FormatSelector matchFormatSelector(FormatInitializationMetadata formatInitMetadata) {\n        if (formatSelector == null) {\n            return null;\n        }\n\n        if (formatSelector.match(formatInitMetadata.getFormatId(), formatInitMetadata.getMimeType())) {\n            return formatSelector;\n        }\n\n        return null;\n    }\n'''
new_match = '''    private FormatSelector matchFormatSelector(FormatInitializationMetadata formatInitMetadata) {\n        if (formatSelector == null) {\n            return null;\n        }\n\n        if (formatSelector.match(formatInitMetadata.getFormatId(), formatInitMetadata.getMimeType())) {\n            return formatSelector;\n        }\n\n        // Some SABR responses contain initialization data for the other enabled\n        // track even when this chunk source requested only audio or only video.\n        // Keep the server informed that the format was consumed, but do not pass\n        // cross-track media to this source's extractor.\n        return emptySelector;\n    }\n'''
processor = replace_once(processor, old_match, new_match, "cross-track initialization metadata")

# The discard selector may be reused for several companion formats. Do not mistake
# that for a server-side quality switch; only a real, non-discard selector is
# exclusive to one initialized format.
old_guard = '''        for (SelectedFormat selectedFormat : selectedFormats.values()) {\n            if (selectedFormat.formatSelector == formatSelector) {\n                throw new SabrStreamError("Server changed format. Changing formats is not currently supported");\n            }\n        }\n'''
new_guard = '''        for (SelectedFormat selectedFormat : selectedFormats.values()) {\n            if (selectedFormat.formatSelector == formatSelector && !formatSelector.isDiscardMedia()) {\n                throw new SabrStreamError("Server changed format. Changing formats is not currently supported");\n            }\n        }\n'''
processor = replace_once(processor, old_guard, new_guard, "allow multiple discarded companion formats")

# The selected companion format is marked discard=true. Propagate that state into
# Segment so processMedia() consumes/skips its bytes instead of feeding audio to a
# video extractor or video to an audio extractor.
old_discard = '''                initializedFormat,\n                actualDurationMs == 0 || actualDurationMs == NO_VALUE,\n                false,\n                false,\n                mediaHeader.hasSequenceLmt() ? mediaHeader.getSequenceLmt() : NO_VALUE\n'''
new_discard = '''                initializedFormat,\n                actualDurationMs == 0 || actualDurationMs == NO_VALUE,\n                initializedFormat.discard,\n                false,\n                mediaHeader.hasSequenceLmt() ? mediaHeader.getSequenceLmt() : NO_VALUE\n'''
processor = replace_once(processor, old_discard, new_discard, "propagate discard flag to Segment")

# Match upstream log severity for expected discarded companion media.
processor = replace_once(
    processor,
    '            Log.e(TAG, "processMedia: part discarded. contentLength: %s, itag: %s", contentLength, segment.formatId.getItag());\n',
    '            Log.d(TAG, "processMedia: part discarded. contentLength: %s, itag: %s", contentLength, segment.formatId.getItag());\n',
    "discarded companion media log level")

processor_path.write_text(processor)

final_text = processor_path.read_text()
if "return emptySelector;" not in final_text:
    raise SystemExit("aa1.39: official cross-track discard selector missing")
if "selectedFormat.formatSelector == formatSelector && !formatSelector.isDiscardMedia()" not in final_text:
    raise SystemExit("aa1.39: discard selector reuse guard missing")
if "initializedFormat.discard,\n                false," not in final_text:
    raise SystemExit("aa1.39: discard flag is not propagated to Segment")

print("aa1.39 applied: complete SmartTube cross-track SABR fix (match + reusable discard selector + media discard); multi-audio, subtitles and auth preserved")
