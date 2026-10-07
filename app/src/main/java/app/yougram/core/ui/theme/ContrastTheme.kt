package app.yougram.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance

/** Тёмная ли тема сейчас, независимо от того, как приложение её выбирает. */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Цвет текста для любого фона по тем же правилам, что и в пузырях. */
fun textColorOn(bg: Color, dark: Boolean): Color =
    if (dark) Color.White
    else if (contrast(bg, Color.White) >= contrast(bg, Color.Black)) Color.White else Color.Black

/**
 * 1) ГЛОБАЛЬНО: применить к colorScheme в YougramTheme, и каждый on* цвет в Material-компонентах
 *    автоматически получит читаемый контраст со своим фоном (в тёмной теме onSurface и onBackground белые).
 *    MaterialTheme(colorScheme = scheme.withReadableContent(dark), ...)
 */
fun ColorScheme.withReadableContent(dark: Boolean): ColorScheme {
    fun on(bg: Color, fg: Color) = ensureContrast(fg, bg, MIN_TEXT_CONTRAST)
    fun onMain(bg: Color, fg: Color) = if (dark) Color.White else on(bg, fg)
    return copy(
        onPrimary = on(primary, onPrimary),
        onPrimaryContainer = on(primaryContainer, onPrimaryContainer),
        onSecondary = on(secondary, onSecondary),
        onSecondaryContainer = on(secondaryContainer, onSecondaryContainer),
        onTertiary = on(tertiary, onTertiary),
        onTertiaryContainer = on(tertiaryContainer, onTertiaryContainer),
        onBackground = onMain(background, onBackground),
        onSurface = onMain(surface, onSurface),
        onSurfaceVariant = on(surfaceVariant, onSurfaceVariant),
        onError = on(error, onError),
        onErrorContainer = on(errorContainer, onErrorContainer),
        inverseOnSurface = on(inverseSurface, inverseOnSurface),
    )
}

/**
 * 2) ДЛЯ ЛЮБОГО КАСТОМНОГО ФОНА: вместо Box(Modifier.background(color)) использовать ContrastSurface(color).
 *    Весь вложенный Text/Icon без явного цвета сам станет читаемым (LocalContentColor).
 */
@Composable
fun ContrastSurface(
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme()
    val fg = textColorOn(color, dark)
    Surface(modifier = modifier, shape = shape, color = color, contentColor = fg) {
        CompositionLocalProvider(LocalContentColor provides fg, content = content)
    }
}

/**
 * 3) ДЛЯ ПУЗЫРЕЙ: отдаёт BubbleStyle всем вложенным (ответ, реакции, ссылки, время) без передачи параметрами.
 */
val LocalBubbleStyle = staticCompositionLocalOf<BubbleStyle?> { null }

@Composable
fun ProvideBubbleStyle(style: BubbleStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBubbleStyle provides style,
        LocalContentColor provides style.text,
        content = content,
    )
}
