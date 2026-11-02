package app.yougram.feature.chat.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Панель под полем ввода (эмодзи/стикеры/GIF): высота растёт вместе с [progress], содержимое «раскрывается»
 * сверху вниз, плавно проявляясь и слегка подъезжая — а не вылетает из-за нижнего края экрана.
 *
 * [progress] — лямбда, а не значение: она читается только в фазе layout, поэтому анимация не вызывает
 * перекомпозицию ни панели, ни самого чата (ни одного лишнего кадра композиции).
 */
@Composable
fun SlideUpPanel(progress: () -> Float, visible: Boolean, content: @Composable () -> Unit) {
    if (!visible) return
    Layout(
        content = content,
        modifier = Modifier.fillMaxWidth().clipToBounds(),
    ) { measurables, constraints ->
        val placeables = measurables.map {
            it.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        }
        val full = placeables.sumOf { it.height }
        val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
        val p = progress().coerceIn(0f, 1f)
        val height = (full * p).roundToInt()
        // Содержимое проявляется во второй половине раскрытия и подъезжает на небольшое расстояние.
        val appear = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
        val shift = ((1f - appear) * 28.dp.toPx()).roundToInt()
        layout(width, height) {
            var y = 0
            placeables.forEach {
                it.placeRelativeWithLayer(0, y + shift) { alpha = appear }
                y += it.height
            }
        }
    }
}
