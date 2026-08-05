package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import java.util.Collections;
import java.util.List;
import org.junit.*;
import static org.junit.Assert.*;

public class MobileSearchViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    @Test public void staleSearchCallbackIsIgnored() {
        FakeSearch repo = new FakeSearch();
        MobileSearchViewModel vm = new MobileSearchViewModel(repo, "");
        vm.search("first");
        MobileResultCallback<MobileSearchPayload> first = repo.callback;
        vm.search("second");
        first.onSuccess(new MobileSearchPayload("first", Collections.emptyList()));
        assertEquals(MobileLoadState.Status.LOADING, vm.getState().getValue().getStatus());
        repo.callback.onSuccess(new MobileSearchPayload("second", Collections.emptyList()));
        assertEquals("second", vm.getState().getValue().getData().getQuery());
    }

    private static final class FakeSearch implements MobileSearchRepository {
        MobileResultCallback<MobileSearchPayload> callback;
        @Override public MobileRequest search(String query, MobileResultCallback<MobileSearchPayload> callback) {
            this.callback = callback;
            return MobileRequest.NONE;
        }
        @Override public MobileRequest suggest(String query, MobileResultCallback<List<String>> callback) {
            callback.onSuccess(Collections.emptyList());
            return MobileRequest.NONE;
        }
    }
}
