package app.yougram.feature.chatlist.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.PlateArea
import app.yougram.core.ui.component.Avatar
import app.yougram.core.ui.glass.plateColor
import app.yougram.feature.chat.data.ChatItem
import app.yougram.feature.settings.component.segmentShape
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Список чатов одной вкладки: «Все» ([folderId] = null) или выбранной папки.
 * Каждая страница горизонтального пейджера в MainScreen — отдельный экземпляр этого списка.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    query: String,
    contentPadding: PaddingValues,
    onOpenChat: (Long) -> Unit,
    lines: Int = 2,
    selectedChatId: Long? = null,
    folderId: Int? = null,
) {
    val context = LocalContext.current
    val all by viewModel.chats.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val selection by viewModel.selected.collectAsState()
    val error by viewModel.error.collectAsState()

    // Папка могла исчезнуть, пока страница ещё на экране: тогда показываем общий список.
    val key = folderId?.takeIf { id -> folders.any { it.id == id } }
    val chats = remember(all, key, query) {
        val q = query.trim()
        all.asSequence()
            .filter { c -> if (key == null) c.order != 0L && c.archiveOrder == 0L else (c.folderOrders[key] ?: 0L) != 0L }
            .filter { c -> q.isEmpty() || c.title.contains(q, ignoreCase = true) }
            .sortedByDescending { c -> if (key == null) c.order else c.folderOrders[key] ?: 0L }
            .toList()
    }

    LaunchedEffect(error) { error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }

    val listState = rememberLazyListState()
    // Подгружаем следующую порцию, когда дошли почти до конца списка (или он короткий и всё влезло).
    val nearEnd by remember(listState, chats.size) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= chats.size - 8
        }
    }
    LaunchedEffect(nearEnd, chats.size, key) { if (nearEnd) viewModel.loadMore(key) }

    Box(Modifier.fillMaxSize()) {
        if (chats.isEmpty()) {
            Text(
                "Чатов нет",
                Modifier.align(Alignment.Center).padding(contentPadding),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(chats, key = { _, c -> c.id }) { index, chat ->
                val selected = chat.id in selection
                val highlighted = selected || chat.id == selectedChatId
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = segmentShape(index, chats.size),
                    color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else plateColor(PlateArea.Chats),
                ) {
                    ChatRow(
                        chat = chat,
                        lines = lines,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (selection.isNotEmpty()) viewModel.toggleSelected(chat.id) else onOpenChat(chat.id)
                            },
                            onLongClick = { viewModel.toggleSelected(chat.id) },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatItem, lines: Int, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title = chat.title, path = chat.avatarPath)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (chat.muted) {
                    Icon(
                        Icons.Filled.NotificationsOff,
                        contentDescription = "Без звука",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (chat.lastOutgoing) {
                    Icon(
                        if (chat.lastRead) Icons.Filled.DoneAll else Icons.Filled.Done,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 2.dp).size(16.dp),
                    )
                }
                Text(
                    formatChatTime(chat.lastMessageDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    chat.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (lines >= 3) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (chat.unreadCount > 0) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(if (chat.muted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (chat.unreadCount > 999) "999+" else chat.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

private fun formatChatTime(date: Int): String {
    if (date == 0) return ""
    val ms = date * 1000L
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = when {
        sameDay -> "HH:mm"
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> "dd.MM"
        else -> "dd.MM.yy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
}
