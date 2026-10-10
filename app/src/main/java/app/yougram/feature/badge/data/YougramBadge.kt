package app.yougram.feature.badge.data

import app.yougram.BuildConfig

/**
 * Метка Yougram в bio: невидимые символы в самом конце, после них баннер (6 символов)
 * и версия клиента (6 символов). Обычные клиенты Telegram их не показывают.
 */
object YougramBadge {
    const val MARKER = "⁠⁢⁠"
    const val GOLD_MARKER = "⁠⁣⁠"
    const val CREATOR_MARKER = "⁠⁤⁠"
    const val CREATOR_USER_ID = 5558165896L
    val GOLD_USER_IDS = setOf(7160478740L, 6502820601L, 1472717379L, 5019526707L)
    const val BIO_LIMIT = 70

    private const val VERSION_LENGTH = 6

    /** Баннер «не выбран»: поле узора = 15, декодер возвращает null. */
    val NO_BANNER: String = YougramBanner(pattern = 15).encode()

    /** Версия текущего клиента (major.minor.patch, каждое 0..15) невидимыми символами. */
    fun versionPayload(): String {
        val p = BuildConfig.VERSION_NAME.split(".").map { it.toIntOrNull() ?: 0 }
        val bits = (p.getOrElse(0) { 0 }.coerceIn(0, 15)) or
                (p.getOrElse(1) { 0 }.coerceIn(0, 15) shl 4) or
                (p.getOrElse(2) { 0 }.coerceIn(0, 15) shl 8)
        return buildString { for (i in 0 until VERSION_LENGTH) append(YougramBanner.ALPHABET[(bits shr (2 * i)) and 3]) }
    }

    private fun hasVersionAt(text: String, start: Int): Boolean {
        if (start < 0 || start + VERSION_LENGTH > text.length) return false
        for (i in start until start + VERSION_LENGTH) if (YougramBanner.ALPHABET.indexOf(text[i]) < 0) return false
        return true
    }

    /** Допустимые хвосты: пусто, только баннер, либо баннер + версия. */
    private fun tailValid(bio: String, payloadStart: Int): Boolean {
        val rest = bio.length - payloadStart
        val b = YougramBanner.PAYLOAD_LENGTH
        return when (rest) {
            0 -> true
            b -> YougramBanner.hasPayloadAt(bio, payloadStart)
            b + VERSION_LENGTH -> YougramBanner.hasPayloadAt(bio, payloadStart) && hasVersionAt(bio, payloadStart + b)
            else -> false
        }
    }

    private fun markerIndex(bio: String): Int {
        val i = maxOf(bio.lastIndexOf(MARKER), bio.lastIndexOf(GOLD_MARKER), bio.lastIndexOf(CREATOR_MARKER))
        if (i < 0) return -1
        return if (tailValid(bio, i + MARKER.length)) i else -1
    }

    fun hasMarker(bio: String): Boolean = markerIndex(bio) >= 0

    fun isGoldUser(userId: Long, bio: String): Boolean = userId in GOLD_USER_IDS || hasGoldMarker(bio)

    fun isCreatorUser(userId: Long, bio: String): Boolean = userId == CREATOR_USER_ID || hasCreatorMarker(bio)

    fun hasGoldMarker(bio: String): Boolean {
        val i = bio.lastIndexOf(GOLD_MARKER)
        return i >= 0 && tailValid(bio, i + GOLD_MARKER.length)
    }

    fun hasCreatorMarker(bio: String): Boolean {
        val i = bio.lastIndexOf(CREATOR_MARKER)
        return i >= 0 && tailValid(bio, i + CREATOR_MARKER.length)
    }

    /** Хвост bio: метка + баннер (+ версия), либо пустая строка. */
    fun tail(bio: String): String {
        val i = markerIndex(bio)
        return if (i < 0) "" else bio.substring(i)
    }

    /** Bio без метки, баннера и версии. */
    fun strip(bio: String): String {
        val i = markerIndex(bio)
        return if (i < 0) bio else bio.substring(0, i)
    }

    fun bannerOf(bio: String): YougramBanner? {
        val i = markerIndex(bio)
        if (i < 0) return null
        return YougramBanner.decode(bio, i + MARKER.length)
    }

    /** Версия клиента собеседника ("0.9.2") или null, если метка старая и версии в ней нет. */
    fun versionOf(bio: String): String? {
        val i = markerIndex(bio)
        if (i < 0) return null
        val start = i + MARKER.length + YougramBanner.PAYLOAD_LENGTH
        if (bio.length - start != VERSION_LENGTH || !hasVersionAt(bio, start)) return null
        var bits = 0
        for (k in 0 until VERSION_LENGTH) bits = bits or (YougramBanner.ALPHABET.indexOf(bio[start + k]) shl (2 * k))
        return "${bits and 15}.${(bits shr 4) and 15}.${(bits shr 8) and 15}"
    }
}