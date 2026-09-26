package com.telestream

import com.lagradost.cloudstream3.mapper
import com.telestream.bot.BotRunner
import com.telestream.config.Config
import com.telestream.database.Database
import com.telestream.providers.ProviderManager
import com.telestream.repo.CloudStreamRepoManager
import com.telestream.telegram.BotCommand
import com.telestream.telegram.MenuButton
import com.telestream.telegram.TelegramClient
import com.telestream.telegram.WebAppInfo
import com.telestream.webapp.WebAppHtml
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Fails fast with an actionable message when [port] is already taken.
 *
 * `embeddedServer(...).start(wait = false)` binds on a background thread, so a port clash otherwise
 * surfaces as a bare `BindException` stack trace with no hint about who owns the port. This does a
 * cheap pre-flight bind instead, and distinguishes "another copy of this app" from "some other
 * program", because those need very different fixes.
 */
private fun checkPortAvailable(port: Int) {
    val probe = try {
        ServerSocket()
    } catch (e: IOException) {
        return // cannot probe (sandbox / restricted); let the real bind report the problem
    }
    try {
        probe.reuseAddress = false
        probe.bind(InetSocketAddress("0.0.0.0", port))
    } catch (e: IOException) {
        val owner = describePortOwner(port)
        System.err.println()
        System.err.println("❌ Cannot start: TCP port $port is already in use.")
        System.err.println()
        if (owner != null) {
            System.err.println("   Held by: $owner")
            System.err.println("   If that is a previous run of TeleStream, stop it first:")
            System.err.println("       Stop-Process -Id ${owner.substringAfter("PID ").substringBefore(")").trim()} -Force")
        } else {
            System.err.println("   Another program is using this port.")
        }
        System.err.println()
        System.err.println("   Fix it with either:")
        System.err.println("       1) stop the process above, or")
        System.err.println("       2) run on a different port:  \$env:PORT=7861; ./gradlew run")
        System.err.println()
        throw SystemExitException(1)
    } finally {
        runCatching { probe.close() }
    }
}

/** Best-effort lookup of the process holding [port], Windows-only, purely for the error message. */
private fun describePortOwner(port: Int): String? = runCatching {
    val cmd = "Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop | " +
        "Select-Object -First 1 -ExpandProperty OwningProcess"
    val procId = ProcessBuilder("powershell", "-NoProfile", "-Command", cmd)
        .redirectErrorStream(true).start()
        .let { it.inputStream.bufferedReader().use { r -> r.readText() } }
        .trim()
    if (procId.isEmpty() || procId.toIntOrNull() == null) return null
    val name = ProcessBuilder("powershell", "-NoProfile", "-Command",
        "(Get-Process -Id $procId -ErrorAction Stop).ProcessName")
        .redirectErrorStream(true).start()
        .let { it.inputStream.bufferedReader().use { r -> r.readText() } }
        .trim()
    "$name (PID $procId)"
}.getOrNull()

/** Thrown to exit `main` with a status code without dumping a stack trace. */
private class SystemExitException(val status: Int) : RuntimeException(null, null, false, false)

