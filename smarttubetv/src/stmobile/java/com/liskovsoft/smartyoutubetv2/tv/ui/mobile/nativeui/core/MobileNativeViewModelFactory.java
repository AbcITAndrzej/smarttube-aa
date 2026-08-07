package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel.*;

public final class MobileNativeViewModelFactory implements ViewModelProvider.Factory {
    private final MobileNativeDependencies.Provider provider;
    private final Bundle arguments;

    public MobileNativeViewModelFactory(MobileNativeDependencies.Provider provider, Bundle arguments) {
        this.provider = provider;
        this.arguments = arguments == null ? Bundle.EMPTY : arguments;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass == MobileBrowseViewModel.class) {
            return (T) new MobileBrowseViewModel(provider.browseRepository(),
                    arguments.getString("page_id", "home"),
                    arguments.getString("item_id", ""));
        }
        if (modelClass == MobileChannelViewModel.class) {
            return (T) new MobileChannelViewModel(provider.channelRepository(),
                    arguments.getString("channel_id", ""));
        }
        if (modelClass == MobileSearchViewModel.class) {
            return (T) new MobileSearchViewModel(provider.searchRepository(),
                    arguments.getString("query", ""));
        }
        if (modelClass == MobileSettingsViewModel.class) {
            return (T) new MobileSettingsViewModel(provider.settingsRepository());
        }
        if (modelClass == MobilePlaybackViewModel.class) {
            return (T) new MobilePlaybackViewModel(provider.playbackRepository(),
                    arguments.getString("media_id", ""),
                    arguments.getLong("start_position_ms", 0),
                    arguments.getStringArrayList("playback_queue"));
        }
        throw new IllegalArgumentException("Unknown ViewModel: " + modelClass.getName());
    }
}
