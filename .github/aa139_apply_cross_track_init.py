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

old_match = '''    private FormatSelector matchFormatSelector(FormatInitializationMetadata formatInitMetadata) {\n        if (formatSelector == null) {\n            return null;\n        }\n\n        if (formatSelector.match(formatInitMetadata.getFormatId(), formatInitMetadata.getMimeType())) {\n            return formatSelector;\n        }\n\n        return null;\n    }\n'''
new_match = '''    private FormatSelector matchFormatSelector(FormatInitializationMetadata formatInitMetadata) {\n        if (formatSelector == null) {\n            return null;\n        }\n\n        if (formatSelector.match(formatInitMetadata.getFormatId(), formatInitMetadata.getMimeType())) {\n            return formatSelector;\n        }\n\n        // SmartTube 32.38 behavior: YouTube may include initialization metadata\n        // for the companion enabled track even when this renderer requested only\n        // AUDIO_ONLY or VIDEO_ONLY. Treat that format as consumed/discarded instead\n        // of aborting playback with \"does not match any format selector\".\n        return emptySelector;\n    }\n'''
processor = replace_once(processor, old_match, new_match, "cross-track initialization metadata")
processor_path.write_text(processor)

final_text = processor_path.read_text()
if "return emptySelector;" not in final_text:
    raise SystemExit("aa1.39: official cross-track discard selector missing")
if "does not match any format selector" not in final_text:
    raise SystemExit("aa1.39: expected strict guard unexpectedly missing")

print("aa1.39 applied: official SmartTube 32.38 cross-track SABR init handling; multi-audio, subtitles and auth preserved")
