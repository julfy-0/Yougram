package app.yougram.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
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

/**
 * Шкала форм Material 3 Expressive (контрастная: от мелких до очень крупных скруглений).
 * extraSmall…extraLarge сохранены близкими к прежним значениям, чтобы экраны не «поехали»;
 * добавлены largeIncreased / extraLargeIncreased / extraExtraLarge для новых компонентов.
 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    largeIncreased = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
    extraLargeIncreased = RoundedCornerShape(40.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

/** Цвет фона экрана чата: не затемняется вместе с остальным фоном приложения. */
val LocalChatBackground = compositionLocalOf { Color.Unspecified }

/** На сколько затемняются фон приложения и подложки (кроме фона в чате). */
private const val AppDarkenAmount = 0.3f

private fun ColorScheme.darkenedBackgrounds(amount: Float): ColorScheme {
    fun Color.d() = lerp(this, Color.Black, amount)
    return copy(
        background = background.d(),
        surface = surface.d(),
        surfaceVariant = surfaceVariant.d(),
        surfaceDim = surfaceDim.d(),
        surfaceBright = surfaceBright.d(),
        surfaceContainerLowest = surfaceContainerLowest.d(),
        surfaceContainerLow = surfaceContainerLow.d(),
        surfaceContainer = surfaceContainer.d(),
        surfaceContainerHigh = surfaceContainerHigh.d(),
        surfaceContainerHighest = surfaceContainerHighest.d(),
    )
}

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
    // Порядок важен: сначала фон/текст тёмной темы, затем уровни surfaceContainer*, затем контраст on*-цветов.
    val schemes = remember(base, dark, settings.dynamic) {
        val withBackground = when {
            dark -> base.withLightText()
            !settings.dynamic -> base.withLightAccentBackground()
            else -> base
        }
        // В тёмной теме поверхности принудительно #121212, поэтому уровни контейнеров считаем сами.
        // В светлой с динамическими цветами оставляем системные уровни.
        val withLevels = if (dark || !settings.dynamic) withBackground.withContainerLevels(dark) else withBackground
        // Фон чата остаётся прежним, остальной фон и подложки затемняются.
        val chat = withLevels.withReadableContent(dark)
        chat.darkenedBackgrounds(AppDarkenAmount).withReadableContent(dark) to chat.background
    }
    val colorScheme = schemes.first
    val chatBackground = schemes.second
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
            val hapticIndication = app.yougram.core.ui.rememberHapticIndication(
                androidx.compose.foundation.LocalIndication.current,
            )
            CompositionLocalProvider(
                LocalContentColor provides colorScheme.onBackground,
                LocalChatBackground provides chatBackground,
                androidx.compose.foundation.LocalIndication provides hapticIndication,
                content = content,
            )
        }
    }
}

/** Типографика приложения: expressive-иерархия, при необходимости со своим шрифтом из файла. Битый файл — молча остаёмся на системном. */
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
    expressiveTypography(family)
}

/**
 * Типографика с заметной иерархией: заголовки и title жирнее, emphasized-варианты ещё контрастнее.
 * Размеры остаются стандартными M3 (их задают токены Typography()).
 */
