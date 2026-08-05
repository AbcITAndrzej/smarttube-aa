package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileRequest;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RxMobileRequest implements MobileRequest {
    private final Disposable disposable;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public RxMobileRequest(Disposable disposable) { this.disposable = disposable; }

    @Override public void cancel() {
        if (cancelled.compareAndSet(false, true) && disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    public boolean isCancelled() { return cancelled.get(); }
}
