package com.telestream.telegram

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

class TelegramClient(private val botToken: String) {
    private val logger = LoggerFactory.getLogger(TelegramClient::class.java)
    private val baseUrl = "https://api.telegram.org/bot$botToken"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun getUpdates(offset: Long = 0, timeout: Int = 25): List<Update> {
        return try {
            val response = client.get("$baseUrl/getUpdates") {
                parameter("offset", offset)
                parameter("timeout", timeout)
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
            client.post("$baseUrl/sendMessage") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
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
    ): Boolean {
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
            res.status.isSuccess()
        } catch (e: Exception) {
            logger.debug("Failed sending photo: ${e.message}")
            false
        }
    }

    suspend fun editMessageText(
        chatId: Long,
        messageId: Long,
        text: String,
        replyMarkup: InlineKeyboardMarkup? = null,
        parseMode: String = "Markdown"
    ) {
        try {
            val payload = buildJsonObject {
                put("chat_id", chatId)
                put("message_id", messageId)
                put("text", text)
                put("parse_mode", parseMode)
                if (replyMarkup != null) {
                    put("reply_markup", json.encodeToJsonElement(replyMarkup))
                }
            }
            client.post("$baseUrl/editMessageText") {
                contentType(ContentType.Application.Json)
                setBody(payload.toString())
            }
        } catch (e: Exception) {
            logger.error("Error editing message $messageId: ${e.message}")
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
}

