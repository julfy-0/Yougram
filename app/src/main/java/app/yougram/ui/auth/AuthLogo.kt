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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Знак приложения: скруглённый треугольник на градиентной плитке с мягкой пульсацией. */
@Composable
fun AuthLogo(modifier: Modifier = Modifier, logoSize: Dp = 92.dp) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "authLogo")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "logoPulse",
    )
    val tile = RoundedCornerShape(logoSize * 0.3f)

    Box(
        modifier = modifier
            .size(logoSize)
            .scale(pulse)
            .shadow(24.dp, tile, ambientColor = scheme.primary, spotColor = scheme.primary)
            .clip(tile)
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(logoSize * 0.44f)) {
            val w = size.width
            val h = size.height
            val points = listOf(Offset(w * 0.5f, h * 0.04f), Offset(w * 0.96f, h * 0.92f), Offset(w * 0.04f, h * 0.92f))
            val radius = w * 0.18f
            val path = Path()
            for (i in points.indices) {
                val prev = points[(i + 2) % 3]
                val cur = points[i]
                val next = points[(i + 1) % 3]
                val toPrev = prev - cur
                val toNext = next - cur
                val start = cur + toPrev / toPrev.getDistance() * radius
                val end = cur + toNext / toNext.getDistance() * radius
                if (i == 0) path.moveTo(start.x, start.y) else path.lineTo(start.x, start.y)
                path.quadraticTo(cur.x, cur.y, end.x, end.y)
            }
            path.close()
            drawPath(path, Color.White)
        }
    }
}