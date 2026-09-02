from pathlib import Path
import re

ROOT = Path('.')

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def write(path, text):
    (ROOT / path).write_text(text, encoding='utf-8')

def replace(path, old, new, expected=1):
    text = read(path)
    n = text.count(old)
    if n != expected:
        raise SystemExit(f'{path}: expected {expected} exact matches, got {n} for {old[:100]!r}')
    write(path, text.replace(old, new))

# 1) Parse complete modern YouTube audio identity from /player.
p = 'MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/videoinfo/models/formats/VideoFormat.java'
replace(p, '    private String mXtags;\n', '    @JsonPath("$.xtags")\n    private String mXtags;\n')
replace(p,
'''    @JsonPath("$.audioTrack.audioIsDefault")
    private boolean mAudioTrackIsDefault;
    private VideoUrlHolder mUrlHolder;
''',
'''    @JsonPath("$.audioTrack.audioIsDefault")
    private boolean mAudioTrackIsDefault;
    @JsonPath("$.audioTrack.isAutoDubbed")
    private boolean mAudioTrackIsAutoDubbed;
    private VideoUrlHolder mUrlHolder;
''')
replace(p,
'''    public boolean isAudioTrackDefault() {
        return mAudioTrackIsDefault;
    }

    public String getContentLength() {
''',
'''    public boolean isAudioTrackDefault() {
        return mAudioTrackIsDefault;
    }

    public boolean isAudioTrackAutoDubbed() {
        return mAudioTrackIsAutoDubbed;
    }

    public String getContentLength() {
''')

# 2) Make logical audio identity available through every MediaFormat implementation.
p = 'MediaServiceCore/mediaserviceinterfaces/src/main/java/com/liskovsoft/mediaserviceinterfaces/data/MediaFormat.java'
replace(p,
'''    String getITag();
    boolean isDrc();

    // DASH
''',
'''    String getITag();
    boolean isDrc();
    default boolean isVb() { return false; }
    default String getAudioTrackId() { return getLanguage(); }
    default String getAudioTrackDisplayName() { return null; }
    default boolean isAudioTrackDefault() { return false; }
    default boolean isAudioTrackAutoDubbed() { return false; }

    // DASH
''')

# 3) Preserve xtags/default/dub metadata in the legacy V2 MediaFormat bridge.
p = 'MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/data/YouTubeMediaFormat.java'
replace(p,
'''    private String mITag;
    private boolean mIsDrc;
    private String mClen;
''',
'''    private String mITag;
    private boolean mIsDrc;
    private boolean mIsVb;
    private String mAudioTrackId;
    private String mAudioTrackDisplayName;
    private boolean mAudioTrackDefault;
    private boolean mAudioTrackAutoDubbed;
    private String mClen;
''')
replace(p,
'''        mediaFormat.mITag = iTag;
        mediaFormat.mIsDrc = format.isDrc();
        mediaFormat.mClen = format.getContentLength();
''',
'''        mediaFormat.mITag = iTag;
        mediaFormat.mIsDrc = format.isDrc();
        mediaFormat.mIsVb = format.isVb();
        mediaFormat.mAudioTrackId = format.getAudioTrackId();
        mediaFormat.mAudioTrackDisplayName = format.getAudioTrackDisplayName();
        mediaFormat.mAudioTrackDefault = format.isAudioTrackDefault();
        mediaFormat.mAudioTrackAutoDubbed = format.isAudioTrackAutoDubbed();
        mediaFormat.mXtags = format.getXtags();
        mediaFormat.mClen = format.getContentLength();
''')
replace(p,
'''    public boolean isDrc() {
        return mIsDrc;
    }

    @Override
    public String getClen() {
''',
'''    public boolean isDrc() {
        return mIsDrc;
    }

    @Override
    public boolean isVb() {
        return mIsVb;
    }

    @Override
    public String getAudioTrackId() {
        return mAudioTrackId;
    }

    @Override
    public String getAudioTrackDisplayName() {
        return mAudioTrackDisplayName;
    }

    @Override
    public boolean isAudioTrackDefault() {
        return mAudioTrackDefault;
    }

    @Override
    public boolean isAudioTrackAutoDubbed() {
        return mAudioTrackAutoDubbed;
    }

    @Override
    public String getClen() {
''')

