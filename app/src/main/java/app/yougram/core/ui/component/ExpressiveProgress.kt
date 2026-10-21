package app.yougram.core.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Выразительный круглый индикатор M3 Expressive с зафиксированной дугой и вырастающим/бегущим волнистым отрезком.
 */
@Composable
fun WavyCircleRing(
    modifier: Modifier = Modifier,
    mainArcColor: Color = MaterialTheme.colorScheme.primary,
    wavySegmentColor: Color = MaterialTheme.colorScheme.tertiary,
    strokeWidth: Dp = 4.dp,
    waveAmplitude: Dp = 5.dp,
    maxWaveSweep: Float = 70f,
    waveFrequency: Float = 3f,
    animating: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "wavy_ring_anim")

    // 1. Анимация бегущей волны (волна течёт вдоль дуги без вращения всего круга)
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // 2. Анимация роста / расширения длины волнистого отрезка
    val sweepProgress by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweepProgress"
    )

    Canvas(modifier = modifier) {
        val strokeWidthPx = strokeWidth.toPx()
        val amplitudePx = waveAmplitude.toPx()
        val radius = (minOf(size.width, size.height) - strokeWidthPx - amplitudePx * 2) / 2f
        val centerPoint = center

        // Зафиксированное начало волнистой секции (сверху справа, ~305°)
        val waveStartDegree = 305f
        val currentWaveSweep = if (animating) maxWaveSweep * sweepProgress else maxWaveSweep
        val waveEndDegree = waveStartDegree + currentWaveSweep

        // Основная сплошная дуга
        val mainArcStartDegree = (waveEndDegree + 10f) % 360f
        val mainArcSweepDegree = (360f - currentWaveSweep - 20f).coerceAtLeast(10f)

        // 1. Рисуем основную фиксированную дугу
        drawArc(
            color = mainArcColor,
            startAngle = mainArcStartDegree,
            sweepAngle = mainArcSweepDegree,
            useCenter = false,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )

        // 2. Рисуем растущий волнистый отрезок с бегущей волной
        if (currentWaveSweep > 2f) {
            val path = Path()
            val steps = 80
            val phase = if (animating) wavePhase else 0f

            for (i in 0..steps) {
                val fraction = i.toFloat() / steps
                val angleDeg = waveStartDegree + fraction * currentWaveSweep
                val angleRad = Math.toRadians(angleDeg.toDouble())

                val envelope = sin(fraction * Math.PI).toFloat()
                val waveOffset = sin(fraction * waveFrequency * 2 * Math.PI - phase).toFloat() * amplitudePx * envelope
                val r = radius + waveOffset

                val x = centerPoint.x + r * cos(angleRad).toFloat()
                val y = centerPoint.y + r * sin(angleRad).toFloat()

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = wavySegmentColor,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Общий индикатор загрузки приложения: морфящаяся фигура M3 Expressive.
 * [contained] = true — фигура в круглом контейнере (для одиноких индикаторов по центру экрана),
 * false — просто фигура (для кнопок, строк, плиток).
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    contained: Boolean = false,
) {
    if (contained) {
        androidx.compose.material3.ContainedLoadingIndicator(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            indicatorColor = color,
        )
    } else {
        androidx.compose.material3.LoadingIndicator(modifier = modifier, color = color)
    }
}

/**
 * Круглый индикатор прогресса как в Material 3 Expressive: пройденная часть кольца волнистая и «течёт»,
 * оставшаяся — ровная бледная дорожка.
 */
@Composable
fun CircularWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.25f),
    strokeWidth: Dp = 3.dp,
    amplitude: Dp = 1.6.dp,
    waves: Int = 6,
) {
    val transition = rememberInfiniteTransition(label = "circular_wavy")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier) {
        val sw = strokeWidth.toPx()
        val amp = amplitude.toPx()
        val radius = (minOf(size.width, size.height) - sw - amp * 2) / 2f
        if (radius <= 0f) return@Canvas
        val p = progress().coerceIn(0f, 1f)
        val stroke = Stroke(width = sw, cap = StrokeCap.Round)
        val topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius)
        val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)

        // Оставшаяся часть — ровная дорожка.
        if (p < 1f) {
            drawArc(
                color = trackColor,
                startAngle = -90f + p * 360f,
                sweepAngle = (1f - p) * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }
        // Пройденная часть — волна.
        if (p > 0f) {
            val sweep = p * 360f
            val steps = maxOf(8, (sweep / 2f).toInt())
            val path = Path()
            for (i in 0..steps) {
                val f = i.toFloat() / steps
                val angle = Math.toRadians((-90f + f * sweep).toDouble())
                val off = sin((f * p * waves * 2 * Math.PI) - phase).toFloat() * amp
                val r = radius + off
                val x = center.x + r * cos(angle).toFloat()
                val y = center.y + r * sin(angle).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = stroke)
        }
    }
}

/** Линейный индикатор M3 Expressive: пройденная часть волнистая и «течёт», остаток — ровная дорожка. */
@Composable
fun LinearWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.25f),
    strokeWidth: Dp = 4.dp,
    amplitude: Dp = 3.dp,
    wavelength: Dp = 22.dp,
) {
    val transition = rememberInfiniteTransition(label = "linear_wavy")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier.height(strokeWidth + amplitude * 2)) {
        val sw = strokeWidth.toPx()
        val amp = amplitude.toPx()
        val wl = wavelength.toPx()
        val cy = size.height / 2f
        val start = sw / 2f
        val end = size.width - sw / 2f
        if (end <= start) return@Canvas
        val p = progress().coerceIn(0f, 1f)
        val x = start + (end - start) * p
        val stroke = Stroke(width = sw, cap = StrokeCap.Round)

        if (p < 1f) {
            val track = Path().apply { moveTo(x + sw, cy); lineTo(end, cy) }
            if (x + sw < end) drawPath(track, trackColor, style = stroke)
        }
        if (p > 0f) {
            val path = Path()
            var px = start
            var first = true
            while (px <= x) {
                // Плавно «успокаиваем» волну у самого конца, чтобы она переходила в дорожку.
                val y = cy + sin((px - start) / wl * 2 * Math.PI - phase).toFloat() * amp
                if (first) { path.moveTo(px, y); first = false } else path.lineTo(px, y)
                px += 2f
            }
            path.lineTo(x, cy + sin((x - start) / wl * 2 * Math.PI - phase).toFloat() * amp)
            drawPath(path, color, style = stroke)
        }
    }
}
