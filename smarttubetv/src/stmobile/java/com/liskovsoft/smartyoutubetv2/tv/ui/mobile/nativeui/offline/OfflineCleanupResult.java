package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

/** Result of automatic/manual storage maintenance. */
public final class OfflineCleanupResult {
    private final boolean capacityAvailable;
    private final int removedItems;
    private final long removedBytes;

    OfflineCleanupResult(boolean capacityAvailable, int removedItems, long removedBytes) {
        this.capacityAvailable = capacityAvailable;
        this.removedItems = Math.max(0, removedItems);
        this.removedBytes = Math.max(0L, removedBytes);
    }

    public boolean isCapacityAvailable() { return capacityAvailable; }
    public int getRemovedItems() { return removedItems; }
    public long getRemovedBytes() { return removedBytes; }
}