fun main(): Unit {
    // Disable coroutines stack trace recovery to prevent DebugMetadata version mismatch crashes with dynamic plugins
    System.setProperty("kotlinx.coroutines.stacktrace.recovery", "false")

    // Fail fast with an actionable message rather than a background BindException stack trace.
    try {
        checkPortAvailable(Config.port)
    } catch (e: SystemExitException) {
        kotlin.system.exitProcess(e.status)
    }

    runBlocking {
        val logger = LoggerFactory.getLogger("Main")

        val botToken = Config.botToken
        val port = Config.port
        val webAppUrl = Config.webAppUrl

        // Initialize networking with DoH (DNS over HTTPS) and optional scraper proxy
        com.lagradost.cloudstream3.MainActivity.initNetwork(Config.scraperProxy, Config.dnsOverHttps)

    logger.info("=====================================================")
    // Note: no emoji in these startup lines. The Kotlin compiler is emitting astral-plane characters
    // (U+1F3AC and friends) as CESU-8 surrogate pairs in the class constant pool, which is not valid
    // JVM "modified UTF-8". The bytes survive into the class file as ED A0 BC ED BE AC, are not
    // decodable, and reach the console as the familiar "ƒÄ¼" mojibake. Plain ASCII renders
    // identically on every console and code page, so the banner avoids them deliberately.
    logger.info("[*] Starting TeleStream Telegram Bot (Pure Kotlin JVM)")
    logger.info("[>] Active Providers: ${ProviderManager.providers.joinToString { it.name }}")
    logger.info("[>] DNS over HTTPS: ${if (Config.dnsOverHttps) "Enabled (Cloudflare/Google)" else "Disabled"}")
    if (Config.scraperProxy != null) {
        logger.info("[>] Scraper Proxy: ${Config.scraperProxy}")
    }
    logger.info("[>] Web Health Server: http://0.0.0.0:$port/health")
    logger.info("[>] Mini App WebUI: ${if (Config.enableWebApp) webAppUrl else "Disabled"}")
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

            // Short redirect route for long streaming URLs
            get("/r/{token}") {
                val token = call.parameters["token"]
                val targetUrl = token?.let { com.telestream.bot.model.CallbackTokenCache.get<String>(it) }
                if (!targetUrl.isNullOrBlank()) {
                    call.respondRedirect(targetUrl, permanent = false)
                } else {
                    call.respondText("Link expired or invalid", status = HttpStatusCode.NotFound)
                }
            }
            get("/webapp/r/{token}") {
                val token = call.parameters["token"]
                val targetUrl = token?.let { com.telestream.bot.model.CallbackTokenCache.get<String>(it) }
                if (!targetUrl.isNullOrBlank()) {
                    call.respondRedirect(targetUrl, permanent = false)
                } else {
                    call.respondText("Link expired or invalid", status = HttpStatusCode.NotFound)
                }
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

            // API: Sources List
            get("/api/sources") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val filter = call.request.queryParameters["filter"] ?: "all"
                val q = call.request.queryParameters["q"]?.trim()
                val all = CloudStreamRepoManager.getAggregatedSources(filter)
                val filtered = if (!q.isNullOrBlank()) {
                    all.filter {
                        it.name.contains(q, ignoreCase = true) ||
                        it.language.contains(q, ignoreCase = true) ||
                        it.description?.contains(q, ignoreCase = true) == true
                    }
                } else {
                    all
                }
                val list = filtered.map {
                    mapOf("name" to it.name, "lang" to it.language, "description" to (it.description ?: ""))
                }
                call.respondText(mapper.writeValueAsString(list), ContentType.Application.Json)
            }

            // API: Source-Specific Search
            get("/api/search") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val query = call.request.queryParameters["q"] ?: ""
                val provider = call.request.queryParameters["provider"] ?: "KissKH"
                if (query.isBlank()) {
                    call.respondText("[]", ContentType.Application.Json)
                    return@get
                }
                val results = ProviderManager.searchInProvider(provider, query)
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

            // API: Provider Categories / Main Page Sections
            get("/api/sections") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val provider = call.request.queryParameters["provider"] ?: "KissKH"
                val sections = ProviderManager.getMainPageSections(provider)
                val list = sections.map {
                    mapOf("name" to it.name, "url" to it.data, "horizontal" to it.horizontalImages)
                }
                call.respondText(mapper.writeValueAsString(list), ContentType.Application.Json)
            }

            // API: Items in a Category / Section
            get("/api/section") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val provider = call.request.queryParameters["provider"] ?: "KissKH"
                val name = call.request.queryParameters["name"] ?: ""
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val items = ProviderManager.getSectionItems(provider, name, page)
                call.respondText(mapper.writeValueAsString(items), ContentType.Application.Json)
            }

            // API: Popular Feed
            get("/api/popular") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val provider = call.request.queryParameters["provider"] ?: "KissKH"
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val items = ProviderManager.getPopular(provider, page)
                call.respondText(mapper.writeValueAsString(items), ContentType.Application.Json)
            }

            // API: Latest Feed
            get("/api/latest") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val provider = call.request.queryParameters["provider"] ?: "KissKH"
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val items = ProviderManager.getLatest(provider, page)
                call.respondText(mapper.writeValueAsString(items), ContentType.Application.Json)
            }

            // API: Random Media Pick
            get("/api/random") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val provider = call.request.queryParameters["provider"] ?: "KissKH"
                val item = ProviderManager.getRandomMedia(provider)
                if (item != null) {
                    call.respondText(mapper.writeValueAsString(item), ContentType.Application.Json)
                } else {
                    call.respondText("{}", ContentType.Application.Json)
                }
            }

            // API: Watch History
            get("/api/history") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                val userId = call.request.queryParameters["userId"]?.toLongOrNull() ?: 0L
                val history = Database.getWatchHistory(userId)
                call.respondText(mapper.writeValueAsString(history), ContentType.Application.Json)
            }
            post("/api/history") {
                call.response.headers.append("Access-Control-Allow-Origin", "*")
                try {
                    val body = call.receiveText()
                    val node = mapper.readTree(body)
                    val userId = node.get("userId")?.asLong() ?: 0L
                    val title = node.get("title")?.asText() ?: ""
                    val url = node.get("url")?.asText() ?: ""
                    val posterUrl = node.get("posterUrl")?.asText()
                    val provider = node.get("provider")?.asText() ?: ""
                    val episodeName = node.get("episodeName")?.asText()
                    val streamUrl = node.get("streamUrl")?.asText()
                    if (title.isNotBlank() && url.isNotBlank()) {
                        Database.recordWatch(
                            userId = userId,
                            provider = provider,
                            mediaUrl = url,
                            title = title,
                            posterUrl = posterUrl,
                            episodeTitle = episodeName,
                            episodeData = streamUrl
                        )
                    }
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                } catch (e: Exception) {
                    call.respondText("""{"status":"error","message":"${e.message}"}""", ContentType.Application.Json)
                }
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

        // Set Telegram Menu Button: if web app enabled, register web_app button; otherwise register standard bot commands menu button
        launch {
            try {
                if (Config.enableWebApp && webAppUrl.startsWith("https://")) {
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
                } else {
                    val ok = client.setChatMenuButton(MenuButton(type = "commands"))
                    if (ok) {
                        logger.info("👉 Telegram Menu Button set to standard bot commands (Mini App disabled)")
                    }
                }
            } catch (e: Exception) {
                logger.debug("Failed setting chat menu button: ${e.message}")
            }
        }

        // Register Telegram Bot Command Menu for default and Persian
        launch {
            try {
                val defaultCommands = listOf(
                    BotCommand("start", "🎬 Main Menu & Dashboard"),
                    BotCommand("popular", "🔥 Popular & Trending Movies/Series"),
                    BotCommand("latest", "🆕 Latest Releases"),
                    BotCommand("random", "🎲 Surprise Me / Random Pick"),
                    BotCommand("categories", "📂 Categories & Sections"),
                    BotCommand("history", "🕒 Continue Watching / History"),
                    BotCommand("search", "🔍 Search Movies & Series"),
                    BotCommand("sources", "📡 Movie & Series Sources"),
                    BotCommand("bookmarks", "⭐ Saved Bookmarks"),
                    BotCommand("help", "💡 User Guide & Help"),
                    BotCommand("language", "🌐 Change Language / تغییر زبان"),
                    BotCommand("check_sources", "🩺 Check Sources Health & Status"),
                    BotCommand("ping", "🏓 Check Bot Status")
                )
                client.setMyCommands(defaultCommands)

                val faCommands = listOf(
                    BotCommand("start", "🎬 منوی اصلی و داشبورد"),
                    BotCommand("popular", "🔥 فیلم‌ها و سریال‌های محبوب و داغ"),
                    BotCommand("latest", "🆕 جدیدترین فیلم‌ها و سریال‌ها"),
                    BotCommand("random", "🎲 پیشنهاد شانسی و تصادفی"),
                    BotCommand("categories", "📂 بخش‌ها و دسته‌بندی‌های منبع"),
                    BotCommand("history", "🕒 ادامه تماشا و تاریخچه"),
                    BotCommand("search", "🔍 جستجوی فیلم و سریال"),
                    BotCommand("sources", "📡 منابع و سورس‌های فیلم و سریال"),
                    BotCommand("bookmarks", "⭐ فیلم‌ها و سریال‌های نشان‌شده"),
                    BotCommand("help", "💡 راهنمای جامع و دستورات ربات"),
                    BotCommand("language", "🌐 تغییر زبان / Change Language"),
                    BotCommand("check_sources", "🩺 تست سلامت و اتصال سورس‌ها"),
                    BotCommand("ping", "🏓 وضعیت آنلاین ربات")
                )
                client.setMyCommands(faCommands, languageCode = "fa")
            } catch (e: Exception) {
                logger.warn("Failed registering bot commands: ${e.message}")
            }
        }

        // Asynchronously sync community repositories in the background if only default seeds present
        launch {
            try {
                if (CloudStreamRepoManager.getAllPlugins().size <= 4) {
                    logger.info("Syncing community repositories in background...")
                    CloudStreamRepoManager.syncAllDefaults()
                    logger.info("Community repositories synced: ${CloudStreamRepoManager.getAllPlugins().size} plugins ready.")
                }
            } catch (e: Exception) {
                logger.warn("Background repo sync skipped: ${e.message}")
            }
        }

        launch {
            runner.startPolling()
        }
    }
}
}
