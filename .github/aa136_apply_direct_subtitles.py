#!/usr/bin/env python3
from pathlib import Path
import subprocess


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"aa1.36: anchor not found: {label}")
    return text.replace(old, new, 1)

# Keep the aa1.35 auth-preserving 32.38 recovery changes first.
subprocess.run(["python3", ".github/aa135_apply_official_3238_recovery.py"], check=True)

path = Path("exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/DefaultSabrChunkSource.java")
text = path.read_text()

# Raw YouTube captions are regular timedtext/VTT URLs, not UMP/SABR media chunks.
# The old SABR fork left ExoPlayer's original SingleSampleMediaChunk branch commented
# out and unconditionally created ContainerMediaChunk with a null extractor for text,
# which crashes in ContainerMediaChunk.load(). Restore a direct single-sample path.
text = replace_once(
    text,
    "import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;\n",
    "import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;\n"
    "import com.google.android.exoplayer2.source.chunk.SingleSampleMediaChunk;\n",
    "SingleSampleMediaChunk import")

# Do not advertise timedtext tracks as SABR FormatIds. Their vssId is non-numeric,
# so the generic selector turns it into the bogus -1/-1 FormatId seen in device logs.
text = replace_once(
    text,
    "        this.formatSelector = createFormatSelector(trackType, trackSelection);\n",
    "        this.formatSelector = trackType == C.TRACK_TYPE_TEXT\n"
    "                ? new FormatSelector(\"selected_caption_direct\", true)\n"
    "                : createFormatSelector(trackType, trackSelection);\n",
    "text selector must stay out of SABR")

text = replace_once(
    text,
    "    public void updateTrackSelection(TrackSelection trackSelection) {\n"
    "        this.trackSelection = trackSelection;\n"
    "        this.formatSelector = createFormatSelector(trackType, trackSelection);\n"
    "    }\n",
    "    public void updateTrackSelection(TrackSelection trackSelection) {\n"
    "        this.trackSelection = trackSelection;\n"
    "        this.formatSelector = trackType == C.TRACK_TYPE_TEXT\n"
    "                ? new FormatSelector(\"selected_caption_direct\", true)\n"
    "                : createFormatSelector(trackType, trackSelection);\n"
    "        this.sabrStream.setFormatSelector(this.formatSelector);\n"
    "    }\n",
    "track selection selector refresh")

# A static timedtext file is one subtitle sample/chunk. Once loaded, finish the text
# stream rather than requesting the same VTT over and over.
text = replace_once(
    text,
    "    public void getNextChunk(long playbackPositionUs, long loadPositionUs, List<? extends MediaChunk> queue, ChunkHolder out) {\n"
    "        if (fatalError != null) {\n"
    "            return;\n"
    "        }\n\n",
    "    public void getNextChunk(long playbackPositionUs, long loadPositionUs, List<? extends MediaChunk> queue, ChunkHolder out) {\n"
    "        if (fatalError != null) {\n"
    "            return;\n"
    "        }\n\n"
    "        if (trackType == C.TRACK_TYPE_TEXT && !queue.isEmpty()) {\n"
    "            out.endOfStream = true;\n"
    "            return;\n"
    "        }\n\n",
    "text end-of-stream after one sample")

text = replace_once(
    text,
    "            long firstSegmentNum,\n"
    "            //int maxSegmentCount,\n"
    "            long seekTimeUs) {\n"
    "        boolean isInit = nexChunkIdx == -1;\n",
    "            long firstSegmentNum,\n"
    "            //int maxSegmentCount,\n"
    "            long seekTimeUs) {\n"
    "        if (trackType == C.TRACK_TYPE_TEXT && representationHolder.extractorWrapper == null) {\n"
    "            Representation textRepresentation = representationHolder.representation;\n"
    "            DataSpec textDataSpec = new DataSpec(\n"
    "                    Uri.parse(textRepresentation.baseUrl),\n"
    "                    0,\n"
    "                    C.LENGTH_UNSET,\n"
    "                    textRepresentation.getCacheKey());\n"
    "            long textEndTimeUs = representationHolder.periodDurationUs != C.TIME_UNSET\n"
    "                    ? representationHolder.periodDurationUs : Long.MAX_VALUE;\n"
    "            Log.d(TAG, \"P19_SUBTITLE_DIRECT id=\" + trackFormat.id\n"
    "                    + \" lang=\" + trackFormat.language\n"
    "                    + \" mime=\" + trackFormat.sampleMimeType);\n"
    "            return new SingleSampleMediaChunk(\n"
    "                    dataSource,\n"
    "                    textDataSpec,\n"
    "                    trackFormat,\n"
    "                    trackSelectionReason,\n"
    "                    trackSelectionData,\n"
    "                    0,\n"
    "                    textEndTimeUs,\n"
    "                    firstSegmentNum,\n"
    "                    trackType,\n"
    "                    trackFormat);\n"
    "        }\n\n"
    "        boolean isInit = nexChunkIdx == -1;\n",
    "direct timedtext SingleSampleMediaChunk")

path.write_text(text)
print("aa1.36 applied: auth preserved + official 32.38 recovery + direct timedtext subtitles")
