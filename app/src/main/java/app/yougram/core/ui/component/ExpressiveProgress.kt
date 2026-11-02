package app.yougram.core.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.isSpecified
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
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

/*
 * Индикаторы прогресса M3 Expressive в едином виде для всего клиента:
 * пройденная часть — «текущая» волна, дальше зазор, потом ровная бледная дорожка (линейный — с точкой на конце).
 * Две толщины: тонкая и [thick]. Варианты с progress — определённые, без progress — неопределённые (бесконечные).
 */

private val LinearGap = 4.dp
private val StopDot = 2.dp

/** Направление бега волны линейных индикаторов: 1f — вправо, -1f — влево. */
private const val LinearWaveDirection = 1f

/** Плавное появление/исчезновение волны у краёв, как в официальном индикаторе. */
private fun waveFactor(p: Float): Float = when {
    p < 0.1f -> p / 0.1f
    p > 0.95f -> (1f - p) / 0.05f
    else -> 1f
}.coerceIn(0f, 1f)

@Composable
private fun rememberWavePhase(durationMs: Int): Float {
    val transition = rememberInfiniteTransition(label = "wave_phase")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    return phase
}

private fun DrawScope.drawLinearWave(x0: Float, x1: Float, cy: Float, amp: Float, wl: Float, phase: Float, color: Color, stroke: Stroke) {
    if (x1 <= x0) return
    val path = Path()
    var px = x0
    var first = true
    val p = phase * LinearWaveDirection
    while (px < x1) {
        val y = cy + sin((px - x0) / wl * 2 * PI - p).toFloat() * amp
        if (first) { path.moveTo(px, y); first = false } else path.lineTo(px, y)
        px += 2f
    }
    path.lineTo(x1, cy + sin((x1 - x0) / wl * 2 * PI - p).toFloat() * amp)
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawLinearTrack(x0: Float, x1: Float, cy: Float, color: Color, stroke: Stroke) {
    if (x1 > x0) drawLine(color, Offset(x0, cy), Offset(x1, cy), strokeWidth = stroke.width, cap = StrokeCap.Round)
}

/**
 * Линейный индикатор M3 Expressive. [progress] задан — определённый, иначе бесконечный.
 * [thick] — толстый вариант (8 dp вместо 4 dp).
 */
@Composable
fun LinearWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.25f),
    thick: Boolean = false,
    strokeWidth: Dp = Dp.Unspecified,
    amplitude: Dp = 3.dp,
    wavelength: Dp = 22.dp,
) {
    val sw0 = if (strokeWidth.isSpecified) strokeWidth else if (thick) 8.dp else 4.dp
    val phase = rememberWavePhase(1200)
    Canvas(modifier.height(sw0 + amplitude * 2)) {
        val sw = sw0.toPx()
        val amp = amplitude.toPx()
        val wl = wavelength.toPx()
        val gap = LinearGap.toPx()
        val cy = size.height / 2f
        val start = sw / 2f
        val end = size.width - sw / 2f
        if (end <= start) return@Canvas
        val p = progress().coerceIn(0f, 1f)
        val x = start + (end - start) * p
        val stroke = Stroke(width = sw, cap = StrokeCap.Round)

        // Дорожка после зазора.
        val trackStart = x + sw + gap
        if (p < 1f) drawLinearTrack(trackStart, end, cy, trackColor, stroke)
        // Точка-«стоп» на конце дорожки.
        if (p < 0.98f) drawCircle(color, StopDot.toPx(), Offset(end - StopDot.toPx(), cy))
        // Пройденная часть — волна.
        if (p > 0f) drawLinearWave(start, x, cy, amp * waveFactor(p), wl, phase, color, stroke)
    }
}

/** Бесконечный линейный индикатор: волнистый отрезок бежит слева направо, вокруг — дорожка с зазорами. */
@Composable
fun LinearWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.25f),
    thick: Boolean = false,
    strokeWidth: Dp = Dp.Unspecified,
    amplitude: Dp = 3.dp,
    wavelength: Dp = 22.dp,
) {
    val sw0 = if (strokeWidth.isSpecified) strokeWidth else if (thick) 8.dp else 4.dp
    val phase = rememberWavePhase(1200)
    val transition = rememberInfiniteTransition(label = "linear_indeterminate")
    val t by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(modifier.height(sw0 + amplitude * 2)) {
        val sw = sw0.toPx()
        val amp = amplitude.toPx()
        val wl = wavelength.toPx()
        val gap = LinearGap.toPx()
        val cy = size.height / 2f
        val start = sw / 2f
        val end = size.width - sw / 2f
        if (end <= start) return@Canvas
        val len = end - start
        val stroke = Stroke(width = sw, cap = StrokeCap.Round)
        val a = start + len * t.coerceIn(0f, 1f)
        val b = start + len * (t + 0.4f).coerceIn(0f, 1f)
        drawLinearTrack(start, a - sw - gap, cy, trackColor, stroke)
        drawLinearTrack(b + sw + gap, end, cy, trackColor, stroke)
        if (b < end - 1f) drawCircle(color, StopDot.toPx(), Offset(end - StopDot.toPx(), cy))
        if (b > a) drawLinearWave(a, b, cy, amp, wl, phase, color, stroke)
    }
}

