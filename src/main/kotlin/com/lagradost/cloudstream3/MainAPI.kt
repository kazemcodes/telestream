package com.lagradost.cloudstream3

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import kotlinx.serialization.json.Json

const val AllLanguagesName = "universal"

const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"

class ErrorLoadingException(message: String? = null) : Exception(message)

val json = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

val mapper = JsonMapper.builder().addModule(kotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build()!!

// -------------------------------------------------------------
// Core Enums & Data Types matching CloudStream 3 API
// -------------------------------------------------------------

enum class TvType {
    Anime,
    AnimeMovie,
    AsianDrama,
    Audio,
    AudioBook,
    Cartoon,
    CustomMedia,
    Documentary,
    Live,
    Movie,
    Music,
    NSFW,
    OVA,
    Others,
    Podcast,
    Torrent,
    TvSeries,
    Video
}

enum class VPNStatus {
    MightBeNeeded,
    None,
    Torrent
}

typealias Qualities = com.lagradost.cloudstream3.utils.Qualities

enum class ShowStatus {
    Ongoing,
    Completed
}

const val INFER_TYPE = "video/mp4"

// -------------------------------------------------------------
// Responses & Models
// -------------------------------------------------------------

open class SearchResponse(
    open val name: String,
    open val url: String,
    open val apiName: String,
    open var type: TvType = TvType.Movie,
    open var posterUrl: String? = null,
    open var year: Int? = null,
    open var id: Int? = null,
    open var posterHeaders: Map<String, String>? = null,
    open var dubStatus: Int? = null,
    open var epCount: Int? = null
) {
    fun addSub(episodes: Int?) {
        this.epCount = episodes
    }
    fun addDub(episodes: Int?) {
        this.epCount = episodes
    }
}

class AnimeSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType = TvType.Anime,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var id: Int? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var dubStatus: Int? = null,
    override var epCount: Int? = null
) : SearchResponse(name, url, apiName, type, posterUrl, year, id, posterHeaders, dubStatus, epCount)

class MovieSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType = TvType.Movie,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var id: Int? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var dubStatus: Int? = null,
    override var epCount: Int? = null
) : SearchResponse(name, url, apiName, type, posterUrl, year, id, posterHeaders, dubStatus, epCount)

class TvSeriesSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType = TvType.TvSeries,
    override var posterUrl: String? = null,
    override var year: Int? = null,
    override var id: Int? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var dubStatus: Int? = null,
    override var epCount: Int? = null
) : SearchResponse(name, url, apiName, type, posterUrl, year, id, posterHeaders, dubStatus, epCount)

data class MainPageData(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false
)

data class MainPageRequest(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false
)

data class HomePageList(
    val name: String,
    var list: List<SearchResponse>,
    val isHorizontalImages: Boolean = false
)

data class HomePageResponse(
    val items: List<HomePageList>,
    val hasNext: Boolean = false
)

fun mainPage(url: String, name: String, horizontalImages: Boolean = false): MainPageData =
    MainPageData(name, url, horizontalImages)

fun mainPageOf(vararg elements: MainPageData): List<MainPageData> = elements.toList()
fun mainPageOf(vararg elements: Pair<String, String>): List<MainPageData> =
    elements.map { (url, name) -> MainPageData(name, url) }

fun newHomePageResponse(name: String, list: List<SearchResponse>, hasNext: Boolean? = null): HomePageResponse =
    HomePageResponse(listOf(HomePageList(name, list)), hasNext ?: list.isNotEmpty())

fun newHomePageResponse(list: List<HomePageList>, hasNext: Boolean? = null): HomePageResponse =
    HomePageResponse(list, hasNext ?: list.any { it.list.isNotEmpty() })

typealias ExtractorLink = com.lagradost.cloudstream3.utils.ExtractorLink

data class Episode(
    val data: String,
    var name: String? = null,
    var season: Int? = 1,
    var episode: Int? = 1,
    var rating: Int? = null,
    var posterUrl: String? = null,
    var description: String? = null
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
// Base MainAPI Class
// -------------------------------------------------------------

abstract class MainAPI {
    open var name: String = "MainAPI"
    open var mainUrl: String = ""
    open var lang: String = "en"
    open val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries)
    open val isNsfw: Boolean
        get() = supportedTypes.contains(TvType.NSFW)
    open var sourcePlugin: String? = null

    open val hasMainPage: Boolean = false
    open val hasQuickSearch: Boolean = false
    open val mainPage: List<MainPageData> = emptyList()

    open suspend fun search(query: String): List<SearchResponse> = emptyList()
    open suspend fun quickSearch(query: String): List<SearchResponse> = search(query)
    open suspend fun getMainPage(page: Int = 1, request: MainPageRequest? = null): HomePageResponse? = null
    open suspend fun getPopular(page: Int = 1): List<SearchResponse> = emptyList()
    open suspend fun getLatest(page: Int = 1): List<SearchResponse> = emptyList()
    open suspend fun load(url: String): LoadResponse? = null
    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean = false,
        subtitleCallback: (SubtitleFile) -> Unit = {},
        callback: (ExtractorLink) -> Unit
    ): Boolean = false
    open fun getVideoInterceptor(extractorLink: ExtractorLink): okhttp3.Interceptor? = null

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