private fun expressiveTypography(family: FontFamily?): Typography {
    val t = Typography()
    fun TextStyle.styled(weight: FontWeight? = null): TextStyle {
        var r = this
        if (family != null) r = r.copy(fontFamily = family)
        if (weight != null) r = r.copy(fontWeight = weight)
        return r
    }
    return t.copy(
        displayLarge = t.displayLarge.styled(),
        displayMedium = t.displayMedium.styled(),
        displaySmall = t.displaySmall.styled(),
        headlineLarge = t.headlineLarge.styled(FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.styled(FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.styled(FontWeight.SemiBold),
        titleLarge = t.titleLarge.styled(FontWeight.SemiBold),
        titleMedium = t.titleMedium.styled(FontWeight.SemiBold),
        titleSmall = t.titleSmall.styled(FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.styled(),
        bodyMedium = t.bodyMedium.styled(),
        bodySmall = t.bodySmall.styled(),
        labelLarge = t.labelLarge.styled(FontWeight.Medium),
        labelMedium = t.labelMedium.styled(FontWeight.Medium),
        labelSmall = t.labelSmall.styled(FontWeight.Medium),
        displayLargeEmphasized = t.displayLargeEmphasized.styled(FontWeight.Bold),
        displayMediumEmphasized = t.displayMediumEmphasized.styled(FontWeight.Bold),
        displaySmallEmphasized = t.displaySmallEmphasized.styled(FontWeight.Bold),
        headlineLargeEmphasized = t.headlineLargeEmphasized.styled(FontWeight.Bold),
        headlineMediumEmphasized = t.headlineMediumEmphasized.styled(FontWeight.Bold),
        headlineSmallEmphasized = t.headlineSmallEmphasized.styled(FontWeight.Bold),
        titleLargeEmphasized = t.titleLargeEmphasized.styled(FontWeight.Bold),
        titleMediumEmphasized = t.titleMediumEmphasized.styled(FontWeight.Bold),
        titleSmallEmphasized = t.titleSmallEmphasized.styled(FontWeight.Bold),
        bodyLargeEmphasized = t.bodyLargeEmphasized.styled(FontWeight.SemiBold),
        bodyMediumEmphasized = t.bodyMediumEmphasized.styled(FontWeight.SemiBold),
        bodySmallEmphasized = t.bodySmallEmphasized.styled(FontWeight.SemiBold),
        labelLargeEmphasized = t.labelLargeEmphasized.styled(FontWeight.Bold),
        labelMediumEmphasized = t.labelMediumEmphasized.styled(FontWeight.Bold),
        labelSmallEmphasized = t.labelSmallEmphasized.styled(FontWeight.Bold),
    )
}

/** Доля акцента в фоне приложения: фон и подложки слегка окрашиваются в цвет акцента. */
private const val DarkBackgroundTint = 0.12f
private const val LightBackgroundTint = 0.07f

/** В тёмной теме весь текст светлый, а фон тёмный с лёгким оттенком акцента (от него считаются и подложки). */
private fun ColorScheme.withLightText(): ColorScheme {
    val bg = lerp(Color(0xFF121212), primary, DarkBackgroundTint)
    return copy(
        background = bg,
        surface = bg,
        surfaceVariant = lerp(Color(0xFF242424), primary, DarkBackgroundTint + 0.04f),
        onBackground = DarkText,
        onSurface = DarkText,
        onSurfaceVariant = DarkTextVariant,
    )
}

/** Светлая тема со своим акцентом: фон и surface с оттенком primary (системные динамические цвета не трогаем). */
private fun ColorScheme.withLightAccentBackground(): ColorScheme {
    val bg = lerp(Color(0xFFFCFCFC), primary, LightBackgroundTint)
    return copy(background = bg, surface = bg)
}

/**
 * Уровни surfaceContainer* для разделения областей без теней и линий.
 * Считаются от surface со слабым оттенком primary, поэтому серый не «грязнеет» и согласуется с акцентом.
 */
private fun ColorScheme.withContainerLevels(dark: Boolean): ColorScheme {
    val base = surface
    return if (dark) {
        val lift = lerp(primary, Color.White, 0.2f)
        copy(
            surfaceDim = lerp(base, Color.Black, 0.4f),
            surfaceBright = lerp(base, lift, 0.18f),
            surfaceContainerLowest = lerp(base, Color.Black, 0.4f),
            surfaceContainerLow = lerp(base, lift, 0.05f),
            surfaceContainer = lerp(base, lift, 0.09f),
            surfaceContainerHigh = lerp(base, lift, 0.13f),
            surfaceContainerHighest = lerp(base, lift, 0.17f),
        )
    } else {
        copy(
            surfaceDim = lerp(base, primary, 0.14f),
            surfaceBright = base,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(base, primary, 0.04f),
            surfaceContainer = lerp(base, primary, 0.07f),
            surfaceContainerHigh = lerp(base, primary, 0.10f),
            surfaceContainerHighest = lerp(base, primary, 0.14f),
        )
    }
}

/** Сдвиг оттенка и масштаб насыщенности/яркости цвета (для secondary/tertiary из акцента). */
private fun Color.hsv(hueShift: Float = 0f, saturation: Float = 1f, value: Float = 1f): Color {
    val a = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), a)
    a[0] = (a[0] + hueShift + 360f) % 360f
    a[1] = (a[1] * saturation).coerceIn(0f, 1f)
    a[2] = (a[2] * value).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(a))
}

/**
 * Expressive-палитра от акцента (когда динамические цвета выключены):
 * насыщенные primary/secondary/tertiary контейнеры. on*-цвета потом выравниваются по контрасту в withReadableContent.
 */
private fun accentScheme(seed: Color, dark: Boolean): ColorScheme = if (dark) {
    val base = Color(0xFF121212)
    val primary = lerp(seed, Color.White, 0.2f)
    val secondary = lerp(seed.hsv(saturation = 0.6f), Color.White, 0.25f)
    val tertiary = lerp(seed.hsv(hueShift = 45f, saturation = 0.65f), Color.White, 0.25f)
    darkColorScheme(
        primary = primary,
        onPrimary = Color.Black,
        primaryContainer = lerp(seed, Color.Black, 0.35f),
        onPrimaryContainer = lerp(seed, Color.White, 0.9f),
        secondary = secondary,
        onSecondary = Color.Black,
        secondaryContainer = lerp(base, seed, 0.28f),
        onSecondaryContainer = lerp(seed, Color.White, 0.9f),
        tertiary = tertiary,
        onTertiary = Color.Black,
        tertiaryContainer = lerp(tertiary, Color.Black, 0.45f),
        onTertiaryContainer = lerp(tertiary, Color.White, 0.9f),
        background = base,
        surface = base,
        surfaceVariant = Color(0xFF242424),
        onSurfaceVariant = Color(0xFFC4C4C4),
        outline = Color(0xFF8A8A8A),
        outlineVariant = Color(0xFF3A3A3A),
    )
} else {
    val base = Color(0xFFFCFCFC)
    val secondary = seed.hsv(saturation = 0.6f, value = 0.85f)
    val tertiary = seed.hsv(hueShift = 45f, saturation = 0.7f, value = 0.85f)
    lightColorScheme(
        primary = seed,
        onPrimary = Color.White,
        primaryContainer = lerp(seed, Color.White, 0.75f),
        onPrimaryContainer = lerp(seed, Color.Black, 0.65f),
        secondary = secondary,
        onSecondary = Color.White,
        secondaryContainer = lerp(base, seed, 0.25f),
        onSecondaryContainer = lerp(seed, Color.Black, 0.65f),
        tertiary = tertiary,
        onTertiary = Color.White,
        tertiaryContainer = lerp(base, tertiary, 0.30f),
        onTertiaryContainer = lerp(tertiary, Color.Black, 0.65f),
        background = base,
        surface = base,
        surfaceVariant = lerp(Color(0xFFE6E6E6), seed, 0.12f),
        onSurfaceVariant = Color(0xFF4A4A4A),
        outline = Color(0xFF7A7A7A),
        outlineVariant = Color(0xFFCACACA),
    )
}