package app.yougram.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/** Элементы экрана, у которых может быть свой шрифт. */
enum class FontRole(val key: String, val title: String, val hint: String) {
    Titles("titles", "Заголовки", "Заголовки экранов, названия чатов и разделов"),
    Body("body", "Основной текст", "Описания, подзаголовки, превью сообщений в списке"),
    Labels("labels", "Подписи и кнопки", "Время, счётчики, надписи на кнопках"),
    Navbar("navbar", "Навбар", "Названия вкладок нижней панели"),
    Messages("messages", "Текст сообщений", "Текст сообщений внутри чата"),
}

/**
 * Свой шрифт элемента. null в поле — «как у всего приложения».
 * [googleFont] — название из Google Fonts, [weightShift] — сдвиг жирности (−200…+200), [italic] — курсив.
 */
data class RoleFont(
    val googleFont: String? = null,
    val weightShift: Int? = null,
    val italic: Boolean? = null,
    /** Скругление шрифта 0…100 (ось ROND). */
    val roundness: Int? = null,
) {
    val isDefault: Boolean get() = googleFont == null && weightShift == null && italic == null && roundness == null
}

/** Применяет шрифт элемента поверх общего стиля приложения. */
fun TextStyle.applyRoleFont(
    rf: RoleFont?,
    globalShift: Int,
    /** Шрифт Google Fonts, который сейчас у всего приложения; null — свой файл (скругление к нему не применить). */
    baseFont: String?,
    globalRoundness: Int,
    familyFor: (String, Int) -> FontFamily,
): TextStyle {
    if (rf == null || rf.isDefault) return this
    var r = this
    if (rf.googleFont != null || rf.roundness != null) {
        val name = rf.googleFont ?: baseFont
        if (name != null) r = r.copy(fontFamily = familyFor(name, rf.roundness ?: globalRoundness))
    }
    rf.weightShift?.let {
        val base = (fontWeight ?: FontWeight.Normal).weight - globalShift
        r = r.copy(fontWeight = FontWeight((base + it).coerceIn(1, 1000)))
    }
    rf.italic?.let { r = r.copy(fontStyle = if (it) FontStyle.Italic else FontStyle.Normal) }
    return r
}

/** Типографика с учётом отдельных шрифтов для заголовков, основного текста и подписей. */
fun Typography.withRoles(
    roles: Map<FontRole, RoleFont>,
    globalShift: Int,
    baseFont: String?,
    globalRoundness: Int,
    familyFor: (String, Int) -> FontFamily,
): Typography = copy(
        displayLarge = displayLarge.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        displayLargeEmphasized = displayLargeEmphasized.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        displayMedium = displayMedium.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        displayMediumEmphasized = displayMediumEmphasized.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        displaySmall = displaySmall.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        displaySmallEmphasized = displaySmallEmphasized.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        headlineLarge = headlineLarge.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        headlineLargeEmphasized = headlineLargeEmphasized.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        headlineMedium = headlineMedium.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        headlineMediumEmphasized = headlineMediumEmphasized.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        headlineSmall = headlineSmall.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        headlineSmallEmphasized = headlineSmallEmphasized.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        titleLarge = titleLarge.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        titleLargeEmphasized = titleLargeEmphasized.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        titleMedium = titleMedium.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        titleMediumEmphasized = titleMediumEmphasized.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        titleSmall = titleSmall.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        titleSmallEmphasized = titleSmallEmphasized.applyRoleFont(roles[FontRole.Titles], globalShift, baseFont, globalRoundness, familyFor),
        bodyLarge = bodyLarge.applyRoleFont(roles[FontRole.Body], globalShift, baseFont, globalRoundness, familyFor),
        bodyLargeEmphasized = bodyLargeEmphasized.applyRoleFont(roles[FontRole.Body], globalShift, baseFont, globalRoundness, familyFor),
        bodyMedium = bodyMedium.applyRoleFont(roles[FontRole.Body], globalShift, baseFont, globalRoundness, familyFor),
        bodyMediumEmphasized = bodyMediumEmphasized.applyRoleFont(roles[FontRole.Body], globalShift, baseFont, globalRoundness, familyFor),
        bodySmall = bodySmall.applyRoleFont(roles[FontRole.Body], globalShift, baseFont, globalRoundness, familyFor),
        bodySmallEmphasized = bodySmallEmphasized.applyRoleFont(roles[FontRole.Body], globalShift, baseFont, globalRoundness, familyFor),
        labelLarge = labelLarge.applyRoleFont(roles[FontRole.Labels], globalShift, baseFont, globalRoundness, familyFor),
        labelLargeEmphasized = labelLargeEmphasized.applyRoleFont(roles[FontRole.Labels], globalShift, baseFont, globalRoundness, familyFor),
        labelMedium = labelMedium.applyRoleFont(roles[FontRole.Labels], globalShift, baseFont, globalRoundness, familyFor),
        labelMediumEmphasized = labelMediumEmphasized.applyRoleFont(roles[FontRole.Labels], globalShift, baseFont, globalRoundness, familyFor),
        labelSmall = labelSmall.applyRoleFont(roles[FontRole.Labels], globalShift, baseFont, globalRoundness, familyFor),
        labelSmallEmphasized = labelSmallEmphasized.applyRoleFont(roles[FontRole.Labels], globalShift, baseFont, globalRoundness, familyFor),
)

/** Применяет шрифт элемента (навбар, сообщения) к стилю; задаётся в теме приложения. */
val LocalRoleFontResolver = staticCompositionLocalOf<(FontRole, TextStyle) -> TextStyle> { { _, s -> s } }

@Composable
fun TextStyle.withRole(role: FontRole): TextStyle = LocalRoleFontResolver.current(role, this)
