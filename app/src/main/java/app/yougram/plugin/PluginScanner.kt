package app.yougram.plugin

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.math.ln

enum class ScanSeverity { INFO, MEDIUM, HIGH }
enum class ScanVerdict { CLEAN, SUSPICIOUS, MALICIOUS }

data class ScanFinding(val severity: ScanSeverity, val file: String?, val message: String)

data class ScanReport(val sha256: String, val findings: List<ScanFinding>) {
    val verdict: ScanVerdict
        get() = when {
            findings.any { it.severity == ScanSeverity.HIGH } -> ScanVerdict.MALICIOUS
            findings.any { it.severity == ScanSeverity.MEDIUM } -> ScanVerdict.SUSPICIOUS
            else -> ScanVerdict.CLEAN
        }

    /** Текст для пользователя: только предупреждения, самые тяжёлые сверху. */
    fun details(): String = findings
        .filter { it.severity != ScanSeverity.INFO }
        .sortedByDescending { it.severity }
        .joinToString("\n") { f ->
            val mark = if (f.severity == ScanSeverity.HIGH) "⛔" else "⚠️"
            "$mark ${f.message}" + (f.file?.let { " ($it)" } ?: "")
        }
}

/** Плагин не заблокирован, но подозрителен: пользователь может подтвердить установку. */
class SuspiciousPluginException(val report: ScanReport) :
    IllegalArgumentException("Плагин выглядит подозрительно:\n" + report.details())

/**
 * Статическая проверка пакета плагина ДО загрузки .so (загрузка уже исполняет код).
 * Это эвристики, а не антивирус: цель — отсечь очевидные трояны, дропперы и подмены.
 */
object PluginScanner {
    private const val MAX_FILES = 200
    private const val MAX_SCAN_BYTES = 24 * 1024 * 1024
    private const val ENTROPY_LIMIT = 7.4

    /** SHA-256 известных вредоносных библиотек (пополняйте списком, строчными hex). */
    private val BLOCKED_SHA256: Set<String> = emptySet()

    private val ABI_MACHINE = mapOf("arm64-v8a" to 183, "armeabi-v7a" to 40, "x86_64" to 62, "x86" to 3)
    private val BAD_EXT = setOf("dex", "jar", "apk", "sh", "exe", "bat", "cmd", "js", "py", "pl", "odex", "vdex", "oat", "art")
    private val OK_EXT = setOf("json", "txt", "md", "png", "webp", "jpg", "jpeg", "svg")

    private data class Pattern(val needle: String, val severity: ScanSeverity, val message: String)

    private val STRINGS = listOf(
        Pattern("/system/xbin/su", ScanSeverity.HIGH, "Поиск root (su)"),
        Pattern("/system/bin/su", ScanSeverity.HIGH, "Поиск root (su)"),
        Pattern("/sbin/su", ScanSeverity.HIGH, "Поиск root (su)"),
        Pattern("su -c", ScanSeverity.HIGH, "Запуск команд от root"),
        Pattern("magisk", ScanSeverity.HIGH, "Обращение к Magisk"),
        Pattern("/proc/self/mem", ScanSeverity.HIGH, "Прямая запись в память процесса"),
        Pattern("frida", ScanSeverity.HIGH, "Инструменты внедрения/перехвата (frida)"),
        Pattern("xposed", ScanSeverity.HIGH, "Инструменты перехвата (Xposed)"),
        Pattern("libsubstrate", ScanSeverity.HIGH, "Инструменты перехвата (Substrate)"),
        Pattern("td.binlog", ScanSeverity.HIGH, "Чтение базы сессии Telegram в обход API"),
        Pattern("/data/local/tmp", ScanSeverity.HIGH, "Каталог, который используют дропперы"),
        Pattern("pm install", ScanSeverity.HIGH, "Тихая установка приложений"),
        Pattern("DexClassLoader", ScanSeverity.HIGH, "Загрузка стороннего кода"),
        Pattern("discord.com/api/webhooks", ScanSeverity.HIGH, "Вебхук Discord — канал вывода данных"),
        Pattern("/data/data/", ScanSeverity.MEDIUM, "Жёстко прописан путь к данным приложений"),
        Pattern("api.telegram.org", ScanSeverity.MEDIUM, "Обращение к Bot API (возможный канал утечки)"),
        Pattern("pastebin.com", ScanSeverity.MEDIUM, "Обращение к pastebin"),
        Pattern("ngrok", ScanSeverity.MEDIUM, "Туннель ngrok"),
        Pattern("/proc/self/maps", ScanSeverity.MEDIUM, "Анализ карты памяти"),
        Pattern("TracerPid", ScanSeverity.MEDIUM, "Антиотладка"),
    )

