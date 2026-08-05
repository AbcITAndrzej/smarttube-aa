package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileTrack;
import java.util.Collections;
import org.junit.Test;

public class LegacyTrackMapperTest {
    @Test public void mapsAndFindsTrackUsingStableFormatId() {
        FormatItem format = mock(FormatItem.class);
        when(format.getType()).thenReturn(FormatItem.TYPE_AUDIO);
        when(format.getFormatId()).thenReturn("audio-pl");
        when(format.getId()).thenReturn(7);
        when(format.getTitle()).thenReturn("Polski");
        when(format.getLanguage()).thenReturn("pl");
        when(format.isSelected()).thenReturn(true);

        LegacyTrackMapper mapper = new LegacyTrackMapper();
        MobileTrack track = mapper.map(Collections.singletonList(format), MobileTrack.Type.AUDIO).get(0);
        assertEquals("1:audio-pl", track.getId());
        assertEquals("Polski", track.getLabel());
        assertEquals("pl", track.getLanguage());
        assertTrue(track.isSelected());
        assertSame(format, mapper.find(Collections.singletonList(format), track.getId()));
    }
}
