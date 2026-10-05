package app.yougram.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.yougram.data.YougramBanner

/** Цвета баннеров: индекс совпадает с [YougramBanner.palette]. */
val BannerPalettes: List<Pair<Color, Color>> = listOf(
    Color(0xFF4FC3F7) to Color(0xFF3949AB),
    Color(0xFFFF8A65) to Color(0xFFD81B60),
    Color(0xFF81C784) to Color(0xFF00897B),
    Color(0xFFFFD54F) to Color(0xFFFB8C00),
    Color(0xFFBA68C8) to Color(0xFF5E35B1),
    Color(0xFFEF5350) to Color(0xFF6A1B9A),
    Color(0xFF26C6DA) to Color(0xFF1E88E5),
    Color(0xFFAED581) to Color(0xFF2E7D32),
    Color(0xFFF06292) to Color(0xFFFF8A65),
    Color(0xFF90A4AE) to Color(0xFF37474F),
    Color(0xFF64B5F6) to Color(0xFF00ACC1),
    Color(0xFFFFB74D) to Color(0xFFE53935),
    Color(0xFF4DB6AC) to Color(0xFF283593),
    Color(0xFFCE93D8) to Color(0xFFEC407A),
    Color(0xFF212121) to Color(0xFF5C6BC0),
    Color(0xFFFFF176) to Color(0xFF66BB6A),
)

val BannerPatternNames = listOf("Без узора", "Точки", "Полосы", "Круги", "Волны", "Плюсы")
val BannerShapeNames = listOf("Диагональ", "Вертикаль", "Горизонталь", "Сияние")

/** Баннер профиля: градиент плюс узор. Показывается только для пользователей Yougram. */
@Composable
fun ProfileBanner(banner: YougramBanner, modifier: Modifier = Modifier) {
    val (a, b) = BannerPalettes[banner.palette.coerceIn(0, BannerPalettes.lastIndex)]
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                val colors = listOf(a, b)
                val brush = when (banner.shape) {
                    1 -> Brush.verticalGradient(colors)
                    2 -> Brush.horizontalGradient(colors)
                    3 -> Brush.radialGradient(colors, center = Offset(size.width / 2f, 0f), radius = size.width)
                    else -> Brush.linearGradient(colors, Offset.Zero, Offset(size.width, size.height))
                }
                drawRect(brush)
                drawPattern(banner.pattern)
            },
    )
}

private fun DrawScope.drawPattern(pattern: Int) {
    val ink = Color.White.copy(alpha = 0.18f)
    val w = size.width
    val h = size.height
    val step = 28.dp.toPx()
    when (pattern) {
        1 -> { // точки в шахматном порядке
            var y = step / 2f
            var row = 0
            while (y < h) {
                var x = if (row % 2 == 0) step / 2f else step
                while (x < w) {
                    drawCircle(ink, radius = 3.dp.toPx(), center = Offset(x, y))
                    x += step
                }
                y += step
                row++
            }
        }
        2 -> { // диагональные полосы
            var x = -h
            while (x < w) {
                drawLine(ink, Offset(x, h), Offset(x + h, 0f), strokeWidth = 6.dp.toPx())
                x += step
            }
        }
        3 -> { // круги от правого верхнего угла
            val center = Offset(w * 0.8f, h * 0.3f)
            var r = step
            while (r < w * 1.2f) {
                drawCircle(ink, radius = r, center = center, style = Stroke(2.dp.toPx()))
                r += step
            }
        }
        4 -> { // волны
            val amp = 6.dp.toPx()
            val period = 48.dp.toPx()
            var y = step
            while (y < h + step) {
                val path = Path().apply {
                    moveTo(0f, y)
                    var x = 0f
                    while (x < w) {
                        quadraticTo(x + period / 4f, y - amp * 2f, x + period / 2f, y)
                        quadraticTo(x + period * 3f / 4f, y + amp * 2f, x + period, y)
                        x += period
                    }
                }
                drawPath(path, ink, style = Stroke(2.dp.toPx()))
                y += step
            }
        }
        5 -> { // плюсы
            val gap = step * 1.4f
            val arm = 6.dp.toPx()
            var y = gap / 2f
            while (y < h) {
                var x = gap / 2f
                while (x < w) {
                    drawLine(ink, Offset(x - arm, y), Offset(x + arm, y), strokeWidth = 2.dp.toPx())
                    drawLine(ink, Offset(x, y - arm), Offset(x, y + arm), strokeWidth = 2.dp.toPx())
                    x += gap
                }
                y += gap
            }
        }
    }
}
