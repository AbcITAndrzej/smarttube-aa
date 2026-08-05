package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import static org.junit.Assert.*;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileMediaItem;
import org.junit.Test;

public class LegacyMediaMapperTest {
    @Test public void mapsPlayableVideoAndIndexesOriginalObject() {
        LegacyMediaIndex index = new LegacyMediaIndex();
        LegacyMediaMapper mapper = new LegacyMediaMapper(index);
        Video source = new Video();
        source.videoId = "abc123";
        source.title = "Example";
        source.author = "Author";
        source.cardImageUrl = "https://example/thumb.jpg";

        MobileMediaItem mapped = mapper.map(source);

        assertEquals("abc123", mapped.getId());
        assertEquals(MobileMediaItem.Kind.VIDEO, mapped.getKind());
        assertTrue(mapped.isPlayable());
        assertSame(source, index.get(mapped.getId()));
    }

    @Test public void mapsChannelWithNamespacedStableId() {
        LegacyMediaMapper mapper = new LegacyMediaMapper(new LegacyMediaIndex());
        Video source = new Video();
        source.channelId = "UC_TEST";
        source.title = "Channel";
        MobileMediaItem mapped = mapper.map(source);
        assertEquals("channel:UC_TEST", mapped.getId());
        assertEquals(MobileMediaItem.Kind.CHANNEL, mapped.getKind());
        assertFalse(mapped.isPlayable());
    }

    @Test public void formatsDurationsWithoutLocaleDependentDigits() {
        assertEquals("1:05", LegacyMediaMapper.formatDuration(65_000));
        assertEquals("1:01:01", LegacyMediaMapper.formatDuration(3_661_000));
        assertEquals("", LegacyMediaMapper.formatDuration(0));
    }
}
