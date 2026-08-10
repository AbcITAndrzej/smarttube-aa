package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfflineTripReserveRepositoryTest {
    @Test public void syntheticIdsRoundTripWithoutTouchingNormalPlaylists() {
        assertFalse(OfflineTripReserveRepository.isTripReservePlaylistId("PL123"));
        assertTrue(OfflineTripReserveRepository.isTripReservePlaylistId(
                OfflineTripReserveRepository.RECENT_PLAYLIST_ID));
        assertTrue(OfflineTripReserveRepository.isTripReservePlaylistId(
                OfflineTripReserveRepository.FAVORITES_PLAYLIST_ID));

        String copy = OfflineTripReserveRepository.copyPlaylistId("PLabc123");
        assertEquals("trip:playlist:PLabc123", copy);
        assertEquals("PLabc123", OfflineTripReserveRepository.originalPlaylistId(copy));
        assertEquals("", OfflineTripReserveRepository.originalPlaylistId("PLabc123"));
    }

    @Test public void blankPlaylistIdNeverCreatesManagedCopy() {
        assertEquals("", OfflineTripReserveRepository.copyPlaylistId(null));
        assertEquals("", OfflineTripReserveRepository.copyPlaylistId("   "));
    }
}
