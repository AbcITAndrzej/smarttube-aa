package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OfflineMediaPreferencesTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("smarttube_mobile_offline", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test public void foundationDefaultsAreSafeAndEnabled() {
        OfflineMediaPreferences preferences = new OfflineMediaPreferences(context);
        assertTrue(preferences.isFoundationEnabled());
        assertTrue(preferences.isAutoCleanupEnabled());
        assertEquals(2, preferences.getStorageLimitGb());
        assertEquals(512, preferences.getReservedFreeMb());
    }

    @Test public void unsupportedStorageValuesAreNormalized() {
        OfflineMediaPreferences preferences = new OfflineMediaPreferences(context);
        preferences.setStorageLimitGb(3);
        preferences.setReservedFreeMb(333);
        assertEquals(2, preferences.getStorageLimitGb());
        assertEquals(512, preferences.getReservedFreeMb());
        preferences.setFoundationEnabled(false);
        preferences.setAutoCleanupEnabled(false);
        assertFalse(preferences.isFoundationEnabled());
        assertFalse(preferences.isAutoCleanupEnabled());
    }
    @Test public void listenSaveDefaultsProtectBandwidthAndStorage() {
        OfflineMediaPreferences preferences = new OfflineMediaPreferences(context);
        assertFalse(preferences.isListenSaveEnabled());
        assertTrue(preferences.isListenSaveWifiOnly());
        assertTrue(preferences.isListenSaveCompleteAfterSwitch());
        assertEquals(50, preferences.getListenSaveRecentLimit());
        assertEquals(15, preferences.getListenSaveThresholdSec());
    }

    @Test public void listenSavePolicyValuesAreNormalized() {
        OfflineMediaPreferences preferences = new OfflineMediaPreferences(context);
        preferences.setListenSaveEnabled(true);
        preferences.setListenSaveWifiOnly(false);
        preferences.setListenSaveCompleteAfterSwitch(false);
        preferences.setListenSaveRecentLimit(77);
        preferences.setListenSaveThresholdSec(17);
        assertTrue(preferences.isListenSaveEnabled());
        assertFalse(preferences.isListenSaveWifiOnly());
        assertFalse(preferences.isListenSaveCompleteAfterSwitch());
        assertEquals(50, preferences.getListenSaveRecentLimit());
        assertEquals(15, preferences.getListenSaveThresholdSec());
    }

}
