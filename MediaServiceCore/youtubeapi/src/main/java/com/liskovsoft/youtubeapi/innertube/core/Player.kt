package com.liskovsoft.youtubeapi.innertube.core

import com.liskovsoft.googlecommon.common.api.FileApi
import com.liskovsoft.googlecommon.common.api.FileContent
import com.liskovsoft.googlecommon.common.helpers.RetrofitHelper
import com.liskovsoft.sharedutils.helpers.Helpers
import com.liskovsoft.sharedutils.mylogger.Log
import com.liskovsoft.youtubeapi.app.AppService
import com.liskovsoft.youtubeapi.app.PoTokenGate
import com.liskovsoft.youtubeapi.common.helpers.AppClient
import com.liskovsoft.youtubeapi.formatbuilders.utils.MediaFormatUtils
import com.liskovsoft.youtubeapi.innertube.impl.MediaFormatImpl
import com.liskovsoft.youtubeapi.innertube.impl.MediaItemFormatInfoImpl
import com.liskovsoft.youtubeapi.innertube.utils.CLIENTS
import com.liskovsoft.youtubeapi.innertube.utils.DeviceCategory
import com.liskovsoft.youtubeapi.innertube.utils.URLS
import com.liskovsoft.youtubeapi.innertube.utils.getRandomUserAgent
import com.liskovsoft.youtubeapi.innertube.utils.getStringBetweenStrings
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData
import com.liskovsoft.youtubeapi.videoinfo.V2.DashInfoApi
import com.liskovsoft.youtubeapi.videoinfo.models.DashInfo
import com.liskovsoft.youtubeapi.videoinfo.models.DashInfoContent
import com.liskovsoft.youtubeapi.videoinfo.models.DashInfoHeaders
import com.liskovsoft.youtubeapi.videoinfo.models.DashInfoUrl
import com.liskovsoft.youtubeapi.videoinfo.models.VideoUrlHolder

