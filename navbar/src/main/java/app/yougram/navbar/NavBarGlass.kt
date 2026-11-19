package app.yougram.navbar

import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
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
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Источник размытия: содержимое экрана записывается в [layer], навбар рисует его размытую копию.
 * Размытие работает на Android 12+; ниже — сплошная подкраска.
 */
@Stable
class BackdropState(val layer: GraphicsLayer, val small: GraphicsLayer) {
    var sourceOffset by mutableStateOf(Offset.Zero)
    internal var appliedKey: Any? = null
}

@Composable
fun rememberBackdropState(): BackdropState {
    val layer = rememberGraphicsLayer()
    val small = rememberGraphicsLayer()
    return remember(layer, small) { BackdropState(layer, small) }
}

private val blurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Помечает содержимое, которое должно просвечивать размытым под навбаром. Сам навбар внутрь класть нельзя. */
fun Modifier.backdropSource(state: BackdropState): Modifier {
    if (!blurSupported) return this
    return this
        .onGloballyPositioned { state.sourceOffset = it.positionInRoot() }
        .drawWithContent {
            state.layer.record {
                this@drawWithContent.drawContent()
            }
            val w = ceil(size.width / BLUR_DOWNSCALE).toInt().coerceAtLeast(1)
            val h = ceil(size.height / BLUR_DOWNSCALE).toInt().coerceAtLeast(1)
            state.small.record(this, layoutDirection, IntSize(w, h)) {
                scale(1f / BLUR_DOWNSCALE, Offset.Zero) { drawLayer(state.layer) }
            }
            drawContent()
        }
}

/** Стеклянный фон: размытая копия [state] + подкраска [tint]. При [state] == null — только подкраска. */
@Composable
fun Modifier.navGlass(
    state: BackdropState?,
    settings: NavBarGlassSettings,
    shape: Shape,
    tint: Color = Color.Unspecified,
): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    val resolvedTint = if (tint != Color.Unspecified) tint else surface
    val density = LocalDensity.current
    val metrics = LocalContext.current.resources.displayMetrics
    var myOffset by remember { mutableStateOf(Offset.Zero) }
    val blurOn = blurSupported && state != null && settings.blurRadius > 0.5f

    SideEffect {
        if (state != null) {
            val radiusPx = with(density) { settings.blurRadius.dp.toPx() }
            val smallW = ceil(metrics.widthPixels / BLUR_DOWNSCALE).toInt()
            val smallH = ceil(metrics.heightPixels / BLUR_DOWNSCALE).toInt()
            val key = if (blurOn) BlurKey(settings.kawase, radiusPx, smallW, smallH) else null
            if (key != state.appliedKey) {
                state.appliedKey = key
                state.small.renderEffect =
                    if (key != null) blurRenderEffect(key.kawase, radiusPx / BLUR_DOWNSCALE, smallW.toFloat(), smallH.toFloat()) else null
            }
        }
    }

    return this
        .onGloballyPositioned { myOffset = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            if (blurOn && state != null && settings.opacity < OPAQUE_THRESHOLD) {
                val layer = state.small
                if (!layer.isReleased && layer.size != IntSize.Zero) {
                    val delta = state.sourceOffset - myOffset
                    runCatching {
                        translate(delta.x, delta.y) {
                            scale(BLUR_DOWNSCALE, Offset.Zero) { drawLayer(layer) }
                        }
                    }
                }
            }
            val alpha = if (blurOn) settings.opacity else maxOf(settings.opacity, FALLBACK_MIN_ALPHA)
            drawRect(resolvedTint.copy(alpha = alpha))
        }
}

private data class BlurKey(val kawase: Boolean, val radiusPx: Float, val w: Int, val h: Int)

private const val BLUR_DOWNSCALE = 4f
private const val OPAQUE_THRESHOLD = 0.98f
private const val FALLBACK_MIN_ALPHA = 0.85f

private fun blurRenderEffect(kawase: Boolean, radiusPx: Float, maxX: Float, maxY: Float): RenderEffect {
    val gaussian = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
    if (!kawase || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return gaussian
    return runCatching { kawaseEffect(radiusPx, maxX, maxY) }.getOrDefault(gaussian)
}

private const val KAWASE_AGSL = """
uniform shader content;
uniform float off;
uniform float2 maxXY;
half4 main(float2 c) {
    half4 col = content.eval(clamp(c, float2(0.0), maxXY));
    col += content.eval(clamp(c + float2( off,  off), float2(0.0), maxXY));
    col += content.eval(clamp(c + float2( off, -off), float2(0.0), maxXY));
    col += content.eval(clamp(c + float2(-off,  off), float2(0.0), maxXY));
    col += content.eval(clamp(c + float2(-off, -off), float2(0.0), maxXY));
    return col / 5.0;
}
"""

private fun kawaseOffsets(passes: Int): List<Float> = buildList {
    add(1.5f)
    for (i in 1 until passes - 1) add(0.5f + i)
    add(0.5f + passes - 1f)
}

private fun kawasePasses(radiusPx: Float): Int {
    for (n in 2..15) {
        val variance = kawaseOffsets(n).sumOf { (it * it).toDouble() } * 0.8
        if (sqrt(variance) >= radiusPx) return n
    }
    return 15
}

private fun kawaseEffect(radiusPx: Float, maxX: Float, maxY: Float): RenderEffect {
    var e: PlatformRenderEffect? = null
    for (o in kawaseOffsets(kawasePasses(radiusPx))) {
        val shader = RuntimeShader(KAWASE_AGSL)
        shader.setFloatUniform("maxXY", maxX, maxY)
        shader.setFloatUniform("off", o)
        val pass = PlatformRenderEffect.createRuntimeShaderEffect(shader, "content")
        e = if (e == null) pass else PlatformRenderEffect.createChainEffect(pass, e)
    }
    return e!!.asComposeRenderEffect()
}
