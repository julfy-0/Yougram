package app.yougram.plugin

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Хост нативных (C++) плагинов. Плагин — .so с экспортом `yougram_plugin_entry` (см. cpp/include/yougram_plugin.h),
 * упакованный в .ygplugin (zip: manifest.json + lib/<abi>/<entry>). Загрузка и вызовы — через JNI (yougram_plugin_host).
 *
 * ВАЖНО: нативный код не изолирован — он работает с правами приложения и может его уронить.
 */
class NativePluginManager(private val context: Context) {
    companion object {
        const val API_VERSION = "1.0"
        private const val TAG = "YougramPlugins"
        private const val BUNDLED_ID = "com.yougram.example.hello"
        private const val MAX_UNPACKED_BYTES = 64L * 1024 * 1024
        private val ENTRY = Regex("[A-Za-z0-9._-]{1,100}\\.so")

        /** false, если библиотека хоста не загрузилась (например, нет сборки под ABI устройства). */
        val hostAvailable: Boolean = runCatching { System.loadLibrary("yougram_plugin_host") }
            .onFailure { Log.e(TAG, "Native plugin host unavailable", it) }
            .isSuccess
    }

    val events = PluginEventBus()

    /** API для плагинов (host->call). Выставляется из AppContainer после создания ChatRepository. */
    @Volatile var api: PluginApi? = null

