package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.automotive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline.OfflineMediaRepository;
import org.junit.Test;

public class AndroidAutoOfflineRoutingTest {
    @Test
    public void explicitOfflineNeverFallsBackToNetwork() {
        assertTrue(AndroidAutoOfflineRouting.shouldUseOffline(
                true, true, false, true, true));
        assertTrue(AndroidAutoOfflineRouting.shouldUseOffline(
                true, true, false, true, false));
    }

    @Test
    public void automaticFallbackOnlyTriggersWhenNetworkIsUnavailable() {
        assertTrue(AndroidAutoOfflineRouting.shouldUseOffline(
                false, true, true, false, true));
        assertFalse(AndroidAutoOfflineRouting.shouldUseOffline(
                false, true, true, true, true));
    }

    @Test
    public void savedFallbackDoesNotStickAfterNetworkReturns() {
        assertTrue(AndroidAutoOfflineRouting.shouldForceSavedOffline(
                true, false, true, true));
        assertTrue(AndroidAutoOfflineRouting.shouldForceSavedOffline(
                false, true, false, true));
        assertFalse(AndroidAutoOfflineRouting.shouldForceSavedOffline(
                false, true, true, true));
    }

    @Test
    public void snapshotMatchesOfflinePlaybackId() {
        assertTrue(AndroidAutoOfflineRouting.snapshotMatches(
                OfflineMediaRepository.playbackId("abc"), "abc", "offline:abc"));
        assertFalse(AndroidAutoOfflineRouting.snapshotMatches(
                OfflineMediaRepository.playbackId("abc"), "abc", "offline:def"));
    }

    @Test
    public void findsNextDownloadedItemAndCanWrap() {
        boolean[] local = {false, false, true, false, true};
        assertEquals(2, AndroidAutoOfflineRouting.findNextLocalIndex(1, local, false));
        assertEquals(-1, AndroidAutoOfflineRouting.findNextLocalIndex(4, new boolean[]{true, false, false, false, false}, false));
        assertEquals(0, AndroidAutoOfflineRouting.findNextLocalIndex(4, new boolean[]{true, false, false, false, false}, true));
    }
}
