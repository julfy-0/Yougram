package app.yougram.feature.chat.ui

import app.yougram.feature.chat.data.MessageItem

/** Склеивает подряд идущие сообщения одного альбома. Список — от новых к старым, как в чате. */
fun List<MessageItem>.groupAlbums(): List<ChatEntry> {
    val out = ArrayList<ChatEntry>(size)
    var i = 0
    while (i < size) {
        val first = this[i]
        var j = i + 1
        if (first.albumId != 0L && first.media != null) {
            while (
                j < size &&
                this[j].albumId == first.albumId &&
                this[j].chatId == first.chatId &&
                this[j].media != null
            ) j++
        }
        if (j - i < 2) {
            out += ChatEntry(first.id, first, emptyList(), setOf(first.id))
        } else {
            val group = subList(i, j).asReversed()
            val primary = group.firstOrNull { it.text.isNotEmpty() } ?: group.first()
            out += ChatEntry(
                key = group.first().id,
                message = primary,
                albumMedia = group.mapNotNull { it.media },
                ids = group.map { it.id }.toSet(),
            )
        }
        i = j
    }
    return out
}
