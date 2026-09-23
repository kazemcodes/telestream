package com.telestream.bot

import com.telestream.bot.handlers.*
import com.telestream.bot.model.*
import com.telestream.bot.state.BotStateManager
import com.telestream.bot.util.UrlSanitizer
import com.telestream.telegram.TelegramClient
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

// Forwarding aliases for backward compatibility
typealias CallbackTokenCache = com.telestream.bot.model.CallbackTokenCache
typealias MediaRef = com.telestream.bot.model.MediaRef
typealias EpisodeRef = com.telestream.bot.model.EpisodeRef
typealias SourceToggleRef = com.telestream.bot.model.SourceToggleRef
typealias RepoPageRef = com.telestream.bot.model.RepoPageRef
typealias SearchExecRef = com.telestream.bot.model.SearchExecRef
typealias SourceBrowserRef = com.telestream.bot.model.SourceBrowserRef
typealias SourceActionRef = com.telestream.bot.model.SourceActionRef
typealias CategorySectionRef = com.telestream.bot.model.CategorySectionRef
typealias MediaItemSummary = com.telestream.bot.model.MediaItemSummary
typealias MediaCarouselRef = com.telestream.bot.model.MediaCarouselRef

fun sanitizeTelegramUrl(rawUrl: String?): String? = UrlSanitizer.sanitizeTelegramUrl(rawUrl)
fun resolveWebUrl(rawUrl: String?, providerName: String? = null, title: String? = null): String? =
    UrlSanitizer.resolveWebUrl(rawUrl, providerName, title)

/**
 * High-level bot coordinator and lifecycle manager.
 * Delegates message commands, callback queries, and inline queries
 * to dedicated domain handlers adhering to Clean Architecture principles.
 */
class BotRunner(private val bot: TelegramClient) {
    private val logger = LoggerFactory.getLogger(BotRunner::class.java)
    private var isRunning = true
    private val workerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val stateManager = BotStateManager()
    val adminHandler = AdminHandler(bot)
    val sourcesHandler = SourcesHandler(bot)
    val carouselHandler = CarouselHandler(bot)
    val feedHandler = FeedHandler(bot, stateManager)
    val searchHandler = SearchHandler(bot, stateManager)
    val mediaHandler = MediaHandler(bot, stateManager)
    val inlineQueryHandler = InlineQueryHandler(bot)
    val commandHandler = CommandHandler(
        bot = bot,
        stateManager = stateManager,
        searchHandler = searchHandler,
        sourcesHandler = sourcesHandler,
        feedHandler = feedHandler,
        mediaHandler = mediaHandler,
        adminHandler = adminHandler
    )
    val callbackRouter = CallbackRouter(
        bot = bot,
        stateManager = stateManager,
        mediaHandler = mediaHandler,
        searchHandler = searchHandler,
        carouselHandler = carouselHandler,
        feedHandler = feedHandler,
        sourcesHandler = sourcesHandler,
        adminHandler = adminHandler
    )

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
                                commandHandler.handleMessage(update.message)
                            } else if (update.callbackQuery != null) {
                                callbackRouter.handleCallback(update.callbackQuery)
                            } else if (update.inlineQuery != null) {
                                inlineQueryHandler.handleInlineQuery(update.inlineQuery)
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
}
