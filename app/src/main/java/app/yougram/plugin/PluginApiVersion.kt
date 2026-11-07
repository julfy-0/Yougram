package app.yougram.plugin

/**
 * Версия API плагинов. Хост — 2.3; плагины старее 2.1 (включая 1.x и 2.0) не устанавливаются и не загружаются.
 * Принимается major 2 и minor от [MIN_MINOR] до [MINOR].
 */
object PluginApiVersion {
    const val MAJOR = 2
    const val MINOR = 3
    const val CURRENT = "2.3"

    /** Самая старая поддерживаемая версия. */
    const val MIN_MINOR = 1
    const val MIN = "2.1"

    fun parse(value: String): Pair<Int, Int>? {
        val parts = value.trim().split('.')
        if (parts.size !in 1..2) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = if (parts.size == 2) (parts[1].toIntOrNull() ?: return null) else 0
        return Pair(major, minor)
    }

    /** Только 2.[MIN_MINOR]..2.[MINOR]: 1.x и 2.0 отклоняются, версии новее хоста тоже. */
    fun isSupported(value: String): Boolean {
        val (major, minor) = parse(value) ?: return false
        return major == MAJOR && minor in MIN_MINOR..MINOR
    }
}
