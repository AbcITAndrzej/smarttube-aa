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

# SmartTube 32.38 intentionally accepts initialization metadata for the companion
# enabled track. The server can include e.g. a Polish dubbed itag 251 on the VIDEO
# SabrStream even when that renderer asked for VIDEO_ONLY. The old AA fork returned
# null here, and processFormatInitializationMetadata then aborted playback with
# "does not match any format selector".
old_match = '''    private FormatSelector matchFormatSelector(FormatInitializationMetadata formatInitMetadata) {\n        if (formatSelector == null) {\n            return null;\n        }\n\n        if (formatSelector.match(formatInitMetadata.getFormatId(), formatInitMetadata.getMimeType())) {\n            return formatSelector;\n        }\n\n        return null;\n    }\n'''
new_match = '''    private FormatSelector matchFormatSelector(FormatInitializationMetadata formatInitMetadata) {\n        if (formatSelector == null) {\n            return null;\n        }\n\n        if (formatSelector.match(formatInitMetadata.getFormatId(), formatInitMetadata.getMimeType())) {\n            return formatSelector;\n        }\n\n        // Some SABR responses contain initialization data for the other enabled\n        // track even when this chunk source requested only audio or only video.\n        // Keep the server informed that the format was consumed, but do not pass\n        // cross-track media to this source's extractor.\n        return emptySelector;\n    }\n'''
processor = replace_once(processor, old_match, new_match, "cross-track initialization metadata")

# Returning emptySelector is only half of the upstream fix. The initialized format
# is marked discard=true; that flag must propagate to Segment so processMedia()
# actually skips companion-track bytes instead of feeding audio to the video
# extractor (or video to the audio extractor).
old_discard = '''                initializedFormat,\n                actualDurationMs == 0 || actualDurationMs == NO_VALUE,\n                false,\n                false,\n                mediaHeader.hasSequenceLmt() ? mediaHeader.getSequenceLmt() : NO_VALUE\n'''
new_discard = '''                initializedFormat,\n                actualDurationMs == 0 || actualDurationMs == NO_VALUE,\n                initializedFormat.discard,\n                false,\n                mediaHeader.hasSequenceLmt() ? mediaHeader.getSequenceLmt() : NO_VALUE\n'''
processor = replace_once(processor, old_discard, new_discard, "propagate discard flag to Segment")

processor_path.write_text(processor)

final_text = processor_path.read_text()
if "return emptySelector;" not in final_text:
    raise SystemExit("aa1.39: official cross-track discard selector missing")
if "initializedFormat.discard,\n                false," not in final_text:
    raise SystemExit("aa1.39: discard flag is not propagated to Segment")

print("aa1.39 applied: complete SmartTube 32.38 cross-track SABR handling (accept + discard); multi-audio, subtitles and auth preserved")
