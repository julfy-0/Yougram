package app.yougram.feature.chat.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.yougram.core.settings.GlassSettings
import app.yougram.core.ui.component.WavySeekBar
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.glass
import app.yougram.core.ui.glass.rememberBackdropState
import app.yougram.core.ui.glass.BackdropState
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun TextureView.fit(videoW: Int, videoH: Int, crop: Boolean) {
    val vw = width.toFloat()
    val vh = height.toFloat()
    if (videoW <= 0 || videoH <= 0 || vw <= 0f || vh <= 0f) return
    val scale = if (crop) max(vw / videoW, vh / videoH) else min(vw / videoW, vh / videoH)
    val matrix = Matrix().apply { setScale(videoW * scale / vw, videoH * scale / vh, vw / 2f, vh / 2f) }
    setTransform(matrix)
}

/** Беззвучное зацикленное видео (GIF-анимации, WEBM-стикеры). */
@Composable
fun LoopingVideo(path: String, modifier: Modifier = Modifier, muted: Boolean = true, crop: Boolean = true) {
    val player = remember(path) { MediaPlayer() }
    DisposableEffect(player) { onDispose { runCatching { player.release() } } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                val view = this
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        runCatching {
                            player.setSurface(Surface(texture))
                            player.setDataSource(path)
                            player.isLooping = true
                            if (muted) player.setVolume(0f, 0f)
                            player.setOnVideoSizeChangedListener { _, vw, vh -> view.fit(vw, vh, crop) }
                            player.setOnPreparedListener { it.start() }
                            player.setOnErrorListener { _, _, _ -> true }
                            player.prepareAsync()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                        runCatching { view.fit(player.videoWidth, player.videoHeight, crop) }
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}

/** Анимированный стикер .tgs (gzip-JSON Lottie). */
@Composable
fun TgsSticker(path: String, modifier: Modifier = Modifier) {
    val json by produceState<String?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                GZIPInputStream(File(path).inputStream()).bufferedReader().use { it.readText() }
            }.getOrNull()
        }
    }
    val source = json ?: return
    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(source))
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        modifier = modifier,
    )
}

private val PlayerGlass = GlassSettings(blurRadius = 28f, opacity = 0.35f)
private val PlayerSpeeds = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

private fun formatTime(ms: Int): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Круглая кнопка со «стеклом»: размытая копия видео под ней + лёгкая подкраска и тонкая рамка. */
@Composable
private fun GlassButton(backdrop: BackdropState, size: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size)
            .glass(backdrop, PlayerGlass, CircleShape, Color.Black)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Полноэкранный видеоплеер. Кнопки и панель перемотки — «стеклянные» (размывают кадр под собой).
 * Тап — показать/скрыть управление, двойной тап слева/справа — ±10 с, скорость — чип справа сверху.
 */
@Composable
fun VideoViewerDialog(path: String, onDismiss: () -> Unit) {
    val player = remember(path) { MediaPlayer() }
    val backdrop = rememberBackdropState()
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var controls by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var speedIndex by remember { mutableIntStateOf(1) }

    DisposableEffect(player) { onDispose { runCatching { player.release() } } }

    fun applySpeed() {
        runCatching { player.playbackParams = player.playbackParams.setSpeed(PlayerSpeeds[speedIndex]) }
    }

    fun seekTo(ms: Int) {
        if (!prepared) return
        val t = ms.coerceIn(0, duration)
        player.seekTo(t)
        position = t
    }

    fun togglePlay() {
        if (!prepared) return
        if (playing) {
            player.pause()
            playing = false
        } else {
            if (duration > 0 && position >= duration - 200) seekTo(0)
            player.start()
            applySpeed() // скорость задаём только на ходу: на паузе это может запустить воспроизведение
            playing = true
        }
        poke++
    }

    LaunchedEffect(playing) {
        while (playing) {
            if (!dragging) position = runCatching { player.currentPosition }.getOrDefault(position)
            delay(200)
        }
    }
    LaunchedEffect(controls, playing, dragging, poke) {
        if (controls && playing && !dragging) {
            delay(3000)
            controls = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        app.yougram.core.ui.SecureWindowEffect()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Источник для размытия: только видео, без панелей управления.
            Box(
                Modifier
                    .fillMaxSize()
                    .backdropSource(backdrop)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controls = !controls },
                            onDoubleTap = { o ->
                                seekTo(position + if (o.x < size.width / 2f) -10_000 else 10_000)
                                controls = true
                                poke++
                            },
                        )
                    },
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            val view = this
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                                    runCatching {
                                        player.setSurface(Surface(texture))
                                        player.setDataSource(path)
                                        player.setOnVideoSizeChangedListener { _, vw, vh -> view.fit(vw, vh, crop = false) }
                                        player.setOnPreparedListener {
                                            duration = it.duration
                                            prepared = true
                                            it.start()
                                            playing = true
                                        }
                                        player.setOnCompletionListener {
                                            playing = false
                                            position = duration
                                            controls = true
                                        }
                                        player.setOnErrorListener { _, _, _ ->
                                            onDismiss()
                                            true
                                        }
                                        player.prepareAsync()
                                    }
                                }

                                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                                    runCatching { view.fit(player.videoWidth, player.videoHeight, crop = false) }
                                }

                                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true
                                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                            }
                        }
                    },
                )
            }

            AnimatedVisibility(visible = controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.align(Alignment.TopStart).padding(top = 36.dp, start = 16.dp)) {
                        GlassButton(backdrop, 44.dp, onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 36.dp, end = 16.dp)
                            .height(44.dp)
                            .glass(backdrop, PlayerGlass, RoundedCornerShape(50), Color.Black)
                            .clickable {
                                speedIndex = (speedIndex + 1) % PlayerSpeeds.size
                                if (playing) applySpeed()
                                poke++
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val speed = PlayerSpeeds[speedIndex]
                        Text(
                            (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "×",
                            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        )
                    }

                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassButton(backdrop, 52.dp, { seekTo(position - 10_000); poke++ }) {
                            Icon(Icons.Filled.Replay10, contentDescription = "Назад на 10 с", tint = Color.White)
                        }
                        GlassButton(backdrop, 76.dp, { togglePlay() }) {
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playing) "Пауза" else "Пуск",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        GlassButton(backdrop, 52.dp, { seekTo(position + 10_000); poke++ }) {
                            Icon(Icons.Filled.Forward10, contentDescription = "Вперёд на 10 с", tint = Color.White)
                        }
                    }

                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 16.dp, end = 16.dp, bottom = 28.dp)
                            .fillMaxWidth()
                            .glass(backdrop, PlayerGlass, RoundedCornerShape(28.dp), Color.Black)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        val fraction = if (dragging) dragFraction
                        else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                        WavySeekBar(
                            progress = fraction,
                            playing = playing && !dragging,
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f),
                            onSeek = { dragging = true; dragFraction = it },
                            onSeekFinished = {
                                seekTo((dragFraction * duration).toInt())
                                dragging = false
                                poke++
                            },
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime((fraction * duration).toInt()), color = Color.White, fontSize = 13.sp)
                            Text(formatTime(duration), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}