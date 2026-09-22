package com.telestream.config

object Config {
    val botToken: String = System.getenv("BOT_TOKEN")?.trim() ?: ""
    val port: Int = System.getenv("PORT")?.toIntOrNull() ?: 7860

    // Public WebApp URL (supports Hugging Face SPACE_HOST or custom domain)
    val webAppUrl: String = System.getenv("WEBAPP_URL")?.trim()
        ?.ifBlank { null }
        ?: System.getenv("SPACE_HOST")?.let { "https://$it" }
        ?: "http://localhost:$port"

    // Admin user IDs (comma separated, e.g. "12345678,87654321")
    val adminIds: Set<Long> = System.getenv("ADMIN_IDS")
        ?.split(",")
        ?.mapNotNull { it.trim().toLongOrNull() }
        ?.toSet() ?: emptySet()

    fun isAdmin(userId: Long): Boolean {
        // If no admins are explicitly set, all users can administer (or set false to restrict)
        return adminIds.isEmpty() || userId in adminIds
    }

    // Crypto donation wallet addresses
    val usdtTrc20: String = System.getenv("DONATION_USDT_TRC20")?.trim()
        ?.ifBlank { null }
        ?: "TQn9Y2khEsLJW1ChVWFMSMeSTow5KaxnSE" // Placeholder / Default

    val tonWallet: String = System.getenv("DONATION_TON")?.trim()
        ?.ifBlank { null }
        ?: "UQDP14pSjV1k8L0Fj8d2p7k8XqZ7YjB7p9" // Placeholder / Default

    val btcWallet: String = System.getenv("DONATION_BTC")?.trim()
        ?.ifBlank { null }
        ?: "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh" // Placeholder / Default

    val ethWallet: String = System.getenv("DONATION_ETH")?.trim()
        ?.ifBlank { null }
        ?: "0x71C...YourEthAddressHere"
}
