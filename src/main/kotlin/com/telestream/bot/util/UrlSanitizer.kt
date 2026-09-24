package com.telestream.bot.util

import com.telestream.providers.ProviderManager
import org.json.JSONObject
import java.net.URLEncoder

object UrlSanitizer {

    fun sanitizeTelegramUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        return trimmed.replace(" ", "%20")
    }

    fun getSafeButtonUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val sanitized = sanitizeTelegramUrl(rawUrl) ?: return null
        if (!sanitized.startsWith("http://", ignoreCase = true) &&
            !sanitized.startsWith("https://", ignoreCase = true) &&
            !sanitized.startsWith("tg://", ignoreCase = true)
        ) {
            return null
        }
        // Telegram Bot API limit for inline button URL is 512 bytes
        if (sanitized.length <= 500) {
            return sanitized
        }
        val base = com.telestream.config.Config.webAppUrl.removeSuffix("/webapp").trimEnd('/')
        if (base.isNotBlank() && (base.startsWith("http://", ignoreCase = true) || base.startsWith("https://", ignoreCase = true))) {
            val token = com.telestream.bot.model.CallbackTokenCache.put(sanitized)
            return "$base/r/$token"
        }
        return null
    }

    fun resolveWebUrl(rawUrl: String?, providerName: String? = null, title: String? = null): String? {
        if (!rawUrl.isNullOrBlank()) {
            val trimmed = rawUrl.trim()
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                return sanitizeTelegramUrl(trimmed)
            }
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    val json = JSONObject(trimmed)
                    val directUrl = json.optString("url")
                    if (directUrl.startsWith("http://", ignoreCase = true) || directUrl.startsWith("https://", ignoreCase = true)) {
                        return sanitizeTelegramUrl(directUrl)
                    }
                    val imdbId = json.optString("imdbId")
                    if (imdbId.isNotBlank() && imdbId != "null") {
                        return "https://www.imdb.com/title/$imdbId"
                    }
                    val id = json.opt("id")?.toString()
                    val type = json.optString("type")
                    if (!id.isNullOrBlank() && id != "null" && type.isNotBlank()) {
                        val tmdbType = if (type.equals("tv", ignoreCase = true)) "tv" else "movie"
                        return "https://www.themoviedb.org/$tmdbType/$id"
                    }
                } catch (_: Exception) {}
            }
        }

        if (!providerName.isNullOrBlank()) {
            val provider = ProviderManager.getProvider(providerName)
            val mainUrl = provider?.mainUrl?.trim()
            if (!mainUrl.isNullOrBlank() &&
                !mainUrl.equals("NONE", ignoreCase = true) &&
                (mainUrl.startsWith("http://", ignoreCase = true) || mainUrl.startsWith("https://", ignoreCase = true))
            ) {
                return sanitizeTelegramUrl(mainUrl)
            }
        }

        if (!title.isNullOrBlank()) {
            val cleanTitle = title.trim()
            if (cleanTitle.isNotBlank()) {
                val encoded = URLEncoder.encode(cleanTitle, "UTF-8")
                return "https://www.themoviedb.org/search?query=$encoded"
            }
        }

        return null
    }
}
