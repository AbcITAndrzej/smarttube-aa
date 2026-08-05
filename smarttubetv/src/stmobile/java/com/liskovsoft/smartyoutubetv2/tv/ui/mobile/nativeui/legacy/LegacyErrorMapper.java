package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.legacy;

import com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model.MobileError;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

public final class LegacyErrorMapper {
    public MobileError map(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof SocketTimeoutException || cause instanceof TimeoutException) {
            return new MobileError(MobileError.Kind.TIMEOUT, message(cause, "Request timed out"), cause, true);
        }
        if (cause instanceof SecurityException) {
            return new MobileError(MobileError.Kind.AUTHENTICATION, message(cause, "Sign-in is required"), cause, false);
        }
        if (cause instanceof IOException) {
            return new MobileError(MobileError.Kind.NETWORK, message(cause, "Network request failed"), cause, true);
        }
        if (cause instanceof IllegalArgumentException) {
            return new MobileError(MobileError.Kind.PARSING, message(cause, "Invalid data received"), cause, false);
        }
        return new MobileError(MobileError.Kind.UNKNOWN, message(cause, "Unexpected SmartTube error"), cause, false);
    }

    public MobileError playback(Throwable error) {
        Throwable cause = unwrap(error);
        return new MobileError(MobileError.Kind.PLAYBACK,
                message(cause, "Video playback failed"), cause, true);
    }

    private static Throwable unwrap(Throwable value) {
        Throwable current = value == null ? new IllegalStateException("Unknown error") : value;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private static String message(Throwable error, String fallback) {
        String value = error == null ? null : error.getMessage();
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
