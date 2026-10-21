package app.yougram.plugin

import app.yougram.core.i18n.tr
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Чтение и проверка manifest.json (API 1.0 и 2.0). */
object PluginManifestReader {
    private val SETTING_KEY = Regex("[a-z0-9_]{1,40}")
    private const val MAX_SETTINGS = 50

    /** Манифест каталога или null, если файла нет/он повреждён. */
    fun read(dir: File): PluginManifest? = runCatching { readOrThrow(dir) }.getOrNull()

    /** То же, но с понятным текстом ошибки — для установки пакета. */
    fun readOrThrow(dir: File): PluginManifest {
        val file = File(dir, "manifest.json")
        require(file.isFile) { tr("manifest.json отсутствует") }
        return try {
            parse(JSONObject(file.readText()))
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException(tr("manifest.json повреждён: {0}", e.message))
        }
    }

    fun parse(json: JSONObject): PluginManifest = PluginManifest(
        id = json.getString("id"),
        name = json.getString("name"),
        version = json.getString("version"),
        api = json.optString("api", "1.0"),
        author = json.optString("author", "Unknown"),
        description = json.optString("description", ""),
        entry = json.optString("entry", "libplugin.so"),
        bundled = json.optBoolean("bundled", false),
        permissions = strings(json.optJSONArray("permissions")),
        priority = json.optInt("priority", 0),
        settings = settings(json.optJSONArray("settings")),
    )

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return List(array.length()) { array.getString(it) }
    }

    private fun settings(array: JSONArray?): List<PluginSetting> {
        if (array == null) return emptyList()
        require(array.length() <= MAX_SETTINGS) { tr("Слишком много настроек (максимум {0})", MAX_SETTINGS) }
        val out = ArrayList<PluginSetting>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val key = item.getString("key")
            require(SETTING_KEY.matches(key)) { tr("Недопустимый ключ настройки: {0}", key) }
            require(out.none { it.key == key }) { tr("Ключ настройки повторяется: {0}", key) }
            val type = when (item.optString("type", "text").lowercase()) {
                "switch" -> PluginSettingType.SWITCH
                "text" -> PluginSettingType.TEXT
                "number" -> PluginSettingType.NUMBER
                "choice" -> PluginSettingType.CHOICE
                else -> throw IllegalArgumentException(tr("Неизвестный тип настройки «{0}»", key))
            }
            val options = options(item.optJSONArray("options"))
            if (type == PluginSettingType.CHOICE) require(options.isNotEmpty()) { tr("У настройки «{0}» нет options", key) }
            val default = when {
                !item.has("default") && type == PluginSettingType.SWITCH -> "false"
                !item.has("default") && type == PluginSettingType.NUMBER -> "0"
                !item.has("default") && type == PluginSettingType.CHOICE -> options.first().value
                !item.has("default") -> ""
                else -> item.get("default").toString()
            }
            out += PluginSetting(
                key = key,
                title = item.optString("title", key),
                type = type,
                default = default,
                description = item.optString("description", ""),
                options = options,
                min = item.optDouble("min", Double.NaN).takeUnless { it.isNaN() },
                max = item.optDouble("max", Double.NaN).takeUnless { it.isNaN() },
            )
        }
        return out
    }

    private fun options(array: JSONArray?): List<PluginSettingOption> {
        if (array == null) return emptyList()
        return List(array.length()) { i ->
            val raw = array.get(i)
            if (raw is JSONObject) {
                val value = raw.getString("value")
                PluginSettingOption(value, raw.optString("title", value))
            } else {
                PluginSettingOption(raw.toString(), raw.toString())
            }
        }
    }
}
