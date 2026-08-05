package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import static org.junit.Assert.*;
import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileError;
import java.io.IOException;
import java.net.SocketTimeoutException;
import org.junit.Test;

public class LegacyErrorMapperTest {
    private final LegacyErrorMapper mapper = new LegacyErrorMapper();

    @Test public void mapsNetworkAndTimeoutAsRetryable() {
        MobileError network = mapper.map(new IOException("offline"));
        MobileError timeout = mapper.map(new RuntimeException(new SocketTimeoutException("slow")));
        assertEquals(MobileError.Kind.NETWORK, network.getKind());
        assertTrue(network.isRetryable());
        assertEquals(MobileError.Kind.TIMEOUT, timeout.getKind());
        assertTrue(timeout.isRetryable());
    }

    @Test public void mapsInvalidInputAsNonRetryableParsingError() {
        MobileError error = mapper.map(new IllegalArgumentException("bad id"));
        assertEquals(MobileError.Kind.PARSING, error.getKind());
        assertFalse(error.isRetryable());
    }

    @Test public void playbackErrorsUseDedicatedKind() {
        assertEquals(MobileError.Kind.PLAYBACK,
                mapper.playback(new IllegalStateException("decoder")).getKind());
    }
}
