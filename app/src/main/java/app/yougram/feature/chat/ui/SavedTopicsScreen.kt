package app.yougram.feature.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.yougram.core.di.AppContainer
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.core.settings.PlateArea
import app.yougram.core.ui.component.LoadingIndicator
import app.yougram.core.ui.glass.LocalGlass
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.glassTopBar
import app.yougram.core.ui.glass.plateColor
import app.yougram.core.ui.glass.rememberBackdropState
import app.yougram.feature.chat.data.ChatRepository
import app.yougram.feature.chat.data.SavedTagItem
import app.yougram.feature.chat.data.SavedTopicItem
import app.yougram.feature.chat.data.SavedTopicKind
import app.yougram.feature.settings.component.segmentShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private const val NONE = Long.MIN_VALUE
private const val ALL = 0L

/**
 * «Избранное» со списком тем: сначала список источников пересылки, нажатие открывает чат с сообщениями
 * только этого источника. Открытие — справа налево, закрытие — слева направо.
 */
@Composable
fun SavedMessagesHost(
    container: AppContainer,
    chatId: Long,
    onBack: () -> Unit,
    onOpenChatProfile: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    onCall: (Long, Boolean) -> Unit,
    onOpenComments: (Long, Long) -> Unit,
    showBack: Boolean,
) {
    var selected by rememberSaveable { mutableStateOf(NONE) }
    var selectedTag by rememberSaveable { mutableStateOf("") }
    fun close() { selected = NONE; selectedTag = "" }
    BackHandler(enabled = selected != NONE) { close() }

    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            val opening = targetState != NONE
            val enter = slideInHorizontally(tween(300)) { if (opening) it else -it / 3 } + fadeIn(tween(300))
            val exit = slideOutHorizontally(tween(300)) { if (opening) -it / 3 else it } + fadeOut(tween(300))
            // Закрываемый экран уезжает поверх списка.
            ContentTransform(enter, exit, targetContentZIndex = if (opening) 1f else -1f)
        },
        label = "saved-topics",
    ) { topic ->
        if (topic == NONE) {
            SavedTopicsScreen(
                repository = container.chatRepository,
                chatId = chatId,
                showBack = showBack,
                onBack = onBack,
                onOpenTopic = { id, tag -> selected = id; selectedTag = tag },
                onOpenChatProfile = onOpenChatProfile,
            )
        } else {
            val vm: ChatViewModel = viewModel(
                key = if (topic == ALL && selectedTag.isEmpty()) "chat-$chatId-0" else "chat-$chatId-saved-$topic-$selectedTag",
                factory = ChatViewModel.factory(
                    repository = container.chatRepository,
                    chatId = chatId,
                    settings = container.settings,
                    savedTopicId = topic,
                    savedTag = selectedTag,
                ),
            )
            ChatScreen(
                container = container,
                viewModel = vm,
                onBack = { close() },
                onOpenChatProfile = onOpenChatProfile,
                onOpenProfile = onOpenProfile,
                onCall = onCall,
                onOpenComments = onOpenComments,
                showBack = true,
            )
        }
    }
}

@Composable
private fun SavedTopicsScreen(
    repository: ChatRepository,
    chatId: Long,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenTopic: (Long, String) -> Unit,
    onOpenChatProfile: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tags by remember { mutableStateOf<List<SavedTagItem>>(emptyList()) }
    LaunchedEffect(chatId) { tags = runCatching { repository.savedTags() }.getOrDefault(emptyList()) }
    val version by repository.savedTopicsVersion.collectAsState()
    var topics by remember { mutableStateOf<List<SavedTopicItem>?>(null) }
    val title by produceState("", chatId) {
        value = runCatching { repository.getChatTitle(chatId) }.getOrDefault("")
    }

    LaunchedEffect(chatId) { runCatching { repository.requestSavedTopics() } }
    // Обновления тем приходят пачками — немного ждём, чтобы не перерисовывать список на каждое.
    LaunchedEffect(version) {
        delay(150)
        topics = runCatching { repository.savedTopics() }.getOrNull() ?: topics
    }

    val backdrop = rememberBackdropState()
    val glass = LocalGlass.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barHeight = topInset + 56.dp
    val list = topics.orEmpty()
    val rowCount = list.size + 1

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(backdrop).background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = barHeight + 8.dp,
                    bottom = bottomInset + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (tags.isNotEmpty()) {
                    item(key = "tags") {
                        LazyRow(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(tags, key = { it.emoji }) { t ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onOpenTopic(ALL, t.emoji) },
                                    label = { Text(if (t.label.isEmpty()) "${t.emoji} ${t.count}" else "${t.emoji} ${t.label}") },
                                )
                            }
                        }
                    }
                }
                item(key = "all") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = segmentShape(0, rowCount),
                        color = plateColor(PlateArea.Chats),
                    ) {
                        SavedRow(
                            icon = { Icon(Icons.Filled.Forum, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) },
                            title = tr("Все сообщения"),
                            preview = "",
                            time = "",
                            pinned = false,
                            onClick = { onOpenTopic(ALL, "") },
                        )
                    }
                }
                itemsIndexed(list, key = { _, t -> t.id }) { index, topic ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = segmentShape(index + 1, rowCount),
                        color = plateColor(PlateArea.Chats),
                    ) {
                        SavedRow(
                            icon = {
                                when (topic.kind) {
                                    SavedTopicKind.NOTES -> Icon(Icons.Filled.Bookmark, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    SavedTopicKind.HIDDEN -> Icon(Icons.Filled.VisibilityOff, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    SavedTopicKind.CHAT -> Text(
                                        topic.title.firstOrNull()?.uppercase() ?: "#",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            },
                            title = when (topic.kind) {
                                SavedTopicKind.NOTES -> tr("Мои заметки")
                                SavedTopicKind.HIDDEN -> tr("Скрытый автор")
                                SavedTopicKind.CHAT -> topic.title.ifEmpty { tr("Чат") }
                            },
                            preview = topic.lastText,
                            time = formatSavedTime(topic.lastDate),
                            pinned = topic.isPinned,
                            onClick = { onOpenTopic(topic.id, "") },
                            // Долгое нажатие закрепляет или открепляет тему.
                            onLongClick = {
                                scope.launch {
                                    runCatching { repository.setSavedTopicPinned(topic.id, !topic.isPinned) }
                                }
                            },
                        )
                    }
                }
                if (topics == null) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(contained = true)
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(barHeight)
                .glassTopBar(backdrop, glass),
        ) {
            Row(
                Modifier
                    .padding(top = topInset)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад")) }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f).clickable(onClick = onOpenChatProfile)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Темы",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedRow(
    icon: @Composable () -> Unit,
    title: String,
    preview: String,
    time: String,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (time.isNotEmpty()) {
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (preview.isNotEmpty() || pinned) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (pinned) {
                        Icon(
                            Icons.Filled.PushPin, contentDescription = tr("Закреплена"),
                            modifier = Modifier.padding(start = 8.dp).size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatSavedTime(date: Int): String {
    if (date == 0) return ""
    val ms = date * 1000L
    val dayMs = 24L * 60 * 60 * 1000
    val pattern = if (System.currentTimeMillis() - ms < dayMs) "HH:mm" else "dd.MM"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
}
