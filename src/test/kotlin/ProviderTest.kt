package com.telestream

import com.lagradost.cloudstream3.TvType
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
}
