package com.telestream.bot.handlers

import com.telestream.bot.model.CallbackTokenCache
import com.telestream.bot.model.MediaCarouselRef
import com.telestream.bot.model.MediaItemSummary
import com.telestream.bot.model.SearchExecRef
import com.telestream.bot.state.BotStateManager
import com.telestream.bot.ui.BotKeyboards
import com.telestream.bot.util.UrlSanitizer
import com.telestream.database.Database
import com.telestream.i18n.I18n.t
import com.telestream.providers.ProviderManager
import com.telestream.providers.year
import com.telestream.telegram.CallbackQuery
import com.telestream.telegram.InlineKeyboardButton
import com.telestream.telegram.InlineKeyboardMarkup
import com.telestream.telegram.TelegramClient

class SearchHandler(
    private val bot: TelegramClient,
    private val stateManager: BotStateManager
) {

    suspend fun executeSearch(
        chatId: Long,
        userId: Long,
        lang: String,
        sourceName: String,
        query: String
    ) {
        if (!stateManager.tryAcquireRequestLock(userId, "search:$query")) {
            bot.sendMessage(chatId, t("request_in_progress", lang))
            return
        }
        bot.sendChatAction(chatId, "typing")
        val tryingTag = if (lang == "fa") "⏳ [در حال تلاش...]" else "⏳ [Trying...]"
        bot.sendMessage(chatId, "$tryingTag ${t("searching_in_source", lang, sourceName, query)}")
        val results = try {
            ProviderManager.searchInProvider(sourceName, query)
        } finally {
            stateManager.releaseRequestLock(userId)
        }
        val queryToken = CallbackTokenCache.put(query)

        if (results.isEmpty()) {
            val err = ProviderManager.getLastError(sourceName)
            val msgText = if (err != null) {
                t("search_error_source", lang, sourceName, err.message)
            } else {
                t("no_results_in_source", lang, sourceName, query)
            }
            val retryToken = CallbackTokenCache.put(Pair(sourceName, query))
            val btnList = mutableListOf<List<InlineKeyboardButton>>()
            val srcUrl = UrlSanitizer.resolveWebUrl(null, sourceName, query)
            if (!srcUrl.isNullOrBlank()) {
                btnList.add(
                    listOf(
                        InlineKeyboardButton(
                            text = t("btn_open_website", lang),
                            url = srcUrl
                        )
                    )
                )
            }
            btnList.add(
                listOf(
                    InlineKeyboardButton(
                        text = t("btn_search_another_source", lang),
                        callbackData = "src_another:$queryToken"
                    )
                )
            )
            btnList.add(
                listOf(
                    InlineKeyboardButton(
                        text = t("btn_retry", lang),
                        callbackData = "src_retry:$retryToken"
                    ),
                    InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
                )
            )
            bot.sendMessage(chatId, msgText, replyMarkup = InlineKeyboardMarkup(btnList))
            return
        }

        val summaries = results.take(30).map { item ->
            MediaItemSummary(
                name = item.name,
                url = UrlSanitizer.sanitizeTelegramUrl(item.url) ?: item.url,
                apiName = item.apiName,
                posterUrl = item.posterUrl,
                type = item.type,
                year = item.year
            )
        }

        val carouselRef = MediaCarouselRef(
            contextType = "search",
            sourceName = sourceName,
            query = query,
            items = summaries,
            currentIndex = 0
        )
        val carToken = CallbackTokenCache.put(carouselRef)

        val srcUrl = UrlSanitizer.resolveWebUrl(null, sourceName, query)
        val extraRows = mutableListOf<List<InlineKeyboardButton>>()
        if (!srcUrl.isNullOrBlank()) {
            extraRows.add(
                listOf(
                    InlineKeyboardButton(
                        text = t("btn_open_website", lang),
                        url = srcUrl
                    )
                )
            )
        }
        extraRows.add(
            listOf(
                InlineKeyboardButton(
                    text = t("btn_search_another_source", lang),
                    callbackData = "src_another:$queryToken"
                )
            )
        )
        extraRows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val keyboard = BotKeyboards.buildCarouselKeyboard(
            carouselToken = carToken,
            items = summaries,
            currentIndex = 0,
            contextType = "search",
            lang = lang,
            extraRows = extraRows
        )

        val currentItem = summaries[0]
        val caption = buildString {
            append(t("search_results", lang, query))
            append("\n(📡 $sourceName • ${summaries.size} items)\n\n")
            append(t("poster_viewing_item", lang, 1, currentItem.name))
            currentItem.year?.let { append(" ($it)") }
        }

        val firstPoster = currentItem.posterUrl?.takeIf { it.startsWith("http") }
            ?: summaries.firstOrNull { it.posterUrl?.startsWith("http") == true }?.posterUrl

        val msgId = if (firstPoster != null) {
            bot.sendPhoto(chatId, firstPoster, caption = caption, replyMarkup = keyboard)
        } else null

        if (msgId == null) {
            bot.sendMessage(chatId, caption, replyMarkup = keyboard)
        }
    }

    suspend fun promptPickSourceToSearch(
        chatId: Long,
        userId: Long,
        lang: String,
        messageId: Long? = null
    ) {
        val enabledSources = Database.getEnabledSources(userId)
        val activeSource = Database.getUserSource(userId)

        if (enabledSources.isEmpty()) {
            val msgText = t("no_enabled_sources", lang)
            val kb = InlineKeyboardMarkup(
                listOf(
                    listOf(InlineKeyboardButton(text = t("btn_manage_sources", lang), callbackData = "menu:manage_sources")),
                    listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close"))
                )
            )
            if (messageId != null) bot.editMessageText(chatId, messageId, msgText, replyMarkup = kb)
            else bot.sendMessage(chatId, msgText, replyMarkup = kb)
            return
        }

        val rows = mutableListOf<List<InlineKeyboardButton>>()

        for (src in enabledSources) {
            val isActive = src.equals(activeSource, ignoreCase = true) ||
                    src.startsWith(activeSource, ignoreCase = true) ||
                    activeSource.startsWith(src, ignoreCase = true)
            val badge = if (isActive) "🔘 " else "📡 "
            val token = CallbackTokenCache.put(src)
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = "$badge$src",
                        callbackData = "src_pick:$token"
                    )
                )
            )
        }

        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_manage_sources", lang), callbackData = "menu:manage_sources"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val text = t("search_prompt_pick_source", lang)
        val keyboard = InlineKeyboardMarkup(rows)

        if (messageId != null) {
            val ed = bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
            if (!ed) bot.sendMessage(chatId, text, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, text, replyMarkup = keyboard)
        }
    }

    suspend fun promptChooseSourceForQuery(
        chatId: Long,
        userId: Long,
        lang: String,
        query: String,
        messageId: Long? = null
    ) {
        val enabledSources = Database.getEnabledSources(userId)
        val activeSource = Database.getUserSource(userId)

        if (enabledSources.isEmpty()) {
            val msgText = t("no_enabled_sources", lang)
            val kb = InlineKeyboardMarkup(
                listOf(
                    listOf(InlineKeyboardButton(text = t("btn_manage_sources", lang), callbackData = "menu:manage_sources")),
                    listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close"))
                )
            )
            if (messageId != null) bot.editMessageText(chatId, messageId, msgText, replyMarkup = kb)
            else bot.sendMessage(chatId, msgText, replyMarkup = kb)
            return
        }

        val rows = mutableListOf<List<InlineKeyboardButton>>()

        for (src in enabledSources) {
            val isActive = src.equals(activeSource, ignoreCase = true) ||
                    src.startsWith(activeSource, ignoreCase = true) ||
                    activeSource.startsWith(src, ignoreCase = true)
            val badge = if (isActive) "🔘 " else "📡 "
            val execToken = CallbackTokenCache.put(SearchExecRef(src, query))
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = "$badge$src",
                        callbackData = "src_exec:$execToken"
                    )
                )
            )
        }

        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_manage_sources", lang), callbackData = "menu:manage_sources"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val text = t("choose_source_to_search", lang, query)
        val keyboard = InlineKeyboardMarkup(rows)

        if (messageId != null) {
            val ed = bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
            if (!ed) bot.sendMessage(chatId, text, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, text, replyMarkup = keyboard)
        }
    }

    suspend fun handleSearchRetry(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val token = data.removePrefix("src_retry:")
        val pair = CallbackTokenCache.get<Pair<String, String>>(token)
        if (pair != null) {
            val (sourceName, query) = pair
            bot.answerCallbackQuery(callback.id, t("searching_tag", lang))
            BotKeyboards.showButtonLoadingTag(bot, chatId, messageId, callback.message?.replyMarkup, data, lang)
            executeSearch(chatId, userId, lang, sourceName, query)
        } else {
            bot.answerCallbackQuery(callback.id)
        }
    }

    suspend fun handleSourcePick(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val token = data.removePrefix("src_pick:")
        val sourceName = CallbackTokenCache.get<String>(token) ?: Database.getUserSource(userId)
        Database.setUserSource(userId, sourceName)
        stateManager.userSearchPending[userId] = sourceName
        bot.answerCallbackQuery(callback.id)

        val kb = InlineKeyboardMarkup(
            listOf(
                listOf(InlineKeyboardButton(text = t("btn_change_source", lang), callbackData = "menu:search")),
                listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close"))
            )
        )
        bot.editMessageText(
            chatId,
            messageId,
            t("source_selected_prompt_query", lang, sourceName),
            replyMarkup = kb
        )
    }

    suspend fun handleSearchExec(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val token = data.removePrefix("src_exec:")
        val ref = CallbackTokenCache.get<SearchExecRef>(token)
        if (ref != null) {
            bot.answerCallbackQuery(callback.id, t("searching_tag", lang))
            BotKeyboards.showButtonLoadingTag(bot, chatId, messageId, callback.message?.replyMarkup, data, lang)
            Database.setUserSource(userId, ref.sourceName)
            executeSearch(chatId, userId, lang, ref.sourceName, ref.query)
        } else {
            bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
        }
    }

    suspend fun handleSearchAnother(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val queryTok = data.removePrefix("src_another:")
        val query = CallbackTokenCache.get<String>(queryTok) ?: ""
        bot.answerCallbackQuery(callback.id)
        if (query.isNotBlank()) {
            promptChooseSourceForQuery(chatId, userId, lang, query, messageId)
        } else {
            promptPickSourceToSearch(chatId, userId, lang, messageId)
        }
    }
}
