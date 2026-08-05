package com.liskovsoft.smartyoutubetv2.common.misc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MobilePlaybackFailurePolicyJUnitTest {
    @Test
    public void noNetworkWaitsForConnectivity() {
        assertEquals(MobilePlaybackFailurePolicy.Recovery.WAIT_FOR_NETWORK,
                MobilePlaybackFailurePolicy.recoveryFor(
                        MobilePlaybackFailurePolicy.Failure.NO_NETWORK, 0));
    }

    @Test
    public void transientServerErrorsUseBoundedRetry() {
        assertEquals(MobilePlaybackFailurePolicy.Recovery.RETRY_WITH_BACKOFF,
                MobilePlaybackFailurePolicy.recoveryFor(
                        MobilePlaybackFailurePolicy.Failure.HTTP_5XX, 2));
        assertEquals(MobilePlaybackFailurePolicy.Recovery.SHOW_PERMANENT_ERROR,
                MobilePlaybackFailurePolicy.recoveryFor(
                        MobilePlaybackFailurePolicy.Failure.HTTP_5XX, 3));
    }

    @Test
    public void decoderFallsBackToAlternateFormat() {
        assertEquals(MobilePlaybackFailurePolicy.Recovery.TRY_ALTERNATE_FORMAT,
                MobilePlaybackFailurePolicy.recoveryFor(
                        MobilePlaybackFailurePolicy.Failure.DECODER, 0));
    }
}
