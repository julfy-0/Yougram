@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.core.ui.wallpaper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

class WallpaperPreset(val id: String, val title: String, val colors: List<Color>)

val WallpaperPresets: List<WallpaperPreset> = listOf(
    WallpaperPreset("midnight", "Полночь", listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))),
    WallpaperPreset("graphite", "Графит", listOf(Color(0xFF121212), Color(0xFF1E1E24), Color(0xFF2A2A33))),
    WallpaperPreset("black", "Чёрный", listOf(Color(0xFF000000))),
    WallpaperPreset("ocean", "Океан", listOf(Color(0xFF0B1F3A), Color(0xFF1D4E89), Color(0xFF2A9D8F))),
    WallpaperPreset("aurora", "Аврора", listOf(Color(0xFF0B1F3A), Color(0xFF1E6F6A), Color(0xFF7AD3A5))),
    WallpaperPreset("forest", "Лес", listOf(Color(0xFF0B3D2E), Color(0xFF1B5E20), Color(0xFF6A994E))),
    WallpaperPreset("sunset", "Закат", listOf(Color(0xFF2C1A47), Color(0xFF8E3A59), Color(0xFFEE7752))),
    WallpaperPreset("cherry", "Вишня", listOf(Color(0xFF3B0A1A), Color(0xFF7A1F3D), Color(0xFFC2415D))),
    WallpaperPreset("sakura", "Сакура", listOf(Color(0xFF3A1C3F), Color(0xFFB5527E), Color(0xFFF4A6B7))),
    WallpaperPreset("lavender", "Лаванда", listOf(Color(0xFF2B2250), Color(0xFF6C5CE7), Color(0xFFA29BFE))),
    WallpaperPreset("gold", "Золото", listOf(Color(0xFF3B2F0B), Color(0xFF8A6D1C), Color(0xFFE0B94A))),
    WallpaperPreset("sand", "Песок", listOf(Color(0xFFD9C5A0), Color(0xFFE8D9B5), Color(0xFFF3E9CF))),
    WallpaperPreset("mint", "Мята", listOf(Color(0xFFB8E1D3), Color(0xFFD4EFE4), Color(0xFFEAF8F2))),
    WallpaperPreset("sky", "Небо", listOf(Color(0xFF89B4E8), Color(0xFFB9D6F3), Color(0xFFE3F0FB))),
    WallpaperPreset("peach", "Персик", listOf(Color(0xFFFF9A8B), Color(0xFFFF6A88), Color(0xFFFF99AC))),
    WallpaperPreset("neon", "Неон", listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24C6DC))),
    WallpaperPreset("candy", "Леденец", listOf(Color(0xFFFF758C), Color(0xFFFF7EB3), Color(0xFFB993FF))),
    WallpaperPreset("lava", "Лава", listOf(Color(0xFF200122), Color(0xFF6F0000), Color(0xFFFF5F2E))),
    WallpaperPreset("emerald", "Изумруд", listOf(Color(0xFF004D40), Color(0xFF00897B), Color(0xFF80CBC4))),
    WallpaperPreset("steel", "Сталь", listOf(Color(0xFF232526), Color(0xFF414345), Color(0xFF7B8794))),
    WallpaperPreset("royal", "Королевский", listOf(Color(0xFF141E30), Color(0xFF35577D), Color(0xFFB79CED))),
    WallpaperPreset("dawn", "Рассвет", listOf(Color(0xFF3A1C71), Color(0xFFD76D77), Color(0xFFFFAF7B))),
    WallpaperPreset("cotton", "Сладкая вата", listOf(Color(0xFFFBC2EB), Color(0xFFA6C1EE), Color(0xFFD4FC79))),
    WallpaperPreset("cream", "Сливки", listOf(Color(0xFFFFF1E0), Color(0xFFFFDDB8), Color(0xFFFFC2A1))),
)

