package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.model;

public final class MobileLoadState<T> {
    public enum Status { IDLE, LOADING, CONTENT, ERROR }

    private final Status status;
    private final T data;
    private final MobileError error;
    private final boolean refreshing;

    private MobileLoadState(Status status, T data, MobileError error, boolean refreshing) {
        this.status = status;
        this.data = data;
        this.error = error;
        this.refreshing = refreshing;
    }

    public static <T> MobileLoadState<T> idle() {
        return new MobileLoadState<>(Status.IDLE, null, null, false);
    }

    public static <T> MobileLoadState<T> loading(T previous, boolean refreshing) {
        return new MobileLoadState<>(Status.LOADING, previous, null, refreshing);
    }

    public static <T> MobileLoadState<T> content(T data) {
        return new MobileLoadState<>(Status.CONTENT, data, null, false);
    }

    public static <T> MobileLoadState<T> error(T previous, MobileError error) {
        return new MobileLoadState<>(Status.ERROR, previous, error, false);
    }

    public Status getStatus() { return status; }
    public T getData() { return data; }
    public MobileError getError() { return error; }
    public boolean isRefreshing() { return refreshing; }
    public boolean hasData() { return data != null; }
}
