package app.yougram.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.GlassSettings
import app.yougram.core.settings.PlateRepository
import app.yougram.core.settings.SettingsRepository
import app.yougram.feature.settings.appearance.AppIconSection
import app.yougram.feature.settings.appearance.PlateSection
import app.yougram.feature.settings.appearance.ThemeSection
import app.yougram.feature.settings.component.DotSlider
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(settings: SettingsRepository, plates: PlateRepository, contentPadding: PaddingValues) {
    val glass by settings.glass.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("Тема и цвета")
        ThemeSection(settings)

        AppIconSection()

        SectionLabel("Панели")
        SettingGroup {
            item {
                SettingRow(
                    title = "Размытие",
                    subtitle = glass.blurRadius.roundToInt().toString(),
                    below = {
                        DotSlider(glass.blurRadius, settings::setBlur, 0f..GlassSettings.MAX_BLUR)
                    },
                )
            }
            item {
                SettingRow(
                    title = "Плотность панелей",
                    subtitle = "${(glass.opacity * 100).roundToInt()} %",
                    below = { DotSlider(glass.opacity, settings::setOpacity, 0f..1f) },
                )
            }
            item {
                SettingRow(
                    title = "Сбросить по умолчанию",
                    subtitle = "Размытие и плотность панелей",
                    onClick = settings::resetGlass,
                )
            }
        }

        PlateSection(plates)
    }
}