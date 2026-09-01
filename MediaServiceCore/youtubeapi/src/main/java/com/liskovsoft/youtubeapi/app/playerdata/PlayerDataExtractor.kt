package com.liskovsoft.youtubeapi.app.playerdata

import com.eclipsesource.v8.V8ScriptExecutionException
import com.liskovsoft.googlecommon.common.helpers.YouTubeHelper
import com.liskovsoft.sharedutils.helpers.Helpers
import com.liskovsoft.sharedutils.mylogger.Log
import com.liskovsoft.youtubeapi.app.nsigsolver.common.YouTubeInfoExtractor
import com.liskovsoft.youtubeapi.app.nsigsolver.impl.V8ChallengeProvider
import com.liskovsoft.youtubeapi.app.nsigsolver.provider.ChallengeInput
import com.liskovsoft.youtubeapi.app.nsigsolver.provider.JsChallengeRequest
import com.liskovsoft.youtubeapi.app.nsigsolver.provider.JsChallengeType
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData

internal class PlayerDataExtractor(val playerUrl: String) {
    private val tag = PlayerDataExtractor::class.java.simpleName
    private val data
        get() = MediaServiceData.instance()
    private var nFuncCode: Boolean = false
    private var sFuncCode: Boolean = false
    private var cpnCode: String? = null
    private var signatureTimestamp: String? = null
    private val fixedPlayerUrl by lazy {
        // Those are implements global helper functions. No fix. Fallback to regular.
        // See https://github.com/yt-dlp/yt-dlp/issues/12398
        // tv url: https://www.youtube.com/s/player/69b31e11/tv-player-es6-tce.vflset/tv-player-es6-tce.js
        // web url: https://www.youtube.com/s/player/e12fbea4/player_ias_tce.vflset/en_US/base.js
        playerUrl
            //.replace("_tce", "") // global helper functions, web url
            //.replace("/player_ias.vflset/en_US/base.js", "/tv-player-ias.vflset/tv-player-ias.js") // does not validate cpn
            //.replace("-es6", "-ias") // es6 no supported
            .replace("-tcl", "") // 403 fix: incompatible nParam in tv-player *-tcl variants
            .replace("/tv-player-es6.vflset/tv-player-es6.js", "/player_es6.vflset/en_US/base.js") // 403 fix: use compatible web nParam
            .replace("/tv-player-ias.vflset/tv-player-ias.js", "/player_ias.vflset/en_US/base.js") // 403 fix: use compatible web nParam
    }

    init {
        Log.d(tag, "playerUrl original=%s fixed=%s changed=%s", playerUrl, fixedPlayerUrl, playerUrl != fixedPlayerUrl)

        // Get the code from the cache
        restoreAllData()
        checkSigData()
        checkCpnData()
        Log.d(tag, "extractor state nFunc=%s sFunc=%s cpnCode=%s signatureTimestamp=%s",
            nFuncCode, sFuncCode, cpnCode != null, signatureTimestamp)

        if (signatureTimestamp == null) {
            fetchAllData()
            checkCpnData()
            persistAllData()
        }
    }

    fun extractNSig(nParam: String): String? {
        return bulkSigExtract(listOf(nParam), null).first?.firstOrNull()
    }

    fun extractSig(sParams: List<String?>): List<String?>? {
        return bulkSigExtract(null, sParams).second
    }

    fun bulkSigExtract(nParams: List<String?>?, sParams: List<String?>?): Pair<List<String?>?, List<String?>?> {
        if (Helpers.allNulls(nParams, sParams)) {
            return Pair(null, null)
        }

        val response = bulkSigExtractReal(nParams, sParams)

        return Pair(response.first, response.second)
    }

    /**
     * "cpn":"KjdxegeSaJXRctIl"
     */
    fun createClientPlaybackNonce(): String? {
        return cpnCode?.let { ClientPlaybackNonceExtractor.createClientPlaybackNonce(it) } ?: YouTubeHelper.generateCPNParameter2()
    }

    /**
     * "signatureTimestamp":20522
     */
    fun getSignatureTimestamp(): String? {
        return signatureTimestamp
    }

