@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.settings.about

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yougram.R
import app.yougram.core.i18n.tr
import kotlin.math.max

/*
 * Баннеры Yougram, нарисованные кодом (вместо PNG):
 *  - [YougramCodeBanner] — обычный баннер «О приложении»: фигуры на тёмном фоне и большой значок по центру;
 *  - [YougramOtaBanner]  — баннер обновления: значок, слово «Yougram» и плашка «OTA».
 *
 * Оба рисуются в «дизайн-пространстве» 1000×400 (как и прежние картинки, 2.5:1) и масштабируются как
 * ContentScale.Crop: при другой пропорции лишнее обрезается по краям, поэтому всё важное лежит ближе к центру.
 */

/**
 * Оттенок акцента темы: [f] — яркость 0..1 (0 — почти чёрный, 0.5 — сам акцент, 1 — белый).
 * Так баннер остаётся таким же «серым по яркости», но окрашивается в акцентный цвет пользователя.
 */
private fun shade(accent: Color, f: Float): Color =
    if (f < 0.5f) lerp(Color(0xFF0C0C0C), accent, f * 2f) else lerp(accent, Color.White, (f - 0.5f) * 2f)
private const val DesignW = 1000f
private const val DesignH = 400f

/** Фигура фона: центр и размер заданы в дизайн-пространстве 1000×400, угол — в градусах. */
private class BannerShape(
    val shape: Shape,
    val color: Color,
    val cx: Float,
    val cy: Float,
    val size: Float,
    val rotation: Float = 0f,
)

/** Общая «сцена»: тёмный градиент, рассыпанные фигуры и [content], выровненный по центру. */
@Composable
private fun BannerScene(
    description: String,
    modifier: Modifier,
    content: @Composable (unit: Dp) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val tertiary = scheme.tertiary
    // toShape() — @Composable, поэтому формы берём заранее, а список фигур запоминаем.
    val triangle = MaterialShapes.Triangle.toShape()
    val square = MaterialShapes.Square.toShape()
    val gem = MaterialShapes.Gem.toShape()
    val circle = CircleShape
    val sunny = MaterialShapes.Sunny.toShape()
    val cookie = MaterialShapes.Cookie12Sided.toShape()
    val bgShapes = remember(triangle, square, gem, sunny, cookie, primary, tertiary) {
        listOf(
            BannerShape(triangle, shade(primary, 0.9f), 35f, 25f, 78f, -18f),
            BannerShape(square, shade(tertiary, 0.72f), 220f, 30f, 82f, -16f),
            BannerShape(triangle, shade(primary, 0.53f), 585f, 28f, 92f, 160f),
            BannerShape(gem, shade(primary, 0.4f), 833f, 30f, 128f, 8f),
            BannerShape(circle, shade(primary, 0.71f), 115f, 187f, 78f),
            BannerShape(triangle, shade(primary, 0.29f), 982f, 172f, 56f, 200f),
            BannerShape(triangle, shade(tertiary, 0.48f), 342f, 362f, 92f, -35f),
            BannerShape(sunny, shade(primary, 0.34f), 785f, 372f, 158f, 12f),
            BannerShape(cookie, shade(tertiary, 0.69f), 62f, 392f, 62f),
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(shade(primary, 0.16f), shade(primary, 0.08f)), Offset.Zero, Offset.Infinite))
            .semantics { contentDescription = description },
    ) {
        // Один пиксель дизайн-пространства в dp; max() даёт поведение Crop.
        val unit = max(maxWidth.value / DesignW, maxHeight.value / DesignH).dp
        val originX = (maxWidth - unit * DesignW) / 2
        val originY = (maxHeight - unit * DesignH) / 2

        bgShapes.forEach { s ->
            Box(
                Modifier
                    .offset(originX + unit * (s.cx - s.size / 2), originY + unit * (s.cy - s.size / 2))
                    .requiredSize(unit * s.size)
                    .rotate(s.rotation)
                    .clip(s.shape)
                    .background(s.color),
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content(unit) }
    }
}

/**
 * Волнистый круг значка: 12 мягких волн, r(θ) = R0 + A·cos(12θ). Параметры сняты с эталонной иконки 192×192
 * (средний радиус 91.5, размах волны ±4.9; максимум касается края квадрата).
 */
