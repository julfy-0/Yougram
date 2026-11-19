package app.yougram.ota

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Обновление изнутри клиента (OTA). Источник — OTA-сервер на Cloudflare (Worker + R2), адрес в [OtaConfig.serverUrl]:
 *  - GET {serverUrl}/v1/check?channel=…&code=… → есть ли сборка новее установленной (сервер сам выбирает лучшую для канала);
 *  - APK скачивается с того же сервера с докачкой (Range), проверяется размер, sha256, пакет и подпись;
 *  - «что нового» и баннер приходят вместе с ответом проверки.
 * Сервер и публикация релизов — в отдельном репозитории Yougram-ota.
 *
 * Внутри: [OtaClient] — сеть, [ApkVerifier] — проверка файла, [ApkInstaller] — установка; здесь только состояние.
 */
class AppUpdater(
    context: Context,
    private val scope: CoroutineScope,
    private val config: OtaConfig,
) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.cacheDir, "updates")

    private val client = OtaClient(config)
    private val verifier = ApkVerifier(appContext, config)
    private val installer = ApkInstaller(appContext, config)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val prefs = appContext.getSharedPreferences("yougram_update", Context.MODE_PRIVATE)

    private val _channel = MutableStateFlow(
        UpdateChannel.entries.firstOrNull { it.key == prefs.getString("channel", null) }
            ?: if (config.isReleaseBuild) UpdateChannel.Release else UpdateChannel.Beta,
    )
    val channel: StateFlow<UpdateChannel> = _channel.asStateFlow()

    fun setChannel(c: UpdateChannel) {
        if (c == _channel.value || _state.value is UpdateState.Downloading) return
        job?.cancel()
        prefs.edit().putString("channel", c.key).apply()
        _channel.value = c
        _postponed.value = null
        _state.value = UpdateState.Idle
        check()
    }

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
            _state.value = runCatching {
                withContext(Dispatchers.IO) { client.fetchLatest(_channel.value, installId()) }
            }.fold(
                onSuccess = {
                    if (it != null && it.versionCode > config.versionCode) UpdateState.Available(it)
                    else UpdateState.UpToDate
                },
                onFailure = {
                    UpdateState.Error(config.tr("Не удалось проверить обновления: {0}", it.message ?: it.javaClass.simpleName))
                },
            )
        }
    }

    fun download() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        _state.value = UpdateState.Downloading(info, 0f)
        job = scope.launch {
            _state.value = runCatching { withContext(Dispatchers.IO) { downloadApk(info) } }.fold(
                onSuccess = { UpdateState.Ready(info, it) },
                onFailure = {
                    UpdateState.Error(config.tr("Не удалось скачать: {0}", it.message ?: it.javaClass.simpleName))
                },
            )
        }
    }

    /** Запускает установку. Если нет разрешения «Установка неизвестных приложений» — открывает его настройку. */
    fun install(): Boolean {
        val ready = _state.value as? UpdateState.Ready ?: return false
        return installer.install(ready.file)
    }

    // ---------- баннер ----------

    private var bannerCache: Pair<String, Bitmap>? = null

    /** Скачивает баннер релиза (кэшируется до перезапуска). null — если не удалось загрузить. */
    suspend fun loadBanner(url: String): Bitmap? = withContext(Dispatchers.IO) {
        bannerCache?.takeIf { it.first == url }?.second
            ?: runCatching { client.read(url) { BitmapFactory.decodeStream(it) } }
                .getOrNull()
                ?.also { bannerCache = url to it }
    }

    // ---------- скачивание ----------

    /** Случайный идентификатор установки: нужен серверу только для поэтапной раздачи (rollout), личных данных нет. */
    private fun installId(): String =
        prefs.getString("install_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("install_id", it).apply()
        }

    private suspend fun downloadApk(info: UpdateInfo): File {
        dir.mkdirs()
        val target = File(dir, "yougram-${info.versionCode}.apk")
        val tmp = File(dir, "yougram-${info.versionCode}.part") // APK или ZIP — как лежит на сервере
        val unpacked = File(dir, "yougram-${info.versionCode}.apk.tmp")
        // Чужие версии убираем, а свою недокачанную часть оставляем — докачаем с места обрыва.
        dir.listFiles()?.forEach { if (it != target && it != tmp) it.delete() }

        if (target.exists()) {
            if (runCatching { verifier.verify(target, info) }.isSuccess) return target
            target.delete()
        }

        client.download(info, tmp) { _state.value = UpdateState.Downloading(info, it) }

        if (info.packed) {
            try {
                verifier.verifyArchive(tmp, info)
                ApkArchive.extractApk(tmp, unpacked, if (info.sizeBytes > 0) info.sizeBytes else MAX_APK_BYTES, config)
            } catch (e: Throwable) {
                tmp.delete() // повреждённый архив докачивать бессмысленно
                unpacked.delete()
                throw e
            }
            tmp.delete()
            try {
                verifier.verify(unpacked, info)
            } catch (e: Throwable) {
                unpacked.delete()
                throw e
            }
            check(unpacked.renameTo(target)) { config.tr("не удалось сохранить файл") }
            return target
        }

        try {
            verifier.verify(tmp, info)
        } catch (e: Throwable) {
            tmp.delete() // повреждённый файл докачивать бессмысленно
            throw e
        }
        check(tmp.renameTo(target)) { config.tr("не удалось сохранить файл") }
        return target
    }

    private companion object {
        const val MAX_APK_BYTES = 2L * 1024 * 1024 * 1024
    }
}
