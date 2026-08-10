package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OfflineAudioFormatSelectorTest {
    @Test
    public void preferredLanguageWinsOverHigherBitrateFallback() {
        MediaFormat english = audio("https://media/en", "audio/mp4; codecs=\"mp4a.40.2\"",
                "en", "256000", "12345", false, false);
        MediaFormat polish = audio("https://media/pl", "audio/mp4; codecs=\"mp4a.40.2\"",
                "pl-PL", "128000", "67890", false, false);
        MediaItemFormatInfo info = mock(MediaItemFormatInfo.class);
        when(info.getAdaptiveFormats()).thenReturn(Arrays.asList(english, polish));
        when(info.getUrlFormats()).thenReturn(Collections.emptyList());

        assertSame(polish, OfflineAudioFormatSelector.select(info, "pl"));
        assertEquals(67890L, OfflineAudioFormatSelector.expectedBytes(polish));
        assertEquals("mp4a.40.2", OfflineAudioFormatSelector.codec(polish));
    }

    @Test
    public void otfAndNonAudioFormatsAreRejected() {
        MediaFormat otf = audio("https://media/otf", "audio/webm; codecs=\"opus\"",
                "pl", "128000", "0", true, false);
        MediaFormat video = audio("https://media/video", "video/mp4; codecs=\"avc1\"",
                "", "1000000", "100", false, false);
        MediaItemFormatInfo info = mock(MediaItemFormatInfo.class);
        when(info.getAdaptiveFormats()).thenReturn(Arrays.asList(otf, video));
        when(info.getUrlFormats()).thenReturn(Collections.emptyList());

        assertNull(OfflineAudioFormatSelector.select(info, "pl"));
    }

    private static MediaFormat audio(String url, String mime, String language, String bitrate,
                                     String clen, boolean otf, boolean drc) {
        MediaFormat format = mock(MediaFormat.class);
        when(format.getUrl()).thenReturn(url);
        when(format.getMimeType()).thenReturn(mime);
        when(format.getLanguage()).thenReturn(language);
        when(format.getBitrate()).thenReturn(bitrate);
        when(format.getClen()).thenReturn(clen);
        when(format.isOtf()).thenReturn(otf);
        when(format.isDrc()).thenReturn(drc);
        return format;
    }
}
