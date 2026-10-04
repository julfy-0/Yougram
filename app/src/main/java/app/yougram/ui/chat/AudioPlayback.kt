package app.yougram.ui.chat

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Один общий плеер для голосовых: одновременно звучит только одно сообщение. */
object AudioPlayback {
    private var player: MediaPlayer? = null

    private val _current = MutableStateFlow<Int?>(null)

    /** id файла, который сейчас загружен в плеер (играет или на паузе). */
    val current: StateFlow<Int?> = _current.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    fun toggle(fileId: Int, path: String) {
        val active = player
        if (_current.value == fileId && active != null) {
            if (active.isPlaying) {
                active.pause()
                _playing.value = false
            } else {
                active.start()
                _playing.value = true
            }
            return
        }
        stop()
        val p = MediaPlayer()
        try {
            p.setDataSource(path)
            p.setOnCompletionListener { stop() }
            p.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            p.prepare()
            p.start()
        } catch (e: Exception) {
            runCatching { p.release() }
            return
        }
        player = p
        _current.value = fileId
        _playing.value = true
    }

    /** Обновляет прогресс; вызывается из UI, пока сообщение играет. */
    fun tick() {
        val p = player ?: return
        val total = runCatching { p.duration }.getOrDefault(0)
        if (total > 0) _progress.value = (p.currentPosition.toFloat() / total).coerceIn(0f, 1f)
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
        _current.value = null
        _playing.value = false
        _progress.value = 0f
    }
}
