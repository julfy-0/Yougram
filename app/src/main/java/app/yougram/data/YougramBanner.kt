package app.yougram.data

/**
 * Баннер профиля, который рисуют только клиенты Yougram.
 *
 * Хранится в bio сразу после [YougramBadge.MARKER] как [PAYLOAD_LENGTH] невидимых символов
 * (по 2 бита на символ): палитра, узор и форма градиента. Обычные клиенты Telegram видят в bio
 * только свой текст, поэтому баннер для них не существует.
 */
data class YougramBanner(
    val palette: Int = 0,
    val pattern: Int = 0,
    val shape: Int = 0,
) {
    fun encode(): String {
        val bits = (palette and 15) or ((pattern and 15) shl 4) or ((shape and 15) shl 8)
        return buildString {
            for (i in 0 until PAYLOAD_LENGTH) append(ALPHABET[(bits shr (2 * i)) and 3])
        }
    }

    companion object {
        const val PAYLOAD_LENGTH = 6
        const val PALETTES = 16
        const val PATTERNS = 6
        const val SHAPES = 4

        /** Невидимые символы из того же блока, что и метка Yougram. */
        private const val ALPHABET = "\u2061\u2062\u2063\u2064"

        fun hasPayloadAt(text: String, start: Int): Boolean {
            if (start < 0 || start + PAYLOAD_LENGTH > text.length) return false
            for (i in start until start + PAYLOAD_LENGTH) if (ALPHABET.indexOf(text[i]) < 0) return false
            return true
        }

        fun decode(text: String, start: Int): YougramBanner? {
            if (!hasPayloadAt(text, start)) return null
            var bits = 0
            for (i in 0 until PAYLOAD_LENGTH) bits = bits or (ALPHABET.indexOf(text[start + i]) shl (2 * i))
            return YougramBanner(
                palette = (bits and 15) % PALETTES,
                pattern = ((bits shr 4) and 15) % PATTERNS,
                shape = ((bits shr 8) and 15) % SHAPES,
            )
        }
    }
}
