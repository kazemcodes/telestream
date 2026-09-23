package com.telestream

import com.telestream.repo.CloudStreamPluginLoader
import com.telestream.repo.CloudStreamRepoManager
import com.telestream.repo.PluginMetadata
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    fun testLoadXDMoviesPlugin() {
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

        val ssPopular = com.telestream.providers.ProviderManager.getPopular("SuperStream")
        assertTrue(ssPopular.isNotEmpty(), "SuperStream getPopular should return results")
        println("Verified SuperStream getPopular: found ${ssPopular.size} items")
    }
}
