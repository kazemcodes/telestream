package com.telestream.telegram

import com.telestream.config.Config
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

class TelegramClient(private val botToken: String) {
    private val logger = LoggerFactory.getLogger(TelegramClient::class.java)
    private val apiBase = Config.telegramApiUrl.trimEnd('/')
    private val baseUrl = "$apiBase/bot$botToken"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000L
            socketTimeoutMillis = 60_000L
            connectTimeoutMillis = 30_000L
        }
        engine {
            requestTimeout = 60_000L
            Config.telegramProxy?.let { proxyUrl ->
                proxy = ProxyBuilder.http(Url(proxyUrl))
            }
        }
    }

    suspend fun getUpdates(offset: Long = 0, timeout: Int = 25): List<Update> {
        return try {
            val response = client.get("$baseUrl/getUpdates") {
                parameter("offset", offset)
                parameter("timeout", timeout)
                timeout {
                    requestTimeoutMillis = (timeout + 30) * 1000L
                    socketTimeoutMillis = (timeout + 30) * 1000L
                }
            }
            val tgResp: TelegramResponse<List<Update>> = response.body()
            tgResp.result ?: emptyList()
        } catch (e: Exception) {
            logger.warn("Failed to getUpdates: ${e.message}")
            emptyList()
        }
    }

    suspend fun sendMessage(
        chatId: Long,
        text: String,
        replyMarkup: InlineKeyboardMarkup? = null,
        parseMode: String = "Markdown"
    ) {
        try {
            val payload = buildJsonObject {
                put("chat_id", chatId)
                put("text", text)
                put("parse_mode", parseMode)
                if (replyMarkup != null) {
                    put("reply_markup", json.encodeToJsonElement(replyMarkup))
                }
            }
            val res = client.post("$baseUrl/sendMessage") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            if (!res.status.isSuccess()) {
                val errorBody = res.bodyAsText()
                logger.error("Error sending message to $chatId [HTTP ${res.status.value}]: $errorBody")
                if (errorBody.contains("can't parse entities") || errorBody.contains("parse")) {
                    client.post("$baseUrl/sendMessage") {
                        contentType(ContentType.Application.Json)
                        val fallback = buildJsonObject {
                            put("chat_id", chatId)
                            put("text", text.replace("*", "").replace("_", "").replace("`", ""))
                            if (replyMarkup != null) {
                                put("reply_markup", json.encodeToJsonElement(replyMarkup))
                            }
                        }
                        setBody(fallback.toString())
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error sending message to $chatId: ${e.message}")
        }
    }

    suspend fun sendPhoto(
        chatId: Long,
        photoUrl: String,
        caption: String? = null,
        replyMarkup: InlineKeyboardMarkup? = null,
        parseMode: String = "Markdown"
    ): Long? {
        return try {
            val payload = buildJsonObject {
                put("chat_id", chatId)
                put("photo", photoUrl)
                if (caption != null) {
                    put("caption", caption)
                    put("parse_mode", parseMode)
                }
                if (replyMarkup != null) {
                    put("reply_markup", json.encodeToJsonElement(replyMarkup))
                }
            }
            val res = client.post("$baseUrl/sendPhoto") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            if (res.status.isSuccess()) {
                val elem = json.parseToJsonElement(res.bodyAsText())
                elem.jsonObject["result"]?.jsonObject?.get("message_id")?.jsonPrimitive?.longOrNull ?: 1L
            } else {
                logger.debug("Failed sending photo: HTTP ${res.status.value}")
                null
            }
        } catch (e: Exception) {
            logger.debug("Failed sending photo: ${e.message}")
            null
        }
    }

    suspend fun editMessageMedia(
        chatId: Long,
        messageId: Long,
        photoUrl: String,
        caption: String? = null,
        replyMarkup: InlineKeyboardMarkup? = null,
        parseMode: String = "Markdown"
    ): Boolean {
        return try {
            val payload = buildJsonObject {
                put("chat_id", chatId)
                put("message_id", messageId)
                put("media", buildJsonObject {
                    put("type", "photo")
                    put("media", photoUrl)
                    if (caption != null) {
                        put("caption", caption)
                        put("parse_mode", parseMode)
                    }
                })
                if (replyMarkup != null) {
                    put("reply_markup", json.encodeToJsonElement(replyMarkup))
                }
            }
            val res = client.post("$baseUrl/editMessageMedia") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            res.status.isSuccess()
        } catch (e: Exception) {
            logger.error("Error editing media $messageId: ${e.message}")
            false
        }
    }

    suspend fun sendMediaGroup(
        chatId: Long,
        mediaList: List<InputMediaPhoto>
    ): Boolean {
        if (mediaList.isEmpty()) return false
        return try {
            val payload = buildJsonObject {
                put("chat_id", chatId)
                put("media", json.encodeToJsonElement(mediaList))
            }
            val res = client.post("$baseUrl/sendMediaGroup") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            if (!res.status.isSuccess()) {
                val err = res.bodyAsText()
                logger.error("Error sending media group: $err")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            logger.error("Error sending media group: ${e.message}")
            false
        }
    }


    suspend fun editMessageText(
        chatId: Long,
        messageId: Long,
        text: String,
        replyMarkup: InlineKeyboardMarkup? = null,
        parseMode: String = "Markdown"
    ): Boolean {
        return try {
            val payload = buildJsonObject {
                put("chat_id", chatId)
                put("message_id", messageId)
                put("text", text)
                put("parse_mode", parseMode)
                if (replyMarkup != null) {
                    put("reply_markup", json.encodeToJsonElement(replyMarkup))
                }
            }
            val res = client.post("$baseUrl/editMessageText") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            res.status.isSuccess()
        } catch (e: Exception) {
            logger.error("Error editing message $messageId: ${e.message}")
            false
        }
    }

    suspend fun editMessageCaption(
        chatId: Long,
        messageId: Long,
        caption: String,
        replyMarkup: InlineKeyboardMarkup? = null,
        parseMode: String = "Markdown"
    ): Boolean {
        return try {
            val payload = buildJsonObject {
                put("chat_id", chatId)
                put("message_id", messageId)
                put("caption", caption)
                put("parse_mode", parseMode)
                if (replyMarkup != null) {
                    put("reply_markup", json.encodeToJsonElement(replyMarkup))
                }
            }
            val res = client.post("$baseUrl/editMessageCaption") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            res.status.isSuccess()
        } catch (e: Exception) {
            logger.error("Error editing caption $messageId: ${e.message}")
            false
        }
    }

    suspend fun answerCallbackQuery(
        callbackQueryId: String,
        text: String? = null,
        showAlert: Boolean = false
    ) {
        try {
            val payload = buildJsonObject {
                put("callback_query_id", callbackQueryId)
                if (text != null) put("text", text)
                put("show_alert", showAlert)
            }
            client.post("$baseUrl/answerCallbackQuery") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
        } catch (e: Exception) {
            logger.error("Error answering callback $callbackQueryId: ${e.message}")
        }
    }

    suspend fun setChatMenuButton(menuButton: MenuButton): Boolean {
        return try {
            val payload = buildJsonObject {
                put("menu_button", json.encodeToJsonElement(menuButton))
            }
            val res = client.post("$baseUrl/setChatMenuButton") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            res.status.isSuccess()
        } catch (e: Exception) {
            logger.debug("Failed setting chat menu button: ${e.message}")
            false
        }
    }

    suspend fun setMyCommands(commands: List<BotCommand>, languageCode: String? = null): Boolean {
        return try {
            val payload = buildJsonObject {
                put("commands", json.encodeToJsonElement(commands))
                if (languageCode != null) {
                    put("language_code", languageCode)
                }
            }
            val res = client.post("$baseUrl/setMyCommands") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            if (res.status.isSuccess()) {
                logger.info("Successfully registered ${commands.size} bot commands (lang: ${languageCode ?: "default"})")
                true
            } else {
                logger.error("Failed to setMyCommands [HTTP ${res.status.value}]: ${res.bodyAsText()}")
                false
            }
        } catch (e: Exception) {
            logger.error("Error setting bot commands: ${e.message}")
            false
        }
    }

    suspend fun getMe(): User? {
        return try {
            val res = client.get("$baseUrl/getMe")
            val tgResp: TelegramResponse<User> = res.body()
            tgResp.result
        } catch (e: Exception) {
            logger.warn("Failed to getMe: ${e.message}")
            null
        }
    }

    suspend fun answerInlineQuery(
        inlineQueryId: String,
        results: List<InlineQueryResultArticle>,
        cacheTime: Int = 1,
        isPersonal: Boolean = true
    ): Boolean {
        return try {
            val payload = buildJsonObject {
                put("inline_query_id", inlineQueryId)
                put("results", json.encodeToJsonElement(results))
                put("cache_time", cacheTime)
                put("is_personal", isPersonal)
            }
            val res = client.post("$baseUrl/answerInlineQuery") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
            if (!res.status.isSuccess()) {
                val errorBody = res.bodyAsText()
                logger.error("Error answering inline query $inlineQueryId [HTTP ${res.status.value}]: $errorBody")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            logger.error("Error answering inline query $inlineQueryId: ${e.message}", e)
            false
        }
    }
}

