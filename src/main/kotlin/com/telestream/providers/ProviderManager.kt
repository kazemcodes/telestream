package com.telestream.providers

import com.lagradost.cloudstream3.*
import com.telestream.database.Database
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

object ProviderManager {
    val providers = mutableListOf<MainAPI>()

    // Thread-safe in-memory cache
    private val searchCache = ConcurrentHashMap<String, List<SearchResponse>>()
    private val loadCache = ConcurrentHashMap<String, LoadResponse>()
    private val popularCache = ConcurrentHashMap<String, List<SearchResponse>>()
    private val latestCache = ConcurrentHashMap<String, List<SearchResponse>>()

    init {
    }

    fun register(provider: MainAPI) {
        providers.add(provider)
    }

    fun getProvider(name: String): MainAPI? {
        var p = providers.firstOrNull { 
            it.name.equals(name, ignoreCase = true) ||
            it.name.startsWith(name, ignoreCase = true) ||
            name.startsWith(it.name, ignoreCase = true)
        }
        if (p == null) {
            val meta = com.telestream.repo.CloudStreamRepoManager.getPlugin(name)
            if (meta != null) {
                p = com.telestream.repo.CloudStreamPluginLoader.loadPlugin(meta)
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
            provider.search(trimmedQuery).filter { it.type != TvType.NSFW || nsfwAllowed }
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
            provider.getPopular(page).filter { it.type != TvType.NSFW || nsfwAllowed }
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
            provider.getLatest(page).filter { it.type != TvType.NSFW || nsfwAllowed }
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
            provider.loadLinks(data) { link ->
                links.add(link)
            }
        } catch (e: Exception) {
            // Log or ignore
        }
        return links
    }
}
