package app.yougram.feature.calls.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Videocam
import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.PlateArea
import app.yougram.core.ui.component.FileAvatar
import app.yougram.core.ui.glass.plateColor
import app.yougram.feature.chat.data.CallItem
import app.yougram.feature.chat.data.CallKind
import app.yougram.feature.settings.component.segmentShape
import java.text.DateFormat
import java.util.Date

@Composable
fun CallsScreen(
    viewModel: CallsViewModel,
    contentPadding: PaddingValues,
    onOpenChat: (Long) -> Unit,
    /** Строка поиска из нижней/верхней панели; пусто — показываем все звонки. */
    query: String = "",
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val calls = remember(state.calls, query) {
        val q = query.trim()
        if (q.isEmpty()) state.calls else state.calls.filter { it.title.contains(q, ignoreCase = true) }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 8
        }.collect { nearEnd -> if (nearEnd) viewModel.loadMore() }
    }

    Box(Modifier.fillMaxSize()) {
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
            item(key = "filter") {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Surface(
                        onClick = { viewModel.setOnlyMissed(false) },
                        shape = CircleShape,
                        color = if (!state.onlyMissed) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                        contentColor = if (!state.onlyMissed) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Text(
                            "Все",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = { viewModel.setOnlyMissed(true) },
                        shape = CircleShape,
                        color = if (state.onlyMissed) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f),
                        contentColor = if (state.onlyMissed) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Text(
                            "Пропущенные",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            itemsIndexed(calls, key = { _, c -> c.messageId }) { index, call ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = segmentShape(index, calls.size),
                    color = plateColor(PlateArea.Calls),
                ) {
                    CallRow(call, viewModel) { onOpenChat(call.chatId) }
                }
            }
        }
        when {
            state.loading -> LoadingIndicator(Modifier.align(Alignment.Center))
            calls.isEmpty() -> Text(
                state.error ?: if (state.calls.isEmpty()) "Звонков нет" else "Ничего не найдено",
                Modifier.align(Alignment.Center),
                color = if (state.error != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CallRow(call: CallItem, viewModel: CallsViewModel, onClick: () -> Unit) {
    val bad = call.kind == CallKind.MISSED || call.kind == CallKind.DECLINED
    val color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = when {
        call.kind == CallKind.MISSED -> Icons.AutoMirrored.Filled.CallMissed
        call.isOutgoing -> Icons.AutoMirrored.Filled.CallMade
        else -> Icons.AutoMirrored.Filled.CallReceived
    }
    val label = when (call.kind) {
        CallKind.MISSED -> if (call.isOutgoing) "Без ответа" else "Пропущенный"
        CallKind.DECLINED -> "Отклонённый"
        CallKind.OUTGOING -> "Исходящий"
        CallKind.INCOMING -> "Входящий"
    }
    val details = buildString {
        append(label)
        if (call.duration > 0) append(", ").append(formatDuration(call.duration))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileAvatar(title = call.title, fileId = call.avatarFileId, fileState = viewModel::fileState)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                call.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(details, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1)
            }
        }
        if (call.isVideo) {
            Icon(Icons.Filled.Videocam, contentDescription = "Видеозвонок", tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(call.date * 1000L)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}