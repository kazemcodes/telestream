package com.telestream.bot.handlers

import com.telestream.bot.model.CallbackTokenCache
import com.telestream.bot.model.MediaRef
import com.telestream.bot.util.UrlSanitizer
import com.telestream.database.Database
import com.telestream.providers.ProviderManager
import com.telestream.providers.year
import com.telestream.repo.AggregatedSource
import com.telestream.repo.CloudStreamRepoManager
import com.telestream.telegram.*
import org.slf4j.LoggerFactory

class InlineQueryHandler(private val bot: TelegramClient) {
    private val logger = LoggerFactory.getLogger(InlineQueryHandler::class.java)

    suspend fun handleInlineQuery(inlineQuery: InlineQuery) {
        try {
            val rawQuery = inlineQuery.query.trim()
            val userId = inlineQuery.from.id
            Database.ensureUser(userId)
            val activeSource = Database.getUserSource(userId)
            val isFa = Database.getUserLanguage(userId) == "fa"

            val isSourceQuery = rawQuery.isBlank() ||
                    rawQuery.startsWith("@sources", ignoreCase = true) ||
                    rawQuery.startsWith("/sources", ignoreCase = true) ||
                    rawQuery.startsWith("sources", ignoreCase = true) ||
                    rawQuery.startsWith("src", ignoreCase = true)

            val results = if (isSourceQuery) {
                val cleanQuery = rawQuery.replaceFirst("(?i)^[@#/]?(sources?|src)[:\\s]*".toRegex(), "").trim()
                val allSources = CloudStreamRepoManager.getAllAggregatedSources()
                val filtered = if (cleanQuery.isBlank()) {
                    allSources.sortedByDescending { it.name.equals(activeSource, ignoreCase = true) }.take(40)
                } else {
                    allSources.filter { src ->
                        src.name.contains(cleanQuery, ignoreCase = true) ||
                        src.language.contains(cleanQuery, ignoreCase = true) ||
                        src.description?.contains(cleanQuery, ignoreCase = true) == true
                    }.sortedWith(
                        compareByDescending<AggregatedSource> { it.name.equals(cleanQuery, ignoreCase = true) }
                            .thenByDescending { it.name.startsWith(cleanQuery, ignoreCase = true) }
                            .thenByDescending { it.name.contains(cleanQuery, ignoreCase = true) }
                    ).take(40)
                }

                if (filtered.isEmpty()) {
                    listOf(
                        InlineQueryResultArticle(
                            id = "empty_0",
                            title = if (isFa) "❌ هیچ سورسی یافت نشد" else "❌ No sources found",
                            description = if (isFa) "سورس «$cleanQuery» پیدا نشد. برای مشاهده همه سورس‌ها کلیک کنید."
                                          else "No source found for \"$cleanQuery\". Tap to view all.",
                            inputMessageContent = InputTextMessageContent(
                                messageText = "/sources",
                                parseMode = null
                            )
                        )
                    )
                } else {
                    filtered.mapIndexed { idx, src ->
                        val isActive = src.name.equals(activeSource, ignoreCase = true)
                        val statusIcon = if (isActive) "🔘" else "📡"
                        val langTag = "[${src.language.uppercase()}]"
                        val title = "$statusIcon ${src.name} $langTag"
                        val desc = "${if (isActive) "Active Source • " else ""}${src.description ?: "Movies & TV Series"}"
                        val safeHash = (src.name.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
                        val token = CallbackTokenCache.put(src.name)

                        InlineQueryResultArticle(
                            id = "src_${idx}_$safeHash",
                            title = title,
                            description = desc,
                            inputMessageContent = InputTextMessageContent(
                                messageText = "/source ${src.name}",
                                parseMode = null
                            ),
                            replyMarkup = InlineKeyboardMarkup(
                                listOf(
                                    listOf(
                                        InlineKeyboardButton(
                                            text = if (isActive) "🔘 Active: ${src.name}" else "📡 Set Active: ${src.name}",
                                            callbackData = "src_quick:$token"
                                        )
                                    )
                                )
                            )
                        )
                    }
                }
            } else {
                // Live Movie & Series Search
                val items = ProviderManager.searchInProvider(activeSource, rawQuery)
                if (items.isEmpty()) {
                    listOf(
                        InlineQueryResultArticle(
                            id = "no_movie_0",
                            title = if (isFa) "❌ عنوانی یافت نشد" else "❌ No results found",
                            description = if (isFa) "«$rawQuery» در منبع $activeSource پیدا نشد. برای تغییر منبع بزنید."
                                          else "No media found for \"$rawQuery\" in $activeSource. Tap to browse sources.",
                            inputMessageContent = InputTextMessageContent(
                                messageText = "/sources",
                                parseMode = null
                            )
                        )
                    )
                } else {
                    items.take(30).mapIndexed { idx, item ->
                        val safeHash = (item.url.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
                        val token = CallbackTokenCache.put(MediaRef(activeSource, item.url))
                        val yearStr = item.year?.let { " ($it)" } ?: ""
                        val typeStr = item.type?.name ?: "Media"
                        val title = "🎬 ${item.name}$yearStr"
                        val desc = "[$typeStr] • 📡 $activeSource"
                        val thumb = UrlSanitizer.sanitizeTelegramUrl(item.posterUrl)
                        val caption = buildString {
                            append("🎬 *${item.name}*$yearStr\n\n")
                            append("🌐 *Source:* $activeSource\n")
                            append("📁 *Type:* $typeStr\n")
                            val sanitizedUrl = UrlSanitizer.sanitizeTelegramUrl(item.url)
                            if (!sanitizedUrl.isNullOrBlank() && sanitizedUrl.startsWith("http")) {
                                append("🔗 [Website Link]($sanitizedUrl)\n")
                            }
                        }
                        InlineQueryResultArticle(
                            id = "mov_${idx}_$safeHash",
                            title = title,
                            description = desc,
                            thumbnailUrl = thumb,
                            inputMessageContent = InputTextMessageContent(
                                messageText = caption,
                                parseMode = "Markdown"
                            ),
                            replyMarkup = InlineKeyboardMarkup(
                                listOf(
                                    listOf(
                                        InlineKeyboardButton(
                                            text = if (isFa) "▶️ تماشا و پخش آنلاین" else "▶️ Watch & Stream",
                                            callbackData = "v:$token"
                                        )
                                    )
                                )
                            )
                        )
                    }
                }
            }

            val ok = bot.answerInlineQuery(inlineQuery.id, results, cacheTime = 1)
            if (!ok) {
                logger.warn("answerInlineQuery returned false for query '$rawQuery' (id: ${inlineQuery.id})")
            }
        } catch (e: Exception) {
            logger.error("Error handling inline query: ${e.message}", e)
        }
    }
}
