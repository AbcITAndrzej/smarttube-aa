package com.liskovsoft.youtubeapi.videoinfo;

import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.youtubeapi.app.AppService;
import com.liskovsoft.googlecommon.common.api.FileApi;
import com.liskovsoft.youtubeapi.app.PoTokenGate;
import com.liskovsoft.googlecommon.common.helpers.RetrofitHelper;
import com.liskovsoft.youtubeapi.formatbuilders.utils.MediaFormatUtils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;
import com.liskovsoft.youtubeapi.videoinfo.V2.DashInfoApi;
import com.liskovsoft.youtubeapi.videoinfo.models.VideoUrlHolder;
import com.liskovsoft.youtubeapi.videoinfo.models.DashInfo;
import com.liskovsoft.youtubeapi.videoinfo.models.DashInfoContent;
import com.liskovsoft.youtubeapi.videoinfo.models.DashInfoHeaders;
import com.liskovsoft.youtubeapi.videoinfo.models.DashInfoUrl;
import com.liskovsoft.youtubeapi.videoinfo.models.VideoInfo;
import com.liskovsoft.youtubeapi.videoinfo.models.formats.AdaptiveVideoFormat;
import com.liskovsoft.youtubeapi.videoinfo.models.formats.VideoFormat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kotlin.Pair;

public abstract class VideoInfoServiceBase {
    private static final String TAG = VideoInfoServiceBase.class.getSimpleName();
    private final DashInfoApi mDashInfoApi;
    private final FileApi mFileApi;
    protected final AppService mAppService;

    protected VideoInfoServiceBase() {
        mAppService = AppService.instance();
        mDashInfoApi = RetrofitHelper.create(DashInfoApi.class);
        mFileApi = RetrofitHelper.create(FileApi.class);
    }

    protected void transformFormats(VideoInfo videoInfo) {
        if (videoInfo == null || videoInfo.isUnplayable()) {
            return;
        }

        // V14: YouTube now emits normal + DRC + voice-boost representations with
        // the same itag. Legacy SABR cannot safely treat those as independent
        // representations of one adaptive group. Keep the normal variant when it
        // exists and retain DRC/VB only when it is the sole available variant.
        normalizeAdaptiveAudioVariants(videoInfo);
        logAdaptiveAudioCatalog(videoInfo);

        decipherFormats(videoInfo);

        if (videoInfo.isLive()) {
            Log.d(TAG, "Enable seeking support on live streams...");
            videoInfo.sync(getDashInfo(videoInfo));
        }

        videoInfo.setVisitorCookie(getData().getVisitorCookie());
    }


    private void normalizeAdaptiveAudioVariants(VideoInfo videoInfo) {
        List<AdaptiveVideoFormat> formats = videoInfo.getAdaptiveFormats();
        if (formats == null || formats.isEmpty()) {
            return;
        }

        Set<String> baseAudioKeys = new HashSet<>();
        Set<String> logicalTracks = new HashSet<>();
        int audioBefore = 0;

        for (AdaptiveVideoFormat format : formats) {
            if (!isAudioFormat(format)) {
                continue;
            }

            audioBefore++;
            String logicalTrack = audioLogicalTrackKey(format);
            logicalTracks.add(logicalTrack);
            if (!format.isDrc() && !format.isVb()) {
                baseAudioKeys.add(audioVariantKey(format));
            }
        }

        int removed = 0;
        List<AdaptiveVideoFormat> normalized = new ArrayList<>(formats.size());
        for (AdaptiveVideoFormat format : formats) {
            boolean removableVariant = isAudioFormat(format)
                    && (format.isDrc() || format.isVb())
                    && baseAudioKeys.contains(audioVariantKey(format));
            if (removableVariant) {
                removed++;
            } else {
                normalized.add(format);
            }
        }

        if (removed > 0) {
            videoInfo.setAdaptiveFormats(normalized);
        }

        if (audioBefore > 0) {
            Log.d(TAG,
                    "V14_AUDIO_NORMALIZE audioBefore=%s audioAfter=%s removedVariants=%s logicalTracks=%s",
                    audioBefore, audioBefore - removed, removed, logicalTracks.size());
        }
    }

