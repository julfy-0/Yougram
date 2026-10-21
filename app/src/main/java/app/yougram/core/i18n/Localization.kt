package app.yougram.core.i18n

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Язык интерфейса. [custom] — созданный пользователем, [base] — язык, на котором он основан. */
data class AppLanguage(val code: String, val name: String, val custom: Boolean, val base: String?)

/** Перевод строки интерфейса. Ключ — русский оригинал; если перевода нет, показывается оригинал. */
fun tr(text: String): String = Localization.tr(text)

/** Перевод шаблона: {0}, {1}… заменяются аргументами уже после перевода. */
fun tr(text: String, vararg args: Any?): String {
    var r = Localization.tr(text)
    args.forEachIndexed { i, a -> r = r.replace("{$i}", a.toString()) }
    return r
}

object Localization {
    const val SOURCE = "ru"
    private const val PREFS = "yougram_i18n"
    private const val MAX_FILE = 5_000_000

    /** Порядок в списке: обязательные языки сверху, остальные по коду. */
    private val PRIORITY = listOf("uk", "en", "zh", "de")

    private lateinit var appContext: Context

    private var currentCode by mutableStateOf(SOURCE)
    private var table by mutableStateOf<Map<String, String>>(emptyMap())

    var languages by mutableStateOf<List<AppLanguage>>(emptyList())
        private set

    /** Все строки, которые интерфейс уже запрашивал: из них редактор собирает список для перевода. */
    private val seen: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val current: String get() = currentCode

    fun init(context: Context) {
        appContext = context.applicationContext
        refreshLanguages()
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString("code", null)
        val system = Locale.getDefault().language
        val start = when {
            saved != null && languages.any { it.code == saved } -> saved
            saved == null && languages.any { it.code == system } -> system
            else -> SOURCE
        }
        apply(start)
    }

    fun tr(text: String): String {
        if (text.isBlank()) return text
        seen.add(text)
        val t = table                       // чтение состояния: Compose перерисует экран при смене языка
        if (currentCode == SOURCE) return text
        return t[text] ?: text
    }

    /** Выбор формы слова по числу: slavic (ru/uk) — 3 формы, zh — одна, остальные — единственное/множественное. */
    fun pluralForm(n: Int, one: String, few: String, many: String): String {
        val lang = currentCode
        val base = if (lang.startsWith("custom_")) (read(lang)?.optString("base").orEmpty().ifEmpty { SOURCE }) else lang
        return when (base) {
            "zh", "ja", "ko" -> many
            "ru", "uk", "pl" -> {
                val m100 = n % 100
                val m10 = n % 10
                when {
                    m100 in 11..14 -> many
                    m10 == 1 -> one
                    m10 in 2..4 -> few
                    else -> many
                }
            }
            else -> if (n == 1) one else few
        }
    }

    fun select(code: String) {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("code", code).apply()
        apply(code)
    }

    private fun apply(code: String) {
        table = resolve(code, 0)
        currentCode = code
    }

    // ---------- хранилище ----------

    private fun customDir() = File(appContext.filesDir, "i18n").apply { mkdirs() }

    private fun read(code: String): JSONObject? = runCatching {
        val f = File(customDir(), "$code.json")
        val text = if (f.exists()) f.readText()
        else appContext.assets.open("i18n/$code.json").bufferedReader().use { it.readText() }
        JSONObject(text)
    }.getOrNull()

    private fun write(code: String, name: String, base: String?, strings: Map<String, String>) {
        val o = JSONObject()
        o.put("name", name)
        if (base != null) o.put("base", base)
        val s = JSONObject()
        strings.forEach { (k, v) -> s.put(k, v) }
        o.put("strings", s)
        File(customDir(), "$code.json").writeText(o.toString(2))
    }

    /** Итоговая таблица: перевод базового языка, поверх него собственные строки. */
    private fun resolve(code: String, depth: Int): Map<String, String> {
        if (code == SOURCE || depth > 5) return emptyMap()
        val o = read(code) ?: return emptyMap()
        val result = HashMap<String, String>()
        o.optString("base").takeIf { it.isNotEmpty() }?.let { result.putAll(resolve(it, depth + 1)) }
        o.optJSONObject("strings")?.let { s -> s.keys().forEach { k -> result[k] = s.optString(k) } }
        return result
    }

