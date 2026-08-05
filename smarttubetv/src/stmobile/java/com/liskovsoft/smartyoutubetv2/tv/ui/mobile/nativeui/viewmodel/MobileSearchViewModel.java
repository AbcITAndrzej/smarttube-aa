package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core.MobileRequestSlot;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import java.util.Collections;
import java.util.List;

public final class MobileSearchViewModel extends ViewModel {
    private final MobileSearchRepository repository;
    private final MobileRequestSlot searchSlot = new MobileRequestSlot();
    private final MobileRequestSlot suggestionSlot = new MobileRequestSlot();
    private final MutableLiveData<MobileLoadState<MobileSearchPayload>> state =
            new MutableLiveData<>(MobileLoadState.<MobileSearchPayload>idle());
    private final MutableLiveData<List<String>> suggestions =
            new MutableLiveData<>(Collections.<String>emptyList());
    private String query;

    public MobileSearchViewModel(MobileSearchRepository repository, String initialQuery) {
        this.repository = repository;
        this.query = initialQuery == null ? "" : initialQuery.trim();
    }

    public LiveData<MobileLoadState<MobileSearchPayload>> getState() { return state; }
    public LiveData<List<String>> getSuggestions() { return suggestions; }
    public String getQuery() { return query; }

    public void requestSuggestions(String value) {
        query = value == null ? "" : value.trim();
        if (query.length() < 2) {
            suggestionSlot.clear();
            suggestions.setValue(Collections.<String>emptyList());
            return;
        }
        final long token = suggestionSlot.begin();
        MobileRequest request = repository.suggest(query, new MobileResultCallback<List<String>>() {
            @Override public void onSuccess(List<String> value) {
                if (suggestionSlot.isCurrent(token)) suggestions.postValue(
                        value == null ? Collections.<String>emptyList() : value);
            }
            @Override public void onError(MobileError error) {
                if (suggestionSlot.isCurrent(token)) suggestions.postValue(Collections.<String>emptyList());
            }
        });
        suggestionSlot.attach(token, request);
    }

    public void search(String value) {
        query = value == null ? "" : value.trim();
        if (query.isEmpty()) return;
        MobileSearchPayload previous = state.getValue() == null ? null : state.getValue().getData();
        state.setValue(MobileLoadState.loading(previous, false));
        final long token = searchSlot.begin();
        MobileRequest request = repository.search(query, new MobileResultCallback<MobileSearchPayload>() {
            @Override public void onSuccess(MobileSearchPayload value) {
                if (searchSlot.isCurrent(token)) state.postValue(MobileLoadState.content(value));
            }
            @Override public void onError(MobileError error) {
                if (searchSlot.isCurrent(token)) state.postValue(MobileLoadState.error(previous, error));
            }
        });
        searchSlot.attach(token, request);
    }

    @Override protected void onCleared() {
        searchSlot.clear();
        suggestionSlot.clear();
    }
}
