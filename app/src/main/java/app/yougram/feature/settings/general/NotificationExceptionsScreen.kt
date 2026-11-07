package app.yougram.feature.settings.general

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.core.telegram.getOrThrow
import app.yougram.feature.chat.data.ChatRepository
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.NotificationSettingsScope
import dev.g000sha256.tdl.dto.NotificationSettingsScopeChannelChats
import dev.g000sha256.tdl.dto.NotificationSettingsScopeGroupChats
import dev.g000sha256.tdl.dto.NotificationSettingsScopePrivateChats
import kotlinx.coroutines.launch

private data class ExceptionRow(val chatId: Long, val title: String, val muted: Boolean)

private val ScopeTitles = listOf("Личные чаты", "Группы", "Каналы")

private fun scopeOf(index: Int): NotificationSettingsScope = when (index) {
    0 -> NotificationSettingsScopePrivateChats()
    1 -> NotificationSettingsScopeGroupChats()
    else -> NotificationSettingsScopeChannelChats()
}

/** Чаты с особыми настройками уведомлений (аналог «Исключений» в настройках уведомлений Telegram). */
@Composable
fun NotificationExceptionsScreen(
    chats: ChatRepository,
    client: TdlClient,
    contentPadding: PaddingValues,
    onOpenChat: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<ExceptionRow>?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, reload) {
        rows = null
        rows = runCatching {
            val ids = client.getChatNotificationSettingsExceptions(scope = scopeOf(tab), compareSound = false).getOrThrow().chatIds
            ids.map { id ->
                val settings = client.getChat(chatId = id).getOrThrow().notificationSettings
                ExceptionRow(
                    chatId = id,
                    title = runCatching { chats.getChatTitle(id) }.getOrDefault(id.toString()),
                    muted = !settings.useDefaultMuteFor && settings.muteFor > 0,
                )
            }
        }.getOrElse {
            Toast.makeText(context, it.message ?: tr("Ошибка"), Toast.LENGTH_SHORT).show()
            emptyList()
        }
    }

    SettingsPageColumn(contentPadding) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScopeTitles.forEachIndexed { i, title ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(title) })
            }
        }

        val list = rows
        when {
            list == null -> SettingsFootnote("Загрузка…")
            list.isEmpty() -> SettingsFootnote("Исключений нет: все чаты используют общие настройки уведомлений.")
            else -> {
                SectionLabel(tr("Чаты с особыми настройками ({0})", list.size))
                SettingGroup {
                    list.forEach { row ->
                        item {
                            SettingRow(
                                title = row.title,
                                subtitle = if (row.muted) "Уведомления выключены" else "Уведомления включены, настройки особые",
                                onClick = { onOpenChat(row.chatId) },
                                trailing = {
                                    IconButton(onClick = {
                                        scope.launch {
                                            runCatching { chats.setMuted(row.chatId, !row.muted) }
                                            reload++
                                        }
                                    }) {
                                        Icon(
                                            if (row.muted) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                                            contentDescription = tr(if (row.muted) "Включить уведомления" else "Выключить уведомления"),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
