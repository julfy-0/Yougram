package app.yougram.core.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Снимок внешнего вида: тема, акцент, шрифт, стекло, прозрачность подложек, порядок вкладок и раскладка
 * настроек, оформление сообщений. Хранится как JSON; при применении учитываются только присутствующие ключи,
 * поэтому пресеты могут быть частичными.
 */
object ThemeSnapshot {
    const val FORMAT = "yougram-theme"
    const val VERSION = 1

    fun capture(settings: SettingsRepository, plates: PlateRepository): JSONObject {
        val t = settings.theme.value
        val g = settings.glass.value
        val c = settings.chatPrefs.value
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("mode", t.mode.name)
            put("dynamic", t.dynamic)
            put("accent", t.accent)
            put("customAccent", t.customAccent)
            put("fontPath", t.fontPath ?: JSONObject.NULL)
            put("fontName", t.fontName ?: JSONObject.NULL)
            put("glass", JSONObject().apply {
                put("blur", g.blurRadius.toDouble())
                put("opacity", g.opacity.toDouble())
                put("blurType", g.blurType.key)
            })
            put("plates", JSONObject().apply {
                PlateArea.entries.forEach { put(it.key, plates.plates.value.of(it).toDouble()) }
            })
            put("homeLayout", settings.homeLayout.value ?: JSONObject.NULL)
            put("navOrder", settings.navOrder.value ?: JSONObject.NULL)
            put("chat", JSONObject().apply {
                put("textSize", c.textSize)
                put("bubbleRadius", c.bubbleRadius)
                put("nameColor", c.nameColor)
                put("listLines", c.listLines)
                put("searchOnTop", c.searchOnTop)
                put("hideStories", c.hideStories)
                put("bubbleOpacity", c.bubbleOpacity)
                put("bubbleBlur", c.bubbleBlur)
                put("ownBubbleColor", c.ownBubbleColor)
                put("otherBubbleColor", c.otherBubbleColor)
                put("uiScale", c.uiScale)
            })
        }
    }

    fun apply(json: JSONObject, settings: SettingsRepository, plates: PlateRepository) {
        json.optString("mode").takeIf { it.isNotEmpty() }?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }?.let(settings::setThemeMode)
        }
        if (json.has("dynamic")) settings.setDynamicColor(json.optBoolean("dynamic", true))
        if (json.has("accent")) settings.setAccent(json.optInt("accent", 0))
        if (json.has("customAccent")) {
            val custom = json.optInt("customAccent", 0)
            if (custom != 0) settings.setCustomAccent(custom)
        }
        if (json.has("fontPath")) {
            val path = if (json.isNull("fontPath")) null else json.optString("fontPath")
            if (path == null) settings.setCustomFont(null, null)
            else if (File(path).exists()) settings.setCustomFont(path, json.optString("fontName").ifEmpty { File(path).name })
        }
        json.optJSONObject("glass")?.let { g ->
            if (g.has("blur")) settings.setBlur(g.optDouble("blur").toFloat().coerceIn(0f, GlassSettings.MAX_BLUR))
            if (g.has("opacity")) settings.setOpacity(g.optDouble("opacity").toFloat().coerceIn(0f, 1f))
            if (g.has("blurType")) settings.setBlurType(BlurType.fromKey(g.optString("blurType")))
        }
        json.optJSONObject("plates")?.let { p ->
            PlateArea.entries.forEach { if (p.has(it.key)) plates.set(it, p.optDouble(it.key).toFloat()) }
        }
        if (json.has("homeLayout")) settings.setHomeLayout(if (json.isNull("homeLayout")) null else json.optString("homeLayout"))
        if (json.has("navOrder")) settings.setNavOrder(if (json.isNull("navOrder")) null else json.optString("navOrder"))
        json.optJSONObject("chat")?.let { c ->
            settings.updateChatPrefs { p ->
                p.copy(
                    textSize = if (c.has("textSize")) c.optInt("textSize").coerceIn(10, 40) else p.textSize,
                    bubbleRadius = if (c.has("bubbleRadius")) c.optInt("bubbleRadius").coerceIn(0, 28) else p.bubbleRadius,
                    nameColor = if (c.has("nameColor")) c.optInt("nameColor") else p.nameColor,
                    listLines = if (c.has("listLines")) c.optInt("listLines") else p.listLines,
                    searchOnTop = if (c.has("searchOnTop")) c.optBoolean("searchOnTop") else p.searchOnTop,
                    hideStories = if (c.has("hideStories")) c.optBoolean("hideStories") else p.hideStories,
                    bubbleOpacity = if (c.has("bubbleOpacity")) c.optInt("bubbleOpacity").coerceIn(20, 100) else p.bubbleOpacity,
                    bubbleBlur = if (c.has("bubbleBlur")) c.optBoolean("bubbleBlur") else p.bubbleBlur,
                    ownBubbleColor = if (c.has("ownBubbleColor")) c.optLong("ownBubbleColor") else p.ownBubbleColor,
                    otherBubbleColor = if (c.has("otherBubbleColor")) c.optLong("otherBubbleColor") else p.otherBubbleColor,
                    uiScale = if (c.has("uiScale")) c.optInt("uiScale").coerceIn(70, 130) else p.uiScale,
                )
            }
        }
    }

    /** Разбирает текст (например, из буфера обмена); null — это не тема Yougram. */
    fun parse(text: String): JSONObject? = runCatching {
        JSONObject(text.trim()).takeIf { it.optString("format") == FORMAT }
    }.getOrNull()
}

