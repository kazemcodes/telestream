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

sealed class ProviderError(val message: String, val isNetworkOrBlocked: Boolean) {
    class NetworkTimeout(msg: String = "Timeout") : ProviderError(msg, true)
    class DnsOrHostUnreachable(msg: String = "Host unreachable or DNS failed") : ProviderError(msg, true)
    class CloudflareOrHttpError(val code: Int, msg: String = "HTTP $code") : ProviderError(msg, true)
    class GeneralError(msg: String) : ProviderError(msg, false)
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
    private val lastErrors = ConcurrentHashMap<String, ProviderError>()

    fun getLastError(providerName: String): ProviderError? = lastErrors[providerName.lowercase()]
    fun clearLastError(providerName: String) { lastErrors.remove(providerName.lowercase()) }

    private fun classifyError(e: Throwable): ProviderError {
        val cause = generateSequence(e) { it.cause }.lastOrNull() ?: e
        val msg = cause.message ?: e.message ?: ""
        return when {
            cause is java.net.UnknownHostException ->
                ProviderError.DnsOrHostUnreachable("سایت سورس در دسترس نیست یا دامنه توسط DNS مسدود شده است")
            cause is java.net.SocketTimeoutException || cause is java.util.concurrent.TimeoutException ->
                ProviderError.NetworkTimeout("پاسخ سرور سورس با وقفه زمانی (Timeout) مواجه شد")
            cause is java.net.ConnectException || cause is javax.net.ssl.SSLException ->
                ProviderError.DnsOrHostUnreachable("اتصال به سرور سورس برقرار نشد (فیلترینگ یا قطعی سرور)")
            msg.contains("403") ->
                ProviderError.CloudflareOrHttpError(403, "سایت سورس مسدود یا تحت محافظت کلودفلر است (HTTP 403)")
            msg.contains("502") || msg.contains("503") || msg.contains("504") ->
                ProviderError.CloudflareOrHttpError(502, "سرور سورس موقتاً در دسترس نیست (HTTP 50x)")
            else ->
                ProviderError.GeneralError(msg.ifBlank { "خطای دریافت اطلاعات از سورس" })
        }
    }

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

        clearLastError(provider.name)
        val results = try {
            var list = (provider.search(trimmedQuery) ?: emptyList()).filter { it.type != TvType.NSFW || nsfwAllowed }
            if (list.isEmpty() && trimmedQuery.any { it.isUpperCase() }) {
                val lowerQuery = trimmedQuery.lowercase()
                val lowerList = (provider.search(lowerQuery) ?: emptyList()).filter { it.type != TvType.NSFW || nsfwAllowed }
                if (lowerList.isNotEmpty()) {
                    list = lowerList
                }
            }
            list
        } catch (e: Throwable) {
            val err = classifyError(e)
            lastErrors[provider.name.lowercase()] = err
            org.slf4j.LoggerFactory.getLogger("ProviderManager")
                .warn("Search in provider '${provider.name}' failed: ${e.javaClass.simpleName} - ${e.message}")
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

        clearLastError(provider.name)
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
        } catch (e: Throwable) {
            val err = classifyError(e)
            lastErrors[provider.name.lowercase()] = err
            org.slf4j.LoggerFactory.getLogger("ProviderManager")
                .warn("getPopular in provider '${provider.name}' failed: ${e.javaClass.simpleName} - ${e.message}")
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

        clearLastError(provider.name)
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
        } catch (e: Throwable) {
            val err = classifyError(e)
            lastErrors[provider.name.lowercase()] = err
            org.slf4j.LoggerFactory.getLogger("ProviderManager")
                .warn("getLatest in provider '${provider.name}' failed: ${e.javaClass.simpleName} - ${e.message}")
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

        clearLastError(provider.name)
        val response = try {
            provider.load(url)
        } catch (e: Throwable) {
            val err = classifyError(e)
            lastErrors[provider.name.lowercase()] = err
            org.slf4j.LoggerFactory.getLogger("ProviderManager")
                .warn("load in provider '${provider.name}' failed: ${e.javaClass.simpleName} - ${e.message}")
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
        clearLastError(provider.name)
        try {
            provider.loadLinks(data, isCasting = false, subtitleCallback = {}) { link ->
                links.add(link)
            }
        } catch (e: Throwable) {
            val err = classifyError(e)
            lastErrors[provider.name.lowercase()] = err
            org.slf4j.LoggerFactory.getLogger("ProviderManager")
                .warn("loadLinks in provider '${provider.name}' failed: ${e.javaClass.simpleName} - ${e.message}")
        }
        return links
    }

    suspend fun pingProvider(providerName: String): Pair<Boolean, Long> {
        val provider = getProvider(providerName) ?: return Pair(false, -1)
        val startTime = System.currentTimeMillis()
        return try {
            val resp = app.get(provider.mainUrl, timeout = 6L)
            val elapsed = System.currentTimeMillis() - startTime
            Pair(resp.isSuccessful, elapsed)
        } catch (_: Exception) {
            Pair(false, -1)
        }
    }
}
