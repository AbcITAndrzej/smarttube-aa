package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model;

public final class MobileError {
    public enum Kind {
        NETWORK, TIMEOUT, AUTHENTICATION, UNAVAILABLE, PARSING, PLAYBACK, UNKNOWN
    }

    private final Kind kind;
    private final String message;
    private final Throwable cause;
    private final boolean retryable;

    public MobileError(Kind kind, String message, Throwable cause, boolean retryable) {
        this.kind = kind == null ? Kind.UNKNOWN : kind;
        this.message = message == null ? "Unknown error" : message;
        this.cause = cause;
        this.retryable = retryable;
    }

    public static MobileError unconfigured(String repositoryName) {
        return new MobileError(Kind.UNKNOWN,
                repositoryName + " is not connected to SmartTube yet", null, false);
    }

    public Kind getKind() { return kind; }
    public String getMessage() { return message; }
    public Throwable getCause() { return cause; }
    public boolean isRetryable() { return retryable; }
}
