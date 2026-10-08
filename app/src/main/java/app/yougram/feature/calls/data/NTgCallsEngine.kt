package app.yougram.feature.calls.data

import dev.g000sha256.tdl.dto.CallProtocol
import dev.g000sha256.tdl.dto.CallServer
import dev.g000sha256.tdl.dto.CallServerTypeTelegramReflector
import dev.g000sha256.tdl.dto.CallServerTypeWebrtc
import dev.g000sha256.tdl.dto.CallStateReady
import io.github.pytgcalls.NTgCalls
import io.github.pytgcalls.media.AudioDescription
import io.github.pytgcalls.media.MediaDescription
import io.github.pytgcalls.media.MediaSource
import io.github.pytgcalls.media.StreamMode
import io.github.pytgcalls.p2p.RTCServer
import java.util.concurrent.Executors

/**
 * Медиа-движок 1:1 звонков на NTgCalls (io.github.pytgcalls:ntgcalls). Сигнализацию и ключ даёт TDLib.
 *
 * Каждый звонок живёт в своей [Session] со своим экземпляром NTgCalls. Все нативные вызовы
 * start/stop идут через один поток по порядку, поэтому остановка старого звонка всегда
 * завершается раньше, чем стартует новый, а запоздавшие колбэки старой сессии игнорируются.
 */
class NTgCallsEngine : CallEngine {

    private class Session(val userId: Long) {
        val lock = Any()
        val pending = ArrayDeque<ByteArray>()

        @Volatile var ntg: NTgCalls? = null
        @Volatile var active = false

