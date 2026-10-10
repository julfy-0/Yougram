package app.yougram.core.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.rememberReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TwoPi = 2f * PI.toFloat()

/** Фаза бегущей волны (радианы). Читать только в draw-фазе, чтобы не перекомпоновывать. */
@Composable
private fun rememberWavePhase(running: Boolean, cyclesPerSecond: Float = 0.8f): FloatState {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    phase.floatValue = (phase.floatValue + (now - last) / 1_000_000_000f * TwoPi * cyclesPerSecond) % TwoPi
                }
                last = now
            }
        }
    }
    return phase
}

// ───────────────────────────── Круговые ─────────────────────────────

/** Неопределённая загрузка: вращающаяся волнистая дуга на светлом кольце. */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    WavyLoadingIndicator(modifier, color)
}

/** Неопределённая загрузка: вращающаяся волнистая дуга на светлом кольце. */
@Composable
fun WavyLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.22f),
) {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "wavyLoading")
    val rotation by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "rotation",
    )
    val sweep by transition.animateFloat(
        70f, 250f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    val phase = rememberWavePhase(!reduceMotion, 1.2f)
    Canvas(modifier.size(40.dp)) {
        drawWavyRing(
            startAngle = rotation - 90f,
            sweepAngle = sweep,
            phase = phase.floatValue,
            color = color,
            trackColor = trackColor,
        )
    }
}

/** Определённый прогресс (скачивание): светлое кольцо + волнистая дуга, бегущая по кругу. */
@Composable
fun CircularWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.22f),
) {
    val reduceMotion = rememberReduceMotion()
    val phase = rememberWavePhase(!reduceMotion, 0.9f)
    Canvas(modifier.size(40.dp)) {
        drawWavyRing(
            startAngle = -90f,
            sweepAngle = (progress().coerceIn(0f, 1f) * 360f).coerceAtLeast(14f),
            phase = phase.floatValue,
            color = color,
            trackColor = trackColor,
        )
    }
}

private fun DrawScope.drawWavyRing(
    startAngle: Float,
    sweepAngle: Float,
    phase: Float,
    color: Color,
    trackColor: Color,
) {
    val side = min(size.width, size.height)
    val stroke = (side * 0.085f).coerceIn(2.dp.toPx(), 5.dp.toPx())
    val amp = stroke * 0.5f
    val r = (side - stroke) / 2f - amp
    if (r <= 0f) return
    val c = Offset(size.width / 2f, size.height / 2f)

    drawCircle(trackColor, r, c, style = Stroke(stroke))

    // Целое число волн на окружности, чтобы фаза не «прыгала».
    val waves = (TwoPi * r / 12.dp.toPx()).roundToInt().coerceAtLeast(5)
    val start = Math.toRadians(startAngle.toDouble()).toFloat()
    val sweep = Math.toRadians(sweepAngle.toDouble()).toFloat()
    val steps = (sweepAngle / 3f).roundToInt().coerceAtLeast(8)
    val path = Path()
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val a = start + sweep * t
        val rr = r + amp * sin(waves * (a - start) - phase + waves * start)
        val x = c.x + rr * cos(a)
        val y = c.y + rr * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ───────────────────────────── Линейные ─────────────────────────────

/** Волнистая полоска определённого прогресса (загрузка обновления, страницы). */
@Composable
fun LinearWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.22f),
) {
    val reduceMotion = rememberReduceMotion()
    val phase = rememberWavePhase(!reduceMotion)
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        drawWavyLinear(
            fraction = progress(),
            phase = phase.floatValue,
            amplitude = 2.dp.toPx(),
            wavelength = 20.dp.toPx(),
            stroke = 3.dp.toPx(),
            color = color,
            trackColor = trackColor,
            thumb = false,
        )
    }
}

/**
 * Волнистая полоска воспроизведения (голосовые, видео): пройденная часть — бегущая волна,
 * остальное — ровная линия с точкой на конце. На паузе волна плавно выпрямляется.
 * Если передан [onSeek], по полоске можно водить пальцем / тапать — это перемотка.
 *
 * @param progress 0..1
 * @param playing волна движется и имеет амплитуду, пока true
 * @param onSeek вызывается с долей 0..1 при тапе и перетаскивании
 * @param onSeekFinished палец отпущен — можно применять перемотку
 */
@Composable
fun WavySeekBar(
    progress: Float,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.25f),
    strokeWidth: Dp = 4.dp,
    wavelength: Dp = 28.dp,
    amplitude: Dp = 3.dp,
    onSeek: ((Float) -> Unit)? = null,
    onSeekFinished: (() -> Unit)? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val waveAmount = animateFloatAsState(if (playing) 1f else 0f, tween(300), label = "waveAmount")
    val phase = rememberWavePhase(playing && !reduceMotion)

    val seek by rememberUpdatedState(onSeek)
    val finished by rememberUpdatedState(onSeekFinished)
    val seekable = onSeek != null
    val gestures = if (seekable) {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(onTap = { o ->
                    seek?.invoke((o.x / size.width).coerceIn(0f, 1f))
                    finished?.invoke()
                })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> seek?.invoke((o.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { finished?.invoke() },
                    onDragCancel = { finished?.invoke() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        seek?.invoke((change.position.x / size.width).coerceIn(0f, 1f))
                    },
                )
            }
    } else Modifier

    Canvas(modifier.fillMaxWidth().height(24.dp).then(gestures)) {
        drawWavyLinear(
            fraction = progress,
            phase = phase.floatValue,
            amplitude = amplitude.toPx() * waveAmount.value,
            wavelength = wavelength.toPx(),
            stroke = strokeWidth.toPx(),
            color = color,
            trackColor = trackColor,
            thumb = seekable,
        )
    }
}

private fun DrawScope.drawWavyLinear(
    fraction: Float,
    phase: Float,
    amplitude: Float,
    wavelength: Float,
    stroke: Float,
    color: Color,
    trackColor: Color,
    thumb: Boolean,
) {
    val half = stroke / 2f
    val cy = size.height / 2f
    val gap = stroke * 0.75f
    val f = fraction.coerceIn(0f, 1f)
    val x0 = half
    val x1 = size.width - half
    val end = x0 + (x1 - x0) * f
    val w = TwoPi / wavelength

    // Неактивная часть — ровная линия после волны, на конце точка.
    val restStart = end + if (f > 0f) gap + half else 0f
    if (restStart < x1) {
        drawLine(trackColor, Offset(restStart, cy), Offset(x1, cy), stroke, StrokeCap.Round)
    }
    drawCircle(color, half * 0.9f, Offset(x1, cy))

    // Пройденная часть — волна; у начала амплитуда плавно набирается.
    if (end > x0) {
        val path = Path()
        val ramp = wavelength * 0.5f
        var x = x0
        while (true) {
            val xx = minOf(x, end)
            val edge = minOf((xx - x0) / ramp, (end - xx) / ramp + 0.35f).coerceIn(0f, 1f)
            val y = cy + amplitude * edge * sin(w * (xx - x0) - phase)
            if (xx == x0) path.moveTo(xx, y) else path.lineTo(xx, y)
            if (xx >= end) break
            x += 2f
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    } else {
        drawCircle(color, half, Offset(x0, cy))
    }
    if (thumb) {
        val tx = end.coerceIn(x0, x1)
        drawLine(color, Offset(tx, cy - 10.dp.toPx()), Offset(tx, cy + 10.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
    }
}