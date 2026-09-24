package com.telestream

import com.telestream.repo.CloudStreamPluginLoader
import com.telestream.repo.CloudStreamRepoManager
import com.telestream.repo.PluginMetadata
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CloudStreamPluginLoaderTest {

    @Test
    fun testLoadPluginFromMetadata() {
        // Ensure test_plugin.cs3 is placed into data/plugins_cache/KissKH.cs3
        val cacheDir = File("data/plugins_cache").apply { mkdirs() }
        val testCs3 = File("test_plugin.cs3")
        if (testCs3.exists()) {
            testCs3.copyTo(File(cacheDir, "KissKH.cs3"), overwrite = true)
        }

        val metadata = PluginMetadata(
            name = "KissKH",
            internalName = "KissKH",
            url = "https://raw.githubusercontent.com/Kraptor123/Cs-Karma/builds/KissKH.cs3",
            repositoryName = "Cs-Karma"
        )

        val provider = CloudStreamPluginLoader.loadPlugin(metadata)
        assertNotNull(provider, "KissKH should be loaded dynamically by CloudStreamPluginLoader")
        assertTrue(provider.name.contains("KissKH", ignoreCase = true))
        println("Successfully loaded dynamic provider: ${provider.name} (${provider.mainUrl})")
    }

    @Test
    fun testRepoManagerAndPluginLookup() {
        val allPlugins = CloudStreamRepoManager.getAllPlugins()
        assertTrue(allPlugins.isNotEmpty(), "Repository manager should have synced plugins")

        val kisskh = CloudStreamRepoManager.getPlugin("KissKH")
        assertNotNull(kisskh, "KissKH should be found in repository plugins")
        println("Found plugin metadata in repo: ${kisskh.name} -> ${kisskh.url}")
    }

    @Test
    fun testLoadStreamPlayPlugin() {
        val streamPlayCs3 = File("data/plugins_cache/StreamPlay.cs3")
        if (streamPlayCs3.exists()) {
            val metadata = PluginMetadata(
                name = "StreamPlay",
                internalName = "StreamPlay",
                url = "local",
                repositoryName = "local"
            )
            val provider = CloudStreamPluginLoader.loadPlugin(metadata)
            assertNotNull(provider, "StreamPlay should load successfully")
            println("Successfully loaded StreamPlay provider: ${provider.name}")
            val animeProvider = com.telestream.providers.ProviderManager.getProvider("StreamPlay-Anime")
            assertNotNull(animeProvider, "StreamPlay-Anime should also be registered")
            println("Successfully verified StreamPlay-Anime: ${animeProvider.name}")
        }
    }

    @Test
    fun testLoadXDMoviesPlugin() = kotlinx.coroutines.runBlocking {
        val xdMoviesCs3 = File("data/plugins_cache/XDMovies.cs3")
        if (xdMoviesCs3.exists()) {
            val metadata = PluginMetadata(
                name = "XDMovies",
                internalName = "XDMovies",
                url = "local",
                repositoryName = "local"
            )
            val provider = CloudStreamPluginLoader.loadPlugin(metadata)
            assertNotNull(provider, "XDMovies should load successfully without NoSuchMethodError: base64Decode")
            println("Successfully loaded XDMovies provider: ${provider.name} (${provider.mainUrl})")

            try {
                val results = com.telestream.providers.ProviderManager.searchInProvider("XD Movies", "batman")
                println("XDMovies search executed, found: ${results.size} items")
            } catch (e: Throwable) {
                val stack = e.stackTraceToString()
                println("XDMovies search executed with: ${e.javaClass.simpleName}: ${e.message}")
                assertFalse(stack.contains("DataStore"), "Should not throw NoClassDefFoundError for DataStore")
            }
        }
    }
    @Test
    fun testStreamPlayAndSuperStreamSearchAndPopular() = kotlinx.coroutines.runBlocking {
        System.setProperty("kotlinx.coroutines.stacktrace.recovery", "false")
        com.lagradost.cloudstream3.MainActivity.initNetwork()

        // 1. Verify StreamPlay
        val spMeta = PluginMetadata(name = "StreamPlay", internalName = "StreamPlay", url = "local", repositoryName = "local")
        val spProvider = CloudStreamPluginLoader.loadPlugin(spMeta)
        assertNotNull(spProvider, "StreamPlay should load")

        val spSearch = com.telestream.providers.ProviderManager.searchInProvider("StreamPlay", "batman")
        assertTrue(spSearch.isNotEmpty(), "StreamPlay search should return results")
        println("Verified StreamPlay search: found ${spSearch.size} items for 'batman'")

        val firstItem = spSearch.first()
        println("Testing StreamPlay.load with url: ${firstItem.url}")
        val loaded = com.telestream.providers.ProviderManager.load("StreamPlay", firstItem.url)
        assertNotNull(loaded, "StreamPlay.load should succeed without SerializationException")
        println("Verified StreamPlay load: name=${loaded.name}, url=${loaded.url}")

        val linkData = when (loaded) {
            is com.lagradost.cloudstream3.MovieLoadResponse -> loaded.dataUrl
            is com.lagradost.cloudstream3.TvSeriesLoadResponse -> loaded.episodes.firstOrNull()?.data
            else -> null
        }
        println("Testing StreamPlay.loadLinks with data: $linkData")
        if (linkData != null) {
            val links = com.telestream.providers.ProviderManager.loadLinks("StreamPlay", linkData)
            println("StreamPlay loadLinks executed! Found ${links.size} links")
        }

        val spPopular = com.telestream.providers.ProviderManager.getPopular("StreamPlay")
        assertTrue(spPopular.isNotEmpty(), "StreamPlay getPopular should return results")
        println("Verified StreamPlay getPopular: found ${spPopular.size} items")

        // 2. Verify SuperStream
        val ssMeta = PluginMetadata(name = "SuperStream", internalName = "SuperStream", url = "local", repositoryName = "local")
        val ssProvider = CloudStreamPluginLoader.loadPlugin(ssMeta)
        assertNotNull(ssProvider, "SuperStream should load")

        val ssSearch = com.telestream.providers.ProviderManager.searchInProvider("SuperStream", "batman")
        assertTrue(ssSearch.isNotEmpty(), "SuperStream search should return results")
        println("Verified SuperStream search: found ${ssSearch.size} items for 'batman'")

        val firstSs = ssSearch.first()
        println("Testing SuperStream.load with url: ${firstSs.url}")
        val ssLoaded = com.telestream.providers.ProviderManager.load("SuperStream", firstSs.url)
        assertNotNull(ssLoaded, "SuperStream.load should succeed")
        println("Verified SuperStream load: name=${ssLoaded.name}")

        val ssLinkData = when (ssLoaded) {
            is com.lagradost.cloudstream3.MovieLoadResponse -> ssLoaded.dataUrl
            is com.lagradost.cloudstream3.TvSeriesLoadResponse -> ssLoaded.episodes.firstOrNull()?.data
            else -> null
        }
        println("Testing SuperStream.loadLinks with data: $ssLinkData")
        if (ssLinkData != null) {
            val links = com.telestream.providers.ProviderManager.loadLinks("SuperStream", ssLinkData)
            println("SuperStream loadLinks executed! Found ${links.size} links")
        }

        val ssPopular = com.telestream.providers.ProviderManager.getPopular("SuperStream")
        assertTrue(ssPopular.isNotEmpty(), "SuperStream getPopular should return results")
        println("Verified SuperStream getPopular: found ${ssPopular.size} items")
    }

    @Test
    fun testSuperStreamSimpsonsLoad() = kotlinx.coroutines.runBlocking {
        System.setProperty("kotlinx.coroutines.stacktrace.recovery", "false")
        com.lagradost.cloudstream3.MainActivity.initNetwork()

        val ssMeta = PluginMetadata(name = "SuperStream", internalName = "SuperStream", url = "local", repositoryName = "local")
        val ssProvider = CloudStreamPluginLoader.loadPlugin(ssMeta)
        assertNotNull(ssProvider, "SuperStream should load")

        val simpsonsSearch = com.telestream.providers.ProviderManager.searchInProvider("SuperStream", "The Simpsons")
        assertTrue(simpsonsSearch.isNotEmpty(), "SuperStream search should find The Simpsons")
        val simpsons = simpsonsSearch.first { it.name.contains("Simpsons", ignoreCase = true) }
        println("Found The Simpsons: ${simpsons.name}, url: ${simpsons.url}")

        val simpsonsLoaded = com.telestream.providers.ProviderManager.load("SuperStream", simpsons.url)
        assertNotNull(simpsonsLoaded, "SuperStream.load should succeed for The Simpsons without split NullPointerException")
        assertTrue(simpsonsLoaded is com.lagradost.cloudstream3.TvSeriesLoadResponse, "The Simpsons should be a TvSeriesLoadResponse")
        println("Verified The Simpsons load: ${simpsonsLoaded.name}, total episodes: ${simpsonsLoaded.episodes.size}")
        assertTrue(simpsonsLoaded.episodes.isNotEmpty(), "The Simpsons should have loaded episodes")
    }
}
