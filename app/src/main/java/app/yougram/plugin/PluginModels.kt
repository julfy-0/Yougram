package app.yougram.plugin

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val api: String = "1.0",
    val author: String = "Unknown",
    val description: String = "",
    /** Имя .so плагина: ищется в lib/<abi>/ внутри пакета. */
    val entry: String = "libplugin.so",
    /** Только для встроенного примера: библиотека лежит в самом APK. */
    val bundled: Boolean = false,
    val permissions: List<String> = emptyList(),
)

data class InstalledPlugin(
    val manifest: PluginManifest,
    val directory: java.io.File,
    val enabled: Boolean,
    val error: String? = null,
)
