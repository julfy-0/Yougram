package app.yougram.core.ui.component

/** Кастомное (премиум) эмодзи в тексте: символы [offset, offset+length) заменяются анимированным стикером [id]. */
data class CustomEmojiSpan(val offset: Int, val length: Int, val id: Long)

/** Ключ встроенного содержимого для [androidx.compose.foundation.text.appendInlineContent]. */
fun customEmojiKey(id: Long): String = "ce:$id"
