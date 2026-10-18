package app.yougram.plugin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.yougram.core.telegram.TelegramClient
import app.yougram.core.telegram.getOrThrow
import app.yougram.feature.chat.data.ChatRepository
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

data class PluginCommand(
    val pluginId: String,
    val name: String,
    val description: String,
    val usage: String = "",
    val aliases: List<String> = emptyList(),
)

/** [where]: "message_menu" — меню сообщения, "chat_menu" — меню чата. */
data class PluginAction(
    val pluginId: String,
    val id: String,
    val title: String,
    val where: String,
    val icon: String = "",
)

private const val MAX_STORAGE_VALUE = 64 * 1024
private const val MAX_STORAGE_KEYS = 1000
private const val MAX_TIMERS = 16
private const val MAX_COMMANDS = 32
private const val MAX_ACTIONS = 32
private const val MAX_ASYNC_REQUESTS = 8
private const val MAX_IPC_PAYLOAD = 64 * 1024
private const val THROTTLE_CALLS = 30
private const val THROTTLE_WINDOW_MS = 30_000L
private const val CALL_TIMEOUT_MS = 30_000L
private const val DOWNLOAD_TIMEOUT_MS = 120_000L
private const val SYNC_HTTP_TIMEOUT_MS = 25_000
private const val ASYNC_HTTP_TIMEOUT_MS = 60_000
private const val DOWNLOAD_HTTP_TIMEOUT_MS = 110_000
private val COMMAND_NAME = Regex("[a-z0-9_]{1,32}")
private val ACTION_ID = Regex("[A-Za-z0-9_.-]{1,64}")
private val ACTION_PLACES = setOf("message_menu", "chat_menu")

private fun longsOf(arr: JSONArray): List<Long> {
    val out = ArrayList<Long>(arr.length())
    for (i in 0 until arr.length()) out.add(arr.getLong(i))
    return out
}

private fun stringsOf(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    return List(arr.length()) { arr.getString(it) }
}

