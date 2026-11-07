package app.yougram.plugin

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Настройки магазина плагинов. Укажи здесь адрес своего index.json на сервере (только HTTPS). */
object PluginStoreConfig {
    const val INDEX_URL = "https://yougram-plugins.sdc9930222.workers.dev/index.json"
}

/**
 * Запись каталога. Формат index.json:
 * { "plugins": [ { "id", "name", "author", "version", "description", "long_description",
 *                  "category", "icon", "url", "sha256", "size", "changelog", "permissions": [] } ] }
 * Относительные "icon" и "url" считаются от адреса index.json.
 */
data class StorePlugin(
    val id: String,
    val name: String,
    val author: String,
    val version: String,
    val description: String,
    val longDescription: String,
    val category: String,
    val iconUrl: String?,
    val url: String,
    val sha256: String,
    val size: Long,
    val changelog: String,
    val permissions: List<String>,
    /** Когда добавлен в каталог (unix-секунды, необязательно): по нему строится раздел «Новые». */
    val added: Long = 0L,
    /** true, если разработчик помечен в каталоге как доверенный ("verified": true в index.json). */
    val verified: Boolean = false,
    /** GitHub-логин того, кто выложил плагин (из каталога); пусто — старый каталог, тогда показываем author. */
    val developer: String = "",
)

object PluginStore {
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000
    private const val MAX_INDEX_BYTES = 2 * 1024 * 1024
    private const val MAX_PLUGIN_BYTES = 64L * 1024 * 1024

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val n = input.read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "Магазин работает только по HTTPS" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Yougram-PluginStore")
        }
    }

    suspend fun fetchCatalog(indexUrl: String = PluginStoreConfig.INDEX_URL): List<StorePlugin> = withContext(Dispatchers.IO) {
        val conn = open(indexUrl)
        try {
            check(conn.responseCode == 200) { "Сервер ответил ${conn.responseCode}" }
            val bytes = conn.inputStream.use { readLimited(it, MAX_INDEX_BYTES + 1) }
            require(bytes.size <= MAX_INDEX_BYTES) { "Каталог слишком большой" }
            val arr = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("plugins")
            val base = URI(indexUrl)
            fun resolve(s: String) = base.resolve(s).toString()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val id = o.optString("id")
                val url = o.optString("url")
                val sha = o.optString("sha256")
                if (id.isEmpty() || url.isEmpty() || sha.isEmpty()) return@mapNotNull null
                // Каталог пишет "api" из manifest.json; плагины вне 2.1..2.3 не показываем: они всё равно не установятся.
                val api = o.optString("api")
                if (api.isNotEmpty() && !PluginApiVersion.isSupported(api)) return@mapNotNull null
                val perms = o.optJSONArray("permissions")
                StorePlugin(
                    id = id,
                    name = o.optString("name", id),
                    author = o.optString("author", "Unknown"),
                    version = o.optString("version", "1.0"),
                    description = o.optString("description"),
                    longDescription = o.optString("long_description", o.optString("description")),
                    category = o.optString("category", "Другое"),
                    iconUrl = o.optString("icon").takeIf { it.isNotEmpty() }?.let(::resolve),
                    url = resolve(url),
                    sha256 = sha.lowercase(),
                    size = o.optLong("size", 0L),
                    changelog = o.optString("changelog"),
                    added = o.optLong("added", 0L),
                    verified = o.optBoolean("verified", false),
                    developer = o.optString("developer"),
                    permissions = perms?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Скачивает плагин в кэш приложения и сверяет SHA-256 с каталогом; при несовпадении файл удаляется. */
    suspend fun download(plugin: StorePlugin, cacheDir: File, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val target = File(cacheDir, "store-${plugin.id.filter { it.isLetterOrDigit() || it == '.' || it == '_' }}-${System.nanoTime()}.ygplugin")
        val conn = open(plugin.url)
        try {
            check(conn.responseCode == 200) { "Сервер ответил ${conn.responseCode}" }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: plugin.size
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        read += n
                        require(read <= MAX_PLUGIN_BYTES) { "Файл плагина слишком большой" }
                        digest.update(buf, 0, n)
                        out.write(buf, 0, n)
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != plugin.sha256) {
                target.delete()
                error("Контрольная сумма не совпала: файл повреждён или подменён")
            }
            target
        } catch (e: Throwable) {
            target.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = open(url)
            try {
                if (conn.responseCode != 200) null else conn.inputStream.use { readLimited(it, 2 * 1024 * 1024) }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** true, если версия [store] новее [installed] (сравнение по числам через точку). */
    fun isNewer(store: String, installed: String): Boolean {
        val a = store.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val b = installed.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}