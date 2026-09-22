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

    // Default curated repositories from cloudstreamrepo.com
    val DEFAULT_REPOS = listOf(
        RepositoryInfo(
            id = "cs-karma",
            name = "Cs-Karma",
            url = "https://raw.githubusercontent.com/Kraptor123/Cs-Karma/refs/heads/master/repo.json",
            description = "Multi-language, English, Anime, and Sports streams",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "re-3arabi",
            name = "Re-3arabi",
            url = "https://raw.githubusercontent.com/re-3arabi/re-3arabi/refs/heads/master/repo.json",
            description = "Arabic & Regional Movies and Series",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "hexated",
            name = "Hexated Providers",
            url = "https://raw.githubusercontent.com/Hexated/Cloudstream-Extensions/builds/repo.json",
            description = "Popular English Movies, Anime, and Series",
            pluginsCount = 0
        ),
        RepositoryInfo(
            id = "stormunblessed",
            name = "Stormunblessed",
            url = "https://raw.githubusercontent.com/stormunblessed/stormunblessed-cs3/builds/repo.json",
            description = "English Streaming Providers",
            pluginsCount = 0
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
                cachedPlugins.addAll(list)
                logger.info("Loaded ${cachedPlugins.size} plugins from local cache.")
            } catch (e: Exception) {
                logger.warn("Could not read local plugins cache: ${e.message}")
            }
        }
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

            // Parse repo.json
            val manifest = try {
                mapper.readValue(resp.text, RepoManifest::class.java)
            } catch (e: Exception) {
                RepoManifest(name = customName ?: "Custom Repo", pluginLists = listOf(repoUrl))
            }

            val repoName = customName ?: manifest.name.ifBlank { "CloudStream Repo" }
            val fetchedPlugins = mutableListOf<PluginMetadata>()

            val pluginLists = if (manifest.pluginLists.isNotEmpty()) manifest.pluginLists else listOf(repoUrl)
            for (pUrl in pluginLists) {
                try {
                    val pResp = app.get(pUrl)
                    if (pResp.isSuccessful) {
                        val plugins = mapper.readValue(
                            pResp.text,
                            mapper.typeFactory.constructCollectionType(List::class.java, PluginMetadata::class.java)
                        ) as List<PluginMetadata>

                        val enriched = plugins.map {
                            it.copy(repositoryUrl = repoUrl, repositoryName = repoName)
                        }
                        fetchedPlugins.addAll(enriched)
                    }
                } catch (e: Exception) {
                    logger.warn("Error fetching plugin list from $pUrl: ${e.message}")
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
                description = manifest.description,
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
        return cachedPlugins.firstOrNull {
            it.name.equals(name, ignoreCase = true) ||
            (it.internalName != null && it.internalName.equals(name, ignoreCase = true)) ||
            it.name.startsWith(name, ignoreCase = true) ||
            name.startsWith(it.name, ignoreCase = true)
        }
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
}
