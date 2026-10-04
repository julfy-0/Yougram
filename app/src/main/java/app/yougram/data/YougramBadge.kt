package app.yougram.data

/** Невидимая метка в bio, по которой клиенты Yougram узнают друг друга. */
object YougramBadge {
    /** Если Telegram вырежет эти символы, поменяй их здесь на другие невидимые. */
    const val MARKER = "\u2063\u2064\u2062\u2063"

    /** Лимит длины bio в Telegram. */
    const val BIO_LIMIT = 70

    fun hasMarker(bio: String): Boolean = bio.contains(MARKER)

    fun withMarker(bio: String): String = bio + MARKER

    fun strip(bio: String): String = bio.replace(MARKER, "")
}