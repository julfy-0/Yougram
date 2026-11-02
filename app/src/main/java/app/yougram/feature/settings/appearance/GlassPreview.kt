package app.yougram.feature.settings.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.core.settings.GlassSettings
import app.yougram.core.ui.glass.LocalGlass
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.glass
import app.yougram.core.ui.glass.rememberBackdropState
import kotlin.math.roundToInt

/**
 * Предпросмотр панели: рисуется тем же [glass], что и настоящие панели, поэтому тип размытия
 * (Гаусс / Kawase), его радиус и плотность выглядят ровно так же, как в приложении.
 */
@Composable
fun GlassPreview(blurRadius: Float, opacity: Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val user = LocalGlass.current
    val backdrop = rememberBackdropState()
    val panel = remember(blurRadius, opacity, user.blurType) {
        GlassSettings(
            blurRadius = blurRadius,
            opacity = opacity,
            blurType = user.blurType,
        )
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(144.dp)
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainerLowest)
            .clearAndSetSemantics {
                contentDescription =
                    tr("Предпросмотр панели: размытие {0}, плотность {1} процентов", blurRadius.roundToInt(), (opacity * 100).roundToInt())
            },
    ) {
        PreviewBackdrop(Modifier.matchParentSize().backdropSource(backdrop))
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .fillMaxWidth()
                .height(64.dp)
                .glass(backdrop, panel, RoundedCornerShape(24.dp)),
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
