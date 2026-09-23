package com.telestream.repo

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI

@Serializable
data class RepoManifest(
    val name: String = "Unknown",
    val description: String = "",
    val manifestVersion: Int = 1,
    val pluginLists: List<String> = emptyList()
)

@Serializable
data class PluginMetadata(
    val name: String,
    val internalName: String? = null,
    val description: String? = null,
    val version: Int? = 1,
    val url: String? = null,
    val language: String? = "en",
    val tvTypes: List<String>? = emptyList(),
    val iconUrl: String? = null,
    val repositoryUrl: String? = null,
    val repositoryName: String? = null
)

data class RepositoryInfo(
    val id: String,
    val name: String,
    val url: String,
    val description: String,
    val pluginsCount: Int,
    val lastSynced: Long = System.currentTimeMillis()
)

/**
 * Manages CloudStream repositories indexed from https://cloudstreamrepo.com/
 * Allows fetching, updating, and querying extensions on-demand without keeping them in git.
 */
object CloudStreamRepoManager {
    private val logger = LoggerFactory.getLogger(CloudStreamRepoManager::class.java)

    private val reposDir = File("data/repos").apply { mkdirs() }
    private val pluginsFile = File(reposDir, "all_plugins.json")

    // Pre-configured curated repositories from user & community (verified active September 2026)
    val DEFAULT_REPOS = listOf(
        RepositoryInfo(
            id = "cspr",
            name = "Official CloudStream",
            url = "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
            description = "The only official CloudStream repository",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "phisherrepo",
            name = "Phisher Providers",
            url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json",
            description = "Plugins in English, Hindi, and multi-language (StreamPlay, SuperStream, XDMovies, HiAnime, Kisskh, FourKHDHub, YTS)",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "megarepo",
            name = "MegaRepo",
            url = "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json",
            description = "CloudStream MegaRepo aggregator",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "csx",
            name = "CSX",
            url = "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CS.json",
            description = "Hindi and English content (CineStream, MoviesDrive, VegaMovies, Bollyflix)",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "indflix",
            name = "Indflix",
            url = "https://raw.githubusercontent.com/dipender98/Indflix/builds/repo.json",
            description = "English, Original and Indian Languages (IndStream, Multimovies, Vegamovies)",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "indos",
            name = "IndoStream",
            url = "https://raw.githubusercontent.com/TeKuma25/IndoStream/builds/repo.json",
            description = "Indonesian, Anime and Asian streaming providers",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "cloudx",
            name = "CloudX",
            url = "https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/repo.json",
            description = "CloudX multi-source collection",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "dogior",
            name = "doGior Repo",
            url = "https://raw.githubusercontent.com/doGior/doGiorsHadEnough/refs/heads/builds/repo.json",
            description = "doGior CloudStream extensions (StreamingCommunity, AnimeWorld, Arte, TV)",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "italia-streaming",
            name = "ItaliaInStreaming",
            url = "https://pastebin.com/raw/qndZtL6D",
            description = "Italian repository (AltaDefinizione, AnimeSaturn, StreamingCommunity, etc.)",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "uk-extensions",
            name = "UK Extensions",
            url = "https://codeberg.org/CakesTwix/cloudstream-extensions-uk/raw/branch/master/repo.json",
            description = "Ukrainian and European providers (Eneyida, UAFlix, Uakino, UASerialsPro)",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "italian-provider",
            name = "Italian Provider",
            url = "https://raw.githubusercontent.com/Gian-Fr/ItalianProvider/builds/repo.json",
            description = "Italian providers (Altadefinizione, GuardaSerie)",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "reflex1337",
            name = "ReflexRepo",
            url = "https://raw.githubusercontent.com/Reflex755/ReflexRepo/refs/heads/builds/repo.json",
            description = "Reflex repository (DiviCast, LibraryOfLadev)",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "redowan",
            name = "Redowan CS",
            url = "https://raw.githubusercontent.com/redowan99/Redowan-CloudStream/master/repo.json",
            description = "Bangla & International streaming providers",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "german-providers",
            name = "German Providers",
            url = "https://raw.githubusercontent.com/Bnyro/GermanProviders/refs/heads/master/repo.json",
            description = "German movies & TV series providers (Aniworld, HDFilme, Megakino)",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "luna712",
            name = "Luna712",
            url = "https://raw.githubusercontent.com/Luna712/Luna712-CloudStream-Extensions/28885d17ceb7f24782b732b6056085c14c1fd027/repo.json",
            description = "Luna712 media extensions (Dailymotion, InternetArchive)",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "lietrepo",
            name = "LietRepo",
            url = "https://raw.githubusercontent.com/lawlietbr/lietrepo/refs/heads/main/builds/repo.json",
            description = "Portuguese & Brazilian media streams (AnimeFire, CineAgora, StreamFlix)",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "cncverse",
            name = "CNCVerse",
            url = "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/refs/heads/builds/CNC.json",
            description = "CNCVerse streaming providers (CastleTV, MovieBox, Cricify)",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "saimuelrepo",
            name = "SaimuelRepo",
            url = "https://raw.githubusercontent.com/saimuelbr/saimuelrepo/refs/heads/main/builds/repo.json",
            description = "Portuguese & Latin streaming providers",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "vietnamese",
            name = "Vietnamese Streams",
            url = "https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/main/repo.json",
            description = "Vietnamese movies, series & anime",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "cxxx",
            name = "CXXX (18+)",
            url = "https://raw.githubusercontent.com/phisher98/CXXX/builds/CXXX.json",
            description = "Adult streaming extensions (18+)",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "gizlikeyif",
            name = "GizliKeyif (18+)",
            url = "https://raw.githubusercontent.com/Kraptor123/Cs-GizliKeyif/refs/heads/master/repo.json",
            description = "Turkish & International adult extensions (18+)",
            pluginsCount = 0
        )
    )

