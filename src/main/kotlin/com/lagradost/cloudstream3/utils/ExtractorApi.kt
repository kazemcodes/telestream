package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile
import java.io.Serializable

open class ExtractorLink(
    open val source: String,
    open val name: String,
    open val url: String,
    open var referer: String,
    open var quality: Int,
    open var isM3u8: Boolean = false,
    open var headers: Map<String, String> = emptyMap(),
    open var extractorData: String? = null
) : Serializable

val extractorApis = mutableListOf<ExtractorApi>()

abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    open val requiresReferer: Boolean = false
    var sourcePlugin: String? = null

    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        getUrl(url, referer)?.forEach(callback)
    }

    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? = emptyList()
}