    private static boolean isAudioFormat(VideoFormat format) {
        String mimeType = format != null ? format.getMimeType() : null;
        return mimeType != null && mimeType.startsWith("audio/");
    }

    private static String audioVariantKey(VideoFormat format) {
        return audioLogicalTrackKey(format) + "|" + format.getITag() + "|"
                + (format.getMimeType() != null ? format.getMimeType() : "");
    }

    private static String audioLogicalTrackKey(VideoFormat format) {
        if (format == null) {
            return "default";
        }

        String trackId = format.getAudioTrackId();
        if (trackId != null && !trackId.isEmpty()) {
            return trackId;
        }

        String language = format.getLanguage();
        return language != null && !language.isEmpty() ? language : "default";
    }

    private void logAdaptiveAudioCatalog(VideoInfo videoInfo) {
        List<AdaptiveVideoFormat> formats = videoInfo.getAdaptiveFormats();
        if (formats == null || formats.isEmpty()) {
            return;
        }

        Map<String, List<AdaptiveVideoFormat>> tracks = new LinkedHashMap<>();
        for (AdaptiveVideoFormat format : formats) {
            if (!isAudioFormat(format)) {
                continue;
            }
            String key = audioLogicalTrackKey(format);
            List<AdaptiveVideoFormat> trackFormats = tracks.get(key);
            if (trackFormats == null) {
                trackFormats = new ArrayList<>();
                tracks.put(key, trackFormats);
            }
            trackFormats.add(format);
        }

        String videoId = videoInfo.getVideoDetails() != null
                ? videoInfo.getVideoDetails().getVideoId() : "-";
        Log.d(TAG, "V15_AUDIO_CATALOG video=%s logicalTracks=%s representations=%s",
                videoId, tracks.size(), countAudioFormats(formats));

        for (Map.Entry<String, List<AdaptiveVideoFormat>> entry : tracks.entrySet()) {
            List<AdaptiveVideoFormat> variants = entry.getValue();
            AdaptiveVideoFormat first = variants.isEmpty() ? null : variants.get(0);
            StringBuilder details = new StringBuilder();
            for (AdaptiveVideoFormat variant : variants) {
                if (details.length() > 0) details.append(',');
                details.append(variant.getITag())
                        .append('@').append(valueOrDash(variant.getLmt()))
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
        }
    }

    private static int countAudioFormats(List<AdaptiveVideoFormat> formats) {
        int count = 0;
        for (AdaptiveVideoFormat format : formats) {
            if (isAudioFormat(format)) count++;
        }
        return count;
    }

    private void decipherFormats(VideoInfo videoInfo) {
        List<? extends VideoFormat> adaptiveFormats = videoInfo.getAdaptiveFormats();
        List<? extends VideoFormat> regularFormats = videoInfo.getRegularFormats();

        List<VideoUrlHolder> urlHolders = new ArrayList<>();
        if (adaptiveFormats != null)
            for (VideoFormat videoFormat : adaptiveFormats) {
                urlHolders.add(videoFormat.getUrlHolder());
            }
        if (regularFormats != null)
            for (VideoFormat videoFormat : regularFormats) {
                urlHolders.add(videoFormat.getUrlHolder());
            }
        urlHolders.add(videoInfo.getUrlHolder());

        List<String> inputN = extractNParams(urlHolders);
        List<String> inputS = extractSParams(urlHolders);
        Log.d(TAG, "V7_AUTH V2 decipher holders=%s nValues=%s sValues=%s",
                urlHolders.size(), countNonNull(inputN), countNonNull(inputS));
        logAuthState("before", urlHolders);

        Pair<List<String>, List<String>> result = mAppService.bulkSigExtract(inputN, inputS);

        if (result != null) {
            List<String> nParams = result.getFirst();
            List<String> signatures = result.getSecond();
            Log.d(TAG, "V7_AUTH V2 transformed nOut=%s nChanged=%s sOut=%s sChanged=%s",
                    countNonNull(nParams), countChanged(inputN, nParams),
                    countNonNull(signatures), countChanged(inputS, signatures));

            applyNParams(urlHolders, nParams);
            applySignatures(urlHolders, signatures);
        } else {
            Log.w(TAG, "V7_AUTH V2 bulkSigExtract returned null");
        }

        // V9: PoToken is client-bound. Web-family clients get their Web CONTENT
        // token; direct IOS/ANDROID_VR clients intentionally do not receive a
        // token produced for a different client/session.
        String poToken = videoInfo.getClient() != null
                ? PoTokenGate.getPoToken(videoInfo.getClient(), videoInfo.getVideoDetails().getVideoId())
                : null;
        Log.d(TAG, "V9_CLIENT path=V2 client=%s version=%s webPotRequired=%s potApplied=%s potLen=%s",
                videoInfo.getClient() != null ? videoInfo.getClient().getClientName() : "unknown",
                videoInfo.getClient() != null ? videoInfo.getClient().getClientVersion() : "unknown",
                videoInfo.getClient() != null && videoInfo.getClient().isWebPotRequired(),
                poToken != null, poToken != null ? poToken.length() : 0);
        videoInfo.setPoToken(poToken);
        applySessionPoToken(urlHolders, poToken);
        logAuthState("final", urlHolders);
    }

    private static int countNonNull(List<String> values) {
        if (values == null) {
            return 0;
        }
        int result = 0;
        for (String value : values) {
            if (value != null) {
                result++;
            }
        }
        return result;
    }

    private static int countChanged(List<String> input, List<String> output) {
        if (input == null || output == null || input.size() != output.size()) {
            return 0;
        }
        int result = 0;
        for (int i = 0; i < input.size(); i++) {
            String before = input.get(i);
            String after = output.get(i);
            if (before != null && after != null && !before.equals(after)) {
                result++;
            }
        }
        return result;
    }

    private static void logAuthState(String stage, List<VideoUrlHolder> urlHolders) {
        for (int i = 0; i < urlHolders.size(); i++) {
            VideoUrlHolder holder = urlHolders.get(i);
            String n = holder.getParam("n");
            String pot = holder.getParam("pot");
            Log.d(TAG,
                    "V7_AUTH V2 %s idx=%s c=%s itag=%s n=%s nLen=%s s=%s sig=%s lsig=%s spc=%s pot=%s potLen=%s",
                    stage, i,
                    valueOrDash(holder.getParam("c")),
                    valueOrDash(holder.getParam("itag")),
                    n != null, n != null ? n.length() : 0,
                    holder.getSParam() != null,
                    holder.getParam("sig") != null || holder.getParam("signature") != null,
                    holder.getParam("lsig") != null,
                    holder.getParam("spc") != null,
                    pot != null, pot != null ? pot.length() : 0);
        }
    }

    private static String valueOrDash(String value) {
        return value != null ? value : "-";
    }

    private static List<String> extractSParams(List<VideoUrlHolder> urlHolders) {
        List<String> result = new ArrayList<>();

        for (VideoUrlHolder urlHolder : urlHolders) {
            result.add(urlHolder.getSParam());
        }

        return result;
    }

    private static void applySignatures(List<VideoUrlHolder> urlHolders, List<String> signatures) {
        if (signatures == null) {
            return;
        }

        if (signatures.size() != urlHolders.size()) {
            throw new IllegalStateException("Sizes of urlHolders and signatures should match!");
        }

        for (int i = 0; i < urlHolders.size(); i++) {
            urlHolders.get(i).setSignature(signatures.get(i));
        }
    }

    private static List<String> extractNParams(List<VideoUrlHolder> urlHolders) {
        List<String> result = new ArrayList<>();

        for (VideoUrlHolder urlHolder : urlHolders) {
            result.add(urlHolder.getNParam());
            // All throttled strings has same values
        }

        return result;
    }

    private static void applyNParams(List<VideoUrlHolder> urlHolders, List<String> nParams) {
        if (nParams == null || nParams.isEmpty()) {
            return;
        }

        // All throttled strings has same values
        boolean sameSize = nParams.size() == urlHolders.size();

        for (int i = 0; i < urlHolders.size(); i++) {
            urlHolders.get(i).setNParam(nParams.get(sameSize ? i : 0));
        }
    }

    private static void applySessionPoToken(List<VideoUrlHolder> urlHolders, String poToken) {
        if (poToken == null) {
            return;
        }

        for (int i = 0; i < urlHolders.size(); i++) {
            urlHolders.get(i).setPoToken(poToken);
        }
    }

    private DashInfoUrl getDashInfoUrl(String url) {
        if (url == null) {
            return null;
        }

        return RetrofitHelper.get(mDashInfoApi.getDashInfoUrl(url));
    }

    private DashInfoContent getDashInfoContent(String url) {
        if (url == null) {
            return null;
        }

        return RetrofitHelper.get(mDashInfoApi.getDashInfoContent(url));
    }

    private DashInfoHeaders getDashInfoHeaders(String url) {
        if (url == null) {
            return null;
        }

        // Range doesn't work???
        //return RetrofitHelper.getHeaders(mFileApi.getHeaders(url + SMALL_RANGE));
        return new DashInfoHeaders(RetrofitHelper.getHeaders(mFileApi.getHeaders(url)));
    }

    private DashInfo getDashInfo(VideoInfo videoInfo) {
        if (videoInfo == null || videoInfo.getAdaptiveFormats() == null || videoInfo.getAdaptiveFormats().isEmpty()) {
            return null;
        }

        DashInfo info = getCumulativeDashInfo(videoInfo);

        // Do retry. Sometimes the previous try failed?
        if (info == null || info.getSegmentDurationUs() <= 0 || info.getStartTimeMs() <= 0 || info.getStartSegmentNum() < 0) {
            info = getCumulativeDashInfo(videoInfo);
        }

        return info;
    }

    private DashInfo getCumulativeDashInfo(VideoInfo videoInfo) {
        AdaptiveVideoFormat format = getSmallestAudio(videoInfo);

        if (format == null) {
            return null;
        }

        try {
            return getDashInfoHeaders(format.getUrl());
        } catch (ArithmeticException | NumberFormatException | IllegalStateException ex) {
            try {
                return getDashInfoUrl(format.getUrl());
            } catch (ArithmeticException | NumberFormatException exc) {
                // Empty results received. Url isn't available or something like that
                return getDashInfoContent(format.getUrl());
            }
        }
    }

    private AdaptiveVideoFormat getSmallestAudio(VideoInfo videoInfo) {
        AdaptiveVideoFormat format = Helpers.findFirst(videoInfo.getAdaptiveFormats(),
                item -> MediaFormatUtils.isAudio(item.getMimeType())); // smallest format
        return format;
    }

    private AdaptiveVideoFormat getSmallestVideo(VideoInfo videoInfo) {
        AdaptiveVideoFormat format = Helpers.findLast(videoInfo.getAdaptiveFormats(),
                item -> MediaFormatUtils.isVideo(item.getMimeType())); // smallest format
        return format;
    }
    
    private AdaptiveVideoFormat getLargestVideo(VideoInfo videoInfo) {
        AdaptiveVideoFormat format = Helpers.findFirst(videoInfo.getAdaptiveFormats(),
                item -> MediaFormatUtils.isVideo(item.getMimeType())); // first is largest
        return format;
    }

    protected static MediaServiceData getData() {
        return MediaServiceData.instance();
    }
}
