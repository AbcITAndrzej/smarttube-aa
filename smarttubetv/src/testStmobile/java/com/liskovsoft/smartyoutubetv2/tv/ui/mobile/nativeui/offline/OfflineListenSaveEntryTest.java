package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import org.junit.Test;

import static org.junit.Assert.*;

public class OfflineListenSaveEntryTest {
    @Test public void progressIsClampedToHundredPercent() {
        OfflineListenSaveEntry entry = entry(150L, 100L);
        assertEquals(100, entry.getProgressPercent());
    }

    @Test public void unknownTotalReportsZeroProgress() {
        OfflineListenSaveEntry entry = entry(50L, 0L);
        assertEquals(0, entry.getProgressPercent());
    }

    @Test public void descriptorContainsMetadataButNoSignedUrlField() {
        OfflineListenSaveEntry entry = new OfflineListenSaveEntry(
                "video123", "Title", "Author", "https://img.example/thumb.jpg",
                123_000L, OfflineListenSaveState.AVAILABLE,
                100L, 100L, "", 1, 1L, 2L, 3L);
        OfflineMediaDescriptor descriptor = entry.toDescriptor("audio/mp4", "mp4a.40.2");
        assertEquals("video123", descriptor.getMediaId());
        assertEquals("Title", descriptor.getTitle());
        assertEquals("Author", descriptor.getAuthor());
        assertEquals("audio/mp4", descriptor.getMimeType());
        assertEquals("mp4a.40.2", descriptor.getCodec());
    }

    private static OfflineListenSaveEntry entry(long downloaded, long total) {
        return new OfflineListenSaveEntry("id", "", "", "", 0L,
                OfflineListenSaveState.DOWNLOADING, downloaded, total,
                "", 0, 0L, 0L, 0L);
    }
}
