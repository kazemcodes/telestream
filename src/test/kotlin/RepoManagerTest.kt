package com.telestream

import com.telestream.repo.CloudStreamRepoManager
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RepoManagerTest {

    @Test
    fun testDefaultReposList() {
        val repos = CloudStreamRepoManager.DEFAULT_REPOS
        assertTrue(repos.isNotEmpty())
        assertTrue(repos.any { it.id == "cs-karma" })
        assertTrue(repos.any { it.id == "re-3arabi" })
        assertTrue(repos.any { it.id == "hexated" })
    }

    @Test
    fun testRepoManagerSummary() {
        val summary = CloudStreamRepoManager.getSummary()
        assertNotNull(summary["totalPlugins"])
        assertNotNull(summary["totalRepositories"])
    }

    @Test
    fun testAggregatedSources() {
        val allSources = CloudStreamRepoManager.getAllAggregatedSources()
        assertTrue(allSources.isNotEmpty())
        assertTrue(allSources.any { it.name == "KissKH" })
        assertTrue(allSources.any { it.name == "AvaMovie" })
        assertTrue(allSources.any { it.name == "FaselHD" })

        val enSources = CloudStreamRepoManager.getAggregatedSources("en")
        assertTrue(enSources.any { it.name == "KissKH" })

        val faSources = CloudStreamRepoManager.getAggregatedSources("fa")
        assertTrue(faSources.any { it.name == "AvaMovie" })

        val langs = CloudStreamRepoManager.getAllSourceLanguages()
        assertTrue(langs.contains("all"))
        assertTrue(langs.contains("en"))
        assertTrue(langs.contains("fa"))
    }
}
