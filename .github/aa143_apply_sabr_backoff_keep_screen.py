#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"aa1.43: anchor not found: {label}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


SABR_INPUT = ROOT / (
    "exoplayer-amzn-2.10.6/library/sabr/src/main/java/"
    "com/google/android/exoplayer2/source/sabr/parser/misc/SabrExtractorInput.java"
)
MATROSKA = ROOT / (
    "exoplayer-amzn-2.10.6/library/sabr/src/main/java/"
    "com/google/android/exoplayer2/source/sabr/parser/adapter/SabrMatroskaAdapter.java"
)
CHUNK_SOURCE = ROOT / (
    "exoplayer-amzn-2.10.6/library/sabr/src/main/java/"
    "com/google/android/exoplayer2/source/sabr/DefaultSabrChunkSource.java"
)
ERROR_POLICY = ROOT / (
    "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/errors/"
    "SabrDefaultLoadErrorHandlingPolicy.java"
)
PLAYBACK_FRAGMENT = ROOT / (
    "smarttubetv/src/stmobile/java/com/liskovsoft/smartyoutubetv2/tv/ui/mobile/"
    "nativeui/fragment/MobilePlaybackFragment.java"
)

# ---------------------------------------------------------------------------
# 1) SABR backoff: port ONLY the safe NextRequestPolicy.backoff behavior.
#    The upstream 66cf8bb patch also handled RefreshPlayerResponse and was later
#    reverted wholesale. We intentionally do not port reload/fatal handling here.
# ---------------------------------------------------------------------------
replace_once(
    SABR_INPUT,
    "    private int remaining;\n    private MediaSegmentDataSabrPart data;\n",
    "    private int remaining;\n    private MediaSegmentDataSabrPart data;\n    private boolean mediaSeen;\n",
    "SabrExtractorInput mediaSeen field",
)

replace_once(
    SABR_INPUT,
    "        startPosition = position;\n        remaining = C.LENGTH_UNSET;\n",
    "        startPosition = position;\n        remaining = C.LENGTH_UNSET;\n        mediaSeen = false;\n",
    "SabrExtractorInput init mediaSeen",
)

replace_once(
    SABR_INPUT,
    "    private void fetchData() {\n",
    "    private void fetchData() throws IOException {\n",
    "SabrExtractorInput fetchData throws",
)

replace_once(
    SABR_INPUT,
    "            if (sabrPart == null) {\n                break;\n            }\n",
    "            if (sabrPart == null) {\n"
    "                // Server may return only NextRequestPolicy with no media. Retrying\n"
    "                // immediately burns the requested wait window and can create hundreds\n"
    "                // of init requests. Surface only this backoff signal to ExoPlayer so\n"
    "                // its normal load-error scheduler performs a delayed retry.\n"
    "                if (!mediaSeen) {\n"
    "                    int backoffMs = sabrStream.getBackoffTimeMs();\n"
    "                    if (backoffMs > 0) {\n"
    "                        String msg = BACKOFF_MARKER + backoffMs;\n"
    "                        Log.e(TAG, \"AA143 SABR backoff wait: %s ms\", backoffMs);\n"
    "                        throw new IOException(msg);\n"
    "                    }\n"
    "                }\n"
    "                break;\n"
    "            }\n",
    "SabrExtractorInput no-media backoff",
)

replace_once(
    SABR_INPUT,
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
    "SabrExtractorInput media seen",
)

replace_once(
    SABR_INPUT,
    "    private static void throwShouldNotBeCalled() {\n",
    "    public static final String BACKOFF_MARKER = \"AA143 SABR backoff requested, ms=\";\n\n"
    "    private static void throwShouldNotBeCalled() {\n",
    "SabrExtractorInput backoff marker",
)

# Matroska adapter currently swallows every extractor exception. Let this one
# control signal reach ExoPlayer's load policy; keep all other legacy behavior.
replace_once(
    MATROSKA,
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
    "Matroska propagate backoff",
)

replace_once(
    CHUNK_SOURCE,
    "import com.google.android.exoplayer2.source.sabr.parser.SabrStream;\n",
    "import com.google.android.exoplayer2.source.sabr.parser.SabrStream;\n"
    "import com.google.android.exoplayer2.source.sabr.parser.misc.SabrExtractorInput;\n",
    "DefaultSabrChunkSource backoff import",
)

replace_once(
    CHUNK_SOURCE,
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
    "DefaultSabrChunkSource delegate backoff",
)

replace_once(
    ERROR_POLICY,
    "package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;\n\n"
    "import com.liskovsoft.sharedutils.helpers.Helpers;\n",
    "package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;\n\n"
    "import com.google.android.exoplayer2.source.sabr.parser.misc.SabrExtractorInput;\n"
    "import com.liskovsoft.sharedutils.helpers.Helpers;\n"
    "import com.liskovsoft.sharedutils.mylogger.Log;\n",
    "Sabr error policy imports",
)

replace_once(
    ERROR_POLICY,
    "public class SabrDefaultLoadErrorHandlingPolicy extends DashDefaultLoadErrorHandlingPolicy {\n",
    "public class SabrDefaultLoadErrorHandlingPolicy extends DashDefaultLoadErrorHandlingPolicy {\n"
    "    private static final String TAG = SabrDefaultLoadErrorHandlingPolicy.class.getSimpleName();\n"
    "    private static final long MIN_BACKOFF_MS = 250L;\n"
    "    private static final long MAX_BACKOFF_MS = 10_000L;\n",
    "Sabr error policy constants",
)

