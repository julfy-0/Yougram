package app.yougram.feature.chat.comments

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageReplyToMessage
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageThreadInfo
import dev.g000sha256.tdl.dto.MessageTopicThread

class CommentsRepository(private val client: TdlClient) {

    suspend fun openThread(channelChatId: Long, postId: Long): MessageThreadInfo? =
        when (val r = client.getMessageThread(chatId = channelChatId, messageId = postId)) {
            is TdlResult.Success -> r.result
            is TdlResult.Failure -> null
        }

    suspend fun loadComments(info: MessageThreadInfo, fromMessageId: Long = 0L, limit: Int = 50): List<Message> =
        when (val r = client.getMessageThreadHistory(
            chatId = info.chatId,
            messageId = info.messageThreadId,
            fromMessageId = fromMessageId,
            offset = 0,
            limit = limit,
        )) {
            is TdlResult.Success -> r.result.messages.orEmpty().filterNotNull()
            is TdlResult.Failure -> emptyList()
        }

    suspend fun sendComment(info: MessageThreadInfo, text: String): Boolean =
        client.sendMessage(
            chatId = info.chatId,
            topicId = MessageTopicThread(messageThreadId = info.messageThreadId),
            replyTo = InputMessageReplyToMessage(
                messageId = info.messageThreadId,
                quote = null,
                checklistTaskId = 0,
                pollOptionId = "",
            ),
            options = null,
            replyMarkup = null,
            inputMessageContent = InputMessageText(
                text = FormattedText(text = text, entities = emptyArray()),
                linkPreviewOptions = null,
                clearDraft = true,
            ),
        ) is TdlResult.Success
}
