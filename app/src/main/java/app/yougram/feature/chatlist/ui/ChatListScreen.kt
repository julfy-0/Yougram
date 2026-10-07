package app.yougram.feature.chatlist.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.PlateArea
import app.yougram.core.ui.component.Avatar
import app.yougram.core.ui.glass.plateColor
import app.yougram.feature.badge.ui.YougramBadge
import app.yougram.feature.chat.data.ChatItem
import java.text.DateFormat
import java.util.Date

/**
 * Список чатов без собственных панелей: верхняя и нижняя панели рисуются снаружи (MainScreen),
 * а список прокручивается под ними, поэтому отступы приходят в [contentPadding].
 */
@Composable
fun ChatListScreen(
    viewModel: ChatListViewModel,
    query: String,
    contentPadding: PaddingValues,
    onOpenChat: (Long) -> Unit,
    /** 2 или 3 строки в строке чата: при трёх последнее сообщение занимает до двух строк. */
    lines: Int = 2,
    /** Чат, открытый в правой панели на планшете: его строка подсвечивается. */
    selectedChatId: Long? = null,
) {
    val chats by viewModel.chats.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val selectedId by viewModel.selectedFolder.collectAsState()
    val error by viewModel.error.collectAsState()
    val archived by viewModel.archivedChats.collectAsState()
    val archiveOpen by viewModel.archiveOpen.collectAsState()

    BackHandler(enabled = archiveOpen) { viewModel.closeArchive() }

    // Если выбранная папка исчезла (удалена в другом клиенте) — возвращаемся на «Все».
    val folderId = selectedId?.takeIf { id -> folders.any { it.id == id } }

    val visible = remember(chats, archived, archiveOpen, query, folderId) {
        val base = if (archiveOpen) {
            archived
        } else if (folderId == null) {
            chats
        } else {
            chats
                .filter { (it.folderOrders[folderId] ?: 0L) != 0L }
                .sortedByDescending { it.folderOrders[folderId] ?: 0L }
        }
        val q = query.trim()
        if (q.isEmpty()) base else base.filter { it.title.contains(q, ignoreCase = true) }
    }

    val listState = rememberLazyListState()
    // Когда до конца списка осталось меньше 10 строк — просим следующую порцию чатов.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 10
        }.collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
    }
    // При смене папки показываем список с начала.
    LaunchedEffect(folderId, archiveOpen) { listState.scrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
        when {
            chats.isEmpty() && archived.isEmpty() && !archiveOpen && error == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            chats.isEmpty() && archived.isEmpty() && !archiveOpen -> Text(
                error.orEmpty(),
                Modifier.align(Alignment.Center).padding(contentPadding),
                color = MaterialTheme.colorScheme.error,
            )
            else -> LazyColumn(
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
                if (archiveOpen) {
                    item(key = "archive_back") {
                        ArchiveRow(
                            title = "Архив",
                            subtitle = "Назад к чатам",
                            unread = 0,
                            onClick = viewModel::closeArchive,
                        )
                    }
                } else if (folderId == null && query.isBlank() && archived.isNotEmpty()) {
                    item(key = "archive_entry") {
                        ArchiveRow(
                            title = "Архив",
                            subtitle = archived.joinToString(", ", limit = 3) { it.title },
                            unread = archived.sumOf { it.unreadCount },
                            onClick = viewModel::openArchive,
                        )
                    }
                }
                if (visible.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            Text(
                                when {
                                    archiveOpen && query.isBlank() -> "Архив пуст"
                                    query.isBlank() && folderId != null -> "В папке нет чатов"
                                    else -> "Ничего не найдено"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                itemsIndexed(visible, key = { _, chat -> chat.id }) { index, chat ->
                    // animateItem — плавная перестановка при обновлении порядка чатов;
                    // appearOnEnter — анимация, когда строка появляется при прокрутке.
                    val interaction = remember(chat.id) { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val selected = chat.id == selectedChatId
                    val topBase = if (index == 0) 24.dp else 6.dp
                    val bottomBase = if (index == visible.size - 1) 24.dp else 6.dp
                    val radiusBoost by animateDpAsState(
                        targetValue = if (pressed || selected) 30.dp else 0.dp,
                        animationSpec = tween(180),
                        label = "chatCornerBoost",
                    )
                    val topRadius = (topBase + radiusBoost).coerceAtMost(32.dp)
                    val bottomRadius = (bottomBase + radiusBoost).coerceAtMost(32.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .appearOnEnter(),
                        shape = RoundedCornerShape(
                            topStart = topRadius,
                            topEnd = topRadius,
                            bottomStart = bottomRadius,
                            bottomEnd = bottomRadius,
                        ),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        } else plateColor(PlateArea.Chats),
                    ) {
                        ChatRow(
                            chat,
                            lines,
                            onClick = { onOpenChat(chat.id) },
                            interactionSource = interaction,
                            archived = archiveOpen,
                            onToggleArchive = { viewModel.setArchived(chat.id, !archiveOpen) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Строка проявляется, слегка подъезжает снизу и увеличивается до нормального размера.
 * Состояние живёт, пока строка в композиции: уехала за экран и вернулась — анимация проигрывается снова.
 * Прогресс читается в graphicsLayer (фаза отрисовки), поэтому рекомпозиций нет.
 */
@Composable
private fun Modifier.appearOnEnter(): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 320, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 28.dp.toPx()
        val scale = 0.94f + 0.06f * p
        scaleX = scale
        scaleY = scale
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatRow(
    chat: ChatItem,
    lines: Int,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    archived: Boolean,
    onToggleArchive: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { menuOpen = true },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                YougramBadge(chat.id, Modifier.padding(start = 4.dp))
            }
            Text(
                chat.lastMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (lines >= 3) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (chat.lastMessageDate != 0) {
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(chat.lastMessageDate * 1000L)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (chat.unreadCount > 0) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Text(
                        chat.unreadCount.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text(if (archived) "Вернуть из архива" else "В архив") },
            onClick = {
                menuOpen = false
                onToggleArchive()
            },
        )
    }
    }
}

@Composable
private fun ArchiveRow(title: String, subtitle: String, unread: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = plateColor(PlateArea.Chats),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(title = title, path = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (unread > 0) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.outline) {
                    Text(
                        unread.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.surface,
                    )
                }
            }
        }
    }
}
