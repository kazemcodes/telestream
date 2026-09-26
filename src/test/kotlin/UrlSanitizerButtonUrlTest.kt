package com.telestream

import com.telestream.bot.util.UrlSanitizer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Telegram resolves inline-keyboard button URLs from its own servers, so a button pointing at
 * `http://localhost:<port>/r/<token>` is rejected with "Wrong HTTP URL" and the entire message
 * fails to send. These tests pin the behaviour that avoids emitting such URLs.
 */
class UrlSanitizerButtonUrlTest {

    /** Comfortably over the 500-char button limit so the redirect path is exercised. */
    private val longUrl = "https://example.com/" + "a".repeat(600) + ".m3u8"

    @Test
    fun `short urls are passed through untouched`() {
        val url = "https://example.com/video.m3u8"
        assertEquals(url, UrlSanitizer.getSafeButtonUrl(url))
    }

    @Test
    fun `button url is never a localhost redirect`() {
        val result = UrlSanitizer.getSafeButtonUrl(longUrl)
        assertNotNull(result, "a long url should still yield something usable")
        assertTrue(
            !result.contains("localhost") && !result.contains("127.0.0.1") && !result.contains("0.0.0.0"),
            "button url must not point at a loopback host, got: $result"
        )
        assertTrue(
            result.startsWith("http://") || result.startsWith("https://") || result.startsWith("tg://"),
            "button url must be a valid scheme, got: $result"
        )
    }

    @Test
    fun `non http urls are rejected`() {
        assertEquals(null, UrlSanitizer.getSafeButtonUrl("javascript:alert(1)"))
        assertEquals(null, UrlSanitizer.getSafeButtonUrl(""))
        assertEquals(null, UrlSanitizer.getSafeButtonUrl(null))
    }

    @Test
    fun `spaces are percent encoded`() {
        assertEquals(
            "https://example.com/a%20b.m3u8",
            UrlSanitizer.sanitizeTelegramUrl("https://example.com/a b.m3u8")
        )
    }
}