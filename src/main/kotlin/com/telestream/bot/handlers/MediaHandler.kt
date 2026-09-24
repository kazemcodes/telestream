package com.telestream.bot.handlers

import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvType
import com.telestream.bot.model.CallbackTokenCache
import com.telestream.bot.model.EpisodeRef
import com.telestream.bot.model.MediaRef
import com.telestream.bot.state.BotStateManager
import com.telestream.bot.ui.BotKeyboards
import com.telestream.bot.util.UrlSanitizer
import com.telestream.config.Config
import com.telestream.database.Database
import com.telestream.database.WatchHistoryItem
import com.telestream.i18n.I18n.t
import com.telestream.providers.ProviderManager
import com.telestream.providers.episodes
import com.telestream.providers.year
import com.telestream.telegram.CallbackQuery
import com.telestream.telegram.InlineKeyboardButton
import com.telestream.telegram.InlineKeyboardMarkup
import com.telestream.telegram.TelegramClient
import com.telestream.telegram.WebAppInfo
import kotlin.math.max

class MediaHandler(
    private val bot: TelegramClient,
    private val stateManager: BotStateManager
) {

    suspend fun handleView(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val token = data.removePrefix("v:")
        val ref = CallbackTokenCache.get<MediaRef>(token)
        if (ref == null) {
            bot.answerCallbackQuery(callback.id, "Session expired. Please search again.", showAlert = true)
            return
        }

        bot.answerCallbackQuery(callback.id)
        BotKeyboards.showButtonLoadingTag(bot, chatId, messageId, callback.message?.replyMarkup, data, lang)
        bot.sendChatAction(chatId, "typing")

        val loadingMsgId = bot.sendMessage(chatId, t("loading_details_msg", lang))

        stateManager.tryAcquireRequestLock(userId, "load:$token")
        val details = try {
            ProviderManager.load(ref.provider, ref.url)
        } finally {
            stateManager.releaseRequestLock(userId)
            if (loadingMsgId != null) {
                bot.deleteMessage(chatId, loadingMsgId)
            }
        }
        if (details == null) {
            val webUrl = UrlSanitizer.resolveWebUrl(ref.url, ref.provider)
            val fallbackButtons = mutableListOf<List<InlineKeyboardButton>>()
            if (!webUrl.isNullOrBlank()) {
                fallbackButtons.add(
                    listOf(
                        InlineKeyboardButton(
                            text = t("btn_open_browser", lang),
                            url = webUrl
                        )
                    )
                )
            }
            fallbackButtons.add(listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")))
            val errorMsg = t("load_failed_open_web", lang, ref.provider)
            bot.sendMessage(chatId, errorMsg, replyMarkup = InlineKeyboardMarkup(fallbackButtons))
            return
        }

        val isSaved = Database.isBookmarked(userId, ref.url)
        val cardText = t(
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
            val mediaData = (details as? MovieLoadResponse)?.dataUrl ?: ref.url
            val epToken = CallbackTokenCache.put(
                EpisodeRef(ref.provider, token, mediaData, details.name, 0)
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

        // Website URL button
        val pageUrl = UrlSanitizer.resolveWebUrl(details.url, ref.provider, details.name)
            ?: UrlSanitizer.resolveWebUrl(ref.url, ref.provider, details.name)
        if (!pageUrl.isNullOrBlank()) {
            buttons.add(
                listOf(
                    InlineKeyboardButton(
                        text = t("btn_open_website", lang),
                        url = pageUrl
                    )
                )
            )
        }

        // Bookmark toggle
        val bmAction = if (isSaved) "unbm" else "bm"
        val bmText = if (isSaved) t("btn_unbookmark", lang) else t("btn_bookmark", lang)
        buttons.add(
            listOf(
                InlineKeyboardButton(
                    text = bmText,
                    callbackData = "$bmAction:$token"
                )
            )
        )
        buttons.add(listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")))

        val keyboard = InlineKeyboardMarkup(buttons)
        if (!details.posterUrl.isNullOrBlank() && details.posterUrl!!.startsWith("http")) {
            val sent = bot.sendPhoto(chatId, details.posterUrl!!, caption = cardText, replyMarkup = keyboard) != null
            if (!sent) {
                bot.sendMessage(chatId, cardText, replyMarkup = keyboard)
            }
        } else {
            bot.sendMessage(chatId, cardText, replyMarkup = keyboard)
        }
    }

    suspend fun handleEpisodes(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val parts = data.removePrefix("eps:").split(":")
        val token = parts.getOrNull(0) ?: return
        val page = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val ref = CallbackTokenCache.get<MediaRef>(token)
        if (ref == null) {
            bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
            return
        }

        bot.answerCallbackQuery(callback.id)
        BotKeyboards.showButtonLoadingTag(bot, chatId, messageId, callback.message?.replyMarkup, data, lang)
        bot.sendChatAction(chatId, "typing")

        val loadingMsgId = bot.sendMessage(chatId, t("loading_episodes_msg", lang))

        stateManager.tryAcquireRequestLock(userId, "eps:$token")
        val details = try {
            ProviderManager.load(ref.provider, ref.url)
        } finally {
            stateManager.releaseRequestLock(userId)
            if (loadingMsgId != null) {
                bot.deleteMessage(chatId, loadingMsgId)
            }
        }
        if (details == null) {
            val webUrl = UrlSanitizer.resolveWebUrl(ref.url, ref.provider)
            val fallbackButtons = mutableListOf<List<InlineKeyboardButton>>()
            if (!webUrl.isNullOrBlank()) {
                fallbackButtons.add(
                    listOf(
                        InlineKeyboardButton(
                            text = t("btn_open_browser", lang),
                            url = webUrl
                        )
                    )
                )
            }
            fallbackButtons.add(listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")))
            val errorMsg = t("load_failed_open_web", lang, ref.provider)
            bot.sendMessage(chatId, errorMsg, replyMarkup = InlineKeyboardMarkup(fallbackButtons))
            bot.answerCallbackQuery(callback.id)
            return
        }
        val episodes = details.episodes ?: emptyList()

        if (episodes.isEmpty()) {
            bot.answerCallbackQuery(callback.id, "No episodes found", showAlert = true)
            return
        }

        val pageSize = 10
        val totalPages = max(1, (episodes.size + pageSize - 1) / pageSize)
        val safePage = page.coerceIn(0, totalPages - 1)
        val pageEpisodes = episodes.drop(safePage * pageSize).take(pageSize)

        val buttons = mutableListOf<List<InlineKeyboardButton>>()

        // 2 columns of episodes
        val epButtons = pageEpisodes.map { ep ->
            val epTitle = ep.name?.take(20) ?: "Episode ${ep.episode}"
            val epToken = CallbackTokenCache.put(
                EpisodeRef(ref.provider, token, ep.data, "$epTitle (E${ep.episode})", safePage)
            )
            InlineKeyboardButton(
                text = "E${ep.episode}: $epTitle",
                callbackData = "q:$epToken"
            )
        }.chunked(2)
        buttons.addAll(epButtons)

        // Pagination bar
        val navRow = mutableListOf<InlineKeyboardButton>()
        if (safePage > 0) {
            navRow.add(
                InlineKeyboardButton(
                    text = t("btn_prev", lang),
                    callbackData = "eps:$token:${safePage - 1}"
                )
            )
        }
        navRow.add(
            InlineKeyboardButton(
                text = "📄 ${safePage + 1}/$totalPages",
                callbackData = "noop"
            )
        )
        if (safePage < totalPages - 1) {
            navRow.add(
                InlineKeyboardButton(
                    text = t("btn_next", lang),
                    callbackData = "eps:$token:${safePage + 1}"
                )
            )
        }
        buttons.add(navRow)

        // Back and Close
        buttons.add(
            listOf(
                InlineKeyboardButton(text = t("btn_back", lang), callbackData = "v:$token"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val text = t("episodes_page", lang, details.name, safePage + 1, totalPages)
        val edited = bot.editMessageText(chatId, messageId, text, replyMarkup = InlineKeyboardMarkup(buttons))
        if (!edited) {
            bot.sendMessage(chatId, text, replyMarkup = InlineKeyboardMarkup(buttons))
        }
        bot.answerCallbackQuery(callback.id)
    }

    suspend fun handleQualityAndLinks(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        val epToken = data.removePrefix("q:")
        val epRef = CallbackTokenCache.get<EpisodeRef>(epToken)
        if (epRef == null) {
            bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
            return
        }

        bot.answerCallbackQuery(callback.id)
        BotKeyboards.showButtonLoadingTag(bot, chatId, messageId, callback.message?.replyMarkup, data, lang)
        bot.sendChatAction(chatId, "typing")

        val loadingMsgId = bot.sendMessage(chatId, t("resolving_links_msg", lang))

        stateManager.tryAcquireRequestLock(userId, "links:$epToken")
        val links = try {
            ProviderManager.loadLinks(epRef.provider, epRef.episodeData)
        } finally {
            stateManager.releaseRequestLock(userId)
            if (loadingMsgId != null) {
                bot.deleteMessage(chatId, loadingMsgId)
            }
        }

        if (links.isEmpty()) {
            val seriesRef = CallbackTokenCache.get<MediaRef>(epRef.seriesRefToken)
            val webUrl = UrlSanitizer.resolveWebUrl(epRef.episodeData, epRef.provider, epRef.episodeTitle)
                ?: seriesRef?.let { UrlSanitizer.resolveWebUrl(it.url, it.provider, epRef.episodeTitle) }
            val fallbackButtons = mutableListOf<List<InlineKeyboardButton>>()
            if (!webUrl.isNullOrBlank()) {
                fallbackButtons.add(
                    listOf(
                        InlineKeyboardButton(
                            text = t("btn_open_website", lang),
                            url = webUrl
                        )
                    )
                )
            }
            val backCallback = if (epRef.episodeData == epRef.seriesRefToken) {
                "v:${epRef.seriesRefToken}"
            } else {
                "eps:${epRef.seriesRefToken}:${epRef.page}"
            }
            fallbackButtons.add(
                listOf(
                    InlineKeyboardButton(text = t("btn_back", lang), callbackData = backCallback),
                    InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
                )
            )
            val noLinksMsg = if (!webUrl.isNullOrBlank()) t("no_links_open_web", lang) else t("no_links", lang)
            bot.sendMessage(
                chatId,
                noLinksMsg,
                replyMarkup = InlineKeyboardMarkup(fallbackButtons)
            )
            return
        }

        val sortedLinks = links.sortedByDescending { it.quality }
        val buttons = mutableListOf<List<InlineKeyboardButton>>()

        // Record watch history
        val seriesRef = CallbackTokenCache.get<MediaRef>(epRef.seriesRefToken)
        val mediaUrl = seriesRef?.url ?: epRef.episodeData
        Database.recordWatch(
            userId = userId,
            provider = epRef.provider,
            mediaUrl = mediaUrl,
            title = epRef.episodeTitle,
            posterUrl = null,
            episodeTitle = epRef.episodeTitle,
            episodeData = sortedLinks.firstOrNull()?.url
        )

        // Web Video Player button (Telegram Mini App)
        val webAppUrl = Config.webAppUrl
        if (webAppUrl.isNotBlank() && webAppUrl.startsWith("https://")) {
            val encodedProvider = java.net.URLEncoder.encode(epRef.provider, "UTF-8")
            val encodedData = java.net.URLEncoder.encode(epRef.episodeData, "UTF-8")
            val encodedTitle = java.net.URLEncoder.encode(epRef.episodeTitle, "UTF-8")
            val playerUrl = "$webAppUrl#/watch?provider=$encodedProvider&data=$encodedData&title=$encodedTitle"
            buttons.add(
                listOf(
                    InlineKeyboardButton(
                        text = t("btn_web_player", lang),
                        webApp = WebAppInfo(playerUrl)
                    )
                )
            )
        }

        // Smart Stream Badges
        val directUrlList = mutableListOf<Pair<String, String>>()
        for (link in sortedLinks.take(10)) {
            val icon = if (link.isM3u8) "⚡" else "📥"
            val typeDesc = if (link.isM3u8) "HLS Stream" else "Direct"
            val qualityBadge = when {
                link.quality >= 2160 -> "4K UHD"
                link.quality >= 1080 -> "1080p FHD"
                link.quality >= 720 -> "720p HD"
                link.quality >= 480 -> "480p SD"
                link.quality > 0 -> "${link.quality}p"
                else -> "Auto"
            }
            val label = "$icon [ $qualityBadge • $typeDesc ] ${link.name.take(16)}"
            val safeLink = UrlSanitizer.getSafeButtonUrl(link.url)

            if (safeLink != null) {
                buttons.add(
                    listOf(
                        InlineKeyboardButton(
                            text = label,
                            url = safeLink
                        )
                    )
                )
            } else {
                directUrlList.add(label to link.url)
            }
        }

        val webUrl = UrlSanitizer.resolveWebUrl(epRef.episodeData, epRef.provider, epRef.episodeTitle)
            ?: seriesRef?.let { UrlSanitizer.resolveWebUrl(it.url, it.provider, epRef.episodeTitle) }
        val safeWebUrl = UrlSanitizer.getSafeButtonUrl(webUrl)
        if (!safeWebUrl.isNullOrBlank()) {
            buttons.add(
                listOf(
                    InlineKeyboardButton(
                        text = t("btn_open_website", lang),
                        url = safeWebUrl
                    )
                )
            )
        }

        val backCallback = if (epRef.episodeData == epRef.seriesRefToken) {
            "v:${epRef.seriesRefToken}"
        } else {
            "eps:${epRef.seriesRefToken}:${epRef.page}"
        }

        buttons.add(
            listOf(
                InlineKeyboardButton(text = t("btn_back", lang), callbackData = backCallback),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val cleanTitle = epRef.episodeTitle.replace("*", "").replace("_", "").replace("`", "")
        val baseMsg = t("quality_selection", lang, cleanTitle)
        val finalMsg = if (directUrlList.isNotEmpty() && buttons.none { it.any { b -> b.url != null } }) {
            val linksText = directUrlList.joinToString("\n\n") { (lbl, url) ->
                "$lbl:\n`$url`"
            }
            "$baseMsg\n\n$linksText"
        } else baseMsg

        bot.sendMessage(
            chatId,
            finalMsg,
            replyMarkup = InlineKeyboardMarkup(buttons)
        )
    }

    suspend fun handleBookmarkToggle(callback: CallbackQuery, data: String, userId: Long, lang: String) {
        val isRemove = data.startsWith("unbm:")
        val token = if (isRemove) data.removePrefix("unbm:") else data.removePrefix("bm:")
        val ref = CallbackTokenCache.get<MediaRef>(token)
        if (ref != null) {
            if (isRemove) {
                Database.removeBookmark(userId, ref.url)
                bot.answerCallbackQuery(callback.id, t("unbookmarked", lang), showAlert = true)
            } else {
                val details = ProviderManager.load(ref.provider, ref.url)
                val title = details?.name ?: "Media"
                val poster = details?.posterUrl ?: ""
                Database.addBookmark(userId, ref.provider, ref.url, title, poster)
                bot.answerCallbackQuery(callback.id, t("bookmarked", lang), showAlert = true)
            }
        } else {
            bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
        }
    }

    suspend fun handleResume(callback: CallbackQuery, data: String, chatId: Long, userId: Long, lang: String) {
        val token = data.removePrefix("resume:")
        val historyItem = CallbackTokenCache.get<WatchHistoryItem>(token)
        if (historyItem != null) {
            bot.answerCallbackQuery(callback.id)
            val mediaToken = CallbackTokenCache.put(MediaRef(historyItem.provider, historyItem.mediaUrl))
            val details = ProviderManager.load(historyItem.provider, historyItem.mediaUrl)
            if (details != null) {
                val isSaved = Database.isBookmarked(userId, historyItem.mediaUrl)
                val cardText = t(
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
                                callbackData = "eps:$mediaToken:0"
                            )
                        )
                    )
                } else {
                    val mediaData = (details as? MovieLoadResponse)?.dataUrl ?: historyItem.mediaUrl
                    val epToken = CallbackTokenCache.put(
                        EpisodeRef(historyItem.provider, mediaToken, mediaData, details.name, 0)
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
                val pageUrl = UrlSanitizer.resolveWebUrl(details.url, historyItem.provider, details.name)
                    ?: UrlSanitizer.resolveWebUrl(historyItem.mediaUrl, historyItem.provider, historyItem.title)
                if (!pageUrl.isNullOrBlank()) {
                    buttons.add(
                        listOf(
                            InlineKeyboardButton(
                                text = t("btn_open_website", lang),
                                url = pageUrl
                            )
                        )
                    )
                }
                val bmAction = if (isSaved) "unbm" else "bm"
                val bmText = if (isSaved) t("btn_unbookmark", lang) else t("btn_bookmark", lang)
                buttons.add(
                    listOf(
                        InlineKeyboardButton(
                            text = bmText,
                            callbackData = "$bmAction:$mediaToken"
                        )
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
            } else {
                bot.sendMessage(chatId, "⚠️ Could not load media details from ${historyItem.provider}.")
            }
        } else {
            bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
        }
    }

    suspend fun showBookmarks(chatId: Long, userId: Long, lang: String, messageId: Long? = null) {
        val bookmarks = Database.getBookmarks(userId)
        if (bookmarks.isEmpty()) {
            val text = t("no_bookmarks", lang)
            if (messageId != null) {
                val edited = bot.editMessageText(chatId, messageId, text)
                if (!edited) bot.sendMessage(chatId, text)
            } else {
                bot.sendMessage(chatId, text)
            }
            return
        }

        val buttons = bookmarks.take(8).map { b ->
            val token = CallbackTokenCache.put(MediaRef(b.provider, b.mediaUrl))
            listOf(
                InlineKeyboardButton(
                    text = "⭐ ${b.title} [${b.provider}]",
                    callbackData = "v:$token"
                )
            )
        }.toMutableList()

        buttons.add(listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")))
        val text = t("my_bookmarks", lang)

        if (messageId != null) {
            val edited = bot.editMessageText(chatId, messageId, text, replyMarkup = InlineKeyboardMarkup(buttons))
            if (!edited) bot.sendMessage(chatId, text, replyMarkup = InlineKeyboardMarkup(buttons))
        } else {
            bot.sendMessage(chatId, text, replyMarkup = InlineKeyboardMarkup(buttons))
        }
    }

    suspend fun showWatchHistory(chatId: Long, userId: Long, lang: String, messageId: Long? = null) {
        val history = Database.getWatchHistory(userId)
        val rows = mutableListOf<List<InlineKeyboardButton>>()

        if (history.isEmpty()) {
            val emptyMsg = t("history_empty", lang)
            rows.add(
                listOf(
                    InlineKeyboardButton(text = t("btn_search", lang), callbackData = "menu:search"),
                    InlineKeyboardButton(text = t("btn_popular", lang), callbackData = "feed:popular")
                )
            )
            rows.add(listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")))
            val kb = InlineKeyboardMarkup(rows)
            if (messageId != null) bot.editMessageText(chatId, messageId, emptyMsg, replyMarkup = kb)
            else bot.sendMessage(chatId, emptyMsg, replyMarkup = kb)
            return
        }

        val titleText = t("history_title", lang)
        for (item in history.take(10)) {
            val token = CallbackTokenCache.put(item)
            val epInfo = if (!item.episodeTitle.isNullOrBlank()) " • ${item.episodeTitle}" else ""
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = "▶️ ${item.title.take(24)}$epInfo",
                        callbackData = "resume:$token"
                    )
                )
            )
        }

        rows.add(
            listOf(
                InlineKeyboardButton(text = if (lang == "fa") "🗑️ پاک کردن تاریخچه" else "🗑️ Clear History", callbackData = "history:clear"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val kb = InlineKeyboardMarkup(rows)
        if (messageId != null) {
            val ed = bot.editMessageText(chatId, messageId, titleText, replyMarkup = kb)
            if (!ed) bot.sendMessage(chatId, titleText, replyMarkup = kb)
        } else {
            bot.sendMessage(chatId, titleText, replyMarkup = kb)
        }
    }

    suspend fun handleHistoryClear(callback: CallbackQuery, chatId: Long, messageId: Long, userId: Long, lang: String) {
        Database.clearWatchHistory(userId)
        bot.answerCallbackQuery(callback.id, if (lang == "fa") "تاریخچه پاک شد" else "History cleared", showAlert = true)
        showWatchHistory(chatId, userId, lang, messageId = messageId)
    }
}
