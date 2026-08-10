package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfflineFileKeyTest {
    @Test public void keyIsDeterministicSafeAndDoesNotExposeMediaId() {
        String mediaId = "abc/../video?id=123";
        String first = OfflineFileKey.fromMediaId(mediaId);
        String second = OfflineFileKey.fromMediaId(mediaId);
        assertEquals(first, second);
        assertEquals(64, first.length());
        assertTrue(first.matches("[0-9a-f]{64}"));
        assertFalse(first.contains("video"));
        assertFalse(first.contains("/"));
    }

    @Test public void differentIdsProduceDifferentKeys() {
        assertFalse(OfflineFileKey.fromMediaId("video-a").equals(OfflineFileKey.fromMediaId("video-b")));
    }
}
