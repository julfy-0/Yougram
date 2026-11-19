package app.yougram.ota

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject

/** Сетевой слой: проверка версии, скачивание APK с докачкой, загрузка баннера. Блокирующий — вызывать из IO. */
internal class OtaClient(private val config: OtaConfig) {

    private val base: String get() = config.serverUrl.trim().trimEnd('/')

    /** null — обновлений нет. */
    fun fetchLatest(channel: UpdateChannel, installId: String): UpdateInfo? {
        check(base.startsWith("https://")) { config.tr("сервер обновлений не настроен (OTA_URL в local.properties)") }
        val query = "channel=${channel.key}&code=${config.versionCode}&did=${URLEncoder.encode(installId, "UTF-8")}"
        val conn = open("$base/v1/check?$query")
        try {
            val j = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (!j.optBoolean("update", false)) return null
            val beta = j.optInt("beta", 0)
            val name = j.getString("versionName").let { if (beta > 0) "$it-b$beta" else it }
            val host = URL(base).host
            // Ссылки из ответа должны вести на тот же https-сервер, что и сам запрос.
            fun sameHost(url: String): String {
                val u = URL(url)
                check(u.protocol == "https" && u.host == host) { config.tr("ссылка на файл ведёт не на сервер обновлений") }
                return url
            }
            return UpdateInfo(
                versionName = name,
                versionCode = j.getInt("versionCode"),
                apkUrl = sameHost(j.getString("apkUrl")),
                notes = j.optString("notes"),
                sizeBytes = j.optLong("size", 0L),
                sha256 = j.optString("sha256").takeIf { it.isNotBlank() && it != "null" },
                bannerUrl = j.optString("bannerUrl").takeIf { it.isNotBlank() && it != "null" }?.let(::sameHost),
                beta = beta,
                packed = j.optBoolean("zip", false),
                packedSizeBytes = j.optLong("zipSize", 0L),
                packedSha256 = j.optString("zipSha256").takeIf { it.isNotBlank() && it != "null" },
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Качает APK в [part]. Если файл уже частично скачан — докачивает с места обрыва (HTTP Range).
     * Проверка целостности — на вызывающей стороне ([ApkVerifier]).
     */
    suspend fun download(info: UpdateInfo, part: File, onProgress: (Float) -> Unit) {
        // Если сборка упакована в ZIP, качаем и сверяем размер именно архива.
        val expectedSize = if (info.packed) info.packedSizeBytes else info.sizeBytes
        var offset = part.length()
        if (expectedSize > 0 && offset > expectedSize) {
            part.delete()
            offset = 0
        }
        // Всё уже скачано в прошлый раз.
        if (expectedSize > 0 && offset == expectedSize) return

        val conn = open(info.apkUrl, rangeFrom = offset)
        try {
            // Сервер мог проигнорировать Range и отдать файл целиком — тогда начинаем с нуля.
            val resumed = offset > 0 && conn.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (!resumed) offset = 0
            val remaining = conn.contentLengthLong.takeIf { it > 0 }
            val total = when {
                expectedSize > 0 -> expectedSize
                remaining != null -> offset + remaining
                else -> 0L
            }
            var done = offset
            conn.inputStream.use { input ->
                FileOutputStream(part, resumed).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0L) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Открывает [url] и отдаёт поток в [block]; соединение закрывается автоматически. */
    fun <T> read(url: String, block: (java.io.InputStream) -> T): T {
        val conn = open(url)
        try {
            return conn.inputStream.use(block)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, rangeFrom: Long = 0L): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Yougram/${config.versionName}")
        if (rangeFrom > 0) conn.setRequestProperty("Range", "bytes=$rangeFrom-")
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            error(
                when (code) {
                    403, 429 -> config.tr("сервер обновлений перегружен, попробуйте позже")
                    404 -> config.tr("обновление не найдено на сервере (HTTP 404)")
                    else -> "HTTP $code"
                },
            )
        }
        return conn
    }
}
