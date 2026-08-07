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
        assertEquals("1:audio-pl:pl:Polski", track.getId());
        assertEquals("Polski", track.getLabel());
        assertEquals("pl", track.getLanguage());
        assertTrue(track.isSelected());
        assertSame(format, mapper.find(Collections.singletonList(format), track.getId()));
    }

    @Test public void buildsReadableVideoQualityWhenTrackHasNoTitle() {
        FormatItem format = mock(FormatItem.class);
        when(format.getType()).thenReturn(FormatItem.TYPE_VIDEO);
        when(format.getFormatId()).thenReturn("video-1080");
        when(format.getTitle()).thenReturn("");
        when(format.getHeight()).thenReturn(1080);
        when(format.getFrameRate()).thenReturn(60f);

        MobileTrack track = new LegacyTrackMapper().map(
                Collections.singletonList(format), MobileTrack.Type.VIDEO).get(0);

        assertEquals("1080p60", track.getLabel());
        assertEquals(MobileTrack.Type.VIDEO, track.getType());
    }

    @Test public void putsPolishOriginalAndAutomaticSubtitlesFirst() {
        FormatItem english = subtitle("en", "English", false);
        FormatItem off = subtitle("", "", true);
        FormatItem polishAuto = subtitle("Polish\u00a4", "Polish (auto-translated)", false);
        FormatItem polish = subtitle("pl", "Polski", false);

        java.util.List<MobileTrack> tracks = new LegacyTrackMapper().map(
                java.util.Arrays.asList(english, off, polishAuto, polish),
                MobileTrack.Type.SUBTITLE);

        assertEquals("Polski", tracks.get(0).getLabel());
        assertEquals("Polski (automatyczne)", tracks.get(1).getLabel());
        assertEquals("Off", tracks.get(2).getLabel());
        assertEquals("English", tracks.get(3).getLabel());
    }

    private static FormatItem subtitle(String language, String title, boolean isDefault) {
        FormatItem item = mock(FormatItem.class);
        when(item.getType()).thenReturn(FormatItem.TYPE_SUBTITLE);
        when(item.getFormatId()).thenReturn("sub-" + language);
        when(item.getLanguage()).thenReturn(language);
        when(item.getTitle()).thenReturn(title);
        when(item.isDefault()).thenReturn(isDefault);
        return item;
    }
}
