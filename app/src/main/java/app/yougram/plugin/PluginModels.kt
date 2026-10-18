package app.yougram.plugin

enum class PluginSettingType { SWITCH, TEXT, NUMBER, CHOICE }

data class PluginSettingOption(val value: String, val title: String)

/** Настройка плагина из manifest.json: экран для неё рисует приложение, плагин читает значение через settings_get. */
data class PluginSetting(
    val key: String,
    val title: String,
    val type: PluginSettingType,
    /** Значение по умолчанию в строковом виде: "true"/"false", число, текст или value варианта. */
    val default: String = "",
    val description: String = "",
    val options: List<PluginSettingOption> = emptyList(),
    val min: Double? = null,
    val max: Double? = null,
)

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val api: String = PluginApiVersion.CURRENT,
    val author: String = "Unknown",
    val description: String = "",
    /** Имя .so плагина: ищется в lib/<abi>/ внутри пакета. */
    val entry: String = "libplugin.so",
    /** Только для встроенного примера: библиотека лежит в самом APK. */
    val bundled: Boolean = false,
    val permissions: List<String> = emptyList(),
    /** Порядок в цепочке хуков: больше — раньше. */
    val priority: Int = 0,
    val settings: List<PluginSetting> = emptyList(),
)

data class InstalledPlugin(
    val manifest: PluginManifest,
    val directory: java.io.File,
    val enabled: Boolean,
    val error: String? = null,
)
