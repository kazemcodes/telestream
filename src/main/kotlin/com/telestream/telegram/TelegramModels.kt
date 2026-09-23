package com.telestream.telegram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null
)

@Serializable
data class Update(
    @SerialName("update_id") val updateId: Long,
    val message: Message? = null,
    @SerialName("callback_query") val callbackQuery: CallbackQuery? = null,
    @SerialName("inline_query") val inlineQuery: InlineQuery? = null
)

@Serializable
data class InlineQuery(
    val id: String,
    val from: User,
    val query: String,
    val offset: String? = null
)

@Serializable
data class InlineQueryResultArticle(
    val type: String = "article",
    val id: String,
    val title: String,
    @SerialName("input_message_content") val inputMessageContent: InputTextMessageContent,
    @SerialName("reply_markup") val replyMarkup: InlineKeyboardMarkup? = null,
    val description: String? = null,
    @SerialName("thumb_url") val thumbUrl: String? = null
)

@Serializable
data class InputTextMessageContent(
    @SerialName("message_text") val messageText: String,
    @SerialName("parse_mode") val parseMode: String = "Markdown"
)

@Serializable
data class Message(
    @SerialName("message_id") val messageId: Long,
    val from: User? = null,
    val chat: Chat,
    val text: String? = null
)

@Serializable
data class User(
    val id: Long,
    @SerialName("is_bot") val isBot: Boolean = false,
    @SerialName("first_name") val firstName: String = "",
    val username: String? = null,
    @SerialName("language_code") val languageCode: String? = null
)

@Serializable
data class Chat(
    val id: Long,
    val type: String,
    val title: String? = null,
    val username: String? = null
)

@Serializable
data class CallbackQuery(
    val id: String,
    val from: User,
    val message: Message? = null,
    val data: String? = null
)

@Serializable
data class InlineKeyboardMarkup(
    @SerialName("inline_keyboard") val inlineKeyboard: List<List<InlineKeyboardButton>>
)

@Serializable
data class WebAppInfo(
    val url: String
)

@Serializable
data class InlineKeyboardButton(
    val text: String,
    @SerialName("callback_data") val callbackData: String? = null,
    val url: String? = null,
    @SerialName("web_app") val webApp: WebAppInfo? = null,
    @SerialName("switch_inline_query_current_chat") val switchInlineQueryCurrentChat: String? = null
)

@Serializable
data class MenuButton(
    val type: String = "web_app",
    val text: String = "🎬 TeleStream",
    @SerialName("web_app") val webApp: WebAppInfo
)

@Serializable
data class SetChatMenuButtonRequest(
    @SerialName("chat_id") val chatId: Long? = null,
    @SerialName("menu_button") val menuButton: MenuButton
)

@Serializable
data class BotCommand(
    val command: String,
    val description: String
)

@Serializable
data class SetMyCommandsRequest(
    val commands: List<BotCommand>,
    @SerialName("language_code") val languageCode: String? = null
)

