package com.telestream.bot

import com.lagradost.cloudstream3.*
import com.telestream.config.Config
import com.telestream.database.Database
import com.telestream.database.WatchHistoryItem
import com.telestream.i18n.I18n.t
import com.telestream.providers.ProviderManager
import com.telestream.providers.episodes
import com.telestream.providers.year
import com.telestream.repo.AggregatedSource
import com.telestream.repo.CloudStreamRepoManager
import com.telestream.telegram.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

// Token cache to guarantee Telegram callback_data never exceeds 64 bytes
object CallbackTokenCache {
    private val counter = AtomicLong(1)
    private const val MAX_SIZE = 10_000
    private val cache = ConcurrentHashMap<String, Any>()
    private val queue = ConcurrentLinkedQueue<String>()

    fun put(value: Any): String {
        val id = counter.getAndIncrement().toString(36)
        cache[id] = value
        queue.add(id)
        if (queue.size > MAX_SIZE) {
            val oldest = queue.poll()
            if (oldest != null) cache.remove(oldest)
        }
        return id
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(token: String): T? = cache[token] as? T
}

fun sanitizeTelegramUrl(rawUrl: String?): String? {
    if (rawUrl.isNullOrBlank()) return null
    val trimmed = rawUrl.trim()
    return trimmed.replace(" ", "%20")
}

data class MediaRef(val provider: String, val url: String)
data class EpisodeRef(
    val provider: String,
    val seriesRefToken: String,
    val episodeData: String,
    val episodeTitle: String,
    val page: Int
)
data class SourceToggleRef(
    val repoName: String,
    val lang: String,
    val page: Int,
    val sourceName: String
)
data class RepoPageRef(
    val repoName: String,
    val lang: String,
    val page: Int
)
data class SearchExecRef(
    val sourceName: String,
    val query: String
)
data class SourceBrowserRef(
    val lang: String,
    val page: Int,
    val query: String? = null
)
data class SourceActionRef(
    val lang: String,
    val page: Int,
    val sourceName: String,
    val query: String? = null
)
data class CategorySectionRef(
    val provider: String,
    val sectionName: String,
    val page: Int = 1
)
data class MediaItemSummary(
    val name: String,
    val url: String,
    val apiName: String,
    val posterUrl: String?,
    val type: TvType?,
    val year: Int?
)
data class MediaCarouselRef(
    val contextType: String,
    val sourceName: String,
    val query: String? = null,
    val feedType: String? = null,
    val page: Int = 0,
    val items: List<MediaItemSummary>,
    var currentIndex: Int = 0
)


class BotRunner(private val bot: TelegramClient) {
    private val logger = LoggerFactory.getLogger(BotRunner::class.java)
    private var isRunning = true
    private val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Multi-user debounce cache to prevent rapid double-clicks
    private val userLastAction = ConcurrentHashMap<Long, Long>()

    // Tracks if user explicitly selected a source for their upcoming text input
    private val userSearchPending = ConcurrentHashMap<Long, String>()
    // Tracks if user is in source keyword filter mode
    private val userSourceFilterPending = ConcurrentHashMap<Long, Boolean>()

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
        workerScope.cancel()
    }