    private val root = File(context.filesDir, "plugins").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("yougram_plugins", Context.MODE_PRIVATE)
    private val lock = Any()
    private val loaded = HashSet<String>()
    private val errors = HashMap<String, String>()
    private val permissionCache = ConcurrentHashMap<String, List<String>>()
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "yougram-plugins") }
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var active = false

    private val _plugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val plugins: StateFlow<List<InstalledPlugin>> = _plugins.asStateFlow()

    private external fun nativeInit()
    private external fun nativeLoad(id: String, path: String): String?
    private external fun nativeSetEnabled(id: String, enabled: Boolean): String?
    private external fun nativeUnload(id: String)
    private external fun nativeEmit(event: String, json: ByteArray, allowed: String)
    private external fun nativeHook(hook: String, json: ByteArray, allowed: String): ByteArray?

    init {
        if (hostAvailable) {
            nativeInit()
            listOf("message_received", "message_edited", "message_deleted").forEach { name ->
                events.on(name) { payload -> dispatch(name, payload, idsWith(PluginPermission.CHAT_READ)) }
            }
        }
    }

    fun permissionsOf(pluginId: String): List<String> = permissionCache[pluginId].orEmpty()

    /** Список вида ",id1,id2," — плагины, которые включены и имеют разрешение. */
    fun idsWith(permission: String): String = _plugins.value
        .filter { it.enabled && permission in it.manifest.permissions }
        .joinToString(",", prefix = ",", postfix = ",") { it.manifest.id }

    fun loadAll() = synchronized(lock) {
        api?.stopAll()
        if (hostAvailable) loaded.toList().forEach { nativeUnload(it) }
        loaded.clear()
        errors.clear()
        installBundledExample()
        root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            readManifest(dir)?.let { loadOne(dir, it) }
        }
        refresh()
    }

    fun setEnabled(pluginId: String, enabled: Boolean) = synchronized(lock) {
        if (!enabled) api?.onPluginStopped(pluginId)
        setDisabled(pluginId, !enabled)
        val dir = File(root, pluginId)
        if (pluginId !in loaded) {
            if (enabled) readManifest(dir)?.let { loadOne(dir, it) }
        } else {
            nativeSetEnabled(pluginId, enabled)?.let { errors[pluginId] = it }
        }
        refresh()
    }

    fun uninstall(pluginId: String) = synchronized(lock) {
        api?.onPluginStopped(pluginId)
        if (hostAvailable && pluginId in loaded) nativeUnload(pluginId)
        loaded -= pluginId
        errors.remove(pluginId)
        permissionCache.remove(pluginId)
        setDisabled(pluginId, false)
        File(root, pluginId).deleteRecursively()
        refresh()
    }

    /** Устанавливает .ygplugin; бросает исключение с понятным текстом, если пакет некорректен или не загрузился. */
    fun installFromZip(zipFile: File): InstalledPlugin {
        val staging = File(context.cacheDir, "plugin-install-${System.nanoTime()}").apply { mkdirs() }
        try {
            var total = 0L
            ZipInputStream(zipFile.inputStream().buffered()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val name = entry.name.replace('\\', '/')
                    require(!name.startsWith("/") && !name.split('/').contains("..")) { "Небезопасный архив плагина" }
                    val target = File(staging, name)
                    require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) { "Небезопасный архив плагина" }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out ->
                            val buf = ByteArray(16 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                total += n
                                require(total <= MAX_UNPACKED_BYTES) { "Плагин слишком большой" }
                                out.write(buf, 0, n)
                            }
                        }
                    }
                }
            }
            val manifest = readManifest(staging) ?: error("manifest.json отсутствует или повреждён")
            validate(manifest)
            resolveLibrary(staging, manifest.copy(bundled = false)) // есть ли .so под ABI устройства

            return synchronized(lock) {
                val target = File(root, manifest.id)
                api?.onPluginStopped(manifest.id)
                if (hostAvailable && manifest.id in loaded) nativeUnload(manifest.id)
                loaded -= manifest.id
                target.deleteRecursively()
                staging.copyRecursively(target, overwrite = true)
                target.walkTopDown().filter { it.isFile && it.name.endsWith(".so") }.forEach { it.setReadOnly() }
                setDisabled(manifest.id, false)
                loadOne(target, manifest.copy(bundled = false))
                val err = errors[manifest.id]
                refresh()
                if (err != null) {
                    uninstall(manifest.id)
                    error("Плагин не загрузился: $err")
                }
                _plugins.value.first { it.manifest.id == manifest.id }
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    fun emitMessageReceived(message: PluginMessage) = events.emit("message_received", message.asMap())
    fun emitMessageEdited(message: PluginMessage) = events.emit("message_edited", message.asMap())
    fun emitMessageDeleted(chatId: Long, messageIds: List<Long>) =
        events.emit("message_deleted", mapOf("chat_id" to chatId, "message_ids" to messageIds))

    /** Событие только одному плагину (timer, command, action). */
    fun emitTo(pluginId: String, event: String, payload: Map<String, Any?>) = dispatch(event, payload, ",$pluginId,")

    /**
     * Хук перед отправкой текста. Возвращает итоговый текст или null, если отправку нужно отменить
     * (плагин отменил сообщение или это была команда плагина).
     */
    suspend fun beforeSend(chatId: Long, text: String, replyTo: Long?): String? {
        if (!hostAvailable || !active) return text
        api?.matchCommand(text)?.let { (cmd, args) ->
            emitTo(
                cmd.pluginId, "command",
                linkedMapOf("chat_id" to chatId, "name" to cmd.name, "args" to args, "reply_to" to (replyTo ?: 0L)),
            )
            return null
        }
        val allowed = idsWith(PluginPermission.HOOKS)
        if (allowed == ",,") return text
        val payload = JSONObject().put("chat_id", chatId).put("text", text).put("reply_to", replyTo ?: 0L)
            .toString().toByteArray(Charsets.UTF_8)
        val out = withContext(Dispatchers.IO) {
            try {
                worker.submit(Callable { nativeHook("before_send", payload, allowed) }).get(1500, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                Log.w(TAG, "before_send hook timed out")
                null
            } catch (t: Throwable) {
                Log.e(TAG, "before_send hook failed", t)
                null
            }
        } ?: return text
        val json = runCatching { JSONObject(String(out, Charsets.UTF_8)) }.getOrNull() ?: return text
        if (json.optBoolean("cancel", false)) return null
        return json.optString("text", text).takeIf { it.isNotEmpty() } ?: text
    }

    /** Вызывается из JNI, когда плагин просит показать уведомление. */
    @Suppress("unused")
    fun onNativeNotify(pluginId: String, title: ByteArray, message: ByteArray) {
        val t = String(title, Charsets.UTF_8)
        val m = String(message, Charsets.UTF_8)
        Log.i(TAG, "[$pluginId] $t: $m")
        main.post { Toast.makeText(context, "$t: $m", Toast.LENGTH_LONG).show() }
    }

    /** Вызывается из JNI: host->call() плагина. */
    @Suppress("unused")
    fun onNativeCall(pluginId: String, method: String, args: ByteArray): ByteArray {
        val response = api?.call(pluginId, method, String(args, Charsets.UTF_8))
            ?: """{"ok":false,"error":"API недоступен"}"""
        return response.toByteArray(Charsets.UTF_8)
    }

    private fun dispatch(event: String, payload: Any?, allowed: String) {
        if (!hostAvailable || !active) return
        val json = (runCatching { JSONObject.wrap(payload)?.toString() }.getOrNull() ?: "null").toByteArray(Charsets.UTF_8)
        worker.execute {
            runCatching { nativeEmit(event, json, allowed) }.onFailure { Log.e(TAG, "Event $event failed", it) }
        }
    }

    private fun loadOne(dir: File, manifest: PluginManifest) {
        errors.remove(manifest.id)
        permissionCache[manifest.id] = manifest.permissions
        api?.onPluginStopped(manifest.id)
        if (!hostAvailable) {
            errors[manifest.id] = "Нативный хост плагинов недоступен на этом устройстве"
            return
        }
        val bundled = manifest.bundled && manifest.id == BUNDLED_ID
        val failure = runCatching {
            validate(manifest)
            nativeLoad(manifest.id, resolveLibrary(dir, manifest.copy(bundled = bundled)))
        }.getOrElse { it.message ?: it.toString() }
        if (failure != null) {
            errors[manifest.id] = failure
            return
        }
        loaded += manifest.id
        if (manifest.id !in disabledIds()) nativeSetEnabled(manifest.id, true)?.let { errors[manifest.id] = it }
    }

    private fun resolveLibrary(dir: File, manifest: PluginManifest): String {
        if (manifest.bundled) {
            // Библиотека лежит в APK: подгружаем по имени, дальше хост находит её через dlopen по soname.
            System.loadLibrary(manifest.entry.removePrefix("lib").removeSuffix(".so"))
            return manifest.entry
        }
        for (abi in Build.SUPPORTED_ABIS) {
            File(dir, "lib/$abi/${manifest.entry}").takeIf { it.isFile }?.let { return it.absolutePath }
        }
        File(dir, manifest.entry).takeIf { it.isFile }?.let { return it.absolutePath }
        error("Нет ${manifest.entry} для ABI устройства (${Build.SUPPORTED_ABIS.joinToString()})")
    }

    private fun validate(manifest: PluginManifest) {
        require(manifest.id.matches(Regex("[A-Za-z0-9._-]{1,120}"))) { "Недопустимый id плагина" }
        require(manifest.name.isNotBlank() && manifest.name.length <= 120) { "Недопустимое имя плагина" }
        require(manifest.api == API_VERSION) { "API плагина ${manifest.api} не поддерживается (нужен $API_VERSION)" }
        require(ENTRY.matches(manifest.entry)) {
            if (manifest.entry.endsWith(".lua")) "Lua-плагины больше не поддерживаются: нужна библиотека .so (C++)"
            else "entry должен быть именем .so файла"
        }
    }

    private fun readManifest(dir: File): PluginManifest? = runCatching {
        val json = JSONObject(File(dir, "manifest.json").readText())
        PluginManifest(
            id = json.getString("id"),
            name = json.getString("name"),
            version = json.getString("version"),
            api = json.optString("api", API_VERSION),
            author = json.optString("author", "Unknown"),
            description = json.optString("description", ""),
            entry = json.optString("entry", "libplugin.so"),
            bundled = json.optBoolean("bundled", false),
            permissions = buildList {
                val array = json.optJSONArray("permissions") ?: return@buildList
                for (i in 0 until array.length()) add(array.getString(i))
            },
        )
    }.getOrNull()

    private fun disabledIds(): Set<String> = prefs.getStringSet("disabled", emptySet()).orEmpty()

    private fun setDisabled(id: String, disabled: Boolean) {
        val set = disabledIds().toMutableSet()
        if (disabled) set += id else set -= id
        prefs.edit().putStringSet("disabled", set).apply()
    }

    private fun refresh() {
        val off = disabledIds()
        _plugins.value = root.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
            readManifest(dir)?.let { m -> InstalledPlugin(m, dir, m.id in loaded && m.id !in off, errors[m.id]) }
        }?.sortedBy { it.manifest.name.lowercase() }.orEmpty()
        active = _plugins.value.any { it.enabled }
    }

    /** Встроенный пример всегда переустанавливается из assets: так он получает новый manifest и права. */
    private fun installBundledExample() {
        val target = File(root, BUNDLED_ID)
        if (target.exists()) target.deleteRecursively()
        runCatching { copyAssetTree("plugins/example-hello", target) }
            .onFailure { Log.w(TAG, "Bundled example plugin unavailable", it) }
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
}