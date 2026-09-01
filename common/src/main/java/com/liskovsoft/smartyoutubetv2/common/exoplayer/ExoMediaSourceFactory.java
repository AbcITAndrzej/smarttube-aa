package com.liskovsoft.smartyoutubetv2.common.exoplayer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ext.cronet.CronetDataSourceFactory;
import com.google.android.exoplayer2.ext.cronet.CronetEngineWrapper;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashChunkSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser2;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.ProgramInformation;
import com.google.android.exoplayer2.source.dash.manifest.UtcTimingElement;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.sabr.DefaultSabrChunkSource;
import com.google.android.exoplayer2.source.sabr.SabrChunkSource;
import com.google.android.exoplayer2.source.sabr.SabrMediaSource;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifest;
import com.google.android.exoplayer2.source.sabr.manifest.SabrManifestParser;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSource.Factory;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource.BaseFactory;
import com.google.android.exoplayer2.util.Util;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.sharedutils.cronet.CronetManager;
import com.liskovsoft.sharedutils.helpers.FileHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.okhttp.OkHttpManager;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.errors.DashDefaultLoadErrorHandlingPolicy;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.errors.SabrDefaultLoadErrorHandlingPolicy;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.errors.TrackErrorFixer;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executors;

public class ExoMediaSourceFactory {
    private static final String TAG = ExoMediaSourceFactory.class.getSimpleName();
    @SuppressLint("StaticFieldLeak")
    //private static ExoMediaSourceFactory sInstance;
    private static final int MAX_SEGMENTS_PER_LOAD = 1; // default - 1 (1-5)
    private static final String USER_AGENT = DefaultHeaders.APP_USER_AGENT;
    @SuppressLint("StaticFieldLeak")
    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
    private final Context mContext;
    private static final Uri DASH_MANIFEST_URI = Uri.parse("https://example.com/test.mpd");
    private static final String DASH_MANIFEST_EXTENSION = "mpd";
    private static final String HLS_PLAYLIST_EXTENSION = "m3u8";
    private static final boolean USE_BANDWIDTH_METER = false;
    private TrackErrorFixer mTrackErrorFixer;
    private Factory mMediaDataSourceFactory;

    public ExoMediaSourceFactory(Context context) {
        mContext = context;
    }

    public MediaSource fromSabrFormatInfo(MediaItemFormatInfo formatInfo) {
        return buildSabrMediaSource(formatInfo);
    }

    public MediaSource fromDashFormatInfo(MediaItemFormatInfo formatInfo) {
        return buildDashMediaSource(formatInfo);
    }

    public MediaSource fromDashManifest(InputStream dashManifest) {
        return buildMPDMediaSource(DASH_MANIFEST_URI, dashManifest);
    }

    public MediaSource fromDashManifestUrl(String dashManifestUrl) {
        return buildMediaSource(Uri.parse(dashManifestUrl), DASH_MANIFEST_EXTENSION);
    }

    public MediaSource fromHlsPlaylist(String hlsPlaylist) {
        return buildMediaSource(Uri.parse(hlsPlaylist), HLS_PLAYLIST_EXTENSION);
    }

    public MediaSource fromUrlList(List<String> urlList) {
        MediaSource[] mediaSources = new MediaSource[urlList.size()];

        for (int i = 0; i < urlList.size(); i++) {
            mediaSources[i] = buildMediaSource(Uri.parse(urlList.get(i)), null);
        }

        //return mediaSources.length == 1 ? mediaSources[0] : new ConcatenatingMediaSource(mediaSources); // or playlist
        return mediaSources[0]; // item with max resolution
    }

    /**
     * V10 survival path for mid-stream GVS 403: use a muxed/progressive format with the
     * same client User-Agent that produced the URL. This avoids DASH audio/video chunk
     * range requests while preserving the original client identity.
     */
    public MediaSource fromUrlList(MediaItemFormatInfo formatInfo) {
        List<String> urlList = formatInfo != null ? formatInfo.createUrlList() : null;
        if (urlList == null || urlList.isEmpty()) {
            throw new IllegalStateException("V10 progressive fallback requested without URL formats");
        }

        MediaItemFormatInfo.ClientInfo clientInfo = formatInfo.getClientInfo();
        String clientName = clientInfo != null ? clientInfo.getClientName() : "unknown";
        String userAgent = clientInfo != null ? clientInfo.getUserAgent() : null;
        Log.d(TAG, "V10_PROGRESSIVE source client=%s urls=%s userAgent=%s",
                clientName, urlList.size(), TextUtils.isEmpty(userAgent) ? "default" : userAgent);

        ExtractorMediaSource extractorSource = new ExtractorMediaSource.Factory(getMediaDataSourceFactory(formatInfo))
                .setExtractorsFactory(new DefaultExtractorsFactory())
                .createMediaSource(Uri.parse(urlList.get(0)));
        if (mTrackErrorFixer != null) {
            extractorSource.addEventListener(Utils.sHandler, mTrackErrorFixer);
        }
        return extractorSource;
    }

