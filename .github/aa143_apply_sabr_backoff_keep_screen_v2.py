#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def patch(path: Path, old: str, new: str, label: str):
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"aa1.43: anchor not found: {label}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


sabr_input = ROOT / "exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/parser/misc/SabrExtractorInput.java"
matroska = ROOT / "exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/parser/adapter/SabrMatroskaAdapter.java"
chunk = ROOT / "exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/DefaultSabrChunkSource.java"
policy = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/errors/SabrDefaultLoadErrorHandlingPolicy.java"
fragment = ROOT / "smarttubetv/src/stmobile/java/com/liskovsoft/smartyoutubetv2/tv/ui/mobile/nativeui/fragment/MobilePlaybackFragment.java"

# SABR: detect responses which contain only NextRequestPolicy(backoff) and no media.
patch(sabr_input,
      "    private int remaining;\n    private MediaSegmentDataSabrPart data;\n",
      "    private int remaining;\n    private MediaSegmentDataSabrPart data;\n    private boolean mediaSeen;\n",
      "mediaSeen field")
patch(sabr_input,
      "        startPosition = position;\n        remaining = C.LENGTH_UNSET;\n",
      "        startPosition = position;\n        remaining = C.LENGTH_UNSET;\n        mediaSeen = false;\n",
      "mediaSeen init")
patch(sabr_input,
      "    private void fetchData() {\n",
      "    private void fetchData() throws IOException {\n",
      "fetchData throws")
patch(sabr_input,
      "            if (sabrPart == null) {\n                break;\n            }\n",
      "            if (sabrPart == null) {\n"
      "                if (!mediaSeen) {\n"
      "                    int backoffMs = sabrStream.getBackoffTimeMs();\n"
      "                    if (backoffMs > 0) {\n"
      "                        Log.e(TAG, \"AA143 SABR backoff wait: %s ms\", backoffMs);\n"
      "                        throw new IOException(BACKOFF_MARKER + backoffMs);\n"
      "                    }\n"
      "                }\n"
      "                break;\n"
      "            }\n",
      "no-media backoff")
patch(sabr_input,
      "            if (sabrPart instanceof MediaSegmentDataSabrPart) {\n"
      "                data = (MediaSegmentDataSabrPart) sabrPart;\n"
      "                startPosition = position;\n"
      "                break;\n"
      "            }\n",
      "            if (sabrPart instanceof MediaSegmentDataSabrPart) {\n"
      "                data = (MediaSegmentDataSabrPart) sabrPart;\n"
      "                startPosition = position;\n"
      "                mediaSeen = true;\n"
      "                break;\n"
      "            }\n",
      "mark media")
patch(sabr_input,
      "    private static void throwShouldNotBeCalled() {\n",
      "    public static final String BACKOFF_MARKER = \"AA143 SABR backoff requested, ms=\";\n\n"
      "    private static void throwShouldNotBeCalled() {\n",
      "backoff marker")

# Audio Matroska adapter swallows generic exceptions; let only our control signal escape.
patch(matroska,
      "        } catch (Exception e) {\n"
      "            Log.e(TAG, \"User doing seek? %s: %s\", e.getClass().getSimpleName(), e.getMessage());\n"
      "            e.printStackTrace();\n"
      "        } finally {\n",
      "        } catch (Exception e) {\n"
      "            if (e instanceof IOException && e.getMessage() != null\n"
      "                    && e.getMessage().contains(SabrExtractorInput.BACKOFF_MARKER)) {\n"
      "                Log.e(TAG, \"AA143 propagating SABR backoff: %s\", e.getMessage());\n"
      "                throw (IOException) e;\n"
      "            }\n"
      "            Log.e(TAG, \"User doing seek? %s: %s\", e.getClass().getSimpleName(), e.getMessage());\n"
      "            e.printStackTrace();\n"
      "        } finally {\n",
      "Matroska propagate")

patch(chunk,
      "import com.google.android.exoplayer2.source.sabr.parser.SabrStream;\n",
      "import com.google.android.exoplayer2.source.sabr.parser.SabrStream;\n"
      "import com.google.android.exoplayer2.source.sabr.parser.misc.SabrExtractorInput;\n",
      "chunk import")
patch(chunk,
      "    public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e, long blacklistDurationMs) {\n"
      "        Log.e(TAG, \"Chunk load failed: \" + e.getMessage());\n"
      "        if (!cancelable) {\n",
      "    public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e, long blacklistDurationMs) {\n"
      "        Log.e(TAG, \"Chunk load failed: \" + e.getMessage());\n"
      "        if (e.getMessage() != null && e.getMessage().contains(SabrExtractorInput.BACKOFF_MARKER)) {\n"
      "            Log.e(TAG, \"AA143 SABR backoff delegated to retry policy: \" + e.getMessage());\n"
      "            return false;\n"
      "        }\n"
      "        if (!cancelable) {\n",
      "chunk delegate")

