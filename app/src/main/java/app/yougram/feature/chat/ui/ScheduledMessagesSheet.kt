package app.yougram.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.feature.chat.data.ScheduledItem
import app.yougram.feature.chat.data.SendSchedule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Список запланированных сообщений чата: отправить сейчас, изменить время, удалить. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesSheet(
    items: List<ScheduledItem>,
    canWhenOnline: Boolean,
    onDismiss: () -> Unit,
    onSendNow: (Long) -> Unit,
    onReschedule: (Long, SendSchedule) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var editingId by remember { mutableStateOf<Long?>(null) }
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(
                "Запланированные сообщения",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (items.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                    Text("Нет запланированных сообщений", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.text.ifBlank { tr("Сообщение") },
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        if (item.whenOnline) tr("Когда будет в сети")
                                        else formatter.format(Instant.ofEpochSecond(item.sendDate.toLong())),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(onClick = { onSendNow(item.id) }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, tr("Отправить сейчас"))
                                }
                                IconButton(onClick = { editingId = item.id }) {
                                    Icon(Icons.Filled.Schedule, tr("Изменить время"))
                                }
                                IconButton(onClick = { onDelete(item.id) }) {
                                    Icon(Icons.Filled.Delete, tr("Удалить"), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingId?.let { id ->
        ScheduleSendDialog(
            canWhenOnline = canWhenOnline,
            onDismiss = { editingId = null },
            onConfirm = { schedule ->
                editingId = null
                onReschedule(id, schedule)
            },
        )
    }
}