package app.yougram.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.yougram.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

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
 * Обновление приложения изнутри клиента. По адресу [BuildConfig.UPDATE_URL] лежит JSON:
 * {"versionCode": 2, "versionName": "0.2.0", "apkUrl": "https://.../yougram.apk", "notes": "что нового"}.
 * Если versionCode больше установленного — APK скачивается в кэш и отдаётся системному установщику.
 */
class AppUpdater(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.cacheDir, "updates")

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null

    fun check() {
        if (job?.isActive == true) return
        val url = BuildConfig.UPDATE_URL
        if (!url.startsWith("https://")) {
            _state.value = UpdateState.Error("Адрес обновлений не задан (UPDATE_URL в local.properties)")
            return
        }
        _state.value = UpdateState.Checking
        job = scope.launch {
            _state.value = runCatching { withContext(Dispatchers.IO) { fetchInfo(url) } }.fold(
                onSuccess = { if (it.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(it) else UpdateState.UpToDate },
                onFailure = { UpdateState.Error("Не удалось проверить обновления: ${it.message ?: it.javaClass.simpleName}") },
            )
        }
    }

    fun download() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        if (!info.apkUrl.startsWith("https://")) {
            _state.value = UpdateState.Error("Ссылка на APK должна начинаться с https://")
            return
        }
        _state.value = UpdateState.Downloading(info, 0f)
        job = scope.launch {
            _state.value = runCatching { withContext(Dispatchers.IO) { downloadApk(info) } }.fold(
                onSuccess = { UpdateState.Ready(info, it) },
                onFailure = { UpdateState.Error("Не удалось скачать: ${it.message ?: it.javaClass.simpleName}") },
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

    private fun fetchInfo(url: String): UpdateInfo {
        val conn = open(url)
        try {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            return UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.optString("versionName"),
                apkUrl = json.getString("apkUrl"),
                notes = json.optString("notes"),
            )
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun downloadApk(info: UpdateInfo): File {
        dir.deleteRecursively()
        dir.mkdirs()
        val target = File(dir, "yougram-${info.versionCode}.apk")
        val tmp = File(dir, "download.part")
        val conn = open(info.apkUrl)
        try {
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (!scope.isActive) error("отменено")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0L) {
                            val progress = (done.toFloat() / total).coerceIn(0f, 1f)
                            _state.value = UpdateState.Downloading(info, progress)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        // Проверяем, что это наш APK и версия действительно новее.
        val pkg = appContext.packageManager.getPackageArchiveInfo(tmp.path, 0)
            ?: error("файл не похож на APK")
        check(pkg.packageName == appContext.packageName) { "чужой пакет ${pkg.packageName}" }
        check(pkg.longVersionCode > BuildConfig.VERSION_CODE) { "в APK нет новой версии" }
        check(tmp.renameTo(target)) { "не удалось сохранить файл" }
        return target
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
        return conn
    }
}