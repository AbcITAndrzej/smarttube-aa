package com.google.android.exoplayer2.source.sabr.manifest;

import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.offline.FilterableManifest;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.sabr.parser.SabrStream;
import com.google.android.exoplayer2.source.sabr.parser.misc.EnabledTrackTypes;
import com.google.android.exoplayer2.source.sabr.parser.misc.Utils;
import com.google.android.exoplayer2.source.sabr.parser.models.FormatSelector;
import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.BufferedRange;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.ClientAbrState;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.MediaHeader;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.StreamerContext.ClientInfo;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.TimeRange;
import com.google.android.exoplayer2.source.sabr.protos.videostreaming.VideoPlaybackAbrRequest;
import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a SABR media presentation
 */
public class SabrManifest implements FilterableManifest<SabrManifest> {
    private static final String TAG = SabrManifest.class.getSimpleName();
    /**
     * The {@code availabilityStartTime} value in milliseconds since epoch, or {@link C#TIME_UNSET} if
     * not present.
     */
    public final long availabilityStartTimeMs;

    /**
     * The duration of the presentation in milliseconds, or {@link C#TIME_UNSET} if not applicable.
     */
    public final long durationMs;

    /**
     * The {@code minBufferTime} value in milliseconds, or {@link C#TIME_UNSET} if not present.
     */
    public final long minBufferTimeMs;

    /**
     * The {@code timeShiftBufferDepth} value in milliseconds, or {@link C#TIME_UNSET} if not
     * present.
     */
    public final long timeShiftBufferDepthMs;

    /**
     * The {@code suggestedPresentationDelay} value in milliseconds, or {@link C#TIME_UNSET} if not
     * present.
     */
    public final long suggestedPresentationDelayMs;

    /**
     * The {@code publishTime} value in milliseconds since epoch, or {@link C#TIME_UNSET} if
     * not present.
     */
    public final long publishTimeMs;

    public final List<Period> periods;

    /**
     * Whether the manifest has value "dynamic" for the {@code type} attribute.
     */
    public final boolean dynamic;

    /**
     * The {@code minimumUpdatePeriod} value in milliseconds, or {@link C#TIME_UNSET} if not
     * applicable.
     */
    public final long minUpdatePeriodMs;

    private final String videoId;
    private final String serverAbrStreamingUrl;
    private final String videoPlaybackUstreamerConfig;
    private final String poToken;
    private final ClientInfo clientInfo;
    private final Map<Integer, SabrStream> sabrStreams;
    private final Map<String, String> xtagsByFormat;
    // V16 diagnostics: log protocol state only when the selected A/V identity changes.
    private final Map<Integer, String> requestDiagKeys;
    private int sabrRequestNumber = -1;
    private final FormatSelector emptySelector;

    public SabrManifest(
            long availabilityStartTimeMs,
            long durationMs,
            long minBufferTimeMs,
            boolean dynamic,
            long minUpdatePeriodMs,
            long timeShiftBufferDepthMs,
            long suggestedPresentationDelayMs,
            long publishTimeMs,
            List<Period> periods,
            String serverAbrStreamingUrl,
            String videoPlaybackUstreamerConfig,
            String poToken,
            String videoId,
            ClientInfo clientInfo,
            Map<String, String> xtagsByFormat) {
        this.availabilityStartTimeMs = availabilityStartTimeMs;
        this.durationMs = durationMs;
        this.minBufferTimeMs = minBufferTimeMs;
        this.dynamic = dynamic;
        this.minUpdatePeriodMs = minUpdatePeriodMs;
        this.timeShiftBufferDepthMs = timeShiftBufferDepthMs;
        this.suggestedPresentationDelayMs = suggestedPresentationDelayMs;
        this.publishTimeMs = publishTimeMs;
        this.periods = periods;
        this.videoId = videoId;
        this.serverAbrStreamingUrl = serverAbrStreamingUrl;
        this.videoPlaybackUstreamerConfig = videoPlaybackUstreamerConfig;
        this.clientInfo = clientInfo;
        this.poToken = poToken;
        this.sabrStreams = new HashMap<>();
        this.xtagsByFormat = xtagsByFormat != null ? new HashMap<>(xtagsByFormat) : new HashMap<>();
        this.requestDiagKeys = new HashMap<>();
        this.emptySelector = new FormatSelector("ignored", true);
    }

