package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class OfflineDownloadCoordinatorTest {
    @After public void cleanup() {
        OfflineDownloadCoordinator.release("listen-save", "a");
        OfflineDownloadCoordinator.release("playlist", "b");
        OfflineDownloadCoordinator.release("listen-save", "same");
        OfflineDownloadCoordinator.release("playlist", "same");
    }

    @Test public void onlyOneOwnerMayWriteAtATime() {
        assertTrue(OfflineDownloadCoordinator.tryAcquire("listen-save", "a"));
        assertTrue(OfflineDownloadCoordinator.isBusy());
        assertEquals("listen-save", OfflineDownloadCoordinator.currentOwner());
        assertFalse(OfflineDownloadCoordinator.tryAcquire("playlist", "b"));
    }

    @Test public void sameOwnerAndMediaMayReenterAndRelease() {
        assertTrue(OfflineDownloadCoordinator.tryAcquire("listen-save", "same"));
        assertTrue(OfflineDownloadCoordinator.tryAcquire("listen-save", "same"));
        OfflineDownloadCoordinator.release("listen-save", "same");
        assertFalse(OfflineDownloadCoordinator.isBusy());
    }

    @Test public void wrongOwnerCannotReleaseForeignSlot() {
        assertTrue(OfflineDownloadCoordinator.tryAcquire("listen-save", "same"));
        OfflineDownloadCoordinator.release("playlist", "same");
        assertTrue(OfflineDownloadCoordinator.isBusy());
        OfflineDownloadCoordinator.release("listen-save", "same");
        assertFalse(OfflineDownloadCoordinator.isBusy());
    }
}