patch(policy,
      "import com.liskovsoft.sharedutils.helpers.Helpers;\n",
      "import com.google.android.exoplayer2.source.sabr.parser.misc.SabrExtractorInput;\n"
      "import com.liskovsoft.sharedutils.helpers.Helpers;\n"
      "import com.liskovsoft.sharedutils.mylogger.Log;\n",
      "policy imports")
patch(policy,
      "public class SabrDefaultLoadErrorHandlingPolicy extends DashDefaultLoadErrorHandlingPolicy {\n",
      "public class SabrDefaultLoadErrorHandlingPolicy extends DashDefaultLoadErrorHandlingPolicy {\n"
      "    private static final String TAG = SabrDefaultLoadErrorHandlingPolicy.class.getSimpleName();\n"
      "    private static final long MIN_BACKOFF_MS = 250L;\n"
      "    private static final long MAX_BACKOFF_MS = 10_000L;\n",
      "policy constants")
patch(policy,
      "    public long getRetryDelayMsFor(int dataType, long loadDurationMs, IOException exception, int errorCount) {\n"
      "        if (Helpers.contains(exception.getMessage(), \"Wait 5 sec\")) {\n",
      "    public long getRetryDelayMsFor(int dataType, long loadDurationMs, IOException exception, int errorCount) {\n"
      "        String message = exception.getMessage();\n"
      "        if (Helpers.contains(message, SabrExtractorInput.BACKOFF_MARKER)) {\n"
      "            long delayMs = parseBackoffMs(message);\n"
      "            Log.d(TAG, \"AA143 honouring SABR backoff: \" + delayMs + \" ms, errorCount=\" + errorCount);\n"
      "            return delayMs;\n"
      "        }\n"
      "        if (Helpers.contains(message, \"Wait 5 sec\")) {\n",
      "policy retry")

policy_text = policy.read_text(encoding="utf-8")
if "private static long parseBackoffMs" not in policy_text:
    pos = policy_text.rfind("\n}")
    if pos < 0:
        raise SystemExit("aa1.43: policy class end not found")
    helper = """

    private static long parseBackoffMs(String message) {
        int index = message.indexOf(SabrExtractorInput.BACKOFF_MARKER);
        if (index < 0) return MIN_BACKOFF_MS;
        String raw = message.substring(index + SabrExtractorInput.BACKOFF_MARKER.length()).trim();
        long parsed;
        try {
            parsed = Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            parsed = MIN_BACKOFF_MS;
        }
        return Math.max(MIN_BACKOFF_MS, Math.min(MAX_BACKOFF_MS, parsed));
    }
"""
    policy.write_text(policy_text[:pos] + helper + policy_text[pos:], encoding="utf-8")

# Phone display: keep awake only while the playback fragment is loading/playing/buffering.
patch(fragment,
      "            boolean loading = value.getStatus() == MobileLoadState.Status.LOADING\n"
      "                    || current != null && current.isBuffering();\n"
      "            progress.setVisibility(loading ? View.VISIBLE : View.GONE);\n",
      "            boolean loading = value.getStatus() == MobileLoadState.Status.LOADING\n"
      "                    || current != null && current.isBuffering();\n"
      "            boolean keepScreenOn = loading || current != null && current.isPlaying();\n"
      "            view.setKeepScreenOn(keepScreenOn);\n"
      "            progress.setVisibility(loading ? View.VISIBLE : View.GONE);\n",
      "keep screen state")
patch(fragment,
      "    @Override public void onDestroyView() {\n        ui.removeCallbacks(hideControls);\n",
      "    @Override public void onDestroyView() {\n"
      "        View playbackView = getView();\n"
      "        if (playbackView != null) playbackView.setKeepScreenOn(false);\n"
      "        ui.removeCallbacks(hideControls);\n",
      "clear keep screen")

# Invariants: preserve aa1.42 and do NOT add RefreshPlayerResponse handling.
assert "AA142 SABR seek reset" in chunk.read_text(encoding="utf-8")
assert "AA143 SABR backoff wait" in sabr_input.read_text(encoding="utf-8")
assert "AA143 honouring SABR backoff" in policy.read_text(encoding="utf-8")
assert "view.setKeepScreenOn(keepScreenOn)" in fragment.read_text(encoding="utf-8")
assert "RefreshPlayerResponseSabrPart" not in policy.read_text(encoding="utf-8")
print("aa1.43 applied: no-media SABR backoff retry + playback keep-screen-on; aa1.42 preserved")