    public final int getPeriodCount() {
        return periods.size();
    }

    public final Period getPeriod(int index) {
        return periods.get(index);
    }

    public final long getPeriodDurationMs(int index) {
        return index == periods.size() - 1
                ? (durationMs == C.TIME_UNSET ? C.TIME_UNSET : (durationMs - periods.get(index).startMs))
                : (periods.get(index + 1).startMs - periods.get(index).startMs);
    }

    public final long getPeriodDurationUs(int index) {
        return C.msToUs(getPeriodDurationMs(index));
    }

    @Override
    public SabrManifest copy(List<StreamKey> streamKeys) {
        return null;
    }

    public final String getVideoId() {
        return videoId;
    }

    static String formatIdentity(String id, long lastModified, String language) {
        return (id != null ? id : "-") + "|" + lastModified + "|" + (language != null ? language : "-");
    }

    private static String formatIdentity(Format format) {
        return formatIdentity(format != null ? format.id : null,
                format != null ? format.lastModified : Format.NO_VALUE,
                format != null ? format.language : null);
    }

    public void applySabrIdentity(FormatSelector selector) {
        if (selector == null || selector.formats.isEmpty() || selector.formatIds.isEmpty()) return;
        int count = Math.min(selector.formats.size(), selector.formatIds.size());
        for (int i = 0; i < count; i++) {
            Format format = selector.formats.get(i);
            String xtags = xtagsByFormat.get(formatIdentity(format));
            if (xtags == null || xtags.isEmpty()) continue;
            FormatId enriched = selector.formatIds.get(i).toBuilder().setXtags(xtags).build();
            selector.formatIds.set(i, enriched);
            Log.d(TAG, "V16_FORMAT_ID_BIND selector=%s itag=%s lmt=%s language=%s xtagsHash=%s",
                    selector.displayName, enriched.getItag(),
                    enriched.hasLastModified() ? enriched.getLastModified() : -1,
                    format != null ? valueOrDash(format.language) : "-",
                    Integer.toHexString(xtags.hashCode()));
        }
    }

    public SabrStream getSabrStream(int trackType) {
        SabrStream sabrStream = sabrStreams.get(trackType);

        if (sabrStream != null) {
            return sabrStream;
        }

        sabrStream = new SabrStream(
                serverAbrStreamingUrl,
                videoPlaybackUstreamerConfig,
                clientInfo,
                -1,
                -1,
                -1,
                poToken,
                false,
                videoId,
                durationMs,
                trackType
        );

        sabrStreams.put(trackType, sabrStream);

        return sabrStream;
    }

    public int getSabrRequestNumber() {
        return sabrRequestNumber;
    }

    public String getRequestUrl(int trackType) {
        SabrStream activeStream = sabrStreams.get(trackType);

        if (activeStream == null) {
            throw new IllegalStateException("Active SabrStream not found for track type " + trackType);
        }

        return Utils.updateQuery(activeStream.getUrl(), "rn", ++sabrRequestNumber);
    }

    public VideoPlaybackAbrRequest createVideoPlaybackAbrRequest(int trackType, boolean isInit) {
        return createVideoPlaybackAbrRequest(trackType, isInit, C.TIME_UNSET);
    }

