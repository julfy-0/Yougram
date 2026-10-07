package app.yougram.core.settings

import android.content.Context

/** Локальное хранилище черновиков сообщений: по одному на чат (и тему/ветку комментариев). */
object DraftStore {
    private const val FILE = "yougram_drafts"

    private fun key(chatId: Long, topicId: Int, threadId: Long) = "$chatId:$topicId:$threadId"

    fun get(context: Context, chatId: Long, topicId: Int, threadId: Long): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(key(chatId, topicId, threadId), "").orEmpty()

    fun set(context: Context, chatId: Long, topicId: Int, threadId: Long, text: String) {
        val edit = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (text.isBlank()) edit.remove(key(chatId, topicId, threadId))
        else edit.putString(key(chatId, topicId, threadId), text)
        edit.apply()
    }
}
