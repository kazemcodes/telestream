package com.telestream

import com.telestream.database.Database
import com.telestream.repo.AggregatedSource
import com.telestream.repo.CloudStreamRepoManager
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SourceSearchTest {

    @Test
    fun testFilterSourcesByQuery() {
        val testSources = listOf(
            AggregatedSource("KissKH", "en", description = "Drama, Anime & Movies"),
            AggregatedSource("AvaMovie", "fa", description = "Persian Movies & Series"),
            AggregatedSource("FaselHD", "ar", description = "Arabic Movies"),
            AggregatedSource("XD Movies", "en", description = "Hindi, English & Telugu Movies"),
            AggregatedSource("AnimePahe", "en", description = "High quality Anime stream")
        )

        // Query by partial name
        val kissMatch = testSources.filter { it.name.contains("kiss", ignoreCase = true) }
        assertEquals(1, kissMatch.size)
        assertEquals("KissKH", kissMatch.first().name)

        // Query by keyword in description (e.g. "anime")
        val animeMatches = testSources.filter {
            it.name.contains("anime", ignoreCase = true) ||
            it.description?.contains("anime", ignoreCase = true) == true
        }
        assertEquals(2, animeMatches.size)
        assertTrue(animeMatches.any { it.name == "KissKH" })
        assertTrue(animeMatches.any { it.name == "AnimePahe" })

        // Query by language
        val faMatches = testSources.filter { it.language.equals("fa", ignoreCase = true) }
        assertEquals(1, faMatches.size)
        assertEquals("AvaMovie", faMatches.first().name)
    }

    @Test
    fun testQuickSourceSwitchingInDatabase() {
        val testUserId = 99998888L
        Database.ensureUser(testUserId)

        // Default should be KissKH or initial
        val initial = Database.getUserSource(testUserId)
        assertNotNull(initial)

        // Fast switch to XD Movies
        Database.setUserSource(testUserId, "XD Movies")
        assertEquals("XD Movies", Database.getUserSource(testUserId))

        // Fast switch to AvaMovie
        Database.setUserSource(testUserId, "AvaMovie")
        assertEquals("AvaMovie", Database.getUserSource(testUserId))
    }
}
