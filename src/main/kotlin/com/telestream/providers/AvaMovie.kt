package com.telestream.providers

import com.lagradost.cloudstream3.*
import java.net.URLEncoder

class AvaMovie : MainAPI() {
    override var name = "AvaMovie (فارسی)"
    override var mainUrl = "https://avamovie3.info"
    override var lang = "fa"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }
        val searchUrl = "$mainUrl/?s=$encodedQuery"

        val doc = try {
            app.get(searchUrl).document
        } catch (e: Exception) {
            return emptyList()
        }

        val articles = doc.select("article.post, div.post-item, div.item, article.item, div.movies-list article")
        val results = mutableListOf<SearchResponse>()

        for (article in articles) {
            val linkElem = article.selectFirst("a[href]") ?: continue
            val itemUrl = linkElem.attr("href")
            val titleElem = article.selectFirst("h2, h3, .title, .post-title")
            val title = titleElem?.text()?.trim() ?: linkElem.attr("title").trim()
            if (title.isBlank()) continue

            val imgElem = article.selectFirst("img[src]")
            var poster = imgElem?.attr("src")
            if (poster != null && poster.startsWith("//")) {
                poster = "https:$poster"
            }

            val isSeries = title.contains("سریال") || title.contains("فصل") || title.contains("season") || title.contains("series")
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

        val titleElem = doc.selectFirst("h1, .post-title, .entry-title")
        val title = titleElem?.text()?.trim() ?: "فیلم / سریال"

        val imgElem = doc.selectFirst(".poster img, .entry-content img, article img")
        var poster = imgElem?.attr("src")
        if (poster != null && poster.startsWith("//")) {
            poster = "https:$poster"
        }

        val descElem = doc.selectFirst(".plot, .story, .entry-content p, .synopsis")
        val desc = descElem?.text()?.trim() ?: "خلاصه داستانی ثبت نشده است."

        val yearMatch = Regex("""\b(19\d\d|20\d\d)\b""").find(title)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()

        val isSeries = title.contains("سریال") || title.contains("فصل") || title.contains("season")

        val episodes = mutableListOf<Episode>()
        val dlLinks = doc.select("a[href*='.mp4'], a[href*='.mkv'], a.dl-link, div.download-box a")

        if (isSeries) {
            var epIndex = 1
            for (a in dlLinks) {
                val href = a.attr("href")
                val text = a.text().trim()
                if (href.isBlank() || href.startsWith("#")) continue

                val epMatch = Regex("""(?:E|قسمت)\s*(\d+)""", RegexOption.IGNORE_CASE).find("$text $href")
                val epNum = epMatch?.groupValues?.get(1)?.toIntOrNull() ?: epIndex

                episodes.add(
                    newEpisode(href) {
                        this.name = "قسمت $epNum ($text)"
                        this.episode = epNum
                    }
                )
                epIndex++
            }
        }

        return if (isSeries) {
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
                this.episodes = episodes
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Direct link from episode data
        if (data.startsWith("http") && (data.contains(".mp4") || data.contains(".mkv") || data.contains(".m3u8"))) {
            val quality = if (data.contains("1080")) Qualities.P1080.value else (if (data.contains("720")) Qualities.P720.value else Qualities.P480.value)
            callback(
                newExtractorLink(name, "AvaMovie Direct", data, INFER_TYPE) {
                    this.quality = quality
                    this.isM3u8 = data.contains(".m3u8")
                }
            )
            return true
        }

        val doc = try {
            app.get(data).document
        } catch (e: Exception) {
            return false
        }

        var found = false
        val links = doc.select("a[href*='.mp4'], a[href*='.mkv'], a.dl-btn")
        for (a in links) {
            val href = a.attr("href")
            if (href.isBlank() || !href.startsWith("http")) continue

            val text = a.text().trim()
            val quality = if (text.contains("1080") || href.contains("1080")) Qualities.P1080.value else (if (text.contains("720") || href.contains("720")) Qualities.P720.value else Qualities.P480.value)
            var label = "AvaMovie ($quality)"
            if (text.contains("دوبله") || href.lowercase().contains("dubbed")) {
                label += " [دوبله فارسی]"
            } else if (text.contains("زیرنویس") || href.lowercase().contains("sub")) {
                label += " [زیرنویس چسبیده]"
            }

            callback(
                newExtractorLink(name, label, href, INFER_TYPE) {
                    this.quality = quality
                    this.isM3u8 = href.contains(".m3u8")
                }
            )
            found = true
        }

        return found
    }
}
