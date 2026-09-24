package com.telestream.bot.handlers

import com.telestream.config.Config
import com.telestream.database.Database
import com.telestream.i18n.I18n.t
import com.telestream.repo.CloudStreamRepoManager
import com.telestream.telegram.CallbackQuery
import com.telestream.telegram.InlineKeyboardButton
import com.telestream.telegram.InlineKeyboardMarkup
import com.telestream.telegram.TelegramClient
import com.telestream.telegram.WebAppInfo
import kotlin.math.max

class AdminHandler(private val bot: TelegramClient) {

    suspend fun showAdminDashboard(chatId: Long, lang: String, messageId: Long? = null) {
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
                    InlineKeyboardButton(text = t("btn_admin_repos", lang), callbackData = "admin:repos:0"),
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

    suspend fun showLanguageSelector(chatId: Long, lang: String, messageId: Long? = null) {
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

    suspend fun showAdminReposManager(chatId: Long, lang: String, page: Int = 0, messageId: Long? = null) {
        val repos = Database.getAdminRepos()
        val pageSize = 6
        val totalPages = max(1, (repos.size + pageSize - 1) / pageSize)
        val safePage = page.coerceIn(0, totalPages - 1)
        val pageRepos = repos.drop(safePage * pageSize).take(pageSize)

        val enabledCount = repos.count { it.isEnabled }
        val text = buildString {
            append(t("admin_repos_title", lang))
            append("\n\n📊 ${if (lang == "fa") "کل مخازن" else "Total Repos"}: *${repos.size}* | ${if (lang == "fa") "فعال" else "Active"}: *${enabledCount}* | ${if (lang == "fa") "غیرفعال" else "Disabled"}: *${repos.size - enabledCount}*")
            append("\n${if (lang == "fa") "• جهت فعال/غیرفعال‌سازی هر مخزن روی آن بزنید:" else "• Tap any repository to toggle its availability:"}")
        }

        val rows = mutableListOf<List<InlineKeyboardButton>>()

        // Repo toggle buttons
        for (repo in pageRepos) {
            val statusIcon = if (repo.isEnabled) "✅" else "❌"
            val label = "$statusIcon ${repo.repoName}"
            rows.add(
                listOf(
                    InlineKeyboardButton(
                        text = label,
                        callbackData = "admin:repo_toggle:${repo.repoId}:$safePage"
                    )
                )
            )
        }

        // Pagination row if > 1 page
        if (totalPages > 1) {
            val navRow = mutableListOf<InlineKeyboardButton>()
            if (safePage > 0) {
                navRow.add(InlineKeyboardButton(text = t("btn_prev", lang), callbackData = "admin:repos:${safePage - 1}"))
            }
            navRow.add(InlineKeyboardButton(text = "📄 ${safePage + 1}/$totalPages", callbackData = "noop"))
            if (safePage < totalPages - 1) {
                navRow.add(InlineKeyboardButton(text = t("btn_next", lang), callbackData = "admin:repos:${safePage + 1}"))
            }
            rows.add(navRow)
        }

        // Bulk action row: [ ✅ Enable All ] [ ❌ Disable All ]
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_enable_all", lang), callbackData = "admin:repo_all:1:$safePage"),
                InlineKeyboardButton(text = t("btn_disable_all", lang), callbackData = "admin:repo_all:0:$safePage")
            )
        )

        // Bottom row: [ 🔄 Sync ] [ ⬅️ Back to Admin ]
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_sync", lang), callbackData = "menu:sync"),
                InlineKeyboardButton(text = t("btn_back_admin", lang), callbackData = "menu:admin")
            )
        )

        val keyboard = InlineKeyboardMarkup(rows)
        if (messageId != null) {
            val edited = bot.editMessageText(chatId, messageId, text, replyMarkup = keyboard)
            if (!edited) bot.sendMessage(chatId, text, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, text, replyMarkup = keyboard)
        }
    }

    suspend fun showDonationMessage(chatId: Long, lang: String, messageId: Long? = null) {
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

    suspend fun showHelpMessage(chatId: Long, lang: String, messageId: Long? = null) {
        val botUsername = try { bot.getMe()?.username ?: "telecloudstreambot" } catch (_: Exception) { "telecloudstreambot" }
        val helpText = t("help_msg", lang, botUsername, botUsername)
        val rows = mutableListOf<List<InlineKeyboardButton>>()
        val webAppUrl = Config.webAppUrl
        if (webAppUrl.startsWith("https://")) {
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
                InlineKeyboardButton(text = t("btn_search", lang), callbackData = "menu:search"),
                InlineKeyboardButton(text = t("btn_sources", lang), callbackData = "menu:sources")
            )
        )
        rows.add(
            listOf(
                InlineKeyboardButton(text = t("btn_donate", lang), callbackData = "menu:donate"),
                InlineKeyboardButton(text = t("btn_close", lang), callbackData = "close")
            )
        )
        val keyboard = InlineKeyboardMarkup(rows)
        if (messageId != null) {
            val edited = bot.editMessageText(chatId, messageId, helpText, replyMarkup = keyboard)
            if (!edited) bot.sendMessage(chatId, helpText, replyMarkup = keyboard)
        } else {
            bot.sendMessage(chatId, helpText, replyMarkup = keyboard)
        }
    }

    suspend fun handleRepoToggle(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        if (!Config.isAdmin(userId)) {
            bot.answerCallbackQuery(callback.id, t("admin_only", lang), showAlert = true)
            return
        }
        val parts = data.removePrefix("admin:repo_toggle:").split(":")
        val repoId = parts.getOrNull(0) ?: ""
        val page = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val repos = Database.getAdminRepos()
        val target = repos.firstOrNull { it.repoId.equals(repoId, ignoreCase = true) }
        if (target != null) {
            val newStatus = !target.isEnabled
            Database.setRepoEnabled(target.repoId, target.repoName, newStatus)
            val statusText = if (newStatus) "✅ ${target.repoName} فعال شد" else "❌ ${target.repoName} غیرفعال شد"
            bot.answerCallbackQuery(callback.id, statusText)
            showAdminReposManager(chatId, lang, page, messageId)
        } else {
            bot.answerCallbackQuery(callback.id)
        }
    }

    suspend fun handleRepoAll(callback: CallbackQuery, data: String, chatId: Long, messageId: Long, userId: Long, lang: String) {
        if (!Config.isAdmin(userId)) {
            bot.answerCallbackQuery(callback.id, t("admin_only", lang), showAlert = true)
            return
        }
        val parts = data.removePrefix("admin:repo_all:").split(":")
        val enable = parts.getOrNull(0) == "1"
        val page = parts.getOrNull(1)?.toIntOrNull() ?: 0
        Database.setAllReposEnabled(enable)
        val statusText = if (enable) "✅ تمامی مخازن فعال شدند" else "❌ تمامی مخازن غیرفعال شدند"
        bot.answerCallbackQuery(callback.id, statusText)
        showAdminReposManager(chatId, lang, page, messageId)
    }

    suspend fun handleNsfwToggle(callback: CallbackQuery, chatId: Long, messageId: Long, userId: Long, lang: String) {
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

    suspend fun handleSync(callback: CallbackQuery, chatId: Long, userId: Long, lang: String) {
        if (!Config.isAdmin(userId)) {
            bot.answerCallbackQuery(callback.id, t("admin_only", lang), showAlert = true)
            return
        }
        bot.answerCallbackQuery(callback.id, t("syncing", lang).take(40))
        val repos = CloudStreamRepoManager.syncAllDefaults()
        val totalPlugins = repos.sumOf { it.pluginsCount }
        bot.sendMessage(chatId, t("sync_done", lang, totalPlugins, repos.size))
        showAdminReposManager(chatId, lang, 0)
    }
}
