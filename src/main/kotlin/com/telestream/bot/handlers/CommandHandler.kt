package com.telestream.bot.handlers

import com.telestream.bot.state.BotStateManager
import com.telestream.bot.ui.BotKeyboards
import com.telestream.config.Config
import com.telestream.database.Database
import com.telestream.i18n.I18n.t
import com.telestream.providers.ProviderManager
import com.telestream.repo.AggregatedSource
import com.telestream.repo.CloudStreamRepoManager
import com.telestream.telegram.InlineKeyboardButton
import com.telestream.telegram.InlineKeyboardMarkup
import com.telestream.telegram.Message
import com.telestream.telegram.TelegramClient
import com.telestream.telegram.WebAppInfo

class CommandHandler(
    private val bot: TelegramClient,
    private val stateManager: BotStateManager,
    private val searchHandler: SearchHandler,
    private val sourcesHandler: SourcesHandler,
    private val feedHandler: FeedHandler,
    private val mediaHandler: MediaHandler,
    private val adminHandler: AdminHandler
) {

    suspend fun handleMessage(message: Message) {
        val chatId = message.chat.id
        val text = message.text?.trim() ?: return
        val userId = message.from?.id ?: chatId
        Database.ensureUser(userId)
        val lang = Database.getUserLanguage(userId)
        val activeSource = Database.getUserSource(userId)
        val isAdmin = Config.isAdmin(userId)

        val parts = text.split("\\s+".toRegex(), limit = 2)
        val rawCmd = parts.getOrNull(0) ?: ""
        val cmd = rawCmd.lowercase().substringBefore("@")
        val arg = parts.getOrNull(1)?.trim() ?: ""

        when {
            cmd == "/start" -> {
                stateManager.userSearchPending.remove(userId)
                bot.sendMessage(chatId, t("welcome", lang), replyMarkup = BotKeyboards.getMainMenuKeyboard(lang, activeSource, isAdmin, userId))
            }

            cmd == "/ping" -> {
                bot.sendMessage(chatId, "🏓 *Pong!* TeleStream bot is online.")
            }

            cmd == "/check_sources" || cmd == "/ping_sources" -> {
                val checkingMsg = if (lang == "fa") "⏳ در حال بررسی وضعیت اتصال به سورس‌ها..." else "⏳ Checking sources connectivity..."
                bot.sendMessage(chatId, checkingMsg)
                val enabled = Database.getEnabledSources(userId)
                val testList = if (enabled.isNotEmpty()) {
                    enabled.mapNotNull { ProviderManager.getProvider(it) }
                } else {
                    ProviderManager.providers.take(6)
                }
                val sb = StringBuilder(t("sources_health_title", lang))
                for (p in testList) {
                    val (ok, ms) = ProviderManager.pingProvider(p.name)
                    if (ok) {
                        sb.append(t("sources_health_ok", lang, p.name, ms)).append("\n")
                    } else {
                        val err = ProviderManager.getLastError(p.name)?.message ?: "Timeout / DNS Blocked"
                        sb.append(t("sources_health_fail", lang, p.name, err)).append("\n")
                    }
                }
                sb.append("\n💡 ")
                if (lang == "fa") {
                    sb.append("سورس‌های با نشان 🟢 بدون مشکل در دسترس هستند. در صورت قرمز بودن 🔴، ممکن است سرور سورس موقتاً قطع باشد یا توسط اینترنت مسدود شده باشد.")
                } else {
                    sb.append("Sources with 🟢 are accessible. If marked with 🔴, the server is down or blocked by ISP/DNS.")
                }
                bot.sendMessage(chatId, sb.toString())
            }

            cmd == "/popular" -> {
                feedHandler.showFeedScreen(chatId, userId, lang, "popular")
            }

            cmd == "/latest" -> {
                feedHandler.showFeedScreen(chatId, userId, lang, "latest")
            }

            cmd == "/random" -> {
                stateManager.userSourceFilterPending.remove(userId)
                feedHandler.showRandomPick(chatId, userId, lang, activeSource)
            }

            cmd == "/categories" -> {
                stateManager.userSourceFilterPending.remove(userId)
                feedHandler.showCategories(chatId, userId, lang, activeSource)
            }

            cmd == "/history" -> {
                stateManager.userSourceFilterPending.remove(userId)
                mediaHandler.showWatchHistory(chatId, userId, lang)
            }

            cmd == "/sources" || cmd == "/manage_sources" || cmd == "/enabled_sources" -> {
                stateManager.userSourceFilterPending.remove(userId)
                sourcesHandler.showSourcesManager(chatId, userId, lang)
            }

            cmd == "/source" || cmd == "/src" -> {
                stateManager.userSourceFilterPending.remove(userId)
                if (arg.isBlank()) {
                    sourcesHandler.showSourcesManager(chatId, userId, lang)
                } else {
                    val allSources = CloudStreamRepoManager.getAllAggregatedSources()
                    val matches = allSources.filter {
                        it.name.contains(arg, ignoreCase = true) ||
                        it.language.contains(arg, ignoreCase = true) ||
                        it.description?.contains(arg, ignoreCase = true) == true
                    }.sortedWith(
                        compareByDescending<AggregatedSource> { it.name.equals(arg, ignoreCase = true) }
                            .thenByDescending { it.name.startsWith(arg, ignoreCase = true) }
                            .thenByDescending { it.name.contains(arg, ignoreCase = true) }
                    )
                    if (matches.size == 1) {
                        val matched = matches.first()
                        Database.setUserSource(userId, matched.name)
                        bot.sendMessage(
                            chatId,
                            t("source_switched_ready", lang, matched.name, matched.language.uppercase()),
                            replyMarkup = BotKeyboards.getMainMenuKeyboard(lang, matched.name, isAdmin, userId)
                        )
                    } else if (matches.isNotEmpty()) {
                        sourcesHandler.showSourcesManager(chatId, userId, lang, filterLang = "all", page = 0, query = arg)
                    } else {
                        bot.sendMessage(chatId, t("no_sources_found", lang, arg))
                    }
                }
            }

            cmd == "/search" -> {
                stateManager.userSourceFilterPending.remove(userId)
                if (arg.isBlank()) {
                    bot.sendMessage(chatId, t("search_prompt_direct", lang, activeSource))
                } else {
                    searchHandler.executeSearch(chatId, userId, lang, activeSource, arg)
                }
            }

            cmd == "/app" -> {
                val webAppUrl = Config.webAppUrl
                if (webAppUrl.startsWith("https://")) {
                    val keyboard = InlineKeyboardMarkup(
                        listOf(
                            listOf(
                                InlineKeyboardButton(
                                    text = t("btn_webapp", lang),
                                    webApp = WebAppInfo(webAppUrl)
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
                } else {
                    bot.sendMessage(
                        chatId,
                        if (lang == "fa") "⚠️ برای استفاده از مینی‌اپ، یک آدرس HTTPS معتبر در متغیر `WEBAPP_URL` لازم است."
                        else "⚠️ Telegram Mini Apps require a valid HTTPS URL in `WEBAPP_URL` (e.g. Cloudflare Tunnel or domain)."
                    )
                }
            }

            cmd == "/donate" -> {
                adminHandler.showDonationMessage(chatId, lang)
            }

            cmd == "/admin" -> {
                if (!Config.isAdmin(userId)) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                } else {
                    adminHandler.showAdminDashboard(chatId, lang)
                }
            }

            cmd == "/nsfw" -> {
                if (!Config.isAdmin(userId)) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                    return
                }
                val newStatus = when (arg.lowercase()) {
                    "on", "enable", "1", "true" -> true
                    "off", "disable", "0", "false" -> false
                    else -> !Database.isNsfwEnabled()
                }
                Database.setNsfwEnabled(newStatus)
                val statusStr = if (newStatus) t("nsfw_on", lang) else t("nsfw_off", lang)
                bot.sendMessage(chatId, t("nsfw_toggled", lang, statusStr))
            }

            cmd == "/repos" -> {
                if (!isAdmin) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                } else {
                    adminHandler.showAdminReposManager(chatId, lang, 0)
                }
            }

            cmd == "/sync" -> {
                if (!isAdmin) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                    return
                }
                bot.sendMessage(chatId, t("syncing", lang))
                val repos = CloudStreamRepoManager.syncAllDefaults()
                val totalPlugins = repos.sumOf { it.pluginsCount }
                bot.sendMessage(chatId, t("sync_done", lang, totalPlugins, repos.size))
            }

            cmd == "/addrepo" -> {
                if (!isAdmin) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                    return
                }
                if (arg.isBlank()) {
                    bot.sendMessage(chatId, "⚠️ Usage: `/addrepo <url>` (e.g., `https://example.com/repo.json`)")
                } else {
                    bot.sendMessage(chatId, "⏳ Fetching repository from `$arg`...")
                    val repo = CloudStreamRepoManager.fetchRepository(arg)
                    if (repo != null) {
                        bot.sendMessage(chatId, "✅ Added repository *${repo.name}* with *${repo.pluginsCount}* plugins!")
                    } else {
                        bot.sendMessage(chatId, "❌ Failed to fetch repository from `$arg`.")
                    }
                }
            }

            cmd == "/language" -> {
                adminHandler.showLanguageSelector(chatId, lang)
            }

            cmd == "/bookmarks" -> {
                mediaHandler.showBookmarks(chatId, userId, lang)
            }

            cmd == "/help" -> {
                sendHelpMessage(chatId, lang)
            }

            else -> {
                if (stateManager.userSourceFilterPending.remove(userId) == true) {
                    sourcesHandler.showSourcesManager(chatId, userId, lang, filterLang = "all", page = 0, query = text)
                } else {
                    // User sent title text directly: search active source immediately
                    val targetSource = stateManager.userSearchPending.remove(userId) ?: activeSource
                    searchHandler.executeSearch(chatId, userId, lang, targetSource, text)
                }
            }
        }
    }

    suspend fun sendHelpMessage(chatId: Long, lang: String, messageId: Long? = null) {
        val botUsername = try { bot.getMe()?.username ?: "telecloudstreambot" } catch (_: Exception) { "telecloudstreambot" }
        val helpText = t("help_msg", lang, botUsername, botUsername)
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        val webAppUrl = Config.webAppUrl
        if (webAppUrl.startsWith("https://")) {
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
                InlineKeyboardButton(text = t("btn_sources", lang), callbackData = "menu:sources")
            )
        )
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_donate", lang), callbackData = "menu:donate"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )
        val keyboard = InlineKeyboardMarkup(rows)
        if (messageId != null) {
            val edited = bot.editMessageText(chatId, messageId, helpText, replyMarkup = keyboard)
            if (!edited) bot.sendMessage(chatId, helpText, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, helpText, replyMarkup = keyboard)
        }
    }
}