internal class Player private constructor(
    val playerUrl: String?
) {
    private val TAG = Player::class.simpleName

    val signatureTimestamp: String by lazy { appService.signatureTimestamp }
    private val appService by lazy { AppService.instance() }
    private val dashInfoApi by lazy { RetrofitHelper.create(DashInfoApi::class.java) }
    private val fileApi by lazy { RetrofitHelper.create(FileApi::class.java) }

    fun decipher(formatInfo: MediaItemFormatInfoImpl) {
        if (formatInfo.isUnplayable()) {
            return
        }

        decipherFormats(formatInfo)

        if (formatInfo.isLive) {
            Log.d(TAG, "Enable seeking support on live streams...")
            formatInfo.sync(getDashInfo(formatInfo))
        }

        formatInfo.visitorCookie = MediaServiceData.instance().visitorCookie
        //formatInfo.setClient(getClient())
    }

    //////////////// DECIPHER //////////////////

    private fun decipherFormats(formatInfo: MediaItemFormatInfoImpl) {
        val adaptiveFormats: List<MediaFormatImpl>? = formatInfo.getAdaptiveFormats()
        val regularFormats: List<MediaFormatImpl>? = formatInfo.getUrlFormats()

        val urlHolders: MutableList<VideoUrlHolder> = mutableListOf()
        if (adaptiveFormats != null) for (videoFormat in adaptiveFormats) {
            urlHolders.add(videoFormat.urlHolder)
        }
        if (regularFormats != null) for (videoFormat in regularFormats) {
            urlHolders.add(videoFormat.urlHolder)
        }
        urlHolders.add(formatInfo.sabrUrlHolder)

        val inputN = extractNParams(urlHolders)
        val inputS = extractSParams(urlHolders)
        Log.d(TAG, "V7_AUTH Player decipher holders=%s nValues=%s sValues=%s",
            urlHolders.size, inputN.count { it != null }, inputS.count { it != null })
        logAuthState("before", urlHolders)

        val result: Pair<MutableList<String?>?, MutableList<String?>?>? =
            appService.bulkSigExtract(inputN, inputS)

        if (result != null) {
            val nParams = result.first
            val signatures = result.second
            Log.d(TAG, "V7_AUTH Player transformed nOut=%s nChanged=%s sOut=%s sChanged=%s",
                nParams?.count { it != null } ?: 0, countChanged(inputN, nParams),
                signatures?.count { it != null } ?: 0, countChanged(inputS, signatures))

            applyNParams(urlHolders, nParams)
            applySignatures(urlHolders, signatures)
        } else {
            Log.w(TAG, "V7_AUTH Player bulkSigExtract returned null")
        }

        applyClientVer(urlHolders)

        // V9: respect the token/client binding. This Innertube implementation
        // is a WEB client, so a WEB CONTENT token is valid here. Never generate
        // a Web BotGuard token merely because a final media URL exists.
        val poToken = PoTokenGate.getPoToken(AppClient.WEB, formatInfo.videoId)
        Log.d(TAG, "V9_CLIENT path=INNER client=WEB version=%s potApplied=%s potLen=%s",
            AppClient.WEB.clientVersion, poToken != null, poToken?.length ?: 0)
        formatInfo.poToken = poToken
        applySessionPoToken(urlHolders, poToken)
        logAuthState("final", urlHolders)
    }

    private fun countChanged(input: List<String?>, output: List<String?>?): Int {
        if (output == null || input.size != output.size) {
            return 0
        }
        return input.indices.count { input[it] != null && output[it] != null && input[it] != output[it] }
    }

    private fun logAuthState(stage: String, urlHolders: List<VideoUrlHolder>) {
        for (i in urlHolders.indices) {
            val holder = urlHolders[i]
            val n = holder.getParam("n")
            val pot = holder.getParam("pot")
            Log.d(TAG,
                "V7_AUTH Player %s idx=%s c=%s itag=%s n=%s nLen=%s s=%s sig=%s lsig=%s spc=%s pot=%s potLen=%s",
                stage, i,
                holder.getParam("c") ?: "-",
                holder.getParam("itag") ?: "-",
                n != null, n?.length ?: 0,
                holder.getSParam() != null,
                holder.getParam("sig") != null || holder.getParam("signature") != null,
                holder.getParam("lsig") != null,
                holder.getParam("spc") != null,
                pot != null, pot?.length ?: 0)
        }
    }

    private fun extractSParams(urlHolders: List<VideoUrlHolder>): List<String?> = urlHolders.map { it.getSParam() }

    private fun extractNParams(urlHolders: List<VideoUrlHolder>): List<String?> = urlHolders.map { it.getNParam() } // All throttled strings has same values
    
    private fun applyNParams(urlHolders: List<VideoUrlHolder>, nParams: List<String?>?) {
        if (nParams == null || nParams.isEmpty()) {
            return
        }

        // All throttled strings has same values
        val sameSize = nParams.size == urlHolders.size

        for (i in urlHolders.indices) {
            urlHolders[i].setNParam(nParams[if (sameSize) i else 0])
        }
    }
    
    private fun applySignatures(urlHolders: List<VideoUrlHolder>, signatures: List<String?>?) {
        if (signatures == null) {
            return
        }

        if (signatures.size != urlHolders.size) {
            throw IllegalStateException("Sizes of urlHolders and signatures should match!")
        }

        for (i in urlHolders.indices) {
            urlHolders[i].setSignature(signatures[i])
        }
    }

    private fun applyClientVer(urlHolders: List<VideoUrlHolder>) {
        val clientParam = "c"
        val clientVersionParam = "cver"
        for (url in urlHolders) {
            val client = url.getParam(clientParam)
            client?.let {
                when (it) {
                    "WEB" -> url.setParam(clientVersionParam, CLIENTS.WEB.VERSION)
                    "MWEB" -> url.setParam(clientVersionParam, CLIENTS.MWEB.VERSION)
                    "WEB_REMIX" -> url.setParam(clientVersionParam, CLIENTS.YTMUSIC.VERSION)
                    "WEB_KIDS" -> url.setParam(clientVersionParam, CLIENTS.WEB_KIDS.VERSION)
                    "TVHTML5" -> url.setParam(clientVersionParam, CLIENTS.TV.VERSION)
                    "TVHTML5_SIMPLY" -> url.setParam(clientVersionParam, CLIENTS.TV_SIMPLY.VERSION)
                    "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> url.setParam(clientVersionParam, CLIENTS.TV_EMBEDDED.VERSION)
                    "WEB_EMBEDDED_PLAYER" -> url.setParam(clientVersionParam, CLIENTS.WEB_EMBEDDED.VERSION)
                }
            }
        }
    }
    
    private fun applySessionPoToken(urlHolders: List<VideoUrlHolder>, poToken: String?) {
        if (poToken == null) {
            return
        }

        for (i in urlHolders.indices) {
            urlHolders[i].setPoToken(poToken)
        }
    }

    //////////// DASH INFO ///////////////

    private fun getDashInfo(formatInfo: MediaItemFormatInfoImpl): DashInfo? {
        if (formatInfo.getAdaptiveFormats().isNullOrEmpty()) {
            return null
        }

        var info = getCumulativeDashInfo(formatInfo)

        // Do retry. Sometimes the previous try failed?
        if (info == null || info.getSegmentDurationUs() <= 0 || info.getStartTimeMs() <= 0 || info.getStartSegmentNum() < 0) {
            info = getCumulativeDashInfo(formatInfo)
        }

        return info
    }

    private fun getCumulativeDashInfo(formatInfo: MediaItemFormatInfoImpl): DashInfo? {
        val format = getSmallestAudio(formatInfo)

        if (format == null) {
            return null
        }

        return try {
            getDashInfoHeaders(format.getUrl())
        } catch (_: Exception) {
            fallbackDashInfo(format)
        }
    }

    private fun fallbackDashInfo(format: MediaFormatImpl): DashInfo? {
        return try {
            getDashInfoUrl(format.getUrl())
        } catch (_: Exception) {
            // Empty results received. Url isn't available or something like that
            getDashInfoContent(format.getUrl())
        }
    }

    private fun getSmallestAudio(formatInfo: MediaItemFormatInfoImpl): MediaFormatImpl? {
        val format = Helpers.findFirst(
            formatInfo.getAdaptiveFormats(),
            Helpers.Filter { item -> MediaFormatUtils.isAudio(item!!.getMimeType()) }) // smallest format
        return format
    }

    private fun getDashInfoUrl(url: String?): DashInfoUrl? {
        if (url == null) {
            return null
        }

        return RetrofitHelper.get(dashInfoApi.getDashInfoUrl(url))
    }

    private fun getDashInfoHeaders(url: String?): DashInfoHeaders? {
        if (url == null) {
            return null
        }

        // Range doesn't work???
        //return RetrofitHelper.getHeaders(mFileApi.getHeaders(url + SMALL_RANGE));
        return DashInfoHeaders(RetrofitHelper.getHeaders(fileApi.getHeaders(url)))
    }
    
    private fun getDashInfoContent(url: String?): DashInfoContent? {
        if (url == null) {
            return null
        }

        return RetrofitHelper.get(dashInfoApi.getDashInfoContent(url))
    }
    
    companion object {
        fun create(poToken: String?, playerId: String?): Player {
            val realPLayerId = playerId ?: getPlayerId()
            val playerUrl = realPLayerId?.let { getPlayerUrl(it) }
            //val js = getPlayerJs(playerUrl)

            return Player(playerUrl)
        }

        fun getPlayerId(): String? {
            val fileApi = RetrofitHelper.create(FileApi::class.java)
            val js = RetrofitHelper.get(fileApi.getContent("${URLS.YT_BASE}/iframe_api"))

            return getStringBetweenStrings(js!!.content!!, "player\\/", "\\/")
        }

        fun getPlayerJs(playerUrl: String): FileContent? {
            val fileApi = RetrofitHelper.create(FileApi::class.java)
            return RetrofitHelper.get(
                fileApi.getContent(mapOf("User-Agent" to getRandomUserAgent(DeviceCategory.DESKTOP)), playerUrl))
        }

        fun getPlayerUrl(playerId: String): String {
            return "${URLS.YT_BASE}/s/player/${playerId}/player_ias.vflset/en_US/base.js"
        }
    }
}
