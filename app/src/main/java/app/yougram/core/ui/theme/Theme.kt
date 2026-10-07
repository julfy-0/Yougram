package app.yougram.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import app.yougram.core.settings.ThemeMode
import app.yougram.core.settings.ThemeSettings
import java.io.File

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

/** Крупные скругления Material 3 Expressive. */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
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
    val base = if (settings.dynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        accentScheme(Accents[settings.accent.coerceIn(Accents.indices)].color, dark)
    }
    val colorScheme = if (dark) base.withLightText() else base
    val typography = rememberTypography(settings.fontPath)
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = typography,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background,
            contentColor = colorScheme.onBackground,
        ) {
            CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground, content = content)
        }
    }
}

/** Типографика приложения: стандартная или со своим шрифтом из файла. Битый файл — молча остаёмся на системном. */
@Composable
private fun rememberTypography(fontPath: String?): Typography = remember(fontPath) {
    val family = fontPath?.let { path ->
        runCatching {
            val file = File(path)
            // Проверяем, что файл — настоящий шрифт: иначе Compose упадёт при первой отрисовке текста.
            android.graphics.fonts.Font.Builder(file).build()
            FontFamily(Font(file, FontWeight.Normal))
        }.getOrNull()
    }
    if (family == null) Typography() else Typography().withFontFamily(family)
}

private fun Typography.withFontFamily(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

/** В тёмной теме весь текст светлый, а фон гарантированно тёмный. */
private fun ColorScheme.withLightText(): ColorScheme = copy(
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF242424),
    onBackground = DarkText,
    onSurface = DarkText,
    onSurfaceVariant = DarkTextVariant,
)

private fun accentScheme(seed: Color, dark: Boolean): ColorScheme = if (dark) {
    val base = Color(0xFF121212)
    darkColorScheme(
        primary = seed,
        onPrimary = Color.White,
        primaryContainer = lerp(seed, Color.Black, 0.45f),
        onPrimaryContainer = lerp(seed, Color.White, 0.8f),
        secondaryContainer = lerp(base, seed, 0.3f),
        onSecondaryContainer = lerp(seed, Color.White, 0.8f),
        background = base,
        surface = base,
        surfaceVariant = Color(0xFF242424),
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
        surface = base,
        surfaceVariant = lerp(Color(0xFFE6E6E6), seed, 0.1f),
        onSurfaceVariant = Color(0xFF4A4A4A),
    )
}
