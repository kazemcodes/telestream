package com.telestream

import com.telestream.i18n.I18n
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class I18nTest {

    @Test
    fun testTranslations() {
        val welcomeEn = I18n.t("welcome", "en")
        assertTrue(welcomeEn.contains("TeleStream"))

        val welcomeFa = I18n.t("welcome", "fa")
        assertTrue(welcomeFa.contains("تله‌استریم"))

        val searchPromptEn = I18n.t("search_prompt", "en")
        assertTrue(searchPromptEn.contains("type the name"))

        val searchPromptFa = I18n.t("search_prompt", "fa")
        assertTrue(searchPromptFa.contains("نام فیلم"))

        val donateEn = I18n.donationMessage("en")
        assertTrue(donateEn.contains("USDT") && donateEn.contains("TON") && donateEn.contains("BTC"))

        val donateFa = I18n.donationMessage("fa")
        assertTrue(donateFa.contains("حمایت مالی") && donateFa.contains("USDT"))

        val adminHelpEn = I18n.t("admin_help", "en")
        assertTrue(adminHelpEn.contains("/sync") && adminHelpEn.contains("/nsfw"))
    }

    @Test
    fun testBotRunnerAllKeysPresent() {
        val botFile = java.io.File("src/main/kotlin/com/telestream/bot/BotRunner.kt")
        val content = botFile.readText()
        val regex = Regex("""(?<![a-zA-Z0-9_])t\(\s*"([a-z0-9_]+)"""")
        val keysUsed = (regex.findAll(content).map { it.groupValues[1] } + listOf("feed_latest_title", "feed_popular_title", "btn_switch_source")).distinct().toList()

        val missingInEn = mutableListOf<String>()
        val missingInFa = mutableListOf<String>()

        for (k in keysUsed) {
            val enVal = I18n.t(k, "en")
            if (enVal == k) missingInEn.add(k)
            val faVal = I18n.t(k, "fa")
            if (faVal == k) missingInFa.add(k)
        }

        assertTrue(missingInEn.isEmpty(), "Missing keys in EN: $missingInEn")
        assertTrue(missingInFa.isEmpty(), "Missing keys in FA: $missingInFa")
    }
}
