package app.yougram.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import app.yougram.data.ThemeMode
import app.yougram.data.ThemeSettings

/** Готовые акценты для случая, когда системные цвета выключены. */
data class Accent(val name: String, val color: Color)

val Accents = listOf(
    Accent("Синий", Color(0xFF3F6FD8)),
    Accent("Бирюзовый", Color(0xFF00897B)),
    Accent("Зелёный", Color(0xFF43A047)),
    Accent("Оранжевый", Color(0xFFF57C00)),
    Accent("Красный", Color(0xFFE53935)),
    Accent("Фиолетовый", Color(0xFF8E24AA)),
)

private val DarkText = Color(0xFFF2F2F2)
private val DarkTextVariant = Color(0xFFD0D0D0)

/** Тёмная ли тема с учётом выбранного режима (для системных панелей и иконок). */
@Composable
fun isDarkTheme(settings: ThemeSettings): Boolean = when (settings.mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun YougramTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val dark = isDarkTheme(settings)
    val context = LocalContext.current
    // minSdk 31, поэтому системные (Material You) цвета доступны всегда.
    val base = if (settings.dynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        accentScheme(Accents[settings.accent.coerceIn(Accents.indices)].color, dark)
    }
    val colorScheme = if (dark) base.withLightText() else base
    MaterialTheme(colorScheme = colorScheme) {
        // Без Surface в корне цвет текста по умолчанию чёрный — задаём его явно.
        CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground, content = content)
    }
}

/** В тёмной теме весь текст светлый; primary затемняется, чтобы белый текст на нём читался. */
private fun ColorScheme.withLightText(): ColorScheme = copy(
    primary = lerp(primary, Color.Black, 0.45f),
    onPrimary = Color.White,
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = DarkTextVariant,
    onPrimaryContainer = DarkText,
    onSecondaryContainer = DarkText,
    onTertiaryContainer = DarkText,
    inverseSurface = lerp(inverseSurface, Color.Black, 0.6f),
    inverseOnSurface = DarkText,
)

private fun accentScheme(seed: Color, dark: Boolean): ColorScheme = if (dark) {
    val base = Color(0xFF121212)
    darkColorScheme(
        primary = lerp(seed, Color.White, 0.35f),
        onPrimary = Color(0xFF101010),
        primaryContainer = lerp(seed, Color.Black, 0.45f),
        onPrimaryContainer = lerp(seed, Color.White, 0.8f),
        secondaryContainer = lerp(base, seed, 0.3f),
        onSecondaryContainer = lerp(seed, Color.White, 0.8f),
        background = base,
        surface = lerp(base, seed, 0.04f),
        surfaceVariant = lerp(Color(0xFF2A2A2A), seed, 0.1f),
        onSurfaceVariant = Color(0xFFC4C4C4),
    )
} else {
    val base = Color(0xFFFCFCFC)
    lightColorScheme(
        primary = seed,
        onPrimary = Color.White,
        primaryContainer = lerp(seed, Color.White, 0.75f),
        onPrimaryContainer = lerp(seed, Color.Black, 0.6f),
        secondaryContainer = lerp(base, seed, 0.2f),
        onSecondaryContainer = lerp(seed, Color.Black, 0.6f),
        background = base,
        surface = lerp(base, seed, 0.03f),
        surfaceVariant = lerp(Color(0xFFE6E6E6), seed, 0.1f),
        onSurfaceVariant = Color(0xFF4A4A4A),
    )
}