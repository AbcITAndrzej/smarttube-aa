package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import static org.junit.Assert.*;
import org.junit.Test;

public class LegacyBrowsePageTest {
    @Test public void aliasesAreNormalized() {
        assertEquals(LegacyBrowsePage.HOME, LegacyBrowsePage.from("start"));
        assertEquals(LegacyBrowsePage.SUBSCRIPTIONS, LegacyBrowsePage.from("subs"));
        assertEquals(LegacyBrowsePage.MY_VIDEOS, LegacyBrowsePage.from("myvideos"));
        assertEquals(LegacyBrowsePage.PLAYLISTS, LegacyBrowsePage.from("user playlists"));
    }

    @Test public void unknownPageFallsBackToHome() {
        assertEquals(LegacyBrowsePage.HOME, LegacyBrowsePage.from("missing-page"));
        assertEquals(LegacyBrowsePage.HOME, LegacyBrowsePage.from(null));
    }

    @Test public void pageChoosesExpectedServiceShape() {
        assertEquals(LegacyBrowsePage.Source.ROWS, LegacyBrowsePage.HOME.source());
        assertEquals(LegacyBrowsePage.Source.GRID, LegacyBrowsePage.HISTORY.source());
    }
}
