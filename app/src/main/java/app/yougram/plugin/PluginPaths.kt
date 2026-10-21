package app.yougram.plugin

import app.yougram.core.i18n.tr
import android.content.Context
import java.io.File

/** Где модуль хранит свои данные. Всё лежит в приватных каталогах приложения. */
object PluginPaths {
    private val UNSAFE = Regex("[^A-Za-z0-9._-]")

    fun safe(id: String): String = id.replace(UNSAFE, "_")

    fun storageName(id: String) = "yougram_plugin_data_" + safe(id)

    fun settingsName(id: String) = "yougram_plugin_settings_" + safe(id)

    fun dataDir(context: Context, id: String): File =
        File(context.filesDir, "plugin_data/" + safe(id)).apply { mkdirs() }

    /** Файл по пути из модуля; допускается только внутри каталога данных этого модуля. */
    fun inside(dir: File, path: String): File {
        val file = File(path).let { if (it.isAbsolute) it else File(dir, path) }
        require(file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) { tr("Путь вне каталога модуля") }
        require(file.isFile) { tr("Файл не найден") }
        return file
    }
}