    suspend fun startPolling() {
        logger.info("Starting TeleStream bot polling loop...")
        var offset = 0L

        while (isRunning) {
            try {
                val updates = bot.getUpdates(offset = offset, timeout = 25)
                for (update in updates) {
                    offset = update.updateId + 1

                    // High-concurrency: dispatch each update asynchronously on workerScope
                    workerScope.launch {
                        try {
                            if (update.message != null) {
                                handleMessage(update.message)
                            } else if (update.callbackQuery != null) {
                                handleCallback(update.callbackQuery)
                            } else if (update.inlineQuery != null) {
                                handleInlineQuery(update.inlineQuery)
                            }
                        } catch (e: Exception) {
                            logger.error("Error processing update ${update.updateId}: ${e.message}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("Polling error: ${e.message}. Retrying in 3s...")
                delay(3000)
            }
        }
    }

    private suspend fun handleInlineQuery(inlineQuery: InlineQuery) {
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
                        val thumb = sanitizeTelegramUrl(item.posterUrl)
                        val caption = buildString {
                            append("🎬 *${item.name}*$yearStr\n\n")
                            append("🌐 *Source:* $activeSource\n")
                            append("📁 *Type:* $typeStr\n")
                            val sanitizedUrl = sanitizeTelegramUrl(item.url)
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

    private fun getMainMenuKeyboard(lang: String, activeSource: String, isAdmin: Boolean = false, userId: Long = 0L): InlineKeyboardMarkup {
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
        // Optional Row 6: Admin Panel (only for admins)
        if (isAdmin) {
            rows.add(
                listOf(
                    InlineKeyboardButton(text = t("btn_admin_panel", lang), callbackData = "menu:admin")
                )
            )
        }
        return InlineKeyboardMarkup(rows)
    }

    private suspend fun handleMessage(message: Message) {
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
                userSearchPending.remove(userId)
                bot.sendMessage(chatId, t("welcome", lang), replyMarkup = getMainMenuKeyboard(lang, activeSource, isAdmin, userId))
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
                showFeedScreen(chatId, userId, lang, "popular")
            }

            cmd == "/latest" -> {
                showFeedScreen(chatId, userId, lang, "latest")
            }

            cmd == "/random" -> {
                userSourceFilterPending.remove(userId)
                showRandomPick(chatId, userId, lang, activeSource)
            }

            cmd == "/categories" -> {
                userSourceFilterPending.remove(userId)
                showCategories(chatId, userId, lang, activeSource)
            }

            cmd == "/history" -> {
                userSourceFilterPending.remove(userId)
                showWatchHistory(chatId, userId, lang)
            }

            cmd == "/sources" || cmd == "/manage_sources" || cmd == "/enabled_sources" -> {
                userSourceFilterPending.remove(userId)
                showSourcesManager(chatId, userId, lang)
            }

            cmd == "/source" || cmd == "/src" -> {
                userSourceFilterPending.remove(userId)
                if (arg.isBlank()) {
                    showSourcesManager(chatId, userId, lang)
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
                            replyMarkup = getMainMenuKeyboard(lang, matched.name, isAdmin, userId)
                        )
                    } else if (matches.isNotEmpty()) {
                        showSourcesManager(chatId, userId, lang, filterLang = "all", page = 0, query = arg)
                    } else {
                        bot.sendMessage(chatId, t("no_sources_found", lang, arg))
                    }
                }
            }

            cmd == "/search" -> {
                userSourceFilterPending.remove(userId)
                if (arg.isBlank()) {
                    bot.sendMessage(chatId, t("search_prompt_direct", lang, activeSource))
                } else {
                    executeSearch(chatId, userId, lang, activeSource, arg)
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
                showDonationMessage(chatId, lang)
            }

            cmd == "/admin" -> {
                if (!Config.isAdmin(userId)) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                } else {
                    showAdminDashboard(chatId, lang)
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
                    showReposSummary(chatId, lang)
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
                showLanguageSelector(chatId, lang)
            }

            cmd == "/bookmarks" -> {
                showBookmarks(chatId, userId, lang)
            }

            else -> {
                if (userSourceFilterPending.remove(userId) == true) {
                    showSourcesManager(chatId, userId, lang, filterLang = "all", page = 0, query = text)
                } else {
                    // User sent title text directly: search active source immediately
                    val targetSource = userSearchPending.remove(userId) ?: activeSource
                    executeSearch(chatId, userId, lang, targetSource, text)
                }
            }
        }
    }

    private suspend fun promptPickSourceToSearch(
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

    private suspend fun promptChooseSourceForQuery(
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

    private fun buildCarouselKeyboard(
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
            val token = CallbackTokenCache.put(MediaRef(item.apiName, sanitizeTelegramUrl(item.url) ?: item.url))
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = "$marker$icon ${item.name}$yearStr",
                        callbackData = "v:$token"
                    )
                )
            )
        }

        if (items.size > 1) {
            val prevIdx = if (currentIndex > 0) currentIndex - 1 else items.size - 1
            val nextIdx = if (currentIndex < items.size - 1) currentIndex + 1 else 0
            val flipperRow = mutableListOf<InlineKeyboardButton>()
            flipperRow.add(InlineKeyboardButton(text = t("btn_prev", lang), callbackData = "car_nav:$carouselToken:$prevIdx"))
            flipperRow.add(InlineKeyboardButton(text = "🖼 ${currentIndex + 1}/${items.size}", callbackData = "car_nav:$carouselToken:$currentIndex"))
            flipperRow.add(InlineKeyboardButton(text = t("btn_next", lang), callbackData = "car_nav:$carouselToken:$nextIdx"))
            rows.add(flipperRow)
        }

        if (items.size > pageSize) {
            val totalPages = (items.size + pageSize - 1) / pageSize
            val prevPageStart = if (pageIndex > 0) (pageIndex - 1) * pageSize else (totalPages - 1) * pageSize
            val nextPageStart = if (pageIndex < totalPages - 1) (pageIndex + 1) * pageSize else 0
            rows.add(
                listOf(
                    InlineKeyboardButton(text = "⏪ ${t("btn_prev", lang)}", callbackData = "car_nav:$carouselToken:$prevPageStart"),
                    InlineKeyboardButton(text = "📄 ${pageIndex + 1} / $totalPages", callbackData = "car_nav:$carouselToken:$currentIndex"),
                    InlineKeyboardButton(text = "${t("btn_next", lang)} ⏩", callbackData = "car_nav:$carouselToken:$nextPageStart")
                )
            )
        }

        val validPosters = items.count { !it.posterUrl.isNullOrBlank() && it.posterUrl.startsWith("http") }
        if (validPosters >= 1) {
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = "${t("btn_image_pager", lang)} ($validPosters)",
                        callbackData = "car_pager:$carouselToken:$currentIndex"
                    )
                )
            )
        }
        if (validPosters >= 2) {
            val albumLimit = validPosters.coerceAtMost(10)
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = "${t("btn_view_album", lang)} ($albumLimit)",
                        callbackData = "car_album:$carouselToken"
                    )
                )
            )
        }

        rows.addAll(extraRows)
        return InlineKeyboardMarkup(rows)
    }

    private fun buildExtraRowsForContext(ref: MediaCarouselRef, lang: String): List<List<InlineKeyboardButton>> {
        return when (ref.contextType) {
            "search" -> {
                val queryToken = CallbackTokenCache.put(ref.query ?: "")
                listOf(
                    listOf(
                        InlineKeyboardButton(
                            text = t("btn_search_another_source", lang),
                            callbackData = "src_another:$queryToken"
                        )
                    ),
                    listOf(
                        InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
                    )
                )
            }
            "category" -> {
                listOf(
                    listOf(
                        InlineKeyboardButton(text = t("btn_categories", lang), callbackData = "menu:categories"),
                        InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
                    )
                )
            }
            else -> {
                val isPopular = ref.feedType == "popular"
                listOf(
                    listOf(
                        InlineKeyboardButton(
                            text = if (isPopular) "🔘 ${t("btn_popular", lang)}" else t("btn_popular", lang),
                            callbackData = "feed:popular"
                        ),
                        InlineKeyboardButton(
                            text = if (!isPopular) "🔘 ${t("btn_latest", lang)}" else t("btn_latest", lang),
                            callbackData = "feed:latest"
                        )
                    ),
                    listOf(
                        InlineKeyboardButton(
                            text = "${t("btn_switch_source", lang)} (${ref.sourceName})",
                            callbackData = "feed_picksrc:${ref.feedType ?: "popular"}"
                        ),
                        InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
                    )
                )
            }
        }
    }

    private fun buildListCaptionForContext(
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

    private fun buildPagerKeyboard(
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
        val itemToken = CallbackTokenCache.put(MediaRef(currentItem.apiName, sanitizeTelegramUrl(currentItem.url) ?: currentItem.url))
        val isSaved = Database.isBookmarked(userId, currentItem.url)
        val bookmarkText = if (isSaved) t("btn_unbookmark", lang) else t("btn_bookmark", lang)
        val bookmarkData = if (isSaved) "unbm:$itemToken" else "bm:$itemToken"
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_watch", lang), callbackData = "v:$itemToken"),
                InlineKeyboardButton(text = bookmarkText, callbackData = bookmarkData)
            )
        )

        // Row 4: Switch back to List View and Close
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_list_view", lang), callbackData = "car_list:$carouselToken:$currentIndex"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        return InlineKeyboardMarkup(rows)
    }

    private fun buildPagerCaption(
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

    private suspend fun showPagerScreen(
        chatId: Long,
        messageId: Long?,
        ref: MediaCarouselRef,
        carouselToken: String,
        index: Int,
        lang: String,
        userId: Long
    ) {
        val safeIndex = index.coerceIn(0, ref.items.size - 1)
        ref.currentIndex = safeIndex
        val currentItem = ref.items[safeIndex]
        val keyboard = buildPagerKeyboard(carouselToken, ref.items, safeIndex, lang, userId)
        val caption = buildPagerCaption(currentItem, safeIndex, ref.items.size, ref.sourceName, lang)
        val poster = currentItem.posterUrl?.takeIf { it.startsWith("http") }?.let { sanitizeTelegramUrl(it) }

        if (messageId != null) {
            val edited = if (poster != null) {
                bot.editMessageMedia(chatId, messageId, poster, caption = caption, replyMarkup = keyboard)
            } else {
                bot.editMessageCaption(chatId, messageId, caption = caption, replyMarkup = keyboard)
            }
            if (!edited) {
                bot.editMessageText(chatId, messageId, caption, replyMarkup = keyboard)
            }
        } else {
            if (poster != null) {
                bot.sendPhoto(chatId, poster, caption = caption, replyMarkup = keyboard)
            } else {
                bot.sendMessage(chatId, caption, replyMarkup = keyboard)
            }
        }
    }

    private suspend fun executeSearch(
        chatId: Long,
        userId: Long,
        lang: String,
        sourceName: String,
        query: String
    ) {
        bot.sendMessage(chatId, t("searching_in_source", lang, sourceName, query))
        val results = ProviderManager.searchInProvider(sourceName, query)
        val queryToken = CallbackTokenCache.put(query)

        if (results.isEmpty()) {
            val err = ProviderManager.getLastError(sourceName)
            val msgText = if (err != null && err.isNetworkOrBlocked) {
                t("search_error_source", lang, sourceName, err.message)
            } else {
                t("no_results_in_source", lang, sourceName, query)
            }
            val retryToken = CallbackTokenCache.put(Pair(sourceName, query))
            val keyboard = InlineKeyboardMarkup(
                listOf(
                    listOf(
                        InlineKeyboardButton(
                            text = t("btn_search_another_source", lang),
                            callbackData = "src_another:$queryToken"
                        )
                    ),
                    listOf(
                        InlineKeyboardButton(
                            text = t("btn_retry", lang),
                            callbackData = "src_retry:$retryToken"
                        ),
                        InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
                    )
                )
            )
            bot.sendMessage(chatId, msgText, replyMarkup = keyboard)
            return
        }

        val summaries = results.take(30).map { item ->
            MediaItemSummary(
                name = item.name,
                url = sanitizeTelegramUrl(item.url) ?: item.url,
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

        val extraRows = listOf(
            listOf(
                InlineKeyboardButton(
                    text = t("btn_search_another_source", lang),
                    callbackData = "src_another:$queryToken"
                )
            ),
            listOf(
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val keyboard = buildCarouselKeyboard(
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

    private suspend fun showSourcesManager(
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
                    switchInlineQueryCurrentChat = ""
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
            val quickPicks = listOf("KissKH", "AvaMovie", "FaselHD", "XD Movies")
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

    private suspend fun showSourcesMenu(
        chatId: Long,
        userId: Long,
        lang: String,
        messageId: Long? = null
    ) {
        showSourcesManager(chatId, userId, lang, messageId = messageId)
    }

    private suspend fun showEnabledSourcesScreen(
        chatId: Long,
        userId: Long,
        lang: String,
        messageId: Long? = null
    ) {
        showSourcesManager(chatId, userId, lang, messageId = messageId)
    }

    private suspend fun showManageSourcesStep1(
        chatId: Long,
        userId: Long,
        lang: String,
        messageId: Long? = null
    ) {
        showSourcesManager(chatId, userId, lang, messageId = messageId)
    }

    private suspend fun showFeedScreen(
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
            val emptyText = if (err != null && err.isNetworkOrBlocked) {
                t("source_unreachable", lang, activeSource, err.message)
            } else {
                "$headerText\n\n${t("feed_no_items", lang)}"
            }
            val retryToken = CallbackTokenCache.put(activeSource)
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
                url = sanitizeTelegramUrl(item.url) ?: item.url,
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

        val extraRows = listOf(
            listOf(
                InlineKeyboardButton(
                    text = if (isPopular) "🔘 ${t("btn_popular", lang)}" else t("btn_popular", lang),
                    callbackData = "feed:popular"
                ),
                InlineKeyboardButton(
                    text = if (!isPopular) "🔘 ${t("btn_latest", lang)}" else t("btn_latest", lang),
                    callbackData = "feed:latest"
                )
            ),
            listOf(
                InlineKeyboardButton(text = "${t("btn_switch_source", lang)} ($activeSource)", callbackData = "feed_picksrc:$feedType"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val keyboard = buildCarouselKeyboard(
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
                bot.editMessageCaption(chatId, messageId, caption, replyMarkup = keyboard)
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

    private suspend fun promptFeedPickSource(
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

    private suspend fun showRandomPick(
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

    private suspend fun showWatchHistory(
        chatId: Long,
        userId: Long,
        lang: String,
        messageId: Long? = null
    ) {
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

    private suspend fun showCategories(
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

    private suspend fun showCategoryItems(
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
                url = sanitizeTelegramUrl(item.url) ?: item.url,
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

        val kb = buildCarouselKeyboard(
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

    private suspend fun handleCallback(callback: CallbackQuery) {
        val chatId = callback.message?.chat?.id ?: return
        val messageId = callback.message.messageId
        val data = callback.data ?: return
        val userId = callback.from.id
        val lang = Database.getUserLanguage(userId)

        if (isDebounced(userId) && !data.startsWith("setlang:") && !data.startsWith("setsource:") && !data.startsWith("src_tog:") && !data.startsWith("src_set:") && !data.startsWith("src_lang:") && !data.startsWith("src_page:") && data != "close" && data != "noop") {
            bot.answerCallbackQuery(callback.id)
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
                    replyMarkup = getMainMenuKeyboard(newLang, activeSource, isAdmin, userId)
                )
            }

            data == "menu:lang" -> {
                showLanguageSelector(chatId, lang, messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:sources" -> {
                showSourcesMenu(chatId, userId, lang, messageId = messageId)
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

            data == "feed:popular" -> {
                showFeedScreen(chatId, userId, lang, "popular", messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "feed:latest" -> {
                showFeedScreen(chatId, userId, lang, "latest", messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("feed_picksrc:") -> {
                val feedType = data.removePrefix("feed_picksrc:")
                promptFeedPickSource(chatId, userId, lang, feedType, messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("feed_src:") -> {
                val parts = data.removePrefix("feed_src:").split(":")
                val feedType = parts.getOrNull(0) ?: "popular"
                val token = parts.getOrNull(1) ?: ""
                val sourceName = CallbackTokenCache.get<String>(token) ?: Database.getUserSource(userId)
                Database.setUserSource(userId, sourceName)
                showFeedScreen(chatId, userId, lang, feedType, sourceName = sourceName, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("feed_retry:") -> {
                val parts = data.removePrefix("feed_retry:").split(":")
                val feedType = parts.getOrNull(0) ?: "popular"
                val page = parts.getOrNull(1)?.toIntOrNull() ?: 1
                val token = parts.getOrNull(2) ?: ""
                val sourceName = CallbackTokenCache.get<String>(token) ?: Database.getUserSource(userId)
                showFeedScreen(chatId, userId, lang, feedType, page = page, sourceName = sourceName, messageId = messageId)
                bot.answerCallbackQuery(callback.id, t("btn_retry", lang))
            }

            data.startsWith("src_retry:") -> {
                val token = data.removePrefix("src_retry:")
                val pair = CallbackTokenCache.get<Pair<String, String>>(token)
                if (pair != null) {
                    val (sourceName, query) = pair
                    executeSearch(chatId, userId, lang, sourceName, query)
                }
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:sources" || data == "menu:manage_sources" || data == "menu:enabled_sources" -> {
                showSourcesManager(chatId, userId, lang, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "src_search_prompt" -> {
                userSourceFilterPending[userId] = true
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
                userSourceFilterPending.remove(userId)
                showSourcesManager(chatId, userId, lang, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "src_clear" -> {
                userSourceFilterPending.remove(userId)
                showSourcesManager(chatId, userId, lang, filterLang = "all", page = 0, query = null, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("src_quick:") -> {
                val raw = data.removePrefix("src_quick:")
                val srcName = CallbackTokenCache.get<String>(raw) ?: raw
                Database.setUserSource(userId, srcName)
                val langTag = CloudStreamRepoManager.getAllAggregatedSources().find { it.name.equals(srcName, ignoreCase = true) }?.language?.uppercase() ?: "ALL"
                bot.answerCallbackQuery(callback.id, t("source_selected", lang, srcName))
                bot.sendMessage(
                    chatId,
                    t("source_switched_ready", lang, srcName, langTag),
                    replyMarkup = getMainMenuKeyboard(lang, srcName, Config.isAdmin(userId), userId)
                )
            }

            data.startsWith("src_lang:") -> {
                val token = data.removePrefix("src_lang:")
                val ref = CallbackTokenCache.get<SourceBrowserRef>(token)
                if (ref != null) {
                    showSourcesManager(chatId, userId, lang, filterLang = ref.lang, page = 0, query = ref.query, messageId = messageId)
                }
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("src_page:") -> {
                val token = data.removePrefix("src_page:")
                val ref = CallbackTokenCache.get<SourceBrowserRef>(token)
                if (ref != null) {
                    showSourcesManager(chatId, userId, lang, filterLang = ref.lang, page = ref.page, query = ref.query, messageId = messageId)
                }
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("src_set:") -> {
                val token = data.removePrefix("src_set:")
                val ref = CallbackTokenCache.get<SourceActionRef>(token)
                if (ref != null) {
                    Database.setUserSource(userId, ref.sourceName)
                    bot.answerCallbackQuery(callback.id, t("source_selected", lang, ref.sourceName))
                    showSourcesManager(chatId, userId, lang, filterLang = ref.lang, page = ref.page, query = ref.query, messageId = messageId)
                } else {
                    bot.answerCallbackQuery(callback.id, "Session expired")
                }
            }

            data.startsWith("src_tog:") -> {
                val token = data.removePrefix("src_tog:")
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

            data == "menu:admin" -> {
                if (Config.isAdmin(userId)) {
                    showAdminDashboard(chatId, lang, messageId)
                    bot.answerCallbackQuery(callback.id)
                } else {
                    bot.answerCallbackQuery(callback.id, t("admin_only", lang), showAlert = true)
                }
            }

            data == "admin:repos" -> {
                if (Config.isAdmin(userId)) {
                    showReposSummary(chatId, lang, messageId)
                    bot.answerCallbackQuery(callback.id)
                } else {
                    bot.answerCallbackQuery(callback.id, t("admin_only", lang), showAlert = true)
                }
            }

            data == "menu:search" -> {
                val active = Database.getUserSource(userId)
                bot.sendMessage(chatId, t("search_prompt_direct", lang, active))
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("src_pick:") -> {
                val token = data.removePrefix("src_pick:")
                val sourceName = CallbackTokenCache.get<String>(token) ?: Database.getUserSource(userId)
                Database.setUserSource(userId, sourceName)
                userSearchPending[userId] = sourceName
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

            data.startsWith("src_exec:") -> {
                val token = data.removePrefix("src_exec:")
                val ref = CallbackTokenCache.get<SearchExecRef>(token)
                if (ref != null) {
                    bot.answerCallbackQuery(callback.id)
                    Database.setUserSource(userId, ref.sourceName)
                    executeSearch(chatId, userId, lang, ref.sourceName, ref.query)
                } else {
                    bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
                }
            }

            data.startsWith("src_another:") -> {
                val queryTok = data.removePrefix("src_another:")
                val query = CallbackTokenCache.get<String>(queryTok) ?: ""
                bot.answerCallbackQuery(callback.id)
                if (query.isNotBlank()) {
                    promptChooseSourceForQuery(chatId, userId, lang, query, messageId)
                } else {
                    promptPickSourceToSearch(chatId, userId, lang, messageId)
                }
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

            data == "close" || data == "menu:start" -> {
                userSearchPending.remove(userId)
                val activeSource = Database.getUserSource(userId)
                val edited = bot.editMessageText(
                    chatId,
                    messageId,
                    t("welcome", lang),
                    replyMarkup = getMainMenuKeyboard(lang, activeSource, Config.isAdmin(userId), userId)
                )
                if (!edited) {
                    bot.sendMessage(chatId, t("welcome", lang), replyMarkup = getMainMenuKeyboard(lang, activeSource, Config.isAdmin(userId), userId))
                }
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("car_nav:") -> {
                // car_nav:{token}:{newIndex}
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
                val extraRows = buildExtraRowsForContext(ref, lang)
                val keyboard = buildCarouselKeyboard(
                    carouselToken = token,
                    items = ref.items,
                    currentIndex = safeIndex,
                    contextType = ref.contextType,
                    lang = lang,
                    extraRows = extraRows
                )
                val caption = buildListCaptionForContext(ref, currentItem, safeIndex, lang)
                val targetPoster = currentItem.posterUrl?.takeIf { it.startsWith("http") }?.let { sanitizeTelegramUrl(it) }
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

            data.startsWith("car_pager:") -> {
                // car_pager:{token}:{index}
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

            data.startsWith("car_pnav:") -> {
                // car_pnav:{token}:{newIndex}
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

            data.startsWith("car_prand:") -> {
                // car_prand:{token}
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

            data.startsWith("car_list:") -> {
                // car_list:{token}:{index}
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
                val extraRows = buildExtraRowsForContext(ref, lang)
                val keyboard = buildCarouselKeyboard(
                    carouselToken = token,
                    items = ref.items,
                    currentIndex = safeIndex,
                    contextType = ref.contextType,
                    lang = lang,
                    extraRows = extraRows
                )
                val caption = buildListCaptionForContext(ref, currentItem, safeIndex, lang)
                val targetPoster = currentItem.posterUrl?.takeIf { it.startsWith("http") }?.let { sanitizeTelegramUrl(it) }
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

            data.startsWith("car_album:") -> {
                // car_album:{token}
                val token = data.removePrefix("car_album:")
                val ref = CallbackTokenCache.get<MediaCarouselRef>(token)
                if (ref == null) {
                    bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
                    return
                }

                val validPhotos = ref.items
                    .filter { !it.posterUrl.isNullOrBlank() && it.posterUrl.startsWith("http") }
                    .take(10)
                    .mapIndexed { idx, item ->
                        val safePoster = sanitizeTelegramUrl(item.posterUrl!!) ?: item.posterUrl!!
                        val yearStr = item.year?.let { " ($it)" } ?: ""
                        InputMediaPhoto(
                            media = safePoster,
                            caption = "${idx + 1}. 🎬 ${item.name}$yearStr",
                            parseMode = null
                        )
                    }

                if (validPhotos.isEmpty()) {
                    bot.answerCallbackQuery(callback.id, if (lang == "fa") "تصویری یافت نشد" else "No images found", showAlert = true)
                    return
                }

                bot.answerCallbackQuery(callback.id, if (lang == "fa") "در حال ارسال آلبوم تصاویر..." else "Sending photo album...")
                val success = bot.sendMediaGroup(chatId, validPhotos)
                if (success) {
                    val promptText = t("album_sent_success", lang)
                    val pagerKb = InlineKeyboardMarkup(
                        listOf(
                            listOf(
                                InlineKeyboardButton(
                                    text = "${t("btn_image_pager", lang)} (${ref.items.size})",
                                    callbackData = "car_pager:$token:0"
                                )
                            )
                        )
                    )
                    bot.sendMessage(chatId, promptText, replyMarkup = pagerKb)
                } else {
                    val fallbackNotice = t("album_fallback_pager", lang)
                    bot.sendMessage(chatId, fallbackNotice)
                    showPagerScreen(chatId, null, ref, token, ref.currentIndex, lang, userId)
                }
            }

            data.startsWith("v:") -> {
                // v:{token}
                val token = data.removePrefix("v:")
                val ref = CallbackTokenCache.get<MediaRef>(token)
                if (ref == null) {
                    bot.answerCallbackQuery(callback.id, "Session expired. Please search again.", showAlert = true)
                    return
                }

                bot.answerCallbackQuery(callback.id, t("resolving_links", lang).take(40))
                val details = ProviderManager.load(ref.provider, ref.url)
                if (details == null) {
                    bot.sendMessage(chatId, "⚠️ Could not load media details.")
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
                val rawPageUrl = details.url.takeIf { it.startsWith("http") } ?: ref.url.takeIf { it.startsWith("http") }
                val pageUrl = sanitizeTelegramUrl(rawPageUrl)
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

            data.startsWith("eps:") -> {
                // eps:{token}:{page}
                val parts = data.removePrefix("eps:").split(":")
                val token = parts.getOrNull(0) ?: return
                val page = parts.getOrNull(1)?.toIntOrNull() ?: 0

                val ref = CallbackTokenCache.get<MediaRef>(token)
                if (ref == null) {
                    bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
                    return
                }

                val details = ProviderManager.load(ref.provider, ref.url) ?: return
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

            data.startsWith("q:") -> {
                // Quality and link resolution
                val epToken = data.removePrefix("q:")
                val epRef = CallbackTokenCache.get<EpisodeRef>(epToken)
                if (epRef == null) {
                    bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
                    return
                }

                bot.answerCallbackQuery(callback.id, t("resolving_links", lang).take(40))
                val links = ProviderManager.loadLinks(epRef.provider, epRef.episodeData)

                if (links.isEmpty()) {
                    val seriesRef = CallbackTokenCache.get<MediaRef>(epRef.seriesRefToken)
                    val rawWebUrl = epRef.episodeData.takeIf { it.startsWith("http") }
                        ?: seriesRef?.url?.takeIf { it.startsWith("http") }
                    val webUrl = sanitizeTelegramUrl(rawWebUrl)
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
                    bot.sendMessage(
                        chatId,
                        t("no_links_open_web", lang),
                        replyMarkup = InlineKeyboardMarkup(fallbackButtons)
                    )
                    return
                }

                // Sort links: highest quality first
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

                // In-App Web Video Player button (Telegram Mini App)
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
                    val cleanLink = sanitizeTelegramUrl(link.url) ?: link.url

                    buttons.add(
                        listOf(
                            InlineKeyboardButton(
                                text = label,
                                url = cleanLink
                            )
                        )
                    )
                }

                // Website URL button
                val rawWebUrl = epRef.episodeData.takeIf { it.startsWith("http") }
                    ?: seriesRef?.url?.takeIf { it.startsWith("http") }
                val webUrl = sanitizeTelegramUrl(rawWebUrl)
                if (!webUrl.isNullOrBlank()) {
                    buttons.add(
                        listOf(
                            InlineKeyboardButton(
                                text = t("btn_open_website", lang),
                                url = webUrl
                            )
                        )
                    )
                }

                // Back button: return to episodes page (for series) or media card (for movie)
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

                bot.sendMessage(
                    chatId,
                    t("quality_selection", lang, epRef.episodeTitle),
                    replyMarkup = InlineKeyboardMarkup(buttons)
                )
            }

            data.startsWith("bm:") -> {
                val token = data.removePrefix("bm:")
                val ref = CallbackTokenCache.get<MediaRef>(token)
                if (ref != null) {
                    val details = ProviderManager.load(ref.provider, ref.url)
                    val title = details?.name ?: "Media"
                    val poster = details?.posterUrl ?: ""
                    Database.addBookmark(userId, ref.provider, ref.url, title, poster)
                    bot.answerCallbackQuery(callback.id, t("bookmarked", lang), showAlert = true)
                }
            }

            data.startsWith("unbm:") -> {
                val token = data.removePrefix("unbm:")
                val ref = CallbackTokenCache.get<MediaRef>(token)
                if (ref != null) {
                    Database.removeBookmark(userId, ref.url)
                    bot.answerCallbackQuery(callback.id, t("unbookmarked", lang), showAlert = true)
                }
            }

            data == "menu:random" -> {
                showRandomPick(chatId, userId, lang, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:categories" -> {
                showCategories(chatId, userId, lang, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:history" -> {
                showWatchHistory(chatId, userId, lang, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "history:clear" -> {
                Database.clearWatchHistory(userId)
                bot.answerCallbackQuery(callback.id, if (lang == "fa") "تاریخچه پاک شد" else "History cleared", showAlert = true)
                showWatchHistory(chatId, userId, lang, messageId = messageId)
            }

            data.startsWith("resume:") -> {
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
                        val rawPageUrl = details.url.takeIf { it.startsWith("http") } ?: historyItem.mediaUrl.takeIf { it.startsWith("http") }
                        val pageUrl = sanitizeTelegramUrl(rawPageUrl)
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

            data.startsWith("cat_pick:") -> {
                val token = data.removePrefix("cat_pick:")
                val catRef = CallbackTokenCache.get<CategorySectionRef>(token)
                if (catRef != null) {
                    bot.answerCallbackQuery(callback.id)
                    showCategoryItems(chatId, userId, lang, catRef.provider, catRef.sectionName, catRef.page, messageId = messageId)
                } else {
                    bot.answerCallbackQuery(callback.id, "Session expired", showAlert = true)
                }
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
                listOf(
                    InlineKeyboardButton(text = t("btn_repos", lang), callbackData = "admin:repos"),
                    InlineKeyboardButton(text = t("btn_sync", lang), callbackData = "menu:sync")
                ),
                listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close"))
            )
        )

        if (messageId != null) {
            val edited = bot.editMessageText(chatId, messageId, stats, replyMarkup = keyboard)
            if (!edited) bot.sendMessage(chatId, stats, replyMarkup = keyboard)
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
            val edited = bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
            if (!edited) bot.sendMessage(chatId, text, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, text, replyMarkup = keyboard)
        }
    }

    private suspend fun showBookmarks(chatId: Long, userId: Long, lang: String, messageId: Long? = null) {
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
            val edited = bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
            if (!edited) bot.sendMessage(chatId, text, replyMarkup = keyboard)
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
            val edited = bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
            if (!edited) bot.sendMessage(chatId, text, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, text, replyMarkup = keyboard)
        }
    }
}
