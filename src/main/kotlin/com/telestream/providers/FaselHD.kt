package com.telestream.providers

import com.lagradost.cloudstream3.*

class FaselHD : MainAPI() {
    override var name = "FaselHD (العربية)"
    override var mainUrl = "https://web31312x.faselhdx.bid"
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val doc = try {
            app.get(searchUrl).document
        } catch (e: Exception) {
            return emptyList()
        }

        val results = mutableListOf<SearchResponse>()
        for (post in doc.select("div.postDiv, div.col-xl-2, div.movie-card")) {
            val linkElem = post.selectFirst("a[href]") ?: continue
            val itemUrl = linkElem.attr("href")
            val titleElem = post.selectFirst(".postTitle, .title, h2, h3")
            val title = titleElem?.text()?.trim() ?: linkElem.attr("title").trim()
            if (title.isBlank()) continue

            val imgElem = post.selectFirst("img[src]")
            var poster = imgElem?.attr("src")
            if (poster != null && poster.startsWith("//")) {
                poster = "https:$poster"
            }

            val isSeries = title.contains("مسلسل") || title.contains("موسم") || title.contains("حلقة")
            val yearMatch = Regex("""\b(19\d\d|20\d\d)\b""").find(title)
            val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()

            results.add(
                newMovieSearchResponse(title, itemUrl, if (isSeries) TvType.TvSeries else TvType.Movie) {
                    this.posterUrl = poster
                    this.year = year
                }
            )
        }
        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = try {
            app.get(url).document
        } catch (e: Exception) {
            return null
        }

        val titleElem = doc.selectFirst("h1, .postTitle")
        val title = titleElem?.text()?.trim() ?: "FaselHD Media"

        val imgElem = doc.selectFirst("div.posterImg img, .single-poster img, article img")
        val poster = imgElem?.attr("src")

        val descElem = doc.selectFirst("div.singleDesc p, div.story")
        val desc = descElem?.text()?.trim() ?: "No description available."

        val yearMatch = Regex("""\b(19\d\d|20\d\d)\b""").find(title)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()

        val episodes = mutableListOf<Episode>()
        val epLinks = doc.select("div.episodes-list a, div.seasonList a, a[href*='/video/']")
        for ((idx, a) in epLinks.withIndex()) {
            val href = a.attr("href")
            val epText = a.text().trim()
            episodes.add(
                newEpisode(href) {
                    this.name = if (epText.isNotBlank()) epText else "Episode ${idx + 1}"
                    this.episode = idx + 1
                }
            )
        }

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = desc
                this.year = year
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = desc
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
        val res = try {
            app.get(data)
        } catch (e: Exception) {
            return false
        }

        val html = res.text
        var found = false

        // 1. Direct m3u8 links
        val m3u8Regex = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""")
        for (match in m3u8Regex.findAll(html)) {
            val m3u8Url = match.value
            callback(
                newExtractorLink(name, "FaselHD HLS (1080p Auto)", m3u8Url, INFER_TYPE) {
                    this.quality = Qualities.P1080.value
                    this.isM3u8 = true
                    this.referer = mainUrl
                }
            )
            found = true
        }

        // 2. Direct mp4 links
        val mp4Regex = Regex("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*""")
        for (match in mp4Regex.findAll(html)) {
            val mp4Url = match.value
            callback(
                newExtractorLink(name, "FaselHD Direct MP4", mp4Url, INFER_TYPE) {
                    this.quality = Qualities.P720.value
                    this.isM3u8 = false
                }
            )
            found = true
        }

        return found
    }
}