# 4) Do the same for the Innertube path, so the fix isn't V2-only.
p = 'MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/innertube/models/PlayerResult.kt'
replace(p,
'''    val qualityLabel: String?,
    val projectionType: String?,
    val averageBitrate: Int?,
''',
'''    val qualityLabel: String?,
    val projectionType: String?,
    val xtags: String?,
    val averageBitrate: Int?,
''')
replace(p,
'''    val trackAbsoluteLoudnessLkfs: Float?,
    val isDrc: Boolean?,
    /**
''',
'''    val trackAbsoluteLoudnessLkfs: Float?,
    val isDrc: Boolean?,
    val isVb: Boolean?,
    val audioTrack: AudioTrack?,
    /**
''')
replace(p,
'''    data class Range(
        val start: String?,
        val end: String?
    )
}
''',
'''    data class Range(
        val start: String?,
        val end: String?
    )

    data class AudioTrack(
        val displayName: String?,
        val id: String?,
        val audioIsDefault: Boolean?,
        val isAutoDubbed: Boolean?
    )
}
''')

p = 'MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/innertube/impl/MediaFormatImpl.kt'
replace(p,
'''    private val _isDrc by lazy { streamingFormat.isDrc ?: false }
    private val _clen by lazy { streamingFormat.contentLength }
''',
'''    private val _isDrc by lazy { streamingFormat.isDrc ?: false }
    private val _isVb by lazy { streamingFormat.isVb ?: false }
    private val _audioTrackId by lazy { streamingFormat.audioTrack?.id }
    private val _audioTrackDisplayName by lazy { streamingFormat.audioTrack?.displayName }
    private val _audioTrackDefault by lazy { streamingFormat.audioTrack?.audioIsDefault ?: false }
    private val _audioTrackAutoDubbed by lazy { streamingFormat.audioTrack?.isAutoDubbed ?: false }
    private val _clen by lazy { streamingFormat.contentLength }
''')
replace(p,
'''    override fun isDrc() = _isDrc

    override fun getClen() = _clen
''',
'''    override fun isDrc() = _isDrc

    override fun isVb() = _isVb

    override fun getAudioTrackId() = _audioTrackId

    override fun getAudioTrackDisplayName() = _audioTrackDisplayName

    override fun isAudioTrackDefault() = _audioTrackDefault

    override fun isAudioTrackAutoDubbed() = _audioTrackAutoDubbed

    override fun getClen() = _clen
''')
replace(p, '    override fun getXtags(): String? = null\n', '    override fun getXtags() = streamingFormat.xtags\n')
replace(p, '    override fun getLanguage() = urlHolder.getLanguage()\n', '    override fun getLanguage() = _audioTrackId ?: urlHolder.getLanguage()\n')

# 5) Rich diagnostics without exposing full xtags.
p = 'MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/videoinfo/VideoInfoServiceBase.java'
replace(p,
'''                details.append(variant.getITag())
                        .append('@').append(valueOrDash(variant.getLmt()))
                        .append(variant.isDrc() ? "/DRC" : "")
                        .append(variant.isVb() ? "/VB" : "");
            }
            Log.d(TAG, "V15_AUDIO_TRACK id=%s name=%s language=%s default=%s variants=[%s]",
                    entry.getKey(),
                    first != null ? valueOrDash(first.getAudioTrackDisplayName()) : "-",
                    first != null ? valueOrDash(first.getLanguage()) : "-",
                    first != null && first.isAudioTrackDefault(),
                    details.toString());
''',
'''                String xtags = variant.getXtags();
                details.append(variant.getITag())
                        .append('@').append(valueOrDash(variant.getLmt()))
                        .append("/x").append(xtags != null ? Integer.toHexString(xtags.hashCode()) : "-")
                        .append(variant.isDrc() ? "/DRC" : "")
                        .append(variant.isVb() ? "/VB" : "");
            }
            Log.d(TAG, "V16_AUDIO_TRACK id=%s name=%s language=%s default=%s autoDubbed=%s variants=[%s]",
                    entry.getKey(),
                    first != null ? valueOrDash(first.getAudioTrackDisplayName()) : "-",
                    first != null ? valueOrDash(first.getLanguage()) : "-",
                    first != null && first.isAudioTrackDefault(),
                    first != null && first.isAudioTrackAutoDubbed(),
                    details.toString());
''')

