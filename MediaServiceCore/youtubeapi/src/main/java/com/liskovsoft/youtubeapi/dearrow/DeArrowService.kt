package com.liskovsoft.youtubeapi.dearrow

import com.liskovsoft.mediaserviceinterfaces.data.DeArrowData
import com.liskovsoft.googlecommon.common.helpers.RetrofitHelper
import java.util.LinkedHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight in-memory cache around the DeArrow branding endpoint.
 *
 * Stage 12 also coalesces concurrent misses for the same video into one request. This matters when
 * legacy and native-mobile processors encounter the same card at nearly the same time. The cache
 * and coalescing are process-local only; no new persistent data or network endpoint is introduced.
 */
object DeArrowService {
    private const val POSITIVE_TTL_MS = 6L * 60L * 60L * 1000L
    private const val NEGATIVE_TTL_MS = 2L * 60L * 1000L
    private const val MAX_ENTRIES = 512

    private val mDeArrowApi = RetrofitHelper.create(DeArrowApi::class.java)

    private data class CacheEntry(val value: DeArrowData?, val expiresAtMs: Long)

    private class CachedDeArrowData(
        private val videoId: String,
        private val title: String?,
        private val thumbnailUrl: String?
    ) : DeArrowData {
        override fun getVideoId(): String = videoId
        override fun getTitle(): String? = title
        override fun getThumbnailUrl(): String? = thumbnailUrl
    }

    private val cache = object : LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > MAX_ENTRIES
        }
    }
    private val inFlight = LinkedHashMap<String, FutureTask<DeArrowData?>>()
    private val cacheHits = AtomicLong()
    private val cacheMisses = AtomicLong()
    private val singleFlightJoins = AtomicLong()

    @JvmStatic
    fun getData(videoId: String?): DeArrowData? {
        if (videoId.isNullOrBlank()) return null

        val now = System.currentTimeMillis()
        synchronized(cache) {
            val cached = cache[videoId]
            if (cached != null) {
                if (cached.expiresAtMs > now) {
                    cacheHits.incrementAndGet()
                    return cached.value
                }
                cache.remove(videoId)
            }
        }
        cacheMisses.incrementAndGet()

        var leader = false
        val task: FutureTask<DeArrowData?>
        synchronized(inFlight) {
            val existing = inFlight[videoId]
            if (existing != null) {
                task = existing
                singleFlightJoins.incrementAndGet()
            } else {
                task = FutureTask { fetchAndCache(videoId) }
                inFlight[videoId] = task
                leader = true
            }
        }
        if (leader) task.run()

        try {
            return task.get()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("DeArrow request interrupted", error)
        } catch (error: ExecutionException) {
            val cause = error.cause
            if (cause is RuntimeException) throw cause
            if (cause is Error) throw cause
            throw IllegalStateException("DeArrow request failed", cause)
        } finally {
            if (leader) {
                synchronized(inFlight) {
                    if (inFlight[videoId] === task) inFlight.remove(videoId)
                }
            }
        }
    }

    private fun fetchAndCache(videoId: String): DeArrowData? {
        // Preserve old failure semantics: HTTP/network exceptions still propagate and are not cached.
        val branding = mDeArrowApi.getBranding(videoId)
        val result = RetrofitHelper.get(branding)?.let { response ->
            val title = response.titles
                ?.firstOrNull { item -> !(item?.original ?: false) }
                ?.title?.replace(">", "")
            val thumbnailUrl = response.thumbnails
                ?.firstOrNull { item -> !(item?.original ?: false) }
                ?.let { item ->
                    "${DeArrowApiHelper.THUMBNAIL_URL}?videoID=$videoId&time=${item.timestamp}"
                }
            CachedDeArrowData(videoId, title, thumbnailUrl)
        }
        synchronized(cache) {
            cache[videoId] = CacheEntry(
                result,
                System.currentTimeMillis() + if (result == null) NEGATIVE_TTL_MS else POSITIVE_TTL_MS
            )
        }
        return result
    }

    @JvmStatic
    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    @JvmStatic
    fun getCacheEntryCount(): Int = synchronized(cache) { cache.size }

    @JvmStatic
    fun getInFlightCount(): Int = synchronized(inFlight) { inFlight.size }

    @JvmStatic
    fun getCacheHits(): Long = cacheHits.get()

    @JvmStatic
    fun getCacheMisses(): Long = cacheMisses.get()

    @JvmStatic
    fun getSingleFlightJoins(): Long = singleFlightJoins.get()
}
