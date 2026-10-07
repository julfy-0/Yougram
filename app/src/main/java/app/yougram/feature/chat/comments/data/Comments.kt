package app.yougram.feature.chat.comments.data

import app.yougram.core.telegram.okOrNull
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.Message

data class CommentsInfo(val count: Int, val hasUnread: Boolean)
data class ThreadTarget(val discussionChatId: Long, val threadId: Long, val rootChatId: Long, val rootMessageId: Long)

/**
 * Комментарии показываются под любым постом, где они есть или включены:
 * есть replyInfo -> счётчик; нет, но пост канала с обсуждением -> "Оставить комментарий".
 * null -> кнопку не рисуем.
 */
fun Message.commentsInfo(): CommentsInfo? {
    val r = interactionInfo?.replyInfo
    // В новых версиях TDLib canGetMessageThread убран из Message; replyInfo приходит у постов с включёнными комментариями (даже при 0).
    if (r != null) return CommentsInfo(r.replyCount, r.lastMessageId > r.lastReadInboxMessageId)
    return null
}

/** Клик по кнопке: узнать, в каком чате-обсуждении лежит ветка. */
suspend fun TdlClient.openComments(chatId: Long, messageId: Long): ThreadTarget? {
    val info = getMessageThread(chatId, messageId).okOrNull() ?: return null
    return ThreadTarget(info.chatId, info.messageThreadId, chatId, messageId)
}

/** Загрузка комментариев (новые первыми, как обычная история). fromMessageId = 0 для первой порции. */
suspend fun TdlClient.loadComments(target: ThreadTarget, fromMessageId: Long = 0L, limit: Int = 50): List<Message> =
    getMessageThreadHistory(target.rootChatId, target.rootMessageId, fromMessageId, 0, limit)
        .okOrNull()?.messages?.filterNotNull().orEmpty()