# 6) Manifest parser: deterministic logical groups, true default flags, and xtags registry.
p = 'exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/manifest/SabrManifestParser.java'
replace(p, 'import java.util.HashMap;\n', 'import java.util.HashMap;\nimport java.util.LinkedHashMap;\n')
replace(p,
'''    private Map<String, Set<MediaFormat>> mMP4Audios;
    private Map<String, Set<MediaFormat>> mWEBMAudios;
    private List<MediaSubtitle> mSubs;
''',
'''    private Map<String, Set<MediaFormat>> mMP4Audios;
    private Map<String, Set<MediaFormat>> mWEBMAudios;
    private Map<String, String> mXtagsByFormat;
    private List<MediaSubtitle> mSubs;
''')
replace(p,
'''        mMP4Audios = new HashMap<>();
        mWEBMAudios = new HashMap<>();
        mSubs = new ArrayList<>();
''',
'''        mMP4Audios = new LinkedHashMap<>();
        mWEBMAudios = new LinkedHashMap<>();
        mXtagsByFormat = new HashMap<>();
        mSubs = new ArrayList<>();
''')
replace(p,
'''                formatInfo.getPoToken(),
                formatInfo.getVideoId(),
                createClientInfo(formatInfo));
''',
'''                formatInfo.getPoToken(),
                formatInfo.getVideoId(),
                createClientInfo(formatInfo),
                mXtagsByFormat);
''')
replace(p,
'''    private RepresentationInfo parseRepresentation(MediaFormat mediaFormat) {
        int roleFlags = C.ROLE_FLAG_MAIN;
        int selectionFlags = C.SELECTION_FLAG_DEFAULT;
        String id = mediaFormat.getITag();
        int bandwidth = Helpers.parseInt(mediaFormat.getBitrate(), Format.NO_VALUE);
        String mimeType = MediaFormatUtils.extractMimeType(mediaFormat);
''',
'''    private RepresentationInfo parseRepresentation(MediaFormat mediaFormat) {
        int roleFlags = C.ROLE_FLAG_MAIN;
        String id = mediaFormat.getITag();
        int bandwidth = Helpers.parseInt(mediaFormat.getBitrate(), Format.NO_VALUE);
        String mimeType = MediaFormatUtils.extractMimeType(mediaFormat);
        boolean isAudio = mimeType != null && mimeType.startsWith("audio/");
        int selectionFlags = !isAudio || mediaFormat.getAudioTrackId() == null
                ? C.SELECTION_FLAG_DEFAULT
                : mediaFormat.isAudioTrackDefault() ? C.SELECTION_FLAG_DEFAULT : 0;
''')
replace(p,
'''                        isDrc,
                        lastModified);

        SegmentBase segmentBase = null;
''',
'''                        isDrc,
                        lastModified);

        String xtags = mediaFormat.getXtags();
        if (xtags != null && !xtags.isEmpty()) {
            mXtagsByFormat.put(SabrManifest.formatIdentity(id, lastModified, language), xtags);
        }

        SegmentBase segmentBase = null;
''')

# 7) FormatSelector: exact modern identity is itag+xtags; only fall back when identity is absent.
p = 'exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/parser/models/FormatSelector.java'
replace(p, 'import com.liskovsoft.sharedutils.helpers.Helpers;\n', 'import com.liskovsoft.sharedutils.helpers.Helpers;\nimport com.liskovsoft.sharedutils.mylogger.Log;\n')
replace(p, 'public class FormatSelector {\n', 'public class FormatSelector {\n    private static final String TAG = FormatSelector.class.getSimpleName();\n')
replace(p,
'''    public boolean match(FormatId formatId, String mimeType) {
        return formatIds.contains(formatId)
                || (formatIds.isEmpty() && getMimePrefix() != null && mimeType != null && mimeType.toLowerCase().startsWith(getMimePrefix()))
                || Helpers.findFirst(formatIds, fmt -> fmt.hasItag() && formatId.hasItag() && fmt.getItag() == formatId.getItag()) != null;
    }
''',
'''    public boolean match(FormatId formatId, String mimeType) {
        if (formatId == null) return false;
        if (formatIds.contains(formatId)) return true;
        if (formatIds.isEmpty()) {
            return getMimePrefix() != null && mimeType != null
                    && mimeType.toLowerCase().startsWith(getMimePrefix());
        }

        for (FormatId expected : formatIds) {
            if (!expected.hasItag() || !formatId.hasItag() || expected.getItag() != formatId.getItag()) continue;

            if (expected.hasXtags() && formatId.hasXtags()) {
                if (expected.getXtags().equals(formatId.getXtags())) return true;
                Log.w(TAG, "V16_FORMAT_ID_REJECT itag=%s expectedX=%s actualX=%s",
                        expected.getItag(), Integer.toHexString(expected.getXtags().hashCode()),
                        Integer.toHexString(formatId.getXtags().hashCode()));
                continue;
            }

            if (expected.hasLastModified() && formatId.hasLastModified()) {
                if (expected.getLastModified() == formatId.getLastModified()) return true;
                continue;
            }

            return true;
        }
        return false;
    }
''')

