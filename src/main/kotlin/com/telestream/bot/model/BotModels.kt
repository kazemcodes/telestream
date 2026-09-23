package com.telestream.bot.model

import com.lagradost.cloudstream3.TvType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

// Token cache to guarantee Telegram callback_data never exceeds 64 bytes
object CallbackTokenCache {
    private val counter = AtomicLong(1)
    private const val MAX_SIZE = 10_000
    private val cache = ConcurrentHashMap<String, Any>()
    private val queue = ConcurrentLinkedQueue<String>()

    fun put(value: Any): String {
        val id = counter.getAndIncrement().toString(36)
        cache[id] = value
        queue.add(id)
        if (queue.size > MAX_SIZE) {
            val oldest = queue.poll()
            if (oldest != null) cache.remove(oldest)
        }
        return id
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(token: String): T? = cache[token] as? T
}

data class MediaRef(val provider: String, val url: String)

data class EpisodeRef(
    val provider: String,
    val seriesRefToken: String,
    val episodeData: String,
    val episodeTitle: String,
    val page: Int
)

data class SourceToggleRef(
    val repoName: String,
    val lang: String,
    val page: Int,
    val sourceName: String
)

data class RepoPageRef(
    val repoName: String,
    val lang: String,
    val page: Int
)

data class SearchExecRef(
    val sourceName: String,
    val query: String
)

data class SourceBrowserRef(
    val lang: String,
    val page: Int,
    val query: String? = null
)

data class SourceActionRef(
    val lang: String,
    val page: Int,
    val sourceName: String,
    val query: String? = null
)

data class CategorySectionRef(
    val provider: String,
    val sectionName: String,
    val page: Int = 1
)

data class MediaItemSummary(
    val name: String,
    val url: String,
    val apiName: String,
    val posterUrl: String?,
    val type: TvType?,
    val year: Int?
)

data class MediaCarouselRef(
    val contextType: String,
    val sourceName: String,
    val query: String? = null,
    val feedType: String? = null,
    val page: Int = 0,
    val items: List<MediaItemSummary>,
    var currentIndex: Int = 0
)
