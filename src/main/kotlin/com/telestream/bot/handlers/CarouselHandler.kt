package com.telestream.bot.handlers

import com.telestream.bot.model.CallbackTokenCache
import com.telestream.bot.model.MediaCarouselRef
import com.telestream.bot.model.MediaRef
import com.telestream.bot.ui.BotKeyboards
import com.telestream.bot.util.UrlSanitizer
import com.telestream.i18n.I18n.t
import com.telestream.telegram.CallbackQuery
import com.telestream.telegram.InlineKeyboardButton
import com.telestream.telegram.InlineKeyboardMarkup
import com.telestream.telegram.InputMediaPhoto
import com.telestream.telegram.TelegramClient

class CarouselHandler(private val bot: TelegramClient) {

    suspend fun showPagerScreen(
        chatId: Long,
        messageId: Long,
        ref: MediaCarouselRef,
        carouselToken: String,
        targetIndex: Int,
        lang: String,
        userId: Long
    ) {
        val safeIndex = targetIndex.coerceIn(0, ref.items.size - 1)
        ref.currentIndex = safeIndex
        val currentItem = ref.items[safeIndex]
        val keyboard = BotKeyboards.buildPagerKeyboard(carouselToken, ref.items, safeIndex, lang, userId)
        val caption = BotKeyboards.buildPagerCaption(currentItem, safeIndex, ref.items.size, ref.sourceName, lang)
        val targetPoster = currentItem.posterUrl?.takeIf { it.startsWith("http") }?.let { UrlSanitizer.sanitizeTelegramUrl(it) }

        val edited = if (targetPoster != null) {
            bot.editMessageMedia(chatId, messageId, targetPoster, caption = caption, replyMarkup = keyboard)
        } else {
            bot.editMessageCaption(chatId, messageId, caption = caption, replyMarkup = keyboard)
        }
        if (!edited) {
            bot.editMessageText(chatId, messageId, caption, replyMarkup = keyboard)
        }
    }

    suspend fun handleCarouselNav(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, lang: String) {
        val parts = data.removePrefix("car_nav:").split(":")
        val token = parts.getOrNull(0) ?: return
        val newIndex = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val ref = CallbackTokenCache.get<MediaCarouselRef>(token)
        if (ref == null) {
            bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
            return
        }

        val safeIndex = newIndex.coerceIn(0, ref.items.size - 1)
        ref.currentIndex = safeIndex
        val currentItem = ref.items[safeIndex]
        val extraRows = BotKeyboards.buildExtraRowsForContext(ref, lang)
        val keyboard = BotKeyboards.buildCarouselKeyboard(
            carouselToken = token,
            items = ref.items,
            currentIndex = safeIndex,
            contextType = ref.contextType,
            lang = lang,
            extraRows = extraRows
        )
        val caption = BotKeyboards.buildListCaptionForContext(ref, currentItem, safeIndex, lang)
        val targetPoster = currentItem.posterUrl?.takeIf { it.startsWith("http") }?.let { UrlSanitizer.sanitizeTelegramUrl(it) }
        val edited = if (targetPoster != null) {
            bot.editMessageMedia(chatId, messageId, targetPoster, caption = caption, replyMarkup = keyboard)
        } else {
            bot.editMessageCaption(chatId, messageId, caption = caption, replyMarkup = keyboard)
        }
        if (!edited) {
            bot.editMessageText(chatId, messageId, caption, replyMarkup = keyboard)
        }
        bot.answerCallbackQuery(callback.id)
    }

    suspend fun handlePager(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, lang: String, userId: Long) {
        val parts = data.removePrefix("car_pager:").split(":")
        val token = parts.getOrNull(0) ?: return
        val index = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val ref = CallbackTokenCache.get<MediaCarouselRef>(token)
        if (ref == null) {
            bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
            return
        }
        bot.answerCallbackQuery(callback.id)
        showPagerScreen(chatId, messageId, ref, token, index, lang, userId)
    }

    suspend fun handlePagerNav(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, lang: String, userId: Long) {
        val parts = data.removePrefix("car_pnav:").split(":")
        val token = parts.getOrNull(0) ?: return
        val newIndex = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val ref = CallbackTokenCache.get<MediaCarouselRef>(token)
        if (ref == null) {
            bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
            return
        }
        bot.answerCallbackQuery(callback.id)
        showPagerScreen(chatId, messageId, ref, token, newIndex, lang, userId)
    }

    suspend fun handlePagerRandom(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, lang: String, userId: Long) {
        val token = data.removePrefix("car_prand:")
        val ref = CallbackTokenCache.get<MediaCarouselRef>(token)
        if (ref == null) {
            bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
            return
        }
        val randIndex = if (ref.items.size > 1) {
            var r = (0 until ref.items.size).random()
            if (r == ref.currentIndex) (r + 1) % ref.items.size else r
        } else 0
        bot.answerCallbackQuery(callback.id, "🎲 🔀")
        showPagerScreen(chatId, messageId, ref, token, randIndex, lang, userId)
    }

