package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.Display;

/** Controls the opt-in CAR_LAUNCHER component without touching the stable MediaBrowser service. */
public final class ExperimentalCarVideoGate {
    private ExperimentalCarVideoGate() { }

    public static ComponentName component(Context context) {
        return new ComponentName(context, ExperimentalCarVideoActivity.class);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getApplicationContext().getPackageManager().setComponentEnabledSetting(
                component(context),
                enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    public static boolean isComponentEnabled(Context context) {
        int state = context.getPackageManager().getComponentEnabledSetting(component(context));
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    /** Diagnostic only: Android Auto parked activities normally run on a non-default display. */
    @SuppressWarnings("deprecation")
    public static boolean isSecondaryDisplay(Activity activity) {
        Display display = Build.VERSION.SDK_INT >= 30
                ? activity.getDisplay() : activity.getWindowManager().getDefaultDisplay();
        return display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY;
    }
}
