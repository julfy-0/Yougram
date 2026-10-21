package app.yougram.feature.update.data

import app.yougram.core.i18n.tr
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.yougram.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    val sizeBytes: Long = 0L,
    val sha256: String? = null,
    /** Картинка 1000×400 из файлов релиза (banner.png / banner.jpg / banner.webp), если приложена. */
    val bannerUrl: String? = null,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Обновление изнутри клиента. Источник правды — последний релиз на GitHub:
 *  - тег релиза (v1.1.0) = версия, сравнивается с BuildConfig.VERSION_NAME;
 *  - первый файл *.apk из релиза = то, что скачивается;
 *  - описание релиза = «что нового».
 */
class AppUpdater(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.cacheDir, "updates")

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _postponed = MutableStateFlow<String?>(null)
    /** Версия, для которой пользователь нажал «Позже» (до перезапуска приложения). */
    val postponedVersion: StateFlow<String?> = _postponed.asStateFlow()

    fun postpone() {
        val info = when (val s = _state.value) {
            is UpdateState.Available -> s.info
            is UpdateState.Ready -> s.info
            else -> return
        }
        _postponed.value = info.versionName
    }

    private var job: Job? = null

    fun check() {
        if (job?.isActive == true) return
        // Уже скачанное или качающееся обновление не сбрасываем повторной проверкой.
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Ready) return
        _state.value = UpdateState.Checking
        job = scope.launch {
            _state.value = runCatching { withContext(Dispatchers.IO) { fetchLatest() } }.fold(
                onSuccess = {
                    if (isNewer(it.versionName, BuildConfig.VERSION_NAME)) UpdateState.Available(it)
                    else UpdateState.UpToDate
                },
                onFailure = { UpdateState.Error(tr("Не удалось проверить обновления: {0}", it.message ?: it.javaClass.simpleName)) },
            )
        }
    }

    fun download() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        _state.value = UpdateState.Downloading(info, 0f)
        job = scope.launch {
            _state.value = runCatching { withContext(Dispatchers.IO) { downloadApk(info) } }.fold(
                onSuccess = { UpdateState.Ready(info, it) },
                onFailure = { UpdateState.Error(tr("Не удалось скачать: {0}", it.message ?: it.javaClass.simpleName)) },
            )
        }
    }


    /** Запускает установку. Если нет разрешения «Установка неизвестных приложений» — открывает его настройку. */
    fun install(): Boolean {
        val ready = _state.value as? UpdateState.Ready ?: return false
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            appContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", ready.file)
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    // ---------- баннер ----------

    private var bannerCache: Pair<String, Bitmap>? = null

    /** Скачивает баннер релиза (кэшируется до перезапуска). null — если не удалось загрузить. */
    suspend fun loadBanner(url: String): Bitmap? = withContext(Dispatchers.IO) {
        bannerCache?.takeIf { it.first == url }?.second ?: runCatching {
            val conn = open(url, api = false)
            try {
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()?.also { bannerCache = url to it }
    }

    // ---------- проверка ----------

    private fun fetchLatest(): UpdateInfo {
        val conn = open("https://api.github.com/repos/$REPO/releases/latest", api = true)
        try {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val assets = json.getJSONArray("assets")
            var asset: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk", ignoreCase = true)) {
                    // Если файлов несколько, берём тот, что называется yougram.apk.
                    if (asset == null || a.getString("name").equals("yougram.apk", ignoreCase = true)) asset = a
                }
            }
            asset ?: error(tr("в релизе {0} нет файла .apk", tag))
            val banner = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { BANNER_NAME.matches(it.getString("name")) }
            val digest = asset.optString("digest")
            return UpdateInfo(
                versionName = tag.removePrefix("v").removePrefix("V"),
                apkUrl = asset.getString("browser_download_url"),
                notes = json.optString("body"),
                sizeBytes = asset.optLong("size", 0L),
                sha256 = digest.removePrefix("sha256:").takeIf { digest.startsWith("sha256:") && it.length == 64 },
                bannerUrl = banner?.getString("browser_download_url"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Сравнение по числам: 1.10.0 > 1.9.3, суффиксы вроде -beta игнорируются. */
    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = Regex("\\d+").findAll(v).map { it.value.toIntOrNull() ?: 0 }.toList()
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    // ---------- скачивание и проверка файла ----------

    private suspend fun downloadApk(info: UpdateInfo): File {
        dir.deleteRecursively()
        dir.mkdirs()
        val target = File(dir, "yougram-${info.versionName}.apk")
        val tmp = File(dir, "download.part")
        val conn = open(info.apkUrl, api = false)
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            var done = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0L) {
                            _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        verify(tmp, info)
        check(tmp.renameTo(target)) { tr("не удалось сохранить файл") }
        return target
    }

    private fun verify(file: File, info: UpdateInfo) {
        if (info.sizeBytes > 0) {
            check(file.length() == info.sizeBytes) { tr("файл скачался не полностью ({0} из {1} байт)", file.length(), info.sizeBytes) }
        }
        info.sha256?.let { expected ->
            check(sha256(file).equals(expected, ignoreCase = true)) { tr("контрольная сумма не совпала, файл повреждён") }
        }

        val pm = appContext.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val apk = pm.getPackageArchiveInfo(file.path, flags) ?: error(tr("файл не похож на APK"))
        check(apk.packageName == appContext.packageName) { tr("чужой пакет {0}", apk.packageName) }

        val installedCode = BuildConfig.VERSION_CODE.toLong()
        check(apk.longVersionCode > installedCode) {
            tr("в релизе {0} лежит APK {1} (код {2}), ", info.versionName, apk.versionName, apk.longVersionCode) +
                    tr("а установлена {0} (код {1}). Загрузите в релиз свежую сборку", BuildConfig.VERSION_NAME, installedCode)
        }

        val installed = pm.getPackageInfo(appContext.packageName, flags)
        check(signers(apk).intersect(signers(installed)).isNotEmpty()) {
            tr("APK подписан другим ключом, Android не установит его поверх. Соберите release тем же ключом yougram.jks")
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

    // ---------- сеть ----------

    private fun open(url: String, api: Boolean): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Yougram/${BuildConfig.VERSION_NAME}")
        if (api) conn.setRequestProperty("Accept", "application/vnd.github+json")
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            error(
                when (code) {
                    403, 429 -> tr("лимит запросов GitHub, попробуйте позже")
                    404 -> tr("релиз не найден (HTTP 404)")
                    else -> "HTTP $code"
                },
            )
        }
        return conn
    }

    private companion object {
        const val REPO = "julfy-0/Yougram"
        val BANNER_NAME = Regex("^banner.*\\.(png|jpe?g|webp)$", RegexOption.IGNORE_CASE)
    }
}