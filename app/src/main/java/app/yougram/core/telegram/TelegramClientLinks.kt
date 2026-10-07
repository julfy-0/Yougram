package app.yougram.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.InternalLinkTypeChatInvite
import dev.g000sha256.tdl.dto.InternalLinkTypeMessage
import dev.g000sha256.tdl.dto.InternalLinkTypePublicChat

internal fun <T> TdlResult<T>.okOrNull(): T? = (this as? TdlResult.Success<T>)?.result

/** Вызывать на самом TdlClient, а не на обёртке TelegramClient. */
suspend fun TdlClient.resolveInternalLink(url: String): TgLinkResult {
    val type = getInternalLinkType(url).okOrNull() ?: return TgLinkResult.Failed
    return when (type) {
        is InternalLinkTypePublicChat ->
            searchPublicChat(type.chatUsername).okOrNull()
                ?.let { TgLinkResult.OpenChat(it.id) } ?: TgLinkResult.Failed
        is InternalLinkTypeChatInvite -> {
            // joinChatByInviteLink в этой версии не возвращает чат, id берём из checkChatInviteLink
            var chatId = checkChatInviteLink(url).okOrNull()?.chatId ?: 0L
            if (chatId == 0L) {
                joinChatByInviteLink(url).okOrNull() ?: return TgLinkResult.Failed
                chatId = checkChatInviteLink(url).okOrNull()?.chatId ?: 0L
            }
            if (chatId != 0L) TgLinkResult.OpenChat(chatId) else TgLinkResult.Failed
        }
        is InternalLinkTypeMessage ->
            getMessageLinkInfo(url).okOrNull()
                ?.let { TgLinkResult.OpenChat(it.chatId) } ?: TgLinkResult.Failed
        else -> TgLinkResult.OpenUrl(url)
    }
}
