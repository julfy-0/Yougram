package app.yougram.feature.settings.general

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yougram.core.settings.SettingsRepository
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.component.SwitchRow

@Composable
fun PowerSavingScreen(settings: SettingsRepository, contentPadding: PaddingValues) {
    val enabled by settings.powerSaving.collectAsState()

    SettingsPageColumn(contentPadding) {
        SettingGroup {
            item {
                SwitchRow(
                    "Режим энергосбережения",
                    enabled,
                    settings::setPowerSaving,
                    subtitle = "Отключает размытие панелей, меню и фона под сообщениями",
                    icon = Icons.Filled.BatterySaver,
                )
            }
        }
        SettingsFootnote("При выключении возвращается прежнее значение размытия из раздела «Внешний вид».")
    }
}