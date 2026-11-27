package com.liskovsoft.youtubeapi.app.potokennp2

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.liskovsoft.sharedutils.mylogger.Log
import com.liskovsoft.youtubeapi.app.AppService
import com.liskovsoft.youtubeapi.app.potokennp2.core.PoTokenGenerator
import com.liskovsoft.youtubeapi.app.potokennp2.generators.PoTokenWebView4
import com.liskovsoft.youtubeapi.common.helpers.AppClient

/** Living-room session handling based on MediaServiceCore's TV playback provider. */
internal object TvPoTokenProvider {
    data class Session(val id: String, val visitorData: String, val token: String)

    private const val TAG = "TvPoTokenProvider"
    private var generator: PoTokenGenerator? = null
    private var session: Session? = null
    private var retryAfterMs = 0L

    @Synchronized
    fun getSession(): Session? {
        currentSession()?.let { return it }
        if (!PoTokenProviderImpl.isWebPotSupported || SystemClock.elapsedRealtime() < retryAfterMs) {
            return null
        }

        reset()
        val result = try {
            buildSession()
        } catch (error: Exception) {
            Log.w(TAG, "Could not initialize TV playback session: %s", error.javaClass.simpleName)
            reset()
            null
        }
        if (result == null) {
            retryAfterMs = SystemClock.elapsedRealtime() + 60_000L
        }
        return result
    }

    @Synchronized
    fun currentSession(): Session? = session?.takeUnless { generator?.isExpired() ?: true }

    private fun buildSession(): Session? {
        val service = AppService.instance()
        val requestKey = service.tvConfig?.challengeRequestKey?.takeIf { it.isNotEmpty() } ?: return null
        val appInfo = service.getTvAppInfo() ?: return null
        val id = appInfo.livingRoomPoTokenId?.takeIf { it.isNotEmpty() } ?: return null
        val visitor = appInfo.visitorData?.takeIf { it.isNotEmpty() } ?: return null
        val tokenGenerator = PoTokenWebView4.newPoTokenGenerator(service.context, requestKey, AppClient.TV_PLAYBACK)
        generator = tokenGenerator
        return Session(id, visitor, tokenGenerator.generatePoToken(id)).also { session = it }
    }

    @Synchronized
    fun reset() {
        val old = generator
        session = null
        generator = null
        retryAfterMs = 0L
        old?.let { Handler(Looper.getMainLooper()).post { it.close() } }
    }
}
