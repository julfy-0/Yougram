package app.yougram.feature.chat.data

/** Когда отправить сообщение: в заданный момент или когда собеседник появится в сети. */
sealed interface SendSchedule {
    /** [epochSeconds] — unix-время отправки (сек); не дальше 367 дней вперёд. */
    data class At(val epochSeconds: Int) : SendSchedule

    /** Только личные чаты, где известен точный онлайн-статус собеседника. */
    data object WhenOnline : SendSchedule
}
