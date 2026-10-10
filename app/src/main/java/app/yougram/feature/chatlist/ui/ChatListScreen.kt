package app.yougram.feature.chatlist.ui

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.PlateArea
import app.yougram.core.ui.component.Avatar
import app.yougram.core.ui.DeviceTier
import app.yougram.core.ui.glass.LocalPlates
import app.yougram.core.ui.glass.plateColor
import app.yougram.core.ui.rememberDeviceTier
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
    val motion = MaterialTheme.motionScheme
    val lowTier = rememberDeviceTier() == DeviceTier.Low
    val plates = LocalPlates.current
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
            EmptyChats(Modifier.align(Alignment.Center).padding(contentPadding))
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
                val pinned = (key ?: 0) in chat.pinnedLists
                // Закреплённые чаты выделены контейнером secondaryContainer (с учётом прозрачности подложек), а не только иконкой.
                val container = when {
                    highlighted -> MaterialTheme.colorScheme.primaryContainer
                    pinned -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 1f - plates.of(PlateArea.Chats))
                    else -> plateColor(PlateArea.Chats)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (lowTier) Modifier else Modifier.animateItem(
                                fadeInSpec = motion.defaultEffectsSpec(),
                                placementSpec = motion.defaultSpatialSpec(),
                                fadeOutSpec = motion.fastEffectsSpec(),
                            ),
                        ),
                    shape = segmentShape(index, chats.size),
                    color = container,
                ) {
                    ChatRow(
                        chat = chat,
                        lines = lines,
                        selected = selected,
                        highlighted = highlighted,
                        pinned = pinned,
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

/** Пустое состояние: «цветочная» форма Expressive с иконкой, крупный заголовок и пояснение. */
@Composable
private fun EmptyChats(modifier: Modifier = Modifier) {
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(112.dp).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("Чатов нет", style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ChatRow(
    chat: ChatItem,
    lines: Int,
    selected: Boolean,
    highlighted: Boolean,
    pinned: Boolean,
    modifier: Modifier,
) {
    // На подсвеченной строке (primaryContainer) вторичный текст берём из onPrimaryContainer, чтобы контраст не терялся.
    val secondary = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
    else MaterialTheme.colorScheme.onSurfaceVariant
    // Выбранный чат: аватарка из круга «перетекает» в скруглённый квадрат с галочкой.
    val avatarCorner by animateDpAsState(
        targetValue = if (selected) 14.dp else 24.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "avatarCorner",
    )
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(title = chat.title, path = chat.avatarPath, shape = RoundedCornerShape(avatarCorner.coerceAtLeast(0.dp)))
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.BottomEnd).size(20.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = "Выбрано", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
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
                        tint = secondary,
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
                    color = secondary,
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    chat.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondary,
                    maxLines = if (lines >= 3) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (chat.unreadCount > 0) {
                    // Бейдж непрочитанного: pill-контейнер минимум 22 dp (одна цифра — круг), у заглушенных нейтральный.
                    Surface(
                        shape = CircleShape,
                        color = if (chat.muted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary,
                        contentColor = if (chat.muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(start = 8.dp).defaultMinSize(minWidth = 22.dp, minHeight = 22.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (chat.unreadCount > 999) "999+" else chat.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmallEmphasized,
                            )
                        }
                    }
                } else if (pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Закреплён",
                        tint = secondary,
                        modifier = Modifier.padding(start = 8.dp).size(16.dp),
                    )
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