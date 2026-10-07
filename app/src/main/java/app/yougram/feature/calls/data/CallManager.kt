package app.yougram.feature.calls.data

import android.content.Context
import app.yougram.core.telegram.TelegramClient
import app.yougram.feature.chat.data.ChatRepository
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.Call
import dev.g000sha256.tdl.dto.CallProtocol
import dev.g000sha256.tdl.dto.CallStateDiscarded
import dev.g000sha256.tdl.dto.CallStateError
import dev.g000sha256.tdl.dto.CallStateExchangingKeys
import dev.g000sha256.tdl.dto.CallStateHangingUp
import dev.g000sha256.tdl.dto.CallStatePending
import dev.g000sha256.tdl.dto.CallStateReady
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class CallPhase { RINGING, CONNECTING, ACTIVE, ENDED }

data class ActiveCall(
    val id: Int,
    val userId: Long,
    val title: String,
    val avatarFileId: Int?,
    val isOutgoing: Boolean,
    val isVideo: Boolean,
    val phase: CallPhase,
    /** Четыре эмодзи для сверки ключа с собеседником. */
    val emojis: List<String> = emptyList(),
    val startedAtMillis: Long? = null,
    val muted: Boolean = false,
    val engineAvailable: Boolean = false,
    val message: String? = null,
)

