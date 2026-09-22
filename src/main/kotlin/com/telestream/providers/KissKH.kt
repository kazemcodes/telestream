package com.telestream.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import org.json.JSONObject

data class KissMedia(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("thumbnail") val thumbnail: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("episodesCount") val episodesCount: Int?
)

data class KissMediaDetail(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("thumbnail") val thumbnail: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("type") val type: String?,
    @JsonProperty("releaseDate") val releaseDate: String?,
    @JsonProperty("episodes") val episodes: List<KissEpisode>?
)

data class KissEpisode(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("number") val number: Double?
)

class KissKH : MainAPI() {
    override var name = "KissKH"
    override var mainUrl = "https://kisskh.id"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/api/DramaList/Search?q=$query&type=0"
        val response = try {
            app.get(searchUrl, referer = "$mainUrl/")
        } catch (e: Exception) {
            return emptyList()
        }

        val mediaList = response.parsedSafe<List<KissMedia>>() ?: return emptyList()

        return mediaList.mapNotNull { media ->
            val title = media.title ?: return@mapNotNull null
            val id = media.id ?: return@mapNotNull null

            newAnimeSearchResponse(title, "$title/$id", TvType.TvSeries) {
                this.posterUrl = media.thumbnail
                this.id = id
                this.epCount = media.episodesCount
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url.split("/").lastOrNull() ?: return null
        val detailUrl = "$mainUrl/api/DramaList/Drama/$id?isq=false"

        val response = app.get(detailUrl, referer = "$mainUrl/")
        val detail = response.parsedSafe<KissMediaDetail>() ?: return null

        val episodes = detail.episodes?.map { eps ->
            val num = eps.number ?: 1.0
            val displayNum = if (num % 1.0 == 0.0) num.toInt().toString() else num.toString()
            newEpisode("${detail.id}:${eps.id}") {
                this.name = "Episode $displayNum"
                this.episode = num.toInt()
            }
        } ?: emptyList()

        val isMovie = detail.type == "Movie" || episodes.size <= 1
        val year = detail.releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()

        return if (isMovie) {
            newMovieLoadResponse(detail.title ?: "Unknown", url, TvType.Movie, url) {
                this.posterUrl = detail.thumbnail
                this.plot = detail.description
                this.year = year
                this.episodes = episodes
            }
        } else {
            newTvSeriesLoadResponse(detail.title ?: "Unknown", url, TvType.TvSeries, episodes) {
                this.posterUrl = detail.thumbnail
                this.plot = detail.description
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val epId = data.split(":").lastOrNull() ?: return false
        val videoApiUrl = "$mainUrl/api/DramaList/Episode/$epId.png?err=false&ts=&time=&kkey="

        val res = try {
            app.get(videoApiUrl, referer = "$mainUrl/")
        } catch (e: Exception) {
            return false
        }

        val json = try {
            JSONObject(res.text)
        } catch (e: Exception) {
            return false
        }

        var found = false
        val videoUrl = json.optString("video", "")
        if (videoUrl.isNotBlank()) {
            callback(
                newExtractorLink(
                    source = name,
                    name = "$name Primary Server",
                    url = fixUrl(videoUrl),
                    type = INFER_TYPE
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = if (videoUrl.contains(".m3u8")) Qualities.P1080.value else Qualities.P720.value
                    this.isM3u8 = videoUrl.contains(".m3u8")
                }
            )
            found = true
        }

        val thirdParty = json.optString("thirdParty", "")
        if (thirdParty.isNotBlank()) {
            callback(
                newExtractorLink(
                    source = name,
                    name = "$name Mirror Server",
                    url = fixUrl(thirdParty),
                    type = INFER_TYPE
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.P720.value
                    this.isM3u8 = thirdParty.contains(".m3u8")
                }
            )
            found = true
        }

        return found
    }
}
