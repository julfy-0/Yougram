package app.yougram.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.yougram.feature.chat.data.MessageItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Набор по умолчанию, пока не пришёл список реакций, доступных в чате. */
val DefaultReactions = listOf("❤️", "👍", "👎", "🔥", "🥰", "👏", "😁")
private val DateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm")

private fun stamp(date: Int): String {
    val at = Instant.ofEpochSecond(date.toLong()).atZone(ZoneId.systemDefault())
    val today = LocalDate.now(at.zone)
    val day = when (at.toLocalDate()) {
        today -> "сегодня"
        today.minusDays(1) -> "вчера"
        else -> at.format(DateFmt)
    }
    return "$day, ${at.format(TimeFmt)}"
}

/** Меню сообщения по долгому тапу: реакции сверху и список действий. */
@Composable
fun MessageMenu(
    message: MessageItem,
    isRead: Boolean,
    canEdit: Boolean,
    canSave: Boolean,
    canShadowBan: Boolean,
    canWatchTyping: Boolean,
    watchingTyping: Boolean,
    reactions: List<String>,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onSave: () -> Unit,
    /** Сохранить через системный проводник; null — пункта нет. */
    onExport: (() -> Unit)? = null,
    onForward: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShadowBan: () -> Unit,
    onToggleTypingWatch: () -> Unit,
) {
    val mine = message.reactions.filter { it.chosen }.map { it.emoji }.toSet()
    Dialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    Modifier
                        .widthIn(max = 320.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    reactions.forEach { emoji ->
                        Text(
                            emoji,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (emoji in mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    else Color.Transparent,
                                )
                                .clickable { onReact(emoji); onDismiss() }
                                .padding(4.dp),
                        )
                    }
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.width(260.dp).padding(vertical = 6.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (message.isOutgoing) {
                            Icon(
                                if (isRead) Icons.Filled.DoneAll else Icons.Filled.Done,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(stamp(message.date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MenuItem(Icons.AutoMirrored.Filled.Reply, "Ответить") { onReply(); onDismiss() }
                    if (canSave) MenuItem(Icons.Filled.Download, "Сохранить в галерею") { onSave(); onDismiss() }
                    if (canSave && onExport != null) MenuItem(Icons.Filled.FolderOpen, "Сохранить в файлы") { onExport(); onDismiss() }
                    MenuItem(Icons.AutoMirrored.Filled.Send, "Переслать") { onForward(); onDismiss() }
                    MenuItem(Icons.Filled.PushPin, "Закрепить") { onPin(); onDismiss() }
                    if (canEdit) MenuItem(Icons.Filled.Edit, "Изменить") { onEdit(); onDismiss() }
                    if (canWatchTyping) MenuItem(
                        Icons.Filled.Visibility,
                        if (watchingTyping) "Не следить за набором" else "Следить за набором",
                    ) { onToggleTypingWatch(); onDismiss() }
                    if (canShadowBan) MenuItem(Icons.Filled.VisibilityOff, "Теневой бан автора") { onShadowBan(); onDismiss() }
                    MenuItem(Icons.Filled.Delete, "Удалить", tint = MaterialTheme.colorScheme.error) { onDelete(); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    val color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = color, style = MaterialTheme.typography.bodyLarge)
    }
}