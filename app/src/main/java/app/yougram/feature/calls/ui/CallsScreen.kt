@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.calls.ui

import app.yougram.core.ui.shape.rememberAvatarShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import app.yougram.core.ui.component.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.PlateArea
import app.yougram.core.ui.component.FileAvatar
import app.yougram.core.ui.glass.plateColor
import app.yougram.feature.chat.data.CallItem
import app.yougram.feature.chat.data.CallKind
import app.yougram.feature.settings.component.ConnectedChoiceGroup
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
                // Фильтр: «Все» / «Пропущенные» в связанной группе кнопок.
                ConnectedChoiceGroup(
                    options = listOf(false, true),
                    selected = state.onlyMissed,
                    label = { if (it) "Пропущенные" else "Все" },
                    onSelect = viewModel::setOnlyMissed,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            itemsIndexed(calls, key = { _, c -> c.messageId }) { index, call ->
                Surface(
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    shape = segmentShape(index, calls.size),
                    color = plateColor(PlateArea.Calls),
                ) {
                    CallRow(call, viewModel) { onOpenChat(call.chatId) }
                }
            }
        }
        when {
            state.loading -> LoadingIndicator(Modifier.align(Alignment.Center))
            calls.isEmpty() -> EmptyCalls(
                text = state.error ?: if (state.calls.isEmpty()) "Звонков нет" else "Ничего не найдено",
                isError = state.error != null,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun EmptyCalls(text: String, isError: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(96.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(if (isError) scheme.errorContainer else scheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Call,
                contentDescription = null,
                tint = if (isError) scheme.onErrorContainer else scheme.onSecondaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleMediumEmphasized,
            textAlign = TextAlign.Center,
            color = if (isError) scheme.error else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CallRow(call: CallItem, viewModel: CallsViewModel, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bad = call.kind == CallKind.MISSED || call.kind == CallKind.DECLINED
    val color = if (bad) scheme.error else scheme.onSurfaceVariant
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
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileAvatar(
            title = call.title,
            fileId = call.avatarFileId,
            fileState = viewModel::fileState,
            shape = rememberAvatarShape(call.chatId),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                call.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (bad) scheme.error else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Тип звонка — значок в контейнере: пропущенные подсвечены errorContainer.
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (bad) scheme.errorContainer else scheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (bad) scheme.onErrorContainer else scheme.onSecondaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
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
            color = scheme.onSurfaceVariant,
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}