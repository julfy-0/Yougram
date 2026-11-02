package app.yougram.feature.assistant.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed interface AiModelState {
    data object Missing : AiModelState
    data class Downloading(val downloaded: Long, val total: Long) : AiModelState {
        val progress: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data class Ready(val file: File) : AiModelState
    data class Error(val message: String) : AiModelState
}

/**
 * Загрузка файлов моделей с нашего хостинга во внутреннее хранилище. Для каждой модели свой файл,
 * выбранная модель запоминается. Докачка по Range, проверка размера, отмена через отмену корутины.
 * Недокачанный файл лежит как *.part и подхватывается при следующем запуске.
 */
class AiModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "ai").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences("yougram_ai", Context.MODE_PRIVATE)

    private val _selected = MutableStateFlow(AiModel.fromId(prefs.getString("model", null)))
    val selected: StateFlow<AiModel> = _selected.asStateFlow()

    private val _state = MutableStateFlow<AiModelState>(AiModelState.Missing)
    val state: StateFlow<AiModelState> = _state.asStateFlow()

    init { _state.value = initialState(_selected.value) }

    val isConfigured: Boolean get() = _selected.value.configured

    private fun target(m: AiModel) =
        File(dir, m.url.substringAfterLast('/').substringBefore('?').ifBlank { "${m.id}.task" })

    private fun part(m: AiModel) = File(dir, target(m).name + ".part")

    private fun sizeOk(m: AiModel, len: Long) = len > 0 && (m.expectedSize <= 0 || len == m.expectedSize)

    private fun initialState(m: AiModel): AiModelState =
        if (m.configured && target(m).isFile && sizeOk(m, target(m).length())) AiModelState.Ready(target(m))
        else AiModelState.Missing

    fun isDownloaded(m: AiModel): Boolean = initialState(m) is AiModelState.Ready

    /** Переключает модель. Активную загрузку/генерацию отменяет вызывающий (см. AiAssistant.select). */
    fun select(model: AiModel) {
        prefs.edit().putString("model", model.id).apply()
        _selected.value = model
        _state.value = initialState(model)
    }

    /** Пишем состояние, только если пользователь не переключился на другую модель. */
    private fun publish(m: AiModel, s: AiModelState) {
        if (_selected.value == m) _state.value = s
    }

    suspend fun download() = withContext(Dispatchers.IO) {
        val model = _selected.value
        if (!model.configured) {
            publish(model, AiModelState.Error("Ссылка на модель «${model.title}» не задана в local.properties"))
            return@withContext
        }
        if (_state.value is AiModelState.Ready) return@withContext
        val target = target(model)
        val part = part(model)
        try {
            var have = if (part.isFile) part.length() else 0L
            val conn = (URL(model.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true // GitHub отдаёт 302 на CDN
                if (have > 0) setRequestProperty("Range", "bytes=$have-")
            }
            try {
                val code = conn.responseCode
                when (code) {
                    200 -> have = 0L // сервер не поддержал Range — качаем сначала
                    206 -> {}
                    416 -> { part.delete(); throw IOException("Сервер отклонил докачку, повторите загрузку") }
                    else -> throw IOException("HTTP $code")
                }
                val total = if (code == 206) have + conn.contentLengthLong else conn.contentLengthLong
                RandomAccessFile(part, "rw").use { out ->
                    out.setLength(have)
                    out.seek(have)
                    val buf = ByteArray(64 * 1024)
                    var done = have
                    var lastEmit = 0L
                    conn.inputStream.use { input ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 200) {
                                lastEmit = now
                                publish(model, AiModelState.Downloading(done, total))
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            if (!sizeOk(model, part.length())) {
                part.delete()
                throw IOException("Размер файла не совпадает (${part.length()} байт), загрузите заново")
            }
            target.delete()
            if (!part.renameTo(target)) throw IOException("Не удалось сохранить модель")
            publish(model, AiModelState.Ready(target))
        } catch (e: kotlinx.coroutines.CancellationException) {
            publish(model, initialState(model))
            throw e
        } catch (e: Exception) {
            publish(model, AiModelState.Error(e.message ?: "Ошибка загрузки"))
        }
    }

    /** Удаляет файлы выбранной модели. */
    fun delete() {
        val m = _selected.value
        target(m).delete()
        part(m).delete()
        _state.value = AiModelState.Missing
    }
}