    fun setSignatureTimestamp(timestamp: String) {
        signatureTimestamp = timestamp
    }

    fun validate(): Boolean {
        // TODO: fix cpn code
        // return mNFuncCode && mSigFuncCode && mCPNCode != null && mSignatureTimestamp != null
        return nFuncCode && sFuncCode && signatureTimestamp != null
    }

    private fun extractNSigReal(nParam: String): String? {
        return bulkSigExtractReal(listOf(nParam), null).first?.firstOrNull()
    }

    private fun extractSigReal(sParam: List<String>): List<String?>? {
        return bulkSigExtractReal(null, sParam).second
    }

    private fun bulkSigExtractReal(nParams: List<String?>?, sParams: List<String?>?): Pair<List<String?>?, List<String?>?> {
        if (Helpers.allNulls(nParams, sParams)) {
            return Pair(null, null)
        }

        var nProcessed: List<String?>? = null
        var sProcessed: List<String?>? = null

        val nValues = nParams?.filterNotNull()?.distinct().orEmpty()
        val sValues = sParams?.filterNotNull()?.distinct().orEmpty()

        // V7: do not suppress a real challenge just because the startup self-test
        // failed. A false-negative self-test previously produced a misleading
        // "responses=0" and left the original URL untouched. We now attempt the
        // solver for actual non-null values and fail safely if it cannot solve them.
        val nRequest = nValues.takeIf { it.isNotEmpty() }?.let {
            JsChallengeRequest(JsChallengeType.N, ChallengeInput(fixedPlayerUrl, it))
        }

        val sRequest = sValues.takeIf { it.isNotEmpty() }?.let {
            JsChallengeRequest(JsChallengeType.SIG, ChallengeInput(fixedPlayerUrl, it))
        }

        val requests = listOfNotNull(nRequest, sRequest)
        Log.d(tag, "V7_AUTH solver request fixedUrl=%s nSlots=%s nValues=%s sSlots=%s sValues=%s selfTestN=%s selfTestS=%s requests=%s",
            fixedPlayerUrl, nParams?.size ?: 0, nValues.size, sParams?.size ?: 0, sValues.size,
            nFuncCode, sFuncCode, requests.size)

        if (requests.isEmpty()) {
            Log.d(tag, "V7_AUTH solver skipped: no non-null n/s values")
            return Pair(null, null)
        }

        val result = try {
            V8ChallengeProvider.bulkSolve(requests).toList()
        } catch (error: Exception) {
            Log.e(tag, "V7_AUTH solver exception: " + error.javaClass.simpleName)
            return Pair(null, null)
        }

        Log.d(tag, "V7_AUTH solver result responses=%s requested=%s", result.size, requests.size)

        for (item in result) {
            val response = item.response
            val outputCount = response?.output?.results?.size ?: 0
            Log.d(tag, "V7_AUTH solver response type=%s ok=%s error=%s outputs=%s",
                item.request.type.value, response != null, item.error?.javaClass?.simpleName ?: "none", outputCount)

            if (response == null) {
                continue
            }

            when (response.type) {
                JsChallengeType.N -> {
                    val mapped = nParams?.map { input -> input?.let { response.output.results[it] } }
                    val expected = nParams?.count { it != null } ?: 0
                    val solved = mapped?.count { it != null } ?: 0
                    val changed = if (nParams != null && mapped != null)
                        nParams.indices.count { nParams[it] != null && mapped[it] != null && nParams[it] != mapped[it] }
                    else 0
                    val complete = expected > 0 && solved == expected
                    Log.d(tag, "V7_AUTH N expected=%s solved=%s changed=%s complete=%s", expected, solved, changed, complete)
                    if (complete) {
                        nProcessed = mapped
                        if (changed > 0) nFuncCode = true
                    }
                }
                JsChallengeType.SIG -> {
                    val mapped = sParams?.map { input -> input?.let { response.output.results[it] } }
                    val expected = sParams?.count { it != null } ?: 0
                    val solved = mapped?.count { it != null } ?: 0
                    val changed = if (sParams != null && mapped != null)
                        sParams.indices.count { sParams[it] != null && mapped[it] != null && sParams[it] != mapped[it] }
                    else 0
                    val complete = expected > 0 && solved == expected
                    Log.d(tag, "V7_AUTH SIG expected=%s solved=%s changed=%s complete=%s", expected, solved, changed, complete)
                    if (complete) {
                        sProcessed = mapped
                        if (changed > 0) sFuncCode = true
                    }
                }
                else -> {}
            }
        }

        if (nRequest != null && nProcessed == null) {
            Log.w(tag, "V7_AUTH N not applied: solver response missing or incomplete")
        }
        if (sRequest != null && sProcessed == null) {
            Log.w(tag, "V7_AUTH SIG not applied: solver response missing or incomplete")
        }

        return Pair(nProcessed, sProcessed)
    }