    /** Импортируемые функции (имена в .dynstr разделены нулями). */
    private val IMPORTS = listOf(
        listOf("execve", "execv", "execvp", "execl", "execlp", "execle", "system", "popen", "fork", "vfork", "posix_spawn", "posix_spawnp")
            to "Запуск процессов и команд",
        listOf("ptrace") to "ptrace (отладка/внедрение в процессы)",
        listOf("dlopen") to "Подгружает другие библиотеки",
        listOf("memfd_create") to "Анонимные файлы в памяти (запуск кода без диска)",
    )
    private val NET_IMPORTS = listOf("socket", "connect", "getaddrinfo", "gethostbyname", "sendto")

    private val URL = Regex("https?://[A-Za-z0-9._~:/?#@!$&'()*+,;=%-]{4,120}")
    private val IP_URL = Regex("https?://\\d{1,3}(\\.\\d{1,3}){3}")

    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun scan(root: File, manifest: PluginManifest, packageSha256: String): ScanReport {
        val out = ArrayList<ScanFinding>()
        val files = root.walkTopDown().filter { it.isFile }.toList()
        if (files.size > MAX_FILES) out.flag(ScanSeverity.HIGH, "Слишком много файлов в пакете (${files.size})")

        for (f in files.take(MAX_FILES)) {
            val rel = f.relativeTo(root).invariantSeparatorsPath
            val ext = f.extension.lowercase()
            val head = readPrefix(f, 4)
            val isElf = head.size >= 4 && head[0] == 0x7f.toByte() && head[1] == 'E'.code.toByte() &&
                head[2] == 'L'.code.toByte() && head[3] == 'F'.code.toByte()
            val isDex = head.size >= 4 && String(head, Charsets.ISO_8859_1) == "dex\n"
            val isZip = head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()

            when {
                rel == "manifest.json" -> Unit
                ext == "so" && !isElf -> out.flag(ScanSeverity.HIGH, "Файл .so не является ELF-библиотекой", rel)
                isElf && (ext == "so" || ".so." in f.name) -> scanLibrary(f, rel, manifest, out)
                isElf -> out.flag(ScanSeverity.HIGH, "Исполняемый файл вне библиотек", rel)
                isDex || ext in BAD_EXT -> out.flag(ScanSeverity.HIGH, "Запрещённый тип файла (.$ext)", rel)
                isZip -> out.flag(ScanSeverity.MEDIUM, "Вложенный архив", rel)
                ext !in OK_EXT -> out.flag(ScanSeverity.MEDIUM, "Неизвестный тип файла", rel)
            }
        }

        val perms = manifest.permissions
        if (PluginPermission.NETWORK in perms && PluginPermission.CHAT_READ in perms) {
            out.flag(ScanSeverity.MEDIUM, "chat.read + network: плагин может отправлять вашу переписку в сеть")
        }
        if (perms.containsAll(PluginPermission.ALL)) out.flag(ScanSeverity.MEDIUM, "Плагин запросил все разрешения")

        return ScanReport(packageSha256, out)
    }