    // Seed essential providers verified to download and run reliably
    val seedPlugins = listOf(
        PluginMetadata(
            name = "KissKH",
            internalName = "KissKH",
            url = "https://raw.githubusercontent.com/Kraptor123/Cs-Karma/builds/KissKH.cs3",
            repositoryName = "Cs-Karma",
            language = "en",
            description = "Asian dramas, movies, and anime"
        ),
        PluginMetadata(
            name = "StreamPlay",
            internalName = "StreamPlay",
            url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/StreamPlay.cs3",
            repositoryName = "Phisher Providers",
            language = "en",
            description = "Movies and TV series aggregator"
        ),
        PluginMetadata(
            name = "XDMovies",
            internalName = "XDMovies",
            url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/XDMovies.cs3",
            repositoryName = "Phisher Providers",
            language = "en",
            description = "HD movies, series, and direct cloud streams"
        ),
        PluginMetadata(
            name = "SuperStream",
            internalName = "SuperStream",
            url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/SuperStream.cs3",
            repositoryName = "Phisher Providers",
            language = "en",
            description = "High-speed multi-quality movies and TV shows"
        ),
        PluginMetadata(
            name = "HiAnime",
            internalName = "HiAnime",
            url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/HiAnime.cs3",
            repositoryName = "Phisher Providers",
            language = "en",
            description = "Anime series and movies streaming"
        ),
        PluginMetadata(
            name = "FourKHDHub",
            internalName = "FourKHDHub",
            url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/FourKHDHub.cs3",
            repositoryName = "Phisher Providers",
            language = "en",
            description = "4K and Ultra-HD movies"
        )
    )

    // In-memory cache of synced plugins
    private val cachedPlugins = mutableListOf<PluginMetadata>()

    init {
        loadCachedPlugins()
    }

    private fun loadCachedPlugins() {
        if (pluginsFile.exists()) {
            try {
                val list = mapper.readValue(
                    pluginsFile,
                    mapper.typeFactory.constructCollectionType(List::class.java, PluginMetadata::class.java)
                ) as List<PluginMetadata>
                cachedPlugins.clear()
                // Purge dead repositories and obsolete 404 URLs from cached entries
                val cleanedList = list.map { p ->
                    if (p.name.equals("StreamPlay", ignoreCase = true) && p.url?.contains("Hexated") == true) {
                        p.copy(
                            url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/StreamPlay.cs3",
                            repositoryName = "Phisher Providers"
                        )
                    } else if (p.name.equals("KissKH", ignoreCase = true) && p.url.isNullOrBlank()) {
                        p.copy(
                            url = "https://raw.githubusercontent.com/Kraptor123/Cs-Karma/builds/KissKH.cs3",
                            repositoryName = "Cs-Karma"
                        )
                    } else {
                        p
                    }
                }.filter { p ->
                    val u = p.url ?: ""
                    !u.contains("Hexated/Cloudstream-Extensions") &&
                    !u.contains("stormunblessed/stormunblessed-cs3") &&
                    !u.contains("Abodabodd/re-3arabi/refs/heads/main/builds") &&
                    !u.contains("Kraptor123/cs-Karma/refs/heads/master")
                }
                cachedPlugins.addAll(cleanedList)
                logger.info("Loaded ${cachedPlugins.size} plugins from local cache after cleanup.")
            } catch (e: Exception) {
                logger.warn("Could not read local plugins cache: ${e.message}")
            }
        }

        // Ensure all verified seed plugins take precedence at the top of the cache
        for (seed in seedPlugins) {
            cachedPlugins.removeAll { it.name.equals(seed.name, ignoreCase = true) }
            cachedPlugins.add(0, seed)
        }
        saveCachedPlugins()
    }

