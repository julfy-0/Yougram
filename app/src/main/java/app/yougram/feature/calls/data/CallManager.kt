package app.yougram.feature.calls.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
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

/**
 * Звонки 1:1 поверх TDLib: исходящие, входящие, принятие, сброс. Звук и ключ — в [engine].
 *
 * Жизненный цикл одного звонка: RINGING → CONNECTING (ключи, поднятие медиа) → ACTIVE → ENDED.
 * Звонок не может «зависнуть»: если медиа не поднялось или пропало надолго, он сбрасывается сам.
 */
class CallManager(
    private val telegram: TelegramClient,
    private val scope: CoroutineScope,
    private val chats: ChatRepository,
    private val appContext: Context,
    private val audio: CallAudio,
) {
    private val client get() = telegram.client

    @Volatile
    var engine: CallEngine = NoCallEngine

    private val _call = MutableStateFlow<ActiveCall?>(null)
    val call: StateFlow<ActiveCall?> = _call.asStateFlow()

    /** Обновления TDLib обрабатываются строго по очереди: иначе start/stop движка и смена состояния пересекаются. */
    private val mutex = Mutex()

    /** Завершённые звонки: запоздавшие updateCall по ним не должны заново открывать экран. */
    private val finished = LinkedHashSet<Int>()

    @Volatile private var dialing = false

    /** Звонок, для которого уже запущен медиа-движок. */
    @Volatile private var engineCallId = Int.MIN_VALUE

    /** Поднято ли сейчас медиа-соединение; нужно, чтобы отложенный сброс не убил восстановившийся звонок. */
    @Volatile private var linkUp = false
    private var linkJob: Job? = null

    /** Исходящие сигнальные пакеты уходят строго по порядку через одну корутину. */
    private val outbox = Channel<Pair<Int, ByteArray>>(Channel.UNLIMITED)

    private val fallbackProtocol = CallProtocol(
        udpP2p = true,
        udpReflector = true,
        minLayer = 65,
        maxLayer = 92,
        libraryVersions = arrayOf("2.4.4", "5.0.0", "7.0.0", "9.0.0", "11.0.0"),
    )

    /** Версии библиотеки должны совпадать с тем, что реально умеет движок, иначе собеседник выберет несовместимую. */
    private val protocol: CallProtocol get() = engine.protocol ?: fallbackProtocol

    val route: StateFlow<AudioRoute> = audio.route
    val bluetoothAvailable: StateFlow<Boolean> = audio.bluetoothAvailable

    fun cycleRoute() = audio.cycle()

    fun start() {
        scope.launch {
            client.callUpdates.collect { onCallUpdate(it.call) }
        }
        scope.launch {
            client.newCallSignalingDataUpdates.collect { update ->
                val id = _call.value?.id
                if (id != null && update.callId == id && !isFinished(id)) engine.onSignalingData(update.data)
            }
        }
        scope.launch {
            for ((id, data) in outbox) {
                val sent = client.sendCallSignalingData(callId = id, data = data)
                if (sent is TdlResult.Failure) CallLog.e(TAG, "sendCallSignalingData failed code=${sent.code} message=${sent.message}")
            }
        }
    }

    /** Исходящий звонок пользователю [userId]. Видео без поддержки движка превращается в аудиозвонок. */
    fun startCall(userId: Long, video: Boolean) {
        if (_call.value != null || dialing) return
        if (!hasMic()) {
            showEnded(userId, false, "Нет доступа к микрофону")
            return
        }
        val isVideo = video && engine.supportsVideo
        CallLog.d(TAG, "startCall user=$userId video=$isVideo (asked=$video)")
        dialing = true
        scope.launch {
            try {
                val result = client.createCall(userId = userId, protocol = protocol, isVideo = isVideo)
                if (result is TdlResult.Failure) {
                    CallLog.e(TAG, "createCall failed code=${result.code} message=${result.message}")
                    val text = if (result.code == 403) "Пользователь не принимает звонки" else result.message
                    showEnded(userId, isVideo, text)
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
        if (!hasMic()) return
        CallLog.d(TAG, "accept id=${c.id}")
        scope.launch {
            val result = client.acceptCall(callId = c.id, protocol = protocol)
            if (result is TdlResult.Failure) {
                CallLog.e(TAG, "acceptCall failed code=${result.code} message=${result.message}")
                finishLocal(c, "Не удалось принять звонок", discard = false)
            }
        }
    }

    /** Сбросить или отклонить звонок. */
    fun hangUp(disconnected: Boolean = false) {
        val c = _call.value ?: return
        if (c.phase == CallPhase.ENDED) return
        CallLog.d(TAG, "hangUp id=${c.id} disconnected=$disconnected phase=${c.phase}")
        finishLocal(c, "Звонок завершён", discard = true, disconnected = disconnected)
    }

    fun setMuted(muted: Boolean) {
        engine.setMuted(muted)
        _call.update { it?.copy(muted = muted) }
    }

    // ---------------------------------------------------------------- TDLib

    private suspend fun onCallUpdate(call: Call) {
        mutex.withLock {
            if (isFinished(call.id)) return
            val state = call.state
            CallLog.d(TAG, "update id=${call.id} out=${call.isOutgoing} video=${call.isVideo} state=${state::class.simpleName}")
            // Уже завершённый звонок на экране не мешает новому.
            val current = _call.value?.takeUnless { it.phase == CallPhase.ENDED && it.id != call.id }

            if (current != null && current.id != call.id) {
                // Второй входящий, пока идёт другой звонок: занято.
                if (!call.isOutgoing && state is CallStatePending) {
                    markFinished(call.id)
                    discard(call.id, call.isVideo, 0, disconnected = false)
                }
                return
            }
            // Запоздалое «завершён» по звонку, которого на экране нет, новый звонок не создаёт.
            if (current == null && (state is CallStateDiscarded || state is CallStateError || state is CallStateHangingUp)) {
                markFinished(call.id)
                return
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
                    engineAvailable = engine.isAvailable,
                )
            }

            when (state) {
                is CallStatePending -> publish(
                    base.copy(
                        phase = CallPhase.RINGING,
                        message = if (call.isOutgoing) (if (state.isReceived) "Звонок…" else "Соединение…") else null,
                    ),
                )
                is CallStateExchangingKeys -> publish(base.copy(phase = CallPhase.CONNECTING, message = "Обмен ключами…"))
                is CallStateReady -> onReady(call, state, base)
                is CallStateHangingUp -> {
                    markFinished(base.id)
                    stopMedia()
                    _call.value = base.copy(phase = CallPhase.ENDED, message = "Завершение…")
                    clearLater(base.id, 5000)
                }
                is CallStateDiscarded -> finish(base, "Звонок завершён")
                is CallStateError -> {
                    CallLog.e(TAG, "call error code=${state.error.code} message=${state.error.message}")
                    finish(base, state.error.message.ifEmpty { "Ошибка звонка" })
                }
                else -> Unit
            }

            // Служба стартует после записи состояния, иначе она сама себя остановит.
            if (isNew && _call.value?.id == call.id) CallService.start(appContext)
        }
    }

    private fun onReady(call: Call, state: CallStateReady, base: ActiveCall) {
        val first = engineCallId != call.id
        val available = engine.isAvailable
        CallLog.d(
            TAG,
            "ready first=$first available=$available allowP2p=${state.allowP2p} " +
                "versions=${state.protocol.libraryVersions.toList()} servers=${state.servers.size} " +
                "emojis=${state.emojis.orEmpty().toList()}",
        )
        if (first && available && !hasMic()) {
            // Без микрофона собеседник услышал бы тишину: честно завершаем.
            finishLocal(base, "Нет доступа к микрофону", discard = true)
            return
        }
        val linked = base.phase == CallPhase.ACTIVE || !available
        publish(
            base.copy(
                phase = if (linked) CallPhase.ACTIVE else CallPhase.CONNECTING,
                emojis = state.emojis.orEmpty().toList(),
                startedAtMillis = if (linked) base.startedAtMillis ?: System.currentTimeMillis() else null,
                engineAvailable = available,
                message = when {
                    !available -> "Медиа-движок не подключён: звука и видео нет"
                    linked -> base.message
                    else -> "Соединение…"
                },
            ),
        )
        if (!first) return

        engineCallId = call.id
        linkUp = false
        audio.start(call.isVideo)
        engine.start(call.userId, state, call.isOutgoing, call.isVideo, { data -> outbox.trySend(call.id to data) }) { link ->
            onLink(call.id, link)
        }
        if (base.muted) engine.setMuted(true)
        watchdog(call.id)
    }

    // ---------------------------------------------------------------- Медиа-соединение

    private fun onLink(id: Int, link: EngineLink) {
        CallLog.d(TAG, "engine link id=$id -> $link")
        scope.launch {
            val refreshAudio = mutex.withLock {
                if (isFinished(id)) return@withLock false
                when (link) {
                    EngineLink.CONNECTED -> {
                        linkUp = true
                        linkJob?.cancel()
                        _call.update { c ->
                            if (c == null || c.id != id || c.phase == CallPhase.ENDED) c
                            else if (c.phase == CallPhase.ACTIVE) c.copy(message = null)
                            else c.copy(phase = CallPhase.ACTIVE, startedAtMillis = System.currentTimeMillis(), message = null)
                        }
                        true
                    }
                    EngineLink.CONNECTING -> {
                        _call.update { c ->
                            if (c != null && c.id == id && c.phase == CallPhase.ACTIVE) c.copy(message = "Переподключение…") else c
                        }
                        false
                    }
                    EngineLink.FAILED -> {
                        linkUp = false
                        _call.update { c ->
                            if (c != null && c.id == id && c.phase != CallPhase.ENDED) c.copy(message = "Нет соединения с собеседником") else c
                        }
                        scheduleLinkDrop(id)
                        false
                    }
                }
            }
            if (refreshAudio) {
                // Нативные потоки открылись и могли сбросить маршрут: применяем его ещё раз (вне мьютекса).
                delay(600)
                if (!isFinished(id)) audio.refresh()
            }
        }
    }

    /** Если связь не вернулась за отведённое время, звонок сбрасывается, а не висит «подключённым». */
    private fun scheduleLinkDrop(id: Int) {
        linkJob?.cancel()
        linkJob = scope.launch {
            val wasActive = _call.value?.takeIf { it.id == id }?.phase == CallPhase.ACTIVE
            delay(if (wasActive) RECONNECT_TIMEOUT_MS else CONNECT_FAIL_GRACE_MS)
            val c = _call.value
            if (c != null && c.id == id && !linkUp && c.phase != CallPhase.ENDED) {
                CallLog.e(TAG, "link did not recover, hanging up")
                finishLocal(c, "Нет соединения с собеседником", discard = true, disconnected = true)
            }
        }
    }

    /** Если медиа не поднялось за 30 с, звонок сбрасывается. */
    private fun watchdog(id: Int) {
        scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            val c = _call.value
            if (c != null && c.id == id && c.phase == CallPhase.CONNECTING) {
                CallLog.e(TAG, "watchdog: media link not up after ${CONNECT_TIMEOUT_MS / 1000}s")
                finishLocal(c, "Не удалось соединиться", discard = true, disconnected = true)
            }
        }
    }

    // ---------------------------------------------------------------- Завершение

    /** Завершение по инициативе приложения: останавливаем медиа, показываем итог, сообщаем TDLib. */
    private fun finishLocal(c: ActiveCall, text: String, discard: Boolean, disconnected: Boolean = false) {
        markFinished(c.id)
        linkJob?.cancel()
        val duration = c.startedAtMillis?.let { ((System.currentTimeMillis() - it) / 1000).toInt() } ?: 0
        stopMedia()
        _call.update { s -> if (s != null && s.id == c.id) s.copy(phase = CallPhase.ENDED, message = text) else s }
        if (discard) discard(c.id, c.isVideo, duration, disconnected)
        clearLater(c.id, if (disconnected) 1800 else 800)
    }

    /** Завершение по сообщению TDLib: звонок уже закрыт на сервере. */
    private fun finish(base: ActiveCall, text: String) {
        CallLog.d(TAG, "finish id=${base.id} text=$text")
        markFinished(base.id)
        linkJob?.cancel()
        stopMedia()
        _call.value = base.copy(phase = CallPhase.ENDED, message = text)
        clearLater(base.id, 1200)
    }

    private fun stopMedia() {
        engineCallId = Int.MIN_VALUE
        linkUp = false
        engine.stop()
        audio.stop()
    }

    private fun discard(id: Int, video: Boolean, duration: Int, disconnected: Boolean) {
        scope.launch {
            client.discardCall(
                callId = id,
                isDisconnected = disconnected,
                duration = duration,
                isVideo = video,
                connectionId = 0L,
                inviteLink = "",
            )
        }
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

    // ---------------------------------------------------------------- Утилиты

    /** Записывает состояние, если звонок за это время не успели завершить. */
    private fun publish(value: ActiveCall) {
        _call.update { if (isFinished(value.id)) it else value }
    }

    /** Убирает экран звонка через [millis], только если на нём всё ещё этот же звонок. */
    private fun clearLater(id: Int, millis: Long) {
        scope.launch {
            delay(millis)
            _call.update { if (it?.id == id) null else it }
        }
    }

    private fun hasMic() =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun markFinished(id: Int) {
        synchronized(finished) {
            finished.add(id)
            if (finished.size > 64) finished.remove(finished.first())
        }
    }

    private fun isFinished(id: Int): Boolean = synchronized(finished) { id in finished }

    private companion object {
        const val TAG = "CallManager"
        const val CONNECT_TIMEOUT_MS = 30_000L
        const val CONNECT_FAIL_GRACE_MS = 8_000L
        const val RECONNECT_TIMEOUT_MS = 25_000L
    }
}
