package app.yougram.feature.chat.ui

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/** Запись голосового в ogg/opus — формат, который Telegram принимает как голосовое сообщение. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        if (recorder != null) return true
        val target = File(context.cacheDir, "voice_${System.currentTimeMillis()}.ogg")
        val r = MediaRecorder(context)
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            r.setAudioSamplingRate(48000)
            r.setAudioEncodingBitRate(32000)
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = target
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            runCatching { r.release() }
            target.delete()
            false
        }
    }

    /** Останавливает запись; возвращает путь и длительность в секундах или null, если запись слишком короткая. */
    fun stop(): Pair<String, Int>? {
        val r = recorder ?: return null
        val f = file
        recorder = null
        file = null
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (!ok || f == null || seconds < 1) {
            f?.delete()
            return null
        }
        return f.absolutePath to seconds
    }

    fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        runCatching { r.release() }
        file?.delete()
        file = null
    }
}