# 8) SabrProcessor/SabrStream: initialized state keyed by full FormatId, not bare itag.
p = 'exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/parser/SabrProcessor.java'
replace(p, 'import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatInitializationMetadata;\n', 'import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;\nimport com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatInitializationMetadata;\n')
replace(p, '    private final Map<Integer, MediaHeader> initializedFormats;\n', '    private final Map<String, MediaHeader> initializedFormats;\n')
replace(p, '            initializedFormats.put(segment.mediaHeader.getItag(), segment.mediaHeader);\n', '            initializedFormats.put(segment.mediaHeader.getFormatId().toString(), segment.mediaHeader);\n')
replace(p,
'''    public long getSegmentStartTimeMs(int iTag) {
        MediaHeader mediaHeader = initializedFormats.get(iTag);

        if (mediaHeader == null || mediaHeader.getStartMs() == -1) {
            return 0;
        }

        return mediaHeader.getStartMs() + mediaHeader.getDurationMs();
    }

    public long getSegmentDurationMs(int iTag) {
        MediaHeader mediaHeader = initializedFormats.get(iTag);

        if (mediaHeader == null) {
            return 0;
        }

        return mediaHeader.getDurationMs();
    }
''',
'''    public long getSegmentStartTimeMs(FormatId formatId) {
        MediaHeader mediaHeader = formatId != null ? initializedFormats.get(formatId.toString()) : null;

        if (mediaHeader == null || mediaHeader.getStartMs() == -1) return 0;
        return mediaHeader.getStartMs() + mediaHeader.getDurationMs();
    }

    public long getSegmentDurationMs(FormatId formatId) {
        MediaHeader mediaHeader = formatId != null ? initializedFormats.get(formatId.toString()) : null;
        return mediaHeader != null ? mediaHeader.getDurationMs() : 0;
    }
''')
replace(p, '    public @NonNull Map<Integer, MediaHeader> getInitializedFormats() {\n', '    public @NonNull Map<String, MediaHeader> getInitializedFormats() {\n')
replace(p,
'''    public void reset(int iTag) {
        MediaHeader mediaHeader = initializedFormats.get(iTag);

        if (mediaHeader != null) {
            MediaHeader newHeader = mediaHeader.toBuilder()
                    .setStartMs(-1)
                    .setSequenceNumber(0)
                    .build();
            initializedFormats.put(iTag, newHeader);
        }
    }
''',
'''    public void reset(FormatId formatId) {
        String key = formatId != null ? formatId.toString() : null;
        MediaHeader mediaHeader = key != null ? initializedFormats.get(key) : null;

        if (mediaHeader != null) {
            MediaHeader newHeader = mediaHeader.toBuilder()
                    .setStartMs(-1)
                    .setSequenceNumber(0)
                    .build();
            initializedFormats.put(key, newHeader);
        }
    }
''')