    /**
     * Returns a new DataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
     *                          DataSource factory.
     * @return A new DataSource factory.
     */
    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter) {
        return buildDataSourceFactory(useBandwidthMeter, USER_AGENT);
    }

    private DataSource.Factory buildDataSourceFactory(boolean useBandwidthMeter, String userAgent) {
        DefaultBandwidthMeter bandwidthMeter = useBandwidthMeter ? BANDWIDTH_METER : null;
        return new DefaultDataSourceFactory(mContext, bandwidthMeter, buildHttpDataSourceFactory(useBandwidthMeter, userAgent));
    }

    /**
     * Returns a new HttpDataSource factory.
     *
     * @param useBandwidthMeter Whether to set {@link #BANDWIDTH_METER} as a listener to the new
     *                          DataSource factory.
     * @return A new HttpDataSource factory.
     */
    private HttpDataSource.Factory buildHttpDataSourceFactory(boolean useBandwidthMeter) {
        return buildHttpDataSourceFactory(useBandwidthMeter, USER_AGENT);
    }

    private HttpDataSource.Factory buildHttpDataSourceFactory(boolean useBandwidthMeter, String userAgent) {
        PlayerTweaksData tweaksData = PlayerTweaksData.instance(mContext);
        int source = tweaksData.getPlayerDataSource();
        DefaultBandwidthMeter bandwidthMeter = useBandwidthMeter ? BANDWIDTH_METER : null;
        String effectiveUserAgent = TextUtils.isEmpty(userAgent) ? USER_AGENT : userAgent;
        return source == PlayerTweaksData.PLAYER_DATA_SOURCE_OKHTTP ? buildOkHttpDataSourceFactory(bandwidthMeter, effectiveUserAgent) :
                        source == PlayerTweaksData.PLAYER_DATA_SOURCE_CRONET && CronetManager.getEngine(mContext) != null ? buildCronetDataSourceFactory(bandwidthMeter, effectiveUserAgent) :
                                buildDefaultHttpDataSourceFactory(bandwidthMeter, effectiveUserAgent);
    }

    @SuppressWarnings("deprecation")
    private MediaSource buildMediaSource(Uri uri, String overrideExtension) {
        int type = TextUtils.isEmpty(overrideExtension) ? Util.inferContentType(uri) : Util.inferContentType("." + overrideExtension);
        switch (type) {
            case C.TYPE_SS:
                SsMediaSource ssSource =
                        new SsMediaSource.Factory(
                                getSsChunkSourceFactory(),
                                getMediaDataSourceFactory()
                        )
                                .createMediaSource(uri);
                if (mTrackErrorFixer != null) {
                    ssSource.addEventListener(Utils.sHandler, mTrackErrorFixer);
                }
                return ssSource;
            case C.TYPE_DASH:
                DashMediaSource dashSource =
                        new DashMediaSource.Factory(
                                getDashChunkSourceFactory(),
                                getMediaDataSourceFactory()
                        )
                                .setManifestParser(new LiveDashManifestParser()) // Don't make static! Need state reset for each live source.
                                .setLoadErrorHandlingPolicy(new DashDefaultLoadErrorHandlingPolicy())
                                .createMediaSource(uri);
                if (mTrackErrorFixer != null) {
                    dashSource.addEventListener(Utils.sHandler, mTrackErrorFixer);
                }
                return dashSource;
            case C.TYPE_HLS:
                HlsMediaSource hlsSource = new HlsMediaSource.Factory(getMediaDataSourceFactory()).createMediaSource(uri);
                if (mTrackErrorFixer != null) {
                    hlsSource.addEventListener(Utils.sHandler, mTrackErrorFixer);
                }
                return hlsSource;
            case C.TYPE_OTHER:
                ExtractorMediaSource extractorSource = new ExtractorMediaSource.Factory(getMediaDataSourceFactory())
                        .setExtractorsFactory(new DefaultExtractorsFactory())
                        .createMediaSource(uri);
                if (mTrackErrorFixer != null) {
                    extractorSource.addEventListener(Utils.sHandler, mTrackErrorFixer);
                }
                return extractorSource;
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
        }
    }

    private MediaSource buildSabrMediaSource(MediaItemFormatInfo formatInfo) {
        // Are you using FrameworkSampleSource or ExtractorSampleSource when you build your player?
        SabrMediaSource sabrSource = new SabrMediaSource.Factory(
                getSabrChunkSourceFactory(formatInfo),
                null
        )
                .setLoadErrorHandlingPolicy(new SabrDefaultLoadErrorHandlingPolicy())
                .createMediaSource(getSabrManifest(formatInfo));
        if (mTrackErrorFixer != null) {
            sabrSource.addEventListener(Utils.sHandler, mTrackErrorFixer);
        }
        return sabrSource;
    }

    private MediaSource buildDashMediaSource(MediaItemFormatInfo formatInfo) {
        // Are you using FrameworkSampleSource or ExtractorSampleSource when you build your player?
        DashMediaSource dashSource = new DashMediaSource.Factory(
                getDashChunkSourceFactory(formatInfo),
                null
        )
                .setLoadErrorHandlingPolicy(new DashDefaultLoadErrorHandlingPolicy())
                .createMediaSource(getManifest(formatInfo));
        if (mTrackErrorFixer != null) {
            dashSource.addEventListener(Utils.sHandler, mTrackErrorFixer);
        }
        return dashSource;
    }

    private MediaSource buildMPDMediaSource(Uri uri, InputStream mpdContent) {
        // Are you using FrameworkSampleSource or ExtractorSampleSource when you build your player?
        DashMediaSource dashSource = new DashMediaSource.Factory(
                getDashChunkSourceFactory(),
                null
        )
                .setLoadErrorHandlingPolicy(new DashDefaultLoadErrorHandlingPolicy())
                .createMediaSource(getManifest(uri, mpdContent));
        if (mTrackErrorFixer != null) {
            dashSource.addEventListener(Utils.sHandler, mTrackErrorFixer);
        }
        return dashSource;
    }

    private MediaSource buildMPDMediaSource(Uri uri, String mpdContent) {
        if (mpdContent == null || mpdContent.isEmpty()) {
            Log.e(TAG, "Can't build media source. MpdContent is null or empty. " + mpdContent);
            return null;
        }

        // Are you using FrameworkSampleSource or ExtractorSampleSource when you build your player?
        DashMediaSource dashSource = new DashMediaSource.Factory(
                new DefaultDashChunkSource.Factory(getMediaDataSourceFactory()),
                null
        )
                .createMediaSource(getManifest(uri, mpdContent));
        if (mTrackErrorFixer != null) {
            dashSource.addEventListener(Utils.sHandler, mTrackErrorFixer);
        }
        return dashSource;
    }

    private SabrManifest getSabrManifest(MediaItemFormatInfo formatInfo) {
        SabrManifestParser parser = new SabrManifestParser();
        return parser.parse(formatInfo);
    }

    private DashManifest getManifest(MediaItemFormatInfo formatInfo) {
        DashManifestParser2 parser = new DashManifestParser2();
        return parser.parse(formatInfo);
    }

    private DashManifest getManifest(Uri uri, InputStream mpdContent) {
        DashManifestParser parser = new StaticDashManifestParser();
        DashManifest result;
        try {
            result = parser.parse(uri, mpdContent);
        } catch (IOException e) {
            throw new IllegalStateException("Malformed mpd file:\n" + mpdContent, e);
        }
        return result;
    }

    private DashManifest getManifest(Uri uri, String mpdContent) {
        DashManifestParser parser = new StaticDashManifestParser();
        DashManifest result;
        try {
            result = parser.parse(uri, FileHelpers.toStream(mpdContent));
        } catch (IOException e) {
            throw new IllegalStateException("Malformed mpd file:\n" + mpdContent, e);
        }
        return result;
    }

    /**
     * Use OkHttp for networking
     */
    private HttpDataSource.Factory buildOkHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
        return buildOkHttpDataSourceFactory(bandwidthMeter, USER_AGENT);
    }

    private HttpDataSource.Factory buildOkHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter, String userAgent) {
        OkHttpDataSourceFactory dataSourceFactory = new OkHttpDataSourceFactory(OkHttpManager.instance().getClient(), userAgent,
                bandwidthMeter);
        addCommonHeaders(dataSourceFactory);
        return dataSourceFactory;
    }

    private HttpDataSource.Factory buildCronetDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
        return buildCronetDataSourceFactory(bandwidthMeter, USER_AGENT);
    }

    private HttpDataSource.Factory buildCronetDataSourceFactory(DefaultBandwidthMeter bandwidthMeter, String userAgent) {
        CronetDataSourceFactory dataSourceFactory =
                new CronetDataSourceFactory(
                        new CronetEngineWrapper(CronetManager.getEngine(mContext)),
                        Executors.newSingleThreadExecutor(),
                        null,
                        bandwidthMeter,
                        (int) OkHttpManager.getConnectTimeoutMs(),
                        (int) OkHttpManager.getReadTimeoutMs(),
                        true,
                        userAgent);
        addCommonHeaders(dataSourceFactory);
        return dataSourceFactory;
    }

    /**
     * Use built-in component for networking
     */
    private HttpDataSource.Factory buildDefaultHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter) {
        return buildDefaultHttpDataSourceFactory(bandwidthMeter, USER_AGENT);
    }

    private HttpDataSource.Factory buildDefaultHttpDataSourceFactory(DefaultBandwidthMeter bandwidthMeter, String userAgent) {
        DefaultHttpDataSourceFactory dataSourceFactory = new DefaultHttpDataSourceFactory(
                userAgent, bandwidthMeter, (int) OkHttpManager.getConnectTimeoutMs(),
                (int) OkHttpManager.getReadTimeoutMs(), true); // allowCrossProtocolRedirects = true

        addCommonHeaders(dataSourceFactory); // cause troubles for some users
        return dataSourceFactory;
    }

    private static void addCommonHeaders(BaseFactory dataSourceFactory) {
        // Doesn't work
        // Trying to fix 429 error (too many requests)
        //String authorization = RetrofitOkHttpHelper.getAuthHeaders().get("Authorization");
        //
        //if (authorization != null) {
        //    dataSourceFactory.getDefaultRequestProperties().set("Authorization", authorization);
        //}

        //HeaderManager headerManager = new HeaderManager(context);
        //HashMap<String, String> headers = headerManager.getHeaders();

        // NOTE: "Accept-Encoding" should not be set manually (gzip is added by default).

        //for (String header : headers.keySet()) {
        //    if (EXO_HEADERS.contains(header)) {
        //        dataSourceFactory.getDefaultRequestProperties().set(header, headers.get(header));
        //    }
        //}

        // Emulate browser request
        //dataSourceFactory.getDefaultRequestProperties().set("accept", "*/*");
        //dataSourceFactory.getDefaultRequestProperties().set("accept-encoding", "identity"); // Next won't work: gzip, deflate, br
        //dataSourceFactory.getDefaultRequestProperties().set("accept-language", "en-US,en;q=0.9");
        //dataSourceFactory.getDefaultRequestProperties().set("dnt", "1");
        //dataSourceFactory.getDefaultRequestProperties().set("origin", "https://www.youtube.com");
        //dataSourceFactory.getDefaultRequestProperties().set("referer", "https://www.youtube.com/");
        //dataSourceFactory.getDefaultRequestProperties().set("sec-fetch-dest", "empty");
        //dataSourceFactory.getDefaultRequestProperties().set("sec-fetch-mode", "cors");
        //dataSourceFactory.getDefaultRequestProperties().set("sec-fetch-site", "cross-site");

        // WARN: Compression won't work with legacy streams.
        // "Accept-Encoding" should not be set manually (gzip is added by default).
        // Otherwise you should do decompression yourself.
        // Source: https://stackoverflow.com/questions/18898959/httpurlconnection-not-decompressing-gzip/42346308#42346308
        //dataSourceFactory.getDefaultRequestProperties().set("Accept-Encoding", AppConstants.ACCEPT_ENCODING_DEFAULT);
    }

    public void setTrackErrorFixer(TrackErrorFixer trackErrorFixer) {
        mTrackErrorFixer = trackErrorFixer;
    }

    public void release() {
        mMediaDataSourceFactory = null;
    }

    @NonNull
    private DefaultSsChunkSource.Factory getSsChunkSourceFactory() {
        return new DefaultSsChunkSource.Factory(getMediaDataSourceFactory());
    }

    @NonNull
    private SabrChunkSource.Factory getSabrChunkSourceFactory() {
        return new DefaultSabrChunkSource.Factory(getMediaDataSourceFactory(), MAX_SEGMENTS_PER_LOAD);
    }

    @NonNull
    private SabrChunkSource.Factory getSabrChunkSourceFactory(MediaItemFormatInfo formatInfo) {
        return new DefaultSabrChunkSource.Factory(getMediaDataSourceFactory(formatInfo), MAX_SEGMENTS_PER_LOAD);
    }

    @NonNull
    private DashChunkSource.Factory getDashChunkSourceFactory() {
        return new DefaultDashChunkSource.Factory(getMediaDataSourceFactory(), MAX_SEGMENTS_PER_LOAD);
    }

    @NonNull
    private DashChunkSource.Factory getDashChunkSourceFactory(MediaItemFormatInfo formatInfo) {
        return new DefaultDashChunkSource.Factory(getMediaDataSourceFactory(formatInfo), MAX_SEGMENTS_PER_LOAD);
    }

    private Factory getMediaDataSourceFactory() {
        if (mMediaDataSourceFactory == null) {
            mMediaDataSourceFactory = buildDataSourceFactory(USE_BANDWIDTH_METER);
        }

        return mMediaDataSourceFactory;
    }

    private Factory getMediaDataSourceFactory(MediaItemFormatInfo formatInfo) {
        String userAgent = USER_AGENT;
        MediaItemFormatInfo.ClientInfo clientInfo = formatInfo != null ? formatInfo.getClientInfo() : null;

        if (clientInfo != null && !TextUtils.isEmpty(clientInfo.getUserAgent())) {
            userAgent = clientInfo.getUserAgent();
            Log.d(TAG, "Media stream client=%s userAgent=%s", clientInfo.getClientName(), userAgent);
        } else {
            Log.d(TAG, "Media stream client is unknown. Using fallback userAgent=%s", USER_AGENT);
        }

        // Do not cache this factory: the next item may be produced by a different YouTube client.
        return buildDataSourceFactory(USE_BANDWIDTH_METER, userAgent);
    }

    // EXO: 2.10 - 2.12
    private static class StaticDashManifestParser extends DashManifestParser {
        @Override
        protected DashManifest buildMediaPresentationDescription(
                long availabilityStartTime,
                long durationMs,
                long minBufferTimeMs,
                boolean dynamic,
                long minUpdateTimeMs,
                long timeShiftBufferDepthMs,
                long suggestedPresentationDelayMs,
                long publishTimeMs,
                ProgramInformation programInformation,
                UtcTimingElement utcTiming,
                Uri location,
                List<Period> periods) {
            return new DashManifest(
                    availabilityStartTime,
                    durationMs,
                    minBufferTimeMs,
                    false,
                    minUpdateTimeMs,
                    timeShiftBufferDepthMs,
                    suggestedPresentationDelayMs,
                    publishTimeMs,
                    programInformation,
                    utcTiming,
                    location,
                    periods);
        }
    }

    // EXO: 2.13
    //private static class StaticDashManifestParser extends DashManifestParser {
    //    @Override
    //    protected DashManifest buildMediaPresentationDescription(
    //            long availabilityStartTime,
    //            long durationMs,
    //            long minBufferTimeMs,
    //            boolean dynamic,
    //            long minUpdateTimeMs,
    //            long timeShiftBufferDepthMs,
    //            long suggestedPresentationDelayMs,
    //            long publishTimeMs,
    //            @Nullable ProgramInformation programInformation,
    //            @Nullable UtcTimingElement utcTiming,
    //            @Nullable ServiceDescriptionElement serviceDescription,
    //            @Nullable Uri location,
    //            List<Period> periods) {
    //        return new DashManifest(
    //                availabilityStartTime,
    //                durationMs,
    //                minBufferTimeMs,
    //                false,
    //                minUpdateTimeMs,
    //                timeShiftBufferDepthMs,
    //                suggestedPresentationDelayMs,
    //                publishTimeMs,
    //                programInformation,
    //                utcTiming,
    //                serviceDescription,
    //                location,
    //                periods);
    //    }
    //}
}
