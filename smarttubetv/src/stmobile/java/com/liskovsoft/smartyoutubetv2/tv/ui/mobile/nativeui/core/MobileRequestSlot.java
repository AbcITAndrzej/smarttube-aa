package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.core;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract.MobileRequest;

/** Cancels superseded requests and rejects stale callbacks after navigation/query changes. */
public final class MobileRequestSlot {
    private long generation;
    private MobileRequest request = MobileRequest.NONE;

    public synchronized long begin() {
        request.cancel();
        request = MobileRequest.NONE;
        return ++generation;
    }

    public synchronized void attach(long token, MobileRequest newRequest) {
        MobileRequest safe = newRequest == null ? MobileRequest.NONE : newRequest;
        if (token != generation) {
            safe.cancel();
            return;
        }
        request = safe;
    }

    public synchronized boolean isCurrent(long token) {
        return token == generation;
    }

    public synchronized void clear() {
        ++generation;
        request.cancel();
        request = MobileRequest.NONE;
    }
}
