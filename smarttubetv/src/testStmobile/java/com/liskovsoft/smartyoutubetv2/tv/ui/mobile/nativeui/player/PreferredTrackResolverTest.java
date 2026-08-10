package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileTrack;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.Test;

public class PreferredTrackResolverTest {
    private static List<MobileTrack> tracks() {
        return Arrays.asList(
                new MobileTrack("en", MobileTrack.Type.AUDIO, "English", "en", true),
                new MobileTrack("pl", MobileTrack.Type.AUDIO, "Polski", "pl-PL", false),
                new MobileTrack("de", MobileTrack.Type.AUDIO, "Deutsch", "de", false));
    }

    @Test public void exactOrRegionalLanguageIsHighlightedWithoutChangingSelection() {
        assertEquals(1, PreferredTrackResolver.findPreferred(tracks(), "pl"));
        // The resolver only returns a UI row; it never alters MobileTrack.isSelected().
        assertEquals(true, tracks().get(0).isSelected());
    }

    @Test public void emptyPreferenceDoesNotInventASelection() {
        assertEquals(-1, PreferredTrackResolver.findPreferred(tracks(), ""));
    }

    @Test public void systemPreferenceUsesCurrentLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("de", "DE"));
            assertEquals(2, PreferredTrackResolver.findPreferred(
                    tracks(), MobilePlayerPreferences.LANGUAGE_SYSTEM));
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test public void preferredTrackSkipsBaseAndDoesNotChooseFirstWrongLanguage() {
        List<MobileTrack> audio = Arrays.asList(
                new MobileTrack("base", MobileTrack.Type.AUDIO, "Original", "en", true),
                new MobileTrack("arabic", MobileTrack.Type.AUDIO, "Arabic dubbed", "ar", false),
                new MobileTrack("polish", MobileTrack.Type.AUDIO, "Polski dubbing", "pl-PL", false));
        assertEquals("polish",
                PreferredTrackResolver.preferredTrack(audio, "pl", "base").getId());
        assertNull(PreferredTrackResolver.preferredTrack(audio, "en", "base"));
    }
}
