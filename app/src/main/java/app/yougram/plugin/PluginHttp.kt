package app.yougram.plugin

import android.util.Base64
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/** HTTP для плагинов: обычный запрос, запрос с бинарным ответом и скачивание файла в каталог плагина. */
object PluginHttp {
    private const val DEFAULT_MAX_BODY = 2 * 1024 * 1024
    private const val HARD_MAX_BODY = 8 * 1024 * 1024
    private const val MAX_DOWNLOAD = 32L * 1024 * 1024
    private val FILE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")

    fun request(a: JSONObject, maxTimeoutMs: Int): Map<String, Any?> {
        val maxBytes = a.optInt("max_bytes", DEFAULT_MAX_BODY).coerceIn(1, HARD_MAX_BODY)
        val conn = open(a, maxTimeoutMs)
        try {
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val bytes = if (stream != null) stream.use { readLimited(it, maxBytes) } else ByteArray(0)
            val body = if (a.optString("response", "text") == "base64") {
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                String(bytes, Charsets.UTF_8)
            }
            return linkedMapOf("status" to code, "headers" to headersOf(conn), "body" to body)
        } finally {
            conn.disconnect()
        }
    }

    /** Скачивает в `<dir>/downloads/<name>`; наружу отдаёт абсолютный путь, который можно передать в send_photo/send_document. */
    fun download(a: JSONObject, dir: File, maxTimeoutMs: Int): Map<String, Any?> {
        val name = a.getString("name")
        require(FILE_NAME.matches(name)) { "Недопустимое имя файла" }
        val folder = File(dir, "downloads").apply { mkdirs() }
        val target = File(folder, name)
        val part = File(folder, "$name.part")
        val conn = open(a, maxTimeoutMs)
        try {
            val code = conn.responseCode
            require(code in 200..299) { "HTTP $code" }
            var total = 0L
            try {
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            require(total <= MAX_DOWNLOAD) { "Файл слишком большой" }
                            out.write(buf, 0, n)
                        }
                    }
                }
                target.delete()
                check(part.renameTo(target)) { "Не удалось сохранить файл" }
            } catch (t: Throwable) {
                part.delete()
                throw t
            }
            return linkedMapOf("path" to target.absolutePath, "size" to total, "status" to code)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(a: JSONObject, maxTimeoutMs: Int): HttpURLConnection {
        val url = URL(a.getString("url"))
        require(url.protocol == "https" || url.protocol == "http") { "Только http/https" }
        val conn = url.openConnection() as HttpURLConnection
        val timeout = a.optInt("timeout_ms", 20_000).coerceIn(1_000, maxTimeoutMs)
        conn.requestMethod = a.optString("method", "GET").uppercase()
        conn.connectTimeout = timeout
        conn.readTimeout = timeout
        conn.instanceFollowRedirects = a.optBoolean("follow_redirects", true)
        a.optJSONObject("headers")?.let { headers ->
            val names = headers.keys()
            while (names.hasNext()) {
                val key = names.next()
                conn.setRequestProperty(key, headers.getString(key))
            }
        }
        val body: ByteArray? = when {
            a.has("body_base64") -> Base64.decode(a.getString("body_base64"), Base64.DEFAULT)
            a.has("body") -> a.getString("body").toByteArray(Charsets.UTF_8)
            else -> null
        }
        if (body != null) {
            require(body.size <= HARD_MAX_BODY) { "Тело запроса слишком большое" }
            conn.doOutput = true
            conn.outputStream.use { it.write(body) }
        }
        return conn
    }

    private fun headersOf(conn: HttpURLConnection): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((key, values) in conn.headerFields) {
            if (key != null) out[key] = values.joinToString(", ")
        }
        return out
    }

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            require(out.size() <= maxBytes) { "Ответ слишком большой" }
        }
        return out.toByteArray()
    }
}
