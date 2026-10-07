package app.yougram.feature.chat.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

/** Полноэкранный просмотр видео и GIF; тап по экрану — пауза/пуск. */
@Composable
fun VideoViewerDialog(path: String, onDismiss: () -> Unit) {
    var videoView: VideoView? = null
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable {
                    videoView?.let { if (it.isPlaying) it.pause() else it.start() }
                },
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    VideoView(ctx).apply {
                        videoView = this
                        setOnPreparedListener { it.start() }
                        setOnErrorListener { _, _, _ ->
                            onDismiss()
                            true
                        }
                        setVideoPath(path)
                    }
                },
                onRelease = { runCatching { it.stopPlayback() } },
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 32.dp, start = 4.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
            }
        }
    }
}