p = 'exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/parser/SabrStream.java'
replace(p, 'import com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatInitializationMetadata;\n', 'import com.google.android.exoplayer2.source.sabr.protos.misc.FormatId;\nimport com.google.android.exoplayer2.source.sabr.protos.videostreaming.FormatInitializationMetadata;\n')
replace(p, '    public void reset(int iTag) {\n        processor.reset(iTag);\n    }\n', '    public void reset(FormatId formatId) {\n        processor.reset(formatId);\n    }\n')
replace(p,
'''    public long getSegmentStartTimeMs(int iTag) {
        return processor.getSegmentStartTimeMs(iTag);
    }

    public long getSegmentDurationMs(int iTag) {
        return processor.getSegmentDurationMs(iTag);
    }
''',
'''    public long getSegmentStartTimeMs(FormatId formatId) {
        return processor.getSegmentStartTimeMs(formatId);
    }

    public long getSegmentDurationMs(FormatId formatId) {
        return processor.getSegmentDurationMs(formatId);
    }
''')
replace(p, '    public MediaHeader getInitializedFormat(int iTag) {\n        return processor.getInitializedFormats().get(iTag);\n    }\n', '    public MediaHeader getInitializedFormat(FormatId formatId) {\n        return formatId != null ? processor.getInitializedFormats().get(formatId.toString()) : null;\n    }\n')

# 9) SabrManifest: bind xtags and match current reference request semantics.
p = 'exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/manifest/SabrManifest.java'
replace(p,
'''    private final Map<Integer, SabrStream> sabrStreams;
    // V15 diagnostics: log protocol state only when the selected A/V identity changes.
''',
'''    private final Map<Integer, SabrStream> sabrStreams;
    private final Map<String, String> xtagsByFormat;
    // V16 diagnostics: log protocol state only when the selected A/V identity changes.
''')
replace(p, '            String videoId,\n            ClientInfo clientInfo) {\n', '            String videoId,\n            ClientInfo clientInfo,\n            Map<String, String> xtagsByFormat) {\n')
replace(p, '        this.sabrStreams = new HashMap<>();\n        this.requestDiagKeys = new HashMap<>();\n', '        this.sabrStreams = new HashMap<>();\n        this.xtagsByFormat = xtagsByFormat != null ? new HashMap<>(xtagsByFormat) : new HashMap<>();\n        this.requestDiagKeys = new HashMap<>();\n')
replace(p,
'''    public final String getVideoId() {
        return videoId;
    }

    public SabrStream getSabrStream(int trackType) {
''',
'''    public final String getVideoId() {
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
''')
replace(p,
'''        // V15: both per-renderer requests describe the same full A/V session.
        // Modern YouTube SABR no longer reliably serves AUDIO_ONLY/VIDEO_ONLY for
        // multi-audio videos, so keep the video resolution identity even on the
        // local audio lane. The companion format is still marked fully buffered
        // later, which prevents the lane from consuming the other renderer's media.
        int height = selectedVideoFormat != null ? selectedVideoFormat.height : -1;
        int videoBitrate = selectedVideoFormat != null ? selectedVideoFormat.bitrate : 0;
        int audioBitrate = selectedAudioFormat != null ? selectedAudioFormat.bitrate : 0;
        int bandwidthEstimate = videoBitrate > 0 || audioBitrate > 0
                ? Math.max(1, videoBitrate) + Math.max(0, audioBitrate) : -1;
        // YouTube's audioTrack.id is carried in Format.language only when the
        // corresponding audioTrack.displayName was present (stored as label).
        // Ordinary language metadata must not be mistaken for a SABR track id.
        String audioTrackId = selectedAudioFormat != null
                && selectedAudioFormat.label != null
                && !selectedAudioFormat.label.isEmpty()
                ? selectedAudioFormat.language : null;
        boolean drcEnabled = selectedAudioFormat != null && selectedAudioFormat.isDrc;

        FormatId formatId = getFormatSelector(trackType).getSelectedFormatId();
        long startTimeMs = isInit ? 0 : seekTimeUs != C.TIME_UNSET
                ? seekTimeUs / 1_000 : activeStream.getSegmentStartTimeMs(formatId != null ? formatId.getItag() : -1);
''',
'''        int height = trackType == C.TRACK_TYPE_VIDEO && selectedVideoFormat != null
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
''')
replace(p,
'''                .setDrcEnabled(drcEnabled)
                // V15: modern SABR expects a complete A/V session. We still mark the
                // companion renderer as fully buffered below, so this per-renderer
                // ExoPlayer port receives only the format it actually consumes.
                .setEnabledTrackTypesBitfield(EnabledTrackTypes.VIDEO_AND_AUDIO);
''',
'''                .setDrcEnabled(drcEnabled)
                .setEnabledTrackTypesBitfield(trackType == C.TRACK_TYPE_VIDEO
                        ? EnabledTrackTypes.VIDEO_ONLY : EnabledTrackTypes.AUDIO_ONLY);
''')
replace(p,
'''        FormatId currentFormat = trackType == C.TRACK_TYPE_VIDEO ? videoFormat : audioFormat;
        int currentFormatKey = currentFormat != null ? currentFormat.getItag() : -1;

        for (FormatId activeFormat : new FormatId[]{videoFormat, audioFormat}) {
''',
'''        FormatId currentFormat = trackType == C.TRACK_TYPE_VIDEO ? videoFormat : audioFormat;

        for (FormatId activeFormat : new FormatId[]{videoFormat, audioFormat}) {
''')
replace(p, '            int activeFormatKey = activeFormat.getItag();\n            boolean shouldDiscard = currentFormatKey != activeFormatKey;\n            MediaHeader initializedFormat = getInitializedFormat(activeFormatKey);\n', '            boolean shouldDiscard = currentFormat == null || !currentFormat.equals(activeFormat);\n            MediaHeader initializedFormat = getInitializedFormat(activeFormat);\n')
replace(p, '    private @Nullable MediaHeader getInitializedFormat(int iTag) {\n        MediaHeader initializedFormat = null;\n\n        for (SabrStream sabrStream : sabrStreams.values()) {\n            MediaHeader mediaHeader = sabrStream.getInitializedFormat(iTag);\n', '    private @Nullable MediaHeader getInitializedFormat(FormatId formatId) {\n        MediaHeader initializedFormat = null;\n\n        for (SabrStream sabrStream : sabrStreams.values()) {\n            MediaHeader mediaHeader = sabrStream.getInitializedFormat(formatId);\n')
replace(p, '        Log.d(TAG, "V15_SABR_REQ lane=" + trackTypeName(trackType)\n                + " protocol=VIDEO_AND_AUDIO"\n', '        Log.d(TAG, "V16_SABR_REQ lane=" + trackTypeName(trackType)\n                + " protocol=" + (trackType == C.TRACK_TYPE_VIDEO ? "VIDEO_ONLY" : "AUDIO_ONLY")\n')

