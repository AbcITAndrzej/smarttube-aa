package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import org.junit.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class LegacyGroupPaginatorTest {
    @Test public void continuesShelvesRoundRobinAndKeepsAccumulatedPages() {
        FakeGroup first = new FakeGroup("first-1", "next-a");
        FakeGroup second = new FakeGroup("second-1", "next-b");
        LegacyGroupPaginator paginator = new LegacyGroupPaginator(
                java.util.Arrays.<MediaGroup>asList(first, second));

        assertEquals(0, paginator.nextSlotIndex());
        paginator.append(0, new FakeGroup("first-2", "next-a2"));
        assertEquals(1, paginator.nextSlotIndex());
        paginator.append(1, new FakeGroup("second-2", ""));
        assertEquals(0, paginator.nextSlotIndex());

        List<List<MediaGroup>> pages = paginator.accumulatedPages();
        assertEquals(2, pages.size());
        assertEquals(2, pages.get(0).size());
        assertEquals(2, pages.get(1).size());
        assertTrue(paginator.hasMore());

        paginator.markFinished(0);
        assertFalse(paginator.hasMore());
        assertEquals(-1, paginator.nextSlotIndex());
    }

    @Test public void ignoresNullGroupsAndEmptyContinuationKeys() {
        LegacyGroupPaginator paginator = new LegacyGroupPaginator(
                java.util.Arrays.<MediaGroup>asList(null,
                        new FakeGroup("done", ""),
                        new FakeGroup("more", "next")));
        assertEquals(2, paginator.slotCount());
        assertEquals(1, paginator.nextSlotIndex());
        paginator.markFinished(1);
        assertFalse(paginator.hasMore());
    }

    private static final class FakeGroup implements MediaGroup {
        private final String title;
        private final String next;

        FakeGroup(String title, String next) {
            this.title = title;
            this.next = next;
        }

        @Override public int getType() { return TYPE_UNDEFINED; }
        @Override public List<MediaItem> getMediaItems() { return Collections.emptyList(); }
        @Override public String getTitle() { return title; }
        @Override public String getChannelId() { return null; }
        @Override public String getParams() { return null; }
        @Override public String getReloadPageKey() { return null; }
        @Override public String getNextPageKey() { return next; }
        @Override public String getChannelUrl() { return null; }
        @Override public boolean isEmpty() { return false; }
    }
}
