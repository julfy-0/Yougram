package app.yougram.plugin

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.ZeroArgFunction
import org.luaj.vm2.lib.jse.JsePlatform

/** Local Lua plugin host. Plugins are user-owned source code and run inside a restricted Lua environment. */
class PluginManager(private val context: Context) {
    companion object {
        const val API_VERSION = "1.0"
        private const val TAG = "YougramPlugins"
    }

    private val root = File(context.filesDir, "plugins")
    private val runtimes = ConcurrentHashMap<String, PluginRuntime>()
    private val _plugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val plugins: StateFlow<List<InstalledPlugin>> = _plugins.asStateFlow()

    init {
        root.mkdirs()
    }

    fun initialize() {
        root.mkdirs()
        installBundledExampleIfMissing()
        loadAll()
    }

    fun loadAll() {
        runtimes.values.forEach { it.close() }
        runtimes.clear()

        val loaded = root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { directory ->
                runCatching { load(directory) }.getOrElse { error ->
                    Log.e(TAG, "Failed to load ${directory.name}", error)
                    readManifest(directory)?.let { InstalledPlugin(it, directory, false, error.message ?: error.toString()) }
                }
            }
            .orEmpty()

        _plugins.value = loaded.sortedBy { it.manifest.name.lowercase() }
    }

    fun setEnabled(pluginId: String, enabled: Boolean) {
        val current = _plugins.value.firstOrNull { it.manifest.id == pluginId } ?: return
        if (enabled) {
            runCatching { load(current.directory) }
                .onFailure { error -> updateError(pluginId, error.message ?: error.toString()) }
        } else {
            runtimes.remove(pluginId)?.close()
        }
        refreshState()
    }

    fun installFromZip(zipFile: File): InstalledPlugin {
        val staging = File(context.cacheDir, "plugin-install-${System.nanoTime()}")
        staging.mkdirs()
        try {
            java.util.zip.ZipInputStream(zipFile.inputStream().buffered()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val cleanName = entry.name.replace('\\', '/')
                    require(!cleanName.startsWith("/") && !cleanName.split('/').contains("..")) { "Unsafe plugin archive" }
                    val target = File(staging, cleanName)
                    require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) { "Unsafe plugin archive" }
                    if (entry.isDirectory) target.mkdirs() else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            val manifest = readManifest(staging) ?: error("manifest.json is missing or invalid")
            validateManifest(manifest)
            val target = File(root, manifest.id)
            target.deleteRecursively()
            staging.renameTo(target)
            load(target)
            refreshState()
            return _plugins.value.first { it.manifest.id == manifest.id }
        } finally {
            staging.deleteRecursively()
        }
    }

    fun emit(event: String, vararg args: LuaValue) {
        runtimes.values.forEach { it.emit(event, args) }
    }

    fun uninstall(pluginId: String) {
        runtimes.remove(pluginId)?.close()
        File(root, pluginId).deleteRecursively()
        refreshState()
    }

    private fun load(directory: File): InstalledPlugin {
        val manifest = readManifest(directory) ?: error("manifest.json is missing or invalid")
        validateManifest(manifest)
        val entry = File(directory, manifest.entry)
        require(entry.exists() && entry.isFile) { "Entry script '${manifest.entry}' not found" }

        runtimes.remove(manifest.id)?.close()
        val runtime = PluginRuntime(manifest, directory)
        runtime.start(entry)
        runtimes[manifest.id] = runtime
        return InstalledPlugin(manifest, directory, true)
    }

    private fun validateManifest(manifest: PluginManifest) {
        require(manifest.id.matches(Regex("[A-Za-z0-9._-]{1,120}"))) { "Invalid plugin id" }
        require(manifest.name.isNotBlank() && manifest.name.length <= 120) { "Invalid plugin name" }
        require(manifest.api == API_VERSION) { "Plugin API ${manifest.api} is not supported; current API is $API_VERSION" }
        require(manifest.entry.endsWith(".lua") && !manifest.entry.contains("..")) { "Invalid entry script" }
    }

    private fun readManifest(directory: File): PluginManifest? = runCatching {
        val json = JSONObject(File(directory, "manifest.json").readText())
        PluginManifest(
            id = json.getString("id"),
            name = json.getString("name"),
            version = json.getString("version"),
            api = json.optString("api", API_VERSION),
            author = json.optString("author", "Unknown"),
            description = json.optString("description", ""),
            entry = json.optString("entry", "main.lua"),
            permissions = buildList {
                val array = json.optJSONArray("permissions") ?: return@buildList
                for (i in 0 until array.length()) add(array.getString(i))
            },
        )
    }.getOrNull()

