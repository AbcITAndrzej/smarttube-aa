package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.radio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import org.robolectric.RuntimeEnvironment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class RadioPreferencesTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("smarttube_mobile_radio", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test public void timeShiftDefaultsToThreeMinutesAndEnabled() {
        RadioPreferences preferences = new RadioPreferences(context);
        assertTrue(preferences.isTimeShiftEnabled());
        assertEquals(3, preferences.getTimeShiftMinutes());
        assertEquals(180_000L, preferences.getTimeShiftWindowMs());
    }

    @Test public void supportedDurationsAreNormalized() {
        RadioPreferences preferences = new RadioPreferences(context);
        preferences.setTimeShiftMinutes(2);
        assertEquals(3, preferences.getTimeShiftMinutes());
        preferences.setTimeShiftMinutes(9);
        assertEquals(5, preferences.getTimeShiftMinutes());
        preferences.setTimeShiftEnabled(false);
        assertFalse(preferences.isTimeShiftEnabled());
    }
}
