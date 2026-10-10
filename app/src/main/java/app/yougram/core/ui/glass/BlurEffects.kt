package app.yougram.core.ui.glass

import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import app.yougram.core.settings.BlurType

/**
 * Собирает [RenderEffect] нужного типа размытия. [radiusPx] — «сила» размытия в тех же единицах, что у
 * гауссова размытия (σ), поэтому при переключении типа общий вид панели остаётся сопоставимым.
 * [maxX], [maxY] — границы изображения: выборка за ними зажимается, края не темнеют.
 *
 * Не-гауссовы типы считаются AGSL-шейдерами (Android 13+); на Android 12 возвращается гауссово размытие.
 */
fun blurRenderEffect(type: BlurType, radiusPx: Float, maxX: Float, maxY: Float): RenderEffect {
    val gaussian = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
    if (type == BlurType.Gaussian || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return gaussian
    return runCatching {
        when (type) {
            BlurType.Box -> box(radiusPx, maxX, maxY)
            BlurType.Kawase -> kawase(radiusPx, maxX, maxY)
            BlurType.Bokeh -> bokeh(radiusPx, maxX, maxY)
            BlurType.Gaussian -> gaussian
        }
    }.getOrDefault(gaussian)
}

// ------------------------------------------------------------------------------------------------ Шейдеры

/** Box: усреднение по отрезку; два раздельных прохода (по X и по Y), оба повторены дважды, чтобы убрать «призраков» от разреженных выборок. */
private const val BOX_AGSL = """
uniform shader content;
uniform float2 dir;
uniform float2 maxXY;
half4 main(float2 c) {
    half4 sum = half4(0.0);
    for (int i = -12; i <= 12; i++) {
        sum += content.eval(clamp(c + dir * float(i), float2(0.0), maxXY));
    }
    return sum / 25.0;
}
"""

/** Kawase: четыре диагональные выборки на расстоянии [off]; несколько проходов с удвоением шага. */
private const val KAWASE_AGSL = """
uniform shader content;
uniform float off;
uniform float2 maxXY;
half4 main(float2 c) {
    half4 s = content.eval(clamp(c + float2( off,  off), float2(0.0), maxXY));
    s += content.eval(clamp(c + float2(-off,  off), float2(0.0), maxXY));
    s += content.eval(clamp(c + float2( off, -off), float2(0.0), maxXY));
    s += content.eval(clamp(c + float2(-off, -off), float2(0.0), maxXY));
    return s * 0.25;
}
"""

/** Боке: выборки по спирали Фогеля внутри круга; яркие точки весят больше и дают блики. */
private const val BOKEH_AGSL = """
uniform shader content;
uniform float radius;
uniform float2 maxXY;
half4 main(float2 c) {
    half4 sum = half4(0.0);
    float total = 0.0;
    for (int i = 0; i < 80; i++) {
        float t = (float(i) + 0.5) / 80.0;
        float r = sqrt(t) * radius;
        float a = float(i) * 2.39996323;
        half4 s = content.eval(clamp(c + r * float2(cos(a), sin(a)), float2(0.0), maxXY));
        float lum = dot(s.rgb, half3(0.299, 0.587, 0.114));
        float w = 1.0 + lum * lum * 3.0;
        sum += s * w;
        total += w;
    }
    return sum / total;
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

private fun box(radiusPx: Float, maxX: Float, maxY: Float): RenderEffect {
    // Дисперсия равномерного отрезка полуширины w равна w²/3, значит для σ = R нужно w = R·√3.
    // Два повторения: каждое даёт σ/√2, итоговая полуширина одного прохода w/√2.
    val half = radiusPx * 1.732f / 1.414f
    val step = half / 12f
    var e: PlatformRenderEffect? = null
    repeat(2) {
        e = chain(e, pass(BOX_AGSL, maxX, maxY) { setFloatUniform("dir", step, 0f) })
        e = chain(e, pass(BOX_AGSL, maxX, maxY) { setFloatUniform("dir", 0f, step) })
    }
    return e!!.asComposeRenderEffect()
}

private fun kawase(radiusPx: Float, maxX: Float, maxY: Float): RenderEffect {
    // Дисперсия одного прохода по оси равна off²; при шагах s·2^i (i = 0..4) сумма равна s²·341, откуда s = R/√341.
    val s = radiusPx / 18.47f
    var e: PlatformRenderEffect? = null
    var off = s
    repeat(5) {
        val o = off
        e = chain(e, pass(KAWASE_AGSL, maxX, maxY) { setFloatUniform("off", o) })
        off *= 2f
    }
    return e!!.asComposeRenderEffect()
}

private fun bokeh(radiusPx: Float, maxX: Float, maxY: Float): RenderEffect {
    // Для круга радиуса ρ дисперсия по оси равна ρ²/4, значит ρ = 2σ; чуть уменьшаем из-за шумности выборок.
    val r = radiusPx * 1.7f
    return pass(BOKEH_AGSL, maxX, maxY) { setFloatUniform("radius", r) }.asComposeRenderEffect()
}
