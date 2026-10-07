package app.yougram.feature.settings.general

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.yougram.core.settings.SettingsRepository
import app.yougram.feature.settings.component.ChoiceDialog
import app.yougram.feature.settings.component.ConfirmDialog
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.component.SwitchRow

private val CallVibrationOptions = listOf("По умолчанию", "Выключено", "Короткий", "Длинный")
private val RingtoneOptions = listOf("По умолчанию", "Без звука")
private val RepeatOptions = listOf(0, 5, 10, 30, 60, 120, 240)

private fun repeatLabel(minutes: Int): String = when {
    minutes == 0 -> "Никогда"
    minutes < 60 -> "$minutes мин"
    minutes == 60 -> "1 час"
    else -> "${minutes / 60} ч"
}

private fun enabledLabel(enabled: Boolean) = if (enabled) "Включены" else "Отключены"

private enum class NotificationDialog { CallVibration, Ringtone, Repeat, Reset }

@Composable
fun NotificationsScreen(settings: SettingsRepository, contentPadding: PaddingValues) {
    val n by settings.notifications.collectAsState()
    var dialog by remember { mutableStateOf<NotificationDialog?>(null) }

    SettingsPageColumn(contentPadding) {
        SectionLabel("Уведомления из чатов")
        SettingGroup {
            item { SwitchRow("Личные чаты", n.privateChats, { v -> settings.updateNotifications { it.copy(privateChats = v) } }, subtitle = enabledLabel(n.privateChats), icon = Icons.Filled.Person) }
            item { SwitchRow("Группы", n.groups, { v -> settings.updateNotifications { it.copy(groups = v) } }, subtitle = enabledLabel(n.groups), icon = Icons.Filled.Group) }
            item { SwitchRow("Каналы", n.channels, { v -> settings.updateNotifications { it.copy(channels = v) } }, subtitle = enabledLabel(n.channels), icon = Icons.Filled.Campaign) }
            item { SwitchRow("Истории", n.stories, { v -> settings.updateNotifications { it.copy(stories = v) } }, subtitle = enabledLabel(n.stories), icon = Icons.Filled.AutoStories) }
            item { SwitchRow("Реакции", n.reactions, { v -> settings.updateNotifications { it.copy(reactions = v) } }, subtitle = enabledLabel(n.reactions), icon = Icons.Filled.Favorite) }
        }

        SectionLabel("Звонки")
        SettingGroup {
            item { SettingRow("Вибросигнал", value = CallVibrationOptions.getOrElse(n.callVibration) { CallVibrationOptions[0] }, onClick = { dialog = NotificationDialog.CallVibration }) }
            item { SettingRow("Рингтон", value = RingtoneOptions.getOrElse(n.ringtone) { RingtoneOptions[0] }, onClick = { dialog = NotificationDialog.Ringtone }) }
        }

        SectionLabel("Счётчик сообщений")
        SettingGroup {
            item { SwitchRow("Показывать счётчик", n.showCounter, { v -> settings.updateNotifications { it.copy(showCounter = v) } }) }
            item { SwitchRow("Чаты без уведомлений", n.countMuted, { v -> settings.updateNotifications { it.copy(countMuted = v) } }) }
            item { SwitchRow("Число сообщений", n.countMessages, { v -> settings.updateNotifications { it.copy(countMessages = v) } }) }
        }

        SectionLabel("В приложении")
        SettingGroup {
            item { SwitchRow("Звук", n.sound, { v -> settings.updateNotifications { it.copy(sound = v) } }) }
            item { SwitchRow("Вибросигнал", n.vibration, { v -> settings.updateNotifications { it.copy(vibration = v) } }) }
            item { SwitchRow("Показывать текст", n.preview, { v -> settings.updateNotifications { it.copy(preview = v) } }) }
            item { SwitchRow("Звук в чате", n.chatSound, { v -> settings.updateNotifications { it.copy(chatSound = v) } }) }
            item { SwitchRow("Всплывающие окна", n.popups, { v -> settings.updateNotifications { it.copy(popups = v) } }, subtitle = "Показывать всплывающие окна, когда приложение открыто.") }
        }

        SectionLabel("События")
        SettingGroup {
            item { SwitchRow("Контакт присоединился к Telegram", n.contactJoined, { v -> settings.updateNotifications { it.copy(contactJoined = v) } }) }
            item { SwitchRow("Закреплённые сообщения", n.pinnedMessages, { v -> settings.updateNotifications { it.copy(pinnedMessages = v) } }) }
        }

        SectionLabel("Другое")
        SettingGroup {
            item { SwitchRow("Перезапуск при закрытии", n.restartOnClose, { v -> settings.updateNotifications { it.copy(restartOnClose = v) } }, subtitle = "Перезапускать приложение, если оно закрыто. Обеспечивает надёжность уведомлений.") }
            item { SwitchRow("Фоновое соединение", n.backgroundConnection, { v -> settings.updateNotifications { it.copy(backgroundConnection = v) } }, subtitle = "Поддерживать минимальное фоновое соединение с Telegram, чтобы получать уведомления. Обеспечивает надёжность уведомлений.") }
            item { SettingRow("Повтор уведомлений", value = repeatLabel(n.repeatMinutes), onClick = { dialog = NotificationDialog.Repeat }) }
        }

        SectionLabel("Сброс")
        SettingGroup {
            item { SettingRow("Сбросить настройки уведомлений", subtitle = "Сбросить все особые настройки уведомлений для отдельных контактов, групп и каналов.", onClick = { dialog = NotificationDialog.Reset }) }
        }
    }

    when (dialog) {
        NotificationDialog.CallVibration -> ChoiceDialog(
            title = "Вибросигнал",
            options = CallVibrationOptions.indices.toList(),
            selected = n.callVibration,
            label = { CallVibrationOptions[it] },
            onSelect = { v -> settings.updateNotifications { it.copy(callVibration = v) }; dialog = null },
            onDismiss = { dialog = null },
        )
        NotificationDialog.Ringtone -> ChoiceDialog(
            title = "Рингтон",
            options = RingtoneOptions.indices.toList(),
            selected = n.ringtone,
            label = { RingtoneOptions[it] },
            onSelect = { v -> settings.updateNotifications { it.copy(ringtone = v) }; dialog = null },
            onDismiss = { dialog = null },
        )
        NotificationDialog.Repeat -> ChoiceDialog(
            title = "Повтор уведомлений",
            options = RepeatOptions,
            selected = n.repeatMinutes,
            label = ::repeatLabel,
            onSelect = { v -> settings.updateNotifications { it.copy(repeatMinutes = v) }; dialog = null },
            onDismiss = { dialog = null },
        )
        NotificationDialog.Reset -> ConfirmDialog(
            title = "Сбросить настройки?",
            text = "Все настройки уведомлений вернутся к значениям по умолчанию.",
            confirmLabel = "Сбросить",
            onConfirm = { settings.resetNotifications(); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}
