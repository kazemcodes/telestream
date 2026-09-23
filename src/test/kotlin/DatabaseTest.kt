package com.telestream

import com.telestream.database.Database
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseTest {

    @Test
    fun testUserLanguagePersistence() {
        val testUserId = 999888777L
        Database.setUserLanguage(testUserId, "fa")
        assertEquals("fa", Database.getUserLanguage(testUserId))

        Database.setUserLanguage(testUserId, "en")
        assertEquals("en", Database.getUserLanguage(testUserId))
    }

    @Test
    fun testBookmarksOperations() {
        val testUserId = 888777666L
        val testUrl = "https://example.com/test-movie"

        Database.addBookmark(testUserId, "KissKH", testUrl, "Test Movie", "https://example.com/poster.jpg")
        assertTrue(Database.isBookmarked(testUserId, testUrl))

        val bookmarks = Database.getBookmarks(testUserId)
        assertTrue(bookmarks.any { it.mediaUrl == testUrl })

        Database.removeBookmark(testUserId, testUrl)
        assertFalse(Database.isBookmarked(testUserId, testUrl))
    }

    @Test
    fun testNsfwSetting() {
        // Toggle off
        Database.setNsfwEnabled(false)
        assertFalse(Database.isNsfwEnabled())

        // Toggle on
        Database.setNsfwEnabled(true)
        assertTrue(Database.isNsfwEnabled())

        // Restore to default (false)
        Database.setNsfwEnabled(false)
        assertFalse(Database.isNsfwEnabled())
    }

    @Test
    fun testDatabaseStats() {
        val totalUsers = Database.getTotalUsers()
        assertTrue(totalUsers >= 0)
        val totalBookmarks = Database.getTotalBookmarks()
        assertTrue(totalBookmarks >= 0)
    }

    @Test
    fun testEnsureUserSmartDefaults() {
        val enUser = 111222333L
        Database.ensureUser(enUser, "en")
        assertEquals("en", Database.getUserLanguage(enUser))
        assertEquals("KissKH", Database.getUserSource(enUser))
        assertTrue(Database.isSourceEnabled(enUser, "KissKH"))

        val faUser = 444555666L
        Database.ensureUser(faUser, "fa")
        assertEquals("fa", Database.getUserLanguage(faUser))
        assertEquals("AvaMovie", Database.getUserSource(faUser))
        assertTrue(Database.isSourceEnabled(faUser, "AvaMovie"))
        assertTrue(Database.isSourceEnabled(faUser, "KissKH"))
    }

    @Test
    fun testUserSourceToggling() {
        val testUser = 777666555L
        Database.ensureUser(testUser, "en")
        // Initially FaselHD is disabled for this user
        assertFalse(Database.isSourceEnabled(testUser, "FaselHD"))

        // Toggle on
        Database.toggleSourceEnabled(testUser, "FaselHD")
        assertTrue(Database.isSourceEnabled(testUser, "FaselHD"))

        // Set as active
        Database.setUserSource(testUser, "FaselHD")
        assertEquals("FaselHD", Database.getUserSource(testUser))

        // Toggle off
        Database.toggleSourceEnabled(testUser, "FaselHD")
        assertFalse(Database.isSourceEnabled(testUser, "FaselHD"))
    }
}
