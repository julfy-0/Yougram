package app.yougram.data

/**
 * Метка Yougram в bio: невидимые символы в самом конце, после них (необязательно) закодированный баннер.
 * Обычные клиенты Telegram их не показывают.
 */
object YougramBadge {
    /** Символы U+2060 не входят в алфавит баннера, поэтому метка не спутается с его данными. */
    const val MARKER = "\u2060\u2062\u2060"

    /** Лимит длины bio в Telegram (без Premium). */
    const val BIO_LIMIT = 70

    private fun markerIndex(bio: String): Int {
        val i = bio.lastIndexOf(MARKER)
        if (i < 0) return -1
        val payloadStart = i + MARKER.length
        val rest = bio.length - payloadStart
        return if (rest == 0 || (rest == YougramBanner.PAYLOAD_LENGTH && YougramBanner.hasPayloadAt(bio, payloadStart))) i else -1
    }

    fun hasMarker(bio: String): Boolean = markerIndex(bio) >= 0

    /** Хвост bio: метка + баннер, либо пустая строка. */
    fun tail(bio: String): String {
        val i = markerIndex(bio)
        return if (i < 0) "" else bio.substring(i)
    }

    /** Bio без метки и баннера. */
    fun strip(bio: String): String {
        val i = markerIndex(bio)
        return if (i < 0) bio else bio.substring(0, i)
    }

    fun bannerOf(bio: String): YougramBanner? {
        val i = markerIndex(bio)
        return if (i < 0) null else YougramBanner.decode(bio, i + MARKER.length)
    }
}