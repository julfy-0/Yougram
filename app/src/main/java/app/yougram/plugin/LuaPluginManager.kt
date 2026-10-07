package app.yougram.plugin

import android.content.Context
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import org.json.JSONObject
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.ZeroArgFunction
import org.luaj.vm2.lib.jse.JsePlatform

/** Minimal Lua plugin host. Plugin API deliberately exposes data, not TDLib Java objects. */
class LuaPluginManager(private val context: Context) {
    private data class Loaded(val id: String, val globals: Globals)
    private val loaded = mutableListOf<Loaded>()
    val events = PluginEventBus()
    private val root = File(context.filesDir, "plugins").apply { mkdirs() }

    fun loadAll() {
        root.listFiles().orEmpty().filter { it.isDirectory }.forEach { load(it) }
    }

    fun installedPlugins(): List<String> = root.listFiles().orEmpty()
        .filter { it.isDirectory && File(it, "manifest.json").isFile }
        .mapNotNull { dir ->
            runCatching { JSONObject(File(dir, "manifest.json").readText()).optString("name", dir.name) }.getOrNull()
        }
        .sorted()

    fun installPackage(packageFile: File): Result<String> = runCatching {
        val temp = File(root, ".install_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            ZipInputStream(packageFile.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val target = File(temp, entry.name)
                    val rootPath = temp.canonicalPath + File.separator
                    if (!target.canonicalPath.startsWith(rootPath)) error("Недопустимый путь в плагине")
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> zip.copyTo(out) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val manifestFile = File(temp, "manifest.json")
            require(manifestFile.isFile) { "В плагине отсутствует manifest.json" }
            val manifest = JSONObject(manifestFile.readText())
            val id = manifest.getString("id")
            val entry = manifest.optString("entry", "main.lua")
            require(File(temp, entry).isFile) { "В плагине отсутствует $entry" }
            val target = File(root, id.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            if (target.exists()) target.deleteRecursively()
            check(temp.renameTo(target)) { "Не удалось установить плагин" }
            load(target)
            manifest.optString("name", id)
        } finally {
            if (temp.exists()) temp.deleteRecursively()
        }
    }

    fun load(dir: File): Boolean {
        return try {
            val manifest = JSONObject(File(dir, "manifest.json").readText())
            val id = manifest.getString("id")
            val entry = manifest.optString("entry", "main.lua")
            val luaFile = File(dir, entry)
            if (!luaFile.isFile) return false
            val globals = JsePlatform.standardGlobals()
            // No direct filesystem/process/network access from plugin scripts.
            globals["io"] = LuaValue.NIL
            globals["os"] = LuaValue.NIL
            globals["debug"] = LuaValue.NIL
            globals["luajava"] = LuaValue.NIL
            globals["package"] = LuaValue.NIL
            globals["dofile"] = LuaValue.NIL
            globals["loadfile"] = LuaValue.NIL
            installApi(globals)
            InputStreamReader(luaFile.inputStream(), Charsets.UTF_8).use { reader ->
                globals.load(reader, luaFile.name).call()
            }
            loaded += Loaded(id, globals)
            true
        } catch (_: Throwable) { false }
    }

    private fun installApi(g: Globals) {
        val plugin = LuaTable()
        plugin["on_load"] = register("plugin.on_load") { fn -> fn.call() }
        plugin["on_enable"] = register("plugin.on_enable") { fn -> fn.call() }
        plugin["on_disable"] = register("plugin.on_disable") { fn -> fn.call() }
        g["plugin"] = plugin

        val eventsTable = LuaTable()
        eventsTable["on"] = object : VarArgFunction() {
            override fun invoke(args: org.luaj.vm2.Varargs): org.luaj.vm2.Varargs {
                val event = args.arg(1).checkjstring()
                val callback = args.arg(2).checkfunction()
                events.on(event) { payload -> callback.call(toLua(payload)) }
                return LuaValue.NIL
            }
        }
        g["events"] = eventsTable

        val ui = LuaTable()
        ui["notification"] = object : VarArgFunction() {
            override fun invoke(args: org.luaj.vm2.Varargs): org.luaj.vm2.Varargs = LuaValue.NIL
        }
        val yougram = LuaTable()
        yougram["ui"] = ui
        g["yougram"] = yougram
    }

    private fun register(name: String, action: (LuaValue) -> Unit) = object : VarArgFunction() {
        override fun invoke(args: org.luaj.vm2.Varargs): org.luaj.vm2.Varargs {
            val fn = args.arg(1).checkfunction()
            action(fn)
            return LuaValue.NIL
        }
    }

    private fun toLua(value: Any?): LuaValue = when (value) {
        null -> LuaValue.NIL
        is Boolean -> LuaValue.valueOf(value)
        is Number -> LuaValue.valueOf(value.toDouble())
        is String -> LuaValue.valueOf(value)
        is Map<*, *> -> LuaTable().also { table ->
            value.entries.forEach { (k, v) -> table[k.toString()] = toLua(v) }
        }
        is Iterable<*> -> LuaTable().also { table ->
            var i = 1
            value.forEach { table[i++] = toLua(it) }
        }
        else -> LuaValue.valueOf(value.toString())
    }

    fun emitMessageReceived(message: PluginMessage) = events.emit("message_received", message.asMap())
    fun emitMessageEdited(message: PluginMessage) = events.emit("message_edited", message.asMap())
    fun emitMessageDeleted(chatId: Long, messageIds: List<Long>) = events.emit(
        "message_deleted", mapOf("chat_id" to chatId, "message_ids" to messageIds)
    )
}
