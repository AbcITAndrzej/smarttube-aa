package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.playbackengine;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Media3MigrationPolicyTest {
    @Test public void masterFlagDisablesEntireWave() {
        assertFalse(Media3MigrationPolicy.shouldUseMedia3(
                Media3MigrationPolicy.SourceKind.RADIO, false, true, true));
        assertFalse(Media3MigrationPolicy.shouldUseMedia3(
                Media3MigrationPolicy.SourceKind.OFFLINE, false, true, true));
    }

    @Test public void vodRemainsOnLegacyDuringStage11() {
        assertFalse(Media3MigrationPolicy.shouldUseMedia3(
                Media3MigrationPolicy.SourceKind.VOD, true, true, true));
    }

    @Test public void radioAndOfflineCanBeRolledOutIndependently() {
        assertTrue(Media3MigrationPolicy.shouldUseMedia3(
                Media3MigrationPolicy.SourceKind.RADIO, true, true, false));
        assertFalse(Media3MigrationPolicy.shouldUseMedia3(
                Media3MigrationPolicy.SourceKind.OFFLINE, true, true, false));

        assertFalse(Media3MigrationPolicy.shouldUseMedia3(
                Media3MigrationPolicy.SourceKind.RADIO, true, false, true));
        assertTrue(Media3MigrationPolicy.shouldUseMedia3(
                Media3MigrationPolicy.SourceKind.OFFLINE, true, false, true));
    }

    @Test public void nullSourceIsNeverMigrated() {
        assertFalse(Media3MigrationPolicy.shouldUseMedia3(null, true, true, true));
    }
}
