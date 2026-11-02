package app.yougram.core.ui.theme

import android.content.Context
import app.yougram.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

private fun parseFamilies(json: String): List<String>? = runCatching {
    val items = JSONObject(json).getJSONArray("items")
    List(items.length()) { items.getJSONObject(it).getString("family") }
}.getOrNull()?.takeIf { it.isNotEmpty() }

/**
 * Полный список семейств Google Fonts (Developer API, по популярности) с кэшем на 7 дней.
 * Без ключа GOOGLE_FONTS_API_KEY или без сети возвращает кэш, а если его нет, встроенный каталог.
 */
suspend fun loadGoogleFontsList(context: Context): List<String> = withContext(Dispatchers.IO) {
    val cache = File(context.filesDir, "google_fonts_list.json")
    val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < CACHE_MAX_AGE_MS
    if (fresh) parseFamilies(cache.readText())?.let { return@withContext it }

    val key = BuildConfig.GOOGLE_FONTS_API_KEY
    if (key.isNotBlank()) {
        val body = runCatching {
            val url = "https://www.googleapis.com/webfonts/v1/webfonts?key=$key&sort=popularity&fields=items(family)"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            try {
                if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
        val families = body?.let(::parseFamilies)
        if (body != null && families != null) {
            runCatching { cache.writeText(body) }
            return@withContext families
        }
    }
    cache.takeIf { it.exists() }?.let { parseFamilies(it.readText()) } ?: GoogleFontsCatalog
}
