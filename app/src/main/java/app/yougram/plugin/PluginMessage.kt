package app.yougram.plugin

import app.yougram.feature.chat.data.MessageItem
import dev.g000sha256.tdl.dto.MessageContent

/** Stable plugin representation of a Telegram message. */
data class PluginMessage(
    val id: Long,
    val chatId: Long,
    val senderUserId: Long?,
    val senderChatId: Long?,
    val date: Int,
    val outgoing: Boolean,
    val text: String,
    val type: String,
    val content: Any?,
    val media: Map<String, Any?>?,
    val replyToMessageId: Long?,
    val reactions: List<Map<String, Any?>>,
) {
    fun asMap(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "chat_id" to chatId,
        "sender_user_id" to senderUserId,
        "sender_chat_id" to senderChatId,
        "date" to date,
        "outgoing" to outgoing,
        "text" to text,
        "type" to type,
        "content" to content,
        "media" to media,
        "reply_to_message_id" to replyToMessageId,
        "reactions" to reactions,
    )

    companion object {
        fun from(item: MessageItem, content: MessageContent?): PluginMessage = PluginMessage(
            id = item.id,
            chatId = item.chatId,
            senderUserId = item.senderUserId,
            senderChatId = item.senderChatId,
            date = item.date,
            outgoing = item.isOutgoing,
            text = item.text,
            type = content?.javaClass?.simpleName?.removePrefix("Message") ?: "Unknown",
            content = TdObjectMapper.toMap(content),
            media = item.media?.let { m ->
                linkedMapOf(
                    "kind" to m.kind.name,
                    "file_id" to m.fileId,
                    "preview_file_id" to m.previewFileId,
                    "width" to m.width,
                    "height" to m.height,
                    "name" to m.name,
                    "mime_type" to m.mimeType,
                    "size" to m.size,
                    "duration" to m.duration,
                    "waveform" to m.waveform,
                )
            },
            replyToMessageId = item.reply?.messageId,
            reactions = item.reactions.map { r ->
                linkedMapOf("emoji" to r.emoji, "count" to r.count, "chosen" to r.chosen)
            },
        )
    }
}
