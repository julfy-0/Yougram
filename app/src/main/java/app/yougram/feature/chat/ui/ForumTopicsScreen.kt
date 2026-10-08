package app.yougram.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.RectangleShape
import app.yougram.core.settings.PlateArea
import app.yougram.core.ui.glass.LocalGlass
import app.yougram.core.ui.glass.backdropSource
import app.yougram.core.ui.glass.glass
import app.yougram.core.ui.glass.plateColor
import app.yougram.core.ui.glass.rememberBackdropState
import app.yougram.feature.settings.component.segmentShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.yougram.core.di.AppContainer
import app.yougram.feature.chat.data.ChatRepository
import app.yougram.feature.chat.data.ForumTopicItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

/**
 * Вход в чат: если это форум (группа с темами) и тема не выбрана, показываем список тем,
 * иначе обычный экран чата (для форума — внутри выбранной темы).
 */
@Composable
fun ChatOrTopics(
    container: AppContainer,
    chatId: Long,
    messageId: Long,
    topicId: Int,
    onBack: () -> Unit,
    onOpenTopic: (Int) -> Unit,
    onOpenChatProfile: () -> Unit,
    onOpenProfile: (Long) -> Unit,
    onCall: (Long, Boolean) -> Unit,
    onOpenComments: (Long, Long) -> Unit = { _, _ -> },
    showBack: Boolean = true,
) {
    val skipCheck = topicId != 0 || messageId != 0L
    val forum by produceState<Boolean?>(if (skipCheck) false else null, chatId, topicId, messageId) {
        if (!skipCheck) value = container.chatRepository.isForum(chatId)
    }
    when {
        forum == null -> Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) { LoadingIndicator() }

        forum == true -> ForumTopicsScreen(
            repository = container.chatRepository,
            chatId = chatId,
            showBack = showBack,
            onBack = onBack,
            onOpenTopic = onOpenTopic,
            onOpenChatProfile = onOpenChatProfile,
        )

        else -> {
            val vm: ChatViewModel = viewModel(
                key = "chat-$chatId-$messageId-$topicId",
                factory = ChatViewModel.factory(container.chatRepository, chatId, container.settings, messageId, topicId),
            )
            val topicName by produceState("", chatId, topicId) {
                if (topicId != 0) value = container.chatRepository.forumTopicName(chatId, topicId)
            }
            ChatScreen(
                viewModel = vm,
                settings = container.settings,
                onBack = onBack,
                showBack = showBack,
                onOpenChatProfile = onOpenChatProfile,
                onOpenProfile = onOpenProfile,
                onCall = onCall,
                onOpenComments = onOpenComments,
                titleOverride = topicName.takeIf { topicId != 0 },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumTopicsScreen(
    repository: ChatRepository,
    chatId: Long,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenTopic: (Int) -> Unit,
    onOpenChatProfile: () -> Unit,
) {
    var topics by remember { mutableStateOf<List<ForumTopicItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val title by produceState("", chatId) {
        value = runCatching { repository.getChatTitle(chatId) }.getOrDefault("")
    }

    LaunchedEffect(chatId) {
        suspend fun load() {
            runCatching { repository.loadForumTopics(chatId) }
                .onSuccess { topics = it; failed = false }
                .onFailure { if (topics == null) failed = true }
        }
        load()
        // Новое сообщение в любой теме обновляет последнюю строку и счётчик.
        repository.newMessages.filter { it.chatId == chatId }.collectLatest {
            delay(300)
            load()
        }
    }

    // Оформление как у списка чатов: стеклянная верхняя панель с размытием, список сегментированных плашек.
    val backdrop = rememberBackdropState()
    val glass = LocalGlass.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barHeight = topInset + 56.dp

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(backdrop).background(MaterialTheme.colorScheme.background)) {
            val list = topics
            when {
                list == null && failed -> Text(
                    "Не удалось загрузить темы",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                list == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                list.isEmpty() -> Text(
                    "Тем пока нет",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = barHeight + 8.dp,
                        bottom = bottomInset + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(list, key = { _, t -> t.id }) { index, topic ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = segmentShape(index, list.size),
                            color = plateColor(PlateArea.Chats),
                        ) {
                            TopicRow(topic, onClick = { onOpenTopic(topic.id) })
                        }
                    }
                }
            }
        }

        // Верхняя панель — та же стеклянная, что в списке чатов и в чате.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(barHeight)
                .glass(backdrop, glass, RectangleShape),
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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
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

@Composable
private fun TopicRow(topic: ForumTopicItem, onClick: () -> Unit) {
    val accent = Color(0xFF000000.toInt() or topic.color)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (topic.isGeneral) "#" else topic.name.firstOrNull()?.uppercase() ?: "#",
                color = accent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    topic.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (topic.isClosed) {
                    Icon(
                        Icons.Filled.Lock, contentDescription = "Тема закрыта",
                        modifier = Modifier.padding(start = 6.dp).size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    formatTopicTime(topic.lastDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = if (topic.lastSender.isNotEmpty()) "${topic.lastSender}: ${topic.lastText}" else topic.lastText
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (topic.unread > 0) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            topic.unread.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                } else if (topic.isPinned) {
                    Icon(
                        Icons.Filled.PushPin, contentDescription = "Закреплена",
                        modifier = Modifier.padding(start = 8.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatTopicTime(date: Int): String {
    if (date == 0) return ""
    val ms = date * 1000L
    val dayMs = 24L * 60 * 60 * 1000
    val pattern = if (System.currentTimeMillis() - ms < dayMs) "HH:mm" else "dd.MM"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
}