    public VideoPlaybackAbrRequest createVideoPlaybackAbrRequest(int trackType, boolean isInit, long seekTimeUs) {
        SabrStream activeStream = sabrStreams.get(trackType);

        if (activeStream == null) {
            throw new IllegalStateException("Active SabrStream not found for track type " + trackType);
        }

        Format selectedVideoFormat = getFormatSelector(C.TRACK_TYPE_VIDEO).getSelectedFormat();
        Format selectedAudioFormat = getFormatSelector(C.TRACK_TYPE_AUDIO).getSelectedFormat();
        int height = trackType == C.TRACK_TYPE_VIDEO && selectedVideoFormat != null
                ? selectedVideoFormat.height : -1;
        int bandwidthEstimate = trackType == C.TRACK_TYPE_VIDEO && selectedVideoFormat != null
                ? selectedVideoFormat.bitrate
                : trackType == C.TRACK_TYPE_AUDIO && selectedAudioFormat != null
                ? selectedAudioFormat.bitrate : -1;
        String audioTrackId = trackType == C.TRACK_TYPE_AUDIO && selectedAudioFormat != null
                && selectedAudioFormat.label != null
                && !selectedAudioFormat.label.isEmpty()
                ? selectedAudioFormat.language : null;
        boolean drcEnabled = trackType == C.TRACK_TYPE_AUDIO
                && selectedAudioFormat != null && selectedAudioFormat.isDrc;

        FormatId formatId = getFormatSelector(trackType).getSelectedFormatId();
        long startTimeMs = isInit ? 0 : seekTimeUs != C.TIME_UNSET
                ? seekTimeUs / 1_000 : activeStream.getSegmentStartTimeMs(formatId);

        ClientAbrState.Builder clientAbrStateBuilder = ClientAbrState.newBuilder()
                .setSabrForceMaxNetworkInterruptionDurationMs(0)
                .setPlaybackRate(1)
                .setPlayerTimeMs(startTimeMs)
                .setClientViewportIsFlexible(false)
                .setBandwidthEstimate(bandwidthEstimate)
                .setDrcEnabled(drcEnabled)
                .setEnabledTrackTypesBitfield(trackType == C.TRACK_TYPE_VIDEO
                        ? EnabledTrackTypes.VIDEO_ONLY : EnabledTrackTypes.AUDIO_ONLY);

        if (audioTrackId != null && !audioTrackId.isEmpty()) {
            clientAbrStateBuilder.setAudioTrackId(audioTrackId);
        }


        if (height != -1) {
            clientAbrStateBuilder
                    .setStickyResolution(height)
                    .setLastManualSelectedResolution(height);
        }

        ClientAbrState clientAbrState = clientAbrStateBuilder.build();

        Pair<List<BufferedRange>, FormatId> bufferRanges = addBufferingInfoToAbrRequest(trackType);

        List<FormatId> selectedFormats = createSelectedFormatIds(trackType);

        if (isInit) {
            selectedFormats.clear();
        }

        if (bufferRanges.second != null) {
            selectedFormats.add(0, bufferRanges.second);
        }

        logRequestStateIfChanged(trackType, isInit, audioTrackId, drcEnabled,
                getFormatSelector(C.TRACK_TYPE_AUDIO).getSelectedFormatId(),
                getFormatSelector(C.TRACK_TYPE_VIDEO).getSelectedFormatId(),
                selectedFormats, bufferRanges.first, bufferRanges.second);

        return VideoPlaybackAbrRequest.newBuilder()
                .setClientAbrState(clientAbrState)
                .addAllPreferredVideoFormatIds(getFormatSelector(C.TRACK_TYPE_VIDEO).formatIds)
                .addAllPreferredAudioFormatIds(getFormatSelector(C.TRACK_TYPE_AUDIO).formatIds)
                .addAllPreferredSubtitleFormatIds(getFormatSelector(C.TRACK_TYPE_TEXT).formatIds)
                .addAllSelectedFormatIds(selectedFormats)
                .addAllBufferedRanges(bufferRanges.first)
                .setVideoPlaybackUstreamerConfig(
                        ByteString.copyFrom(
                                Base64.decode(videoPlaybackUstreamerConfig, Base64.URL_SAFE)
                        )
                )
                .setStreamerContext(createStreamerContext(trackType))
                .build();
    }

