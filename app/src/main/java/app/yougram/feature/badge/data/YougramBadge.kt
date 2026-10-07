package app.yougram.feature.badge.data

/**
 * Метка Yougram в bio: невидимые символы в самом конце, после них (необязательно) закодированный баннер.
 * Обычные клиенты Telegram их не показывают.
 */
object YougramBadge {
    /** Обычный значок Yougram. */
    const val MARKER = "\u2060\u2062\u2060"

    /** Особый золотой значок помощника проекта. */
    const val GOLD_MARKER = "\u2060\u2063\u2060"

    /** Особый синий значок создателя Yougram. */
    const val CREATOR_MARKER = "\u2060\u2064\u2060"

    /** Telegram ID создателя клиента Yougram (синий бейдж). */
    const val CREATOR_USER_ID = 5558165896L

    /** Telegram ID помощников проекта (золотой бейдж). */
    val GOLD_USER_IDS = setOf(7160478740L)

    /** Лимит длины bio в Telegram (без Premium). */
    const val BIO_LIMIT = 70

    private fun markerIndex(bio: String): Int {
        val i1 = bio.lastIndexOf(MARKER)
        val i2 = bio.lastIndexOf(GOLD_MARKER)
        val i3 = bio.lastIndexOf(CREATOR_MARKER)
        val i = maxOf(i1, maxOf(i2, i3))
        if (i < 0) return -1
        val markerLen = when (i) {
            i3 -> CREATOR_MARKER.length
            i2 -> GOLD_MARKER.length
            else -> MARKER.length
        }
        val payloadStart = i + markerLen
        val rest = bio.length - payloadStart
        return if (rest == 0 || (rest == YougramBanner.PAYLOAD_LENGTH && YougramBanner.hasPayloadAt(bio, payloadStart))) i else -1
    }

    fun hasMarker(bio: String): Boolean = markerIndex(bio) >= 0

    fun isGoldUser(userId: Long, bio: String): Boolean =
        userId in GOLD_USER_IDS || hasGoldMarker(bio)

    fun isCreatorUser(userId: Long, bio: String): Boolean =
        userId == CREATOR_USER_ID || hasCreatorMarker(bio)

    fun hasGoldMarker(bio: String): Boolean {
        val i = bio.lastIndexOf(GOLD_MARKER)
        if (i < 0) return false
        val payloadStart = i + GOLD_MARKER.length
        val rest = bio.length - payloadStart
        return rest == 0 || (rest == YougramBanner.PAYLOAD_LENGTH && YougramBanner.hasPayloadAt(bio, payloadStart))
    }

    fun hasCreatorMarker(bio: String): Boolean {
        val i = bio.lastIndexOf(CREATOR_MARKER)
        if (i < 0) return false
        val payloadStart = i + CREATOR_MARKER.length
        val rest = bio.length - payloadStart
        return rest == 0 || (rest == YougramBanner.PAYLOAD_LENGTH && YougramBanner.hasPayloadAt(bio, payloadStart))
    }

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
        if (i < 0) return null
        val markerLen = when {
            bio.startsWith(CREATOR_MARKER, i) -> CREATOR_MARKER.length
            bio.startsWith(GOLD_MARKER, i) -> GOLD_MARKER.length
            else -> MARKER.length
        }
        return YougramBanner.decode(bio, i + markerLen)
    }
}