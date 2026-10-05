package app.yougram.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yougram.data.PlateArea
import app.yougram.data.PlateRepository
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
                subtitle = "Вернуть сплошной фон везде",
                onClick = plates::reset,
            )
        }
    }
}
