package app.yougram.feature.settings.extras

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.yougram.feature.settings.component.CheckSwitch
import app.yougram.plugin.PluginSetting
import app.yougram.plugin.PluginSettingType

/** Одно поле настройки плагина; тип поля берётся из схемы manifest.json. Значение — в строковом виде, как его хранит PluginSettingsStore. */
@Composable
fun PluginSettingField(setting: PluginSetting, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (setting.type) {
            PluginSettingType.SWITCH -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(setting.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                CheckSwitch(value == "true") { onChange(it.toString()) }
            }
            PluginSettingType.TEXT, PluginSettingType.NUMBER -> OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(setting.title) },
                singleLine = setting.type == PluginSettingType.NUMBER,
                keyboardOptions = if (setting.type == PluginSettingType.NUMBER) {
                    KeyboardOptions(keyboardType = KeyboardType.Decimal)
                } else {
                    KeyboardOptions.Default
                },
                modifier = Modifier.fillMaxWidth(),
            )
            PluginSettingType.CHOICE -> {
                Text(setting.title, style = MaterialTheme.typography.titleMedium)
                setting.options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onChange(option.value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == option.value, onClick = { onChange(option.value) })
                        Text(option.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        if (setting.description.isNotBlank()) {
            Text(
                setting.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
