package com.lagradost.cloudstream3

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// -------------------------------------------------------------
// Core Enums & Data Types matching CloudStream 3 API
// -------------------------------------------------------------

enum class TvType {
    Movie,
    TvSeries,
    Anime,
    AsianDrama,
    Live,
    Cartoon,
    Documentary,
    Others,
    NSFW
}

enum class Qualities(val value: Int) {
    Unknown(400),
    P144(144),
    P240(240),
    P360(360),
    P480(480),
    P720(720),
    P1080(1080),
    P2160(2160)
}

enum class ShowStatus {
    Ongoing,
    Completed
}

const val INFER_TYPE = "video/mp4"

// -------------------------------------------------------------
// Responses & Models
// -------------------------------------------------------------

data class SearchResponse(
    val name: String,
    val url: String,
    val apiName: String,
    var type: TvType = TvType.Movie,
    var posterUrl: String? = null,
    var year: Int? = null,
    var id: Int? = null,
    var posterHeaders: Map<String, String>? = null,
    var dubStatus: Int? = null,
    var epCount: Int? = null
) {
    fun addSub(episodes: Int?) {
        this.epCount = episodes
    }
}

data class Episode(
    val data: String,
    var name: String? = null,
    var season: Int? = 1,
    var episode: Int? = 1,
    var rating: Int? = null,
    var posterUrl: String? = null,
    var description: String? = null
)

data class ExtractorLink(
    val source: String,
    val name: String,
    val url: String,
    val referer: String,
    val quality: Int,
    val isM3u8: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val extractorData: String? = null
)

data class SubtitleFile(
    val lang: String,
    val url: String
)

data class LoadResponse(
    val name: String,
    val url: String,
    val apiName: String,
    val type: TvType,
    val dataUrl: String,
    var episodes: List<Episode>? = null,
    var posterUrl: String? = null,
    var year: Int? = null,
    var plot: String? = null,
    var rating: Int? = null,
    var tags: List<String>? = null,
    var showStatus: ShowStatus? = null,
    var posterHeaders: Map<String, String>? = null
)

// Helper builder functions matching CloudStream DSL
fun newMovieSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    fix: Boolean = true,
    builder: SearchResponse.() -> Unit = {}
): SearchResponse {
    val res = SearchResponse(name = name, url = url, apiName = "CloudStream", type = type)
    res.builder()
    return res
}

fun newAnimeSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    builder: SearchResponse.() -> Unit = {}
): SearchResponse {
    val res = SearchResponse(name = name, url = url, apiName = "CloudStream", type = type)
    res.builder()
    return res
}

fun newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType,
    episodes: List<Episode>,
    builder: LoadResponse.() -> Unit = {}
): LoadResponse {
    val res = LoadResponse(
        name = name,
        url = url,
        apiName = "CloudStream",
        type = type,
        dataUrl = url,
        episodes = episodes
    )
    res.builder()
    return res
}

fun newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    dataUrl: String,
    builder: LoadResponse.() -> Unit = {}
): LoadResponse {
    val res = LoadResponse(
        name = name,
        url = url,
        apiName = "CloudStream",
        type = type,
        dataUrl = dataUrl
    )
    res.builder()
    return res
}

fun newEpisode(
    data: String,
    builder: Episode.() -> Unit = {}
): Episode {
    val ep = Episode(data = data)
    ep.builder()
    return ep
}

fun newExtractorLink(
    source: String,
    name: String,
    url: String,
    type: String = INFER_TYPE,
    builder: ExtractorLinkBuilder.() -> Unit = {}
): ExtractorLink {
    val b = ExtractorLinkBuilder(source, name, url)
    b.builder()
    return ExtractorLink(
        source = b.source,
        name = b.name,
        url = b.url,
        referer = b.referer,
        quality = b.quality,
        isM3u8 = b.isM3u8 || url.contains(".m3u8"),
        headers = b.headers
    )
}

class ExtractorLinkBuilder(
    var source: String,
    var name: String,
    var url: String,
    var referer: String = "",
    var quality: Int = Qualities.P1080.value,
    var isM3u8: Boolean = false,
    var headers: Map<String, String> = emptyMap()
)

// -------------------------------------------------------------
// Pure JVM Network Helper: app (OkHttp + Jsoup + Jackson)
// -------------------------------------------------------------

val mapper: JsonMapper = JsonMapper.builder()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .addModule(kotlinModule())
    .build()

class NiceResponse(
    val code: Int,
    val url: String,
    val text: String,
    val headers: Map<String, List<String>>
) {
    val document: Document by lazy {
        Jsoup.parse(text, url)
    }

    val isSuccessful: Boolean = code in 200..299

    inline fun <reified T> parsedSafe(): T? {
        return try {
            mapper.readValue(text, T::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

object app {
    private val trustAllCerts = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    )

    private val sslContext = SSLContext.getInstance("SSL").apply {
        init(null, trustAllCerts, SecureRandom())
    }

    private val dispatcher = okhttp3.Dispatcher().apply {
        maxRequests = 128
        maxRequestsPerHost = 32
    }

    private val connectionPool = okhttp3.ConnectionPool(64, 5, TimeUnit.MINUTES)

    val client: OkHttpClient = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectionPool(connectionPool)
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        timeout: Long = 20000
    ): NiceResponse {
        val reqHeaders = headers.toMutableMap()
        if (!reqHeaders.containsKey("User-Agent")) {
            reqHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }
        referer?.let { reqHeaders["Referer"] = it }

        val request = Request.Builder()
            .url(url)
            .headers(reqHeaders.toHeaders())
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            return NiceResponse(
                code = response.code,
                url = response.request.url.toString(),
                text = body,
                headers = response.headers.toMultimap()
            )
        }
    }

    fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        data: Map<String, String>? = null,
        referer: String? = null
    ): NiceResponse {
        val reqHeaders = headers.toMutableMap()
        if (!reqHeaders.containsKey("User-Agent")) {
            reqHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }
        referer?.let { reqHeaders["Referer"] = it }

        val formBody = okhttp3.FormBody.Builder().apply {
            data?.forEach { (k, v) -> add(k, v) }
        }.build()

        val request = Request.Builder()
            .url(url)
            .headers(reqHeaders.toHeaders())
            .post(formBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            return NiceResponse(
                code = response.code,
                url = response.request.url.toString(),
                text = body,
                headers = response.headers.toMultimap()
            )
        }
    }
}

// -------------------------------------------------------------
// Base MainAPI Class
// -------------------------------------------------------------

abstract class MainAPI {
    open var name: String = "MainAPI"
    open var mainUrl: String = ""
    open var lang: String = "en"
    open val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries)
    open val isNsfw: Boolean
        get() = supportedTypes.contains(TvType.NSFW)

    open suspend fun search(query: String): List<SearchResponse> = emptyList()
    open suspend fun load(url: String): LoadResponse? = null
    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean = false,
        subtitleCallback: (SubtitleFile) -> Unit = {},
        callback: (ExtractorLink) -> Unit
    ): Boolean = false

    fun fixUrl(url: String): String {
        return if (url.startsWith("//")) {
            "https:$url"
        } else if (url.startsWith("/")) {
            "${mainUrl.trimEnd('/')}/$url"
        } else {
            url
        }
    }
}
