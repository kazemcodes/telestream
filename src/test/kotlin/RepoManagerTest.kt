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
        assertTrue(repos.any { it.id == "cspr" })
        assertTrue(repos.any { it.id == "phisherrepo" })
        assertTrue(repos.any { it.id == "csx" })
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
        assertTrue(allSources.any { it.name == "StreamPlay" })
        assertTrue(allSources.any { it.name == "SuperStream" })

        val enSources = CloudStreamRepoManager.getAggregatedSources("en")
        assertTrue(enSources.any { it.name == "KissKH" })
        assertTrue(enSources.any { it.name == "StreamPlay" })

        val langs = CloudStreamRepoManager.getAllSourceLanguages()
        assertTrue(langs.contains("all"))
        assertTrue(langs.contains("en"))
    }

    @Test
    fun testAdminRepoDisablementHidesSources() {
        val initialSources = CloudStreamRepoManager.getAllAggregatedSources()
        assertTrue(initialSources.any { it.name == "StreamPlay" })

        // Disable Phisher Providers repo
        com.telestream.database.Database.setRepoEnabled("phisherrepo", "Phisher Providers", false)
        val filteredSources = CloudStreamRepoManager.getAllAggregatedSources()
        kotlin.test.assertFalse(filteredSources.any { it.name == "StreamPlay" })

        // Re-enable Phisher Providers repo
        com.telestream.database.Database.setRepoEnabled("phisherrepo", "Phisher Providers", true)
        val restoredSources = CloudStreamRepoManager.getAllAggregatedSources()
        assertTrue(restoredSources.any { it.name == "StreamPlay" })
    }
}
