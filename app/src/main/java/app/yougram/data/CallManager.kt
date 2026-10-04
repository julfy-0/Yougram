package app.yougram.data

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
) {
    private val client get() = telegram.client

    /** Подставьте реализацию на tgcalls, чтобы появились звук и видео. */
    @Volatile
    var engine: CallEngine = NoCallEngine

    private val _call = MutableStateFlow<ActiveCall?>(null)
    val call: StateFlow<ActiveCall?> = _call.asStateFlow()

    private val protocol = CallProtocol(
        udpP2p = true,
        udpReflector = true,
        minLayer = 65,
        maxLayer = 92,
        libraryVersions = arrayOf("2.4.4", "5.0.0", "7.0.0", "9.0.0", "11.0.0"),
    )

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
        if (_call.value != null) return
        scope.launch {
            val result = client.createCall(userId = userId, protocol = protocol, isVideo = video)
            if (result is TdlResult.Failure) {
                val text = if (result.code == 403) "Пользователь не принимает звонки" else result.message
                showEnded(userId, video, text)
            }
            // Дальше состояние придёт в onCallUpdate.
        }
    }

    fun accept() {
        val c = _call.value ?: return
        if (c.isOutgoing || c.phase != CallPhase.RINGING) return
        scope.launch { client.acceptCall(callId = c.id, protocol = protocol) }
    }

    /** Сбросить или отклонить звонок. */
    fun hangUp() {
        val c = _call.value ?: return
        val duration = c.startedAtMillis?.let { ((System.currentTimeMillis() - it) / 1000).toInt() } ?: 0
        engine.stop()
        _call.update { it?.copy(phase = CallPhase.ENDED, message = "Звонок завершён") }
        scope.launch {
            client.discardCall(
                callId = c.id,
                isDisconnected = false,
                duration = duration,
                isVideo = c.isVideo,
                connectionId = 0L,
                inviteLink = "",
            )
            delay(800)
            _call.update { null }
        }
    }

    fun setMuted(muted: Boolean) {
        engine.setMuted(muted)
        _call.update { it?.copy(muted = muted) }
    }

    private suspend fun onCallUpdate(call: Call) {
        val current = _call.value
        if (current != null && current.id != call.id) {
            // Второй входящий, пока идёт другой звонок: занято.
            if (!call.isOutgoing && call.state is CallStatePending) {
                client.discardCall(callId = call.id, isDisconnected = false, duration = 0, isVideo = call.isVideo, connectionId = 0L, inviteLink = "")
            }
            return
        }
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
        when (val state = call.state) {
            is CallStatePending -> _call.value = base.copy(
                phase = CallPhase.RINGING,
                message = if (call.isOutgoing) (if (state.isReceived) "Звонок…" else "Соединение…") else null,
            )
            is CallStateExchangingKeys -> _call.value = base.copy(phase = CallPhase.CONNECTING, message = "Обмен ключами…")
            is CallStateReady -> {
                val first = current?.phase != CallPhase.ACTIVE
                _call.value = base.copy(
                    phase = CallPhase.ACTIVE,
                    emojis = state.emojis.orEmpty().toList(),
                    startedAtMillis = base.startedAtMillis ?: System.currentTimeMillis(),
                    engineAvailable = engine.isAvailable,
                    message = if (engine.isAvailable) null else "Медиа-движок не подключён: звука и видео нет",
                )
                if (first) {
                    engine.start(call.userId, state, call.isOutgoing, call.isVideo) { data ->
                        scope.launch { client.sendCallSignalingData(callId = call.id, data = data) }
                    }
                }
            }
            is CallStateHangingUp -> _call.value = base.copy(phase = CallPhase.ENDED, message = "Завершение…")
            is CallStateDiscarded -> finish(base, "Звонок завершён")
            is CallStateError -> finish(base, state.error.message.ifEmpty { "Ошибка звонка" })
            else -> Unit
        }
    }

    private suspend fun finish(base: ActiveCall, text: String) {
        engine.stop()
        _call.value = base.copy(phase = CallPhase.ENDED, message = text)
        delay(1200)
        if (_call.value?.id == base.id) _call.value = null
    }

    private fun showEnded(userId: Long, video: Boolean, text: String) {
        scope.launch {
            val info = runCatching { chats.userCardInfo(userId) }.getOrNull()
            _call.value = ActiveCall(
                id = -1, userId = userId, title = info?.first.orEmpty(), avatarFileId = info?.second,
                isOutgoing = true, isVideo = video, phase = CallPhase.ENDED, message = text,
            )
            delay(1800)
            if (_call.value?.id == -1) _call.value = null
        }
    }
}