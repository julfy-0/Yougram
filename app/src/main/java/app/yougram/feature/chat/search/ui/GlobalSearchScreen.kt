package app.yougram.feature.chat.search.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.component.FileAvatar
import app.yougram.core.ui.component.highlightMatches
import app.yougram.feature.chat.data.FileState
import app.yougram.feature.chat.data.SearchChatHit
import app.yougram.feature.chat.data.SearchMessageHit
import app.yougram.feature.settings.component.segmentShape
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.flow.Flow

/**
 * Результаты поиска поверх списка чатов: свои чаты, контакты, глобальный поиск по публичным чатам и сообщения.
 * Отступы [contentPadding] учитывают панели, под которыми прокручивается список.
 */
@Composable
fun GlobalSearchScreen(
    viewModel: GlobalSearchViewModel,
    query: String,
    contentPadding: PaddingValues,
    fileState: (Int) -> Flow<FileState>,
    onOpenChat: (Long) -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(query) { viewModel.search(query) }
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

    // Когда до конца списка осталось меньше 5 строк — просим следующую страницу сообщений.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 5
        }.collect { nearEnd -> if (nearEnd) viewModel.loadMoreMessages() }
    }

    val loading = state.loadingPeople || state.loadingMessages
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (state.isEmpty) {
            item(key = "status") {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    when {
                        loading -> LoadingIndicator()
                        state.error != null -> Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        else -> Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        chatSection("chats", "Чаты", state.chats, fileState, onOpenChat)
        chatSection("contacts", "Контакты", state.contacts, fileState, onOpenChat)
        chatSection("global", "Глобальный поиск", state.global, fileState, onOpenChat)
        if (state.messages.isNotEmpty()) {
            item(key = "messages-title") { SectionTitle("Сообщения") }
            itemsIndexed(
                state.messages,
                key = { _, hit -> "m-${hit.chatId}-${hit.messageId}" },
            ) { index, hit ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = segmentShape(index, state.messages.size),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    MessageHitRow(hit, state.query, highlight, fileState) { onOpenMessage(hit.chatId, hit.messageId) }
                }
            }
        }
        if (state.loadingMessages && state.messages.isNotEmpty()) {
            item(key = "more-loader") {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(24.dp))
                }
            }
        }
    }
}

private fun LazyListScope.chatSection(
    key: String,
    title: String,
    hits: List<SearchChatHit>,
    fileState: (Int) -> Flow<FileState>,
    onOpenChat: (Long) -> Unit,
) {
    if (hits.isEmpty()) return
    item(key = "$key-title") { SectionTitle(title) }
    itemsIndexed(hits, key = { _, hit -> "$key-${hit.chatId}" }) { index, hit ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = segmentShape(index, hits.size),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            ChatHitRow(hit, fileState) { onOpenChat(hit.chatId) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ChatHitRow(hit: SearchChatHit, fileState: (Int) -> Flow<FileState>, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileAvatar(title = hit.title, fileId = hit.avatarFileId, fileState = fileState, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(hit.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hit.subtitle.isNotEmpty()) {
                Text(
                    hit.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MessageHitRow(
    hit: SearchMessageHit,
    query: String,
    highlight: Color,
    fileState: (Int) -> Flow<FileState>,
    onClick: () -> Unit,
) {
    val body = remember(hit, query, highlight) {
        buildAnnotatedString {
            if (hit.author.isNotEmpty()) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Medium))
                append(hit.author + ": ")
                pop()
            }
            append(snippet(hit.text, query))
        }.highlightMatches(query, highlight)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileAvatar(title = hit.chatTitle.ifEmpty { "?" }, fileId = hit.avatarFileId, fileState = fileState, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    hit.chatTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatHitDate(hit.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Обрезает длинный текст так, чтобы найденное слово оказалось в начале видимой части. */
private fun snippet(text: String, query: String): String {
    val flat = text.replace('\n', ' ')
    val i = flat.indexOf(query.trim(), ignoreCase = true)
    return if (i > 40) "…" + flat.substring(i - 24) else flat
}

private fun formatHitDate(date: Int): String {
    val millis = date * 1000L
    return if (DateUtils.isToday(millis)) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
    } else {
        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(millis))
    }
}