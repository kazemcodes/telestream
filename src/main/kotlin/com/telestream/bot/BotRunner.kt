package com.telestream.bot

import com.lagradost.cloudstream3.*
import com.telestream.config.Config
import com.telestream.database.Database
import com.telestream.i18n.I18n.t
import com.telestream.providers.ProviderManager
import com.telestream.providers.episodes
import com.telestream.providers.year
import com.telestream.repo.CloudStreamRepoManager
import com.telestream.telegram.CallbackQuery
import com.telestream.telegram.InlineKeyboardButton
import com.telestream.telegram.InlineKeyboardMarkup
import com.telestream.telegram.Message
import com.telestream.telegram.TelegramClient
import com.telestream.telegram.WebAppInfo
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

class BotRunner(private val bot: TelegramClient) {
    private val logger = LoggerFactory.getLogger(BotRunner::class.java)
    private var isRunning = true
    private val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Multi-user debounce cache to prevent rapid double-clicks
    private val userLastAction = ConcurrentHashMap<Long, Long>()

    // Tracks if user explicitly selected a source for their upcoming text input
    private val userSearchPending = ConcurrentHashMap<Long, String>()

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

    private fun getMainMenuKeyboard(lang: String, activeSource: String): InlineKeyboardMarkup {
        val rows = mutableListOf<List<InlineKeyboardButton>>()
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
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_popular", lang), callbackData = "feed:popular"),
                InlineKeyboardButton(text = t("btn_latest", lang), callbackData = "feed:latest")
            )
        )
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_search", lang), callbackData = "menu:search"),
                InlineKeyboardButton(text = t("btn_sources", lang), callbackData = "menu:sources")
            )
        )
        rows.add(
            listOf(
                InlineKeyboardButton(text = "📡 $activeSource (${t("btn_change_source", lang)})", callbackData = "menu:sources")
            )
        )
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_enabled_sources", lang), callbackData = "menu:enabled_sources"),
                InlineKeyboardButton(text = t("btn_manage_sources", lang), callbackData = "menu:manage_sources")
            )
        )
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_bookmarks", lang), callbackData = "menu:bookmarks"),
                InlineKeyboardButton(text = t("btn_repos", lang), callbackData = "menu:repos")
            )
        )
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_donate", lang), callbackData = "menu:donate"),
                InlineKeyboardButton(text = t("btn_lang", lang), callbackData = "menu:lang")
            )
        )
        return InlineKeyboardMarkup(rows)
    }

    private suspend fun handleMessage(message: Message) {
        val chatId = message.chat.id
        val text = message.text?.trim() ?: return
        val userId = message.from?.id ?: chatId
        val lang = Database.getUserLanguage(userId)
        val activeSource = Database.getUserSource(userId)

        when {
            text.startsWith("/start") -> {
                userSearchPending.remove(userId)
                bot.sendMessage(chatId, t("welcome", lang), replyMarkup = getMainMenuKeyboard(lang, activeSource))
            }

            text.startsWith("/ping") -> {
                bot.sendMessage(chatId, "🏓 *Pong!* TeleStream bot is online.")
            }

            text.startsWith("/popular") -> {
                showFeedScreen(chatId, userId, lang, "popular")
            }

            text.startsWith("/latest") -> {
                showFeedScreen(chatId, userId, lang, "latest")
            }

            text.startsWith("/sources") -> {
                showSourcesMenu(chatId, userId, lang)
            }

            text.startsWith("/manage_sources") -> {
                showManageSourcesStep1(chatId, userId, lang)
            }

            text.startsWith("/enabled_sources") -> {
                showEnabledSourcesScreen(chatId, userId, lang)
            }

            text.startsWith("/search") -> {
                val query = text.removePrefix("/search").trim()
                if (query.isBlank()) {
                    // Prompt user to choose which source to search in first
                    promptPickSourceToSearch(chatId, userId, lang)
                } else {
                    // User supplied a query: prompt to choose source for this query
                    promptChooseSourceForQuery(chatId, userId, lang, query)
                }
            }

            text.startsWith("/app") -> {
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

            text.startsWith("/donate") -> {
                showDonationMessage(chatId, lang)
            }

            text.startsWith("/admin") -> {
                if (!Config.isAdmin(userId)) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                } else {
                    showAdminDashboard(chatId, lang)
                }
            }

            text.startsWith("/nsfw") -> {
                if (!Config.isAdmin(userId)) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                    return
                }
                val arg = text.removePrefix("/nsfw").trim().lowercase()
                val newStatus = when (arg) {
                    "on", "enable", "1", "true" -> true
                    "off", "disable", "0", "false" -> false
                    else -> !Database.isNsfwEnabled()
                }
                Database.setNsfwEnabled(newStatus)
                val statusStr = if (newStatus) t("nsfw_on", lang) else t("nsfw_off", lang)
                bot.sendMessage(chatId, t("nsfw_toggled", lang, statusStr))
            }

            text.startsWith("/repos") -> {
                showReposSummary(chatId, lang)
            }

            text.startsWith("/sync") -> {
                if (!Config.isAdmin(userId)) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                    return
                }
                bot.sendMessage(chatId, t("syncing", lang))
                val repos = CloudStreamRepoManager.syncAllDefaults()
                val totalPlugins = repos.sumOf { it.pluginsCount }
                bot.sendMessage(chatId, t("sync_done", lang, totalPlugins, repos.size))
            }

            text.startsWith("/addrepo") -> {
                if (!Config.isAdmin(userId)) {
                    bot.sendMessage(chatId, t("admin_only", lang))
                    return
                }
                val url = text.removePrefix("/addrepo").trim()
                if (url.isBlank()) {
                    bot.sendMessage(chatId, "⚠️ Usage: `/addrepo <url>` (e.g., `https://example.com/repo.json`)")
                } else {
                    bot.sendMessage(chatId, "⏳ Fetching repository from `$url`...")
                    val repo = CloudStreamRepoManager.fetchRepository(url)
                    if (repo != null) {
                        bot.sendMessage(chatId, "✅ Added repository *${repo.name}* with *${repo.pluginsCount}* plugins!")
                    } else {
                        bot.sendMessage(chatId, "❌ Failed to fetch repository from `$url`.")
                    }
                }
            }

            text.startsWith("/language") -> {
                showLanguageSelector(chatId, lang)
            }

            text.startsWith("/bookmarks") -> {
                showBookmarks(chatId, userId, lang)
            }

            else -> {
                // User sent title text directly
                val pendingSource = userSearchPending.remove(userId)
                if (pendingSource != null) {
                    // User had explicitly selected a source just before typing
                    executeSearch(chatId, userId, lang, pendingSource, text)
                } else {
                    // Let user select which source to search in for this query
                    promptChooseSourceForQuery(chatId, userId, lang, text)
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
            val keyboard = InlineKeyboardMarkup(
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
            )
            bot.sendMessage(chatId, t("no_results_in_source", lang, sourceName, query), replyMarkup = keyboard)
            return
        }

        val buttons = results.take(8).map { item ->
            val icon = if (item.type == TvType.Movie) "🎬" else "📺"
            val yearStr = item.year?.let { " ($it)" } ?: ""
            val token = CallbackTokenCache.put(MediaRef(item.apiName, item.url))
            listOf(
                InlineKeyboardButton(
                    text = "$icon ${item.name}$yearStr",
                    callbackData = "v:$token"
                )
            )
        }.toMutableList()

        buttons.add(
            listOf(
                InlineKeyboardButton(
                    text = t("btn_search_another_source", lang),
                    callbackData = "src_another:$queryToken"
                )
            )
        )
        buttons.add(
            listOf(
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        bot.sendMessage(
            chatId,
            t("search_results", lang, query) + "\n(📡 $sourceName)",
            replyMarkup = InlineKeyboardMarkup(buttons)
        )
    }

    private suspend fun showSourcesMenu(
        chatId: Long,
        userId: Long,
        lang: String,
        messageId: Long? = null
    ) {
        val activeSource = Database.getUserSource(userId)
        val enabledSources = Database.getEnabledSources(userId)

        val rows = mutableListOf<List<InlineKeyboardButton>>()

        if (enabledSources.isEmpty()) {
            val msgText = t("no_enabled_sources", lang)
            val kb = InlineKeyboardMarkup(
                listOf(
                    listOf(InlineKeyboardButton(text = t("btn_manage_sources", lang), callbackData = "menu:manage_sources")),
                    listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close"))
                )
            )
            if (messageId != null) {
                val ed = bot.editMessageText(chatId, messageId, msgText, replyMarkup = kb)
                if (!ed) bot.sendMessage(chatId, msgText, replyMarkup = kb)
            } else {
                bot.sendMessage(chatId, msgText, replyMarkup = kb)
            }
            return
        }

        // List all enabled sources cleanly
        for (src in enabledSources) {
            val isCurrent = src.equals(activeSource, ignoreCase = true) ||
                    src.startsWith(activeSource, ignoreCase = true) ||
                    activeSource.startsWith(src, ignoreCase = true)
            val icon = if (isCurrent) "🔘 " else "📡 "
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = "$icon$src",
                        callbackData = "setsource:$src"
                    )
                )
            )
        }

        // Navigation row
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_manage_sources", lang), callbackData = "menu:manage_sources"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val keyboard = InlineKeyboardMarkup(rows)
        val text = t("choose_source", lang, activeSource)

        if (messageId != null) {
            val edited = bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
            if (!edited) {
                bot.sendMessage(chatId, text, replyMarkup = keyboard)
            }
        } else {
            bot.sendMessage(chatId, text, replyMarkup = keyboard)
        }
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
            val emptyText = "$headerText\n\n${t("feed_no_items", lang)}"
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
                    InlineKeyboardButton(text = t("btn_switch_source", lang), callbackData = "feed_picksrc:$feedType"),
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

        for (item in items.take(8)) {
            val icon = if (item.type == TvType.Movie) "🎬" else "📺"
            val yearStr = item.year?.let { " ($it)" } ?: ""
            val token = CallbackTokenCache.put(MediaRef(activeSource, item.url))
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = "$icon ${item.name}$yearStr",
                        callbackData = "v:$token"
                    )
                )
            )
        }

        // Toggle row: Popular vs Latest
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

        // Switch source and Back buttons
        rows.add(
            listOf(
                InlineKeyboardButton(text = "${t("btn_switch_source", lang)} ($activeSource)", callbackData = "feed_picksrc:$feedType"),
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

    private suspend fun showEnabledSourcesScreen(
        chatId: Long,
        userId: Long,
        lang: String,
        messageId: Long? = null
    ) {
        val enabled = Database.getEnabledSources(userId)
        val activeSource = Database.getUserSource(userId)

        val rows = mutableListOf<List<InlineKeyboardButton>>()

        if (enabled.isEmpty()) {
            val msgText = t("no_enabled_sources", lang)
            val kb = InlineKeyboardMarkup(
                listOf(
                    listOf(InlineKeyboardButton(text = t("btn_manage_sources", lang), callbackData = "menu:manage_sources")),
                    listOf(InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close"))
                )
            )
            if (messageId != null) {
                val ed = bot.editMessageText(chatId, messageId, msgText, replyMarkup = kb)
                if (!ed) bot.sendMessage(chatId, msgText, replyMarkup = kb)
            } else {
                bot.sendMessage(chatId, msgText, replyMarkup = kb)
            }
            return
        }

        // List all enabled sources; clicking sets it as active search source
        for (src in enabled) {
            val isActive = src.equals(activeSource, ignoreCase = true) ||
                    src.startsWith(activeSource, ignoreCase = true) ||
                    activeSource.startsWith(src, ignoreCase = true)
            val label = if (isActive) "🔘 $src [Active]" else "✅ $src"
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = label,
                        callbackData = "setsource:$src"
                    )
                )
            )
        }

        // Action controls
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_manage_sources", lang), callbackData = "menu:manage_sources"),
                InlineKeyboardButton(text = t("btn_sources", lang), callbackData = "menu:sources")
            )
        )
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_search", lang), callbackData = "menu:search"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val text = t("enabled_sources_title", lang, enabled.size)
        val keyboard = InlineKeyboardMarkup(rows)

        if (messageId != null) {
            val ed = bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
            if (!ed) bot.sendMessage(chatId, text, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, text, replyMarkup = keyboard)
        }
    }

    private suspend fun showManageSourcesStep1(
        chatId: Long,
        userId: Long,
        lang: String,
        messageId: Long? = null
    ) {
        val repos = CloudStreamRepoManager.getRepositoryNames()
        val rows = mutableListOf<List<InlineKeyboardButton>>()

        for (repo in repos) {
            val count = CloudStreamRepoManager.getPluginsForRepo(repo).size
            val icon = if (repo.contains("built-in", ignoreCase = true)) "⭐" else "📦"
            val token = CallbackTokenCache.put(repo)
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = "$icon $repo ($count)",
                        callbackData = "ms_r:$token"
                    )
                )
            )
        }

        // Navigation
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_enabled_sources", lang), callbackData = "menu:enabled_sources"),
                InlineKeyboardButton(text = t("btn_sources", lang), callbackData = "menu:sources")
            )
        )
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val text = t("step_choose_provider", lang)
        val keyboard = InlineKeyboardMarkup(rows)

        if (messageId != null) {
            val ed = bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
            if (!ed) bot.sendMessage(chatId, text, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, text, replyMarkup = keyboard)
        }
    }

    private suspend fun showManageSourcesStep2(
        chatId: Long,
        userId: Long,
        lang: String,
        repoName: String,
        messageId: Long? = null
    ) {
        val languages = CloudStreamRepoManager.getLanguagesForRepo(repoName)
        val rows = mutableListOf<List<InlineKeyboardButton>>()

        val langButtons = languages.map { l ->
            val label = if (l == "all") t("all_languages", lang) else {
                when (l.lowercase()) {
                    "en" -> "🇬🇧 English"
                    "fa" -> "🇮🇷 فارسی"
                    "ar" -> "🇸🇦 العربية"
                    "mx" -> "🇲🇽 Español"
                    "hi" -> "🇮🇳 Hindi"
                    "fr" -> "🇫🇷 Français"
                    "it" -> "🇮🇹 Italiano"
                    "de" -> "🇩🇪 Deutsch"
                    "ru" -> "🇷🇺 Russian"
                    "zh" -> "🇨🇳 Chinese"
                    "id" -> "🇮🇩 Indonesian"
                    else -> l.uppercase()
                }
            }
            val refToken = CallbackTokenCache.put(RepoPageRef(repoName, l, 0))
            InlineKeyboardButton(text = label, callbackData = "ms_l:$refToken")
        }.chunked(2)
        rows.addAll(langButtons)

        // Back to Step 1 & Close
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_back", lang), callbackData = "menu:manage_sources"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val text = t("step_choose_lang", lang, repoName)
        val keyboard = InlineKeyboardMarkup(rows)

        if (messageId != null) {
            val ed = bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
            if (!ed) bot.sendMessage(chatId, text, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, text, replyMarkup = keyboard)
        }
    }

    private suspend fun showManageSourcesStep3(
        chatId: Long,
        userId: Long,
        lang: String,
        repoName: String,
        filterLang: String,
        page: Int,
        messageId: Long? = null
    ) {
        val plugins = CloudStreamRepoManager.getPluginsForRepo(repoName, filterLang)
        val pageSize = 8
        val totalPages = max(1, (plugins.size + pageSize - 1) / pageSize)
        val safePage = page.coerceIn(0, totalPages - 1)
        val pagePlugins = plugins.drop(safePage * pageSize).take(pageSize)

        val rows = mutableListOf<List<InlineKeyboardButton>>()

        // Bulk action row
        val pageRefToken = CallbackTokenCache.put(RepoPageRef(repoName, filterLang, safePage))
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_enable_all", lang), callbackData = "ms_b:$pageRefToken:1"),
                InlineKeyboardButton(text = t("btn_disable_all", lang), callbackData = "ms_b:$pageRefToken:0")
            )
        )

        // Plugin toggle buttons (2 columns)
        val itemButtons = pagePlugins.map { p ->
            val isEnabled = Database.isSourceEnabled(userId, p.name)
            val icon = if (isEnabled) "✅" else "❌"
            val token = CallbackTokenCache.put(SourceToggleRef(repoName, filterLang, safePage, p.name))
            InlineKeyboardButton(
                text = "$icon ${p.name.take(18)}",
                callbackData = "ms_t:$token"
            )
        }.chunked(2)
        rows.addAll(itemButtons)

        // Pagination row if > 1 page
        if (totalPages > 1) {
            val navRow = mutableListOf<InlineKeyboardButton>()
            if (safePage > 0) {
                val prevToken = CallbackTokenCache.put(RepoPageRef(repoName, filterLang, safePage - 1))
                navRow.add(InlineKeyboardButton(text = t("btn_prev", lang), callbackData = "ms_l:$prevToken"))
            }
            navRow.add(InlineKeyboardButton(text = "📄 ${safePage + 1}/$totalPages", callbackData = "noop"))
            if (safePage < totalPages - 1) {
                val nextToken = CallbackTokenCache.put(RepoPageRef(repoName, filterLang, safePage + 1))
                navRow.add(InlineKeyboardButton(text = t("btn_next", lang), callbackData = "ms_l:$nextToken"))
            }
            rows.add(navRow)
        }

        // Navigation back row
        val repoToken = CallbackTokenCache.put(repoName)
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_back", lang), callbackData = "ms_r:$repoToken"),
                InlineKeyboardButton(text = t("btn_enabled_sources", lang), callbackData = "menu:enabled_sources"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )

        val text = t("manage_sources_list", lang, repoName, filterLang)
        val keyboard = InlineKeyboardMarkup(rows)

        if (messageId != null) {
            val ed = bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
            if (!ed) bot.sendMessage(chatId, text, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, text, replyMarkup = keyboard)
        }
    }

    private suspend fun handleCallback(callback: CallbackQuery) {
        val chatId = callback.message?.chat?.id ?: return
        val messageId = callback.message.messageId
        val data = callback.data ?: return
        val userId = callback.from.id
        val lang = Database.getUserLanguage(userId)

        if (isDebounced(userId) && !data.startsWith("setlang:") && !data.startsWith("setsource:") && !data.startsWith("ms_t:") && data != "close" && data != "noop") {
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
                bot.editMessageText(
                    chatId,
                    messageId,
                    t("welcome", newLang),
                    replyMarkup = getMainMenuKeyboard(newLang, activeSource)
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

            data == "menu:manage_sources" -> {
                showManageSourcesStep1(chatId, userId, lang, messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data == "menu:enabled_sources" -> {
                showEnabledSourcesScreen(chatId, userId, lang, messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("ms_r:") -> {
                val token = data.removePrefix("ms_r:")
                val repoName = CallbackTokenCache.get<String>(token) ?: "Built-in Sources"
                showManageSourcesStep2(chatId, userId, lang, repoName, messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("ms_l:") -> {
                val token = data.removePrefix("ms_l:")
                val ref = CallbackTokenCache.get<RepoPageRef>(token)
                if (ref != null) {
                    showManageSourcesStep3(chatId, userId, lang, ref.repoName, ref.lang, ref.page, messageId)
                }
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("ms_t:") -> {
                val token = data.removePrefix("ms_t:")
                val ref = CallbackTokenCache.get<SourceToggleRef>(token)
                if (ref != null) {
                    val newStatus = Database.toggleSourceEnabled(userId, ref.sourceName)
                    val toast = if (newStatus) t("source_toggled_on", lang, ref.sourceName) else t("source_toggled_off", lang, ref.sourceName)
                    bot.answerCallbackQuery(callback.id, toast)
                    showManageSourcesStep3(chatId, userId, lang, ref.repoName, ref.lang, ref.page, messageId)
                } else {
                    bot.answerCallbackQuery(callback.id, "Session expired")
                }
            }

            data.startsWith("ms_b:") -> {
                val parts = data.removePrefix("ms_b:").split(":")
                val refToken = parts.getOrNull(0) ?: ""
                val enable = parts.getOrNull(1) == "1"
                val ref = CallbackTokenCache.get<RepoPageRef>(refToken)
                if (ref != null) {
                    val plugins = CloudStreamRepoManager.getPluginsForRepo(ref.repoName, ref.lang)
                    val pageSize = 8
                    val pagePlugins = plugins.drop(ref.page * pageSize).take(pageSize)
                    Database.setSourcesBulk(userId, pagePlugins.map { it.name }, enable)
                    val toast = if (enable) t("bulk_enabled", lang, pagePlugins.size) else t("bulk_disabled", lang, pagePlugins.size)
                    bot.answerCallbackQuery(callback.id, toast, showAlert = true)
                    showManageSourcesStep3(chatId, userId, lang, ref.repoName, ref.lang, ref.page, messageId)
                } else {
                    bot.answerCallbackQuery(callback.id, "Session expired")
                }
            }

            data.startsWith("filter:") -> {
                showSourcesMenu(chatId, userId, lang, messageId = messageId)
                bot.answerCallbackQuery(callback.id)
            }

            data.startsWith("setsource:") -> {
                val newSource = data.removePrefix("setsource:")
                Database.setSourceEnabled(userId, newSource, true)
                Database.setUserSource(userId, newSource)
                bot.answerCallbackQuery(callback.id, t("source_changed", lang, newSource), showAlert = true)
                showSourcesMenu(chatId, userId, lang, messageId = messageId)
            }

            data == "menu:search" -> {
                // Prompt user to select source first
                promptPickSourceToSearch(chatId, userId, lang, messageId)
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
                    replyMarkup = getMainMenuKeyboard(lang, activeSource)
                )
                if (!edited) {
                    bot.sendMessage(chatId, t("welcome", lang), replyMarkup = getMainMenuKeyboard(lang, activeSource))
                }
                bot.answerCallbackQuery(callback.id)
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
                    val sent = bot.sendPhoto(chatId, details.posterUrl!!, caption = cardText, replyMarkup = keyboard)
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
                    bot.sendMessage(chatId, t("no_links", lang))
                    return
                }

                // Sort links: highest quality first
                val sortedLinks = links.sortedByDescending { it.quality }
                val buttons = mutableListOf<List<InlineKeyboardButton>>()

                for (link in sortedLinks.take(10)) {
                    val icon = if (link.isM3u8) "📡" else "📥"
                    val typeDesc = if (link.isM3u8) "Stream" else "Direct"
                    val qualityDesc = if (link.quality > 0) "${link.quality}p" else "Auto"
                    val label = "$icon [$qualityDesc] ${link.name.take(18)} ($typeDesc)"

                    buttons.add(
                        listOf(
                            InlineKeyboardButton(
                                text = label,
                                url = link.url
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
                listOf(InlineKeyboardButton(text = t("btn_sync", lang), callbackData = "menu:sync")),
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
