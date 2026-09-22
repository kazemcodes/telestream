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
}
