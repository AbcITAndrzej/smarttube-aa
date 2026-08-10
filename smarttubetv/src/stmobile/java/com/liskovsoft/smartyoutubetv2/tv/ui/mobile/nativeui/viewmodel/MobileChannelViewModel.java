package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileRequestSlot;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;

public final class MobileChannelViewModel extends ViewModel {
    private final MobileChannelRepository repository;
    private final String channelId;
    private final MobileRequestSlot slot = new MobileRequestSlot();
    private final MutableLiveData<MobileLoadState<MobileChannelPayload>> state =
            new MutableLiveData<>(MobileLoadState.<MobileChannelPayload>idle());
    private boolean loadingMore;

    public MobileChannelViewModel(MobileChannelRepository repository, String channelId) {
        this.repository = repository;
        this.channelId = channelId;
    }

    public LiveData<MobileLoadState<MobileChannelPayload>> getState() { return state; }

    public void load() {
        loadingMore = false;
        MobileChannelPayload previous = state.getValue() == null ? null : state.getValue().getData();
        state.setValue(MobileLoadState.loading(previous, false));
        final long token = slot.begin();
        MobileRequest request = repository.loadChannel(channelId, new MobileResultCallback<MobileChannelPayload>() {
            @Override public void onSuccess(MobileChannelPayload value) {
                if (slot.isCurrent(token)) state.postValue(MobileLoadState.content(value));
            }
            @Override public void onError(MobileError error) {
                if (slot.isCurrent(token)) state.postValue(MobileLoadState.error(previous, error));
            }
        });
        slot.attach(token, request);
    }

    public void loadMore() {
        MobileLoadState<MobileChannelPayload> current = state.getValue();
        MobileChannelPayload previous = current == null ? null : current.getData();
        if (loadingMore || previous == null || !previous.hasMore()) return;
        loadingMore = true;
        final long token = slot.begin();
        MobileRequest request = repository.loadMoreChannel(channelId,
                new MobileResultCallback<MobileChannelPayload>() {
                    @Override public void onSuccess(MobileChannelPayload value) {
                        loadingMore = false;
                        if (slot.isCurrent(token)) state.postValue(MobileLoadState.content(value));
                    }

                    @Override public void onError(MobileError error) {
                        loadingMore = false;
                        if (slot.isCurrent(token)) state.postValue(MobileLoadState.error(previous, error));
                    }
                });
        slot.attach(token, request);
    }

    @Override protected void onCleared() { slot.clear(); }
}