    suspend fun handleCarouselList(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, lang: String) {
        val parts = data.removePrefix("car_list:").split(":")
        val token = parts.getOrNull(0) ?: return
        val index = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val ref = CallbackTokenCache.get<MediaCarouselRef>(token)
        if (ref == null) {
            bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
            return
        }
        val safeIndex = index.coerceIn(0, ref.items.size - 1)
        ref.currentIndex = safeIndex
        val currentItem = ref.items[safeIndex]
        val extraRows = BotKeyboards.buildExtraRowsForContext(ref, lang)
        val keyboard = BotKeyboards.buildCarouselKeyboard(
            carouselToken = token,
            items = ref.items,
            currentIndex = safeIndex,
            contextType = ref.contextType,
            lang = lang,
            extraRows = extraRows
        )
        val caption = BotKeyboards.buildListCaptionForContext(ref, currentItem, safeIndex, lang)
        val targetPoster = currentItem.posterUrl?.takeIf { it.startsWith("http") }?.let { UrlSanitizer.sanitizeTelegramUrl(it) }
        val edited = if (targetPoster != null) {
            bot.editMessageMedia(chatId, messageId, targetPoster, caption = caption, replyMarkup = keyboard)
        } else {
            bot.editMessageCaption(chatId, messageId, caption = caption, replyMarkup = keyboard)
        }
        if (!edited) {
            bot.editMessageText(chatId, messageId, caption, replyMarkup = keyboard)
        }
        bot.answerCallbackQuery(callback.id)
    }

    suspend fun handleCarouselAlbum(callback: CallbackQuery, data: String, chatId: Long, lang: String) {
        val token = data.removePrefix("car_album:")
        val ref = CallbackTokenCache.get<MediaCarouselRef>(token)
        if (ref == null) {
            bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
            return
        }

        val validItems = ref.items
            .filter { !it.posterUrl.isNullOrBlank() && it.posterUrl.startsWith("http") }
            .take(10)

        val validPhotos = validItems
            .mapIndexed { idx, item ->
                val safePoster = UrlSanitizer.sanitizeTelegramUrl(item.posterUrl!!) ?: item.posterUrl!!
                val yearStr = item.year?.let { " ($it)" } ?: ""
                InputMediaPhoto(
                    media = safePoster,
                    caption = "${idx + 1}. 🎬 ${item.name}$yearStr",
                    parseMode = null
                )
            }

        if (validPhotos.isEmpty()) {
            bot.answerCallbackQuery(callback.id, if (lang == "fa") "تصویری برای اسلایدر یافت نشد" else "No images found for slider", showAlert = true)
            return
        }

        bot.answerCallbackQuery(callback.id)
        val success = bot.sendMediaGroup(chatId, validPhotos)
        if (success) {
            val promptText = "${t("btn_photo_slider", lang)}\n\n${t("slider_instructions", lang)}"
            val sliderButtons = mutableListOf<List<InlineKeyboardButton>>()

            // Watch buttons in 2 columns
            val actionButtons = validItems.mapIndexed { idx, item ->
                val itemToken = CallbackTokenCache.put(MediaRef(item.apiName, UrlSanitizer.sanitizeTelegramUrl(item.url) ?: item.url))
                InlineKeyboardButton(
                    text = "▶️ ${idx + 1}. ${item.name.take(16)}",
                    callbackData = "v:$itemToken"
                )
            }.chunked(2)
            sliderButtons.addAll(actionButtons)

            // Navigation row: [ 🌐 Open Link ] [ 📋 Return to List ] [ ❌ Close ]
            val albumWebUrl = UrlSanitizer.resolveWebUrl(null, ref.sourceName, ref.query)
            if (!albumWebUrl.isNullOrBlank()) {
                sliderButtons.add(
                    listOf(
                        InlineKeyboardButton(text = t("btn_open_website", lang), url = albumWebUrl)
                    )
                )
            }
            sliderButtons.add(
                listOf(
                    InlineKeyboardButton(text = t("btn_view_list", lang), callbackData = "car_list:$token:0"),
                    InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
                )
            )
            bot.sendMessage(chatId, promptText, replyMarkup = InlineKeyboardMarkup(sliderButtons))
        } else {
            bot.sendMessage(chatId, if (lang == "fa") "⚠️ امکان ارسال اسلایدر تصاویر وجود نداشت. لطفاً از لیست متنی استفاده کنید." else "⚠️ Could not send photo slider album. Please use the text list.")
        }
    }
}