/** Пресет из своих цветов пользователя (presetId == "custom"). */
fun WallpaperConfig.resolvePreset(): WallpaperPreset =
    if (presetId == CustomPresetId) WallpaperPreset(CustomPresetId, "Свой", customColors.map { Color(it) }.ifEmpty { listOf(Color.Black) })
    else wallpaperPreset(presetId)

/** Кисть обоев нужного типа градиента (угол — для линейного и поворота конусного). */
fun wallpaperBrush(colors: List<Color>, type: GradientType, angle: Float, w: Float, h: Float): Brush {
    if (colors.size == 1) return Brush.linearGradient(listOf(colors[0], colors[0]))
    val center = Offset(w / 2, h / 2)
    return when (type) {
        GradientType.Linear -> {
            val rad = Math.toRadians(angle.toDouble())
            val dx = kotlin.math.cos(rad).toFloat()
            val dy = kotlin.math.sin(rad).toFloat()
            val half = (kotlin.math.abs(w * dx) + kotlin.math.abs(h * dy)) / 2f
            Brush.linearGradient(colors, start = Offset(center.x - dx * half, center.y - dy * half), end = Offset(center.x + dx * half, center.y + dy * half))
        }
        GradientType.Radial -> Brush.radialGradient(colors, center = center, radius = kotlin.math.max(w, h) * 0.75f)
        // Конусный градиент замыкаем первым цветом, чтобы не было шва.
        GradientType.Sweep -> Brush.sweepGradient(colors + colors.first(), center)
    }
}

fun wallpaperPreset(id: String): WallpaperPreset = WallpaperPresets.firstOrNull { it.id == id } ?: WallpaperPresets.first()

fun Brush.Companion.wallpaper(colors: List<Color>, w: Float, h: Float): Brush =
    if (colors.size == 1) Brush.linearGradient(listOf(colors[0], colors[0]))
    else Brush.linearGradient(colors, start = Offset.Zero, end = Offset(w, h))

/**
 * Обои чата. [patternScale] < 1 уменьшает рисунок узора (для превью-плиток).
 * Фон под сообщениями, поэтому сам ничего не перехватывает.
 */
@Composable
fun WallpaperBackground(config: WallpaperConfig, modifier: Modifier = Modifier, patternScale: Float = 1f) {
    when (config.kind) {
        WallpaperKind.None -> Box(modifier)
        WallpaperKind.Preset -> GradientCanvas(config.resolvePreset(), config, null, 0f, modifier, patternScale)
        WallpaperKind.Pattern -> GradientCanvas(config.resolvePreset(), config, config.pattern, config.patternIntensity, modifier, patternScale)
        WallpaperKind.Photo -> PhotoWallpaper(config, modifier)
    }
}

@Composable
private fun GradientCanvas(
    preset: WallpaperPreset,
    config: WallpaperConfig,
    pattern: WallpaperPattern?,
    intensity: Float,
    modifier: Modifier,
    scale: Float,
) {
    val avg = remember(preset) { preset.colors.map { it.luminance() }.average() }
    val ink = if (avg > 0.5) Color.Black else Color.White
    val shapes = rememberWpShapes()
    Canvas(modifier) {
        val isCustom = config.presetId == CustomPresetId
        drawRect(
            wallpaperBrush(
                preset.colors,
                if (isCustom) config.gradientType else GradientType.Linear,
                if (isCustom) config.gradientAngle else 45f,
                size.width, size.height,
            ),
        )
        if (pattern != null) {
            drawPattern(pattern, shapes, ink.copy(alpha = intensity.coerceIn(0.03f, 0.5f)), 34.dp.toPx() * scale)
        }
    }
}

@Composable
private fun PhotoWallpaper(config: WallpaperConfig, modifier: Modifier) {
    val context = LocalContext.current
    val store = remember { WallpaperStore.get(context) }
    val bitmap by produceState<ImageBitmap?>(null, config.photoStamp) {
        value = withContext(Dispatchers.IO) {
            runCatching { android.graphics.BitmapFactory.decodeFile(store.photoFile.path)?.asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier.clipToBounds().background(Color.Black)) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(
                    if (config.photoBlur > 0.5f) Modifier.blur(config.photoBlur.dp, BlurredEdgeTreatment.Unbounded) else Modifier,
                ),
            )
        }
        if (config.photoDim > 0.01f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = config.photoDim)))
    }
}

