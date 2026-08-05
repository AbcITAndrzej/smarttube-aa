package com.liskovsoft.smartyoutubetv2.common.misc;

/** Testable classification of common playback failures. */
public final class MobilePlaybackFailurePolicy {
    public enum Failure {
        NO_NETWORK,
        TIMEOUT,
        HTTP_4XX,
        HTTP_5XX,
        SOURCE_UNAVAILABLE,
        DECODER,
        DRM,
        UNKNOWN
    }

    public enum Recovery {
        WAIT_FOR_NETWORK,
        RETRY_WITH_BACKOFF,
        TRY_ALTERNATE_FORMAT,
        SHOW_PERMANENT_ERROR,
        RELEASE_AND_RECREATE_PLAYER
    }

    private MobilePlaybackFailurePolicy() {
    }

    public static Recovery recoveryFor(Failure failure, int retryCount) {
        if (failure == null) {
            return Recovery.RELEASE_AND_RECREATE_PLAYER;
        }
        switch (failure) {
            case NO_NETWORK:
                return Recovery.WAIT_FOR_NETWORK;
            case TIMEOUT:
            case HTTP_5XX:
                return retryCount < 3
                        ? Recovery.RETRY_WITH_BACKOFF
                        : Recovery.SHOW_PERMANENT_ERROR;
            case DECODER:
                return Recovery.TRY_ALTERNATE_FORMAT;
            case HTTP_4XX:
            case SOURCE_UNAVAILABLE:
            case DRM:
                return Recovery.SHOW_PERMANENT_ERROR;
            case UNKNOWN:
            default:
                return Recovery.RELEASE_AND_RECREATE_PLAYER;
        }
    }
}
