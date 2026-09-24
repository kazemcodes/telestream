package com.telestream.bot.handlers

import com.telestream.bot.model.*
import com.telestream.bot.state.BotStateManager
import com.telestream.bot.ui.BotKeyboards
import com.telestream.config.Config
import com.telestream.database.Database
import com.telestream.i18n.I18n.t
import com.telestream.providers.ProviderManager
import com.telestream.telegram.*
import org.slf4j.LoggerFactory

class CallbackRouter(
    private val bot: TelegramClient,
    private val stateManager: BotStateManager,
    private val mediaHandler: MediaHandler,
    private val searchHandler: SearchHandler,
    private val carouselHandler: CarouselHandler,
    private val feedHandler: FeedHandler,
    private val sourcesHandler: SourcesHandler,
    private val adminHandler: AdminHandler
) {
    private val logger = LoggerFactory.getLogger(CallbackRouter::class.java)

    suspend fun handleCallback(callback: CallbackQuery) {
        val chatId = callback.message?.chat?.id ?: return
        val messageId = callback.message.messageId
        val data = callback.data ?: return
        val userId = callback.from.id
        Database.ensureUser(userId)
        val lang = Database.getUserLanguage(userId)

        if (stateManager.isDebounced(userId) &&
            !data.startsWith("setlang:") &&
            !data.startsWith("setsource:") &&
            !data.startsWith("src_tog:") &&
            !data.startsWith("src_set:") &&
            !data.startsWith("src_lang:") &&
            !data.startsWith("src_page:") &&
            data != "close" &&
            data != "noop"
        ) {
            bot.answerCallbackQuery(callback.id)
            return
        }

        if (stateManager.isRequestInProgress(userId)) {
            bot.answerCallbackQuery(callback.id, t("request_in_progress", lang))
            return
        }

        when {
            data == "noop" -> {
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("setlang:") -> {
                val newLang = data.removePrefix("setlang:")
                Database.setUserLanguage(userId, newLang)
                bot.answerCallbackQuery(callback.id, t("lang_changed", newLang), showAlert = true)
                val activeSource = Database.getUserSource(userId)
                val isAdmin = Config.isAdmin(userId)
                bot.editMessageText(
                    chatId,
                    messageId,
                    t("welcome", newLang),
                    replyMarkup = BotKeyboards.getMainMenuKeyboard(newLang, activeSource, isAdmin, userId)
                )
            }

            data == "menu:lang" -> {
                adminHandler.showLanguageSelector(chatId, lang, messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:sources" -> {
                sourcesHandler.showSourcesMenu(chatId, userId, lang, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:check_sources" -> {
                bot.answerCallbackQuery(callback.id)
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

            data == "feed:popular" || data == "feed:latest" -> {
                feedHandler.handleFeedSelect(callback, data, chatId, messageId, userId, lang)
            }

            data.startsWith("feed_picksrc:") -> {
                feedHandler.handleFeedPickSource(callback, data, chatId, messageId, userId, lang)
            }

            data.startsWith("feed_src:") -> {
                feedHandler.handleFeedSourceSelected(callback, data, chatId, messageId, userId, lang)
            }

            data.startsWith("feed_retry:") -> {
                feedHandler.handleFeedRetry(callback, data, chatId, messageId, userId, lang)
            }

            data.startsWith("src_retry:") -> {
                searchHandler.handleSearchRetry(callback, data, chatId, messageId, userId, lang)
            }

            data == "menu:manage_sources" || data == "menu:enabled_sources" -> {
                sourcesHandler.showSourcesManager(chatId, userId, lang, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "src_search_prompt" -> {
                stateManager.userSourceFilterPending[userId] = true
                val prompt = t("source_search_prompt", lang)
                val cancelBtn = InlineKeyboardMarkup(
                    listOf(
                        listOf(
                            InlineKeyboardButton(text = t("btn_back", lang), callbackData = "src_search_cancel")
                        )
                    )
                )
                bot.sendMessage(chatId, prompt, replyMarkup = cancelBtn)
                bot.answerCallbackQuery(callback.id)
            }

            data == "src_search_cancel" -> {
                stateManager.userSourceFilterPending.remove(userId)
                sourcesHandler.showSourcesManager(chatId, userId, lang, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "src_clear" -> {
                stateManager.userSourceFilterPending.remove(userId)
                sourcesHandler.showSourcesManager(chatId, userId, lang, filterLang = "all", page = 0, query = null, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("src_quick:") -> {
                sourcesHandler.handleSourceQuick(callback, data, chatId, userId, lang)
            }

            data.startsWith("src_lang:") -> {
                val token = data.removePrefix("src_lang:")
                sourcesHandler.handleSourceLang(callback, token, chatId, messageId, userId, lang)
            }

            data.startsWith("src_page:") -> {
                val token = data.removePrefix("src_page:")
                sourcesHandler.handleSourcePage(callback, token, chatId, messageId, userId, lang)
            }

            data.startsWith("src_set:") -> {
                val token = data.removePrefix("src_set:")
                sourcesHandler.handleSourceSet(callback, token, chatId, messageId, userId, lang)
            }

            data.startsWith("src_tog:") -> {
                val token = data.removePrefix("src_tog:")
                sourcesHandler.handleSourceToggle(callback, token, chatId, messageId, userId, lang)
            }

            data == "menu:admin" -> {
                if (Config.isAdmin(userId)) {
                    adminHandler.showAdminDashboard(chatId, lang, messageId)
                    bot.answerCallbackQuery(callback.id)
                } else {
                    bot.answerCallbackQuery(callback.id, t("admin_only", lang), showAlert = true)
                }
            }

            data.startsWith("admin:repos") -> {
                if (Config.isAdmin(userId)) {
                    val page = data.removePrefix("admin:repos").removePrefix(":").toIntOrNull() ?: 0
                    adminHandler.showAdminReposManager(chatId, lang, page, messageId)
                    bot.answerCallbackQuery(callback.id)
                } else {
                    bot.answerCallbackQuery(callback.id, t("admin_only", lang), showAlert = true)
                }
            }

            data.startsWith("admin:repo_toggle:") -> {
                adminHandler.handleRepoToggle(callback, data, chatId, messageId, userId, lang)
            }

            data.startsWith("admin:repo_all:") -> {
                adminHandler.handleRepoAll(callback, data, chatId, messageId, userId, lang)
            }

            data == "menu:search" -> {
                val active = Database.getUserSource(userId)
                bot.sendMessage(chatId, t("search_prompt_direct", lang, active))
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("src_pick:") -> {
                searchHandler.handleSourcePick(callback, data, chatId, messageId, userId, lang)
            }

            data.startsWith("src_exec:") -> {
                searchHandler.handleSearchExec(callback, data, chatId, messageId, userId, lang)
            }

            data.startsWith("src_another:") -> {
                searchHandler.handleSearchAnother(callback, data, chatId, messageId, userId, lang)
            }

            data == "menu:bookmarks" -> {
                mediaHandler.showBookmarks(chatId, userId, lang, messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:repos" -> {
                if (Config.isAdmin(userId)) {
                    adminHandler.showAdminReposManager(chatId, lang, 0, messageId)
                } else {
                    bot.answerCallbackQuery(callback.id, t("admin_only", lang), showAlert = true)
                }
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:donate" -> {
                adminHandler.showDonationMessage(chatId, lang, messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:help" -> {
                adminHandler.showHelpMessage(chatId, lang, messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "toggle_nsfw" -> {
                adminHandler.handleNsfwToggle(callback, chatId, messageId, userId, lang)
            }

            data == "menu:sync" -> {
                adminHandler.handleSync(callback, chatId, userId, lang)
            }

            data == "close" || data == "menu:start" -> {
                stateManager.userSearchPending.remove(userId)
                val activeSource = Database.getUserSource(userId)
                val edited = bot.editMessageText(
                    chatId,
                    messageId,
                    t("welcome", lang),
                    replyMarkup = BotKeyboards.getMainMenuKeyboard(lang, activeSource, Config.isAdmin(userId), userId)
                )
                if (!edited) {
                    bot.sendMessage(chatId, t("welcome", lang), replyMarkup = BotKeyboards.getMainMenuKeyboard(lang, activeSource, Config.isAdmin(userId), userId))
                }
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("car_nav:") -> {
                carouselHandler.handleCarouselNav(callback, data, chatId, messageId, lang)
            }

            data.startsWith("car_pager:") -> {
                carouselHandler.handlePager(callback, data, chatId, messageId, lang, userId)
            }

            data.startsWith("car_pnav:") -> {
                carouselHandler.handlePagerNav(callback, data, chatId, messageId, lang, userId)
            }

            data.startsWith("car_prand:") -> {
                carouselHandler.handlePagerRandom(callback, data, chatId, messageId, lang, userId)
            }

            data.startsWith("car_list:") -> {
                carouselHandler.handleCarouselList(callback, data, chatId, messageId, lang)
            }

            data.startsWith("car_album:") -> {
                carouselHandler.handleCarouselAlbum(callback, data, chatId, lang)
            }

            data.startsWith("v:") -> {
                mediaHandler.handleView(callback, data, chatId, messageId, userId, lang)
            }

            data.startsWith("eps:") -> {
                mediaHandler.handleEpisodes(callback, data, chatId, messageId, userId, lang)
            }

            data.startsWith("q:") -> {
                mediaHandler.handleQualityAndLinks(callback, data, chatId, messageId, userId, lang)
            }

            data.startsWith("bm:") || data.startsWith("unbm:") -> {
                mediaHandler.handleBookmarkToggle(callback, data, userId, lang)
            }

            data == "menu:random" -> {
                feedHandler.showRandomPick(chatId, userId, lang, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:categories" -> {
                feedHandler.showCategories(chatId, userId, lang, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:history" -> {
                mediaHandler.showWatchHistory(chatId, userId, lang, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "history:clear" -> {
                mediaHandler.handleHistoryClear(callback, chatId, messageId, userId, lang)
            }

            data.startsWith("resume:") -> {
                mediaHandler.handleResume(callback, data, chatId, userId, lang)
            }

            data.startsWith("cat_pick:") -> {
                feedHandler.handleCategoryPick(callback, data.removePrefix("cat_pick:"), chatId, messageId, userId, lang)
            }

            else -> {
                logger.debug("Unhandled callback data: {}", data)
                bot.answerCallbackQuery(callback.id)
            }
        }
    }
}
