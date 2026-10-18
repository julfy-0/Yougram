package app.yougram.feature.chat.ui

import app.yougram.feature.chat.data.MediaItem
import app.yougram.feature.chat.data.MessageItem

/**
 * Строка списка чата: одно сообщение или альбом, склеенный в один пузырь.
 * [key] — id самого старого сообщения альбома (стабилен, пока приходят новые части),
 * [message] — сообщение, от имени которого работают подпись, реакции и меню (с подписью, иначе самое старое),
 * [albumMedia] — медиа альбома от старых к новым (пусто, если это не альбом), [ids] — id всех сообщений строки.
 */
data class ChatEntry(
    val key: Long,
    val message: MessageItem,
    val albumMedia: List<MediaItem>,
    val ids: Set<Long>,
)
