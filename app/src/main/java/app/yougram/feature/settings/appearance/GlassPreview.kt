package app.yougram.feature.settings.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.DeviceTier
import app.yougram.core.ui.rememberDeviceTier
import kotlin.math.roundToInt

/**
 * Предпросмотр панели: цветные фигуры, нижняя половина которых показана «через стекло» с выбранным радиусом
 * размытия и плотностью. Показывает размытие по Гауссу; на слабых устройствах размытие в предпросмотре отключено.
 */
@Composable
fun GlassPreview(blurRadius: Float, opacity: Float, modifier: Modifier = Modifier) {
    val low = rememberDeviceTier() == DeviceTier.Low
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(144.dp)
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerLowest)
            .clearAndSetSemantics {
                contentDescription =
                    "Предпросмотр панели: размытие ${blurRadius.roundToInt()}, плотность ${(opacity * 100).roundToInt()} процентов"
            },
    ) {
        PreviewBackdrop(Modifier.matchParentSize())
        if (!low && blurRadius > 0f) {
            PreviewBackdrop(
                Modifier
                    .matchParentSize()
                    .drawWithContent { clipRect(top = size.height * 0.5f) { this@drawWithContent.drawContent() } }
                    .blur(blurRadius.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(scheme.surface.copy(alpha = opacity.coerceIn(0f, 1f))),
            contentAlignment = Alignment.Center,
        ) {
            Text("Так выглядит панель", style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.onSurface)
        }
    }
}

@Composable
private fun PreviewBackdrop(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val a = scheme.primary
    val b = scheme.tertiary
    val c = scheme.secondary
    Canvas(modifier) {
        drawCircle(a, radius = size.height * 0.5f, center = Offset(size.width * 0.2f, size.height * 0.35f))
        drawCircle(b, radius = size.height * 0.42f, center = Offset(size.width * 0.55f, size.height * 0.62f))
        drawRoundRect(
            c,
            topLeft = Offset(size.width * 0.72f, size.height * 0.08f),
            size = Size(size.width * 0.24f, size.height * 0.55f),
            cornerRadius = CornerRadius(size.height * 0.2f),
        )
    }
}