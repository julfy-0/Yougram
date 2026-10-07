package app.yougram.feature.settings.general

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.component.Avatar
import app.yougram.feature.chat.data.ChatRepository
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import kotlinx.coroutines.launch

/** Архив чатов теперь живёт в настройках, а не в списке чатов. */
@Composable
fun ArchiveScreen(
    chats: ChatRepository,
    contentPadding: PaddingValues,
    onOpenChat: (Long) -> Unit,
) {
    val archived by chats.archivedChats.collectAsState()
    val scope = rememberCoroutineScope()

    // Подгружаем весь архив порциями, пока TDLib не скажет, что чатов больше нет.
    LaunchedEffect(Unit) {
        repeat(20) {
            val more = runCatching { chats.loadChats(archive = true) }.getOrDefault(false)
            if (!more) return@LaunchedEffect
        }
    }

    SettingsPageColumn(contentPadding) {
        if (archived.isEmpty()) {
            SettingsFootnote("Архив пуст. Чат можно отправить в архив долгим нажатием в списке чатов.")
        } else {
            SectionLabel("Архивные чаты (${archived.size})")
            SettingGroup {
                archived.forEach { chat ->
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenChat(chat.id) }
                                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(title = chat.title, path = chat.avatarPath)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    chat.lastMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (chat.unreadCount > 0) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.outline) {
                                    Text(
                                        chat.unreadCount.toString(),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.surface,
                                    )
                                }
                            }
                            IconButton(onClick = { scope.launch { runCatching { chats.setChatArchived(chat.id, false) } } }) {
                                Icon(Icons.Filled.Unarchive, contentDescription = "Вернуть из архива")
                            }
                        }
                    }
                }
            }
            SettingsFootnote("Нажмите на чат, чтобы открыть его, или на значок справа, чтобы вернуть в список чатов.")
        }
    }
}