replace_once(
    ERROR_POLICY,
    "    public long getRetryDelayMsFor(int dataType, long loadDurationMs, IOException exception, int errorCount) {\n"
    "        if (Helpers.contains(exception.getMessage(), \"Wait 5 sec\")) {\n",
    "    public long getRetryDelayMsFor(int dataType, long loadDurationMs, IOException exception, int errorCount) {\n"
    "        String message = exception.getMessage();\n\n"
    "        if (Helpers.contains(message, SabrExtractorInput.BACKOFF_MARKER)) {\n"
    "            long delayMs = parseBackoffMs(message);\n"
    "            Log.d(TAG, \"AA143 honouring SABR backoff: \" + delayMs\n"
    "                    + \" ms, errorCount=\" + errorCount);\n"
    "            return delayMs;\n"
    "        }\n\n"
    "        if (Helpers.contains(message, \"Wait 5 sec\")) {\n",
    "Sabr error policy retry backoff",
)

replace_once(
    ERROR_POLICY,
    "        return super.getRetryDelayMsFor(dataType, loadDurationMs, exception, errorCount);\n"
    "    }\n    \n}\n",
    "        return super.getRetryDelayMsFor(dataType, loadDurationMs, exception, errorCount);\n"
    "    }\n\n"
    "    private static long parseBackoffMs(String message) {\n"
    "        int index = message.indexOf(SabrExtractorInput.BACKOFF_MARKER);\n"
    "        if (index < 0) return MIN_BACKOFF_MS;\n"
    "        String raw = message.substring(index + SabrExtractorInput.BACKOFF_MARKER.length()).trim();\n"
    "        long parsed;\n"
    "        try {\n"
    "            parsed = Long.parseLong(raw);\n"
    "        } catch (NumberFormatException ignored) {\n"
    "            parsed = MIN_BACKOFF_MS;\n"
    "        }\n"
    "        return Math.max(MIN_BACKOFF_MS, Math.min(MAX_BACKOFF_MS, parsed));\n"
    "    }\n"
    "    \n}\n",
    "Sabr error policy parse helper",
)

# ---------------------------------------------------------------------------
# 2) Keep the phone display awake only while playback is active/buffering.
#    View.setKeepScreenOn() is scoped to the visible playback fragment, so leaving
#    the player restores the user's normal system screen timeout automatically.
# ---------------------------------------------------------------------------
replace_once(
    PLAYBACK_FRAGMENT,
    "        viewModel.getState().observe(getViewLifecycleOwner(), value -> {\n"
    "            MobilePlaybackSnapshot current = value.getData();\n"
    "            boolean loading = value.getStatus() == MobileLoadState.Status.LOADING\n"
    "                    || current != null && current.isBuffering();\n"
    "            progress.setVisibility(loading ? View.VISIBLE : View.GONE);\n",
    "        viewModel.getState().observe(getViewLifecycleOwner(), value -> {\n"
    "            MobilePlaybackSnapshot current = value.getData();\n"
    "            boolean loading = value.getStatus() == MobileLoadState.Status.LOADING\n"
    "                    || current != null && current.isBuffering();\n"
    "            boolean keepScreenOn = loading || current != null && current.isPlaying();\n"
    "            view.setKeepScreenOn(keepScreenOn);\n"
    "            progress.setVisibility(loading ? View.VISIBLE : View.GONE);\n",
    "MobilePlaybackFragment keep screen on state",
)

replace_once(
    PLAYBACK_FRAGMENT,
    "    @Override public void onDestroyView() {\n"
    "        ui.removeCallbacks(hideControls);\n",
    "    @Override public void onDestroyView() {\n"
    "        View playbackView = getView();\n"
    "        if (playbackView != null) playbackView.setKeepScreenOn(false);\n"
    "        ui.removeCallbacks(hideControls);\n",
    "MobilePlaybackFragment clear keep screen on",
)

# Verify the exact intended scope. No RefreshPlayerResponse handling is added.
checks = {
    SABR_INPUT: [
        'BACKOFF_MARKER = "AA143 SABR backoff requested, ms="',
        "AA143 SABR backoff wait",
        "mediaSeen = true",
    ],
    MATROSKA: ["AA143 propagating SABR backoff"],
    CHUNK_SOURCE: ["AA143 SABR backoff delegated to retry policy", "AA142 SABR seek reset"],
    ERROR_POLICY: ["AA143 honouring SABR backoff", "parseBackoffMs"],
    PLAYBACK_FRAGMENT: ["view.setKeepScreenOn(keepScreenOn)", "playbackView.setKeepScreenOn(false)"],
}
for path, markers in checks.items():
    final_text = path.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in final_text:
            raise SystemExit(f"aa1.43: missing invariant in {path.name}: {marker}")

for forbidden in ("RELOAD_MARKER", "RefreshPlayerResponseSabrPart", "Player response reload requested"):
    if forbidden in ERROR_POLICY.read_text(encoding="utf-8"):
        raise SystemExit(f"aa1.43: forbidden reload handling leaked into error policy: {forbidden}")

print("aa1.43 applied: SABR no-media backoff retry + playback keep-screen-on; aa1.42 seek reset preserved")
