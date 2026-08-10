package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import android.content.Context;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileNativeDependencies;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileDiagnosticsStore;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.diagnostics.MobileFeatureFlags;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;

/** Production Part 7 provider connecting native mobile ViewModels to current SmartTube services. */
public final class SmartTubeMobileNativeProvider implements MobileNativeDependencies.Provider {
    private final MobileBrowseRepository browse;
    private final MobileChannelRepository channel;
    private final MobileSearchRepository search;
    private final MobileSettingsRepository settings;
    private final Context applicationContext;
    private final LegacyMediaIndex index;
    private final LegacyErrorMapper errors;
    private final MobileImageLoader images = new LegacyGlideImageLoader();
    private final MobilePlayerViewBinder binder = new LegacyMobilePlayerViewBinder();

    public static SmartTubeMobileNativeProvider create(Context context) {
        if (context == null) throw new IllegalArgumentException("context == null");
        return new SmartTubeMobileNativeProvider(context.getApplicationContext(), true);
    }

    /** Stable Android Auto data path: mobile-only card enhancements are intentionally excluded. */
    public static SmartTubeMobileNativeProvider createForAutomotive(Context context) {
        if (context == null) throw new IllegalArgumentException("context == null");
        return new SmartTubeMobileNativeProvider(context.getApplicationContext(), false);
    }

    private SmartTubeMobileNativeProvider(Context context, boolean enableMobileListEnhancements) {
        ServiceManager service = YouTubeServiceManager.instance();
        if (service == null) throw new IllegalStateException("YouTubeServiceManager returned null");
        applicationContext = context.getApplicationContext();
        MobileDiagnosticsStore diagnostics = MobileDiagnosticsStore.get(applicationContext);
        diagnostics.syncCaptureFlag();
        MobileFeatureFlags featureFlags = new MobileFeatureFlags(applicationContext);
        MobileDiagnostics.info("DataProvider", "Installing SmartTube mobile data adapters");
        index = new LegacyMediaIndex();
        errors = new LegacyErrorMapper();
        MobileMetadataEnhancer metadataEnhancer = enableMobileListEnhancements
                ? new MobileMetadataEnhancer(applicationContext) : null;
        LegacyMediaMapper mapper = new LegacyMediaMapper(index, metadataEnhancer);
        browse = new LegacyBrowseRepository(service.getContentService(),
                service.getNotificationsService(), index, mapper, errors, metadataEnhancer);
        channel = new LegacyChannelRepository(service.getContentService(), index, mapper, errors,
                metadataEnhancer, featureFlags, diagnostics);
        search = new LegacySearchRepository(service.getContentService(), mapper, errors,
                metadataEnhancer, featureFlags, diagnostics);
        settings = new LegacySettingsRepository(applicationContext, errors);
    }

    @Override public MobileBrowseRepository browseRepository() { return browse; }
    @Override public MobileChannelRepository channelRepository() { return channel; }
    @Override public MobileSearchRepository searchRepository() { return search; }
    @Override public MobileSettingsRepository settingsRepository() { return settings; }
    @Override public MobilePlaybackRepository playbackRepository() {
        // Playback owns Activity-bound surfaces and lifecycle callbacks, so it must never be shared
        // between two Fragment/ViewModel lifecycles after configuration changes.
        return new LegacyMobilePlaybackRepository(applicationContext, index, errors);
    }

    /** Dedicated Android Auto player. It may initialize ExoPlayer without Activity or PlayerView. */
    public MobilePlaybackRepository automotivePlaybackRepository() {
        return new LegacyMobilePlaybackRepository(applicationContext, index, errors, true);
    }
    @Override public MobileImageLoader imageLoader() { return images; }
    @Override public MobilePlayerViewBinder playerViewBinder() { return binder; }

    @Override public void openLegacyPlayback(Context context, String mediaId, long startPositionMs) {
        Video video = index.get(mediaId);
        if (video == null) {
            video = Video.from(mediaId);
        }
        if (video == null) return;
        if (startPositionMs > 0) video.pendingPosMs = startPositionMs;
        PlaybackPresenter.instance(context).openVideo(video);
    }
}
