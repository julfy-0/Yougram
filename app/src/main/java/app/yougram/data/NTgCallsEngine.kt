package app.yougram.data

import android.util.Log
import dev.g000sha256.tdl.dto.CallProtocol
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Медиа-движок 1:1 звонков на NTgCalls (io.github.pytgcalls:ntgcalls).
 * Сигнализацию и ключ шифрования даёт TDLib, здесь только звук.
 *
 * Все нативные вызовы идут через один поток (start/stop не блокируют UI и не пересекаются),
 * сигнальные пакеты, пришедшие до готовности соединения, копятся и отправляются по порядку.
 */
class NTgCallsEngine : CallEngine {
    private val worker = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ntgcalls-worker").apply { isDaemon = true }
    }
    private val generation = AtomicInteger(0)
    private val lock = Any()
    private val pending = ArrayDeque<ByteArray>()

    @Volatile private var ntg: NTgCalls? = null
    @Volatile private var userId: Long = 0L
    @Volatile private var active = false
    @Volatile private var muted = false

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
        val gen = generation.incrementAndGet()
        worker.execute {
            if (gen != generation.get()) return@execute
            release()
            connect(gen, userId, state, isOutgoing, sendSignaling, onLink)
        }
    }

    private fun connect(
        gen: Int,
        userId: Long,
        state: CallStateReady,
        isOutgoing: Boolean,
        sendSignaling: (ByteArray) -> Unit,
        onLink: (EngineLink) -> Unit,
    ) {
        val instance = NTgCalls()
        this.userId = userId
        ntg = instance
        try {
            instance.onSignalingData { _, data -> sendSignaling(data) }
            instance.onConnectionChange { _, info ->
                Log.d(TAG, "connection: $info")
                if (gen == generation.get()) onLink(linkOf(info))
            }

            instance.createP2pCall(userId)
            // Ключ уже согласован TDLib — обмен ключами внутри NTgCalls пропускаем.
            instance.skipExchange(userId, state.encryptionKey, isOutgoing)

            // Звук: микрофон на захват, динамик на воспроизведение (устройства по умолчанию).
            instance.setStreamSources(
                userId,
                StreamMode.CAPTURE,
                MediaDescription(AudioDescription(MediaSource.DEVICE, 48000, 1, "", false), null, null, null),
            )
            instance.setStreamSources(
                userId,
                StreamMode.PLAYBACK,
                MediaDescription(null, AudioDescription(MediaSource.DEVICE, 48000, 1, "", false), null, null),
            )

            instance.connectP2p(
                userId,
                state.servers.map(::toRtcServer),
                state.protocol.libraryVersions.toList(),
                state.allowP2p,
                state.customParameters.ifEmpty { null },
            )
            if (muted) runCatching { instance.mute(userId) }

            synchronized(lock) {
                if (gen == generation.get()) {
                    active = true
                    while (pending.isNotEmpty()) {
                        val data = pending.removeFirst()
                        runCatching { instance.sendSignalingData(userId, data) }
                            .onFailure { Log.e(TAG, "signaling", it) }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "start failed", e)
            release()
            if (gen == generation.get()) onLink(EngineLink.FAILED)
        }
    }

    override fun onSignalingData(data: ByteArray) {
        val instance = synchronized(lock) {
            if (active) {
                ntg
            } else {
                if (pending.size < MAX_PENDING) pending.addLast(data)
                null
            }
        } ?: return
        runCatching { instance.sendSignalingData(userId, data) }.onFailure { Log.e(TAG, "signaling", it) }
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        val instance = ntg ?: return
        if (!active) return
        runCatching { if (muted) instance.mute(userId) else instance.unmute(userId) }
    }

    override fun setVideoEnabled(enabled: Boolean) = Unit

    override fun stop() {
        generation.incrementAndGet()
        synchronized(lock) {
            active = false
            pending.clear()
        }
        muted = false
        worker.execute { release() }
    }

    /** Останавливает звонок и освобождает нативный экземпляр (иначе микрофон и потоки остаются занятыми). */
    private fun release() {
        val instance = ntg ?: return
        ntg = null
        runCatching { instance.stop(userId) }
        runCatching { instance.javaClass.getMethod("free").invoke(instance) }
    }

    /** Достаёт состояние из NetworkInfo без привязки к точным именам полей библиотеки. */
    private fun linkOf(info: Any): EngineLink {
        val raw = runCatching { info.javaClass.getField("state").get(info).toString() }
            .getOrElse { info.toString() }
            .uppercase()
        return when {
            "FAIL" in raw || "TIMEOUT" in raw || "CLOSED" in raw || "DISCONNECT" in raw -> EngineLink.FAILED
            "CONNECTED" in raw -> EngineLink.CONNECTED
            else -> EngineLink.CONNECTING
        }
    }

    private fun toRtcServer(server: dev.g000sha256.tdl.dto.CallServer): RTCServer =
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
    }
}