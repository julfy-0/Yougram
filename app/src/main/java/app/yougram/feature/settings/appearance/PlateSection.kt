package app.yougram.feature.settings.appearance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yougram.core.settings.PlateArea
import app.yougram.core.settings.PlateRepository
import app.yougram.feature.settings.component.DotSlider
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import kotlin.math.roundToInt

/** Настройка прозрачности подложек: отдельный ползунок для чатов, контактов, звонков и настроек. */
@Composable
fun PlateSection(plates: PlateRepository) {
    val values by plates.plates.collectAsState()

    SectionLabel("Подложки")
    SettingGroup {
        PlateArea.entries.forEach { area ->
            item {
                SettingRow(
                    title = area.title,
                    subtitle = "Прозрачность ${(values.of(area) * 100).roundToInt()} %",
                    below = { DotSlider(values.of(area), { plates.set(area, it) }, 0f..1f) },
                )
            }
        }
        item {
            SettingRow(
                title = "Сбросить подложки",
                subtitle = "Вернуть значения по умолчанию",
                onClick = plates::reset,
            )
        }
    }
}
