package app.yougram.feature.chat.data

/** Запланированное сообщение в списке «Запланированные». */
data class ScheduledItem(
    val id: Long,
    val chatId: Long,
    /** Текст или краткое описание медиа. */
    val text: String,
    /** Unix-время отправки (сек); 0, если сообщение уйдёт, когда собеседник будет в сети. */
    val sendDate: Int,
    val whenOnline: Boolean,
)
