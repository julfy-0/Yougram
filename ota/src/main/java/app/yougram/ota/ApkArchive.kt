package app.yougram.ota

import java.io.File
import java.util.zip.ZipInputStream

/** Распаковка APK из ZIP-архива обновления. Имя записи в архиве как путь не используется — обход каталогов невозможен. */
internal object ApkArchive {
    /** Достаёт первый файл *.apk из [zip] в [out]. [maxBytes] защищает от архива-бомбы. */
    fun extractApk(zip: File, out: File, maxBytes: Long, config: OtaConfig) {
        ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: error(config.tr("в архиве нет APK"))
                if (entry.isDirectory || !entry.name.endsWith(".apk", ignoreCase = true)) continue
                var total = 0L
                out.outputStream().buffered().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = zin.read(buf)
                        if (n < 0) break
                        total += n
                        check(total <= maxBytes) { config.tr("архив распаковывается в файл неожиданного размера") }
                        os.write(buf, 0, n)
                    }
                }
                return
            }
        }
    }
}
