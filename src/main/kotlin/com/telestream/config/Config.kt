package com.telestream.config

import java.io.File

object Config {
    private val envMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        val candidates = listOf(
            File(".env"),
            File(System.getProperty("user.dir", "."), ".env")
        )
        val envFile = candidates.firstOrNull { it.exists() && it.isFile }
        if (envFile != null) {
            envFile.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isNotEmpty() && !line.startsWith("#")) {
                    val cleanLine = if (line.startsWith("export ")) line.removePrefix("export ").trim() else line
                    if (cleanLine.contains("=")) {
                        val key = cleanLine.substringBefore("=").trim()
                        var value = cleanLine.substringAfter("=").trim()
                        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length - 1)
                        }
                        if (key.isNotEmpty()) {
                            map[key] = value
                        }
                    }
                }
            }
        }
        map
    }

    private fun get(key: String): String? {
        val sys = System.getenv(key)?.trim()
        if (!sys.isNullOrEmpty()) return sys
        return envMap[key]?.trim()?.ifEmpty { null }
    }

    val botToken: String = get("BOT_TOKEN") ?: ""
    val port: Int = get("PORT")?.toIntOrNull() ?: 7860

    // Telegram API Endpoint & Proxy
    val telegramApiUrl: String = get("TELEGRAM_API_URL") ?: "https://api.telegram.org"
    val telegramProxy: String? = get("TELEGRAM_PROXY") ?: get("HTTPS_PROXY") ?: get("HTTP_PROXY")
    val scraperProxy: String? = get("SCRAPER_PROXY") ?: get("ALL_PROXY") ?: get("HTTPS_PROXY") ?: get("HTTP_PROXY")
    // DNS & Anti-Censorship
    val dnsOverHttps: Boolean = get("ENABLE_DOH")?.toBooleanStrictOrNull() ?: true

    // Cloudflare Bypass / FlareSolverr Endpoint
    val flareSolverrUrl: String = get("FLARESOLVERR_URL") ?: "http://localhost:8191/v1"

    init {
        System.setProperty("FLARESOLVERR_URL", flareSolverrUrl)
    }

    // Public WebApp URL (supports Hugging Face SPACE_HOST or custom domain)
    val webAppUrl: String = get("WEBAPP_URL")?.let {
        if (it.endsWith("/webapp")) it else "${it.trimEnd('/')}/webapp"
    } ?: get("SPACE_HOST")?.let { "https://$it/webapp" }
      ?: "http://localhost:$port/webapp"

    // Admin user IDs (comma separated, e.g. "12345678,87654321")
    val adminIds: Set<Long> = get("ADMIN_IDS")
        ?.split(",")
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?.toSet() ?: emptySet()

    fun isAdmin(userId: Long): Boolean {
        // If no admins are explicitly set, all users can administer (or set false to restrict)
        return adminIds.isEmpty() || userId in adminIds
    }

    // Crypto donation wallet addresses
    val usdtTrc20: String = get("DONATION_USDT_TRC20")
        ?: "TQn9Y2khEsLJW1ChVWFMSMeSTow5KaxnSE" // Placeholder / Default

    val tonWallet: String = get("DONATION_TON")
        ?: "UQDP14pSjV1k8L0Fj8d2p7k8XqZ7YjB7p9" // Placeholder / Default

    val btcWallet: String = get("DONATION_BTC")
        ?: "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh" // Placeholder / Default

    val ethWallet: String = get("DONATION_ETH")
        ?: "0x71C...YourEthAddressHere"
}