/** Готовый пресет: название, описание и частичный снимок. */
data class BuiltInPreset(val name: String, val description: String, val data: JSONObject)

object BuiltInPresets {
    private fun preset(name: String, description: String, build: JSONObject.() -> Unit) =
        BuiltInPreset(name, description, JSONObject().apply { put("format", ThemeSnapshot.FORMAT); put("version", 1); build() })

    private fun glass(blur: Double, opacity: Double) = JSONObject().put("blur", blur).put("opacity", opacity)

    private fun plates(value: Double) = JSONObject().apply {
        listOf(PlateArea.Chats, PlateArea.Contacts, PlateArea.Calls, PlateArea.Settings).forEach { put(it.key, value) }
    }

    val all: List<BuiltInPreset> = listOf(
        preset("Классика", "Стандартный вид Yougram") {
            put("mode", "Dark"); put("dynamic", true); put("accent", 0)
            put("glass", glass(GlassSettings.DEFAULT_BLUR.toDouble(), GlassSettings.DEFAULT_OPACITY.toDouble()))
            put("plates", plates(0.0))
            put("chat", JSONObject().put("bubbleRadius", 24).put("bubbleOpacity", 100).put("bubbleBlur", false)
                .put("ownBubbleColor", 0L).put("otherBubbleColor", 0L).put("uiScale", 100).put("textSize", 16))
        },
        preset("Океан", "Холодный синий, сильное стекло") {
            put("mode", "Dark"); put("dynamic", false); put("customAccent", 0xFF0288D1.toInt())
            put("glass", glass(40.0, 0.30))
            put("plates", plates(0.35))
            put("chat", JSONObject().put("bubbleRadius", 28).put("bubbleOpacity", 85).put("bubbleBlur", true))
        },
        preset("Лес", "Спокойный зелёный") {
            put("mode", "Dark"); put("dynamic", false); put("accent", 2)
            put("glass", glass(28.0, 0.45))
            put("plates", plates(0.15))
            put("chat", JSONObject().put("bubbleRadius", 20).put("bubbleOpacity", 100).put("bubbleBlur", false))
        },
        preset("Закат", "Тёплый оранжевый, мягкие углы") {
            put("mode", "Dark"); put("dynamic", false); put("customAccent", 0xFFFF7043.toInt())
            put("glass", glass(32.0, 0.35))
            put("plates", plates(0.25))
            put("chat", JSONObject().put("bubbleRadius", 28).put("bubbleOpacity", 90).put("bubbleBlur", true))
        },
        preset("Светлый минимализм", "Светлая тема, почти без стекла") {
            put("mode", "Light"); put("dynamic", false); put("accent", 0)
            put("glass", glass(12.0, 0.75))
            put("plates", plates(0.0))
            put("chat", JSONObject().put("bubbleRadius", 12).put("bubbleOpacity", 100).put("bubbleBlur", false))
        },
        preset("Прозрачное стекло", "Максимум размытия и прозрачности") {
            put("mode", "Dark"); put("dynamic", true)
            put("glass", glass(56.0, 0.18))
            put("plates", plates(0.6))
            put("chat", JSONObject().put("bubbleRadius", 28).put("bubbleOpacity", 60).put("bubbleBlur", true))
        },
    )
}

/** Сохранённая пользователем тема. */
data class SavedTheme(val id: String, val name: String, val data: JSONObject, val createdAt: Long)

/** Хранилище своих тем (SharedPreferences, JSON-массив). */
class ThemeLibrary(context: Context) {
    private val prefs = context.getSharedPreferences("yougram_themes", Context.MODE_PRIVATE)

    fun load(): List<SavedTheme> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val data = o.optJSONObject("data") ?: return@mapNotNull null
            SavedTheme(o.optString("id"), o.optString("name"), data, o.optLong("createdAt"))
        }
    }.getOrDefault(emptyList())

    private fun store(list: List<SavedTheme>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("data", it.data).put("createdAt", it.createdAt))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun add(name: String, data: JSONObject): List<SavedTheme> {
        val list = load() + SavedTheme(UUID.randomUUID().toString(), name.trim().ifEmpty { "Моя тема" }, data, System.currentTimeMillis())
        store(list)
        return list
    }

    fun delete(id: String): List<SavedTheme> = load().filterNot { it.id == id }.also(::store)

    fun rename(id: String, name: String): List<SavedTheme> =
        load().map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }.also(::store)

    private companion object { const val KEY = "themes" }
}
