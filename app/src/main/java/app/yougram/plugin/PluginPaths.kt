package app.yougram.plugin

import android.content.Context
import java.io.File

/** Где плагин хранит свои данные. Всё лежит в приватных каталогах приложения. */
object PluginPaths {
    private val UNSAFE = Regex("[^A-Za-z0-9._-]")

    fun safe(id: String): String = id.replace(UNSAFE, "_")

    fun storageName(id: String) = "yougram_plugin_data_" + safe(id)

    fun settingsName(id: String) = "yougram_plugin_settings_" + safe(id)

    fun dataDir(context: Context, id: String): File =
        File(context.filesDir, "plugin_data/" + safe(id)).apply { mkdirs() }

    /** Файл по пути из плагина; допускается только внутри каталога данных этого плагина. */
    fun inside(dir: File, path: String): File {
        val file = File(path).let { if (it.isAbsolute) it else File(dir, path) }
        require(file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) { "Путь вне каталога плагина" }
        require(file.isFile) { "Файл не найден" }
        return file
    }
}
