package app.yougram.feature.calls.data

import dev.g000sha256.tdl.dto.CallProtocol
import dev.g000sha256.tdl.dto.CallStateReady

/** Состояние медиа-соединения движка с собеседником. */
enum class EngineLink { CONNECTING, CONNECTED, FAILED }

/**
 * Медиа-транспорт звонка. TDLib отвечает только за сигнализацию и ключи: когда звонок переходит
 * в [CallStateReady], в нём приходят серверы, ключ шифрования и параметры протокола,
 * а звук гонит нативная библиотека.
 *
 * Контракт:
 *  - [start] не блокирует вызывающий поток; результат приходит через `onLink`;
 *  - [onSignalingData] можно вызывать в любой момент, в том числе до [start]: пакеты не теряются;
 *  - [stop] идемпотентен и освобождает микрофон и нативные потоки.
 */
interface CallEngine {
    /** true, если движок реально передаёт медиа. */
    val isAvailable: Boolean

    /** Умеет ли движок видео. Если нет, видеозвонок начинается как аудиозвонок. */
    val supportsVideo: Boolean get() = false

    /** Протокол, который поддерживает движок; null — использовать значения по умолчанию. */
    val protocol: CallProtocol? get() = null

    fun start(
        userId: Long,
        state: CallStateReady,
        isOutgoing: Boolean,
        isVideo: Boolean,
        sendSignaling: (ByteArray) -> Unit,
        onLink: (EngineLink) -> Unit,
    )

    fun onSignalingData(data: ByteArray)
    fun setMuted(muted: Boolean)
    fun stop()
}

object NoCallEngine : CallEngine {
    override val isAvailable = false
    override fun start(
        userId: Long,
        state: CallStateReady,
        isOutgoing: Boolean,
        isVideo: Boolean,
        sendSignaling: (ByteArray) -> Unit,
        onLink: (EngineLink) -> Unit,
    ) = Unit

    override fun onSignalingData(data: ByteArray) = Unit
    override fun setMuted(muted: Boolean) = Unit
    override fun stop() = Unit
}
