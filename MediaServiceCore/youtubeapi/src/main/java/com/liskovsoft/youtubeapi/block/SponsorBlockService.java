package com.liskovsoft.youtubeapi.block;

import android.os.SystemClock;

import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.youtubeapi.block.data.SegmentList;
import com.liskovsoft.googlecommon.common.helpers.RetrofitHelper;
import com.liskovsoft.googlecommon.common.helpers.ServiceHelper;
import retrofit2.Call;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicLong;

/** SponsorBlock read client with a small TTL/LRU cache and single-flight request coalescing. */
public class SponsorBlockService {
    private static final long POSITIVE_TTL_MS = 15L * 60L * 1000L;
    private static final long NEGATIVE_TTL_MS = 2L * 60L * 1000L;
    private static final int MAX_CACHE_ENTRIES = 192;

    private static SponsorBlockService sInstance;
    private final SponsorBlockApi mSponsorBlockApi;
    private final Map<String, CacheEntry> mCache = new LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };
    private final Map<String, FutureTask<SegmentList>> mInFlight = new LinkedHashMap<>();
    private final AtomicLong mCacheHits = new AtomicLong();
    private final AtomicLong mCacheMisses = new AtomicLong();
    private final AtomicLong mSingleFlightJoins = new AtomicLong();

    private static final class CacheEntry {
        final SegmentList value;
        final long expiresAtElapsedMs;

        CacheEntry(SegmentList value, long expiresAtElapsedMs) {
            this.value = value;
            this.expiresAtElapsedMs = expiresAtElapsedMs;
        }
    }

    private SponsorBlockService() {
        mSponsorBlockApi = RetrofitHelper.create(SponsorBlockApi.class);
    }

    public static SponsorBlockService instance() {
        if (sInstance == null) {
            synchronized (SponsorBlockService.class) {
                if (sInstance == null) sInstance = new SponsorBlockService();
            }
        }
        return sInstance;
    }

    public SegmentList getSegmentList(String videoId) {
        return getCached(videoId, null);
    }

    public SegmentList getSegmentList(String videoId, Set<String> categories) {
        return categories != null && !categories.isEmpty()
                ? getCached(videoId, categories)
                : getCached(videoId, null);
    }

    private SegmentList getCached(String videoId, Set<String> categories) {
        if (videoId == null || videoId.trim().isEmpty()) return null;
        final boolean altServer = isAltServerEnabled();
        final String key = cacheKey(videoId, categories, altServer);
        final long now = SystemClock.elapsedRealtime();
        synchronized (mCache) {
            CacheEntry cached = mCache.get(key);
            if (cached != null) {
                if (cached.expiresAtElapsedMs > now) {
                    mCacheHits.incrementAndGet();
                    return cached.value;
                }
                mCache.remove(key);
            }
        }
        mCacheMisses.incrementAndGet();

        FutureTask<SegmentList> task;
        boolean leader = false;
        synchronized (mInFlight) {
            task = mInFlight.get(key);
            if (task == null) {
                task = new FutureTask<>(() -> fetch(videoId, categories, altServer));
                mInFlight.put(key, task);
                leader = true;
            } else {
                mSingleFlightJoins.incrementAndGet();
            }
        }

        if (leader) task.run();
        try {
            SegmentList result = task.get();
            if (leader) {
                long ttl = result == null ? NEGATIVE_TTL_MS : POSITIVE_TTL_MS;
                synchronized (mCache) {
                    mCache.put(key, new CacheEntry(result, SystemClock.elapsedRealtime() + ttl));
                }
            }
            return result;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SponsorBlock request interrupted", error);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("SponsorBlock request failed", cause);
        } finally {
            if (leader) {
                synchronized (mInFlight) {
                    if (mInFlight.get(key) == task) mInFlight.remove(key);
                }
            }
        }
    }

    private SegmentList fetch(String videoId, Set<String> categories, boolean altServer) {
        Call<SegmentList> wrapper;
        if (categories == null || categories.isEmpty()) {
            wrapper = altServer ? mSponsorBlockApi.getSegments2(videoId)
                    : mSponsorBlockApi.getSegments(videoId);
        } else {
            String categoryJson = ServiceHelper.toJsonArrayString(categories);
            wrapper = altServer ? mSponsorBlockApi.getSegments2(videoId, categoryJson)
                    : mSponsorBlockApi.getSegments(videoId, categoryJson);
        }
        return RetrofitHelper.get(wrapper);
    }

    private static String cacheKey(String videoId, Set<String> categories, boolean altServer) {
        StringBuilder key = new StringBuilder(64)
                .append(altServer ? "alt|" : "main|")
                .append(videoId)
                .append('|');
        if (categories != null && !categories.isEmpty()) {
            List<String> sorted = new ArrayList<>(categories);
            Collections.sort(sorted);
            for (String category : sorted) key.append(category).append(',');
        }
        return key.toString();
    }

    private boolean isAltServerEnabled() {
        return GlobalPreferences.isInitialized() && GlobalPreferences.sInstance.isContentBlockAltServerEnabled();
    }

    public void clearCache() {
        synchronized (mCache) { mCache.clear(); }
    }

    public int getCacheEntryCount() {
        synchronized (mCache) { return mCache.size(); }
    }

    public int getInFlightCount() {
        synchronized (mInFlight) { return mInFlight.size(); }
    }

    public long getCacheHits() { return mCacheHits.get(); }
    public long getCacheMisses() { return mCacheMisses.get(); }
    public long getSingleFlightJoins() { return mSingleFlightJoins.get(); }
}
