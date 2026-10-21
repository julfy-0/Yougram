package app.yougram.feature.auth.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
 * потом обычные медленно дрейфующие размытые цветные пятна.
 */
@Composable
fun AuthBackground(modifier: Modifier = Modifier) {
    if (HalloweenEvent.isActive()) {
        HalloweenAuthBackground(modifier)
        return
    }
    val scheme = MaterialTheme.colorScheme
    val alpha = if (scheme.background.luminance() < 0.5f) 0.42f else 0.5f
    // Слабое железо или «Удалить анимации»: статичные градиенты, бесконечный transition даже не создаётся.
    if (rememberReduceMotion()) {
        StaticBlobs(modifier, alpha)
    } else {
        AnimatedBlobs(modifier, alpha)
    }
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

@Composable
private fun StaticBlobs(modifier: Modifier, alpha: Float) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().background(scheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun blob(color: Color, r: Float, c: Offset) = drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = c, radius = r),
                radius = r,
                center = c,
            )
            blob(scheme.primary, w * 0.8f, Offset(w * 0.25f, h * 0.15f))
            blob(scheme.tertiary, w * 0.75f, Offset(w * 0.8f, h * 0.65f))
            blob(scheme.secondary, w * 0.7f, Offset(w * 0.3f, h * 0.95f))
        }
    }
}

@Composable
private fun AnimatedBlobs(modifier: Modifier, alpha: Float) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "authBackground")
    val a by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "blobA",
    )
    val b by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(21000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "blobB",
    )
    val c by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(27000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "blobC",
    )
    Box(modifier.fillMaxSize().background(scheme.background)) {
        // Радиальные градиенты вместо blur(72.dp): выглядит так же, но не гоняет размытие на весь экран каждый кадр.
        Canvas(Modifier.fillMaxSize()) {
            fun blob(color: Color, r: Float, c: Offset) = drawCircle(
                brush = Brush.radialGradient(listOf(color, Color.Transparent), center = c, radius = r * 1.5f),
                radius = r * 1.5f,
                center = c,
            )
            val w = size.width
            val h = size.height
            blob(scheme.primary.copy(alpha = alpha), w * 0.62f, Offset(w * (0.1f + 0.5f * a), h * (0.08f + 0.18f * b)))
            blob(scheme.tertiary.copy(alpha = alpha), w * 0.55f, Offset(w * (0.95f - 0.5f * b), h * (0.55f + 0.2f * c)))
            blob(scheme.secondary.copy(alpha = alpha * 0.8f), w * 0.5f, Offset(w * (0.2f + 0.4f * c), h * (0.95f - 0.15f * a)))
        }
    }
}