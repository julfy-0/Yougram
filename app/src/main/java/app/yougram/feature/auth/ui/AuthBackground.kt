@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.auth.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.rememberReduceMotion
import app.yougram.feature.splash.ui.HalloweenEvent
import app.yougram.feature.splash.ui.halloweenFall

/**
 * Фон за стеклянной карточкой входа. Пока идёт хэллоуинский ивент — тыквенный фон с падающими тыквами,
 * потом крупные фигуры Material 3 Expressive (MaterialShapes), медленно вращающиеся и дрейфующие.
 */
@Composable
fun AuthBackground(modifier: Modifier = Modifier) {
    if (HalloweenEvent.isActive()) {
        HalloweenAuthBackground(modifier)
        return
    }
    // Слабое железо или «Удалить анимации»: фигуры стоят на месте, бесконечный transition не создаётся.
    ShapesBackground(modifier, animated = !rememberReduceMotion())
}

/** Хэллоуинский фон: тёмный фиолетовый (тёмная тема) или тёплый кремовый (светлая), оранжевое свечение, падающие тыквы. */
@Composable
private fun HalloweenAuthBackground(modifier: Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val top = if (dark) Color(0xFF1B0A2E) else Color(0xFFFFF3E3)
    val bottom = if (dark) Color(0xFF09050F) else Color(0xFFFFD9AE)
    val orange = Color(0xFFFF8A1F)
    val reduceMotion = rememberReduceMotion()

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            // Свечение рисуется до частиц, поэтому тыквы лежат поверх него.
            .drawBehind {
                val w = size.width
                val h = size.height
                val glowAlpha = if (dark) 0.34f else 0.45f
                fun glow(c: Offset, r: Float, color: Color) = drawCircle(
                    brush = Brush.radialGradient(listOf(color.copy(alpha = glowAlpha), Color.Transparent), center = c, radius = r),
                    radius = r,
                    center = c,
                )
                glow(Offset(w * 0.2f, h * 0.12f), w * 0.8f, orange)
                glow(Offset(w * 0.85f, h * 0.7f), w * 0.7f, Color(0xFF8E3BFF))
                glow(Offset(w * 0.3f, h * 0.98f), w * 0.65f, orange)
            }
            .then(if (reduceMotion) Modifier else Modifier.halloweenFall(speedScale = 1.2f, darkBackground = dark)),
    )
}

private class BgShape(
    val shape: Shape,
    val color: Color,
    /** Диаметр как доля ширины экрана. */
    val size: Float,
    /** Центр как доля ширины / высоты экрана. */
    val x: Float,
    val y: Float,
    /** Целое число оборотов за цикл (знак — направление), поэтому цикл замыкается без рывка. */
    val spin: Int,
    val phase: Float,
)

/** Фон из фигур M3 Expressive в контейнерных цветах темы: крупные, полупрозрачные, медленно вращаются. */
@Composable
private fun ShapesBackground(modifier: Modifier, animated: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val a = if (dark) 0.55f else 0.7f
    val sCookie12Sided = MaterialShapes.Cookie12Sided.toShape()
    val sFlower = MaterialShapes.Flower.toShape()
    val sClover4Leaf = MaterialShapes.Clover4Leaf.toShape()
    val sSoftBurst = MaterialShapes.SoftBurst.toShape()
    val sSunny = MaterialShapes.Sunny.toShape()
    val sPentagon = MaterialShapes.Pentagon.toShape()
    val shapes = remember(scheme) {
        listOf(
            BgShape(sCookie12Sided, scheme.primaryContainer.copy(alpha = a), 0.95f, 0.12f, 0.10f, 1, 0.0f),
            BgShape(sFlower, scheme.tertiaryContainer.copy(alpha = a), 0.80f, 0.95f, 0.38f, -1, 0.3f),
            BgShape(sClover4Leaf, scheme.secondaryContainer.copy(alpha = a), 0.70f, 0.05f, 0.62f, 1, 0.6f),
            BgShape(sSoftBurst, scheme.primaryContainer.copy(alpha = a * 0.8f), 0.60f, 0.88f, 0.86f, -1, 0.15f),
            BgShape(sSunny, scheme.tertiaryContainer.copy(alpha = a * 0.7f), 0.38f, 0.62f, 0.04f, 1, 0.8f),
            BgShape(sPentagon, scheme.secondaryContainer.copy(alpha = a * 0.7f), 0.34f, 0.40f, 0.97f, -1, 0.45f),
        )
    }
    val t = if (animated) {
        val transition = rememberInfiniteTransition(label = "authShapes")
        val v by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(90_000, easing = LinearEasing)),
            label = "authShapesT",
        )
        v
    } else {
        0f
    }

    BoxWithConstraints(modifier.fillMaxSize().background(scheme.background)) {
        val w = maxWidth
        val h = maxHeight
        shapes.forEach { sp ->
            val d = w * sp.size
            Box(
                Modifier
                    .offset(x = w * sp.x - d / 2, y = h * sp.y - d / 2)
                    .size(d)
                    .graphicsLayer {
                        rotationZ = t * 360f * sp.spin
                        // Лёгкий дрейф по кругу, тоже замкнутый в цикл.
                        val ang = (t * 3f + sp.phase) * 2f * Math.PI.toFloat()
                        translationX = kotlin.math.sin(ang) * 18f * density
                        translationY = kotlin.math.cos(ang) * 18f * density
                    }
                    .clip(sp.shape)
                    .background(sp.color),
            )
        }
    }
}