    /**
     * Adds buffering information to the ABR request for all active formats.<br/><br/>
     *
     * NOTE:
     * On the web, mobile, and TV clients, buffered ranges in combination to player time is what dictates the segments you get.
     * In our case, we are cheating a bit by abusing the player time field (in clientAbrState), setting it to the exact start
     * time value of the segment we want, while YouTube simply uses the actual player time.<br/><br/>
     *
     * We don't have to fully replicate this behavior for two reasons:
     * 1. The SABR server will only send so much segments for a given player time. That means players like Shaka would
     * not be able to buffer more than what the server thinks is enough. It would behave like YouTube's.
     * 2. We don't have to know what segment a buffered range starts/ends at. It is easy to do in Shaka, but not in other players.
     *
     * @return The format to discard (if any) - typically formats that are active but not currently requested.
     */
    private Pair<List<BufferedRange>, FormatId> addBufferingInfoToAbrRequest(int trackType) {
        SabrStream activeStream = sabrStreams.get(trackType);

        if (activeStream == null) {
            throw new IllegalStateException("Active SabrStream not found for track type " + trackType);
        }

        FormatId audioFormat = getFormatSelector(C.TRACK_TYPE_AUDIO).getSelectedFormatId();
        FormatId videoFormat = getFormatSelector(C.TRACK_TYPE_VIDEO).getSelectedFormatId();

        FormatId formatToDiscard = null;
        List<BufferedRange> bufferedRanges = new ArrayList<>();

        FormatId currentFormat = trackType == C.TRACK_TYPE_VIDEO ? videoFormat : audioFormat;

        for (FormatId activeFormat : new FormatId[]{videoFormat, audioFormat}) {
            if (activeFormat == null) {
                continue;
            }

            boolean shouldDiscard = currentFormat == null || !currentFormat.equals(activeFormat);
            MediaHeader initializedFormat = getInitializedFormat(activeFormat);

            BufferedRange bufferedRange = shouldDiscard ? createFullBufferRange(activeFormat) : createPartialBufferRange(initializedFormat);

            if (bufferedRange != null) {
                bufferedRanges.add(bufferedRange);

                if (shouldDiscard) {
                    formatToDiscard = activeFormat;
                }
            }
        }

        return new Pair<>(bufferedRanges, formatToDiscard);
    }

    private @Nullable MediaHeader getInitializedFormat(FormatId formatId) {
        MediaHeader initializedFormat = null;

        for (SabrStream sabrStream : sabrStreams.values()) {
            MediaHeader mediaHeader = sabrStream.getInitializedFormat(formatId);

            if (mediaHeader != null) {
                initializedFormat = mediaHeader;
                break;
            }
        }

        return initializedFormat;
    }

    private @NonNull FormatSelector getFormatSelector(int trackType) {
        SabrStream sabrStream = sabrStreams.get(trackType);

        if (sabrStream == null) {
            return emptySelector;
        }

        return sabrStream.getFormatSelector();
    }

    /**
     * Creates a bogus buffered range for a format. Used when we want to signal to the server to not send any
     * segments for this format.
     * @param format - The format to create a full buffer range for.
     * @return A BufferedRange object indicating the entire format is buffered.
     */
    private BufferedRange createFullBufferRange(@NonNull FormatId format) {
        return BufferedRange.newBuilder()
                .setFormatId(format)
                .setDurationMs(Integer.MAX_VALUE)
                .setStartTimeMs(0)
                .setStartSegmentIndex(Integer.MAX_VALUE)
                .setEndSegmentIndex(Integer.MAX_VALUE)
                .setTimeRange(TimeRange.newBuilder()
                        .setDurationTicks(Integer.MAX_VALUE)
                        .setStartTicks(0)
                        .setTimescale(1_000)
                        .build())
                .build();
    }

    /**
     * Creates a buffered range representing a partially buffered format.
     * @param initializedFormat - The format with initialization data.
     * @return A BufferedRange object with segment information, or null if no metadata is available.
     */
    private BufferedRange createPartialBufferRange(MediaHeader initializedFormat) {
        if (initializedFormat == null) {
            return null;
        }

        int sequenceNumber = initializedFormat.hasSequenceNumber() ? initializedFormat.getSequenceNumber() : 1;
        TimeRange timeRange = initializedFormat.hasTimeRange() ? initializedFormat.getTimeRange() : null;
        int timeScale = timeRange != null && timeRange.hasTimescale() ? timeRange.getTimescale() : 1_000;
        long startMs = initializedFormat.hasStartMs() ? initializedFormat.getStartMs() : 0;
        long durationMs = initializedFormat.hasDurationMs() ? initializedFormat.getDurationMs() : 0;
        return BufferedRange.newBuilder()
                .setFormatId(initializedFormat.getFormatId())
                .setStartSegmentIndex(sequenceNumber) // should be the real start position
                .setEndSegmentIndex(sequenceNumber) // should be the real start position
                .setStartTimeMs(0) // not used
                .setDurationMs(durationMs)
                .setTimeRange(TimeRange.newBuilder()
                        .setTimescale(timeScale)
                        .setStartTicks(0) // not used
                        .setDurationTicks(durationMs)
                        .build())
                .build();
    }

