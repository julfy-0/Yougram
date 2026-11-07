package app.yougram.feature.settings.hub

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.runtime.Composable
import app.yougram.feature.settings.SettingsPage
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsPageColumn
import java.util.Locale

/** Вкладка «Система»: данные и память, энергосбережение и язык. */
@Composable
fun SystemHubScreen(contentPadding: PaddingValues, onNavigate: (SettingsPage) -> Unit) {
    val language = Locale.getDefault().let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.uppercase() } }
    SettingsPageColumn(contentPadding) {
        SectionLabel("Данные и ресурсы")
        SettingGroup {
            item { SettingRow("Данные и память", subtitle = "Кэш, автозагрузка медиа", icon = Icons.Filled.PieChart, onClick = { onNavigate(SettingsPage.DataStorage) }) }
            item { SettingRow("Использование сети", subtitle = "Трафик по Wi-Fi, мобильной сети и звонкам", icon = Icons.Filled.DataUsage, onClick = { onNavigate(SettingsPage.NetworkUsage) }) }
            item { SettingRow("Энергосбережение", subtitle = "Экономия заряда", icon = Icons.Filled.BatterySaver, onClick = { onNavigate(SettingsPage.PowerSaving) }) }
        }

        SectionLabel("Контент")
        SettingGroup {
            item { SettingRow("Стикеры и реакции", subtitle = "Наборы, архив, популярные, быстрая реакция", icon = Icons.Filled.EmojiEmotions, onClick = { onNavigate(SettingsPage.Stickers) }) }
        }

        SectionLabel("Общее")
        SettingGroup {
            item { SettingRow("Язык", icon = Icons.Filled.Language, value = language, onClick = { onNavigate(SettingsPage.Language) }) }
        }
    }
}
