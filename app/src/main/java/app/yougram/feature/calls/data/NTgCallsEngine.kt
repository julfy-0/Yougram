package app.yougram.feature.calls.data

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

    private inline fun <T> step(name: String, block: () -> T): T {
        CallLog.d(TAG, "-> $name")
        return try {
            block().also { CallLog.d(TAG, "<- $name ok") }
        } catch (t: Throwable) {
            CallLog.e(TAG, "<- $name FAILED", t)
            throw t
        }
    }

    override fun start(
        userId: Long,
        state: CallStateReady,
        isOutgoing: Boolean,
        isVideo: Boolean,
        sendSignaling: (ByteArray) -> Unit,
        onLink: (EngineLink) -> Unit,
    ) {
        val gen = generation.incrementAndGet()
        CallLog.d(TAG, "start user=$userId outgoing=$isOutgoing video=$isVideo gen=$gen")
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
            instance.onSignalingData { _, data ->
                CallLog.d(TAG, "signaling out ${data.size}B")
                sendSignaling(data)
            }
            instance.onConnectionChange { _, info ->
                CallLog.d(TAG, "connection: $info")
                if (gen == generation.get()) onLink(linkOf(info))
            }

            step("createP2pCall") { instance.createP2pCall(userId) }
            // Ключ уже согласован TDLib — обмен ключами внутри NTgCalls пропускаем.
            step("skipExchange") { instance.skipExchange(userId, state.encryptionKey, isOutgoing) }

            // Звук: микрофон на захват, динамик на воспроизведение (устройства по умолчанию).
            // На Android (Oboe) input обязан быть JSON с is_microphone, иначе "Invalid device metadata".
            step("setStreamSources CAPTURE") {
                instance.setStreamSources(
                    userId,
                    StreamMode.CAPTURE,
                    MediaDescription(AudioDescription(MediaSource.DEVICE, 48000, 1, MIC_METADATA, false), null, null, null),
                )
            }
            step("setStreamSources PLAYBACK") {
                instance.setStreamSources(
                    userId,
                    StreamMode.PLAYBACK,
                    MediaDescription(null, AudioDescription(MediaSource.DEVICE, 48000, 1, SPEAKER_METADATA, false), null, null),
                )
            }

            step("connectP2p") {
                instance.connectP2p(
                    userId,
                    state.servers.map(::toRtcServer),
                    state.protocol.libraryVersions.toList(),
                    state.allowP2p,
                    state.customParameters.ifEmpty { null },
                )
            }
            if (muted) runCatching { instance.mute(userId) }

            synchronized(lock) {
                if (gen == generation.get()) {
                    active = true
                    CallLog.d(TAG, "active, flushing ${pending.size} queued signaling packets")
                    while (pending.isNotEmpty()) {
                        val data = pending.removeFirst()
                        runCatching { instance.sendSignalingData(userId, data) }
                            .onFailure { CallLog.e(TAG, "signaling", it) }
                    }
                }
            }
        } catch (e: Throwable) {
            CallLog.e(TAG, "start failed", e)
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
        }
        CallLog.d(TAG, "signaling in ${data.size}B ${if (instance != null) "delivered" else "queued"}")
        if (instance == null) return
        runCatching { instance.sendSignalingData(userId, data) }.onFailure { CallLog.e(TAG, "signaling", it) }
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
        CallLog.d(TAG, "stop")
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
        val raw = buildString {
            append(info.toString())
            info.javaClass.fields.forEach { f ->
                runCatching { append(' ').append(f.name).append('=').append(f.get(info)) }
            }
            info.javaClass.methods
                .filter { it.parameterCount == 0 && it.name.startsWith("get") && it.name != "getClass" }
                .forEach { m -> runCatching { append(' ').append(m.invoke(info)) } }
        }.uppercase()
        CallLog.d(TAG, "link raw: $raw")
        return when {
            "FAIL" in raw || "TIMEOUT" in raw || "CLOSED" in raw -> EngineLink.FAILED
            "DISCONNECTED" in raw -> EngineLink.FAILED
            Regex("\\bCONNECTED\\b").containsMatchIn(raw) -> EngineLink.CONNECTED
            else -> EngineLink.CONNECTING
        }
    }

    override fun reopenPlayback() {
        val inst = ntg ?: return
        val uid = userId
        worker.execute {
            if (ntg !== inst) return@execute
            runCatching {
                inst.setStreamSources(
                    uid,
                    StreamMode.PLAYBACK,
                    MediaDescription(null, AudioDescription(MediaSource.DEVICE, 48000, 1, SPEAKER_METADATA, false), null, null),
                )
            }.onFailure { CallLog.e(TAG, "reopenPlayback failed", it) }
            CallLog.d(TAG, "reopenPlayback done")
        }
    }

    override fun debugStats(tag: String) {
        val inst = ntg ?: return
        val uid = userId
        worker.execute {
            if (ntg !== inst) return@execute
            CallLog.d(
                TAG,
                "stats $tag " + listOf(
                    probe(inst, "timeCap", "time", uid, StreamMode.CAPTURE),
                    probe(inst, "timePlay", "time", uid, StreamMode.PLAYBACK),
                    probe(inst, "state", "getState", uid),
                    probe(inst, "connMode", "getConnectionMode", uid),
                    probe(inst, "cpu", "cpuUsage"),
                ).joinToString(" "),
            )
        }
    }

    private fun probe(inst: NTgCalls, label: String, name: String, vararg args: Any?): String =
        try {
            val m = inst.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == args.size }
            if (m == null) "$label=n/a" else "$label=" + describe(m.invoke(inst, *args))
        } catch (t: Throwable) {
            "$label=ERR(${t.cause?.message ?: t.message})"
        }

    private fun describe(v: Any?): String {
        if (v == null) return "null"
        if (v is Number || v is Boolean || v is String || v is Enum<*>) return v.toString()
        return buildString {
            append(v.javaClass.simpleName)
            v.javaClass.fields.forEach { f -> runCatching { append(' ').append(f.name).append('=').append(f.get(v)) } }
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
        const val MIC_METADATA = """{"is_microphone":true}"""
        const val SPEAKER_METADATA = """{"is_microphone":false}"""
    }
}