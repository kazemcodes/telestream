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
}
