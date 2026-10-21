package app.yougram.core.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph

/** Только плавные фигуры. Последняя = первая, поэтому цикл бесшовный. */
private val LoaderMorphs: List<Morph> by lazy {
    val s = listOf(
        MaterialShapes.Circle,
        MaterialShapes.Cookie6Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.SoftBurst,
        MaterialShapes.Flower,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Circle,
    )
    s.zipWithNext { a, b -> Morph(a, b) }
}

private fun Morph.fillPath(progress: Float, scale: Float, path: Path) {
    path.rewind()
    var first = true
    forEachCubic(progress) { b ->
        if (first) {
            path.moveTo(b.anchor0X * scale, b.anchor0Y * scale)
            first = false
        }
        path.cubicTo(
            b.control0X * scale, b.control0Y * scale,
            b.control1X * scale, b.control1Y * scale,
            b.anchor1X * scale, b.anchor1Y * scale,
        )
    }
    path.close()
}

/**
 * Индикатор из морфящихся фигур Material 3 Expressive (только плавные).
 * [progress] == null — бесконечная загрузка; иначе фигура проходит весь цикл за 0..1 и заканчивает кругом.
 */
@Composable
fun ShapeLoadingIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 56.dp,
) {
    val n = LoaderMorphs.size
    val inf = rememberInfiniteTransition(label = "shape_loader")
    val spin by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val loop by inf.animateFloat(
        0f, n.toFloat(),
        infiniteRepeatable(tween(n * 450, easing = LinearEasing), RepeatMode.Restart),
        label = "loop",
    )
    val path = remember { Path() }

    Canvas(modifier.size(size)) {
        val pos = if (progress != null) progress().coerceIn(0f, 1f) * n else loop
        val idx = pos.toInt().coerceAtMost(n - 1)
        val local = (pos - idx).coerceIn(0f, 1f)
        // Небольшая пауза на каждой фигуре, потом плавный переход.
        val t = FastOutSlowInEasing.transform(((local - 0.2f) / 0.6f).coerceIn(0f, 1f))

        LoaderMorphs[idx].fillPath(t, this.size.minDimension, path)
        rotate(spin, center) {
            drawPath(path, color)
        }
    }
}
