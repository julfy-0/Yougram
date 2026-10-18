package app.yougram.plugin

/** Версия API плагинов. Манифест с тем же major и minor не выше хостового считается совместимым. */
object PluginApiVersion {
    const val MAJOR = 2
    const val MINOR = 0
    const val CURRENT = "2.0"

    fun parse(value: String): Pair<Int, Int>? {
        val parts = value.trim().split('.')
        if (parts.size !in 1..2) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = if (parts.size == 2) (parts[1].toIntOrNull() ?: return null) else 0
        return Pair(major, minor)
    }

    /** 1.x (старые плагины) и 2.0..2.[MINOR]. */
    fun isSupported(value: String): Boolean {
        val (major, minor) = parse(value) ?: return false
        return major in 1 until MAJOR || (major == MAJOR && minor <= MINOR)
    }
}
