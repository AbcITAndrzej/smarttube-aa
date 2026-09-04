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

# Port only the missing-end-of-stream part of upstream SmartTube commit
# c9c5141787ad6ba90ae0bc0e56f4a6306d6d844f (2026-08-20):
# "exo sabr: fix backward seek stall; fix missing end-of-stream event".
#
# Our public SABR base predates that commit. Without this guard, a finite VOD/Short
# reaches its duration but getNextChunk() still creates another SABR POST. The server
# returns the final segment again, ExoPlayer queues it again, and the cycle repeats
# until the Java heap is exhausted. loadPositionUs is ExoPlayer's authoritative next
# load position, so this restores the same end-of-period decision used upstream.
old_period = '''        long periodDurationUs = representationHolder.periodDurationUs;\n        boolean periodEnded = periodDurationUs != C.TIME_UNSET;\n\n'''
new_period = '''        long periodDurationUs = representationHolder.periodDurationUs;\n        boolean periodEnded = periodDurationUs != C.TIME_UNSET;\n\n        // FIX: fire ending event on a video end\n        if (periodEnded && loadPositionUs >= periodDurationUs) {\n            // No segment index in SABR, so we can't compare per-segment boundaries like stock\n            // DASH does — comparing loadPositionUs directly against periodDurationUs is the\n            // SABR equivalent. Without this, getNextChunk() keeps firing "next chunk" requests\n            // past the real end of the video forever, and the player never reaches STATE_ENDED.\n            out.endOfStream = true;\n            return;\n        }\n\n'''
chunk_source = replace_once(chunk_source, old_period, new_period, "upstream SABR VOD end-of-stream guard")

chunk_source_path.write_text(chunk_source)

final_text = chunk_source_path.read_text()
if "if (periodEnded && loadPositionUs >= periodDurationUs)" not in final_text:
    raise SystemExit("aa1.40: upstream VOD EOS condition missing")
if "out.endOfStream = true;" not in final_text:
    raise SystemExit("aa1.40: EOS signal missing")

print("aa1.40 applied: upstream SmartTube finite SABR VOD end-of-stream fix; aa1.39 stack preserved")
