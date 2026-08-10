package com.liskovsoft.smartyoutubetv2.tv.ui.mobile.nativeui.offline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.OutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OfflineMediaRepositoryTest {
    private OfflineMediaRepository repository;

    @Before public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        OfflineMediaRepository.resetForTests();
        context.getSharedPreferences("smarttube_mobile_offline", Context.MODE_PRIVATE)
                .edit().clear().commit();
        context.getSharedPreferences("smarttube_mobile_feature_flags", Context.MODE_PRIVATE)
                .edit().clear().commit();
        repository = OfflineMediaRepository.get(context);
        repository.clearAll();
        repository.getPreferences().reset();
    }

    @Test public void downloadLifecyclePromotesPrivateFileToAvailable() throws Exception {
        OfflineMediaDescriptor descriptor = new OfflineMediaDescriptor(
                "video-123", "Title", "Author", "https://example.invalid/thumb.jpg",
                120_000L, "audio/webm", "opus");
        OfflineMediaRecord downloading = repository.beginDownload(descriptor, 5L);
        assertNotNull(downloading);
        assertEquals(OfflineMediaState.DOWNLOADING, downloading.getState());

        try (OutputStream output = repository.openPartialOutput("video-123", false)) {
            output.write(new byte[]{1, 2, 3, 4, 5});
        }
        repository.updateProgress("video-123", 5L, 5L);
        OfflineMediaRecord available = repository.markAvailable("video-123");

        assertEquals(OfflineMediaState.AVAILABLE, available.getState());
        assertEquals(5L, available.getBytesDownloaded());
        assertNotNull(repository.resolveAvailableFile("video-123"));
        assertTrue(repository.resolveAvailableFile("video-123").isFile());
        assertEquals(1, repository.getStats().getAvailableCount());
    }

    @Test public void cleanupRemovesFailedPartialItems() throws Exception {
        OfflineMediaDescriptor descriptor = new OfflineMediaDescriptor(
                "failed-video", "Broken", "", "", 0L, "audio/mp4", "mp4a");
        repository.beginDownload(descriptor, 3L);
        try (OutputStream output = repository.openPartialOutput("failed-video", false)) {
            output.write(new byte[]{7, 8, 9});
        }
        repository.updateProgress("failed-video", 3L, 3L);
        repository.markFailed("failed-video", "test");
        assertEquals(1, repository.getStats().getFailedCount());

        OfflineCleanupResult result = repository.cleanupNow();
        assertTrue(result.getRemovedItems() >= 1);
        assertEquals(0, repository.getStats().getFailedCount());
    }
}
