package app.yougram.data

/** Невидимая метка в bio, по которой клиенты Yougram узнают друг друга; после неё может идти баннер. */
object YougramBadge {
    /** Если Telegram вырежет эти символы, поменяй их здесь на другие невидимые. */
    const val MARKER = "\u2063\u2064\u2062\u2063"

    /** Лимит длины bio в Telegram. */
    const val BIO_LIMIT = 70

    fun hasMarker(bio: String): Boolean = bio.contains(MARKER)

    /** Метка (и баннер, если задан) в конце bio; прежние метка и баннер заменяются. */
    fun withMarker(bio: String, banner: YougramBanner? = null): String =
        strip(bio) + MARKER + banner?.encode().orEmpty()

    /** Bio без метки и без баннера. */
    fun strip(bio: String): String {
        var s = bio
        while (true) {
            val i = s.indexOf(MARKER)
            if (i < 0) return s
            s = s.removeRange(i, tailEnd(s, i))
        }
    }

    /** Метка вместе с баннером в том виде, как она лежит в bio; пустая строка, если метки нет. */
    fun tail(bio: String): String {
        val i = bio.indexOf(MARKER)
        return if (i < 0) "" else bio.substring(i, tailEnd(bio, i))
    }

    fun bannerOf(bio: String): YougramBanner? {
        val i = bio.indexOf(MARKER)
        return if (i < 0) null else YougramBanner.decode(bio, i + MARKER.length)
    }

    private fun tailEnd(bio: String, markerStart: Int): Int {
        val end = markerStart + MARKER.length
        return if (YougramBanner.hasPayloadAt(bio, end)) end + YougramBanner.PAYLOAD_LENGTH else end
    }
}