/** Сигнализация звонков поверх TDLib: исходящие, входящие, принятие, сброс. */
class CallManager(
    private val telegram: TelegramClient,
    private val scope: CoroutineScope,
    private val chats: ChatRepository,
    private val appContext: Context,
    private val audio: CallAudio,
) {
    private val client get() = telegram.client

    /** Подставьте реализацию на tgcalls, чтобы появились звук и видео. */
    @Volatile
    var engine: CallEngine = NoCallEngine

    private val _call = MutableStateFlow<ActiveCall?>(null)
    val call: StateFlow<ActiveCall?> = _call.asStateFlow()

    /** Обновления звонка обрабатываются строго по очереди: иначе start/stop движка и смена состояния пересекаются. */
    private val mutex = Mutex()

    /** Уже завершённые звонки: запоздавшие updateCall по ним не должны заново открывать экран звонка. */
    private val finished = LinkedHashSet<Int>()

    @Volatile
    private var dialing = false

    /** Звонок, для которого уже запущен медиа-движок. */
    @Volatile
    private var engineCallId = Int.MIN_VALUE

    /** Запасной протокол, если движок не сообщил свой. */
    private val fallbackProtocol = CallProtocol(
        udpP2p = true,
        udpReflector = true,
        minLayer = 65,
        maxLayer = 92,
        libraryVersions = arrayOf("2.4.4", "5.0.0", "7.0.0", "9.0.0", "11.0.0"),
    )

    /** Версии библиотеки должны совпадать с теми, что реально умеет движок, иначе собеседник выберет несовместимую. */
    private val protocol: CallProtocol get() = engine.protocol ?: fallbackProtocol

    /** Текущий маршрут звука и наличие Bluetooth-гарнитуры. */
    val route: StateFlow<AudioRoute> = audio.route
    val bluetoothAvailable: StateFlow<Boolean> = audio.bluetoothAvailable

    fun cycleRoute() = audio.cycle()

    fun start() {
        scope.launch {
            client.callUpdates.collect { onCallUpdate(it.call) }
        }
        scope.launch {
            client.newCallSignalingDataUpdates.collect { update ->
                if (update.callId == _call.value?.id) engine.onSignalingData(update.data)
            }
        }
    }

    /** Исходящий звонок пользователю [userId]. */
    fun startCall(userId: Long, video: Boolean) {
        if (_call.value != null || dialing) return
        CallLog.d("CallManager", "startCall user=$userId video=$video")
        dialing = true
        scope.launch {
            try {
                val result = client.createCall(userId = userId, protocol = protocol, isVideo = video)
                if (result is TdlResult.Failure) {
                    CallLog.e("CallManager", "createCall failed code=${result.code} message=${result.message}")
                    val text = if (result.code == 403) "Пользователь не принимает звонки" else result.message
                    showEnded(userId, video, text)
                }
                // Дальше состояние придёт в onCallUpdate.
            } finally {
                dialing = false
            }
        }
    }

    fun accept() {
        val c = _call.value ?: return
        if (c.isOutgoing || c.phase != CallPhase.RINGING) return
        CallLog.d("CallManager", "accept id=${c.id}")
        scope.launch { client.acceptCall(callId = c.id, protocol = protocol) }
    }

    /** Сбросить или отклонить звонок. */
    fun hangUp(disconnected: Boolean = false) {
        val c = _call.value ?: return
        if (c.phase == CallPhase.ENDED) return
        CallLog.d("CallManager", "hangUp id=${c.id} disconnected=$disconnected phase=${c.phase} msg=${c.message} caller=${Throwable().stackTrace.drop(1).take(3).joinToString { it.methodName }}")
        markFinished(c.id)
        val duration = c.startedAtMillis?.let { ((System.currentTimeMillis() - it) / 1000).toInt() } ?: 0
        engine.stop()
        audio.stop()
        _call.update { s -> s?.takeIf { it.id == c.id }?.copy(phase = CallPhase.ENDED, message = "Звонок завершён") ?: s }
        scope.launch {
            client.discardCall(
                callId = c.id,
                isDisconnected = disconnected,
                duration = duration,
                isVideo = c.isVideo,
                connectionId = 0L,
                inviteLink = "",
            )
        }
        clearLater(c.id, 800)
    }

    fun setMuted(muted: Boolean) {
        engine.setMuted(muted)
        _call.update { it?.copy(muted = muted) }
    }

    private suspend fun onCallUpdate(call: Call) = mutex.withLock {
        if (isFinished(call.id)) return@withLock
        val state = call.state
        CallLog.d("CallManager", "update id=${call.id} user=${call.userId} out=${call.isOutgoing} video=${call.isVideo} state=${state::class.simpleName}")
        // Уже завершённый звонок на экране не мешает новому.
        val current = _call.value?.takeUnless { it.phase == CallPhase.ENDED && it.id != call.id }

        if (current != null && current.id != call.id) {
            // Второй входящий, пока идёт другой звонок: занято.
            if (!call.isOutgoing && state is CallStatePending) {
                markFinished(call.id)
                client.discardCall(callId = call.id, isDisconnected = false, duration = 0, isVideo = call.isVideo, connectionId = 0L, inviteLink = "")
            }
            return@withLock
        }
        // Запоздалое «завершён» по звонку, которого на экране уже нет, новый звонок не создаёт.
        if (current == null && (state is CallStateDiscarded || state is CallStateError || state is CallStateHangingUp)) {
            markFinished(call.id)
            return@withLock
        }

        val isNew = current == null
        if (isNew) CallLog.header(call.id, call.isOutgoing, call.isVideo)
        val base = current ?: run {
            val info = runCatching { chats.userCardInfo(call.userId) }.getOrNull()
            ActiveCall(
                id = call.id,
                userId = call.userId,
                title = info?.first.orEmpty(),
                avatarFileId = info?.second,
                isOutgoing = call.isOutgoing,
                isVideo = call.isVideo,
                phase = CallPhase.RINGING,
            )
        }
        when (state) {
            is CallStatePending -> _call.value = base.copy(
                phase = CallPhase.RINGING,
                message = if (call.isOutgoing) (if (state.isReceived) "Звонок…" else "Соединение…") else null,
            )
            is CallStateExchangingKeys -> _call.value = base.copy(phase = CallPhase.CONNECTING, message = "Обмен ключами…")
            is CallStateReady -> {
                val first = engineCallId != call.id
                CallLog.d(
                    "CallManager",
                    "ready first=$first engine=${engine::class.simpleName} available=${engine.isAvailable} allowP2p=${state.allowP2p} " +
                            "versions=${state.protocol.libraryVersions.toList()} layers=${state.protocol.minLayer}..${state.protocol.maxLayer} " +
                            "customParams=${state.customParameters.length} emojis=${state.emojis.orEmpty().toList()} servers=${state.servers.size}",
                )
                if (first) state.servers.forEach { sv ->
                    CallLog.d("CallManager", "server id=${sv.id} type=${sv.type::class.simpleName} ip=${sv.ipAddress} ipv6=${sv.ipv6Address} port=${sv.port}")
                }
                val linked = current?.phase == CallPhase.ACTIVE || !engine.isAvailable
                _call.value = base.copy(
                    phase = if (linked) CallPhase.ACTIVE else CallPhase.CONNECTING,
                    emojis = state.emojis.orEmpty().toList(),
                    startedAtMillis = if (linked) base.startedAtMillis ?: System.currentTimeMillis() else null,
                    engineAvailable = engine.isAvailable,
                    message = if (engine.isAvailable) (if (linked) null else "Соединение…") else "Медиа-движок не подключён: звука и видео нет",
                )
                if (first) {
                    engineCallId = call.id
                    audio.start(call.isVideo)
                    engine.start(call.userId, state, call.isOutgoing, call.isVideo, { data ->
                        scope.launch {
                            val sent = client.sendCallSignalingData(callId = call.id, data = data)
                            if (sent is TdlResult.Failure) CallLog.e("CallManager", "sendCallSignalingData failed code=${sent.code} message=${sent.message}")
                        }
                    }) { link -> onLink(call.id, link) }
                    if (base.muted) engine.setMuted(true)
                    watchdog(call.id)
                }
            }
            is CallStateHangingUp -> {
                engine.stop()
                audio.stop()
                _call.value = base.copy(phase = CallPhase.ENDED, message = "Завершение…")
                clearLater(base.id, 5000)
            }
            is CallStateDiscarded -> finish(base, "Звонок завершён")
            is CallStateError -> {
                CallLog.e("CallManager", "call error code=${state.error.code} message=${state.error.message}")
                finish(base, state.error.message.ifEmpty { "Ошибка звонка" })
            }
            else -> Unit
        }

        // Служба запускается только после того, как состояние звонка уже записано, иначе она сама же себя остановит.
        if (isNew && _call.value?.id == call.id) CallService.start(appContext)
    }

    /** Реальное состояние медиа-соединения: таймер и «активный» режим только после CONNECTED. */
    private fun onLink(id: Int, link: EngineLink) {
        CallLog.d("CallManager", "engine link id=$id -> $link")
        scope.launch {
            mutex.withLock {
                _call.update { c ->
                    if (c == null || c.id != id || c.phase == CallPhase.ENDED) return@update c
                    when (link) {
                        EngineLink.CONNECTED -> if (c.phase == CallPhase.ACTIVE) c.copy(message = null) else
                            c.copy(phase = CallPhase.ACTIVE, startedAtMillis = System.currentTimeMillis(), message = null)
                        EngineLink.CONNECTING -> if (c.phase == CallPhase.ACTIVE) c.copy(message = "Переподключение…") else c
                        EngineLink.FAILED -> c.copy(message = "Нет соединения с собеседником")
                    }
                }
            }
        }
    }

    /** Если медиа-соединение не поднялось за 30 с, звонок сбрасывается, а не висит «подключённым». */
    private fun watchdog(id: Int) {
        scope.launch {
            delay(30_000)
            val c = _call.value
            if (c != null && c.id == id && c.phase == CallPhase.CONNECTING) {
                CallLog.e("CallManager", "watchdog: media link not up after 30s, hanging up")
                hangUp(disconnected = true)
            }
        }
    }

    private fun finish(base: ActiveCall, text: String) {
        CallLog.d("CallManager", "finish id=${base.id} text=$text")
        markFinished(base.id)
        engine.stop()
        audio.stop()
        _call.value = base.copy(phase = CallPhase.ENDED, message = text)
        clearLater(base.id, 1200)
    }

    private fun showEnded(userId: Long, video: Boolean, text: String) {
        scope.launch {
            if (_call.value != null) return@launch
            val info = runCatching { chats.userCardInfo(userId) }.getOrNull()
            _call.value = ActiveCall(
                id = -1, userId = userId, title = info?.first.orEmpty(), avatarFileId = info?.second,
                isOutgoing = true, isVideo = video, phase = CallPhase.ENDED, message = text,
            )
            clearLater(-1, 1800)
        }
    }

    /** Убирает экран звонка через [millis], только если на нём всё ещё этот же звонок. */
    private fun clearLater(id: Int, millis: Long) {
        scope.launch {
            delay(millis)
            _call.update { if (it?.id == id) null else it }
        }
    }

    private fun markFinished(id: Int) {
        synchronized(finished) {
            finished.add(id)
            if (finished.size > 64) finished.remove(finished.first())
        }
    }

    private fun isFinished(id: Int): Boolean = synchronized(finished) { id in finished }
}