    private fun scanLibrary(f: File, rel: String, m: PluginManifest, out: MutableList<ScanFinding>) {
        if (f.length() > MAX_SCAN_BYTES) out.flag(ScanSeverity.MEDIUM, "Библиотека очень большая — проверена частично", rel)
        if (sha256(f) in BLOCKED_SHA256) out.flag(ScanSeverity.HIGH, "Библиотека в списке известных вредоносных", rel)

        val raw = readPrefix(f, MAX_SCAN_BYTES)
        if (u16(raw, 16) == 2) out.flag(ScanSeverity.MEDIUM, "Это исполняемый файл, а не библиотека", rel)

        // Архитектура должна совпадать с каталогом lib/<abi>/ (иначе подмена).
        val parts = rel.split('/')
        if (parts.size >= 3 && parts[0] == "lib") {
            val expected = ABI_MACHINE[parts[1]]
            if (expected == null) out.flag(ScanSeverity.MEDIUM, "Неизвестный ABI: ${parts[1]}", rel)
            else if (u16(raw, 18) != expected) out.flag(ScanSeverity.HIGH, "Архитектура библиотеки не совпадает с каталогом ${parts[1]}", rel)
        }

        val text = String(raw, Charsets.ISO_8859_1)
        if (f.name == m.entry && !text.contains("yougram_plugin_entry")) {
            out.flag(ScanSeverity.MEDIUM, "Нет точки входа yougram_plugin_entry", rel)
        }

        for (p in STRINGS) if (text.contains(p.needle, ignoreCase = true)) out.flag(p.severity, p.message, rel)

        fun imported(name: String) = text.contains("\u0000$name\u0000")
        for ((names, message) in IMPORTS) {
            val hit = names.filter(::imported)
            if (hit.isNotEmpty()) out.flag(ScanSeverity.MEDIUM, "$message: ${hit.joinToString()}", rel)
        }
        val net = NET_IMPORTS.filter(::imported)
        if (net.isNotEmpty() && PluginPermission.NETWORK !in m.permissions) {
            out.flag(ScanSeverity.MEDIUM, "Прямые сетевые вызовы в обход разрешения network: ${net.joinToString()}", rel)
        }

        val urls = URL.findAll(text).map { it.value }.distinct().take(5).toList()
        if (urls.any { IP_URL.containsMatchIn(it) }) out.flag(ScanSeverity.MEDIUM, "Зашит адрес по IP вместо домена", rel)
        if (urls.isNotEmpty()) {
            out.flag(ScanSeverity.INFO, "Зашитые адреса: " + urls.joinToString { it.substringAfter("://").substringBefore('/') }, rel)
        }

        if (raw.size > 64 * 1024 && entropy(raw) > ENTROPY_LIMIT) {
            out.flag(ScanSeverity.MEDIUM, "Высокая энтропия: библиотека упакована или зашифрована", rel)
        }
    }

    private fun MutableList<ScanFinding>.flag(sev: ScanSeverity, msg: String, file: String? = null) {
        if (none { it.severity == sev && it.message == msg && it.file == file }) add(ScanFinding(sev, file, msg))
    }

    private fun readPrefix(f: File, max: Int): ByteArray {
        val buf = ByteArray(minOf(f.length(), max.toLong()).toInt())
        RandomAccessFile(f, "r").use { it.readFully(buf) }
        return buf
    }

    private fun u16(b: ByteArray, off: Int): Int =
        if (b.size < off + 2) 0 else (b[off].toInt() and 0xff) or ((b[off + 1].toInt() and 0xff) shl 8)

    private fun entropy(b: ByteArray): Double {
        val counts = IntArray(256)
        for (x in b) counts[x.toInt() and 0xff]++
        val n = b.size.toDouble()
        var h = 0.0
        for (c in counts) if (c > 0) {
            val p = c / n
            h -= p * ln(p) / ln(2.0)
        }
        return h
    }
}