private object WavyBadgeShape : Shape {
    private const val Waves = 12
    private const val Steps = 360
    private const val R0 = 91.5f / 192f
    private const val Amp = 4.9f / 192f

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val d = minOf(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path()
        for (i in 0 until Steps) {
            val th = 2.0 * Math.PI * i / Steps
            val r = (R0 + Amp * kotlin.math.cos(Waves * th).toFloat()) * d
            val x = cx + (r * kotlin.math.cos(th)).toFloat()
            val y = cy + (r * kotlin.math.sin(th)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * Знак Yougram: волнистый «печенюшечный» круг с серым градиентом и белым скруглённым треугольником.
 * [diameter] — диаметр знака.
 */
@Composable
private fun YougramMark(diameter: Dp, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .requiredSize(diameter)
            .clip(WavyBadgeShape)
            .background(
                // Диагональ сверху-слева вниз-вправо: акцент → тот же цвет, сдвинутый по тону (как синий → фиолетовый).
                Brush.linearGradient(
                    listOf(lerp(accent, Color.White, 0.10f), hueShift(accent, 28f)),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            // Вершины (до скругления) сняты с эталонной иконки 192×192: острый угол справа-сверху, тупее — слева и снизу.
            val pts = listOf(
                Offset(43.5f, 90.5f),
                Offset(145.8f, 48.1f),
                Offset(99.1f, 146.1f),
            ).map { Offset(it.x / 192f * w, it.y / 192f * w) }
            val tip = pts.minOf { it.y }
            val bottom = pts.maxOf { it.y }
            drawPath(
                roundedPolygon(pts, radius = w * 0.0625f),
                Brush.verticalGradient(
                    listOf(Color.White, lerp(Color.White, accent, 0.18f)),
                    startY = tip,
                    endY = bottom,
                ),
            )
        }
    }
}

/** Тот же цвет, но с тоном, сдвинутым на [degrees] по цветовому кругу; насыщенность и яркость сохраняются. */
private fun hueShift(color: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Многоугольник по вершинам [pts]: каждый угол скруглён дугой окружности радиуса [radius] (кубическая аппроксимация). */
private fun roundedPolygon(pts: List<Offset>, radius: Float): Path {
    val path = Path()
    val n = pts.size
    for (i in 0 until n) {
        val prev = pts[(i + n - 1) % n]
        val cur = pts[i]
        val next = pts[(i + 1) % n]
        val toPrev = prev - cur
        val toNext = next - cur
        val u1 = toPrev / toPrev.getDistance()
        val u2 = toNext / toNext.getDistance()
        // Угол при вершине и длина касательной от вершины до начала дуги.
        val theta = kotlin.math.acos((u1.x * u2.x + u1.y * u2.y).coerceIn(-1f, 1f))
        val tan = kotlin.math.tan(theta / 2f)
        val t = minOf(radius / tan, toPrev.getDistance() / 2f, toNext.getDistance() / 2f)
        val r = t * tan
        val p1 = cur + u1 * t
        val p2 = cur + u2 * t
        // Ручки кубической Безье для дуги с углом поворота (π − θ).
        val h = 4f / 3f * kotlin.math.tan((Math.PI.toFloat() - theta) / 4f) * r
        if (i == 0) path.moveTo(p1.x, p1.y) else path.lineTo(p1.x, p1.y)
        path.cubicTo(
            p1.x - u1.x * h, p1.y - u1.y * h,
            p2.x - u2.x * h, p2.y - u2.y * h,
            p2.x, p2.y,
        )
    }
    path.close()
    return path
}

/** Обычный баннер «О приложении»: большой знак по центру на фоне из фигур. */
@Composable
internal fun YougramCodeBanner(modifier: Modifier = Modifier) {
    BannerScene(description = tr("Баннер Yougram"), modifier = modifier) { unit ->
        YougramMark(diameter = unit * 180f)
    }
}

/** Баннер обновления (OTA): знак, надпись «Yougram» и плашка «OTA». */
@Composable
internal fun YougramOtaBanner(modifier: Modifier = Modifier) {
    BannerScene(description = tr("Баннер обновления"), modifier = modifier) { unit ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(unit * 18f),
        ) {
            YougramMark(diameter = unit * 62f)
            // Размер в sp без учёта масштаба шрифта системы: баннер должен выглядеть одинаково у всех.
            Text(
                text = "Yougram",
                style = TextStyle(
                    color = Color.White,
                    fontSize = (unit.value * 64f).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                ),
                maxLines = 1,
                softWrap = false,
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(shade(MaterialTheme.colorScheme.primary, 0.40f))
                    .padding(horizontal = unit * 22f, vertical = unit * 6f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "OTA",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = (unit.value * 40f).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily(Font(R.font.google_sans_flex, FontWeight.Bold)),
                        letterSpacing = (unit.value * 1.5f).sp,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}