    private fun loadPlayer(): String? {
        return YouTubeInfoExtractor.loadPlayerSilent(fixedPlayerUrl)
    }

    private fun fetchAllData() {
        val jsCode = loadPlayer()

        cpnCode = jsCode?.let { ClientPlaybackNonceExtractor.extractClientPlaybackNonceCode(it) }
        signatureTimestamp = jsCode?.let { CommonExtractor.extractSignatureTimestamp(it) }
        Log.d(tag, "player data fetched fixedUrl=%s jsLoaded=%s cpnCode=%s signatureTimestamp=%s",
            fixedPlayerUrl, jsCode != null, cpnCode != null, signatureTimestamp)
    }

    private fun persistAllData() {
        if (validate()) {
            data.playerExtractorCache = PlayerExtractorCache(playerUrl, cpnCode, signatureTimestamp)
        }
    }

    private fun restoreAllData() {
        val playerCache = data.playerExtractorCache

        if (playerCache?.playerUrl == playerUrl) {
            cpnCode = playerCache.cpnCode
            signatureTimestamp = playerCache.signatureTimestamp
            nFuncCode = true
            sFuncCode = true
        }
    }

    private fun checkCpnData() {
        cpnCode?.let {
            try {
                val result = createClientPlaybackNonce()
                if (result == null)
                    cpnCode = null
            } catch (error: V8ScriptExecutionException) {
                cpnCode = null
            }
        }
    }

    private fun checkSigData() {
        if (nFuncCode && sFuncCode) {
            Log.d(tag, "V7_AUTH self-test skipped: cached n/s capabilities are valid")
            V8ChallengeProvider.warmup() // enable hot start
            return
        }

        try {
            val nParam = "5cNpZqIJ7ixNqU68Y7S"
            val sigParam = "NJAJEij0EwRgIhAI0KExTgjfPk-MPM9MAdzyyPRt=BM8-XO5tm5hlMCSVpAiEAv7eP3CURqZNSPow8BXXAoazVoXgeMP7gH9BdylHCwgw=gwzz"
            val result = V8ChallengeProvider.bulkSolve(
                listOf(
                    JsChallengeRequest(JsChallengeType.N, ChallengeInput(fixedPlayerUrl, listOf(nParam))),
                    JsChallengeRequest(JsChallengeType.SIG, ChallengeInput(fixedPlayerUrl, listOf(sigParam))),
                )).toList()

            Log.d(tag, "V7_AUTH self-test responses=%s fixedUrl=%s", result.size, fixedPlayerUrl)
            for (item in result) {
                val response = item.response
                val outputCount = response?.output?.results?.size ?: 0
                Log.d(tag, "V7_AUTH self-test type=%s ok=%s error=%s outputs=%s",
                    item.request.type.value, response != null, item.error?.javaClass?.simpleName ?: "none", outputCount)
                if (response == null) {
                    continue
                }
                when (response.type) {
                    JsChallengeType.N ->
                        if (response.output.results[nParam]?.let { it != nParam } ?: false)
                            nFuncCode = true
                    JsChallengeType.SIG ->
                        if (response.output.results[sigParam]?.let { it != sigParam } ?: false)
                            sFuncCode = true
                    else -> {}
                }
            }
            Log.d(tag, "V7_AUTH self-test final nFunc=%s sFunc=%s", nFuncCode, sFuncCode)
        } catch (error: Exception) {
            Log.e(tag, "V7_AUTH self-test exception: " + error.javaClass.simpleName)
        }
    }

}