// ---------- Узоры из фигур Material 3 Expressive (MaterialShapes) ----------

/** Фигуры M3, из которых собираются узоры. toShape() — composable, поэтому создаём их заранее, а в Canvas только рисуем. */
private class WpShapes(
    val circle: Shape, val square: Shape, val pill: Shape, val oval: Shape, val triangle: Shape,
    val diamond: Shape, val heart: Shape, val pentagon: Shape, val gem: Shape,
    val sunny: Shape, val verySunny: Shape, val burst: Shape,
    val cookie4: Shape, val cookie6: Shape, val cookie9: Shape, val cookie12: Shape,
    val clover4: Shape, val clover8: Shape, val flower: Shape, val puffy: Shape, val softBurst: Shape,
) {
    val mix = listOf(
        circle, flower, heart, sunny, cookie9, clover4, triangle, diamond, puffy, softBurst, cookie6, verySunny, gem, pentagon,
    )
}

@Composable
private fun rememberWpShapes() = WpShapes(
    circle = MaterialShapes.Circle.toShape(),
    square = MaterialShapes.Square.toShape(),
    pill = MaterialShapes.Pill.toShape(),
    oval = MaterialShapes.Oval.toShape(),
    triangle = MaterialShapes.Triangle.toShape(),
    diamond = MaterialShapes.Diamond.toShape(),
    heart = MaterialShapes.Heart.toShape(),
    pentagon = MaterialShapes.Pentagon.toShape(),
    gem = MaterialShapes.Gem.toShape(),
    sunny = MaterialShapes.Sunny.toShape(),
    verySunny = MaterialShapes.VerySunny.toShape(),
    burst = MaterialShapes.Burst.toShape(),
    cookie4 = MaterialShapes.Cookie4Sided.toShape(),
    cookie6 = MaterialShapes.Cookie6Sided.toShape(),
    cookie9 = MaterialShapes.Cookie9Sided.toShape(),
    cookie12 = MaterialShapes.Cookie12Sided.toShape(),
    clover4 = MaterialShapes.Clover4Leaf.toShape(),
    clover8 = MaterialShapes.Clover8Leaf.toShape(),
    flower = MaterialShapes.Flower.toShape(),
    puffy = MaterialShapes.Puffy.toShape(),
    softBurst = MaterialShapes.SoftBurst.toShape(),
)

/** Рисует [shape] с центром в (cx, cy) в рамке [w]×[h], повёрнутую на [angle]. */
private fun DrawScope.shapeAt(shape: Shape, cx: Float, cy: Float, w: Float, h: Float, angle: Float, color: Color) {
    translate(cx - w / 2, cy - h / 2) {
        rotate(angle, Offset(w / 2, h / 2)) {
            drawOutline(shape.createOutline(Size(w, h), layoutDirection, this), color)
        }
    }
}

private fun DrawScope.shapeAt(shape: Shape, cx: Float, cy: Float, s: Float, angle: Float, color: Color) =
    shapeAt(shape, cx, cy, s, s, angle, color)

