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
    fun testFastJumpMath() {
        val total = 25
        var current = 2
        // Jump -5 from 2: ((2 - 5) % 25 + 25) % 25 = 22
        val jumpMinus5 = ((current - 5) % total + total) % total
        assertEquals(22, jumpMinus5)

        // Jump +5 from 2: (2 + 5) % 25 = 7
        val jumpPlus5 = (current + 5) % total
        assertEquals(7, jumpPlus5)

        // Jump +5 from 23: (23 + 5) % 25 = 3
        current = 23
        val jumpPlus5FromEnd = (current + 5) % total
        assertEquals(3, jumpPlus5FromEnd)
    }

    @Test
    fun testPagePartitioningMath() {
        val totalItems = 25
        val pageSize = 8
        val totalPages = (totalItems + pageSize - 1) / pageSize
        assertEquals(4, totalPages)

        // Page for item 0
        var pageIndex = (0 / pageSize).coerceIn(0, totalPages - 1)
        assertEquals(0, pageIndex)

        // Page for item 15
        pageIndex = (15 / pageSize).coerceIn(0, totalPages - 1)
        assertEquals(1, pageIndex)

        // Page for item 24
        pageIndex = (24 / pageSize).coerceIn(0, totalPages - 1)
        assertEquals(3, pageIndex)
    }
}

