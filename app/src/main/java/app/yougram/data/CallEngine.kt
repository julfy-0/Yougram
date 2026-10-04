package app.yougram.data

import dev.g000sha256.tdl.dto.CallProtocol
import dev.g000sha256.tdl.dto.CallStateReady

/**
 * Медиа-транспорт звонка (WebRTC / tgcalls). TDLib отвечает только за сигнализацию и ключи:
 * когда звонок переходит в [CallStateReady], в нём приходят серверы, ключ шифрования и
 * параметры протокола, а сам звук и видео должна гнать нативная библиотека tgcalls.
 *
 * Реализация подключается через [CallManager.engine]. Пока стоит [NoCallEngine], сигнализация
 * работает (звонок звонит, принимается, сбрасывается), но звука и картинки нет.
 */
interface CallEngine {
    /** true, если движок реально передаёт медиа. */
    val isAvailable: Boolean

    /** Протокол, который поддерживает движок; null — использовать значения по умолчанию. */
    val protocol: CallProtocol? get() = null

    fun start(userId: Long, state: CallStateReady, isOutgoing: Boolean, isVideo: Boolean, sendSignaling: (ByteArray) -> Unit)
    fun onSignalingData(data: ByteArray)
    fun setMuted(muted: Boolean)
    fun setVideoEnabled(enabled: Boolean)
    fun stop()
}

object NoCallEngine : CallEngine {
    override val isAvailable = false
    override fun start(userId: Long, state: CallStateReady, isOutgoing: Boolean, isVideo: Boolean, sendSignaling: (ByteArray) -> Unit) = Unit
    override fun onSignalingData(data: ByteArray) = Unit
    override fun setMuted(muted: Boolean) = Unit
    override fun setVideoEnabled(enabled: Boolean) = Unit
    override fun stop() = Unit
}