private fun DrawScope.drawArcWave(c: Offset, r: Float, startDeg: Float, sweepDeg: Float, amp: Float, wl: Float, phase: Float, color: Color, stroke: Stroke) {
    if (sweepDeg <= 0.5f) return
    val arcLen = r * Math.toRadians(sweepDeg.toDouble()).toFloat()
    val steps = max(8, min(400, (arcLen / 3f).toInt()))
    val path = Path()
    for (i in 0..steps) {
        val f = i.toFloat() / steps
        val ang = Math.toRadians((startDeg + f * sweepDeg).toDouble())
        val off = sin(f * arcLen / wl * 2 * PI - phase).toFloat() * amp
        val rr = r + off
        val x = c.x + rr * cos(ang).toFloat()
        val y = c.y + rr * sin(ang).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawArcTrack(c: Offset, r: Float, startDeg: Float, sweepDeg: Float, color: Color, stroke: Stroke) {
    if (sweepDeg <= 0.5f) return
    drawArc(
        color, startDeg, sweepDeg, false,
        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = stroke,
    )
}

private fun circularMetrics(side: Float, strokeDp: Dp, thick: Boolean, density: Float): Triple<Float, Float, Float> {
    // Штрих не толще 1/6 размера, чтобы маленькие кольца не превращались в «бублик».
    val sw = min(strokeDp.value * density, side / 6f)
    val amp = if (thick) sw * 0.3f else sw * 0.5f
    val radius = (side - sw - amp * 2) / 2f
    return Triple(sw, amp, radius)
}

/** Круглый индикатор M3 Expressive: волнистая дуга прогресса, зазор, ровная дорожка. */
@Composable
fun CircularWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.25f),
    thick: Boolean = false,
    strokeWidth: Dp = Dp.Unspecified,
    wavelength: Dp = 10.dp,
) {
    val sw0 = if (strokeWidth.isSpecified) strokeWidth else if (thick) 6.dp else 3.dp
    val phase = rememberWavePhase(1400)
    Canvas(modifier) {
        val (sw, amp, radius) = circularMetrics(min(size.width, size.height), sw0, thick, density)
        if (radius <= 0f) return@Canvas
        val p = progress().coerceIn(0f, 1f)
        val stroke = Stroke(width = sw, cap = StrokeCap.Round)
        val gapDeg = Math.toDegrees(((sw * 2f) / radius).toDouble()).toFloat()
        val sweep = p * 360f
        val start = -90f
        if (p < 1f) drawArcTrack(center, radius, start + sweep + gapDeg, 360f - sweep - gapDeg * 2f, trackColor, stroke)
        if (p > 0f) drawArcWave(center, radius, start, sweep, amp * waveFactor(p), wavelength.toPx(), phase, color, stroke)
    }
}

/** Бесконечный круглый индикатор: волнистая дуга вращается и меняет длину, остальное — дорожка. */
@Composable
fun CircularWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.25f),
    thick: Boolean = false,
    strokeWidth: Dp = Dp.Unspecified,
    wavelength: Dp = 10.dp,
) {
    val sw0 = if (strokeWidth.isSpecified) strokeWidth else if (thick) 6.dp else 3.dp
    val phase = rememberWavePhase(1400)
    val transition = rememberInfiniteTransition(label = "circular_indeterminate")
    val rotation by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    val sweepAnim by transition.animateFloat(
        initialValue = 50f, targetValue = 250f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    Canvas(modifier) {
        val (sw, amp, radius) = circularMetrics(min(size.width, size.height), sw0, thick, density)
        if (radius <= 0f) return@Canvas
        val stroke = Stroke(width = sw, cap = StrokeCap.Round)
        val gapDeg = Math.toDegrees(((sw * 2f) / radius).toDouble()).toFloat()
        val start = rotation - 90f
        drawArcTrack(center, radius, start + sweepAnim + gapDeg, 360f - sweepAnim - gapDeg * 2f, trackColor, stroke)
        drawArcWave(center, radius, start, sweepAnim, amp, wavelength.toPx(), phase, color, stroke)
    }
}
