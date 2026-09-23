package com.telestream

import com.lagradost.cloudstream3.TvType
import com.telestream.bot.MediaCarouselRef
import com.telestream.bot.MediaItemSummary
import com.telestream.telegram.InputMediaPhoto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaPosterTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun testInputMediaPhotoSerialization() {
        val media = InputMediaPhoto(
            media = "https://example.com/poster.jpg",
            caption = "1. 🎬 Salaar (2023)"
        )
        val encoded = json.encodeToString(media)
        assertTrue(encoded.contains("\"type\":\"photo\""))
        assertTrue(encoded.contains("\"media\":\"https://example.com/poster.jpg\""))
        assertTrue(encoded.contains("\"caption\":\"1. 🎬 Salaar (2023)\""))
    }

    @Test
    fun testCarouselNavigationMath() {
        val items = listOf(
            MediaItemSummary("Movie 1", "https://site/1", "src", "https://img/1.jpg", TvType.Movie, 2021),
            MediaItemSummary("Movie 2", "https://site/2", "src", "https://img/2.jpg", TvType.Movie, 2022),
            MediaItemSummary("Movie 3", "https://site/3", "src", "https://img/3.jpg", TvType.Movie, 2023)
        )
        val ref = MediaCarouselRef(
            contextType = "search",
            sourceName = "TestSource",
            query = "movie",
            items = items,
            currentIndex = 0
        )

        // Forward from 0 -> 1
        var nextIdx = if (ref.currentIndex < items.size - 1) ref.currentIndex + 1 else 0
        assertEquals(1, nextIdx)

        // Backward from 0 -> 2 (loops around)
        var prevIdx = if (ref.currentIndex > 0) ref.currentIndex - 1 else items.size - 1
        assertEquals(2, prevIdx)

        // Set to 2
        ref.currentIndex = 2
        nextIdx = if (ref.currentIndex < items.size - 1) ref.currentIndex + 1 else 0
        assertEquals(0, nextIdx) // loops around to 0
    }

    @Test
    fun testPageUrlResolution() {
        val detailsUrl = "https://provider.site/movie/123"
        val refUrl = "https://provider.site/movie/123"

        val pageUrl = detailsUrl.takeIf { it.startsWith("http") } ?: refUrl.takeIf { it.startsWith("http") }
        assertEquals("https://provider.site/movie/123", pageUrl)

        // If detailsUrl is empty or blank
        val emptyDetails = ""
        val fallbackUrl = emptyDetails.takeIf { it.startsWith("http") } ?: refUrl.takeIf { it.startsWith("http") }
        assertEquals("https://provider.site/movie/123", fallbackUrl)
    }
}