    private fun refreshLanguages() {
        val list = ArrayList<AppLanguage>()
        list += AppLanguage(SOURCE, "Русский", custom = false, base = null)
        val builtin = appContext.assets.list("i18n").orEmpty()
            .filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }.filter { it != SOURCE }.sortedBy { c -> PRIORITY.indexOf(c).let { if (it < 0) PRIORITY.size else it } }
        builtin.forEach { code ->
            val name = read(code)?.optString("name").takeUnless { it.isNullOrBlank() } ?: code
            list += AppLanguage(code, name, custom = false, base = null)
        }
        customDir().listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }.forEach { f ->
            val code = f.nameWithoutExtension
            val o = read(code) ?: return@forEach
            list += AppLanguage(
                code, o.optString("name").ifBlank { code }, custom = true,
                base = o.optString("base").takeIf { it.isNotEmpty() },
            )
        }
        languages = list
    }

    // ---------- своя локализация ----------

    fun nameOf(code: String): String = languages.firstOrNull { it.code == code }?.name ?: code

    /** Создаёт локализацию [name] на основе языка [base] и сразу включает её. */
    fun create(name: String, base: String): String {
        val code = "custom_${System.currentTimeMillis()}"
        write(code, name.trim().ifBlank { "My language" }, base, emptyMap())
        refreshLanguages()
        select(code)
        return code
    }

    /** Собственные (изменённые) строки локализации. */
    fun overrides(code: String): Map<String, String> {
        val s = read(code)?.optJSONObject("strings") ?: return emptyMap()
        return s.keys().asSequence().associateWith { s.optString(it) }
    }

    /** Перевод, который даёт базовый язык (до изменений). */
    fun baseTable(code: String): Map<String, String> =
        resolve(read(code)?.optString("base").orEmpty().ifEmpty { SOURCE }, 0)

    fun saveStrings(code: String, strings: Map<String, String>) {
        val o = read(code) ?: return
        write(code, o.optString("name"), o.optString("base").takeIf { it.isNotEmpty() }, strings)
        if (currentCode == code) apply(code)
    }

    fun rename(code: String, name: String) {
        val o = read(code) ?: return
        write(code, name.trim().ifBlank { o.optString("name") }, o.optString("base").takeIf { it.isNotEmpty() }, overrides(code))
        refreshLanguages()
    }

    fun delete(code: String) {
        File(customDir(), "$code.json").delete()
        refreshLanguages()
        if (currentCode == code) select(SOURCE)
    }

    /** Все известные ключи: запрошенные интерфейсом + ключи встроенных переводов. */
    fun knownKeys(): List<String> {
        val keys = HashSet<String>(seen)
        languages.filter { !it.custom }.forEach { keys += resolve(it.code, 0).keys }
        return keys.filter { it.isNotBlank() }.sorted()
    }

    fun exportTo(code: String, uri: Uri): Boolean = runCatching {
        val o = read(code) ?: return false
        appContext.contentResolver.openOutputStream(uri)!!.use { it.write(o.toString(2).toByteArray()) }
        true
    }.getOrDefault(false)

    /** Импорт файла локализации ({"name":..., "base":..., "strings":{...}}). Возвращает код или null. */
    fun importFrom(uri: Uri): String? = runCatching {
        val text = appContext.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        require(text.length < MAX_FILE)
        val o = JSONObject(text)
        val s = o.getJSONObject("strings")
        val map = s.keys().asSequence().associateWith { s.optString(it) }
        val base = o.optString("base").takeIf { b -> b.isNotEmpty() && languages.any { it.code == b } } ?: SOURCE
        val code = "custom_${System.currentTimeMillis()}"
        write(code, o.optString("name").ifBlank { "Imported" }, base, map)
        refreshLanguages()
        select(code)
        code
    }.getOrNull()
}
