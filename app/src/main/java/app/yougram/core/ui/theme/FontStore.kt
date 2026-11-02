package app.yougram.core.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Хранилище шрифтов Google Fonts: файлы .ttf физически скачиваются в память приложения
 * (filesDir/google_fonts/<имя>/<вес>.ttf) и дальше работают без интернета и без Google Play Services.
 * Скачивание запускается только для тех шрифтов, которые реально нужны: выбранных в настройках
 * или показанных на экране в списке выбора.
 */
object FontStore {
    /** Растёт после каждой успешной загрузки/очистки; от него зависит перерисовка шрифтов. */
    var revision by mutableIntStateOf(0)
        private set

    private var appContext: Context? = null
    private val loading = Semaphore(3)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private const val RETRY_AFTER_MS = 60_000L
    private val Weights = listOf(400, 500, 600, 700, 800)

    fun attach(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun root(ctx: Context) = File(ctx.filesDir, "google_fonts")
    private fun key(name: String) = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
    private fun dir(ctx: Context, name: String) = File(root(ctx), key(name))

    /** Уже скачанные файлы шрифта: вес → файл. */
    fun localFiles(name: String): Map<Int, File> {
        val ctx = appContext ?: return emptyMap()
        val files = dir(ctx, name).listFiles { f -> f.isFile && f.name.endsWith(".ttf") } ?: return emptyMap()
        return files.mapNotNull { f -> f.nameWithoutExtension.toIntOrNull()?.let { it to f } }.toMap()
    }

    fun isDownloaded(name: String) = localFiles(name).isNotEmpty()

    /** Скачивает шрифт, если его ещё нет. Возвращает true, если файлы есть на устройстве. */
    suspend fun ensure(context: Context, name: String): Boolean {
        attach(context)
        val n = name.trim()
        if (n.isEmpty()) return false
        if (isDownloaded(n)) return true
        val k = key(n)
        failedAt[k]?.let { if (System.currentTimeMillis() - it < RETRY_AFTER_MS) return false }
        if (!inFlight.add(k)) return false
        try {
            val ok = withContext(Dispatchers.IO) { loading.withPermit { runCatching { download(context.applicationContext, n) }.getOrDefault(false) } }
            if (ok) { failedAt.remove(k); revision++ } else failedAt[k] = System.currentTimeMillis()
            return ok
        } finally {
            inFlight.remove(k)
        }
    }

    private fun http(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 20000
            // Не браузерный User-Agent: Google Fonts отдаёт .ttf, а не woff2 (woff2 Android не загружает).
            c.setRequestProperty("User-Agent", "curl/8.0")
            if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
        } finally {
            c.disconnect()
        }
    }

    /** Разбор CSS: вес → ссылка на .ttf. */
    private fun parseCss(css: String): Map<Int, String> {
        val out = LinkedHashMap<Int, String>()
        Regex("@font-face\\s*\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL).findAll(css).forEach { m ->
            val body = m.groupValues[1]
            val w = Regex("font-weight:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 400
            val u = Regex("url\\(([^)]+)\\)").find(body)?.groupValues?.get(1)?.trim('\'', '"', ' ')
            if (u != null && w !in out) out[w] = u
        }
        return out
    }

    private fun download(ctx: Context, name: String): Boolean {
        val fam = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
        val attempts = listOf(
            "https://fonts.googleapis.com/css2?family=$fam:wght@${Weights.joinToString(";")}",
            "https://fonts.googleapis.com/css2?family=$fam:wght@400;700",
            "https://fonts.googleapis.com/css2?family=$fam",
        )
        var faces: Map<Int, String> = emptyMap()
        for (u in attempts) {
            faces = http(u)?.toString(Charsets.UTF_8)?.let(::parseCss).orEmpty()
            if (faces.isNotEmpty()) break
        }
        if (faces.isEmpty()) return false
        val target = dir(ctx, name).apply { mkdirs() }
        val byUrl = HashMap<String, File>()
        var saved = 0
        for ((weight, url) in faces) {
            val shared = byUrl[url]
            val out = File(target, "$weight.ttf")
            if (shared != null) {
                shared.copyTo(out, overwrite = true); saved++; continue
            }
            val bytes = http(url) ?: continue
            val tmp = File(target, "$weight.tmp")
            tmp.writeBytes(bytes)
            try {
                // Проверяем, что это настоящий шрифт: иначе Compose упадёт при отрисовке.
                android.graphics.fonts.Font.Builder(tmp).build()
            } catch (e: Exception) {
                tmp.delete(); continue
            }
            tmp.renameTo(out)
            byUrl[url] = out
            saved++
        }
        if (saved == 0) target.deleteRecursively()
        return saved > 0
    }

    fun sizeBytes(context: Context): Long {
        attach(context)
        return root(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Удаляет все скачанные шрифты, кроме перечисленных (используемых сейчас). */
    fun clearExcept(context: Context, keep: Collection<String>) {
        attach(context)
        val keepKeys = keep.map(::key).toSet()
        root(context).listFiles()?.forEach { if (it.name !in keepKeys) it.deleteRecursively() }
        failedAt.clear()
        revision++
    }
}
