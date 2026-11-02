package app.yougram.feature.miniapp.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.g000sha256.tdl.dto.ThemeParameters
import org.json.JSONObject

private fun Color.rgb(): Int = toArgb() and 0xFFFFFF

private fun Color.hex(): String = String.format("#%06x", rgb())

/**
 * Тема мини-приложения строится из текущей схемы Material 3 (в том числе динамической),
 * поэтому приложения красятся в цвета Material You.
 */
internal fun ColorScheme.toThemeParameters(): ThemeParameters = ThemeParameters(
    backgroundColor = surface.rgb(),
    secondaryBackgroundColor = surfaceContainer.rgb(),
    headerBackgroundColor = surfaceContainer.rgb(),
    bottomBarBackgroundColor = surfaceContainer.rgb(),
    sectionBackgroundColor = surfaceContainerHigh.rgb(),
    sectionSeparatorColor = outlineVariant.rgb(),
    textColor = onSurface.rgb(),
    accentTextColor = primary.rgb(),
    sectionHeaderTextColor = primary.rgb(),
    subtitleTextColor = onSurfaceVariant.rgb(),
    destructiveTextColor = error.rgb(),
    hintColor = outline.rgb(),
    linkColor = primary.rgb(),
    buttonColor = primary.rgb(),
    buttonTextColor = onPrimary.rgb(),
)

/** То же в виде theme_params для события theme_changed. */
internal fun ColorScheme.toThemeJson(): JSONObject = JSONObject()
    .put("bg_color", surface.hex())
    .put("secondary_bg_color", surfaceContainer.hex())
    .put("header_bg_color", surfaceContainer.hex())
    .put("bottom_bar_bg_color", surfaceContainer.hex())
    .put("section_bg_color", surfaceContainerHigh.hex())
    .put("section_separator_color", outlineVariant.hex())
    .put("text_color", onSurface.hex())
    .put("accent_text_color", primary.hex())
    .put("section_header_text_color", primary.hex())
    .put("subtitle_text_color", onSurfaceVariant.hex())
    .put("destructive_text_color", error.hex())
    .put("hint_color", outline.hex())
    .put("link_color", primary.hex())
    .put("button_color", primary.hex())
    .put("button_text_color", onPrimary.hex())
