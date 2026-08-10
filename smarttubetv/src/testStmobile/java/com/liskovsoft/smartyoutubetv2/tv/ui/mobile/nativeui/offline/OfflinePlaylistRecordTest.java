package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfflinePlaylistRecordTest {
    @Test
    public void progressUsesCompletedTrackCount() {
        OfflinePlaylistRecord record = new OfflinePlaylistRecord(
                "PL123", "Road trip", "", OfflinePlaylistState.DOWNLOADING,
                20, 7, 1, 10_000L, 30_000L, "", 1L, 2L);

        assertEquals(35, record.getProgressPercent());
        assertEquals(12, record.getRemainingCount());
        assertFalse(record.isTerminal());
    }

    @Test
    public void terminalStatesAreRecognized() {
        assertTrue(record(OfflinePlaylistState.AVAILABLE).isTerminal());
        assertTrue(record(OfflinePlaylistState.PARTIAL).isTerminal());
        assertTrue(record(OfflinePlaylistState.FAILED).isTerminal());
        assertFalse(record(OfflinePlaylistState.PAUSED).isTerminal());
    }

    @Test
    public void storageFallbackIsSafe() {
        assertEquals(OfflinePlaylistState.FAILED, OfflinePlaylistState.fromStorage("unknown"));
        assertEquals(OfflinePlaylistItemState.FAILED, OfflinePlaylistItemState.fromStorage(null));
    }

    private static OfflinePlaylistRecord record(OfflinePlaylistState state) {
        return new OfflinePlaylistRecord("PL", "", "", state,
                1, state == OfflinePlaylistState.AVAILABLE ? 1 : 0,
                state == OfflinePlaylistState.FAILED ? 1 : 0,
                0L, 0L, "", 0L, 0L);
    }
}
