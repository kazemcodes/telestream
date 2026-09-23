package com.telestream.bot.handlers

import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvType
import com.telestream.bot.model.*
import com.telestream.bot.state.BotStateManager
import com.telestream.bot.ui.BotKeyboards
import com.telestream.bot.util.UrlSanitizer
import com.telestream.database.Database
import com.telestream.i18n.I18n.t
import com.telestream.providers.ProviderManager
import com.telestream.providers.episodes
import com.telestream.providers.year
import com.telestream.telegram.CallbackQuery
import com.telestream.telegram.InlineKeyboardButton
import com.telestream.telegram.InlineKeyboardMarkup
import com.telestream.telegram.TelegramClient

class FeedHandler(
    private val bot: TelegramClient,
    private val stateManager: BotStateManager
) {

    suspend fun showFeedScreen(
        chatId: Long,
        userId: Long,
        lang: String,
        feedType: String,
        sourceName: String? = null,
        page: Int = 1,
        messageId: Long? = null
    ) {
        val activeSource = sourceName ?: Database.getUserSource(userId)
        val isPopular = feedType == "popular"

        val titleKey = if (isPopular) "feed_popular_title" else "feed_latest_title"
        val headerText = t(titleKey, lang, activeSource)

        val items = if (isPopular) {
            ProviderManager.getPopular(activeSource, page)
        } else {
            ProviderManager.getLatest(activeSource, page)
        }

        val rows = mutableListOf<List<InlineKeyboardButton>>()

        if (items.isEmpty()) {
            val err = ProviderManager.getLastError(activeSource)
            val emptyText = if (err != null) {
                t("source_unreachable", lang, activeSource, err.message)
            } else {
                "$headerText\n\n${t("feed_no_items", lang)}"
            }
            val retryToken = CallbackTokenCache.put(activeSource)
            val srcUrl = UrlSanitizer.resolveWebUrl(null, activeSource)
            if (!srcUrl.isNullOrBlank()) {
                rows.add(
                    listOf(
                        InlineKeyboardButton(
                            text = t("btn_open_website", lang),
                            url = srcUrl
                        )
                    )
                )
            }
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = if (isPopular) "🔘 ${t("btn_popular", lang)}" else t("btn_popular", lang),
                        callbackData = "feed:popular"
                    ),
                    InlineKeyboardButton(
                        text = if (!isPopular) "🔘 ${t("btn_latest", lang)}" else t("btn_latest", lang),
                        callbackData = "feed:latest"
                    )
                )
            )
            rows.add(
                listOf(
                    InlineKeyboardButton(text = t("btn_retry", lang), callbackData = "feed_retry:$feedType:$page:$retryToken"),
                    InlineKeyboardButton(text = t("btn_switch_source", lang), callbackData = "feed_picksrc:$feedType")
                )
            )
            rows.add(
                listOf(
                    InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
                )
            )
            val kb = InlineKeyboardMarkup(rows)
            if (messageId != null) {
                val ed = bot.editMessageText(chatId, messageId, emptyText, replyMarkup = kb)
                if (!ed) bot.sendMessage(chatId, emptyText, replyMarkup = kb)
            } else {
                bot.sendMessage(chatId, emptyText, replyMarkup = kb)
            }
            return
        }

        val summaries = items.take(30).map { item ->
            MediaItemSummary(
                name = item.name,
                url = UrlSanitizer.sanitizeTelegramUrl(item.url) ?: item.url,
                apiName = activeSource,
                posterUrl = item.posterUrl,
                type = item.type,
                year = item.year
            )
        }

        val carouselRef = MediaCarouselRef(
            contextType = "feed",
            sourceName = activeSource,
            feedType = feedType,
            page = page,
            items = summaries,
            currentIndex = 0
        )
        val carToken = CallbackTokenCache.put(carouselRef)

        val srcUrl = UrlSanitizer.resolveWebUrl(null, activeSource)
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
                    text = if (isPopular) "🔘 ${t("btn_popular", lang)}" else t("btn_popular", lang),
                    callbackData = "feed:popular"
                ),
                InlineKeyboardButton(
                    text = if (!isPopular) "🔘 ${t("btn_latest", lang)}" else t("btn_latest", lang),
                    callbackData = "feed:latest"
                )
            )
        )
        extraRows.add(
            listOf(
                InlineKeyboardButton(text = "${t("btn_switch_source", lang)} ($activeSource)", callbackData = "feed_picksrc:$feedType"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val keyboard = BotKeyboards.buildCarouselKeyboard(
            carouselToken = carToken,
            items = summaries,
            currentIndex = 0,
            contextType = "feed",
            lang = lang,
            extraRows = extraRows
        )

        val currentItem = summaries[0]
        val caption = buildString {
            append(headerText)
            append("\n\n")
            append(t("poster_viewing_item", lang, 1, currentItem.name))
            currentItem.year?.let { append(" ($it)") }
        }

        val firstPoster = currentItem.posterUrl?.takeIf { it.startsWith("http") }
            ?: summaries.firstOrNull { it.posterUrl?.startsWith("http") == true }?.posterUrl

        if (messageId != null) {
            val edited = if (firstPoster != null) {
                bot.editMessageMedia(chatId, messageId, firstPoster, caption = caption, replyMarkup = keyboard)
            } else {
                bot.editMessageCaption(chatId, messageId, caption = caption, replyMarkup = keyboard)
            }
            if (!edited) {
                val edText = bot.editMessageText(chatId, messageId, caption, replyMarkup = keyboard)
                if (!edText) {
                    if (firstPoster != null) {
                        bot.sendPhoto(chatId, firstPoster, caption = caption, replyMarkup = keyboard)
                    } else {
                        bot.sendMessage(chatId, caption, replyMarkup = keyboard)
                    }
                }
            }
        } else {
            val msgId = if (firstPoster != null) {
                bot.sendPhoto(chatId, firstPoster, caption = caption, replyMarkup = keyboard)
            } else null

            if (msgId == null) {
                bot.sendMessage(chatId, caption, replyMarkup = keyboard)
            }
        }
    }

    suspend fun promptFeedPickSource(
        chatId: Long,
        userId: Long,
        lang: String,
        feedType: String,
        messageId: Long? = null
    ) {
        val enabled = Database.getEnabledSources(userId)
        val activeSource = Database.getUserSource(userId)
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        for (src in enabled) {
            val isActive = src.equals(activeSource, ignoreCase = true) ||
                    src.startsWith(activeSource, ignoreCase = true) ||
                    activeSource.startsWith(src, ignoreCase = true)
            val badge = if (isActive) "🔘 " else "📡 "
            val token = CallbackTokenCache.put(src)
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = "$badge$src",
                        callbackData = "feed_src:$feedType:$token"
                    )
                )
            )
        }
        rows.add(listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")))
        val kb = InlineKeyboardMarkup(rows)
        val text = t("search_prompt_pick_source", lang)
        if (messageId != null) {
            val ed = bot.editMessageText(chatId, messageId, text, replyMarkup = kb)
            if (!ed) bot.sendMessage(chatId, text, replyMarkup = kb)
        } else {
            bot.sendMessage(chatId, text, replyMarkup = kb)
        }
    }

    suspend fun showRandomPick(
        chatId: Long,
        userId: Long,
        lang: String,
        sourceName: String? = null,
        messageId: Long? = null
    ) {
        val activeSource = sourceName ?: Database.getUserSource(userId)
        val randomItem = ProviderManager.getRandomMedia(activeSource)
        if (randomItem == null) {
            val emptyMsg = "${t("random_title", lang)}\n\n${t("random_empty", lang)}"
            val kb = InlineKeyboardMarkup(
                listOf(
                    listOf(
                        InlineKeyboardButton(text = t("btn_popular", lang), callbackData = "feed:popular"),
                        InlineKeyboardButton(text = t("btn_latest", lang), callbackData = "feed:latest")
                    ),
                    listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close"))
                )
            )
            if (messageId != null) bot.editMessageText(chatId, messageId, emptyMsg, replyMarkup = kb)
            else bot.sendMessage(chatId, emptyMsg, replyMarkup = kb)
            return
        }

        val token = CallbackTokenCache.put(MediaRef(activeSource, randomItem.url))
        val details = ProviderManager.load(activeSource, randomItem.url) ?: run {
            bot.sendMessage(chatId, "⚠️ Could not load picked title details.")
            return
        }
        val isSaved = Database.isBookmarked(userId, randomItem.url)
        val cardText = "${t("random_title", lang)}\n\n" + t(
            "details_card",
            lang,
            details.name,
            details.year?.toString() ?: "N/A",
            details.score?.toInt(100)?.let { "${it / 10.0}" } ?: "N/A",
            details.type.name,
            details.apiName,
            (details.plot ?: "N/A").take(350)
        )

        val buttons = mutableListOf<List<InlineKeyboardButton>>()
        val episodes = details.episodes ?: emptyList()
        if ((details.type == TvType.TvSeries || details.type == TvType.Anime) && episodes.isNotEmpty()) {
            buttons.add(
                listOf(
                    InlineKeyboardButton(
                        text = "📺 ${t("btn_episodes", lang)} (${episodes.size})",
                        callbackData = "eps:$token:0"
                    )
                )
            )
        } else {
            val mediaData = (details as? MovieLoadResponse)?.dataUrl ?: randomItem.url
            val epToken = CallbackTokenCache.put(
                EpisodeRef(activeSource, token, mediaData, details.name, 0)
            )
            buttons.add(
                listOf(
                    InlineKeyboardButton(
                        text = t("btn_watch", lang),
                        callbackData = "q:$epToken"
                    )
                )
            )
        }

        buttons.add(
            listOf(
                InlineKeyboardButton(text = t("btn_random", lang), callbackData = "menu:random"),
                InlineKeyboardButton(text = if (isSaved) t("btn_unbookmark", lang) else t("btn_bookmark", lang), callbackData = "${if (isSaved) "unbm" else "bm"}:$token")
            )
        )
        buttons.add(listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")))

        val kb = InlineKeyboardMarkup(buttons)
        if (!details.posterUrl.isNullOrBlank() && details.posterUrl!!.startsWith("http")) {
            val sent = bot.sendPhoto(chatId, details.posterUrl!!, caption = cardText, replyMarkup = kb) != null
            if (!sent) bot.sendMessage(chatId, cardText, replyMarkup = kb)
        } else {
            bot.sendMessage(chatId, cardText, replyMarkup = kb)
        }
    }

    suspend fun showCategories(
        chatId: Long,
        userId: Long,
        lang: String,
        sourceName: String? = null,
        messageId: Long? = null
    ) {
        val activeSource = sourceName ?: Database.getUserSource(userId)
        val sections = ProviderManager.getMainPageSections(activeSource)
        val rows = mutableListOf<List<InlineKeyboardButton>>()

        if (sections.isEmpty()) {
            val emptyMsg = "${t("categories_title", lang, activeSource)}\n\n${t("categories_empty", lang)}"
            rows.add(
                listOf(
                    InlineKeyboardButton(text = t("btn_popular", lang), callbackData = "feed:popular"),
                    InlineKeyboardButton(text = t("btn_latest", lang), callbackData = "feed:latest")
                )
            )
            rows.add(
                listOf(
                    InlineKeyboardButton(text = t("btn_switch_source", lang), callbackData = "menu:sources"),
                    InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
                )
            )
            val kb = InlineKeyboardMarkup(rows)
            if (messageId != null) bot.editMessageText(chatId, messageId, emptyMsg, replyMarkup = kb)
            else bot.sendMessage(chatId, emptyMsg, replyMarkup = kb)
            return
        }

        val headerText = t("categories_title", lang, activeSource)
        val chunked = sections.chunked(2)
        for (pair in chunked) {
            val row = pair.map { sec ->
                val token = CallbackTokenCache.put(CategorySectionRef(activeSource, sec.name, 1))
                InlineKeyboardButton(
                    text = "📂 ${sec.name.take(20)}",
                    callbackData = "cat_pick:$token"
                )
            }
            rows.add(row)
        }

        rows.add(
            listOf(
                InlineKeyboardButton(text = "${t("btn_switch_source", lang)} ($activeSource)", callbackData = "menu:sources"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val kb = InlineKeyboardMarkup(rows)
        if (messageId != null) {
            val ed = bot.editMessageText(chatId, messageId, headerText, replyMarkup = kb)
            if (!ed) bot.sendMessage(chatId, headerText, replyMarkup = kb)
        } else {
            bot.sendMessage(chatId, headerText, replyMarkup = kb)
        }
    }

    suspend fun showCategoryItems(
        chatId: Long,
        userId: Long,
        lang: String,
        provider: String,
        sectionName: String,
        page: Int = 1,
        messageId: Long? = null
    ) {
        val items = ProviderManager.getSectionItems(provider, sectionName, page)
        if (items.isEmpty()) {
            val emptyMsg = "📂 *$sectionName* ($provider)\n\n${t("feed_no_items", lang)}"
            val kb = InlineKeyboardMarkup(
                listOf(
                    listOf(InlineKeyboardButton(text = t("btn_categories", lang), callbackData = "menu:categories")),
                    listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close"))
                )
            )
            if (messageId != null) bot.editMessageText(chatId, messageId, emptyMsg, replyMarkup = kb)
            else bot.sendMessage(chatId, emptyMsg, replyMarkup = kb)
            return
        }

        val summaries = items.take(30).map { item ->
            MediaItemSummary(
                name = item.name,
                url = UrlSanitizer.sanitizeTelegramUrl(item.url) ?: item.url,
                apiName = provider,
                posterUrl = item.posterUrl,
                type = item.type,
                year = item.year
            )
        }

        val carouselRef = MediaCarouselRef(
            contextType = "category",
            sourceName = provider,
            query = sectionName,
            feedType = null,
            page = page,
            items = summaries,
            currentIndex = 0
        )
        val carouselToken = CallbackTokenCache.put(carouselRef)

        val extraRows = listOf(
            listOf(
                InlineKeyboardButton(text = t("btn_categories", lang), callbackData = "menu:categories"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val kb = BotKeyboards.buildCarouselKeyboard(
            carouselToken = carouselToken,
            items = summaries,
            currentIndex = 0,
            contextType = "category",
            lang = lang,
            extraRows = extraRows
        )

        val firstItem = summaries[0]
        val caption = buildString {
            append("📂 *$sectionName*\n")
            append("Source: *$provider*\n\n")
            append(t("poster_viewing_item", lang, 1, firstItem.name))
            firstItem.year?.let { append(" ($it)") }
        }

        val targetPoster = firstItem.posterUrl?.takeIf { it.startsWith("http") }
        if (messageId != null) {
            val edited = if (targetPoster != null) {
                bot.editMessageMedia(chatId, messageId, targetPoster, caption = caption, replyMarkup = kb)
            } else {
                bot.editMessageCaption(chatId, messageId, caption = caption, replyMarkup = kb)
            }
            if (!edited) {
                bot.editMessageText(chatId, messageId, caption, replyMarkup = kb)
            }
        } else {
            if (targetPoster != null) {
                val sent = bot.sendPhoto(chatId, targetPoster, caption = caption, replyMarkup = kb) != null
                if (!sent) bot.sendMessage(chatId, caption, replyMarkup = kb)
            } else {
                bot.sendMessage(chatId, caption, replyMarkup = kb)
            }
        }
    }

    suspend fun handleFeedSelect(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val feedType = if (data == "feed:popular") "popular" else "latest"
        bot.answerCallbackQuery(callback.id)
        BotKeyboards.showButtonLoadingTag(bot, chatId, messageId, callback.message?.replyMarkup, data, lang)
        bot.sendChatAction(chatId, "typing")
        stateManager.tryAcquireRequestLock(userId, data)
        try {
            showFeedScreen(chatId, userId, lang, feedType, messageId = messageId)
        } finally {
            stateManager.releaseRequestLock(userId)
        }
    }

    suspend fun handleFeedPickSource(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val feedType = data.removePrefix("feed_picksrc:")
        promptFeedPickSource(chatId, userId, lang, feedType, messageId)
        bot.answerCallbackQuery(callback.id)
    }

    suspend fun handleFeedSourceSelected(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val parts = data.removePrefix("feed_src:").split(":")
        val feedType = parts.getOrNull(0) ?: "popular"
        val token = parts.getOrNull(1) ?: ""
        val sourceName = CallbackTokenCache.get<String>(token) ?: Database.getUserSource(userId)
        Database.setUserSource(userId, sourceName)
        bot.answerCallbackQuery(callback.id)
        BotKeyboards.showButtonLoadingTag(bot, chatId, messageId, callback.message?.replyMarkup, data, lang)
        bot.sendChatAction(chatId, "typing")
        stateManager.tryAcquireRequestLock(userId, "feed_src")
        try {
            showFeedScreen(chatId, userId, lang, feedType, sourceName = sourceName, messageId = messageId)
        } finally {
            stateManager.releaseRequestLock(userId)
        }
    }

    suspend fun handleFeedRetry(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val parts = data.removePrefix("feed_retry:").split(":")
        val feedType = parts.getOrNull(0) ?: "popular"
        val page = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val token = parts.getOrNull(2) ?: ""
        val sourceName = CallbackTokenCache.get<String>(token) ?: Database.getUserSource(userId)
        bot.answerCallbackQuery(callback.id)
        BotKeyboards.showButtonLoadingTag(bot, chatId, messageId, callback.message?.replyMarkup, data, lang)
        bot.sendChatAction(chatId, "typing")
        stateManager.tryAcquireRequestLock(userId, "feed_retry")
        try {
            showFeedScreen(chatId, userId, lang, feedType, page = page, sourceName = sourceName, messageId = messageId)
        } finally {
            stateManager.releaseRequestLock(userId)
        }
    }

    suspend fun handleCategoryPick(callback: CallbackQuery, token: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val catRef = CallbackTokenCache.get<CategorySectionRef>(token)
        if (catRef != null) {
            bot.answerCallbackQuery(callback.id)
            showCategoryItems(chatId, userId, lang, catRef.provider, catRef.sectionName, catRef.page, messageId = messageId)
        } else {
            bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
        }
    }
}
