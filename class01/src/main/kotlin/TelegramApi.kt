package org.jetbrains.edu.sed2026.class01

import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow

open class TextResponse(
    val chatId: String = "",
    val message: String = "",
    val buttons: List<List<String>> = emptyList(),
    var messageId: Int? = null
) {
    open fun execute(): Int? { return null }

    open fun update(newMessage: String): Int? { return null }

    open fun copy(
        chatId: String = this.chatId,
        message: String = this.message,
        buttons: List<List<String>> = this.buttons,
        messageId: Int? = this.messageId
    ): TextResponse {
        return TextResponse(chatId, message, buttons, messageId)
    }
}

interface ResponseFactory {
    fun createTextResponse(): TextResponse
}

data class TelegramRequest(
    val userId: Long,
    val message: String
)
class TelegramTextResponse(
    chatId: String = "",
    message: String = "",
    buttons: List<List<String>> = emptyList(),
    messageId: Int? = null,
    private val bot: TelegramLongPollingBot
) : TextResponse(chatId, message, buttons, messageId) {
    override fun execute(): Int? {
        if (chatId.isEmpty()) return null
        val sendMessage = SendMessage(chatId, message)
        if (buttons.isNotEmpty()) {
            sendMessage.replyMarkup = ReplyKeyboardMarkup().apply {
                keyboard = buttons.map { row ->
                    KeyboardRow().apply { addAll(row) }
                }
                resizeKeyboard = true
            }
        }
        val sentMessage = bot.execute(sendMessage)
        this.messageId = sentMessage.messageId
        return this.messageId
    }

    override fun update(newMessage: String): Int? {
        if (chatId.isEmpty() || messageId == null) return null
        val editMessage = org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText()
        editMessage.chatId = chatId
        editMessage.messageId = messageId!!
        editMessage.text = newMessage
        bot.execute(editMessage)
        return messageId
    }

    override fun copy(
        chatId: String,
        message: String,
        buttons: List<List<String>>,
        messageId: Int?
    ): TextResponse {
        return TelegramTextResponse(chatId, message, buttons, messageId, bot)
    }
}

class TelegramResponseFactory(private val bot: TelegramLongPollingBot) : ResponseFactory {
    override fun createTextResponse(): TextResponse {
        return TelegramTextResponse(bot = bot)
    }
}

