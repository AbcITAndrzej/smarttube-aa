package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.viewmodel;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.*;
import java.util.Collections;
import org.junit.*;
import static org.junit.Assert.*;

public class MobileBrowseViewModelTest {
    @Rule public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    @Test public void successProducesContentState() {
        MobileBrowseRepository repo = (page, callback) -> {
            callback.onSuccess(new MobileBrowsePayload("Home", Collections.<MobileSection>emptyList()));
            return MobileRequest.NONE;
        };
        MobileBrowseViewModel vm = new MobileBrowseViewModel(repo, "home"); vm.load();
        assertEquals(MobileLoadState.Status.CONTENT, vm.getState().getValue().getStatus());
        assertEquals("Home", vm.getState().getValue().getData().getTitle());
    }
    @Test public void failureKeepsErrorTyped() {
        MobileBrowseRepository repo = (page, callback) -> {
            callback.onError(new MobileError(MobileError.Kind.NETWORK, "offline", null, true));
            return MobileRequest.NONE;
        };
        MobileBrowseViewModel vm = new MobileBrowseViewModel(repo, "home"); vm.load();
        assertEquals(MobileLoadState.Status.ERROR, vm.getState().getValue().getStatus());
        assertTrue(vm.getState().getValue().getError().isRetryable());
    }
}
