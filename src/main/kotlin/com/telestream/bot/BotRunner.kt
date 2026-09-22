package com.telestream.bot

import com.lagradost.cloudstream3.TvType
import com.telestream.config.Config
import com.telestream.database.Database
import com.telestream.i18n.I18n.t
import com.telestream.providers.ProviderManager
import com.telestream.repo.CloudStreamRepoManager
import com.telestream.telegram.CallbackQuery
import com.telestream.telegram.InlineKeyboardButton
import com.telestream.telegram.InlineKeyboardMarkup
import com.telestream.telegram.Message
import com.telestream.telegram.TelegramClient
import com.telestream.telegram.WebAppInfo
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class BotRunner(private val bot: TelegramClient) {
    private val logger = LoggerFactory.getLogger(BotRunner::class.java)
    private var isRunning = true

    // Multi-user debounce cache to prevent rapid double-clicks
    private val userLastAction = ConcurrentHashMap<Long, Long>()

    private fun isDebounced(userId: Long): Boolean {
        val now = System.currentTimeMillis()
        val last = userLastAction[userId] ?: 0L
        if (now - last < 350) {
            return true
        }
        userLastAction[userId] = now
        return false
    }

    fun stop() {
        isRunning = false
    }

    suspend fun startPolling() {
        logger.info("Starting TeleStream bot polling loop...")
        var offset = 0L

        while (isRunning) {
            try {
                val updates = bot.getUpdates(offset = offset, timeout = 25)
                for (update in updates) {
                    offset = update.updateId + 1

                    // High-concurrency: dispatch each update on Dispatchers.IO
                    coroutineScope {
                        launch(Dispatchers.IO) {
                            try {
                                if (update.message != null) {
                                    handleMessage(update.message)
                                } else if (update.callbackQuery != null) {
                                    handleCallback(update.callbackQuery)
                                }
                            } catch (e: Exception) {
                                logger.error("Error processing update ${update.updateId}: ${e.message}", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("Polling error: ${e.message}. Retrying in 3s...")
                delay(3000)
            }
        }
    }

    private fun getMainMenuKeyboard(lang: String): InlineKeyboardMarkup {
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        val webAppUrl = Config.webAppUrl
        if (webAppUrl.isNotBlank()) {
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = t("btn_webapp", lang),
                        webApp = WebAppInfo(webAppUrl)
                    )
                )
            )
        }
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_search", lang), callbackData = "menu:search"),
                InlineKeyboardButton(text = t("btn_bookmarks", lang), callbackData = "menu:bookmarks")
            )
        )
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_repos", lang), callbackData = "menu:repos"),
                InlineKeyboardButton(text = t("btn_donate", lang), callbackData = "menu:donate")
            )
        )
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_lang", lang), callbackData = "menu:lang")
            )
        )
        return InlineKeyboardMarkup(rows)
    }

    private suspend fun handleMessage(message: Message) {
        val chatId = message.chat.id
        val text = message.text?.trim() ?: return
        val userId = message.from?.id ?: chatId
        val lang = Database.getUserLanguage(userId)

        when {
            text.startsWith("/start") -> {
                bot.sendMessage(chatId, t("welcome", lang), replyMarkup = getMainMenuKeyboard(lang))
            }

            text.startsWith("/app") -> {
                val keyboard = InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            InlineKeyboardButton(
                                text = t("btn_webapp", lang),
                                webApp = WebAppInfo(Config.webAppUrl)
                            )
                        )
                    )
                )
                bot.sendMessage(
                    chatId,
                    if (lang == "fa") "🎬 *برای اجرای نسخه مینی‌اپ تله‌استریم روی دکمه زیر کلیک کنید:*"
                    else "🎬 *Click the button below to launch TeleStream Mini App:*",
                    replyMarkup = keyboard
                )
            }

            text.startsWith("/donate") -> {
                showDonationMessage(chatId, lang)
            }

            text.startsWith("/admin") -> {
                if (!Config.isAdmin(userId)) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                } else {
                    showAdminDashboard(chatId, lang)
                }
            }

            text.startsWith("/nsfw") -> {
                if (!Config.isAdmin(userId)) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                    return
                }
                val arg = text.removePrefix("/nsfw").trim().lowercase()
                val newStatus = when (arg) {
                    "on", "enable", "1", "true" -> true
                    "off", "disable", "0", "false" -> false
                    else -> !Database.isNsfwEnabled()
                }
                Database.setNsfwEnabled(newStatus)
                val statusStr = if (newStatus) t("nsfw_on", lang) else t("nsfw_off", lang)
                bot.sendMessage(chatId, t("nsfw_toggled", lang, statusStr))
            }

            text.startsWith("/repos") -> {
                showReposSummary(chatId, lang)
            }

            text.startsWith("/sync") -> {
                if (!Config.isAdmin(userId)) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                    return
                }
                bot.sendMessage(chatId, t("syncing", lang))
                val repos = CloudStreamRepoManager.syncAllDefaults()
                val totalPlugins = repos.sumOf { it.pluginsCount }
                bot.sendMessage(chatId, t("sync_done", lang, totalPlugins, repos.size))
            }

            text.startsWith("/addrepo") -> {
                if (!Config.isAdmin(userId)) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                    return
                }
                val url = text.removePrefix("/addrepo").trim()
                if (url.isBlank()) {
                    bot.sendMessage(chatId, "⚠️ Usage: `/addrepo <url>` (e.g., `https://example.com/repo.json`)")
                } else {
                    bot.sendMessage(chatId, "⏳ Fetching repository from `$url`...")
                    val repo = CloudStreamRepoManager.fetchRepository(url)
                    if (repo != null) {
                        bot.sendMessage(chatId, "✅ Added repository *${repo.name}* with *${repo.pluginsCount}* plugins!")
                    } else {
                        bot.sendMessage(chatId, "❌ Failed to fetch repository from `$url`.")
                    }
                }
            }

            text.startsWith("/language") -> {
                showLanguageSelector(chatId, lang)
            }

            text.startsWith("/bookmarks") -> {
                showBookmarks(chatId, userId, lang)
            }

            else -> {
                // Search query
                bot.sendMessage(chatId, t("searching", lang, text))
                val results = ProviderManager.search(text)

                if (results.isEmpty()) {
                    bot.sendMessage(chatId, t("no_results", lang, text))
                    return
                }

                val buttons = results.take(6).map { item ->
                    val icon = if (item.type == TvType.Movie) "🎬" else "📺"
                    val yearStr = item.year?.let { " ($it)" } ?: ""
                    listOf(
                        InlineKeyboardButton(
                            text = "$icon ${item.name}$yearStr [${item.apiName}]",
                            callbackData = "v:${item.apiName}:${item.url}".take(64)
                        )
                    )
                }.toMutableList()

                buttons.add(listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")))

                bot.sendMessage(
                    chatId,
                    t("search_results", lang, text),
                    replyMarkup = InlineKeyboardMarkup(buttons)
                )
            }
        }
    }

    private suspend fun handleCallback(callback: CallbackQuery) {
        val chatId = callback.message?.chat?.id ?: return
        val messageId = callback.message.messageId
        val data = callback.data ?: return
        val userId = callback.from.id
        val lang = Database.getUserLanguage(userId)

        if (isDebounced(userId) && !data.startsWith("setlang:") && data != "close") {
            bot.answerCallbackQuery(callback.id)
            return
        }

        when {
            data.startsWith("setlang:") -> {
                val newLang = data.removePrefix("setlang:")
                Database.setUserLanguage(userId, newLang)
                bot.answerCallbackQuery(callback.id, t("lang_changed", newLang), showAlert = true)
                bot.editMessageText(
                    chatId,
                    messageId,
                    t("welcome", newLang),
                    replyMarkup = getMainMenuKeyboard(newLang)
                )
            }

            data == "menu:lang" -> {
                showLanguageSelector(chatId, lang, messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:search" -> {
                bot.sendMessage(chatId, t("search_prompt", lang))
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:bookmarks" -> {
                showBookmarks(chatId, userId, lang, messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:repos" -> {
                showReposSummary(chatId, lang, messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:donate" -> {
                showDonationMessage(chatId, lang, messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "toggle_nsfw" -> {
                if (!Config.isAdmin(userId)) {
                    bot.answerCallbackQuery(callback.id, t("admin_only", lang), showAlert = true)
                    return
                }
                val current = Database.isNsfwEnabled()
                Database.setNsfwEnabled(!current)
                val newStatus = !current
                val statusStr = if (newStatus) t("nsfw_on", lang) else t("nsfw_off", lang)
                bot.answerCallbackQuery(callback.id, t("nsfw_toggled", lang, statusStr), showAlert = true)
                showAdminDashboard(chatId, lang, messageId)
            }

            data == "menu:sync" -> {
                if (!Config.isAdmin(userId)) {
                    bot.answerCallbackQuery(callback.id, t("admin_only", lang), showAlert = true)
                    return
                }
                bot.answerCallbackQuery(callback.id, t("syncing", lang).take(40))
                val repos = CloudStreamRepoManager.syncAllDefaults()
                val totalPlugins = repos.sumOf { it.pluginsCount }
                bot.sendMessage(chatId, t("sync_done", lang, totalPlugins, repos.size))
                showReposSummary(chatId, lang)
            }

            data == "close" -> {
                bot.editMessageText(chatId, messageId, "✖️")
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("v:") -> {
                // v:{provider}:{url}
                bot.answerCallbackQuery(callback.id, t("resolving_links", lang).take(40))
                val parts = data.removePrefix("v:").split(":", limit = 2)
                if (parts.size < 2) return

                val providerName = parts[0]
                val itemUrl = parts[1]
                val details = ProviderManager.load(providerName, itemUrl) ?: return

                val isSaved = Database.isBookmarked(userId, itemUrl)
                val cardText = t(
                    "details_card",
                    lang,
                    details.name,
                    details.year?.toString() ?: "N/A",
                    details.rating?.toString() ?: "N/A",
                    details.type.name,
                    details.apiName,
                    (details.plot ?: "N/A").take(350)
                )

                val buttons = mutableListOf<List<InlineKeyboardButton>>()

                val episodes = details.episodes ?: emptyList()
                if (details.type == TvType.TvSeries && episodes.isNotEmpty()) {
                    buttons.add(
                        listOf(
                            InlineKeyboardButton(
                                text = "📺 ${t("btn_episodes", lang)} (${episodes.size})",
                                callbackData = "eps:$providerName:$itemUrl".take(64)
                            )
                        )
                    )
                } else {
                    buttons.add(
                        listOf(
                            InlineKeyboardButton(
                                text = t("btn_watch", lang),
                                callbackData = "dl:$providerName:$itemUrl".take(64)
                            )
                        )
                    )
                }

                // Bookmark toggle
                val bmText = if (isSaved) t("btn_unbookmark", lang) else t("btn_bookmark", lang)
                val bmAction = if (isSaved) "unbm" else "bm"
                buttons.add(
                    listOf(
                        InlineKeyboardButton(
                            text = bmText,
                            callbackData = "$bmAction:$providerName:$itemUrl".take(64)
                        )
                    )
                )
                buttons.add(listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")))

                val keyboard = InlineKeyboardMarkup(buttons)

                if (!details.posterUrl.isNullOrBlank() && details.posterUrl!!.startsWith("http")) {
                    val sent = bot.sendPhoto(chatId, details.posterUrl!!, caption = cardText, replyMarkup = keyboard)
                    if (!sent) {
                        bot.sendMessage(chatId, cardText, replyMarkup = keyboard)
                    }
                } else {
                    bot.sendMessage(chatId, cardText, replyMarkup = keyboard)
                }
            }

            data.startsWith("eps:") -> {
                val parts = data.removePrefix("eps:").split(":", limit = 2)
                if (parts.size < 2) return
                val providerName = parts[0]
                val itemUrl = parts[1]
                val details = ProviderManager.load(providerName, itemUrl) ?: return
                val episodes = details.episodes ?: emptyList()

                val buttons = episodes.take(12).chunked(2).map { row ->
                    row.map { ep ->
                        InlineKeyboardButton(
                            text = "E${ep.episode}: ${ep.name?.take(16) ?: ""}",
                            callbackData = "dl:$providerName:${ep.data}".take(64)
                        )
                    }
                }.toMutableList()

                buttons.add(listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")))

                bot.sendMessage(
                    chatId,
                    t("episodes_list", lang, details.name),
                    replyMarkup = InlineKeyboardMarkup(buttons)
                )
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("dl:") -> {
                bot.answerCallbackQuery(callback.id, t("resolving_links", lang).take(40))
                val parts = data.removePrefix("dl:").split(":", limit = 2)
                if (parts.size < 2) return
                val providerName = parts[0]
                val linkData = parts[1]

                val links = ProviderManager.loadLinks(providerName, linkData)
                if (links.isEmpty()) {
                    bot.sendMessage(chatId, t("no_links", lang))
                    return
                }

                val buttons = links.take(8).map { link ->
                    val icon = if (link.isM3u8) "📡" else "📥"
                    listOf(
                        InlineKeyboardButton(
                            text = "$icon ${link.name} [${link.quality}p]",
                            url = link.url
                        )
                    )
                }.toMutableList()

                buttons.add(listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")))

                bot.sendMessage(
                    chatId,
                    t("links_ready", lang, providerName),
                    replyMarkup = InlineKeyboardMarkup(buttons)
                )
            }

            data.startsWith("bm:") -> {
                val parts = data.removePrefix("bm:").split(":", limit = 2)
                if (parts.size >= 2) {
                    val details = ProviderManager.load(parts[0], parts[1])
                    val title = details?.name ?: "Media"
                    val poster = details?.posterUrl ?: ""
                    Database.addBookmark(userId, parts[0], parts[1], title, poster)
                    bot.answerCallbackQuery(callback.id, t("bookmarked", lang), showAlert = true)
                }
            }

            data.startsWith("unbm:") -> {
                val parts = data.removePrefix("unbm:").split(":", limit = 2)
                val url = if (parts.size == 2) parts[1] else parts[0]
                Database.removeBookmark(userId, url)
                bot.answerCallbackQuery(callback.id, t("unbookmarked", lang), showAlert = true)
            }
        }
    }

    private suspend fun showAdminDashboard(chatId: Long, lang: String, messageId: Long? = null) {
        val summary = CloudStreamRepoManager.getSummary()
        val runtime = Runtime.getRuntime()
        val totalMb = runtime.totalMemory() / (1024 * 1024)
        val freeMb = runtime.freeMemory() / (1024 * 1024)
        val usedMb = totalMb - freeMb
        val nsfwStatus = if (Database.isNsfwEnabled()) t("nsfw_on", lang) else t("nsfw_off", lang)

        val stats = t(
            "admin_stats",
            lang,
            Database.getTotalUsers(),
            Database.getTotalBookmarks(),
            summary["totalRepositories"] as? Int ?: 0,
            summary["totalPlugins"] as? Int ?: 0,
            nsfwStatus,
            usedMb,
            totalMb
        )

        val keyboard = InlineKeyboardMarkup(
            listOf(
                listOf(InlineKeyboardButton(text = t("btn_toggle_nsfw", lang), callbackData = "toggle_nsfw")),
                listOf(InlineKeyboardButton(text = t("btn_sync", lang), callbackData = "menu:sync")),
                listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close"))
            )
        )

        if (messageId != null) {
            bot.editMessageText(chatId, messageId, stats, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, stats, replyMarkup = keyboard)
        }
    }

    private suspend fun showLanguageSelector(chatId: Long, lang: String, messageId: Long? = null) {
        val keyboard = InlineKeyboardMarkup(
            listOf(
                listOf(
                    InlineKeyboardButton(text = "🇬🇧 English", callbackData = "setlang:en"),
                    InlineKeyboardButton(text = "🇮🇷 فارسی", callbackData = "setlang:fa")
                ),
                listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close"))
            )
        )
        val text = t("choose_lang", lang)
        if (messageId != null) {
            bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, text, replyMarkup = keyboard)
        }
    }

    private suspend fun showBookmarks(chatId: Long, userId: Long, lang: String, messageId: Long? = null) {
        val bookmarks = Database.getBookmarks(userId)
        if (bookmarks.isEmpty()) {
            val text = t("no_bookmarks", lang)
            if (messageId != null) {
                bot.editMessageText(chatId, messageId, text)
            } else {
                bot.sendMessage(chatId, text)
            }
            return
        }

        val buttons = bookmarks.take(8).map { b ->
            listOf(
                InlineKeyboardButton(
                    text = "⭐ ${b.title} [${b.provider}]",
                    callbackData = "v:${b.provider}:${b.mediaUrl}".take(64)
                )
            )
        }.toMutableList()

        buttons.add(listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")))
        val text = t("my_bookmarks", lang)

        if (messageId != null) {
            bot.editMessageText(chatId, messageId, text, replyMarkup = InlineKeyboardMarkup(buttons))
        } else {
            bot.sendMessage(chatId, text, replyMarkup = InlineKeyboardMarkup(buttons))
        }
    }

    private suspend fun showReposSummary(chatId: Long, lang: String, messageId: Long? = null) {
        val summary = CloudStreamRepoManager.getSummary()
        val totalRepos = summary["totalRepositories"] as? Int ?: 0
        val totalPlugins = summary["totalPlugins"] as? Int ?: 0

        val text = t("repos_summary", lang, totalRepos, totalPlugins)
        val keyboard = InlineKeyboardMarkup(
            listOf(
                listOf(InlineKeyboardButton(text = t("btn_sync", lang), callbackData = "menu:sync")),
                listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close"))
            )
        )

        if (messageId != null) {
            bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, text, replyMarkup = keyboard)
        }
    }

    private suspend fun showDonationMessage(chatId: Long, lang: String, messageId: Long? = null) {
        val text = t(
            "donate_msg",
            lang,
            Config.usdtTrc20,
            Config.tonWallet,
            Config.btcWallet,
            Config.ethWallet
        )
        val keyboard = InlineKeyboardMarkup(
            listOf(listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")))
        )
        if (messageId != null) {
            bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, text, replyMarkup = keyboard)
        }
    }
}