private fun DrawScope.drawPattern(p: WallpaperPattern, sh: WpShapes, ink: Color, step: Float) {
    val w = size.width
    val h = size.height
    when (p) {
        // «Круги»: шахматная сетка кругов двух размеров.
        WallpaperPattern.Dots -> forGrid(w, h, step) { cx, cy, i ->
            shapeAt(sh.circle, cx, cy, step * if (i % 2 == 0) 0.3f else 0.16f, 0f, ink)
        }
        // «Квадраты»: скруглённые квадраты и ромбы вперемешку.
        WallpaperPattern.Grid -> forGrid(w, h, step) { cx, cy, i ->
            if (i % 2 == 0) shapeAt(sh.square, cx, cy, step * 0.3f, 0f, ink)
            else shapeAt(sh.diamond, cx, cy, step * 0.26f, 0f, ink)
        }
        // «Капсулы»: наклонные капсулы и овалы.
        WallpaperPattern.Diagonal -> forGrid(w, h, step * 0.9f) { cx, cy, i ->
            if (i % 2 == 0) shapeAt(sh.pill, cx, cy, step * 0.2f, step * 0.62f, -45f, ink)
            else shapeAt(sh.oval, cx, cy, step * 0.16f, step * 0.4f, -45f, ink)
        }
        // «Цветы»: цветок и печенье на 7 граней.
        WallpaperPattern.Waves -> forGrid(w, h, step) { cx, cy, i ->
            if (i % 2 == 0) shapeAt(sh.flower, cx, cy, step * 0.42f, (i * 17 % 40).toFloat(), ink)
            else shapeAt(sh.cookie4, cx, cy, step * 0.28f, 0f, ink)
        }
        WallpaperPattern.Triangles -> forGrid(w, h, step) { cx, cy, i ->
            shapeAt(sh.triangle, cx, cy, step * 0.36f, if (i % 2 == 0) 0f else 180f, ink)
        }
        // «Искры»: солнышки и всплески разного размера.
        WallpaperPattern.Stars -> forGrid(w, h, step) { cx, cy, i ->
            when (i % 3) {
                0 -> shapeAt(sh.sunny, cx, cy, step * 0.4f, 0f, ink)
                1 -> shapeAt(sh.burst, cx, cy, step * 0.26f, 15f, ink)
                else -> shapeAt(sh.verySunny, cx, cy, step * 0.2f, 0f, ink)
            }
        }
        WallpaperPattern.Hearts -> forGrid(w, h, step) { cx, cy, i ->
            shapeAt(sh.heart, cx, cy, step * 0.36f, if (i % 2 == 0) -12f else 12f, ink)
        }
        // «Печенье»: печенья M3 с разным числом граней.
        WallpaperPattern.Rings -> forGrid(w, h, step) { cx, cy, i ->
            val shape = when (i % 4) { 0 -> sh.cookie6; 1 -> sh.cookie9; 2 -> sh.cookie12; else -> sh.cookie4 }
            shapeAt(shape, cx, cy, step * if (i % 2 == 0) 0.42f else 0.3f, (i * 23 % 60).toFloat(), ink)
        }
        // «Клевер»: клевер на 4 и 8 лепестков.
        WallpaperPattern.Crosses -> forGrid(w, h, step) { cx, cy, i ->
            shapeAt(if (i % 2 == 0) sh.clover4 else sh.clover8, cx, cy, step * if (i % 2 == 0) 0.4f else 0.3f, 0f, ink)
        }
        // «Соты»: плотная шахматка пятиугольников и самоцветов.
        WallpaperPattern.Hex -> {
            val r = step * 0.5f
            val dx = r * 1.15f
            val dy = r * 1.0f
            var row = 0
            var y = 0f
            while (y < h + r * 2) {
                var x = if (row % 2 == 0) 0f else dx / 2
                var col = 0
                while (x < w + dx) {
                    shapeAt(if ((row + col) % 2 == 0) sh.pentagon else sh.gem, x, y, r * 0.85f, 0f, ink)
                    x += dx; col++
                }
                y += dy; row++
            }
        }
        // «Мозаика»: плитки-квадраты разной величины и прозрачности, как мозаика из стекла.
        WallpaperPattern.Mosaic -> {
            val rnd = Random(11)
            val cell = step * 0.62f
            val gap = cell * 0.1f
            var y = 0f
            while (y < h) {
                var x = 0f
                while (x < w) {
                    val roll = rnd.nextFloat()
                    val span = if (roll < 0.18f) 2 else 1
                    val a = 0.35f + rnd.nextFloat() * 0.65f
                    if (roll < 0.8f) {
                        drawRoundRect(
                            ink.copy(alpha = ink.alpha * a),
                            topLeft = Offset(x + gap, y + gap),
                            size = Size(cell * span - gap * 2, cell - gap * 2),
                            cornerRadius = CornerRadius(cell * 0.18f),
                        )
                    }
                    x += cell * span
                }
                y += cell
            }
        }
        // «Кирпичи»: кладка со смещением рядов.
        WallpaperPattern.Bricks -> {
            val bw = step * 0.9f
            val bh = step * 0.42f
            val gap = bh * 0.14f
            var row = 0
            var y = 0f
            while (y < h) {
                var x = if (row % 2 == 0) 0f else -bw / 2
                while (x < w) {
                    drawRoundRect(ink, Offset(x + gap, y + gap), Size(bw - gap * 2, bh - gap * 2), CornerRadius(bh * 0.2f))
                    x += bw
                }
                y += bh; row++
            }
        }
        // «Клетка»: тонкая сетка с утолщёнными линиями через одну.
        WallpaperPattern.Plaid -> {
            val s = step * 0.7f
            var x = 0f
            var i = 0
            while (x < w) {
                drawRect(ink.copy(alpha = ink.alpha * if (i % 3 == 0) 1f else 0.45f), Offset(x, 0f), Size(if (i % 3 == 0) s * 0.14f else s * 0.05f, h))
                x += s; i++
            }
            var y = 0f
            i = 0
            while (y < h) {
                drawRect(ink.copy(alpha = ink.alpha * if (i % 3 == 0) 1f else 0.45f), Offset(0f, y), Size(w, if (i % 3 == 0) s * 0.14f else s * 0.05f))
                y += s; i++
            }
        }
        // «Полосы»: диагональные полосы.
        WallpaperPattern.Stripes -> {
            val s = step * 0.55f
            val sw = s * 0.3f
            var d = -h
            while (d < w) {
                drawLine(ink, Offset(d, 0f), Offset(d + h, h), strokeWidth = sw, cap = StrokeCap.Round)
                d += s
            }
        }
        // «Чешуя»: перекрывающиеся дуги, как рыбья чешуя.
        WallpaperPattern.Scales -> {
            val r = step * 0.45f
            val stroke = Stroke(width = r * 0.14f)
            var row = 0
            var y = 0f
            while (y < h + r) {
                var x = if (row % 2 == 0) 0f else r
                while (x < w + r * 2) {
                    drawArc(ink, 180f, 180f, false, Offset(x - r, y - r), Size(r * 2, r * 2), style = stroke)
                    drawArc(ink.copy(alpha = ink.alpha * 0.6f), 180f, 180f, false, Offset(x - r * 0.6f, y - r * 0.6f), Size(r * 1.2f, r * 1.2f), style = stroke)
                    x += r * 2
                }
                y += r * 0.9f; row++
            }
        }
        // «Пузыри»: окружности разного размера, контуром и заливкой.
        WallpaperPattern.Bubbles -> {
            val rnd = Random(5)
            forGrid(w, h, step * 0.95f) { cx0, cy0, _ ->
                val cx = cx0 + (rnd.nextFloat() - 0.5f) * step * 0.5f
                val cy = cy0 + (rnd.nextFloat() - 0.5f) * step * 0.5f
                val r = step * (0.08f + rnd.nextFloat() * 0.2f)
                if (rnd.nextBoolean()) drawCircle(ink, r, Offset(cx, cy))
                else drawCircle(ink, r, Offset(cx, cy), style = Stroke(width = r * 0.25f))
            }
        }
        // «Конфетти»: капсулы, круги и точки в беспорядке.
        WallpaperPattern.Confetti -> {
            val rnd = Random(21)
            forGrid(w, h, step * 0.8f) { cx0, cy0, i ->
                val cx = cx0 + (rnd.nextFloat() - 0.5f) * step * 0.6f
                val cy = cy0 + (rnd.nextFloat() - 0.5f) * step * 0.6f
                val angle = rnd.nextFloat() * 180f
                when (i % 3) {
                    0 -> shapeAt(sh.pill, cx, cy, step * 0.09f, step * 0.3f, angle, ink)
                    1 -> shapeAt(sh.circle, cx, cy, step * 0.13f, 0f, ink)
                    else -> shapeAt(sh.cookie4, cx, cy, step * 0.2f, angle, ink)
                }
            }
        }
        // «Лепестки»: крупные цветы с клевером между ними.
        WallpaperPattern.Petals -> forGrid(w, h, step * 1.25f) { cx, cy, i ->
            if (i % 2 == 0) shapeAt(sh.flower, cx, cy, step * 0.7f, (i * 11 % 45).toFloat(), ink)
            else shapeAt(sh.clover8, cx, cy, step * 0.34f, 22f, ink)
        }
        // «Кристаллы»: плотные самоцветы и ромбы.
        WallpaperPattern.Gems -> forGrid(w, h, step * 0.85f) { cx, cy, i ->
            if (i % 2 == 0) shapeAt(sh.gem, cx, cy, step * 0.4f, 0f, ink)
            else shapeAt(sh.diamond, cx, cy, step * 0.22f, 0f, ink)
        }
        // «Зигзаги»: ломаные линии рядами.
        WallpaperPattern.Zigzag -> {
            val amp = step * 0.2f
            val seg = step * 0.4f
            val sw = step * 0.07f
            var y = amp
            var row = 0
            while (y < h + amp) {
                var x = 0f
                var up = row % 2 == 0
                while (x < w + seg) {
                    drawLine(ink, Offset(x, if (up) y + amp else y - amp), Offset(x + seg, if (up) y - amp else y + amp), strokeWidth = sw, cap = StrokeCap.Round)
                    x += seg; up = !up
                }
                y += step * 0.75f; row++
            }
        }
        // «Шахматы»: крупная шахматная доска со скруглёнными клетками.
        WallpaperPattern.Checker -> {
            val s = step * 0.7f
            var row = 0
            var y = 0f
            while (y < h) {
                var col = 0
                var x = 0f
                while (x < w) {
                    if ((row + col) % 2 == 0) drawRoundRect(ink, Offset(x, y), Size(s, s), CornerRadius(s * 0.12f))
                    x += s; col++
                }
                y += s; row++
            }
        }
        // «Взрывы»: всплески и солнышки по убывающей.
        WallpaperPattern.Bursts -> forGrid(w, h, step * 1.1f) { cx, cy, i ->
            shapeAt(sh.burst, cx, cy, step * if (i % 2 == 0) 0.5f else 0.28f, (i * 15 % 30).toFloat(), ink)
            if (i % 2 == 0) shapeAt(sh.softBurst, cx + step * 0.5f, cy + step * 0.4f, step * 0.2f, 0f, ink)
        }
        // «Микс»: случайные, но стабильные (seed) фигуры любой формы и поворота.
        WallpaperPattern.Doodle -> {
            val rnd = Random(7)
            forGrid(w, h, step * 1.1f) { cx0, cy0, i ->
                val cx = cx0 + (rnd.nextFloat() - 0.5f) * step * 0.4f
                val cy = cy0 + (rnd.nextFloat() - 0.5f) * step * 0.4f
                val angle = rnd.nextFloat() * 360f
                val s = step * (0.26f + rnd.nextFloat() * 0.16f)
                shapeAt(sh.mix[(i + rnd.nextInt(3)) % sh.mix.size], cx, cy, s, angle, ink)
            }
        }
    }
}

private inline fun forGrid(w: Float, h: Float, step: Float, block: (cx: Float, cy: Float, index: Int) -> Unit) {
    var i = 0
    var row = 0
    var y = step / 2
    while (y < h + step) {
        var x = if (row % 2 == 0) step / 2 else step
        while (x < w + step) { block(x, y, i++); x += step }
        y += step; row++
    }
}