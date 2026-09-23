package com.telestream.bot.ui

import com.lagradost.cloudstream3.TvType
import com.telestream.bot.model.*
import com.telestream.bot.util.UrlSanitizer
import com.telestream.config.Config
import com.telestream.database.Database
import com.telestream.i18n.I18n.t
import com.telestream.telegram.InlineKeyboardButton
import com.telestream.telegram.InlineKeyboardMarkup
import com.telestream.telegram.TelegramClient
import com.telestream.telegram.WebAppInfo

object BotKeyboards {

    fun getMainMenuKeyboard(
        lang: String,
        activeSource: String,
        isAdmin: Boolean = false,
        userId: Long = 0L
    ): InlineKeyboardMarkup {
        val rows = mutableListOf<List<InlineKeyboardButton>>()

        // Resume / Continue watching button if history exists
        if (userId != 0L) {
            val lastWatched = Database.getLastWatched(userId)
            if (lastWatched != null) {
                val resumeToken = CallbackTokenCache.put(lastWatched)
                rows.add(
                    listOf(
                        InlineKeyboardButton(
                            text = t("btn_continue_watch", lang, lastWatched.title.take(24)),
                            callbackData = "resume:$resumeToken"
                        )
                    )
                )
            }
        }

        val webAppUrl = Config.webAppUrl
        if (webAppUrl.isNotBlank() && webAppUrl.startsWith("https://")) {
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = t("btn_webapp", lang),
                        webApp = WebAppInfo(webAppUrl)
                    )
                )
            )
        }
        // Row 1: Search & Bookmarks
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_search", lang), callbackData = "menu:search"),
                InlineKeyboardButton(text = t("btn_bookmarks", lang), callbackData = "menu:bookmarks")
            )
        )
        // Row 2: Popular & Latest
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_popular", lang), callbackData = "feed:popular"),
                InlineKeyboardButton(text = t("btn_latest", lang), callbackData = "feed:latest")
            )
        )
        // Row 3: Surprise Me & Categories
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_random", lang), callbackData = "menu:random"),
                InlineKeyboardButton(text = t("btn_categories", lang), callbackData = "menu:categories")
            )
        )
        // Row 4: Watch History & Active Source
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_history", lang), callbackData = "menu:history"),
                InlineKeyboardButton(text = "📡 $activeSource", callbackData = "menu:sources")
            )
        )
        // Row 5: Language & Donate
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_lang", lang), callbackData = "menu:lang"),
                InlineKeyboardButton(text = t("btn_donate", lang), callbackData = "menu:donate")
            )
        )

        if (isAdmin) {
            rows.add(
                listOf(
                    InlineKeyboardButton(text = t("btn_admin_panel", lang), callbackData = "menu:admin")
                )
            )
        }

        return InlineKeyboardMarkup(rows)
    }

    suspend fun showButtonLoadingTag(
        bot: TelegramClient,
        chatId: Long,
        messageId: Long,
        originalMarkup: InlineKeyboardMarkup?,
        clickedCallbackData: String,
        lang: String
    ) {
        if (originalMarkup == null) return
        val tryingText = t("btn_trying", lang)
        val updatedRows = originalMarkup.inlineKeyboard.map { row ->
            row.map { btn ->
                if (btn.callbackData == clickedCallbackData) {
                    btn.copy(text = tryingText, callbackData = "noop")
                } else {
                    btn
                }
            }
        }
        bot.editMessageReplyMarkup(chatId, messageId, InlineKeyboardMarkup(updatedRows))
    }

    fun buildCarouselKeyboard(
        carouselToken: String,
        items: List<MediaItemSummary>,
        currentIndex: Int,
        contextType: String,
        lang: String,
        extraRows: List<List<InlineKeyboardButton>> = emptyList()
    ): InlineKeyboardMarkup {
        val rows = mutableListOf<List<InlineKeyboardButton>>()

        val pageSize = 8
        val pageIndex = (currentIndex / pageSize).coerceIn(0, (items.size - 1) / pageSize)
        val pageStart = pageIndex * pageSize
        val pageItems = items.drop(pageStart).take(pageSize)

        for ((idx, item) in pageItems.withIndex()) {
            val actualIdx = pageStart + idx
            val icon = if (item.type == TvType.Movie) "🎬" else "📺"
            val isCurrent = actualIdx == currentIndex
            val marker = if (isCurrent) "🔘 " else "${actualIdx + 1}. "
            val yearStr = item.year?.let { " ($it)" } ?: ""
            val token = CallbackTokenCache.put(MediaRef(item.apiName, UrlSanitizer.sanitizeTelegramUrl(item.url) ?: item.url))
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = "$marker$icon ${item.name}$yearStr",
                        callbackData = "v:$token"
                    )
                )
            )
        }

        if (items.size > pageSize) {
            val totalPages = (items.size + pageSize - 1) / pageSize
            val prevPageStart = if (pageIndex > 0) (pageIndex - 1) * pageSize else (totalPages - 1) * pageSize
            val nextPageStart = if (pageIndex < totalPages - 1) (pageIndex + 1) * pageSize else 0
            rows.add(
                listOf(
                    InlineKeyboardButton(text = "⏪ ${t("btn_prev", lang)}", callbackData = "car_nav:$carouselToken:$prevPageStart"),
                    InlineKeyboardButton(text = "📄 ${pageIndex + 1} / $totalPages", callbackData = "noop"),
                    InlineKeyboardButton(text = "${t("btn_next", lang)} ⏩", callbackData = "car_nav:$carouselToken:$nextPageStart")
                )
            )
        }

        // Native Telegram Photo Slider (Album) Button
        val validPosters = items.count { !it.posterUrl.isNullOrBlank() && it.posterUrl.startsWith("http") }
        if (validPosters >= 1) {
            val countLabel = validPosters.coerceAtMost(10)
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = "${t("btn_photo_slider", lang)} ($countLabel)",
                        callbackData = "car_album:$carouselToken"
                    )
                )
            )
        }

        rows.addAll(extraRows)
        return InlineKeyboardMarkup(rows)
    }

    fun buildExtraRowsForContext(ref: MediaCarouselRef, lang: String): List<List<InlineKeyboardButton>> {
        return when (ref.contextType) {
            "search" -> {
                val queryToken = CallbackTokenCache.put(ref.query ?: "")
                val srcUrl = UrlSanitizer.resolveWebUrl(null, ref.sourceName, ref.query)
                val rows = mutableListOf<List<InlineKeyboardButton>>()
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
                            text = t("btn_search_another_source", lang),
                            callbackData = "src_another:$queryToken"
                        )
                    )
                )
                rows.add(
                    listOf(
                        InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
                    )
                )
                rows
            }
            "category" -> {
                val catUrl = UrlSanitizer.resolveWebUrl(null, ref.sourceName, ref.query)
                val rows = mutableListOf<List<InlineKeyboardButton>>()
                if (!catUrl.isNullOrBlank()) {
                    rows.add(
                        listOf(
                            InlineKeyboardButton(
                                text = t("btn_open_website", lang),
                                url = catUrl
                            )
                        )
                    )
                }
                rows.add(
                    listOf(
                        InlineKeyboardButton(text = t("btn_categories", lang), callbackData = "menu:categories"),
                        InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
                    )
                )
                rows
            }
            else -> {
                val isPopular = ref.feedType == "popular"
                val srcUrl = UrlSanitizer.resolveWebUrl(null, ref.sourceName)
                val rows = mutableListOf<List<InlineKeyboardButton>>()
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
                        InlineKeyboardButton(
                            text = "${t("btn_switch_source", lang)} (${ref.sourceName})",
                            callbackData = "feed_picksrc:${ref.feedType ?: "popular"}"
                        ),
                        InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
                    )
                )
                rows
            }
        }
    }

    fun buildPagerKeyboard(
        carouselToken: String,
        items: List<MediaItemSummary>,
        currentIndex: Int,
        lang: String,
        userId: Long
    ): InlineKeyboardMarkup {
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        val total = items.size
        val currentItem = items[currentIndex]

        // Row 1: Single-step Flipper
        val prevIdx = if (currentIndex > 0) currentIndex - 1 else total - 1
        val nextIdx = if (currentIndex < total - 1) currentIndex + 1 else 0
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_prev", lang), callbackData = "car_pnav:$carouselToken:$prevIdx"),
                InlineKeyboardButton(text = "🖼 ${currentIndex + 1} / $total", callbackData = "car_pnav:$carouselToken:$currentIndex"),
                InlineKeyboardButton(text = t("btn_next", lang), callbackData = "car_pnav:$carouselToken:$nextIdx")
            )
        )

        // Row 2: Fast Jump Controls (-5, Random, +5)
        if (total >= 5) {
            val jumpMinus5 = ((currentIndex - 5) % total + total) % total
            val jumpPlus5 = (currentIndex + 5) % total
            rows.add(
                listOf(
                    InlineKeyboardButton(text = "⏪ -5", callbackData = "car_pnav:$carouselToken:$jumpMinus5"),
                    InlineKeyboardButton(text = "🔀 ${t("btn_random", lang)}", callbackData = "car_prand:$carouselToken"),
                    InlineKeyboardButton(text = "+5 ⏩", callbackData = "car_pnav:$carouselToken:$jumpPlus5")
                )
            )
        }

        // Row 3: Action Buttons (Watch Online & Bookmark)
        val itemToken = CallbackTokenCache.put(MediaRef(currentItem.apiName, UrlSanitizer.sanitizeTelegramUrl(currentItem.url) ?: currentItem.url))
        val isSaved = Database.isBookmarked(userId, currentItem.url)
        val bookmarkText = if (isSaved) t("btn_unbookmark", lang) else t("btn_bookmark", lang)
        val bookmarkData = if (isSaved) "unbm:$itemToken" else "bm:$itemToken"
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_watch", lang), callbackData = "v:$itemToken"),
                InlineKeyboardButton(text = bookmarkText, callbackData = bookmarkData)
            )
        )

        val itemWebUrl = UrlSanitizer.resolveWebUrl(currentItem.url, currentItem.apiName, currentItem.name)
        if (!itemWebUrl.isNullOrBlank()) {
            rows.add(
                listOf(
                    InlineKeyboardButton(text = t("btn_open_website", lang), url = itemWebUrl)
                )
            )
        }

        // Row 4: Switch back to List View and Close
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_list_view", lang), callbackData = "car_list:$carouselToken:$currentIndex"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        return InlineKeyboardMarkup(rows)
    }

    fun buildListCaptionForContext(
        ref: MediaCarouselRef,
        currentItem: MediaItemSummary,
        safeIndex: Int,
        lang: String
    ): String {
        return when (ref.contextType) {
            "search" -> {
                buildString {
                    append(t("search_results", lang, ref.query ?: ""))
                    append("\n(📡 ${ref.sourceName} • ${ref.items.size} items)\n\n")
                    append(t("poster_viewing_item", lang, safeIndex + 1, currentItem.name))
                    currentItem.year?.let { append(" ($it)") }
                }
            }
            "category" -> {
                buildString {
                    append("📂 *${ref.query ?: "Category"}*\n")
                    append("Source: *${ref.sourceName}*\n\n")
                    append(t("poster_viewing_item", lang, safeIndex + 1, currentItem.name))
                    currentItem.year?.let { append(" ($it)") }
                }
            }
            else -> {
                val titleKey = if (ref.feedType == "popular") "feed_popular_title" else "feed_latest_title"
                buildString {
                    append(t(titleKey, lang, ref.sourceName))
                    append("\n\n")
                    append(t("poster_viewing_item", lang, safeIndex + 1, currentItem.name))
                    currentItem.year?.let { append(" ($it)") }
                }
            }
        }
    }

    fun buildPagerCaption(
        item: MediaItemSummary,
        index: Int,
        total: Int,
        sourceName: String,
        lang: String
    ): String {
        val yearStr = item.year?.let { " ($it)" } ?: ""
        val typeStr = when (item.type) {
            TvType.Movie -> if (lang == "fa") "سینمایی" else "Movie"
            TvType.TvSeries -> if (lang == "fa") "سریال" else "TV Series"
            TvType.Anime -> if (lang == "fa") "انیمه" else "Anime"
            TvType.AnimeMovie -> if (lang == "fa") "سینمایی انیمه" else "Anime Movie"
            else -> item.type?.name ?: "Video"
        }
        return buildString {
            append("🖼 *${t("btn_image_pager", lang)}* (${index + 1}/$total)\n\n")
            append("🎬 *${item.name}*$yearStr\n")
            append("📁 *${if (lang == "fa") "نوع" else "Type"}:* $typeStr • 📡 *${if (lang == "fa") "منبع" else "Source"}:* $sourceName\n\n")
            append(if (lang == "fa") "👇 جهت ورق زدن سریع، پیشنهاد تصادفی یا تماشای فیلم کلیدهای زیر را لمس کنید:"
                   else "👇 Use the controls below to flip, jump randomly, or watch now:")
        }
    }
}