    private List<FormatId> createSelectedFormatIds(int trackType) {
        FormatSelector formatSelector = getFormatSelector(trackType);

        return new ArrayList<>(formatSelector.formatIds);
    }

    private void logRequestStateIfChanged(
            int trackType,
            boolean isInit,
            String audioTrackId,
            boolean drcEnabled,
            FormatId audioFormat,
            FormatId videoFormat,
            List<FormatId> selectedFormats,
            List<BufferedRange> bufferedRanges,
            FormatId discardFormat) {
        String key = trackType + "|" + formatKey(audioFormat) + "|" + formatKey(videoFormat)
                + "|" + audioTrackId + "|" + drcEnabled + "|" + formatKey(discardFormat);
        String previous = requestDiagKeys.get(trackType);
        if (key.equals(previous)) {
            return;
        }
        requestDiagKeys.put(trackType, key);

        Log.d(TAG, "V16_SABR_REQ lane=" + trackTypeName(trackType)
                + " protocol=" + (trackType == C.TRACK_TYPE_VIDEO ? "VIDEO_ONLY" : "AUDIO_ONLY")
                + " init=" + isInit
                + " rn=" + sabrRequestNumber
                + " audioTrackId=" + valueOrDash(audioTrackId)
                + " audio=" + formatKey(audioFormat)
                + " video=" + formatKey(videoFormat)
                + " drc=" + drcEnabled
                + " preferredAudio=" + getFormatSelector(C.TRACK_TYPE_AUDIO).formatIds.size()
                + " preferredVideo=" + getFormatSelector(C.TRACK_TYPE_VIDEO).formatIds.size()
                + " selected=" + formatList(selectedFormats)
                + " buffered=" + bufferedRangeList(bufferedRanges)
                + " companionDiscard=" + formatKey(discardFormat));
    }

    private static String trackTypeName(int trackType) {
        if (trackType == C.TRACK_TYPE_AUDIO) return "AUDIO";
        if (trackType == C.TRACK_TYPE_VIDEO) return "VIDEO";
        if (trackType == C.TRACK_TYPE_TEXT) return "TEXT";
        return Integer.toString(trackType);
    }

    private static String valueOrDash(String value) {
        return value != null && !value.isEmpty() ? value : "-";
    }

    private static String formatKey(FormatId formatId) {
        if (formatId == null) return "-";
        return formatId.getItag() + "/"
                + (formatId.hasLastModified() ? formatId.getLastModified() : 0)
                + (formatId.hasXtags() && !formatId.getXtags().isEmpty()
                ? "/x" + Integer.toHexString(formatId.getXtags().hashCode()) : "");
    }

    private static String formatList(List<FormatId> formats) {
        if (formats == null || formats.isEmpty()) return "[]";
        StringBuilder result = new StringBuilder("[");
        for (FormatId format : formats) {
            if (result.length() > 1) result.append(',');
            result.append(formatKey(format));
        }
        return result.append(']').toString();
    }

    private static String bufferedRangeList(List<BufferedRange> ranges) {
        if (ranges == null || ranges.isEmpty()) return "[]";
        StringBuilder result = new StringBuilder("[");
        for (BufferedRange range : ranges) {
            if (result.length() > 1) result.append(',');
            result.append(range.hasFormatId() ? formatKey(range.getFormatId()) : "-")
                    .append(':')
                    .append(range.getDurationMs());
        }
        return result.append(']').toString();
    }

    private StreamerContext createStreamerContext(int trackType) {
        SabrStream activeStream = sabrStreams.get(trackType);

        if (activeStream == null) {
            throw new IllegalStateException("Active SabrStream not found for track type " + trackType);
        }

        return activeStream.createStreamerContext();
    }
}