    private fun saveCachedPlugins() {
        try {
            mapper.writeValue(pluginsFile, cachedPlugins)
        } catch (e: Exception) {
            logger.error("Failed to save plugins to $pluginsFile: ${e.message}")
        }
    }

    /**
     * Fetch and parse a repository manifest and its plugin list URLs.
     */
    fun fetchRepository(repoUrl: String, customName: String? = null): RepositoryInfo? = kotlinx.coroutines.runBlocking {
        try {
            logger.info("Fetching CloudStream repository: $repoUrl")
            val resp = app.get(repoUrl)
            if (!resp.isSuccessful) {
                logger.warn("Failed fetching repo $repoUrl: HTTP ${resp.code}")
                return@runBlocking null
            }

            val text = resp.text.trim()
            val fetchedPlugins = mutableListOf<PluginMetadata>()
            var repoName = customName ?: "CloudStream Repo"
            var repoDesc = ""

            if (text.startsWith("[")) {
                // Direct plugin list JSON array
                try {
                    val plugins = mapper.readValue(
                        text,
                        mapper.typeFactory.constructCollectionType(List::class.java, PluginMetadata::class.java)
                    ) as List<PluginMetadata>
                    val enriched = plugins.map {
                        it.copy(repositoryUrl = repoUrl, repositoryName = repoName)
                    }
                    fetchedPlugins.addAll(enriched)
                } catch (e: Exception) {
                    logger.warn("Failed parsing direct plugins array from $repoUrl: ${e.message}")
                }
            } else {
                // Object with manifest: parse RepoManifest
                val manifest = try {
                    mapper.readValue(text, RepoManifest::class.java)
                } catch (e: Exception) {
                    RepoManifest(name = customName ?: "Custom Repo", pluginLists = listOf(repoUrl))
                }

                repoName = customName ?: manifest.name.ifBlank { "CloudStream Repo" }
                repoDesc = manifest.description
                val pluginLists = if (manifest.pluginLists.isNotEmpty()) manifest.pluginLists else listOf(repoUrl)

                for (pUrl in pluginLists) {
                    try {
                        val resolvedUrl = if (pUrl.startsWith("http://") || pUrl.startsWith("https://")) {
                            pUrl
                        } else {
                            try {
                                URI(repoUrl).resolve(pUrl).toString()
                            } catch (_: Exception) {
                                pUrl
                            }
                        }

                        val pResp = if (resolvedUrl == repoUrl) resp else app.get(resolvedUrl)
                        if (pResp.isSuccessful) {
                            val pText = pResp.text.trim()
                            if (pText.startsWith("[")) {
                                val plugins = mapper.readValue(
                                    pText,
                                    mapper.typeFactory.constructCollectionType(List::class.java, PluginMetadata::class.java)
                                ) as List<PluginMetadata>

                                val enriched = plugins.map {
                                    it.copy(repositoryUrl = repoUrl, repositoryName = repoName)
                                }
                                fetchedPlugins.addAll(enriched)
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("Error fetching plugin list from $pUrl: ${e.message}")
                    }
                }
            }

            // Update in-memory cache and remove duplicates
            cachedPlugins.removeAll { it.repositoryUrl == repoUrl }
            cachedPlugins.addAll(fetchedPlugins)
            saveCachedPlugins()

            logger.info("Successfully synced $repoName: ${fetchedPlugins.size} plugins found.")

            val sanitizedId = repoName.lowercase().replace(Regex("[^a-z0-9]"), "-")
            RepositoryInfo(
                id = sanitizedId,
                name = repoName,
                url = repoUrl,
                description = repoDesc,
                pluginsCount = fetchedPlugins.size
            )
        } catch (e: Exception) {
            logger.error("Failed to fetch repository $repoUrl: ${e.message}")
            null
        }
    }

    /**
     * Fetch all default repositories indexed from cloudstreamrepo.com
     */
    fun syncAllDefaults(): List<RepositoryInfo> {
        val results = mutableListOf<RepositoryInfo>()
        for (repo in DEFAULT_REPOS) {
            val info = fetchRepository(repo.url, customName = repo.name)
            if (info != null) {
                results.add(info)
            }
        }
        return results
    }

    /**
     * Search available plugins in synced repositories by query or TV type.
     */
    fun searchPlugins(query: String, lang: String? = null): List<PluginMetadata> {
        val q = query.trim().lowercase()
        return cachedPlugins.filter { p ->
            val matchQuery = (p.name.lowercase().contains(q) ||
                    (p.description?.lowercase()?.contains(q) == true) ||
                    (p.internalName?.lowercase()?.contains(q) == true))

            val matchLang = if (lang != null && lang != "all") {
                p.language.equals(lang, ignoreCase = true)
            } else true

            matchQuery && matchLang
        }
    }

    /**
     * List all plugins currently available in cached repositories.
     */
    fun getAllPlugins(): List<PluginMetadata> = cachedPlugins

    fun getPlugin(name: String): PluginMetadata? {
        val clean = name.trim()
        val normalized = clean.replace("[^A-Za-z0-9]".toRegex(), "").lowercase()
        if (normalized.isBlank()) return null

        // 1. Exact match (case-insensitive)
        cachedPlugins.firstOrNull {
            it.name.equals(clean, ignoreCase = true) ||
            (it.internalName != null && it.internalName.equals(clean, ignoreCase = true))
        }?.let { return it }

        // 2. Normalized alphanumeric match (handles spaces, e.g. "XD Movies" == "XDMovies")
        cachedPlugins.firstOrNull {
            val pNorm = it.name.replace("[^A-Za-z0-9]".toRegex(), "").lowercase()
            val iNorm = it.internalName?.replace("[^A-Za-z0-9]".toRegex(), "")?.lowercase()
            pNorm == normalized || iNorm == normalized
        }?.let { return it }

        // 3. Prefix match
        cachedPlugins.firstOrNull {
            val pNorm = it.name.replace("[^A-Za-z0-9]".toRegex(), "").lowercase()
            pNorm.startsWith(normalized) || normalized.startsWith(pNorm)
        }?.let { return it }

        // 4. Fallback to locally cached .cs3 or .jar file if present
        try {
            val localFiles = CloudStreamPluginLoader.cacheDir.listFiles()
            val localMatch = localFiles?.firstOrNull { file ->
                val base = file.nameWithoutExtension.replace("[^A-Za-z0-9]".toRegex(), "").lowercase()
                (file.extension.equals("cs3", true) || file.extension.equals("jar", true)) &&
                (base == normalized || base.startsWith(normalized) || normalized.startsWith(base))
            }
            if (localMatch != null) {
                val baseName = localMatch.nameWithoutExtension
                return PluginMetadata(
                    name = baseName,
                    internalName = baseName,
                    url = null,
                    repositoryName = "Local Cache",
                    language = "en"
                )
            }
        } catch (_: Throwable) {}

        return null
    }

    /**
     * List all distinct repository names available.
     */
    fun getRepositoryNames(): List<String> {
        val synced = cachedPlugins.mapNotNull { it.repositoryName }.distinct().filter { it.isNotBlank() }
        return synced.distinct()
    }

    /**
     * List all distinct languages for a specific repository.
     */
    fun getLanguagesForRepo(repoName: String): List<String> {
        val langs = cachedPlugins.filter { it.repositoryName.equals(repoName, ignoreCase = true) }
            .mapNotNull { it.language?.lowercase()?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        return listOf("all") + langs
    }

    /**
     * Get plugins for a specific repository and language filter.
     */
    fun getPluginsForRepo(repoName: String, lang: String? = null): List<PluginMetadata> {
        return cachedPlugins.filter { p ->
            val matchRepo = p.repositoryName.equals(repoName, ignoreCase = true)
            val matchLang = if (lang != null && lang != "all") {
                p.language.equals(lang, ignoreCase = true)
            } else true
            matchRepo && matchLang
        }
    }

    /**
     * Get summary of total repositories and plugins loaded.
     */
    fun getSummary(): Map<String, Any> {
        val reposCount = cachedPlugins.mapNotNull { it.repositoryName }.distinct().size
        return mapOf(
            "totalPlugins" to cachedPlugins.size,
            "totalRepositories" to reposCount,
            "languages" to cachedPlugins.mapNotNull { it.language }.distinct()
        )
    }

    /**
     * Returns all available sources aggregated across all synced remote repositories.
     */
    fun getAllAggregatedSources(): List<AggregatedSource> {
        val list = mutableListOf<AggregatedSource>()
        val seen = mutableSetOf<String>()

        // Remote repository plugins
        for (p in cachedPlugins) {
            val key = p.name.lowercase()
            if (!seen.contains(key)) {
                seen.add(key)
                list.add(
                    AggregatedSource(
                        name = p.name,
                        language = p.language?.lowercase() ?: "en",
                        description = p.description
                    )
                )
            }
        }
        return list
    }

    /**
     * Returns aggregated sources filtered by language code ('en', 'fa', 'ar', or 'all'/null).
     */
    fun getAggregatedSources(langFilter: String? = null): List<AggregatedSource> {
        val all = getAllAggregatedSources()
        if (langFilter.isNullOrBlank() || langFilter == "all") return all
        return all.filter { it.language.equals(langFilter, ignoreCase = true) }
    }

    /**
     * Returns all distinct language codes available across all sources, with 'all' first.
     */
    fun getAllSourceLanguages(): List<String> {
        val langs = getAllAggregatedSources().map { it.language.lowercase().trim() }.distinct().sorted()
        return listOf("all") + langs
    }
}

data class AggregatedSource(
    val name: String,
    val language: String,
    val description: String? = null
)

