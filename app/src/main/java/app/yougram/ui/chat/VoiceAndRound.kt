package app.yougram.ui.chat

import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.yougram.data.FileState
import app.yougram.data.MediaItem
import app.yougram.ui.rememberFileBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

/** Голосовые и кружки меньше этого размера скачиваются сами, как в официальном клиенте. */
private const val AutoDownloadLimit = 8L shl 20
private const val WaveBars = 36

private fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** Раскладывает 5-битную волну TDLib в [bars] столбиков высотой 0..1. */
private fun decodeWaveform(data: ByteArray?, bars: Int): FloatArray {
    val total = if (data == null) 0 else data.size * 8 / 5
    if (data == null || total == 0) return FloatArray(bars) { 0.25f }
    val values = IntArray(total) { i ->
        var v = 0
        for (b in 0 until 5) {
            val bit = i * 5 + b
            if (((data[bit / 8].toInt() shr (bit % 8)) and 1) == 1) v = v or (1 shl b)
        }
        v
    }
    return FloatArray(bars) { k ->
        val from = k * total / bars
        val to = minOf(total, maxOf(from + 1, (k + 1) * total / bars))
        var max = 0
        for (j in from until to) max = maxOf(max, values[j])
        (max / 31f).coerceIn(0.12f, 1f)
    }
}

/** Голосовое сообщение: кнопка, волна с прогрессом, длительность. */
@Composable
fun VoiceNoteRow(media: MediaItem, viewModel: ChatViewModel, full: FileState) {
    val current by AudioPlayback.current.collectAsState()
    val playing by AudioPlayback.playing.collectAsState()
    val progress by AudioPlayback.progress.collectAsState()
    val isCurrent = current == media.fileId
    val active = isCurrent && playing

    LaunchedEffect(media.fileId) {
        if (media.size in 1..AutoDownloadLimit) viewModel.download(media.fileId, 8)
    }
    LaunchedEffect(active) {
        while (active) {
            AudioPlayback.tick()
            delay(100)
        }
    }

    val bars = remember(media.waveform) { decodeWaveform(media.waveform, WaveBars) }
    val played = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Row(
        Modifier.width(240.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable {
                    val path = full.path
                    when {
                        path != null -> AudioPlayback.toggle(media.fileId, path)
                        !full.active -> viewModel.download(media.fileId, 16)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val tint = MaterialTheme.colorScheme.onPrimary
            when {
                full.active -> CircularProgressIndicator(Modifier.size(28.dp), color = tint, strokeWidth = 2.5.dp)
                full.path == null -> Icon(Icons.Filled.Download, null, tint = tint)
                active -> Icon(Icons.Filled.Pause, null, tint = tint)
                else -> Icon(Icons.Filled.PlayArrow, null, tint = tint)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Canvas(Modifier.width(150.dp).height(28.dp)) {
                val step = size.width / WaveBars
                val barWidth = step * 0.6f
                val shown = if (isCurrent) progress else 0f
                bars.forEachIndexed { i, level ->
                    val h = size.height * level
                    drawRoundRect(
                        color = if ((i + 0.5f) / WaveBars <= shown) played else rest,
                        topLeft = Offset(i * step, (size.height - h) / 2f),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
            }
            Text(
                formatDuration(media.duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Видеосообщение-кружок: превью, по нажатию играет прямо в круге. */
@Composable
fun VideoNoteView(media: MediaItem, viewModel: ChatViewModel, full: FileState) {
    val preview by remember(media.previewFileId) {
        media.previewFileId?.let(viewModel::fileState) ?: flowOf(FileState())
    }.collectAsState(FileState())
    LaunchedEffect(media.previewFileId) {
        media.previewFileId?.let { viewModel.download(it, 16) }
    }
    LaunchedEffect(media.fileId) {
        if (media.size in 1..AutoDownloadLimit) viewModel.download(media.fileId, 8)
    }
    val mini = remember(media.miniThumb) {
        media.miniThumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val previewBitmap = rememberFileBitmap(preview.path, 512)
    var playing by remember { mutableStateOf(false) }

    Box(
        Modifier
            .size(200.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable {
                val path = full.path
                when {
                    playing -> playing = false
                    path != null -> {
                        AudioPlayback.stop()
                        playing = true
                    }
                    !full.active -> viewModel.download(media.fileId, 16)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        mini?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        previewBitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        val path = full.path
        if (playing && path != null) {
            RoundVideoPlayer(path = path, onEnded = { playing = false })
        } else {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                if (full.active) {
                    CircularProgressIndicator(Modifier.size(36.dp), color = Color.White, strokeWidth = 2.5.dp)
                } else {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Text(
                formatDuration(media.duration),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun RoundVideoPlayer(path: String, onEnded: () -> Unit) {
    val player = remember(path) { MediaPlayer() }
    DisposableEffect(player) {
        onDispose { runCatching { player.release() } }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        try {
                            player.setSurface(Surface(texture))
                            player.setDataSource(path)
                            player.setOnPreparedListener { it.start() }
                            player.setOnCompletionListener { onEnded() }
                            player.setOnErrorListener { _, _, _ ->
                                onEnded()
                                true
                            }
                            player.prepareAsync()
                        } catch (e: Exception) {
                            onEnded()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}
