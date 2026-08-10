package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

/** Snapshot used by settings/diagnostics; calculating it never performs network I/O. */
public final class OfflineMediaStats {
    private final int totalCount;
    private final int downloadingCount;
    private final int availableCount;
    private final int failedCount;
    private final int expiredCount;
    private final long trackedBytes;
    private final long storageLimitBytes;
    private final long reservedFreeBytes;
    private final long availableDeviceBytes;

    OfflineMediaStats(int totalCount, int downloadingCount, int availableCount, int failedCount,
                      int expiredCount, long trackedBytes, long storageLimitBytes,
                      long reservedFreeBytes, long availableDeviceBytes) {
        this.totalCount = Math.max(0, totalCount);
        this.downloadingCount = Math.max(0, downloadingCount);
        this.availableCount = Math.max(0, availableCount);
        this.failedCount = Math.max(0, failedCount);
        this.expiredCount = Math.max(0, expiredCount);
        this.trackedBytes = Math.max(0L, trackedBytes);
        this.storageLimitBytes = Math.max(0L, storageLimitBytes);
        this.reservedFreeBytes = Math.max(0L, reservedFreeBytes);
        this.availableDeviceBytes = Math.max(0L, availableDeviceBytes);
    }

    public int getTotalCount() { return totalCount; }
    public int getDownloadingCount() { return downloadingCount; }
    public int getAvailableCount() { return availableCount; }
    public int getFailedCount() { return failedCount; }
    public int getExpiredCount() { return expiredCount; }
    public long getTrackedBytes() { return trackedBytes; }
    public long getStorageLimitBytes() { return storageLimitBytes; }
    public long getReservedFreeBytes() { return reservedFreeBytes; }
    public long getAvailableDeviceBytes() { return availableDeviceBytes; }
}
