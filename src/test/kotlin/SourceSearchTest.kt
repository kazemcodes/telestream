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

    @Test
    fun testCaseInsensitiveCommandParsing() {
        val testInputs = listOf(
            "/SEARCH Avatar" to Pair("/search", "Avatar"),
            "/search avatar" to Pair("/search", "avatar"),
            "/Search@TeleStreamBot Inception" to Pair("/search", "Inception"),
            "/SOURCE Kiss" to Pair("/source", "Kiss"),
            "/Src@MyBot Ava" to Pair("/src", "Ava"),
            "/START" to Pair("/start", "")
        )

        for ((input, expected) in testInputs) {
            val parts = input.split("\\s+".toRegex(), limit = 2)
            val rawCmd = parts.getOrNull(0) ?: ""
            val cmd = rawCmd.lowercase().substringBefore("@")
            val arg = parts.getOrNull(1)?.trim() ?: ""

            assertEquals(expected.first, cmd)
            assertEquals(expected.second, arg)
        }
    }

    @Test
    fun testCaseInsensitiveInlineQueryPrefixStripping() {
        val queries = listOf(
            "@source anime" to "anime",
            "@sources anime" to "anime",
            "@Source Anime" to "Anime",
            "@Sources Anime" to "Anime",
            "@SOURCE KISS" to "KISS",
            "@SOURCES KISS" to "KISS",
            "#source Drama" to "Drama",
            "/source Movie" to "Movie",
            "/sources Movie" to "Movie",
            "/SRC avatar" to "avatar",
            "@src action" to "action",
            "source: comedy" to "comedy",
            "sources: thriller" to "thriller",
            "just query" to "just query",
            "" to ""
        )

        val prefixRegex = "(?i)^[@#/]?(sources?|src)[:\\s]*".toRegex()
        for ((raw, expected) in queries) {
            val clean = raw.replaceFirst(prefixRegex, "").trim()
            assertEquals(expected, clean)
        }
    }

    @Test
    fun testInlineQueryResultSerialization() {
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            explicitNulls = false
        }

        val article = com.telestream.telegram.InlineQueryResultArticle(
            id = "src_0_abcd1234",
            title = "🔘 KissKH [EN]",
            description = "Active Source • Drama & Movies",
            inputMessageContent = com.telestream.telegram.InputTextMessageContent(
                messageText = "/source KissKH"
            )
        )

        val jsonStr = json.encodeToString(com.telestream.telegram.InlineQueryResultArticle.serializer(), article)
        println("Serialized InlineQueryResultArticle: $jsonStr")

        // Crucial: Must contain "type":"article" for Telegram Bot API
        assertTrue(jsonStr.contains("\"type\":\"article\""), "JSON must include type: 'article' for Telegram API")
        // Crucial: Must not fail with parse_mode for non-markdown commands
        assertTrue(!jsonStr.contains("\"parse_mode\""), "parse_mode should be omitted when null")
        // Crucial: id length must be <= 64 bytes
        assertTrue(article.id.toByteArray().size <= 64)
    }

    @Test
    fun testRepoManagerAggregatedSourcesNotEmpty() {
        val sources = CloudStreamRepoManager.getAllAggregatedSources()
        println("Aggregated sources count: ${sources.size}")
        assertTrue(sources.isNotEmpty())
    }

    @Test
    fun testSanitizeTelegramUrl() {
        val raw1 = "https://kisskh.id/Colony (2026)/12905"
        val expected1 = "https://kisskh.id/Colony%20(2026)/12905"
        assertEquals(expected1, com.telestream.bot.sanitizeTelegramUrl(raw1))

        val raw2 = "https://kisskh.id/Colony%20(2026)/12905"
        assertEquals(raw2, com.telestream.bot.sanitizeTelegramUrl(raw2))

        val raw3 = "https://example.com/movie name with spaces/file.mkv"
        val expected3 = "https://example.com/movie%20name%20with%20spaces/file.mkv"
        assertEquals(expected3, com.telestream.bot.sanitizeTelegramUrl(raw3))

        assertEquals(null, com.telestream.bot.sanitizeTelegramUrl(null))
        assertEquals(null, com.telestream.bot.sanitizeTelegramUrl(""))
        assertEquals(null, com.telestream.bot.sanitizeTelegramUrl("   "))
    }
}
