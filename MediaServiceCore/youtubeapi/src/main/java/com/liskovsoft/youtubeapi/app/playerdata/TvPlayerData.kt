package com.liskovsoft.youtubeapi.app.playerdata

import com.liskovsoft.youtubeapi.app.nsigsolver.common.YouTubeInfoExtractor
import com.liskovsoft.youtubeapi.app.nsigsolver.impl.TclChallengeProvider

/** Keeps the TCL player script and timestamp tied to the TV configuration. */
internal class TvPlayerData(private val url: String) {
    private val playerCode by lazy { YouTubeInfoExtractor.loadPlayerSilent(url) }

    val signatureTimestamp: String? by lazy {
        playerCode?.let { CommonExtractor.extractSignatureTimestamp(it) }
    }

    fun extractN(values: List<String?>): List<String?>? {
        val input = values.filterNotNull().distinct()
        if (input.isEmpty()) return null
        val code = playerCode ?: return null
        val results = TclChallengeProvider.solveN(url, code, input)
        return values.map { results[it] }
    }
}
