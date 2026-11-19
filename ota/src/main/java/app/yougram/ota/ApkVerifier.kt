package app.yougram.ota

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

/** Проверка скачанного APK перед установкой: размер, sha256, пакет, версия и подпись. */
internal class ApkVerifier(context: Context, private val config: OtaConfig) {
    private val appContext = context.applicationContext

    /** Бросает IllegalStateException с понятным текстом, если файл ставить нельзя. */
    fun verify(file: File, info: UpdateInfo) {
        if (info.sizeBytes > 0) {
            check(file.length() == info.sizeBytes) {
                config.tr("файл скачался не полностью ({0} из {1} байт)", file.length(), info.sizeBytes)
            }
        }
        info.sha256?.let { expected ->
            check(sha256(file).equals(expected, ignoreCase = true)) { config.tr("контрольная сумма не совпала, файл повреждён") }
        }

        val pm = appContext.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val apk = pm.getPackageArchiveInfo(file.path, flags) ?: error(config.tr("файл не похож на APK"))
        check(apk.packageName == appContext.packageName) { config.tr("чужой пакет {0}", apk.packageName) }

        val installedCode = config.versionCode.toLong()
        check(apk.longVersionCode > installedCode) {
            config.tr("в релизе {0} лежит APK {1} (код {2}), ", info.versionName, apk.versionName, apk.longVersionCode) +
                config.tr("а установлена {0} (код {1}). Загрузите в релиз свежую сборку", config.versionName, installedCode)
        }

        val installed = pm.getPackageInfo(appContext.packageName, flags)
        check(signers(apk).intersect(signers(installed)).isNotEmpty()) {
            config.tr("APK подписан другим ключом, Android не установит его поверх. Соберите release тем же ключом yougram.jks")
        }
    }

    /** Проверка скачанного ZIP-архива (до распаковки): размер и sha256 самого архива. */
    fun verifyArchive(file: File, info: UpdateInfo) {
        if (info.packedSizeBytes > 0) {
            check(file.length() == info.packedSizeBytes) {
                config.tr("архив скачался не полностью ({0} из {1} байт)", file.length(), info.packedSizeBytes)
            }
        }
        info.packedSha256?.let { expected ->
            check(sha256(file).equals(expected, ignoreCase = true)) { config.tr("контрольная сумма не совпала, файл повреждён") }
        }
    }

    private fun signers(info: PackageInfo): Set<String> {
        val si = info.signingInfo ?: return emptySet()
        val certs = if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        return certs.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
