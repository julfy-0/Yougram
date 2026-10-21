package app.yougram.plugin

import app.yougram.core.i18n.tr
import android.content.Context

/** Значения настроек плагинов (по схеме из manifest.json). Хранятся в строковом виде, наружу отдаются типизированными. */
class PluginSettingsStore(private val context: Context) {
    private companion object {
        const val MAX_TEXT = 4096
    }

    private fun prefs(pluginId: String) =
        context.getSharedPreferences(PluginPaths.settingsName(pluginId), Context.MODE_PRIVATE)

    private fun setting(manifest: PluginManifest, key: String): PluginSetting =
        manifest.settings.firstOrNull { it.key == key } ?: throw IllegalArgumentException(tr("Нет настройки «{0}»", key))

    /** Текущее значение в строковом виде (или значение по умолчанию). */
    fun raw(manifest: PluginManifest, key: String): String {
        val s = setting(manifest, key)
        return prefs(manifest.id).getString(key, null) ?: s.default
    }

    /** Текущее значение: Boolean для switch, число для number, строка для остальных. */
    fun get(manifest: PluginManifest, key: String): Any {
        val s = setting(manifest, key)
        return typed(s, raw(manifest, key))
    }

    fun all(manifest: PluginManifest): Map<String, Any> =
        manifest.settings.associate { it.key to typed(it, raw(manifest, it.key)) }

    /**
     * Проверяет все значения и только потом записывает. Возвращает изменившиеся настройки (ключ -> новое значение).
     * При ошибке в любом значении бросает IllegalArgumentException и ничего не пишет.
     */
    fun setAll(manifest: PluginManifest, values: Map<String, String>): Map<String, Any> {
        val normalized = LinkedHashMap<String, String>()
        for ((key, value) in values) normalized[key] = normalize(setting(manifest, key), value)
        val changed = LinkedHashMap<String, Any>()
        val edit = prefs(manifest.id).edit()
        for ((key, value) in normalized) {
            if (value == raw(manifest, key)) continue
            edit.putString(key, value)
            changed[key] = typed(setting(manifest, key), value)
        }
        edit.apply()
        return changed
    }

    fun clear(pluginId: String) {
        context.deleteSharedPreferences(PluginPaths.settingsName(pluginId))
    }

    private fun normalize(s: PluginSetting, value: String): String = when (s.type) {
        PluginSettingType.SWITCH -> {
            require(value == "true" || value == "false") { tr("«{0}»: ожидается true или false", s.title) }
            value
        }
        PluginSettingType.NUMBER -> {
            val n = value.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: throw IllegalArgumentException(tr("«{0}»: ожидается число", s.title))
            s.min?.let { require(n >= it) { tr("«{0}»: не меньше {1}", s.title, format(it)) } }
            s.max?.let { require(n <= it) { tr("«{0}»: не больше {1}", s.title, format(it)) } }
            format(n)
        }
        PluginSettingType.CHOICE -> {
            require(s.options.any { it.value == value }) { tr("«{0}»: недопустимый вариант", s.title) }
            value
        }
        PluginSettingType.TEXT -> {
            require(value.length <= MAX_TEXT) { tr("«{0}»: слишком длинный текст", s.title) }
            value
        }
    }

    private fun typed(s: PluginSetting, value: String): Any = when (s.type) {
        PluginSettingType.SWITCH -> value == "true"
        PluginSettingType.NUMBER -> value.toDoubleOrNull()?.let { if (it % 1.0 == 0.0 && kotlin.math.abs(it) < 1e15) it.toLong() else it } ?: 0L
        else -> value
    }

    private fun format(n: Double): String = if (n % 1.0 == 0.0 && kotlin.math.abs(n) < 1e15) n.toLong().toString() else n.toString()
}
