#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PLAYBACK_FRAGMENT = ROOT / (
    "smarttubetv/src/stmobile/java/com/liskovsoft/smartyoutubetv2/tv/ui/mobile/"
    "nativeui/fragment/MobilePlaybackFragment.java"
)

text = PLAYBACK_FRAGMENT.read_text(encoding="utf-8")

old_state = '''        viewModel.getState().observe(getViewLifecycleOwner(), value -> {\n            MobilePlaybackSnapshot current = value.getData();\n            boolean loading = value.getStatus() == MobileLoadState.Status.LOADING\n                    || current != null && current.isBuffering();\n            progress.setVisibility(loading ? View.VISIBLE : View.GONE);\n'''
new_state = '''        viewModel.getState().observe(getViewLifecycleOwner(), value -> {\n            MobilePlaybackSnapshot current = value.getData();\n            boolean loading = value.getStatus() == MobileLoadState.Status.LOADING\n                    || current != null && current.isBuffering();\n            boolean keepScreenOn = loading || current != null && current.isPlaying();\n            view.setKeepScreenOn(keepScreenOn);\n            progress.setVisibility(loading ? View.VISIBLE : View.GONE);\n'''

old_destroy = '''    @Override public void onDestroyView() {\n        ui.removeCallbacks(hideControls);\n'''
new_destroy = '''    @Override public void onDestroyView() {\n        View playbackView = getView();\n        if (playbackView != null) playbackView.setKeepScreenOn(false);\n        ui.removeCallbacks(hideControls);\n'''

if new_state not in text:
    if old_state not in text:
        raise SystemExit("aa1.43.1: playback state anchor not found")
    text = text.replace(old_state, new_state, 1)

if new_destroy not in text:
    if old_destroy not in text:
        raise SystemExit("aa1.43.1: onDestroyView anchor not found")
    text = text.replace(old_destroy, new_destroy, 1)

PLAYBACK_FRAGMENT.write_text(text, encoding="utf-8")

final_text = PLAYBACK_FRAGMENT.read_text(encoding="utf-8")
for marker in (
    "view.setKeepScreenOn(keepScreenOn)",
    "playbackView.setKeepScreenOn(false)",
):
    if marker not in final_text:
        raise SystemExit(f"aa1.43.1: missing invariant: {marker}")

print("aa1.43.1 applied: keep screen on only during playback/buffering; no SABR changes")