        /** Освобождает нативный экземпляр. Вызывается только с рабочего потока. */
        fun release() {
            val instance = ntg ?: return
            ntg = null
            synchronized(lock) { active = false; pending.clear() }
            runCatching { instance.stop(userId) }
            runCatching { instance.javaClass.getMethod("free").invoke(instance) }
        }
    }

    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ntgcalls-worker").apply { isDaemon = true }
    }

    @Volatile private var session: Session? = null
    @Volatile private var muted = false

    /** Сигнальные пакеты, пришедшие раньше, чем стартовал движок. */
    private val earlyLock = Any()
    private val early = ArrayDeque<ByteArray>()

    override val isAvailable: Boolean = true

    override val protocol: CallProtocol?
        get() = runCatching {
            val p = NTgCalls.getProtocol()
            CallProtocol(
                udpP2p = p.udp_p2p,
                udpReflector = p.udp_reflector,
                minLayer = p.min_layer,
                maxLayer = p.max_layer,
                libraryVersions = p.library_versions.toTypedArray(),
            )
        }.getOrNull()

    override fun start(
        userId: Long,
        state: CallStateReady,
        isOutgoing: Boolean,
        isVideo: Boolean,
        sendSignaling: (ByteArray) -> Unit,
        onLink: (EngineLink) -> Unit,
    ) {
        val s = Session(userId)
        synchronized(earlyLock) {
            synchronized(s.lock) { while (early.isNotEmpty()) s.pending.addLast(early.removeFirst()) }
        }
        val previous = session
        session = s
        CallLog.d(TAG, "start user=$userId outgoing=$isOutgoing video=$isVideo")
        worker.execute {
            previous?.release()
            if (session === s) connect(s, state, isOutgoing, sendSignaling, onLink) else s.release()
        }
    }

    private fun connect(
        s: Session,
        state: CallStateReady,
        isOutgoing: Boolean,
        sendSignaling: (ByteArray) -> Unit,
        onLink: (EngineLink) -> Unit,
    ) {
        val instance = NTgCalls()
        s.ntg = instance
        try {
            instance.onSignalingData { _, data -> if (session === s) sendSignaling(data) }
            instance.onConnectionChange { _, info ->
                val link = linkOf(info)
                CallLog.d(TAG, "connection: $link")
                if (session === s) onLink(link)
            }

            instance.createP2pCall(s.userId)
            // Ключ уже согласован TDLib: обмен ключами внутри NTgCalls пропускаем.
            instance.skipExchange(s.userId, state.encryptionKey, isOutgoing)

            // На Android (Oboe) метаданные устройства обязаны быть JSON с is_microphone.
            instance.setStreamSources(
                s.userId, StreamMode.CAPTURE,
                MediaDescription(AudioDescription(MediaSource.DEVICE, SAMPLE_RATE, 1, MIC_METADATA, false), null, null, null),
            )
            instance.setStreamSources(
                s.userId, StreamMode.PLAYBACK,
                MediaDescription(null, AudioDescription(MediaSource.DEVICE, SAMPLE_RATE, 1, SPEAKER_METADATA, false), null, null),
            )

            instance.connectP2p(
                s.userId,
                state.servers.map(::toRtcServer),
                state.protocol.libraryVersions.toList(),
                state.allowP2p,
                state.customParameters.ifEmpty { null },
            )
            if (muted) runCatching { instance.mute(s.userId) }

            // Пакеты, пришедшие за время подключения, уходят по порядку, и только потом движок «активен».
            synchronized(s.lock) {
                if (session !== s) return@synchronized
                while (s.pending.isNotEmpty()) {
                    val data = s.pending.removeFirst()
                    runCatching { instance.sendSignalingData(s.userId, data) }
                        .onFailure { CallLog.e(TAG, "signaling flush", it) }
                }
                s.active = true
            }
            if (session !== s) s.release()
            CallLog.d(TAG, "connected to native, waiting for link")
        } catch (e: Throwable) {
            CallLog.e(TAG, "start failed", e)
            s.release()
            if (session === s) onLink(EngineLink.FAILED)
        }
    }

    override fun onSignalingData(data: ByteArray) {
        val s = session
        if (s == null) {
            synchronized(earlyLock) { if (early.size < MAX_PENDING) early.addLast(data) }
            return
        }
        val instance = synchronized(s.lock) {
            if (s.active) {
                s.ntg
            } else {
                if (s.pending.size < MAX_PENDING) s.pending.addLast(data)
                null
            }
        } ?: return
        runCatching { instance.sendSignalingData(s.userId, data) }.onFailure { CallLog.e(TAG, "signaling", it) }
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        val s = session ?: return
        val instance = s.ntg ?: return
        if (!s.active) return
        runCatching { if (muted) instance.mute(s.userId) else instance.unmute(s.userId) }
    }

    override fun stop() {
        CallLog.d(TAG, "stop")
        val s = session
        session = null
        muted = false
        synchronized(earlyLock) { early.clear() }
        if (s != null) worker.execute { s.release() }
    }

    /** Состояние берём из поля `state` NetworkInfo; если у библиотеки другая форма, разбираем текстом. */
    private fun linkOf(info: Any): EngineLink {
        val raw = runCatching { info.javaClass.getField("state").get(info)?.toString() }.getOrNull()
            ?: buildString {
                append(info.toString())
                info.javaClass.fields.forEach { f -> runCatching { append(' ').append(f.get(info)) } }
                info.javaClass.methods
                    .filter { it.parameterCount == 0 && it.name.startsWith("get") && it.name != "getClass" }
                    .forEach { m -> runCatching { append(' ').append(m.invoke(info)) } }
            }
        val text = raw.uppercase()
        return when {
            "FAIL" in text || "TIMEOUT" in text || "CLOSED" in text || "DISCONNECTED" in text -> EngineLink.FAILED
            Regex("\\bCONNECTED\\b").containsMatchIn(text) -> EngineLink.CONNECTED
            else -> EngineLink.CONNECTING
        }
    }

    private fun toRtcServer(server: CallServer): RTCServer =
        when (val type = server.type) {
            is CallServerTypeTelegramReflector -> RTCServer(
                server.id, server.ipAddress, server.ipv6Address, server.port,
                null, null,
                false, false, type.isTcp, type.peerTag,
            )
            is CallServerTypeWebrtc -> RTCServer(
                server.id, server.ipAddress, server.ipv6Address, server.port,
                type.username, type.password,
                type.supportsTurn, type.supportsStun, false, null,
            )
            else -> error("Неизвестный тип сервера звонка")
        }

    private companion object {
        const val TAG = "NTgCallsEngine"
        const val MAX_PENDING = 256
        const val SAMPLE_RATE = 48000
        const val MIC_METADATA = """{"is_microphone":true}"""
        const val SPEAKER_METADATA = """{"is_microphone":false}"""
    }
}
