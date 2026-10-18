package app.yougram.feature.calls.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.telegram.messenger.voip.VideoCapturerDevice
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSink

/**
 * Compose-обёртка поверх WebRTC SurfaceViewRenderer для отображения
 * локального (превью) или удалённого видеопотока.
 */
@Composable
fun VideoSurface(
    videoSinkSetter: ((VideoSink?) -> Unit)?,
    modifier: Modifier = Modifier,
    isOverlay: Boolean = false,
    mirror: Boolean = false,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL,
    /** Вызывается на главном потоке, когда рендерер реально нарисовал первый кадр. */
    onFirstFrame: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val firstFrame = rememberUpdatedState(onFirstFrame)
    // Сбой инициализации GL не должен ронять приложение посреди звонка: без поверхности просто нет картинки.
    val renderer = remember {
        runCatching {
            SurfaceViewRenderer(context).apply {
                init(
                    VideoCapturerDevice.getSharedEGLContext(),
                    object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {
                            // Вызывается с потока рендера, а состояние Compose трогаем только с главного.
                            Handler(Looper.getMainLooper()).post { firstFrame.value?.invoke() }
                        }

                        override fun onFrameResolutionChanged(videoWidth: Int, videoHeight: Int, rotation: Int) = Unit
                    },
                )
                setScalingType(scalingType)
                setEnableHardwareScaler(true)
                if (isOverlay) {
                    setZOrderMediaOverlay(true)
                }
            }
        }.getOrNull()
    } ?: return

    DisposableEffect(renderer) {
        videoSinkSetter?.invoke(renderer)
        onDispose {
            // Сначала отключаем рендерер от потока кадров (синхронно с доставкой кадра), потом освобождаем.
            videoSinkSetter?.invoke(null)
            runCatching { renderer.clearImage() }
            runCatching { renderer.release() }
        }
    }
    // Зеркало меняется отдельно: раньше смена mirror освобождала рендерер и привязывала уже мёртвый.
    SideEffect { renderer.setMirror(mirror) }

    AndroidView(
        factory = { renderer },
        modifier = modifier,
    )
}