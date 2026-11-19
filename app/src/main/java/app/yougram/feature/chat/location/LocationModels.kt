package app.yougram.feature.chat.location

/** Геопозиция или место в сообщении. [liveUntil] — unix-время конца трансляции (0 — не трансляция или закончена). */
data class LocationItem(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double = 0.0,
    /** Период трансляции в секундах; 0 — обычная геопозиция. */
    val livePeriod: Int = 0,
    val liveUntil: Long = 0L,
    val heading: Int = 0,
    /** Название места (для Venue), иначе пусто. */
    val title: String = "",
    val address: String = "",
) {
    val isLive: Boolean get() = livePeriod > 0
}
