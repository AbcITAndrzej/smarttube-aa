package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileRequestSlot;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import java.util.ArrayList;
import java.util.List;

public final class MobileSettingsViewModel extends ViewModel {
    private final MobileSettingsRepository repository;
    private final MobileRequestSlot loadSlot = new MobileRequestSlot();
    private final MobileRequestSlot updateSlot = new MobileRequestSlot();
    private final MutableLiveData<MobileLoadState<List<MobileSettingItem>>> state =
            new MutableLiveData<>(MobileLoadState.<List<MobileSettingItem>>idle());

    public MobileSettingsViewModel(MobileSettingsRepository repository) {
        this.repository = repository;
    }

    public LiveData<MobileLoadState<List<MobileSettingItem>>> getState() { return state; }

    public void load() {
        List<MobileSettingItem> previous = state.getValue() == null ? null : state.getValue().getData();
        state.setValue(MobileLoadState.loading(previous, false));
        final long token = loadSlot.begin();
        MobileRequest request = repository.loadSettings(new MobileResultCallback<List<MobileSettingItem>>() {
            @Override public void onSuccess(List<MobileSettingItem> value) {
                if (loadSlot.isCurrent(token)) state.postValue(MobileLoadState.content(
                        value == null ? new ArrayList<MobileSettingItem>() : value));
            }
            @Override public void onError(MobileError error) {
                if (loadSlot.isCurrent(token)) state.postValue(MobileLoadState.error(previous, error));
            }
        });
        loadSlot.attach(token, request);
    }

    public void update(MobileSettingItem item, String newValue) {
        if (item == null || item.getId() == null || !item.isEnabled()) return;
        final long token = updateSlot.begin();
        MobileRequest request = repository.updateSetting(item.getId(), newValue,
                new MobileResultCallback<MobileSettingItem>() {
                    @Override public void onSuccess(MobileSettingItem updated) {
                        if (!updateSlot.isCurrent(token) || updated == null) return;
                        List<MobileSettingItem> old = state.getValue() == null ? null : state.getValue().getData();
                        if (old == null) return;
                        List<MobileSettingItem> copy = new ArrayList<>(old.size());
                        for (MobileSettingItem current : old) {
                            copy.add(current.getId() != null && current.getId().equals(updated.getId())
                                    ? updated : current);
                        }
                        state.postValue(MobileLoadState.content(copy));
                    }
                    @Override public void onError(MobileError error) {
                        List<MobileSettingItem> old = state.getValue() == null ? null : state.getValue().getData();
                        if (updateSlot.isCurrent(token)) state.postValue(MobileLoadState.error(old, error));
                    }
                });
        updateSlot.attach(token, request);
    }

    @Override protected void onCleared() {
        loadSlot.clear();
        updateSlot.clear();
    }
}
