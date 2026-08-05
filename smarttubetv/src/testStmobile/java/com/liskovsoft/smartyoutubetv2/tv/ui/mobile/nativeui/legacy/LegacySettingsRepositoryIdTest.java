package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import static org.junit.Assert.*;
import org.junit.Test;

public class LegacySettingsRepositoryIdTest {
    @Test public void categoryIdsAreStableAndSanitized() {
        assertEquals("legacy-setting:2:jezyk-i-kraj",
                LegacySettingsRepository.idFor(2, "Język i kraj"));
        assertEquals("legacy-setting:0:setting",
                LegacySettingsRepository.idFor(0, "---"));
    }
}
