package app.yougram.plugin

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.yougram.data.ChatRepository
import app.yougram.data.TelegramClient
import app.yougram.data.getOrThrow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object PluginPermission {
    const val CHAT_READ = "chat.read"
    const val CHAT_SEND = "chat.send"
    const val CHAT_MODIFY = "chat.modify"
    const val NETWORK = "network"
    const val HOOKS = "hooks"
    const val UI = "ui"
    val ALL = listOf(CHAT_READ, CHAT_SEND, CHAT_MODIFY, NETWORK, HOOKS, UI)
}

data class PluginCommand(val pluginId: String, val name: String, val description: String)
data class PluginAction(val pluginId: String, val id: String, val title: String, val where: String)

private const val MAX_STORAGE_VALUE = 64 * 1024
private const val MAX_HTTP_BODY = 2 * 1024 * 1024
private val COMMAND_NAME = Regex("[a-z0-9_]{1,32}")

/** метод -> нужное разрешение (null — разрешение не требуется). */
private val REQUIRED: Map<String, String?> = mapOf(
    "get_me" to PluginPermission.CHAT_READ,
    "get_chat" to PluginPermission.CHAT_READ,
    "get_user" to PluginPermission.CHAT_READ,
    "get_message" to PluginPermission.CHAT_READ,
    "mark_read" to PluginPermission.CHAT_READ,
    "send_message" to PluginPermission.CHAT_SEND,
    "forward_message" to PluginPermission.CHAT_SEND,
    "edit_message" to PluginPermission.CHAT_MODIFY,
    "delete_message" to PluginPermission.CHAT_MODIFY,
    "http_request" to PluginPermission.NETWORK,
    "register_command" to PluginPermission.HOOKS,
    "register_action" to PluginPermission.UI,
    "toast" to PluginPermission.UI,
    "storage_get" to null,
    "storage_set" to null,
    "storage_delete" to null,
    "timer_start" to null,
    "timer_stop" to null,
)

private fun longsOf(arr: JSONArray): List<Long> {
    val out = ArrayList<Long>(arr.length())
    for (i in 0 until arr.length()) out.add(arr.getLong(i))
    return out
}

