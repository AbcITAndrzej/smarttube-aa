package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import java.util.Collections;

/**
 * Small service locator used only by the native-mobile source set.
 * Application startup may replace the default provider with adapters backed by SmartTube.
 */
public final class MobileNativeDependencies {
    public interface Provider {
        MobileBrowseRepository browseRepository();
        MobileChannelRepository channelRepository();
        MobileSearchRepository searchRepository();
        MobileSettingsRepository settingsRepository();
        MobilePlaybackRepository playbackRepository();
        MobileImageLoader imageLoader();
        MobilePlayerViewBinder playerViewBinder();
        void openLegacyPlayback(Context context, String mediaId, long startPositionMs);
    }

    private static volatile Provider sProvider = new UnconfiguredProvider();

    private MobileNativeDependencies() {}

    public static Provider get() {
        return sProvider;
    }

    public static boolean isConfigured() {
        return !(sProvider instanceof UnconfiguredProvider);
    }

    public static void install(Provider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider == null");
        }
        sProvider = provider;
    }

    public static void resetForTests() {
        sProvider = new UnconfiguredProvider();
    }

    private static final class UnconfiguredProvider implements Provider {
        private final MobileBrowseRepository browse = (pageId, callback) -> {
            callback.onError(MobileError.unconfigured("MobileBrowseRepository"));
            return MobileRequest.NONE;
        };
        private final MobileChannelRepository channel = (channelId, callback) -> {
            callback.onError(MobileError.unconfigured("MobileChannelRepository"));
            return MobileRequest.NONE;
        };
        private final MobileSearchRepository search = new MobileSearchRepository() {
            @Override
            public MobileRequest search(String query, MobileResultCallback<MobileSearchPayload> callback) {
                callback.onError(MobileError.unconfigured("MobileSearchRepository"));
                return MobileRequest.NONE;
            }

            @Override
            public MobileRequest suggest(String query, MobileResultCallback<java.util.List<String>> callback) {
                callback.onSuccess(Collections.<String>emptyList());
                return MobileRequest.NONE;
            }
        };
        private final MobileSettingsRepository settings = new MobileSettingsRepository() {
            @Override
            public MobileRequest loadSettings(MobileResultCallback<java.util.List<MobileSettingItem>> callback) {
                callback.onError(MobileError.unconfigured("MobileSettingsRepository"));
                return MobileRequest.NONE;
            }

            @Override
            public MobileRequest updateSetting(String settingId, String value,
                                               MobileResultCallback<MobileSettingItem> callback) {
                callback.onError(MobileError.unconfigured("MobileSettingsRepository"));
                return MobileRequest.NONE;
            }
        };
        private final MobilePlaybackRepository playback = new MobilePlaybackRepository() {
            private Listener listener;
            @Override public void setListener(Listener value) { listener = value; }
            @Override public void prepare(String mediaId, long startPositionMs) {
                if (listener != null) listener.onPlaybackError(MobileError.unconfigured("MobilePlaybackRepository"));
            }
            @Override public void play() {}
            @Override public void pause() {}
            @Override public void playNext() {}
            @Override public void playPrevious() {}
            @Override public void seekTo(long positionMs) {}
            @Override public void seekBy(long deltaMs) {}
            @Override public void setPlaybackSpeed(float speed) {}
            @Override public void selectVideoTrack(String trackId) {}
            @Override public void selectAudioTrack(String trackId) {}
            @Override public void selectSubtitleTrack(String trackId) {}
            @Override public void setResizeMode(int resizeMode) {}
            @Override public void release() { listener = null; }
        };
        private final MobileImageLoader images = new MobileImageLoader() {
            @Override public void load(ImageView target, String url) { target.setImageDrawable(null); }
            @Override public void clear(ImageView target) { target.setImageDrawable(null); }
        };
        private final MobilePlayerViewBinder binder = (container, repository) -> {
            TextView placeholder = new TextView(container.getContext());
            placeholder.setText("Player surface adapter not connected");
            placeholder.setGravity(android.view.Gravity.CENTER);
            container.removeAllViews();
            container.addView(placeholder, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return () -> container.removeAllViews();
        };

        @Override public MobileBrowseRepository browseRepository() { return browse; }
        @Override public MobileChannelRepository channelRepository() { return channel; }
        @Override public MobileSearchRepository searchRepository() { return search; }
        @Override public MobileSettingsRepository settingsRepository() { return settings; }
        @Override public MobilePlaybackRepository playbackRepository() { return playback; }
        @Override public MobileImageLoader imageLoader() { return images; }
        @Override public MobilePlayerViewBinder playerViewBinder() { return binder; }
        @Override public void openLegacyPlayback(Context context, String mediaId, long startPositionMs) { }
    }
}
