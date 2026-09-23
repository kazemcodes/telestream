package com.telestream.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.telestream.database.Database
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

val MainAPI.isNsfw: Boolean get() = this.supportedTypes.contains(TvType.NSFW)

val LoadResponse.episodes: List<Episode>?
    get() = when (this) {
        is TvSeriesLoadResponse -> this.episodes
        is AnimeLoadResponse -> this.episodes.values.flatten()
        else -> null
    }

val SearchResponse.year: Int?
    get() = when (this) {
        is MovieSearchResponse -> this.year
        is TvSeriesSearchResponse -> this.year
        is AnimeSearchResponse -> this.year
        else -> null
    }

object ProviderManager {
    private val customProviders = java.util.concurrent.CopyOnWriteArrayList<MainAPI>()

    val providers: List<MainAPI>
        get() {
            val list = mutableListOf<MainAPI>()
            list.addAll(customProviders)
            for (p in APIHolder.allProviders) {
                if (!list.contains(p)) {
                    list.add(p)
                }
            }
            return list
        }

    // Thread-safe in-memory cache
    private val searchCache = ConcurrentHashMap<String, List<SearchResponse>>()
    private val loadCache = ConcurrentHashMap<String, LoadResponse>()
    private val popularCache = ConcurrentHashMap<String, List<SearchResponse>>()
    private val latestCache = ConcurrentHashMap<String, List<SearchResponse>>()

    init {
    }

    fun register(provider: MainAPI) {
        if (!customProviders.contains(provider)) {
            customProviders.add(provider)
        }
        if (!APIHolder.allProviders.contains(provider)) {
            APIHolder.allProviders.add(provider)
        }
    }

    fun unregister(provider: MainAPI) {
        customProviders.remove(provider)
        try {
            APIHolder.allProviders.remove(provider)
        } catch (_: Throwable) {}
    }

    fun remove(provider: MainAPI) = unregister(provider)

    fun removeAll(providers: Collection<MainAPI>) {
        providers.forEach { unregister(it) }
    }

    fun getProvider(name: String): MainAPI? {
        val cleanName = name.replace(" ", "")
        var p = providers.firstOrNull { 
            it.name.equals(name, ignoreCase = true) ||
            it.name.replace(" ", "").equals(cleanName, ignoreCase = true) ||
            it.name.startsWith(name, ignoreCase = true) ||
            name.startsWith(it.name, ignoreCase = true)
        }
        if (p == null) {
            val meta = com.telestream.repo.CloudStreamRepoManager.getPlugin(name)
            if (meta != null) {
                p = com.telestream.repo.CloudStreamPluginLoader.loadPlugin(meta)
                if (p?.name?.equals(name, ignoreCase = true) != true) {
                    p = providers.firstOrNull {
                        it.name.equals(name, ignoreCase = true) ||
                        it.name.replace(" ", "").equals(cleanName, ignoreCase = true) ||
                        it.name.startsWith(name, ignoreCase = true) ||
                        name.startsWith(it.name, ignoreCase = true)
                    } ?: p
                }
            }
        }
        if (p == null) return null
        if (p.isNsfw && !Database.isNsfwEnabled()) return null
        return p
    }

    fun getProvidersByFilter(filter: String = "all"): List<MainAPI> {
        val nsfwAllowed = Database.isNsfwEnabled()
        val available = providers.filter { !it.isNsfw || nsfwAllowed }
        return when (filter.lowercase()) {
            "fa", "persian" -> available.filter { it.lang == "fa" || it.name.contains("ava", ignoreCase = true) }
            "ar", "arabic" -> available.filter { it.lang == "ar" || it.name.contains("fasel", ignoreCase = true) }
            "anime", "asian" -> available.filter {
                it.name.contains("kiss", ignoreCase = true) ||
                it.supportedTypes.contains(TvType.Anime) ||
                it.supportedTypes.contains(TvType.AsianDrama)
            }
            "en", "english" -> available.filter { it.lang == "en" }
            else -> available
        }
    }

    fun isPersianText(text: String): Boolean {
        return text.any { it in '\u0600'..'\u06FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF' }
    }

