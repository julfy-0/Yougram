package app.yougram.feature.chat.ui

import app.yougram.core.i18n.tr
import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Rational
import androidx.camera.core.Camera
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import app.yougram.core.i18n.Text
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
import app.yougram.core.settings.CameraPrefs
import app.yougram.core.settings.VideoCameraChoice
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
    camera: CameraPrefs = CameraPrefs(),
    onCameraUsed: (front: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }

    val advanced = camera.advanced
    var front by remember {
        mutableStateOf(
            if (advanced && camera.rememberLast) camera.lastFront else camera.videoCamera == VideoCameraChoice.Main,
        )
    }
    var zoom by remember { mutableStateOf(0f) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
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
                boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, group)
                zoom = 0f
                videoCapture = capture
            }.onFailure {
                android.widget.Toast.makeText(context, tr("Не удалось включить камеру"), android.widget.Toast.LENGTH_SHORT).show()
            }
        }, executor)
    }

    LaunchedEffect(zoom, boundCamera) {
        boundCamera?.cameraControl?.setLinearZoom(zoom)
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

    /** Склеивает куски (до/после переворота камеры) и отправляет результат. */
    fun deliver(segments: List<File>, startedAt: Long) {
        val elapsed = ((System.currentTimeMillis() - startedAt) / 1000).toInt().coerceAtLeast(1)
        fun fail() {
            segments.forEach { it.delete() }
            onDismiss()
        }
        val valid = segments.filter { it.exists() && it.length() > 0 }
        if (valid.isEmpty() || elapsed < 1) return fail()
        if (valid.size == 1) {
            onSend(valid[0].absolutePath, elapsed.coerceAtMost(MAX_SECONDS), squareSide(valid[0]))
            onDismiss()
            return
        }
        val out = File(context.cacheDir, "round_${System.currentTimeMillis()}_m.mp4")
        mergeSegments(context, valid, out) { ok ->
            segments.forEach { it.delete() }
            if (ok && out.length() > 0) {
                onSend(out.absolutePath, elapsed.coerceAtMost(MAX_SECONDS), squareSide(out))
                onDismiss()
            } else {
                out.delete()
                android.widget.Toast.makeText(context, tr("Не удалось склеить запись"), android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }

    handle.pendingDeliver = { segs -> deliver(segs, handle.startedAt) }

    fun start() {
        val capture = videoCapture ?: return
        val file = File(context.cacheDir, "round_${System.currentTimeMillis()}.mp4")
        if (handle.segments.isEmpty()) handle.startedAt = System.currentTimeMillis()
        val recording = capture.output
            .prepareRecording(context, FileOutputOptions.Builder(file).build())
            .withAudioEnabled()
            .start(executor) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    if (!event.hasError() && file.length() > 0) handle.segments.add(file) else file.delete()
                    if (handle.switching && !handle.finishRequested) {
                        // Переворот камеры посреди записи: кусок закрыт, переключаем объектив и продолжим.
                        handle.recording = null
                        handle.resumeAfterBind = true
                        front = !front
                        onCameraUsed(front)
                    } else {
                        handle.switching = false
                        val segs = handle.segments.toList()
                        handle.segments.clear()
                        if (handle.sendOnFinalize) deliver(segs, handle.startedAt)
                        else {
                            segs.forEach { it.delete() }
                            // слишком короткая запись при отпускании: окно всё равно закрываем
                        }
                    }
                }
            }
        handle.recording = recording
        recordingState = true
    }

    // После переворота камеры в записи — продолжаем новым куском, как только новая камера готова.
    LaunchedEffect(videoCapture) {
        if (handle.resumeAfterBind && videoCapture != null) {
            handle.resumeAfterBind = false
            handle.switching = false
            start()
        }
    }

    // Запись зажатием: стартуем, как только камера готова; дальше управляет состояние hold.
    LaunchedEffect(videoCapture) {
        if (autoStart && videoCapture != null && !recordingState && !handle.resumeAfterBind && hold != HOLD_RELEASED && hold != HOLD_CANCELLED) start()
    }
    LaunchedEffect(hold) {
        when (hold) {
            HOLD_RELEASED -> if (handle.recording != null || handle.switching) handle.finish(send = true) else onDismiss()
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
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(280.dp)
                            .clip(CircleShape),
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
                }
                if (advanced && camera.zoomSlider) {
                    Spacer(Modifier.height(12.dp))
                    Slider(
                        value = zoom,
                        onValueChange = { zoom = it },
                        onValueChangeFinished = { if (!camera.staticZoom) zoom = 0f },
                        modifier = Modifier.width(220.dp),
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
                    RoundButton(Icons.Filled.Close, tr("Отмена"), Color.White.copy(alpha = 0.18f)) {
                        handle.finish(send = false)
                        onDismiss()
                    }
                    if (recordingState) {
                        RoundButton(
                            Icons.AutoMirrored.Filled.Send, tr("Отправить"),
                            MaterialTheme.colorScheme.primary, size = 72,
                        ) {
                            handle.finish(send = true)
                            recordingState = false
                        }
                    } else {
                        RoundButton(
                            Icons.Filled.FiberManualRecord, tr("Запись"),
                            MaterialTheme.colorScheme.error, size = 72,
                        ) { start() }
                    }
                    RoundButton(
                        Icons.Filled.FlipCameraAndroid, tr("Сменить камеру"),
                        Color.White.copy(alpha = 0.18f),
                    ) {
                        if (recordingState) {
                            if (!handle.switching && handle.recording != null) {
                                handle.switching = true
                                runCatching { handle.recording?.stop() }
                            }
                        } else {
                            front = !front
                            onCameraUsed(front)
                        }
                    }
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
    val segments = mutableListOf<File>()
    var startedAt: Long = 0L
    var switching: Boolean = false
    var finishRequested: Boolean = false
    var resumeAfterBind: Boolean = false

    fun finish(send: Boolean) {
        sendOnFinalize = send
        val r = recording
        if (r == null) {
            // Идёт переворот камеры: запись между кусками — завершаем сразу.
            if (switching || resumeAfterBind) {
                finishRequested = true
                switching = false
                resumeAfterBind = false
                val segs = segments.toList()
                segments.clear()
                if (!send) segs.forEach { it.delete() } else pendingDeliver?.invoke(segs)
            }
            return
        }
        recording = null
        if (switching) finishRequested = true
        runCatching { r.stop() }
    }

    var pendingDeliver: ((List<File>) -> Unit)? = null
}

/** Склейка кусков записи в один mp4 (Media3 Transformer сам выравнивает поворот и размер). */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun mergeSegments(context: Context, files: List<File>, out: File, onDone: (Boolean) -> Unit) {
    runCatching {
        val sequence = androidx.media3.transformer.EditedMediaItemSequence.Builder().apply {
            files.forEach {
                addItem(
                    androidx.media3.transformer.EditedMediaItem.Builder(
                        androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(it)),
                    ).build(),
                )
            }
        }.build()
        val composition = androidx.media3.transformer.Composition.Builder(sequence).build()
        androidx.media3.transformer.Transformer.Builder(context)
            .addListener(object : androidx.media3.transformer.Transformer.Listener {
                override fun onCompleted(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                ) = onDone(true)

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                    exportException: androidx.media3.transformer.ExportException,
                ) = onDone(false)
            })
            .build()
            .start(composition, out.absolutePath)
    }.onFailure { onDone(false) }
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