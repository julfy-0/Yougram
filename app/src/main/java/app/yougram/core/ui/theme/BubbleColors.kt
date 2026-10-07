package app.yougram.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** Все цвета пузыря, посчитанные вместе, чтобы всё было читаемо на его фоне. */
data class BubbleStyle(
    val background: Color,
    val text: Color,       // основной текст
    val secondary: Color,  // время, галочки, "изменено", имя пересланного
    val link: Color,       // ссылки, @упоминания, хэштеги
    val quoteBar: Color,   // полоска цитаты/ответа
    val quoteBg: Color,    // фон блока цитаты
    val chipBg: Color,     // фон реакций и кнопок внутри пузыря
)

const val MIN_TEXT_CONTRAST = 4.5f   // WCAG AA для обычного текста
const val MIN_META_CONTRAST = 3.0f   // для времени и вторичного текста

fun contrast(a: Color, b: Color): Float {
    val l1 = maxOf(a.luminance(), b.luminance()) + 0.05f
    val l2 = minOf(a.luminance(), b.luminance()) + 0.05f
    return l1 / l2
}

/** Двигает fg к белому/чёрному, пока контраст с bg не достигнет min. */
fun ensureContrast(fg: Color, bg: Color, min: Float): Color {
    if (contrast(fg, bg) >= min) return fg
    val target = if (bg.luminance() < 0.4f) Color.White else Color.Black
    var c = fg
    repeat(10) {
        c = lerp(c, target, 0.2f)
        if (contrast(c, bg) >= min) return c
    }
    return target
}

/** Для тёмной темы фон затемняется ровно настолько, чтобы БЕЛЫЙ текст был читаем. */
private fun darkenForWhiteText(bg: Color, min: Float = MIN_TEXT_CONTRAST): Color {
    var c = bg
    repeat(12) {
        if (contrast(Color.White, c) >= min) return c
        c = lerp(c, Color.Black, 0.12f)
    }
    return c
}

fun chatBackground(dark: Boolean): Color = if (dark) Color(0xFF17181B) else Color(0xFFF4F5F7)

/**
 * @param customBg  свой цвет пузыря (акцент, обои); если задан, подгоняется под читаемость.
 * Тёмная тема: пузыри серые, текст ВСЕГДА белый (фон подстраивается).
 * Светлая тема: текст белый или чёрный по лучшему контрасту.
 */
fun bubbleStyle(
    outgoing: Boolean,
    dark: Boolean,
    accent: Color,
    customBg: Color? = null,
    wallpaper: Color = chatBackground(dark),
): BubbleStyle {
    var bg = customBg ?: if (dark) {
        if (outgoing) lerp(Color(0xFF3B3F46), accent, 0.12f) else Color(0xFF2A2C31)
    } else {
        if (outgoing) lerp(Color(0xFFDDE8F7), accent, 0.10f) else Color(0xFFE9EAEE)
    }
    // полупрозрачный фон считаем поверх обоев
    bg = bg.compositeOver(wallpaper)

    val text: Color
    if (dark) {
        bg = darkenForWhiteText(bg)
        text = Color.White
    } else {
        text = if (contrast(bg, Color.White) >= contrast(bg, Color.Black)) Color.White else Color.Black
    }
    // не даём пузырю слиться с фоном экрана
    if (contrast(bg, wallpaper) < 1.15f) {
        bg = lerp(bg, if (dark) Color.White else Color.Black, 0.08f)
        if (dark) bg = darkenForWhiteText(bg)
    }

    val secondary = ensureContrast(text.copy(alpha = 0.65f).compositeOver(bg), bg, MIN_META_CONTRAST)
    val link = ensureContrast(accent, bg, MIN_TEXT_CONTRAST)
    val quoteBar = ensureContrast(accent, bg, MIN_META_CONTRAST)
    val quoteBg = lerp(bg, text, 0.08f)
    val chipBg = lerp(bg, text, 0.14f)
    return BubbleStyle(bg, text, secondary, link, quoteBar, quoteBg, chipBg)
}
