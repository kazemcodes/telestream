package com.telestream

import com.lagradost.cloudstream3.TvType
import com.telestream.database.Database
import com.telestream.providers.AvaMovie
import com.telestream.providers.FaselHD
import com.telestream.providers.KissKH
import com.telestream.providers.ProviderManager
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProviderTest {

    @Test
    fun testProviderRegistration() {
        assertNotNull(ProviderManager.getProvider("KissKH"))
        assertNotNull(ProviderManager.getProvider("AvaMovie (فارسی)"))
        assertNotNull(ProviderManager.getProvider("FaselHD (العربية)"))
        assertEquals(3, ProviderManager.providers.size)
    }

    @Test
    fun testPersianTextDetection() {
        assertTrue(ProviderManager.isPersianText("جوکر"))
        assertTrue(ProviderManager.isPersianText("پوست شیر"))
        assertTrue(ProviderManager.isPersianText("سلام"))
        assertFalse(ProviderManager.isPersianText("Spider-Man"))
        assertFalse(ProviderManager.isPersianText("Dune Part 2"))
    }

    @Test
    fun testProvidersMetadata() {
        val kiss = KissKH()
        assertEquals("en", kiss.lang)
        assertTrue(kiss.supportedTypes.contains(TvType.TvSeries))

        val ava = AvaMovie()
        assertEquals("fa", ava.lang)
        assertTrue(ava.supportedTypes.contains(TvType.Movie))

        val fasel = FaselHD()
        assertEquals("ar", fasel.lang)
    }

    @Test
    fun testNsfwFiltering() {
        // Create a dummy NSFW provider
        val nsfwProvider = object : com.lagradost.cloudstream3.MainAPI() {
            override var name = "AdultTestProvider"
            override var mainUrl = "https://adult.example.com"
            override var supportedTypes = setOf(TvType.NSFW)
            override suspend fun search(query: String): List<com.lagradost.cloudstream3.SearchResponse> = emptyList()
        }
        ProviderManager.register(nsfwProvider)

        // When NSFW is disabled
        com.telestream.database.Database.setNsfwEnabled(false)
        kotlin.test.assertNull(ProviderManager.getProvider("AdultTestProvider"))

        // When NSFW is enabled
        com.telestream.database.Database.setNsfwEnabled(true)
        kotlin.test.assertNotNull(ProviderManager.getProvider("AdultTestProvider"))

        // Clean up
        com.telestream.database.Database.setNsfwEnabled(false)
        ProviderManager.providers.remove(nsfwProvider)
    }

    @Test
    fun testProviderFilters() {
        val faProviders = ProviderManager.getProvidersByFilter("fa")
        assertTrue(faProviders.any { it.name.contains("AvaMovie", ignoreCase = true) })

        val arProviders = ProviderManager.getProvidersByFilter("ar")
        assertTrue(arProviders.any { it.name.contains("FaselHD", ignoreCase = true) })

        val animeProviders = ProviderManager.getProvidersByFilter("anime")
        assertTrue(animeProviders.any { it.name.contains("KissKH", ignoreCase = true) })

        val allProviders = ProviderManager.getProvidersByFilter("all")
        assertEquals(3, allProviders.size)
    }

    @Test
    fun testUserSourcePersistence() {
        val testUserId = 777888L
        Database.setUserSource(testUserId, "KissKH")
        assertEquals("KissKH", Database.getUserSource(testUserId))

        Database.setUserSource(testUserId, "AvaMovie (فارسی)")
        assertEquals("AvaMovie (فارسی)", Database.getUserSource(testUserId))
    }

    @Test
    fun testCallbackTokenCache() {
        val ref = com.telestream.bot.MediaRef("AvaMovie", "https://avamovie3.info/series/12345")
        val token = com.telestream.bot.CallbackTokenCache.put(ref)
        assertNotNull(token)
        assertTrue(token.length < 10) // compact token!

        val retrieved = com.telestream.bot.CallbackTokenCache.get<com.telestream.bot.MediaRef>(token)
        assertNotNull(retrieved)
        assertEquals("AvaMovie", retrieved.provider)
        assertEquals("https://avamovie3.info/series/12345", retrieved.url)
    }

    @Test
    fun testGlobalSearchDisabled() {
        org.junit.jupiter.api.assertThrows<UnsupportedOperationException> {
            kotlinx.coroutines.runBlocking {
                @Suppress("DEPRECATION_ERROR")
                ProviderManager.search("test")
            }
        }
    }

    @Test
    fun testSourceEnableDisableAndBulk() {
        val testUser = System.currentTimeMillis()

        // Built-in sources default to enabled
        assertTrue(Database.isSourceEnabled(testUser, "AvaMovie (فارسی)"))
        assertTrue(Database.isSourceEnabled(testUser, "KissKH"))
        // Non-builtin sources default to disabled
        assertFalse(Database.isSourceEnabled(testUser, "CustomPluginX"))

        // Toggle non-builtin to enabled
        val toggledOn = Database.toggleSourceEnabled(testUser, "CustomPluginX")
        assertTrue(toggledOn)
        assertTrue(Database.isSourceEnabled(testUser, "CustomPluginX"))

        // Toggle built-in to disabled
        val toggledOff = Database.toggleSourceEnabled(testUser, "KissKH")
        assertFalse(toggledOff)
        assertFalse(Database.isSourceEnabled(testUser, "KissKH"))

        // Verify getEnabledSources
        val enabledList = Database.getEnabledSources(testUser)
        assertTrue(enabledList.contains("CustomPluginX"))
        assertTrue(enabledList.contains("AvaMovie (فارسی)"))
        assertFalse(enabledList.contains("KissKH"))

        // Bulk operations
        Database.setSourcesBulk(testUser, listOf("SourceA", "SourceB"), true)
        assertTrue(Database.isSourceEnabled(testUser, "SourceA"))
        assertTrue(Database.isSourceEnabled(testUser, "SourceB"))

        Database.setSourcesBulk(testUser, listOf("SourceA", "SourceB"), false)
        assertFalse(Database.isSourceEnabled(testUser, "SourceA"))
        assertFalse(Database.isSourceEnabled(testUser, "SourceB"))
    }

    @Test
    fun testCloudStreamRepoManagerHelpers() {
        val repos = com.telestream.repo.CloudStreamRepoManager.getRepositoryNames()
        assertTrue(repos.contains("Built-in Sources"))

        val builtInLangs = com.telestream.repo.CloudStreamRepoManager.getLanguagesForRepo("Built-in Sources")
        assertTrue(builtInLangs.contains("fa"))
        assertTrue(builtInLangs.contains("ar"))
        assertTrue(builtInLangs.contains("en"))

        val builtInPlugins = com.telestream.repo.CloudStreamRepoManager.getPluginsForRepo("Built-in Sources")
        assertEquals(3, builtInPlugins.size)
        assertTrue(builtInPlugins.any { it.name.contains("AvaMovie") })
    }

    @Test
    fun testPopularAndLatestFeeds() {
        kotlinx.coroutines.runBlocking {
            val popularKiss = ProviderManager.getPopular("KissKH")
            assertNotNull(popularKiss)

            val latestAva = ProviderManager.getLatest("AvaMovie (فارسی)")
            assertNotNull(latestAva)

            val nonExistent = ProviderManager.getPopular("UnknownProviderXYZ")
            assertTrue(nonExistent.isEmpty())
        }
    }
}
