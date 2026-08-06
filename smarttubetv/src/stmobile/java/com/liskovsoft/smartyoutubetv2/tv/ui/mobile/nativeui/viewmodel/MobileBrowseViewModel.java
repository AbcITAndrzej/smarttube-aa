package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel;

import android.os.Looper;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileRequestSlot;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;

public final class MobileBrowseViewModel extends ViewModel {
    private final MobileBrowseRepository repository;
    private final String pageId;
    private final String itemId;
    private final MobileRequestSlot requestSlot = new MobileRequestSlot();
    private final MutableLiveData<MobileLoadState<MobileBrowsePayload>> state =
            new MutableLiveData<>(MobileLoadState.<MobileBrowsePayload>idle());

    public MobileBrowseViewModel(MobileBrowseRepository repository, String pageId, String itemId) {
        this.repository = repository;
        this.pageId = pageId;
        this.itemId = itemId;
    }

    public LiveData<MobileLoadState<MobileBrowsePayload>> getState() { return state; }

    public void load() { load(false); }
    public void refresh() { load(true); }

    private void load(boolean refreshing) {
        MobileLoadState<MobileBrowsePayload> current = state.getValue();
        MobileBrowsePayload previous = current == null ? null : current.getData();
        state.setValue(MobileLoadState.loading(previous, refreshing));
        if (refreshing && (itemId == null || itemId.isEmpty())) {
            repository.invalidateBrowse(pageId);
        }
        final long token = requestSlot.begin();
        MobileResultCallback<MobileBrowsePayload> callback = new MobileResultCallback<MobileBrowsePayload>() {
            @Override public void onSuccess(MobileBrowsePayload value) {
                if (requestSlot.isCurrent(token)) {
                    publish(MobileLoadState.content(value));
                    prefetchSlowHomeCategories();
                }
            }
            @Override public void onError(MobileError error) {
                if (requestSlot.isCurrent(token)) publish(MobileLoadState.error(previous, error));
            }
        };
        MobileRequest request = itemId == null || itemId.isEmpty()
                ? repository.loadBrowse(pageId, callback)
                : repository.loadItem(itemId, callback);
        requestSlot.attach(token, request);
    }

    private void publish(MobileLoadState<MobileBrowsePayload> value) {
        if (Looper.myLooper() == Looper.getMainLooper()) state.setValue(value);
        else state.postValue(value);
    }

    private void prefetchSlowHomeCategories() {
        if (itemId != null && !itemId.isEmpty()) return;
        if (!"home".equals(pageId)) return;
        repository.prefetchBrowse("trending");
        repository.prefetchBrowse("news");
    }

    @Override protected void onCleared() { requestSlot.clear(); }
}
