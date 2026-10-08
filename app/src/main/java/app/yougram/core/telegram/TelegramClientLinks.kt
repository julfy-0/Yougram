package app.yougram.core.telegram

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.InternalLinkTypeBotStart
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
        // t.me/bot?start=param: открываем чат с ботом (параметр старта отправит сам пользователь).
        is InternalLinkTypeBotStart ->
            searchPublicChat(type.botUsername).okOrNull()
                ?.let { TgLinkResult.OpenChat(it.id) } ?: TgLinkResult.Failed
        is InternalLinkTypeChatInvite -> {
            val info = checkChatInviteLink(url).okOrNull() ?: return TgLinkResult.Failed
            // Уже состоим в чате — просто открываем, иначе сначала подтверждение.
            if (info.chatId != 0L) TgLinkResult.OpenChat(info.chatId) else TgLinkResult.JoinInvite(url, info.title)
        }
        is InternalLinkTypeMessage -> {
            val info = getMessageLinkInfo(url).okOrNull() ?: return TgLinkResult.Failed
            if (info.chatId == 0L) TgLinkResult.Failed
            else TgLinkResult.OpenChat(info.chatId, info.message?.id ?: 0L)
        }
        else -> TgLinkResult.OpenUrl(url)
    }
}

/** Вступает в чат по приглашению и возвращает его id (или null, если не вышло). */
suspend fun TdlClient.joinByInvite(url: String): Long? {
    joinChatByInviteLink(url).okOrNull() ?: return null
    return checkChatInviteLink(url).okOrNull()?.chatId?.takeIf { it != 0L }
}
