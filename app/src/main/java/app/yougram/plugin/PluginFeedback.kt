package app.yougram.plugin

import android.content.Context
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Средняя оценка плагина. */
data class PluginRating(val avg: Double, val count: Int)

/** Отзыв: [mine] — отзыв этого устройства (его можно изменить или удалить). */
data class PluginReview(
    val rating: Int,
    val text: String,
    val name: String,
    val ts: Long,
    val mine: Boolean,
)

/** Причины жалоб: код для сервера и подпись для пользователя. */
val PluginReportReasons = listOf(
    "malware" to "Вредоносный код или слежка",
    "scam" to "Мошенничество или обман",
    "broken" to "Не работает или ломает приложение",
    "copyright" to "Нарушение авторских прав",
    "other" to "Другое",
)

/**
 * Отзывы и жалобы на плагины. Живут на том же сервере, что и каталог (/api/ratings, /api/reviews, /api/reports).
 * Пользователь анонимен: приложение хранит случайный device-id, сервер видит только его хеш.
 */
object PluginFeedback {
    private const val TIMEOUT = 15_000
    private const val MAX_RESPONSE = 512 * 1024

    private fun api(path: String): String = URI(PluginStoreConfig.INDEX_URL).resolve(path).toString()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun deviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences("plugin_feedback", Context.MODE_PRIVATE)
        prefs.getString("device", null)?.takeIf { it.length == 32 }?.let { return it }
        val id = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString("device", id).apply()
        return id
    }

    private suspend fun call(method: String, url: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "Работаем только по HTTPS" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            conn.setRequestProperty("User-Agent", "MaterialGram-PluginStore")
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { s ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                while (out.size() < MAX_RESPONSE) {
                    val n = s.read(buf, 0, minOf(buf.size, MAX_RESPONSE - out.size()))
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                out.toString("UTF-8")
            }.orEmpty()
            val obj = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (code !in 200..299) error(obj.optString("error").ifBlank { "Сервер ответил $code" })
            obj
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchRatings(): Map<String, PluginRating> {
        val o = call("GET", api("/api/ratings")).optJSONObject("ratings") ?: return emptyMap()
        val out = HashMap<String, PluginRating>()
        for (id in o.keys()) {
            val r = o.optJSONObject(id) ?: continue
            val count = r.optInt("count")
            if (count > 0) out[id] = PluginRating(r.optDouble("avg"), count)
        }
        return out
    }

    suspend fun fetchReviews(context: Context, pluginId: String): List<PluginReview> {
        val arr = call("GET", api("/api/reviews?id=${enc(pluginId)}&device=${deviceId(context)}")).optJSONArray("reviews")
            ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PluginReview(
                rating = o.optInt("rating"),
                text = o.optString("text"),
                name = o.optString("name"),
                ts = o.optLong("ts"),
                mine = o.optBoolean("mine"),
            )
        }
    }

    suspend fun submitReview(context: Context, pluginId: String, rating: Int, text: String, name: String) {
        call(
            "POST", api("/api/reviews"),
            JSONObject()
                .put("plugin", pluginId).put("device", deviceId(context))
                .put("rating", rating).put("text", text).put("name", name),
        )
    }

    suspend fun deleteMyReview(context: Context, pluginId: String) {
        call("DELETE", api("/api/reviews?id=${enc(pluginId)}&device=${deviceId(context)}"))
    }

    suspend fun submitReport(context: Context, pluginId: String, reason: String, text: String) {
        call(
            "POST", api("/api/reports"),
            JSONObject()
                .put("plugin", pluginId).put("device", deviceId(context))
                .put("reason", reason).put("text", text),
        )
    }
}
