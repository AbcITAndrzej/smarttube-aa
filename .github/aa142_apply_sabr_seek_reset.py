#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CHUNK_SOURCE = ROOT / (
    "exoplayer-amzn-2.10.6/library/sabr/src/main/java/"
    "com/google/android/exoplayer2/source/sabr/DefaultSabrChunkSource.java"
)

text = CHUNK_SOURCE.read_text(encoding="utf-8")

old = '''        boolean isInit = nexChunkIdx == -1;\n        FormatId formatId = formatSelector.getSelectedFormatId();\n        int iTag = formatId != null ? formatId.getItag() : -1;\n\n        if (nexChunkIdx == -1) {\n            sabrStream.reset(iTag);\n        }\n\n        nexChunkIdx++;\n'''

new = '''        boolean isInit = nexChunkIdx == -1;\n        FormatId formatId = formatSelector.getSelectedFormatId();\n        int iTag = formatId != null ? formatId.getItag() : -1;\n\n        // Upstream SmartTube c9c5141: reset SABR state whenever ExoPlayer supplies\n        // an explicit seek/load position. Without this, the request playerTime can\n        // advance while SabrStream still carries stale consumed ranges / playback\n        // state, which can make the server send older media sequences and eventually\n        // leave ExoPlayer buffering even though its logical buffered position is ahead.\n        boolean isSeek = seekTimeUs != C.TIME_UNSET;\n        if (isSeek) {\n            Log.e(TAG, "AA142 SABR seek reset: track=" + trackType\n                    + ", seekTimeUs=" + seekTimeUs + ", itag=" + iTag\n                    + ", previousChunkIdx=" + nexChunkIdx);\n            sabrStream.reset(iTag);\n            nexChunkIdx = -1;\n        }\n\n        nexChunkIdx++;\n'''

if new in text:
    print("aa1.42 already applied")
elif old not in text:
    raise SystemExit("aa1.42: DefaultSabrChunkSource seek-reset anchor not found")
else:
    CHUNK_SOURCE.write_text(text.replace(old, new, 1), encoding="utf-8")

final_text = CHUNK_SOURCE.read_text(encoding="utf-8")
required = [
    "boolean isSeek = seekTimeUs != C.TIME_UNSET;",
    "sabrStream.reset(iTag);",
    "nexChunkIdx = -1;",
    "AA142 SABR seek reset",
]
for marker in required:
    if marker not in final_text:
        raise SystemExit(f"aa1.42: missing invariant: {marker}")

print("aa1.42 applied: upstream SABR seek-state reset added; aa1.41.1 stack otherwise unchanged")
