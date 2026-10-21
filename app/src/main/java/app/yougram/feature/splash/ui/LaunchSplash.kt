package app.yougram.feature.splash.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.MaterialTheme
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import app.yougram.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Длительность заставки при запуске приложения. */
private const val SplashMillis = 2000

private val HalloweenOrange = Color(0xFFFF8A1F)
private val HalloweenBgTop = Color(0xFF1B0A2E)
private val HalloweenBgBottom = Color(0xFF09050F)

/** Падающая частичка: тыква или паутинка. */
internal class Faller(
    val x: Float,          // 0..1 по ширине
    val size: Float,       // в dp
    val speed: Float,      // доля высоты экрана в секунду
    val offset: Float,     // начальный сдвиг 0..1
    val swayAmp: Float,    // амплитуда покачивания в dp
    val swayFreq: Float,
    val phase: Float,
    val spin: Float,       // градусов в секунду
    val isWeb: Boolean,
)

private fun makeFallers(): List<Faller> {
    val r = Random(31)
    return List(26) { i ->
        val web = i % 3 == 0
        Faller(
            x = r.nextFloat(),
            size = if (web) 26f + r.nextFloat() * 22f else 12f + r.nextFloat() * 16f,
            speed = if (web) 0.10f + r.nextFloat() * 0.08f else 0.14f + r.nextFloat() * 0.14f,
            offset = r.nextFloat(),
            swayAmp = 8f + r.nextFloat() * 16f,
            swayFreq = 0.6f + r.nextFloat() * 0.9f,
            phase = r.nextFloat() * (2f * PI.toFloat()),
            spin = (r.nextFloat() - 0.5f) * if (web) 30f else 90f,
            isWeb = web,
        )
    }
}

/**
 * Заставка при запуске. Пока идёт хэллоуинский ивент — хэллоуинская,
 * потом возвращается обычная (иконка приложения и волнистая полоска).
 */
@Composable
fun LaunchSplash(onFinished: () -> Unit) {
    if (HalloweenEvent.isActive()) HalloweenSplash(onFinished) else ClassicSplash(onFinished)
}

/** Обычная заставка: иконка приложения и морфящаяся фигура (M3 ContainedLoadingIndicator), показывается 2 секунды. */
@Composable
private fun ClassicSplash(onFinished: () -> Unit) {
    val context = LocalContext.current
    val icon = remember {
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap(512, 512)?.asImageBitmap()
    }
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(SplashMillis, easing = LinearEasing))
        alpha.animateTo(0f, tween(350))
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(alpha.value)
            .background(MaterialTheme.colorScheme.background)
            // Пока идёт заставка, нажатия не проходят к экрану под ней.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = "Yougram",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(144.dp).clip(CircleShape),
                )
            }
            // Официальный M3 Expressive индикатор: морфящаяся фигура в круглом контейнере.
            ContainedLoadingIndicator(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                indicatorColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Заставка в тематике Хэллоуина: иконка-бейдж, падающие тыквы и паутинки, волнистая полоска прогресса. */
@Composable
private fun HalloweenSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val fallers = remember { makeFallers() }
    var timeSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(SplashMillis, easing = LinearEasing))
        alpha.animateTo(0f, tween(350))
        onFinished()
    }

    // Время для анимации падающих частичек.
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { timeSec = (it - start) / 1_000_000_000f }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(alpha.value)
            .background(Brush.verticalGradient(listOf(HalloweenBgTop, HalloweenBgBottom)))
            // Пока идёт заставка, нажатия не проходят к экрану под ней.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        // Оранжевое свечение за иконкой.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(HalloweenOrange.copy(alpha = 0.32f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.62f,
                ),
                radius = size.minDimension * 0.62f,
                center = center,
            )
        }

        // Падающие тыквы и паутинки.
        Canvas(Modifier.fillMaxSize()) {
            val t = timeSec
            fallers.forEach { f ->
                val s = f.size.dp.toPx()
                val travel = size.height + s * 3f
                val p = (t * f.speed + f.offset) % 1f
                val y = p * travel - s * 1.5f
                val x = f.x * size.width + sin(t * f.swayFreq + f.phase) * f.swayAmp.dp.toPx()
                translate(x, y) {
                    rotate(degrees = f.phase * 20f + t * f.spin, pivot = Offset.Zero) {
                        if (f.isWeb) drawCobweb(s) else drawPumpkin(s)
                    }
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Image(
                painter = painterResource(R.drawable.ic_badge_halloween),
                contentDescription = "Yougram",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(144.dp).clip(CircleShape),
            )
            ContainedLoadingIndicator(
                containerColor = HalloweenOrange.copy(alpha = 0.22f),
                indicatorColor = HalloweenOrange,
            )
        }
    }
}

