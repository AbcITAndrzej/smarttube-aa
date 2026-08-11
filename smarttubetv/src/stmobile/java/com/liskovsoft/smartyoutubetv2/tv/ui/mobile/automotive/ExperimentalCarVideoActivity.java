package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive;

import android.os.Bundle;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import com.liskovsoft.smartyoutubetv2.common.misc.MobileDiagnostics;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.host.MobileNativeActivity;

/**
 * Opt-in parked-car surface. It reuses the normal touch player/UI but is a separate component,
 * so enabling/disabling it cannot alter SmartTubeAutoMusicService or normal AA audio playback.
 */
public final class ExperimentalCarVideoActivity extends MobileNativeActivity {
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isFinishing()) return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        MobileDiagnostics.info("P18-AA-Video", "isolated parked surface opened");
    }

    @Override protected boolean isHostAllowed() {
        AndroidAutoPreferences preferences = new AndroidAutoPreferences(this);
        boolean enabled = preferences.isExperimentalParkedVideoEnabled();
        boolean projected = ExperimentalCarVideoGate.isSecondaryDisplay(this);
        if (!enabled || !projected) {
            MobileDiagnostics.warn("P18-AA-Video", "surface rejected enabled=" + enabled
                    + " secondaryDisplay=" + projected);
        }
        return enabled && projected;
    }
    @Override protected boolean shouldRequestNotificationPermission() {
        return false;
    }

    @Override protected boolean shouldRunStartupFlow() {
        return false;
    }
}
