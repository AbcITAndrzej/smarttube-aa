#!/usr/bin/env python3
from pathlib import Path
import subprocess


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"aa1.40: anchor not found: {label}")
    return text.replace(old, new, 1)


# Keep the complete aa1.39 playback stack first (auth, subtitles, audio metadata,
# per-renderer SABR lanes and cross-track initialization handling).
subprocess.run(["python3", ".github/aa139_apply_cross_track_init.py"], check=True)

chunk_source_path = Path(
    "exoplayer-amzn-2.10.6/library/sabr/src/main/java/"
    "com/google/android/exoplayer2/source/sabr/DefaultSabrChunkSource.java"
)
chunk_source = chunk_source_path.read_text()

# SABR has no ExoPlayer segment index in this port, so the stock end-of-period
# check is commented out. For finite VOD this means that after the final segment
# has already been loaded, getNextChunk() keeps POSTing from the same SABR player
# time forever. Shorts make this especially visible because the last ~1.5 MiB
# segment is returned many times per second until the Java heap is exhausted.
#
# getSegmentStartTimeMs() is historically misnamed here: it returns the END of
# the most recently initialized non-init segment (start + duration), i.e. exactly
# the next SABR player time. Once that reaches the finite period duration, the
# already queued final chunk is complete and ExoPlayer should receive EOS instead
# of another network chunk.
old_tag = '''    private static final String TAG = DefaultSabrChunkSource.class.getSimpleName();\n'''
new_tag = '''    private static final String TAG = DefaultSabrChunkSource.class.getSimpleName();\n    // Allow for millisecond rounding differences between MediaHeader and manifest duration.\n    private static final long VOD_END_TOLERANCE_MS = 250L;\n'''
chunk_source = replace_once(chunk_source, old_tag, new_tag, "VOD EOS tolerance constant")

old_period = '''        long periodDurationUs = representationHolder.periodDurationUs;\n        boolean periodEnded = periodDurationUs != C.TIME_UNSET;\n\n'''
new_period = '''        long periodDurationUs = representationHolder.periodDurationUs;\n        boolean periodEnded = periodDurationUs != C.TIME_UNSET;\n\n        // SABR VOD EOS guard. The index-based ExoPlayer end check below cannot run\n        // in this port because SABR does not expose a SegmentIndex. Use the end of\n        // the last successfully initialized SABR segment as the next request time.\n        // Never apply this to dynamic/live manifests.\n        if (!manifest.dynamic && periodEnded) {\n            FormatId selectedFormatId = formatSelector.getSelectedFormatId();\n            int selectedItag = selectedFormatId != null ? selectedFormatId.getItag() : -1;\n            long nextSabrStartMs = sabrStream.getSegmentStartTimeMs(selectedItag);\n            long periodDurationMs = periodDurationUs / 1_000L;\n\n            if (nextSabrStartMs > 0L\n                    && nextSabrStartMs + VOD_END_TOLERANCE_MS >= periodDurationMs) {\n                Log.d(TAG, "SABR VOD EOS: track=" + trackType\n                        + ", nextStartMs=" + nextSabrStartMs\n                        + ", periodDurationMs=" + periodDurationMs);\n                out.endOfStream = true;\n                return;\n            }\n        }\n\n'''
chunk_source = replace_once(chunk_source, old_period, new_period, "finite SABR VOD end-of-stream guard")

chunk_source_path.write_text(chunk_source)

final_text = chunk_source_path.read_text()
if "VOD_END_TOLERANCE_MS = 250L" not in final_text:
    raise SystemExit("aa1.40: VOD EOS tolerance missing")
if "SABR VOD EOS: track=" not in final_text:
    raise SystemExit("aa1.40: VOD EOS guard missing")
if "if (!manifest.dynamic && periodEnded)" not in final_text:
    raise SystemExit("aa1.40: live-stream safety guard missing")
if "out.endOfStream = true;" not in final_text:
    raise SystemExit("aa1.40: EOS signal missing")

print("aa1.40 applied: finite SABR VOD stops after the final loaded segment; live playback unchanged")
