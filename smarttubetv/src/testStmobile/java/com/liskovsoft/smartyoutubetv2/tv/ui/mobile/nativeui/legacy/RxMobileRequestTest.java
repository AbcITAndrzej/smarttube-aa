package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import io.reactivex.disposables.Disposable;
import org.junit.Test;

public class RxMobileRequestTest {
    @Test public void cancellationIsIdempotent() {
        Disposable disposable = mock(Disposable.class);
        when(disposable.isDisposed()).thenReturn(false);
        RxMobileRequest request = new RxMobileRequest(disposable);
        request.cancel();
        request.cancel();
        verify(disposable, times(1)).dispose();
        assertTrue(request.isCancelled());
    }
}
