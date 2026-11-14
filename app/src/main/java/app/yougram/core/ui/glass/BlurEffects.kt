package app.yougram.core.ui.glass

import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import app.yougram.core.settings.BlurType
import kotlin.math.sqrt

/**
 * Собирает [RenderEffect] нужного типа размытия. [radiusPx] — «сила» размытия в тех же единицах, что у
 * гауссова размытия (σ), поэтому при переключении типа общий вид панели остаётся сопоставимым.
 * [maxX], [maxY] — границы изображения: выборка за ними зажимается, края не темнеют.
 *
 * Kawase считается AGSL-шейдерами (Android 13+); на Android 12 возвращается гауссово размытие.
 */
fun blurRenderEffect(type: BlurType, radiusPx: Float, maxX: Float, maxY: Float): RenderEffect {
    val gaussian = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
    if (type == BlurType.Gaussian || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return gaussian
    return runCatching {
        when (type) {
            BlurType.Kawase -> kawase(radiusPx, maxX, maxY)
            BlurType.Gaussian -> gaussian
        }
    }.getOrDefault(gaussian)
}

/**
 * Поднимает насыщенность уже размытого фона (приём «жидкого стекла»: цвета под панелью становятся ярче,
 * как у стекла в iOS). Работает на Android 12+, шейдеры не нужны.
 */
fun saturatedEffect(inner: RenderEffect, saturation: Float): RenderEffect = runCatching {
    val m = android.graphics.ColorMatrix().apply { setSaturation(saturation) }
    PlatformRenderEffect.createColorFilterEffect(
        android.graphics.ColorMatrixColorFilter(m),
        inner.asAndroidRenderEffect(),
    ).asComposeRenderEffect()
}.getOrDefault(inner)

// ------------------------------------------------------------------------------------------------ Шейдеры

/**
 * Kawase (по urp-kawase-blur): центр + четыре диагонали на расстоянии [off] пикселей, среднее из 5 выборок.
 * Несколько проходов подряд с растущим смещением.
 */
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

private fun pass(agsl: String, maxX: Float, maxY: Float, setup: RuntimeShader.() -> Unit): PlatformRenderEffect {
    val shader = RuntimeShader(agsl)
    shader.setFloatUniform("maxXY", maxX, maxY)
    shader.setup()
    return PlatformRenderEffect.createRuntimeShaderEffect(shader, "content")
}

/** Применяет [next] поверх уже собранной цепочки [prev] (prev исполняется первой). */
private fun chain(prev: PlatformRenderEffect?, next: PlatformRenderEffect): PlatformRenderEffect =
    if (prev == null) next else PlatformRenderEffect.createChainEffect(next, prev)

private const val KAWASE_MIN_PASSES = 2
private const val KAWASE_MAX_PASSES = 15

/** Смещения проходов как в KawaseBlur.cs: 1.5, затем 0.5 + i, последний — 0.5 + passes - 1. */
private fun kawaseOffsets(passes: Int): List<Float> = buildList {
    add(1.5f)
    for (i in 1 until passes - 1) add(0.5f + i)
    add(0.5f + passes - 1f)
}

/** Подбирает число проходов так, чтобы σ получившегося размытия была не меньше [radiusPx]. */
private fun kawasePasses(radiusPx: Float): Int {
    for (n in KAWASE_MIN_PASSES..KAWASE_MAX_PASSES) {
        // Дисперсия по оси: диагональные выборки (4 из 5) дают off² каждая.
        val variance = kawaseOffsets(n).sumOf { (it * it).toDouble() } * 0.8
        if (sqrt(variance) >= radiusPx) return n
    }
    return KAWASE_MAX_PASSES
}

private fun kawase(radiusPx: Float, maxX: Float, maxY: Float): RenderEffect {
    var e: PlatformRenderEffect? = null
    for (o in kawaseOffsets(kawasePasses(radiusPx))) {
        e = chain(e, pass(KAWASE_AGSL, maxX, maxY) { setFloatUniform("off", o) })
    }
    return e!!.asComposeRenderEffect()
}
