package app.yougram.feature.chat.ui

import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import app.yougram.core.ui.component.LoadingIndicator
import app.yougram.core.ui.component.WavySeekBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.DeviceTier
import app.yougram.core.ui.rememberDeviceTier
import androidx.compose.ui.viewinterop.AndroidView
import app.yougram.core.ui.component.rememberFileBitmap
import app.yougram.feature.chat.data.FileState
import app.yougram.feature.chat.data.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

/** Голосовые и кружки меньше этого размера скачиваются сами, как в официальном клиенте. */
private const val AutoDownloadLimit = 8L shl 20

private fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** Голосовое сообщение: кнопка, волнистая полоска воспроизведения, длительность. */
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

    var drag by remember { mutableStateOf<Float?>(null) }
    val lowTier = rememberDeviceTier() == DeviceTier.Low
    val motion = MaterialTheme.motionScheme
    val cornerSpec: FiniteAnimationSpec<Dp> = if (lowTier) snap() else motion.fastSpatialSpec()
    val iconFadeSpec: FiniteAnimationSpec<Float> = if (lowTier) tween(100) else motion.defaultEffectsSpec()
    val buttonCorner by animateDpAsState(if (active) 22.dp else 14.dp, cornerSpec, label = "voiceButtonCorner")
    val played = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)

    Row(
        Modifier.width(240.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Кнопка воспроизведения: в покое — «квадрат со скруглением», при воспроизведении — круг.
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(buttonCorner.coerceAtLeast(0.dp)))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button) {
                    val path = full.path
                    when {
                        path != null -> AudioPlayback.toggle(media.fileId, path)
                        !full.active -> viewModel.download(media.fileId, 16)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val tint = MaterialTheme.colorScheme.onPrimary
            val state = when {
                full.active -> 0
                full.path == null -> 1
                active -> 2
                else -> 3
            }
            Crossfade(targetState = state, animationSpec = iconFadeSpec, label = "voiceIcon") { st ->
                when (st) {
                    0 -> LoadingIndicator(Modifier.size(28.dp), color = tint)
                    1 -> Icon(Icons.Filled.Download, "Скачать", tint = tint)
                    2 -> Icon(Icons.Filled.Pause, "Пауза", tint = tint)
                    else -> Icon(Icons.Filled.PlayArrow, "Воспроизвести", tint = tint)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            WavySeekBar(
                progress = drag ?: if (isCurrent) progress else 0f,
                modifier = Modifier.width(150.dp),
                playing = active && drag == null,
                color = played,
                trackColor = rest,
                onSeek = if (isCurrent) ({ drag = it }) else null,
                onSeekFinished = {
                    drag?.let(AudioPlayback::seekTo)
                    drag = null
                },
            )
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

    val lowTier = rememberDeviceTier() == DeviceTier.Low
    val motion = MaterialTheme.motionScheme
    val cornerSpec: FiniteAnimationSpec<Dp> = if (lowTier) snap() else motion.fastSpatialSpec()
    val fadeSpec: FiniteAnimationSpec<Float> = if (lowTier) tween(100) else motion.defaultEffectsSpec()
    // Как у голосового: в покое «квадрат со скруглением», при воспроизведении — круг.
    val buttonCorner by animateDpAsState(if (playing) 24.dp else 12.dp, cornerSpec, label = "roundButtonCorner")
    val overlayAlpha by animateFloatAsState(if (playing) 0f else 1f, fadeSpec, label = "roundOverlayAlpha")

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
        }
        Box(
            Modifier
                .size(48.dp)
                .graphicsLayer {
                    alpha = overlayAlpha
                    val s = 0.7f + 0.3f * overlayAlpha
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(buttonCorner.coerceAtLeast(0.dp)))
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            val st = when {
                full.active -> 0
                playing -> 1
                else -> 2
            }
            Crossfade(targetState = st, animationSpec = fadeSpec, label = "roundIcon") { s ->
                when (s) {
                    0 -> LoadingIndicator(Modifier.size(36.dp), color = Color.White)
                    1 -> Icon(Icons.Filled.Pause, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    else -> Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
        Text(
            formatDuration(media.duration),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { alpha = overlayAlpha }
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
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