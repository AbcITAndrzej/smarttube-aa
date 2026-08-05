package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.contract;

/** Cancel handle returned by asynchronous repository operations. */
public interface MobileRequest {
    MobileRequest NONE = new MobileRequest() {
        @Override
        public void cancel() {
            // Intentionally empty.
        }
    };

    void cancel();
}
