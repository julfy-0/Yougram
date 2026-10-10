package app.yougram.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yougram.core.settings.GlassSettings
import app.yougram.core.settings.PlateRepository
import app.yougram.core.settings.SettingsRepository
import app.yougram.feature.settings.appearance.AppIconSection
import app.yougram.feature.settings.appearance.BlurTypeRow
import app.yougram.feature.settings.appearance.GlassPreview
import app.yougram.feature.settings.appearance.PlateSection
import app.yougram.feature.settings.appearance.ThemeSection
import app.yougram.feature.settings.component.DotSlider
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(settings: SettingsRepository, plates: PlateRepository, contentPadding: PaddingValues) {
    val glass by settings.glass.collectAsState()

    SettingsPageColumn(contentPadding) {
        SectionLabel("Тема и цвета")
        ThemeSection(settings)

        AppIconSection()

        SectionLabel("Панели")
        GlassPreview(glass.blurRadius, glass.opacity)
        SettingGroup {
            item {
                SettingRow(
                    title = "Размытие",
                    subtitle = glass.blurRadius.roundToInt().toString(),
                    belowFullWidth = true,
                    below = {
                        DotSlider(glass.blurRadius, settings::setBlur, 0f..GlassSettings.MAX_BLUR)
                    },
                )
            }
            item { BlurTypeRow(glass.blurType, settings::setBlurType) }
            item {
                SettingRow(
                    title = "Плотность панелей",
                    subtitle = "${(glass.opacity * 100).roundToInt()} %",
                    belowFullWidth = true,
                    below = { DotSlider(glass.opacity, settings::setOpacity, 0f..1f) },
                )
            }
            item {
                SettingRow(
                    title = "Сбросить по умолчанию",
                    subtitle = "Размытие, тип размытия и плотность панелей",
                    onClick = settings::resetGlass,
                )
            }
        }
        SettingsFootnote("Предпросмотр показывает размытие по Гауссу, выбранный тип применяется к самим панелям.")

        PlateSection(plates)
    }
}