/** Всё, что плагин может попросить у приложения через host->call() (API 2.0, см. yougram_plugin.h). */
class PluginApi(
    private val context: Context,
    private val telegram: TelegramClient,
    private val chats: ChatRepository,
    private val manager: NativePluginManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private val timers = ConcurrentHashMap<String, Job>()
    private val requests = ConcurrentHashMap<String, Job>()
    private val limiter = PluginRateLimiter(THROTTLE_CALLS, THROTTLE_WINDOW_MS)
    private val registryLock = Any()

    private val _commands = MutableStateFlow<List<PluginCommand>>(emptyList())
    val commands: StateFlow<List<PluginCommand>> = _commands.asStateFlow()

    private val _actions = MutableStateFlow<List<PluginAction>>(emptyList())
    val actions: StateFlow<List<PluginAction>> = _actions.asStateFlow()

    /** Точка входа из JNI. Вызывается из потока плагина, поэтому блокирующая. */
    fun call(pluginId: String, method: String, argsJson: String): String {
        return try {
            require(method in PluginMethods.REQUIRED) { "Неизвестный метод: $method" }
            val need = PluginMethods.REQUIRED.getValue(method)
            if (need != null && need !in manager.permissionsOf(pluginId)) error("Нет разрешения $need")
            if (method in PluginMethods.THROTTLED) {
                check(limiter.tryAcquire(pluginId)) {
                    "Слишком часто: не больше $THROTTLE_CALLS вызовов за ${THROTTLE_WINDOW_MS / 1000} с"
                }
            }
            val args = if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
            val limit = if (method == "http_download") DOWNLOAD_TIMEOUT_MS else CALL_TIMEOUT_MS
            val result = runBlocking(Dispatchers.IO) {
                withTimeout(limit) { dispatch(pluginId, method, args) }
            }
            JSONObject().put("ok", true).put("result", JSONObject.wrap(result) ?: JSONObject.NULL).toString()
        } catch (t: Throwable) {
            JSONObject().put("ok", false).put("error", t.message ?: t.toString()).toString()
        }
    }

    private suspend fun dispatch(pid: String, method: String, a: JSONObject): Any? {
        val td = telegram.client
        return when (method) {
            // ---- без разрешений ----
            "get_app_info" -> appInfo(pid)
            "storage_get" -> storage(pid).getString(a.getString("key"), null)
            "storage_set" -> storageSet(pid, a.getString("key"), a.getString("value"))
            "storage_delete" -> {
                storage(pid).edit().remove(a.getString("key")).apply()
                true
            }
            "storage_keys" -> {
                val prefix = a.optString("prefix", "")
                storage(pid).all.keys.filter { it.startsWith(prefix) }.sorted()
            }
            "storage_clear" -> {
                storage(pid).edit().clear().apply()
                true
            }
            "settings_get" -> manager.settings.get(manifestOf(pid), a.getString("key"))
            "settings_all" -> manager.settings.all(manifestOf(pid))
            "settings_set" -> {
                manager.settings.setAll(manifestOf(pid), mapOf(a.getString("key") to a.get("value").toString()))
                true
            }
            "timer_start" -> {
                startTimer(pid, a.getString("id"), a.getLong("interval_ms"), a.optBoolean("repeat", true))
                true
            }
            "timer_stop" -> {
                timers.remove("$pid:" + a.getString("id"))?.cancel()
                true
            }

            // ---- chat.read ----
            "get_me" -> TdObjectMapper.toMap(td.getMe().getOrThrow())
            "get_chat" -> TdObjectMapper.toMap(td.getChat(chatId = a.getLong("chat_id")).getOrThrow())
            "get_user" -> TdObjectMapper.toMap(td.getUser(userId = a.getLong("user_id")).getOrThrow())
            "get_message" -> TdObjectMapper.toMap(
                td.getMessage(chatId = a.getLong("chat_id"), messageId = a.getLong("message_id")).getOrThrow(),
            )
            "get_chats" -> {
                val limit = a.optInt("limit", 50).coerceIn(1, 200)
                val list = if (a.optBoolean("archived", false)) chats.archivedChats.value else chats.chats.value
                list.take(limit).map { c ->
                    linkedMapOf<String, Any?>(
                        "id" to c.id,
                        "title" to c.title,
                        "last_message" to c.lastMessage,
                        "last_message_date" to c.lastMessageDate,
                        "unread_count" to c.unreadCount,
                        "muted" to c.muted,
                        "last_outgoing" to c.lastOutgoing,
                    )
                }
            }
            "get_chat_history" -> {
                val page = td.getChatHistory(
                    chatId = a.getLong("chat_id"),
                    fromMessageId = a.optLong("from_message_id", 0L),
                    offset = a.optInt("offset", 0),
                    limit = a.optInt("limit", 20).coerceIn(1, 100),
                    onlyLocal = a.optBoolean("only_local", false),
                ).getOrThrow()
                page.messages.orEmpty().filterNotNull().map { TdObjectMapper.toMap(it) }
            }
            "search_messages" -> {
                val page = chats.searchMessages(a.getString("query"), "", a.optInt("limit", 30).coerceIn(1, 100))
                TdObjectMapper.toMap(page)
            }
            "mark_read" -> {
                chats.markRead(a.getLong("chat_id"), longsOf(a.getJSONArray("message_ids")))
                true
            }

            // ---- chat.send ----
            "send_message" -> {
                val replyTo = a.optLong("reply_to", 0L)
                // fromPlugin = true: отправка плагина идёт мимо хука before_send, иначе плагин зациклится на себе.
                chats.sendText(
                    a.getLong("chat_id"),
                    a.getString("text"),
                    if (replyTo != 0L) replyTo else null,
                    true,
                )
                true
            }
            "send_photo" -> {
                val file = PluginPaths.inside(PluginPaths.dataDir(context, pid), a.getString("path"))
                chats.sendPhoto(a.getLong("chat_id"), file.absolutePath, a.optString("caption", ""))
                true
            }
            "send_document" -> {
                val file = PluginPaths.inside(PluginPaths.dataDir(context, pid), a.getString("path"))
                chats.sendDocument(a.getLong("chat_id"), file.absolutePath, a.optString("caption", ""))
                true
            }
            "forward_message" -> {
                chats.forwardMessage(a.getLong("to_chat_id"), a.getLong("from_chat_id"), a.getLong("message_id"))
                true
            }

            // ---- chat.modify ----
            "edit_message" -> {
                chats.editText(a.getLong("chat_id"), a.getLong("message_id"), a.getString("text"), fromPlugin = true)
                true
            }
            "delete_message" -> {
                chats.deleteMessage(a.getLong("chat_id"), a.getLong("message_id"))
                true
            }
            "react" -> {
                chats.react(a.getLong("chat_id"), a.getLong("message_id"), a.getString("emoji"), a.optBoolean("remove", false))
                true
            }
            "pin_message" -> {
                chats.pinMessage(a.getLong("chat_id"), a.getLong("message_id"))
                true
            }

            // ---- network ----
            "http_request" -> PluginHttp.request(a, SYNC_HTTP_TIMEOUT_MS)
            "http_request_async" -> startAsyncRequest(pid, a)
            "http_download" -> PluginHttp.download(a, PluginPaths.dataDir(context, pid), DOWNLOAD_HTTP_TIMEOUT_MS)

            // ---- hooks ----
            "register_command" -> registerCommand(pid, a)
            "unregister_command" -> {
                val name = normalizeCommand(a.getString("name"))
                synchronized(registryLock) {
                    _commands.update { list -> list.filterNot { it.pluginId == pid && it.name == name } }
                }
                true
            }

            // ---- ui ----
            "register_action" -> registerAction(pid, a)
            "unregister_action" -> {
                val id = a.getString("id")
                synchronized(registryLock) {
                    _actions.update { list -> list.filterNot { it.pluginId == pid && it.id == id } }
                }
                true
            }
            "toast" -> {
                val text = listOf(a.optString("title"), a.optString("message"))
                    .filter { it.isNotEmpty() }
                    .joinToString(": ")
                main.post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
                true
            }
            "copy_to_clipboard" -> {
                val text = a.getString("text")
                main.post {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("plugin", text))
                }
                true
            }

            // ---- ipc ----
            "send_to_plugin" -> {
                val target = a.getString("plugin_id")
                require(target != pid) { "Нельзя отправить сообщение самому себе" }
                require(manager.isEnabled(target)) { "Плагин $target не запущен" }
                val payload: Any = a.opt("payload") ?: JSONObject.NULL
                require(payload.toString().length <= MAX_IPC_PAYLOAD) { "Сообщение слишком большое" }
                manager.emitTo(target, "plugin_message", linkedMapOf<String, Any?>("from" to pid, "payload" to payload))
                true
            }
            else -> error("Неизвестный метод: $method")
        }
    }

    private fun manifestOf(pid: String): PluginManifest =
        manager.manifestOf(pid) ?: error("Плагин не найден")

    private fun appInfo(pid: String): Map<String, Any?> = linkedMapOf(
        "api" to PluginApiVersion.CURRENT,
        "api_major" to PluginApiVersion.MAJOR,
        "api_minor" to PluginApiVersion.MINOR,
        "app_version" to runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty(),
        "sdk" to Build.VERSION.SDK_INT,
        "abi" to Build.SUPPORTED_ABIS.first(),
        "plugin_id" to pid,
        "data_dir" to PluginPaths.dataDir(context, pid).absolutePath,
        "language" to Locale.getDefault().toLanguageTag(),
        "permissions" to manager.permissionsOf(pid),
        "methods" to PluginMethods.REQUIRED.keys.sorted(),
    )

    private fun normalizeCommand(raw: String) = raw.lowercase().removePrefix("/")

    private fun registerCommand(pid: String, a: JSONObject): Boolean {
        val name = normalizeCommand(a.getString("name"))
        require(COMMAND_NAME.matches(name)) { "Имя команды: a-z, 0-9, _ (до 32 символов)" }
        val aliases = stringsOf(a.optJSONArray("aliases")).map { normalizeCommand(it) }.distinct().filter { it != name }
        require(aliases.size <= 8) { "Не больше 8 алиасов" }
        aliases.forEach { require(COMMAND_NAME.matches(it)) { "Недопустимый алиас: $it" } }
        val taken = listOf(name) + aliases
        val command = PluginCommand(
            pluginId = pid,
            name = name,
            description = a.optString("description", "").take(200),
            usage = a.optString("usage", "").take(200),
            aliases = aliases,
        )
        synchronized(registryLock) {
            val current = _commands.value
            current.firstOrNull { it.pluginId != pid && (it.name in taken || it.aliases.any { alias -> alias in taken }) }
                ?.let { error("Команда уже занята плагином ${it.pluginId}") }
            val own = current.filter { it.pluginId == pid && it.name != name }
            check(own.size < MAX_COMMANDS) { "Слишком много команд" }
            _commands.value = current.filterNot { it.pluginId == pid && it.name == name } + command
        }
        return true
    }

    private fun registerAction(pid: String, a: JSONObject): Boolean {
        val id = a.getString("id")
        require(ACTION_ID.matches(id)) { "Недопустимый id действия" }
        val title = a.getString("title").trim()
        require(title.isNotEmpty() && title.length <= 60) { "Название действия: 1–60 символов" }
        val where = a.optString("where", "message_menu")
        require(where in ACTION_PLACES) { "where: ${ACTION_PLACES.joinToString()}" }
        val action = PluginAction(pid, id, title, where, a.optString("icon", "").take(64))
        synchronized(registryLock) {
            val current = _actions.value
            check(current.count { it.pluginId == pid && it.id != id } < MAX_ACTIONS) { "Слишком много действий" }
            _actions.value = current.filterNot { it.pluginId == pid && it.id == id } + action
        }
        return true
    }

    /** Если текст — команда (или алиас) зарегистрированного плагина, возвращает (команда, аргументы). */
    fun matchCommand(text: String): Pair<PluginCommand, String>? {
        if (!text.startsWith("/")) return null
        val name = text.substring(1).substringBefore(' ').substringBefore('@').lowercase()
        val cmd = _commands.value.firstOrNull { it.name == name || name in it.aliases } ?: return null
        return Pair(cmd, text.substringAfter(' ', "").trim())
    }

    /** UI вызывает это, когда пользователь нажал пункт плагина (например, в меню сообщения). */
    fun invokeAction(action: PluginAction, chatId: Long, messageId: Long) {
        manager.emitTo(
            action.pluginId,
            "action",
            linkedMapOf<String, Any?>("id" to action.id, "chat_id" to chatId, "message_id" to messageId),
        )
    }

    /** Плагин выключен/удалён/перезагружается: чистим всё, что он зарегистрировал. */
    fun onPluginStopped(pluginId: String) {
        val prefix = "$pluginId:"
        timers.keys.filter { it.startsWith(prefix) }.forEach { timers.remove(it)?.cancel() }
        requests.keys.filter { it.startsWith(prefix) }.forEach { requests.remove(it)?.cancel() }
        limiter.reset(pluginId)
        synchronized(registryLock) {
            _commands.update { list -> list.filter { it.pluginId != pluginId } }
            _actions.update { list -> list.filter { it.pluginId != pluginId } }
        }
    }

    /** Плагин удалён: стираем его хранилище и файлы. Настройки чистит менеджер. */
    fun wipeData(pluginId: String) {
        context.deleteSharedPreferences(PluginPaths.storageName(pluginId))
        PluginPaths.dataDir(context, pluginId).deleteRecursively()
    }

    fun stopAll() {
        timers.values.forEach { it.cancel() }
        timers.clear()
        requests.values.forEach { it.cancel() }
        requests.clear()
        limiter.clear()
        synchronized(registryLock) {
            _commands.value = emptyList()
            _actions.value = emptyList()
        }
    }

    private fun startTimer(pid: String, id: String, intervalMs: Long, repeat: Boolean) {
        val prefix = "$pid:"
        val key = prefix + id
        timers.remove(key)?.cancel()
        check(timers.keys.count { it.startsWith(prefix) } < MAX_TIMERS) { "Слишком много таймеров" }
        val every = if (intervalMs < 1_000L) 1_000L else intervalMs
        timers[key] = scope.launch {
            do {
                delay(every)
                manager.emitTo(pid, "timer", linkedMapOf<String, Any?>("id" to id))
            } while (repeat)
            timers.remove(key)
        }
    }

    /** Запрос в фоне: сразу возвращает request_id, итог приходит плагину событием http_response. */
    private fun startAsyncRequest(pid: String, a: JSONObject): Map<String, Any?> {
        val prefix = "$pid:"
        check(requests.keys.count { it.startsWith(prefix) } < MAX_ASYNC_REQUESTS) { "Слишком много запросов одновременно" }
        val id = a.optString("request_id", "").ifEmpty { UUID.randomUUID().toString() }
        val key = prefix + id
        check(!requests.containsKey(key)) { "request_id уже используется" }
        val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val payload = linkedMapOf<String, Any?>("request_id" to id)
            try {
                payload["ok"] = true
                payload.putAll(PluginHttp.request(a, ASYNC_HTTP_TIMEOUT_MS))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                payload.clear()
                payload["request_id"] = id
                payload["ok"] = false
                payload["error"] = t.message ?: t.toString()
            }
            requests.remove(key)
            if (isActive) manager.emitTo(pid, "http_response", payload)
        }
        requests[key] = job
        job.start()
        return linkedMapOf("request_id" to id)
    }

    private fun storage(pid: String) =
        context.getSharedPreferences(PluginPaths.storageName(pid), Context.MODE_PRIVATE)

    private fun storageSet(pid: String, key: String, value: String): Boolean {
        require(key.isNotEmpty() && key.length <= 128) { "Ключ: 1–128 символов" }
        require(value.length <= MAX_STORAGE_VALUE) { "Значение слишком большое" }
        val prefs = storage(pid)
        if (!prefs.contains(key)) check(prefs.all.size < MAX_STORAGE_KEYS) { "Слишком много ключей" }
        prefs.edit().putString(key, value).apply()
        return true
    }
}
