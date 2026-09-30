package com.liskovsoft.smartyoutubetv2.common.exoplayer.errors;

import com.google.android.exoplayer2.source.sabr.parser.misc.SabrExtractorInput;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.IOException;

public class SabrDefaultLoadErrorHandlingPolicy extends DashDefaultLoadErrorHandlingPolicy {
    private static final String TAG = SabrDefaultLoadErrorHandlingPolicy.class.getSimpleName();
    private static final long MIN_BACKOFF_MS = 250L;
    private static final long MAX_BACKOFF_MS = 10_000L;

    @Override
    public long getBlacklistDurationMsFor(int dataType, long loadDurationMs, IOException exception, int errorCount) {
        return super.getBlacklistDurationMsFor(dataType, loadDurationMs, exception, errorCount);
    }
    
    @Override
    public long getRetryDelayMsFor(int dataType, long loadDurationMs, IOException exception, int errorCount) {
        String message = exception.getMessage();

        if (Helpers.contains(message, SabrExtractorInput.BACKOFF_MARKER)) {
            long delayMs = parseBackoffMs(message);
            Log.d(TAG, "AA143 honouring SABR backoff: " + delayMs
                    + " ms, errorCount=" + errorCount);
            return delayMs;
        }

        if (Helpers.contains(message, "Wait 5 sec")) {
            return 5_000;
        }

        return super.getRetryDelayMsFor(dataType, loadDurationMs, exception, errorCount);
    }

    private static long parseBackoffMs(String message) {
        int index = message.indexOf(SabrExtractorInput.BACKOFF_MARKER);
        if (index < 0) return MIN_BACKOFF_MS;
        String raw = message.substring(index + SabrExtractorInput.BACKOFF_MARKER.length()).trim();
        long parsed;
        try {
            parsed = Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            parsed = MIN_BACKOFF_MS;
        }
        return Math.max(MIN_BACKOFF_MS, Math.min(MAX_BACKOFF_MS, parsed));
    }
}
