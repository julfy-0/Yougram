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

/**
 * Медиа-движок 1:1 звонков на NTgCalls (io.github.pytgcalls:ntgcalls).
 * Сигнализацию и ключ шифрования даёт TDLib, здесь только звук.
 */
class NTgCallsEngine : CallEngine {
    private var ntg: NTgCalls? = null
    private var userId: Long = 0L
    private var sendSignaling: ((ByteArray) -> Unit)? = null
    @Volatile private var active = false

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
    ) {
        stop()
        this.userId = userId
        this.sendSignaling = sendSignaling
        val instance = NTgCalls()
        ntg = instance
        try {
            instance.onSignalingData { _, data -> this.sendSignaling?.invoke(data) }
            instance.onConnectionChange { _, info -> Log.d(TAG, "connection: $info") }

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
            active = true
        } catch (e: Throwable) {
            Log.e(TAG, "start failed", e)
            stop()
        }
    }

    override fun onSignalingData(data: ByteArray) {
        if (!active) return
        runCatching { ntg?.sendSignalingData(userId, data) }.onFailure { Log.e(TAG, "signaling", it) }
    }

    override fun setMuted(muted: Boolean) {
        runCatching { if (muted) ntg?.mute(userId) else ntg?.unmute(userId) }
    }

    override fun setVideoEnabled(enabled: Boolean) = Unit

    override fun stop() {
        active = false
        val instance = ntg ?: return
        ntg = null
        runCatching { instance.stop(userId) }
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
    }
}