    private fun updateError(id: String, message: String) {
        _plugins.value = _plugins.value.map { if (it.manifest.id == id) it.copy(enabled = false, error = message) else it }
    }

    private fun refreshState() {
        _plugins.value = root.listFiles()?.filter { it.isDirectory }?.mapNotNull { directory ->
            readManifest(directory)?.let { manifest ->
                InstalledPlugin(manifest, directory, runtimes.containsKey(manifest.id))
            }
        }?.sortedBy { it.manifest.name.lowercase() }.orEmpty()
    }

    private fun installBundledExampleIfMissing() {
        val target = File(root, "com.yougram.example.hello")
        if (target.exists()) return
        val assetRoot = "plugins/example-hello"
        runCatching {
            copyAssetTree(assetRoot, target)
        }.onFailure { Log.w(TAG, "Bundled example plugin unavailable", it) }
    }

    private fun copyAssetTree(assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input -> target.outputStream().use { input.copyTo(it) } }
            return
        }
        target.mkdirs()
        for (child in children) copyAssetTree("$assetPath/$child", File(target, child))
    }

    private class PluginRuntime(
        private val manifest: PluginManifest,
        private val directory: File,
    ) {
        private var globals: Globals? = null

        fun start(entry: File) {
            val g = JsePlatform.standardGlobals()
            // Restrict the standard Java/Lua environment. Plugins get only Yougram's explicit API.
            g["io"] = LuaValue.NIL
            g["os"] = LuaValue.NIL
            g["debug"] = LuaValue.NIL
            g["luajava"] = LuaValue.NIL
            g["package"] = LuaValue.NIL
            g["dofile"] = LuaValue.NIL
            g["loadfile"] = LuaValue.NIL

            val pluginTable = LuaValue.tableOf()
            pluginTable.set("on_load", CallbackFunction { loadCallbacks.add(it) })
            pluginTable.set("on_enable", CallbackFunction { enableCallbacks.add(it) })
            pluginTable.set("on_disable", CallbackFunction { disableCallbacks.add(it) })
            g.set("plugin", pluginTable)

            val events = LuaValue.tableOf()
            events.set("on", TwoArgFunctionImpl { event, callback ->
                if (!callback.isfunction()) throw LuaError("events.on requires a function")
                eventCallbacks.computeIfAbsent(event.tojstring()) { mutableListOf() }.add(callback)
                LuaValue.NIL
            })
            g.set("events", events)

            val yougram = LuaValue.tableOf()
            val ui = LuaValue.tableOf()
            ui.set("notification", TwoArgFunctionImpl { title, message ->
                Log.i("YougramPlugins", "[${manifest.id}] ${title.tojstring()}: ${message.tojstring()}")
                LuaValue.NIL
            })
            yougram.set("ui", ui)
            g.set("yougram", yougram)

            globals = g
            g.load(entry.readText(), entry.name).call()
            loadCallbacks.forEach { it.call() }
            enableCallbacks.forEach { it.call() }
        }

        private val loadCallbacks = mutableListOf<LuaValue>()
        private val enableCallbacks = mutableListOf<LuaValue>()
        private val disableCallbacks = mutableListOf<LuaValue>()
        private val eventCallbacks = ConcurrentHashMap<String, MutableList<LuaValue>>()

        fun emit(event: String, args: Array<out LuaValue>) {
            eventCallbacks[event]?.toList()?.forEach { callback ->
                runCatching { callback.invoke(LuaValue.varargsOf(args)) }
                    .onFailure { Log.e("YougramPlugins", "Event $event failed for ${manifest.id}", it) }
            }
        }

        fun close() {
            disableCallbacks.toList().forEach { runCatching { it.call() } }
            globals = null
            loadCallbacks.clear()
            enableCallbacks.clear()
            disableCallbacks.clear()
            eventCallbacks.clear()
        }
    }

    private class CallbackFunction(private val register: (LuaValue) -> Unit) : OneArgFunction() {
        override fun call(arg: LuaValue): LuaValue {
            if (!arg.isfunction()) throw LuaError("plugin lifecycle callback must be a function")
            register(arg)
            return LuaValue.NIL
        }
    }

    private class TwoArgFunctionImpl(private val block: (LuaValue, LuaValue) -> LuaValue) : TwoArgFunction() {
        override fun call(a: LuaValue, b: LuaValue): LuaValue = block(a, b)
    }
}
