package app.yougram.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.yougram.data.SettingsRepository

@Composable
fun NotificationsScreen(settings: SettingsRepository, contentPadding: PaddingValues) {
    val n by settings.notifications.collectAsState()

    SettingsPageColumn(contentPadding) {
        SectionLabel("Уведомления о сообщениях")
        SettingGroup {
            item { SwitchRow("Личные чаты", n.privateChats, { v -> settings.updateNotifications { it.copy(privateChats = v) } }, icon = Icons.Filled.Person) }
            item { SwitchRow("Группы", n.groups, { v -> settings.updateNotifications { it.copy(groups = v) } }, icon = Icons.Filled.Group) }
            item { SwitchRow("Каналы", n.channels, { v -> settings.updateNotifications { it.copy(channels = v) } }, icon = Icons.Filled.Campaign) }
        }

        SectionLabel("Параметры")
        SettingGroup {
            item { SwitchRow("Показывать текст", n.preview, { v -> settings.updateNotifications { it.copy(preview = v) } }, subtitle = "Текст сообщения в уведомлении", icon = Icons.Filled.Visibility) }
            item { SwitchRow("Звук", n.sound, { v -> settings.updateNotifications { it.copy(sound = v) } }, icon = Icons.Filled.VolumeUp) }
            item { SwitchRow("Вибрация", n.vibration, { v -> settings.updateNotifications { it.copy(vibration = v) } }, icon = Icons.Filled.Vibration) }
        }
        SettingsFootnote("Параметры сохраняются и будут применены, когда в Yougram появятся push-уведомления.")
    }
}