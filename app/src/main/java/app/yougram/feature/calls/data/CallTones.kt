package app.yougram.feature.calls.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import app.yougram.R

/** Чем закончился звонок; от этого зависит финальный звуковой сигнал. */
enum class CallEnd { DONE, BUSY, FAILED }

/** Какой циклический сигнал сейчас должен играть. */
enum class CallLoop { NONE, CONNECTING, RINGBACK }

/**
 * Звуки звонка из оригинального Telegram (VoIPService): «соединение», гудки вызова,
 * «занято», «ошибка», «завершён». Играют через [SoundPool] на канале голосовой связи.
 *
 * Все вызовы приходят с главного потока; загрузка звуков асинхронная, поэтому сигнал,
 * запрошенный до окончания загрузки, запускается сразу после неё.
 */
class CallTones(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val connecting = pool.load(context, R.raw.voip_connecting, 1)
    private val ringback = pool.load(context, R.raw.voip_ringback, 1)
    private val busy = pool.load(context, R.raw.voip_busy, 1)
    private val failed = pool.load(context, R.raw.voip_failed, 1)
    private val end = pool.load(context, R.raw.voip_end, 1)

    private val loaded = HashSet<Int>()
    private var streamId = 0
    private var loop = CallLoop.NONE

    /** Звук, ожидающий окончания загрузки: (id звука, зациклить ли). */
    private var waiting: Pair<Int, Boolean>? = null

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            synchronized(this) {
                if (status != 0) return@synchronized
                loaded += sampleId
                val w = waiting
                if (w != null && w.first == sampleId) {
                    waiting = null
                    play(sampleId, w.second)
                }
            }
        }
    }

    /** Включает циклический сигнал [wanted]; повторный вызов с тем же значением ничего не меняет. */
    @Synchronized
    fun setLoop(wanted: CallLoop) {
        if (wanted == loop) return
        loop = wanted
        stopStream()
        when (wanted) {
            CallLoop.NONE -> waiting = null
            CallLoop.CONNECTING -> play(connecting, true)
            CallLoop.RINGBACK -> play(ringback, true)
        }
    }

    /** Финальный одиночный сигнал; циклический при этом останавливается. */
    @Synchronized
    fun playEnd(kind: CallEnd) {
        loop = CallLoop.NONE
        stopStream()
        play(
            when (kind) {
                CallEnd.DONE -> end
                CallEnd.BUSY -> busy
                CallEnd.FAILED -> failed
            },
            false,
        )
    }

    @Synchronized
    fun stop() {
        loop = CallLoop.NONE
        waiting = null
        stopStream()
    }

    private fun play(sampleId: Int, looped: Boolean) {
        if (sampleId !in loaded) {
            waiting = sampleId to looped
            return
        }
        streamId = pool.play(sampleId, 1f, 1f, 0, if (looped) -1 else 0, 1f)
    }

    private fun stopStream() {
        if (streamId != 0) pool.stop(streamId)
        streamId = 0
    }
}
