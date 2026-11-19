package app.yougram.ota

/**
 * Всё, что модулю нужно знать о приложении. Модуль не видит BuildConfig и локализацию приложения,
 * поэтому приложение передаёт эти данные при создании [AppUpdater].
 */
data class OtaConfig(
    /** Адрес OTA-сервера (https, без «/» в конце), например https://yougram-ota.<аккаунт>.workers.dev. */
    val serverUrl: String,
    /** versionCode установленной сборки: обновлением считается только более высокий. */
    val versionCode: Int,
    /** versionName установленной сборки — для User-Agent и сообщений об ошибках. */
    val versionName: String,
    /** true — сборка стабильного канала, иначе по умолчанию выбирается бета-канал. */
    val isReleaseBuild: Boolean,
    /** Authority FileProvider для установки APK; по умолчанию «<applicationId>.files». */
    val fileProviderAuthority: String? = null,
    /** Перевод строки интерфейса (ключ — русский оригинал). По умолчанию строки не переводятся. */
    val translate: (String) -> String = { it },
)

/** Перевод шаблона: {0}, {1}… заменяются аргументами уже после перевода. */
internal fun OtaConfig.tr(text: String, vararg args: Any?): String {
    var r = translate(text)
    args.forEachIndexed { i, a -> r = r.replace("{$i}", a.toString()) }
    return r
}