    suspend fun searchInProvider(providerName: String, query: String): List<SearchResponse> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return emptyList()

        val provider = getProvider(providerName) ?: return emptyList()
        val nsfwAllowed = Database.isNsfwEnabled()
        val cacheKey = "${provider.name.lowercase()}:${trimmedQuery.lowercase()}:nsfw=$nsfwAllowed"
        searchCache[cacheKey]?.let { return it }

        val results = try {
            (provider.search(trimmedQuery) ?: emptyList()).filter { it.type != TvType.NSFW || nsfwAllowed }
        } catch (e: Exception) {
            emptyList()
        }

        searchCache[cacheKey] = results
        return results
    }

    suspend fun getPopular(providerName: String, page: Int = 1): List<SearchResponse> {
        val provider = getProvider(providerName) ?: return emptyList()
        val nsfwAllowed = Database.isNsfwEnabled()
        val cacheKey = "${provider.name.lowercase()}:page=$page:nsfw=$nsfwAllowed"
        popularCache[cacheKey]?.let { return it }

        val results = try {
            val section = provider.mainPage.firstOrNull {
                it.name.contains("popular", ignoreCase = true) ||
                it.name.contains("trending", ignoreCase = true) ||
                it.name.contains("top", ignoreCase = true) ||
                it.name.contains("hot", ignoreCase = true)
            } ?: provider.mainPage.firstOrNull()

            val list = if (section != null) {
                val req = MainPageRequest(section.name, section.data, section.horizontalImages)
                provider.getMainPage(page, req)?.items?.flatMap { it.list } ?: emptyList()
            } else {
                emptyList()
            }
            list.filter { it.type != TvType.NSFW || nsfwAllowed }
        } catch (e: Exception) {
            emptyList()
        }
        if (results.isNotEmpty()) {
            popularCache[cacheKey] = results
        }
        return results
    }

    suspend fun getLatest(providerName: String, page: Int = 1): List<SearchResponse> {
        val provider = getProvider(providerName) ?: return emptyList()
        val nsfwAllowed = Database.isNsfwEnabled()
        val cacheKey = "${provider.name.lowercase()}:page=$page:nsfw=$nsfwAllowed"
        latestCache[cacheKey]?.let { return it }

        val results = try {
            val section = provider.mainPage.firstOrNull {
                it.name.contains("latest", ignoreCase = true) ||
                it.name.contains("recent", ignoreCase = true) ||
                it.name.contains("new", ignoreCase = true) ||
                it.name.contains("updated", ignoreCase = true)
            } ?: provider.mainPage.getOrNull(1) ?: provider.mainPage.firstOrNull()

            val list = if (section != null) {
                val req = MainPageRequest(section.name, section.data, section.horizontalImages)
                provider.getMainPage(page, req)?.items?.flatMap { it.list } ?: emptyList()
            } else {
                emptyList()
            }
            list.filter { it.type != TvType.NSFW || nsfwAllowed }
        } catch (e: Exception) {
            emptyList()
        }
        if (results.isNotEmpty()) {
            latestCache[cacheKey] = results
        }
        return results
    }

    @Deprecated("Global multi-source search is disabled. Use searchInProvider instead.", level = DeprecationLevel.ERROR)
    suspend fun search(query: String): List<SearchResponse> {
        throw UnsupportedOperationException("Global multi-source search is strictly disabled. Use searchInProvider(providerName, query) instead.")
    }

    suspend fun load(providerName: String, url: String): LoadResponse? {
        val provider = getProvider(providerName) ?: return null
        val cacheKey = "$providerName:$url"
        loadCache[cacheKey]?.let { return it }

        val response = try {
            provider.load(url)
        } catch (e: Exception) {
            null
        }

        if (response != null) {
            loadCache[cacheKey] = response
        }
        return response
    }

    suspend fun loadLinks(providerName: String, data: String): List<ExtractorLink> {
        val provider = getProvider(providerName) ?: return emptyList()
        val links = mutableListOf<ExtractorLink>()
        try {
            provider.loadLinks(data, isCasting = false, subtitleCallback = {}) { link ->
                links.add(link)
            }
        } catch (e: Exception) {
            // Log or ignore
        }
        return links
    }
}