/** Маленькая тыква с вырезанным лицом; центр в (0,0), ширина [s]. */
internal fun DrawScope.drawPumpkin(s: Float) {
    val h = s * 0.84f
    val dark = Color(0xFFE0610A)
    val mid = Color(0xFFFF8A1F)
    val light = Color(0xFFFFA64D)
    // Дольки тыквы.
    drawOval(dark, Offset(-s * 0.5f, -h / 2), Size(s * 0.6f, h))
    drawOval(dark, Offset(-s * 0.1f, -h / 2), Size(s * 0.6f, h))
    drawOval(mid, Offset(-s * 0.32f, -h / 2), Size(s * 0.64f, h))
    drawOval(light, Offset(-s * 0.14f, -h / 2), Size(s * 0.28f, h))
    // Хвостик.
    drawRect(Color(0xFF3C7A2A), Offset(-s * 0.06f, -h / 2 - s * 0.16f), Size(s * 0.12f, s * 0.2f))

    val face = Color(0xFF2A1200)
    val eye = Path().apply {
        moveTo(-s * 0.26f, -s * 0.02f); lineTo(-s * 0.10f, -s * 0.02f); lineTo(-s * 0.18f, -s * 0.16f); close()
        moveTo(s * 0.10f, -s * 0.02f); lineTo(s * 0.26f, -s * 0.02f); lineTo(s * 0.18f, -s * 0.16f); close()
    }
    drawPath(eye, face)
    val mouth = Path().apply {
        moveTo(-s * 0.26f, s * 0.10f)
        lineTo(-s * 0.13f, s * 0.20f)
        lineTo(-s * 0.04f, s * 0.12f)
        lineTo(s * 0.04f, s * 0.20f)
        lineTo(s * 0.13f, s * 0.12f)
        lineTo(s * 0.26f, s * 0.10f)
        lineTo(s * 0.18f, s * 0.28f)
        lineTo(-s * 0.18f, s * 0.28f)
        close()
    }
    drawPath(mouth, face)
}

/** Маленькая паутинка: лучи, провисающие кольца и ниточка вверх; радиус [s]/2. */
internal fun DrawScope.drawCobweb(s: Float, color: Color = Color.White.copy(alpha = 0.6f)) {
    val r = s / 2f
    val spokes = 8
    val rings = 3
    val stroke = Stroke(width = 1.dp.toPx())
    fun pt(i: Int, rad: Float): Offset {
        val a = (2.0 * PI * i / spokes).toFloat()
        return Offset(cos(a) * rad, sin(a) * rad)
    }
    // Ниточка, на которой падает паутинка.
    drawLine(color.copy(alpha = color.alpha * 0.6f), Offset(0f, -r), Offset(0f, -r * 3f), strokeWidth = 1.dp.toPx())
    // Лучи.
    for (i in 0 until spokes) drawLine(color, Offset.Zero, pt(i, r), strokeWidth = 1.dp.toPx())
    // Кольца с провисанием между лучами.
    for (k in 1..rings) {
        val rad = r * k / rings
        val path = Path()
        for (i in 0 until spokes) {
            val a = pt(i, rad)
            val b = pt(i + 1, rad)
            val mid = Offset((a.x + b.x) / 2f * 0.80f, (a.y + b.y) / 2f * 0.80f)
            if (i == 0) path.moveTo(a.x, a.y)
            path.quadraticTo(mid.x, mid.y, b.x, b.y)
        }
        drawPath(path, color, style = stroke)
    }
}