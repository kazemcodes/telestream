package com.telestream

import com.telestream.telegram.InlineKeyboardButton
import com.telestream.telegram.MenuButton
import com.telestream.telegram.WebAppInfo
import com.telestream.webapp.WebAppHtml
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class WebAppTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testWebAppHtmlRendering() {
        val html = WebAppHtml.renderHtml()
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("telegram-web-app.js"))
        assertTrue(html.contains("hls.min.js"))
        assertTrue(html.contains("videoPlayer"))
        assertTrue(html.contains("searchMedia"))
        assertTrue(html.contains("i18n"))
    }

    @Test
    fun testWebAppModelsSerialization() {
        val button = InlineKeyboardButton(
            text = "Launch App",
            webApp = WebAppInfo("https://example.com/webapp")
        )
        val buttonJson = json.encodeToString(button)
        assertTrue(buttonJson.contains("web_app"))
        assertTrue(buttonJson.contains("https://example.com/webapp"))

        val menuBtn = MenuButton(
            type = "web_app",
            text = "🎬 TeleStream",
            webApp = WebAppInfo("https://example.com/webapp")
        )
        val menuJson = json.encodeToString(menuBtn)
        assertTrue(menuJson.contains("menu_button") || menuJson.contains("web_app"))
    }
}
