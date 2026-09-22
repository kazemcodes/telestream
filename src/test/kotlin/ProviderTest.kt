package com.telestream

import com.lagradost.cloudstream3.TvType
import com.telestream.database.Database
import com.telestream.providers.ProviderManager
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProviderTest {

    @Test
    fun testProviderRegistration() {
        val dummy = object : com.lagradost.cloudstream3.MainAPI() {
            override var name = "TestProvider"
            override var mainUrl = "https://example.com"
            override var lang = "en"
            override var supportedTypes = setOf(TvType.TvSeries)
            override suspend fun search(query: String): List<com.lagradost.cloudstream3.SearchResponse> = emptyList()
        }
        ProviderManager.register(dummy)
        assertNotNull(ProviderManager.getProvider("TestProvider"))
        assertTrue(ProviderManager.providers.isNotEmpty())
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
    fun testNsfwFiltering() {
        val nsfwProvider = object : com.lagradost.cloudstream3.MainAPI() {
            override var name = "AdultTestProvider"
            override var mainUrl = "https://adult.example.com"
            override var supportedTypes = setOf(TvType.NSFW)
            override suspend fun search(query: String): List<com.lagradost.cloudstream3.SearchResponse> = emptyList()
        }
        ProviderManager.register(nsfwProvider)

        Database.setNsfwEnabled(false)
        kotlin.test.assertNull(ProviderManager.getProvider("AdultTestProvider"))

        Database.setNsfwEnabled(true)
        kotlin.test.assertNotNull(ProviderManager.getProvider("AdultTestProvider"))

        Database.setNsfwEnabled(false)
        ProviderManager.providers.remove(nsfwProvider)
    }

    @Test
    fun testProviderFilters() {
        val p1 = object : com.lagradost.cloudstream3.MainAPI() {
            override var name = "FaMovie"
            override var mainUrl = "https://fa.example.com"
            override var lang = "fa"
        }
        val p2 = object : com.lagradost.cloudstream3.MainAPI() {
            override var name = "ArMovie"
            override var mainUrl = "https://ar.example.com"
            override var lang = "ar"
        }
        val p3 = object : com.lagradost.cloudstream3.MainAPI() {
            override var name = "AnimeTest"
            override var mainUrl = "https://anime.example.com"
            override var lang = "en"
            override var supportedTypes = setOf(TvType.Anime)
        }
        ProviderManager.register(p1)
        ProviderManager.register(p2)
        ProviderManager.register(p3)

        val faProviders = ProviderManager.getProvidersByFilter("fa")
        assertTrue(faProviders.any { it.name == "FaMovie" })

        val arProviders = ProviderManager.getProvidersByFilter("ar")
        assertTrue(arProviders.any { it.name == "ArMovie" })

        val animeProviders = ProviderManager.getProvidersByFilter("anime")
        assertTrue(animeProviders.any { it.name == "AnimeTest" })

        ProviderManager.providers.removeAll(listOf(p1, p2, p3))
    }

    @Test
    fun testUserSourcePersistence() {
        val testUserId = 777888L
        Database.setUserSource(testUserId, "KissKH")
        assertEquals("KissKH", Database.getUserSource(testUserId))

        Database.setUserSource(testUserId, "AnimeAV")
        assertEquals("AnimeAV", Database.getUserSource(testUserId))
    }

    @Test
    fun testCallbackTokenCache() {
        val ref = com.telestream.bot.MediaRef("AnimeAV", "https://animeav1.com/series/12345")
        val token = com.telestream.bot.CallbackTokenCache.put(ref)
        assertNotNull(token)
        assertTrue(token.length < 10) // compact token!

        val retrieved = com.telestream.bot.CallbackTokenCache.get<com.telestream.bot.MediaRef>(token)
        assertNotNull(retrieved)
        assertEquals("AnimeAV", retrieved.provider)
        assertEquals("https://animeav1.com/series/12345", retrieved.url)
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

        // Default: KissKH enabled initially
        assertTrue(Database.isSourceEnabled(testUser, "KissKH"))
        assertFalse(Database.isSourceEnabled(testUser, "CustomPluginX"))

        // Toggle non-builtin to enabled
        val toggledOn = Database.toggleSourceEnabled(testUser, "CustomPluginX")
        assertTrue(toggledOn)
        assertTrue(Database.isSourceEnabled(testUser, "CustomPluginX"))

        // Toggle KissKH to disabled
        val toggledOff = Database.toggleSourceEnabled(testUser, "KissKH")
        assertFalse(toggledOff)
        assertFalse(Database.isSourceEnabled(testUser, "KissKH"))

        // Verify getEnabledSources
        val enabledList = Database.getEnabledSources(testUser)
        assertTrue(enabledList.contains("CustomPluginX"))
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
        assertTrue(repos.isNotEmpty())

        val plugins = com.telestream.repo.CloudStreamRepoManager.getAllPlugins()
        assertTrue(plugins.isNotEmpty())

        val firstRepo = repos.first()
        val langs = com.telestream.repo.CloudStreamRepoManager.getLanguagesForRepo(firstRepo)
        assertTrue(langs.contains("all"))

        val repoPlugins = com.telestream.repo.CloudStreamRepoManager.getPluginsForRepo(firstRepo)
        assertTrue(repoPlugins.isNotEmpty())
    }

    @Test
    fun testPopularAndLatestFeeds() {
        val p = object : com.lagradost.cloudstream3.MainAPI() {
            override var name = "FeedProvider"
            override var mainUrl = "https://feed.example.com"
            override suspend fun getPopular(page: Int): List<com.lagradost.cloudstream3.SearchResponse> {
                return listOf(com.lagradost.cloudstream3.MovieSearchResponse("Popular 1", "https://url1", name, TvType.Movie, null, null))
            }
            override suspend fun getLatest(page: Int): List<com.lagradost.cloudstream3.SearchResponse> {
                return listOf(com.lagradost.cloudstream3.MovieSearchResponse("Latest 1", "https://url2", name, TvType.Movie, null, null))
            }
        }
        ProviderManager.register(p)

        kotlinx.coroutines.runBlocking {
            val popular = ProviderManager.getPopular("FeedProvider")
            assertEquals(1, popular.size)
            assertEquals("Popular 1", popular[0].name)

            val latest = ProviderManager.getLatest("FeedProvider")
            assertEquals(1, latest.size)
            assertEquals("Latest 1", latest[0].name)

            val nonExistent = ProviderManager.getPopular("UnknownProviderXYZ")
            assertTrue(nonExistent.isEmpty())
        }

        ProviderManager.providers.remove(p)
    }
}