/** Всё, что плагин может попросить у приложения через host->call(). */
class PluginApi(
    private val context: Context,
    private val telegram: TelegramClient,
    private val chats: ChatRepository,
    private val manager: NativePluginManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val main = Handler(Looper.getMainLooper())
    private val timers = ConcurrentHashMap<String, Job>()

    private val _commands = MutableStateFlow<List<PluginCommand>>(emptyList())
    val commands: StateFlow<List<PluginCommand>> = _commands.asStateFlow()

    private val _actions = MutableStateFlow<List<PluginAction>>(emptyList())
    val actions: StateFlow<List<PluginAction>> = _actions.asStateFlow()

    /** Точка входа из JNI. Вызывается из потока плагина, поэтому блокирующая. */
    fun call(pluginId: String, method: String, argsJson: String): String {
        return try {
            require(method in REQUIRED) { "Неизвестный метод: $method" }
            val need = REQUIRED.getValue(method)
            if (need != null && need !in manager.permissionsOf(pluginId)) error("Нет разрешения $need")
            val args = if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
            val result = runBlocking(Dispatchers.IO) {
                withTimeout(30_000) { dispatch(pluginId, method, args) }
            }
            JSONObject().put("ok", true).put("result", JSONObject.wrap(result) ?: JSONObject.NULL).toString()
        } catch (t: Throwable) {
            JSONObject().put("ok", false).put("error", t.message ?: t.toString()).toString()
        }
    }

    private suspend fun dispatch(pid: String, method: String, a: JSONObject): Any? {
        val td = telegram.client
        return when (method) {
            "get_me" -> TdObjectMapper.toMap(td.getMe().getOrThrow())
            "get_chat" -> TdObjectMapper.toMap(td.getChat(chatId = a.getLong("chat_id")).getOrThrow())
            "get_user" -> TdObjectMapper.toMap(td.getUser(userId = a.getLong("user_id")).getOrThrow())
            "get_message" -> TdObjectMapper.toMap(
                td.getMessage(chatId = a.getLong("chat_id"), messageId = a.getLong("message_id")).getOrThrow(),
            )
            "mark_read" -> {
                chats.markRead(a.getLong("chat_id"), longsOf(a.getJSONArray("message_ids")))
                true
            }
            "send_message" -> {
                val replyTo = a.optLong("reply_to", 0L)
                chats.sendText(
                    a.getLong("chat_id"),
                    a.getString("text"),
                    if (replyTo != 0L) replyTo else null,
                    true,
                )
                true
            }
            "forward_message" -> {
                chats.forwardMessage(a.getLong("to_chat_id"), a.getLong("from_chat_id"), a.getLong("message_id"))
                true
            }
            "edit_message" -> {
                chats.editText(a.getLong("chat_id"), a.getLong("message_id"), a.getString("text"))
                true
            }
            "delete_message" -> {
                chats.deleteMessage(a.getLong("chat_id"), a.getLong("message_id"))
                true
            }
            "http_request" -> http(a)
            "storage_get" -> storage(pid).getString(a.getString("key"), null)
            "storage_set" -> {
                val value = a.getString("value")
                require(value.length <= MAX_STORAGE_VALUE) { "Значение слишком большое" }
                storage(pid).edit().putString(a.getString("key"), value).apply()
                true
            }
            "storage_delete" -> {
                storage(pid).edit().remove(a.getString("key")).apply()
                true
            }
            "timer_start" -> {
                startTimer(pid, a.getString("id"), a.getLong("interval_ms"), a.optBoolean("repeat", true))
                true
            }
            "timer_stop" -> {
                timers.remove(pid + ":" + a.getString("id"))?.cancel()
                true
            }
            "register_command" -> {
                val name = a.getString("name").lowercase().removePrefix("/")
                require(COMMAND_NAME.matches(name)) { "Имя команды: a-z, 0-9, _ (до 32 символов)" }
                val description = a.optString("description", "")
                _commands.update { list ->
                    list.filter { it.name != name } + PluginCommand(pid, name, description)
                }
                true
            }
            "register_action" -> {
                val id = a.getString("id")
                val title = a.getString("title")
                val where = a.optString("where", "message_menu")
                _actions.update { list ->
                    list.filter { !(it.pluginId == pid && it.id == id) } + PluginAction(pid, id, title, where)
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
            else -> error("Неизвестный метод: $method")
        }
    }

    /** Если текст — команда зарегистрированного плагина, возвращает (команда, аргументы). */
    fun matchCommand(text: String): Pair<PluginCommand, String>? {
        if (!text.startsWith("/")) return null
        val name = text.substring(1).substringBefore(' ').substringBefore('@').lowercase()
        val cmd = _commands.value.firstOrNull { it.name == name } ?: return null
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
        _commands.update { list -> list.filter { it.pluginId != pluginId } }
        _actions.update { list -> list.filter { it.pluginId != pluginId } }
    }

    fun stopAll() {
        timers.values.forEach { it.cancel() }
        timers.clear()
        _commands.value = emptyList()
        _actions.value = emptyList()
    }

    private fun startTimer(pid: String, id: String, intervalMs: Long, repeat: Boolean) {
        val prefix = "$pid:"
        val key = prefix + id
        timers.remove(key)?.cancel()
        check(timers.keys.count { it.startsWith(prefix) } < 16) { "Слишком много таймеров" }
        val every = if (intervalMs < 1_000L) 1_000L else intervalMs
        timers[key] = scope.launch {
            do {
                delay(every)
                manager.emitTo(pid, "timer", linkedMapOf<String, Any?>("id" to id))
            } while (repeat)
            timers.remove(key)
        }
    }

    private fun storage(pid: String) = context.getSharedPreferences(
        "yougram_plugin_data_" + pid.replace(Regex("[^A-Za-z0-9._-]"), "_"),
        Context.MODE_PRIVATE,
    )

    private fun http(a: JSONObject): Map<String, Any?> {
        val url = URL(a.getString("url"))
        require(url.protocol == "https" || url.protocol == "http") { "Только http/https" }
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = a.optString("method", "GET").uppercase()
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            val headers = a.optJSONObject("headers")
            if (headers != null) {
                val names = headers.keys()
                while (names.hasNext()) {
                    val k = names.next()
                    conn.setRequestProperty(k, headers.getString(k))
                }
            }
            if (a.has("body")) {
                conn.doOutput = true
                conn.outputStream.use { it.write(a.getString("body").toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = if (stream != null) stream.use { readLimited(it) } else ""
            return mapOf("status" to code, "body" to body)
        } finally {
            conn.disconnect()
        }
    }

    private fun readLimited(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            require(out.size() <= MAX_HTTP_BODY) { "Ответ слишком большой" }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}