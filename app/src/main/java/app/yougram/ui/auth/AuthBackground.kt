package app.yougram.ui.auth

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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** Медленно дрейфующие размытые цветные пятна за стеклянной карточкой входа. */
@Composable
fun AuthBackground(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
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
    val alpha = if (dark) 0.42f else 0.5f

    Box(modifier.fillMaxSize().background(scheme.background)) {
        Canvas(Modifier.fillMaxSize().blur(72.dp)) {
            val w = size.width
            val h = size.height
            drawCircle(
                color = scheme.primary.copy(alpha = alpha),
                radius = w * 0.62f,
                center = Offset(w * (0.1f + 0.5f * a), h * (0.08f + 0.18f * b)),
            )
            drawCircle(
                color = scheme.tertiary.copy(alpha = alpha),
                radius = w * 0.55f,
                center = Offset(w * (0.95f - 0.5f * b), h * (0.55f + 0.2f * c)),
            )
            drawCircle(
                color = scheme.secondary.copy(alpha = alpha * 0.8f),
                radius = w * 0.5f,
                center = Offset(w * (0.2f + 0.4f * c), h * (0.95f - 0.15f * a)),
            )
        }
    }
}