# 10) Chunk source enriches every selector and propagates track changes immediately.
p = 'exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/DefaultSabrChunkSource.java'
replace(p, '        this.trackSelection = trackSelection;\n        this.formatSelector = createFormatSelector(trackType, trackSelection);\n        this.trackType = trackType;\n', '        this.trackSelection = trackSelection;\n        this.formatSelector = createFormatSelector(trackType, trackSelection);\n        manifest.applySabrIdentity(this.formatSelector);\n        this.trackType = trackType;\n')
replace(p, '    public void updateTrackSelection(TrackSelection trackSelection) {\n        this.trackSelection = trackSelection;\n        this.formatSelector = createFormatSelector(trackType, trackSelection);\n    }\n', '    public void updateTrackSelection(TrackSelection trackSelection) {\n        this.trackSelection = trackSelection;\n        this.formatSelector = createFormatSelector(trackType, trackSelection);\n        manifest.applySabrIdentity(this.formatSelector);\n        sabrStream.setFormatSelector(this.formatSelector);\n    }\n')
replace(p, '        FormatId formatId = formatSelector.getSelectedFormatId();\n        int iTag = formatId != null ? formatId.getItag() : -1;\n\n        if (nexChunkIdx == -1) {\n            sabrStream.reset(iTag);\n        }\n', '        FormatId formatId = formatSelector.getSelectedFormatId();\n\n        if (nexChunkIdx == -1) {\n            sabrStream.reset(formatId);\n        }\n')
replace(p, '        long startTimeMs = sabrStream.getSegmentStartTimeMs(iTag);\n        long durationMs = sabrStream.getSegmentDurationMs(iTag);\n', '        long startTimeMs = sabrStream.getSegmentStartTimeMs(formatId);\n        long durationMs = sabrStream.getSegmentDurationMs(formatId);\n')

print('aa1.31 runtime transformations applied successfully')
