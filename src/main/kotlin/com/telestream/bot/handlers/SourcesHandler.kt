package com.telestream.bot.handlers

import com.telestream.bot.model.CallbackTokenCache
import com.telestream.bot.model.SourceActionRef
import com.telestream.bot.model.SourceBrowserRef
import com.telestream.bot.ui.BotKeyboards
import com.telestream.config.Config
import com.telestream.database.Database
import com.telestream.i18n.I18n.t
import com.telestream.repo.AggregatedSource
import com.telestream.repo.CloudStreamRepoManager
import com.telestream.telegram.CallbackQuery
import com.telestream.telegram.InlineKeyboardButton
import com.telestream.telegram.InlineKeyboardMarkup
import com.telestream.telegram.TelegramClient
import kotlin.math.max

class SourcesHandler(private val bot: TelegramClient) {

    suspend fun showSourcesManager(
        chatId: Long,
        userId: Long,
        lang: String,
        filterLang: String = "all",
        page: Int = 0,
        query: String? = null,
        messageId: Long? = null
    ) {
        val activeSource = Database.getUserSource(userId)
        val allSources = CloudStreamRepoManager.getAggregatedSources(filterLang)
        val sources = if (!query.isNullOrBlank()) {
            val q = query.trim()
            allSources.filter { src ->
                src.name.contains(q, ignoreCase = true) ||
                src.language.contains(q, ignoreCase = true) ||
                src.description?.contains(q, ignoreCase = true) == true
            }.sortedWith(
                compareByDescending<AggregatedSource> { it.name.equals(q, ignoreCase = true) }
                    .thenByDescending { it.name.startsWith(q, ignoreCase = true) }
                    .thenByDescending { it.name.contains(q, ignoreCase = true) }
            )
        } else {
            allSources
        }

        val pageSize = 6
        val totalPages = max(1, (sources.size + pageSize - 1) / pageSize)
        val safePage = page.coerceIn(0, totalPages - 1)
        val pageSources = sources.drop(safePage * pageSize).take(pageSize)

        val rows = mutableListOf<List<InlineKeyboardButton>>()

        // Row 1: Fast Search Controls: [ ⚡ Live Search Sources ] [ 🔍 Search by Name ]
        rows.add(
            listOf(
                InlineKeyboardButton(
                    text = t("btn_live_search", lang),
                    switchInlineQueryCurrentChat = "@sources "
                ),
                InlineKeyboardButton(
                    text = t("btn_search_source", lang),
                    callbackData = "src_search_prompt"
                )
            )
        )

        // Row 2: Language Filter Tabs: [ 🌐 All ] [ 🇬🇧 EN ] [ 🇮🇷 FA ] [ 🇸🇦 AR ]
        val topLangs = listOf("all", "en", "fa", "ar")
        val tabButtons = topLangs.map { l ->
            val isSelected = l.equals(filterLang, ignoreCase = true)
            val flag = when (l) {
                "all" -> "🌐"
                "en" -> "🇬🇧"
                "fa" -> "🇮🇷"
                "ar" -> "🇸🇦"
                else -> "🏳️"
            }
            val title = when (l) {
                "all" -> if (lang == "fa") "همه" else "All"
                "en" -> "EN"
                "fa" -> "FA"
                "ar" -> "AR"
                else -> l.uppercase()
            }
            val label = if (isSelected) "• $flag $title •" else "$flag $title"
            val token = CallbackTokenCache.put(SourceBrowserRef(l, 0, query))
            InlineKeyboardButton(text = label, callbackData = "src_lang:$token")
        }
        rows.add(tabButtons)

        // Row 3 (optional): Active search query clear button, or Quick Picks row if no query & page 0
        if (!query.isNullOrBlank()) {
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = "${t("btn_clear_filter", lang)} (\"$query\")",
                        callbackData = "src_clear"
                    )
                )
            )
        } else if (safePage == 0) {
            val quickPicks = listOf("KissKH", "StreamPlay", "SuperStream", "XDMovies", "HiAnime")
            val pickButtons = quickPicks.map { pickName ->
                val isActive = pickName.equals(activeSource, ignoreCase = true)
                val token = CallbackTokenCache.put(SourceActionRef(filterLang, safePage, pickName, query))
                val label = if (isActive) "🔘 $pickName" else pickName
                InlineKeyboardButton(text = label, callbackData = if (isActive) "noop" else "src_set:$token")
            }
            rows.add(pickButtons)
        }

        // Sources rows: Left button = Set Active, Right button = 1-click Toggle
        for (src in pageSources) {
            val isActive = src.name.equals(activeSource, ignoreCase = true) ||
                    src.name.startsWith(activeSource, ignoreCase = true) ||
                    activeSource.startsWith(src.name, ignoreCase = true)
            val isEnabled = Database.isSourceEnabled(userId, src.name)

            val actionToken = CallbackTokenCache.put(SourceActionRef(filterLang, safePage, src.name, query))
            val selectLabel = if (isActive) "🔘 ${src.name}" else "📡 ${src.name}"
            val selectCb = if (isActive) "noop" else "src_set:$actionToken"

            val toggleLabel = if (isEnabled) "✅" else "❌"
            val toggleCb = "src_tog:$actionToken"

            rows.add(
                listOf(
                    InlineKeyboardButton(text = selectLabel, callbackData = selectCb),
                    InlineKeyboardButton(text = toggleLabel, callbackData = toggleCb)
                )
            )
        }

        // Pagination row if totalPages > 1
        if (totalPages > 1) {
            val navRow = mutableListOf<InlineKeyboardButton>()
            if (safePage > 0) {
                val prevToken = CallbackTokenCache.put(SourceBrowserRef(filterLang, safePage - 1, query))
                navRow.add(InlineKeyboardButton(text = t("btn_prev", lang), callbackData = "src_page:$prevToken"))
            }
            navRow.add(InlineKeyboardButton(text = "📄 ${safePage + 1}/$totalPages", callbackData = "noop"))
            if (safePage < totalPages - 1) {
                val nextToken = CallbackTokenCache.put(SourceBrowserRef(filterLang, safePage + 1, query))
                navRow.add(InlineKeyboardButton(text = t("btn_next", lang), callbackData = "src_page:$nextToken"))
            }
            rows.add(navRow)
        }

        // Navigation bottom row
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_check_sources", lang), callbackData = "menu:check_sources"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val header = if (!query.isNullOrBlank()) {
            t("sources_search_results", lang, query, sources.size, activeSource)
        } else {
            t("sources_manager_title", lang, activeSource)
        }
        val keyboard = InlineKeyboardMarkup(rows)

        if (messageId != null) {
            val ed = bot.editMessageText(chatId, messageId, header, replyMarkup = keyboard)
            if (!ed) bot.sendMessage(chatId, header, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, header, replyMarkup = keyboard)
        }
    }

    suspend fun showSourcesMenu(chatId: Long, userId: Long, lang: String, messageId: Long? = null) {
        showSourcesManager(chatId, userId, lang, messageId = messageId)
    }

    suspend fun showEnabledSourcesScreen(chatId: Long, userId: Long, lang: String, messageId: Long? = null) {
        showSourcesManager(chatId, userId, lang, messageId = messageId)
    }

    suspend fun showManageSourcesStep1(chatId: Long, userId: Long, lang: String, messageId: Long? = null) {
        showSourcesManager(chatId, userId, lang, messageId = messageId)
    }

    suspend fun handleSourceQuick(callback: CallbackQuery, data: String, chatId: Long, userId: Long, lang: String) {
        val raw = data.removePrefix("src_quick:")
        val srcName = CallbackTokenCache.get<String>(raw) ?: raw
        Database.setUserSource(userId, srcName)
        val langTag = CloudStreamRepoManager.getAllAggregatedSources().find { it.name.equals(srcName, ignoreCase = true) }?.language?.uppercase() ?: "ALL"
        bot.answerCallbackQuery(callback.id, t("source_selected", lang, srcName))
        bot.sendMessage(
            chatId,
            t("source_switched_ready", lang, srcName, langTag),
            replyMarkup = BotKeyboards.getMainMenuKeyboard(lang, srcName, Config.isAdmin(userId), userId)
        )
    }

    suspend fun handleSourceLang(callback: CallbackQuery, token: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val ref = CallbackTokenCache.get<SourceBrowserRef>(token)
        if (ref != null) {
            showSourcesManager(chatId, userId, lang, filterLang = ref.lang, page = 0, query = ref.query, messageId = messageId)
        }
        bot.answerCallbackQuery(callback.id)
    }

    suspend fun handleSourcePage(callback: CallbackQuery, token: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val ref = CallbackTokenCache.get<SourceBrowserRef>(token)
        if (ref != null) {
            showSourcesManager(chatId, userId, lang, filterLang = ref.lang, page = ref.page, query = ref.query, messageId = messageId)
        }
        bot.answerCallbackQuery(callback.id)
    }

    suspend fun handleSourceSet(callback: CallbackQuery, token: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val ref = CallbackTokenCache.get<SourceActionRef>(token)
        if (ref != null) {
            Database.setUserSource(userId, ref.sourceName)
            bot.answerCallbackQuery(callback.id, t("source_selected", lang, ref.sourceName))
            showSourcesManager(chatId, userId, lang, filterLang = ref.lang, page = ref.page, query = ref.query, messageId = messageId)
        } else {
            bot.answerCallbackQuery(callback.id, "Session expired")
        }
    }

    suspend fun handleSourceToggle(callback: CallbackQuery, token: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val ref = CallbackTokenCache.get<SourceActionRef>(token)
        if (ref != null) {
            val newStatus = Database.toggleSourceEnabled(userId, ref.sourceName)
            val toast = if (newStatus) t("source_toggled_on", lang, ref.sourceName) else t("source_toggled_off", lang, ref.sourceName)
            bot.answerCallbackQuery(callback.id, toast)
            showSourcesManager(chatId, userId, lang, filterLang = ref.lang, page = ref.page, query = ref.query, messageId = messageId)
        } else {
            bot.answerCallbackQuery(callback.id, "Session expired")
        }
    }
}
