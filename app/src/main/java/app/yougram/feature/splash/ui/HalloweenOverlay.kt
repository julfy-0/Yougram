package app.yougram.feature.splash.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private fun makeOverlayFallers(): List<Faller> {
    val r = Random(77)
    return List(9) { i ->
        val web = i % 3 == 0
        Faller(
            x = r.nextFloat(),
            size = if (web) 16f + r.nextFloat() * 10f else 8f + r.nextFloat() * 7f,
            speed = if (web) 0.05f + r.nextFloat() * 0.04f else 0.07f + r.nextFloat() * 0.06f,
            offset = r.nextFloat(),
            swayAmp = 6f + r.nextFloat() * 10f,
            swayFreq = 0.5f + r.nextFloat() * 0.6f,
            phase = r.nextFloat() * (2f * PI.toFloat()),
            spin = (r.nextFloat() - 0.5f) * if (web) 20f else 60f,
            isWeb = web,
        )
    }
}

/**
 * Хэллоуинские частички (мелкие тыквы и паутинки), нарисованные ПОД содержимым и обрезанные по границам элемента.
 * Ставить сразу после фона/стекла: частицы лягут на него, а всё остальное окажется поверх.
 * После окончания ивента ([HalloweenEvent.isActive]) ничего не делает.
 *
 * @param speedScale ускоряет падение (для низких элементов вроде верхней панели).
 * @param darkBackground тёмный ли фон под частицами: от этого зависит цвет паутинки.
 *   По умолчанию (null) определяется по теме приложения.
 */
fun Modifier.halloweenFall(speedScale: Float = 1f, darkBackground: Boolean? = null): Modifier {
    if (!HalloweenEvent.isActive()) return this
    return composed {
        val fallers = remember { makeOverlayFallers() }
        val layerPaint = remember { Paint().apply { alpha = 0.55f } }
        var timeSec by remember { mutableFloatStateOf(0f) }
        val dark = darkBackground ?: (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        val webColor = if (dark) Color.White.copy(alpha = 0.6f) else Color(0xFF4A3A5C).copy(alpha = 0.6f)

        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) {
                // ~30 кадров/с вместо 60: частицы медленные, а панель висит на экране постоянно.
                delay(33)
                withFrameNanos { timeSec = (it - start) / 1_000_000_000f }
            }
        }

        drawBehind {
            val t = timeSec
            // saveLayer с границами элемента заодно обрезает частицы по краям.
            drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height), layerPaint)
            fallers.forEach { f ->
                val s = f.size.dp.toPx()
                val travel = size.height + s * 3f
                val p = (t * f.speed * speedScale + f.offset) % 1f
                val y = p * travel - s * 1.5f
                val x = f.x * size.width + sin(t * f.swayFreq + f.phase) * f.swayAmp.dp.toPx()
                translate(x, y) {
                    rotate(degrees = f.phase * 20f + t * f.spin, pivot = Offset.Zero) {
                        if (f.isWeb) drawCobweb(s, webColor) else drawPumpkin(s)
                    }
                }
            }
            drawContext.canvas.restore()
        }
    }
}