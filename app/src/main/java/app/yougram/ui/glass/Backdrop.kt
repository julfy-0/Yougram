package app.yougram.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.yougram.data.GlassSettings

/**
 * Состояние «фона для размытия».
 *
 * Идея: содержимое экрана (список чатов, сообщения...) рисуется как обычно, но параллельно
 * записывается в [layer]. Панели рисуют в своём фоне размытую копию этого слоя, сдвинутую
 * так, чтобы она совпадала с тем, что реально находится под панелью, и поверх — полупрозрачную подкраску.
 * Работает на Android 12+ (RenderEffect), то есть на всём, что поддерживает приложение.
 */
@Stable
class BackdropState(val layer: GraphicsLayer) {
    /** Позиция источника в координатах окна. */
    var sourceOffset by mutableStateOf(Offset.Zero)

    /** Радиус размытия (px), уже применённый к слою: не пересоздаём RenderEffect без причины. */
    internal var appliedBlurPx: Float = -1f
}

@Composable
fun rememberBackdropState(): BackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { BackdropState(layer) }
}

/** Помечает содержимое, которое должно просвечивать (размытым) под панелями. Сами панели в него класть нельзя. */
fun Modifier.backdropSource(state: BackdropState): Modifier = this
    .onGloballyPositioned { state.sourceOffset = it.positionInRoot() }
    .drawWithContent {
        state.layer.record {
            this@drawWithContent.drawContent()
        }
        drawContent()
    }

/** Фон панели: размытая копия того, что под ней, и поверх — подкраска с заданной плотностью. */
@Composable
fun Modifier.glass(
    state: BackdropState,
    settings: GlassSettings,
    shape: Shape,
    tint: Color = MaterialTheme.colorScheme.surface,
): Modifier {
    val density = LocalDensity.current
    var myOffset by remember { mutableStateOf(Offset.Zero) }

    SideEffect {
        val radiusPx = with(density) { settings.blurRadius.dp.toPx() }
        if (radiusPx != state.appliedBlurPx) {
            state.appliedBlurPx = radiusPx
            state.layer.renderEffect =
                if (radiusPx > 0.5f) BlurEffect(radiusPx, radiusPx, TileMode.Clamp) else null
        }
    }

    return this
        .onGloballyPositioned { myOffset = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            val delta = state.sourceOffset - myOffset
            translate(delta.x, delta.y) { drawLayer(state.layer) }
            drawRect(tint.copy(alpha = settings.opacity))
        }
}