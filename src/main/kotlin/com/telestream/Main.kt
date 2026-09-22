package com.telestream

import com.lagradost.cloudstream3.mapper
import com.telestream.bot.BotRunner
import com.telestream.config.Config
import com.telestream.database.Database
import com.telestream.providers.ProviderManager
import com.telestream.telegram.MenuButton
import com.telestream.telegram.TelegramClient
import com.telestream.telegram.WebAppInfo
import com.telestream.webapp.WebAppHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

fun main(): Unit = runBlocking {
    val logger = LoggerFactory.getLogger("Main")

    val botToken = Config.botToken
    val port = Config.port
    val webAppUrl = Config.webAppUrl

    logger.info("=====================================================")
    logger.info("🎬 Starting TeleStream Telegram Bot (Pure Kotlin JVM)")
    logger.info("👉 Active Providers: ${ProviderManager.providers.joinToString { it.name }}")
    logger.info("👉 Web Health Server: http://0.0.0.0:$port/health")
    logger.info("👉 Mini App WebUI: $webAppUrl")
    logger.info("=====================================================")

    // Start embedded Ktor web server for Telegram Mini App & API
    val server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
        routing {
            // Telegram Mini App HTML SPA
            get("/") {
                call.respondText(WebAppHtml.renderHtml(), ContentType.Text.Html)
            }
            get("/webapp") {
                call.respondText(WebAppHtml.renderHtml(), ContentType.Text.Html)
            }

            // Health check
            get("/health") {
                call.respondText(
                    """{"status":"ok","engine":"pure-jvm-cloudstream"}""",
                    ContentType.Application.Json
                )
            }

            // CORS preflight
            options("{...}") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                call.response.headers.append("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                call.response.headers.append("Access-Control-Allow-Headers", "*")
                call.respond(HttpStatusCode.OK)
            }

            // API: App & Wallet Config
            get("/api/config") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val map = mapOf(
                    "webAppUrl" to Config.webAppUrl,
                    "usdtTrc20" to Config.usdtTrc20,
                    "tonWallet" to Config.tonWallet,
                    "btcWallet" to Config.btcWallet,
                    "ethWallet" to Config.ethWallet,
                    "activeProviders" to ProviderManager.providers.map { it.name }
                )
                call.respondText(mapper.writeValueAsString(map), ContentType.Application.Json)
            }

            // API: Real-time Search
            get("/api/search") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val query = call.request.queryParameters["q"] ?: ""
                if (query.isBlank()) {
                    call.respondText("[]", ContentType.Application.Json)
                    return@get
                }
                val results = ProviderManager.search(query)
                call.respondText(mapper.writeValueAsString(results), ContentType.Application.Json)
            }

            // API: Load Details & Episodes
            get("/api/load") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val provider = call.request.queryParameters["provider"] ?: ""
                val url = call.request.queryParameters["url"] ?: ""
                val details = ProviderManager.load(provider, url)
                if (details != null) {
                    call.respondText(mapper.writeValueAsString(details), ContentType.Application.Json)
                } else {
                    call.respondText("""{"error":"Not found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            // API: Resolve Stream & Download Links
            get("/api/links") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val provider = call.request.queryParameters["provider"] ?: ""
                val data = call.request.queryParameters["data"] ?: ""
                val links = ProviderManager.loadLinks(provider, data)
                call.respondText(mapper.writeValueAsString(links), ContentType.Application.Json)
            }

            // API: Bookmarks
            get("/api/bookmarks") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val userId = call.request.queryParameters["userId"]?.toLongOrNull() ?: 0L
                val bookmarks = Database.getBookmarks(userId)
                call.respondText(mapper.writeValueAsString(bookmarks), ContentType.Application.Json)
            }
        }
    }.start(wait = false)

    if (botToken.isNullOrBlank() || botToken == "YOUR_BOT_TOKEN") {
        logger.warn("⚠️ BOT_TOKEN environment variable is not set!")
        logger.warn("⚠️ Bot polling paused. Please set BOT_TOKEN in your environment or .env file.")
        logger.info("⚠️ Embedded web server is running on port $port for container health checks.")

        // Keep process alive for health check
        while (true) {
            delay(10000)
        }
    } else {
        logger.info("Connecting Telegram client...")
        val client = TelegramClient(botToken)
        val runner = BotRunner(client)

        // Register Telegram Menu Button to open Mini App if public HTTPS URL available
        if (webAppUrl.startsWith("https://")) {
            launch {
                try {
                    val ok = client.setChatMenuButton(
                        MenuButton(
                            type = "web_app",
                            text = "🎬 TeleStream",
                            webApp = WebAppInfo(webAppUrl)
                        )
                    )
                    if (ok) {
                        logger.info("👉 Registered Telegram Mini App Menu Button: $webAppUrl")
                    }
                } catch (e: Exception) {
                    logger.debug("Failed setting chat menu button: ${e.message}")
                }
            }
        }

        launch {
            runner.startPolling()
        }
    }
}
