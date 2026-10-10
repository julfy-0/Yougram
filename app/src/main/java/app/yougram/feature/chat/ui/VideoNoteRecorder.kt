package app.yougram.feature.chat.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Rational
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.yougram.core.ui.rememberDeviceTier
import app.yougram.core.ui.DeviceTier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlinx.coroutines.delay

private const val MAX_SECONDS = 60

/** Состояние зажатой кнопки записи (управляется снаружи, из ChatScreen). */
const val HOLD_NONE = 0 // обычный режим: запись стартует кнопкой в окне
const val HOLD_ACTIVE = 1 // палец зажат: идёт запись
const val HOLD_LOCKED = 2 // закреплено свайпом вверх: палец можно убрать
const val HOLD_RELEASED = 3 // палец отпущен: остановить и отправить
const val HOLD_CANCELLED = 4 // свайп влево: отменить

/**
 * Полноэкранная запись кружка: круглое превью, переключение камеры, таймер.
 * Видео обрезается до квадрата (ViewPort 1:1), так что Telegram принимает его как видеосообщение.
 * Нужны разрешения CAMERA и RECORD_AUDIO — запрашиваются снаружи до показа.
 */
@Composable
fun VideoNoteRecorderDialog(
    onSend: (path: String, seconds: Int, length: Int) -> Unit,
    onDismiss: () -> Unit,
    autoStart: Boolean = false,
    hold: Int = HOLD_NONE,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }

    var front by remember { mutableStateOf(true) }
    var recordingState by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val handle = remember { RecordingHandle() }

    // (Пере)привязка камеры при смене объектива.
    LaunchedEffect(front, previewView) {
        val view = previewView ?: return@LaunchedEffect
        val provider = ProcessCameraProvider.getInstance(context)
        provider.addListener({
            runCatching {
                val cameraProvider = provider.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)),
                    )
                    .build()
                val capture = VideoCapture.withOutput(recorder)
                val group = UseCaseGroup.Builder()
                    .setViewPort(ViewPort.Builder(Rational(1, 1), view.display?.rotation ?: android.view.Surface.ROTATION_0).build())
                    .addUseCase(preview)
                    .addUseCase(capture)
                    .build()
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, group)
                videoCapture = capture
            }
        }, executor)
    }

    LaunchedEffect(recordingState) {
        seconds = 0
        while (recordingState) {
            delay(1000)
            seconds++
            if (seconds >= MAX_SECONDS) {
                handle.finish(send = true)
                recordingState = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            handle.finish(send = false)
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    fun start() {
        val capture = videoCapture ?: return
        val file = File(context.cacheDir, "round_${System.currentTimeMillis()}.mp4")
        val startedAt = System.currentTimeMillis()
        val recording = capture.output
            .prepareRecording(context, FileOutputOptions.Builder(file).build())
            .withAudioEnabled()
            .start(executor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    val elapsed = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
                    val ok = !event.hasError() && elapsed >= 1 && file.length() > 0
                    if (handle.sendOnFinalize && ok) {
                        onSend(file.absolutePath, elapsed.coerceAtMost(MAX_SECONDS), squareSide(file))
                        onDismiss()
                    } else {
                        file.delete()
                        if (handle.sendOnFinalize) onDismiss() // слишком короткая запись при отпускании
                    }
                }
            }
        handle.recording = recording
        recordingState = true
    }

    // Запись зажатием: стартуем, как только камера готова; дальше управляет состояние hold.
    LaunchedEffect(videoCapture) {
        if (autoStart && videoCapture != null && !recordingState && hold != HOLD_RELEASED && hold != HOLD_CANCELLED) start()
    }
    LaunchedEffect(hold) {
        when (hold) {
            HOLD_RELEASED -> if (handle.recording != null) handle.finish(send = true) else onDismiss()
            HOLD_CANCELLED -> {
                handle.finish(send = false)
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) {
        // Всё за окном записи размывается (как под меню сообщения); на слабых устройствах — плотное затемнение вместо блюра.
        val lowTier = rememberDeviceTier() == DeviceTier.Low
        DialogWindowBlur(radius = if (lowTier) 0.dp else 28.dp, dim = if (lowTier) 0.45f else 0.30f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (lowTier) 0.85f else 0.15f))) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(280.dp)
                        .clip(CircleShape)
                        .border(3.dp, if (recordingState) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.4f), CircleShape),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                previewView = this
                            }
                        },
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "%d:%02d".format(seconds / 60, seconds % 60),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(24.dp))
                if (hold == HOLD_ACTIVE) {
                    Text(
                        "Вверх — закрепить, влево — отмена",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else Row(
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButton(Icons.Filled.Close, "Отмена", Color.White.copy(alpha = 0.18f)) {
                        handle.finish(send = false)
                        onDismiss()
                    }
                    if (recordingState) {
                        RoundButton(
                            Icons.AutoMirrored.Filled.Send, "Отправить",
                            MaterialTheme.colorScheme.primary, size = 72,
                        ) {
                            handle.finish(send = true)
                            recordingState = false
                        }
                    } else {
                        RoundButton(
                            Icons.Filled.FiberManualRecord, "Запись",
                            MaterialTheme.colorScheme.error, size = 72,
                        ) { start() }
                    }
                    RoundButton(
                        Icons.Filled.FlipCameraAndroid, "Сменить камеру",
                        Color.White.copy(alpha = if (recordingState) 0.06f else 0.18f),
                    ) { if (!recordingState) front = !front }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    color: Color,
    size: Int = 52,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, shape = CircleShape, color = color, contentColor = Color.White, modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size((size * 0.45f).dp))
        }
    }
}

private class RecordingHandle {
    var recording: Recording? = null
    var sendOnFinalize: Boolean = false

    fun finish(send: Boolean) {
        val r = recording ?: return
        recording = null
        sendOnFinalize = send
        runCatching { r.stop() }
    }
}

/** Реальная сторона квадрата готового ролика (после обрезки). */
private fun squareSide(file: File): Int {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(file.absolutePath)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 480
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 480
        minOf(w, h).coerceIn(240, 640)
    } catch (_: Exception) {
        480
    } finally {
        runCatching { r.release() }
    }
}