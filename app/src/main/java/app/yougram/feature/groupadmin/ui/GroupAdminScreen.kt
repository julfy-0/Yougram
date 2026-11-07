package app.yougram.feature.groupadmin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.core.settings.PlateArea
import app.yougram.core.ui.component.Button
import app.yougram.core.ui.component.LoadingIndicator
import app.yougram.core.ui.component.TextButton
import app.yougram.core.ui.glass.plateColor
import app.yougram.feature.groupadmin.data.AdminEventKind
import app.yougram.feature.groupadmin.data.AdminLogEntry
import app.yougram.feature.groupadmin.data.BoostInfo
import app.yougram.feature.groupadmin.data.ChatStatsResult
import app.yougram.feature.groupadmin.data.GroupAdminRepository
import app.yougram.feature.groupadmin.data.InviteLinkItem
import app.yougram.feature.groupadmin.data.JoinRequestItem
import app.yougram.feature.groupadmin.data.LinkLifetime
import app.yougram.feature.settings.component.ConnectedChoiceGroup
import app.yougram.feature.settings.component.segmentShape
import java.text.DateFormat
import java.util.Date

/**
 * Управление группой или каналом (порт ChannelAdminLogActivity / MemberRequestsActivity / ManageLinksActivity
 * из Telegram). Material 3 Expressive: связанная группа кнопок для вкладок, сегментированные плитки списка,
 * расширенная FAB, индикатор загрузки LoadingIndicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupAdminScreen(viewModel: GroupAdminViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.dismissError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Управление", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Назад")) }
                },
            )
        },
        floatingActionButton = {
            if (state.tab == AdminTab.LINKS) {
                ExtendedFloatingActionButton(
                    onClick = { showCreate = true },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Новая ссылка") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ConnectedChoiceGroup(
                options = AdminTab.entries,
                selected = state.tab,
                label = { tr(it.title) },
                onSelect = viewModel::selectTab,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            when (state.tab) {
                AdminTab.LOG -> LogTab(state.log, state.logEnd, state.logLoadingMore, viewModel::loadMoreLog)
                AdminTab.REQUESTS -> RequestsTab(
                    items = state.requests,
                    onProcess = viewModel::processRequest,
                    onProcessAll = viewModel::processAllRequests,
                )
                AdminTab.LINKS -> LinksTab(
                    active = state.links,
                    revoked = state.revokedLinks,
                    onRevoke = viewModel::revokeLink,
                    onDelete = viewModel::deleteRevokedLink,
                )
                AdminTab.STATS -> StatsTab(state.statsLoaded, state.stats, state.statsError)
                AdminTab.SETTINGS -> SlowModeTab(
                    loaded = state.slowModeLoaded,
                    current = state.slowMode,
                    steps = viewModel.slowModeSteps,
                    autoDelete = state.autoDelete,
                    autoDeleteSteps = viewModel.autoDeleteSteps,
                    onSelect = viewModel::setSlowMode,
                    onAutoDelete = viewModel::setAutoDelete,
                )
            }
        }
    }

    if (showCreate) {
        CreateLinkDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, lifetime, limit, requests ->
                showCreate = false
                viewModel.createLink(name, lifetime, limit, requests)
            },
        )
    }
}

// ── Общие плитки ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun EmptyState(icon: ImageVector, text: String) {
    Centered {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(
                    icon, null,
                    Modifier.padding(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Сегментированная плитка списка: у крайних элементов крупнее внешние углы, между плитками узкий зазор. */
@Composable
private fun SegmentTile(index: Int, count: Int, content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = segmentShape(index, count),
        color = plateColor(PlateArea.Settings),
    ) { content() }
}

private fun formatDate(seconds: Int): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(seconds * 1000L))

private val ListPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp)

// ── Журнал ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun LogTab(items: List<AdminLogEntry>?, end: Boolean, loadingMore: Boolean, onMore: () -> Unit) {
    when {
        items == null -> Centered { LoadingIndicator(contained = true) }
        items.isEmpty() -> EmptyState(Icons.Filled.Settings, "Действий пока нет")
        else -> {
            val listState = rememberLazyListState()
            val nearEnd by remember(items.size) {
                derivedStateOf {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= items.size - 5
                }
            }
            LaunchedEffect(nearEnd, end) { if (nearEnd && !end) onMore() }
            LazyColumn(
                state = listState,
                contentPadding = ListPadding,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(items, key = { _, e -> e.id }) { index, entry ->
                    SegmentTile(index, items.size) { LogRow(entry) }
                }
                if (loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: AdminLogEntry) {
    val icon = when (entry.kind) {
        AdminEventKind.MEMBERS -> Icons.Filled.Group
        AdminEventKind.MESSAGES -> Icons.AutoMirrored.Filled.Message
        AdminEventKind.SETTINGS -> Icons.Filled.Settings
        AdminEventKind.LINKS -> Icons.Filled.Link
        AdminEventKind.OTHER -> Icons.Filled.MoreHoriz
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer) {
            Icon(
                icon, null,
                Modifier.padding(10.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(entry.actor, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.text, style = MaterialTheme.typography.bodyMedium)
            Text(
                formatDate(entry.date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Заявки ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun RequestsTab(
    items: List<JoinRequestItem>?,
    onProcess: (Long, Boolean) -> Unit,
    onProcessAll: (Boolean) -> Unit,
) {
    when {
        items == null -> Centered { LoadingIndicator(contained = true) }
        items.isEmpty() -> EmptyState(Icons.Filled.Group, "Заявок на вступление нет")
        else -> LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { onProcessAll(true) }, modifier = Modifier.weight(1f)) { Text("Принять всех") }
                    TextButton(onClick = { onProcessAll(false) }, modifier = Modifier.weight(1f)) { Text("Отклонить всех") }
                }
            }
            itemsIndexed(items, key = { _, r -> r.userId }) { index, request ->
                SegmentTile(index, items.size) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(request.name, style = MaterialTheme.typography.titleMedium)
                        val meta = listOfNotNull(request.username?.let { "@$it" }, formatDate(request.date)).joinToString(" · ")
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (request.bio.isNotBlank()) {
                            Text(request.bio, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onProcess(request.userId, true) }) {
                                Icon(Icons.Filled.Check, null, Modifier.padding(end = 6.dp))
                                Text("Принять")
                            }
                            TextButton(onClick = { onProcess(request.userId, false) }) {
                                Icon(Icons.Filled.Close, null, Modifier.padding(end = 6.dp))
                                Text("Отклонить")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Ссылки ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun LinksTab(
    active: List<InviteLinkItem>?,
    revoked: List<InviteLinkItem>,
    onRevoke: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (active == null) {
        Centered { LoadingIndicator(contained = true) }
        return
    }
    if (active.isEmpty() && revoked.isEmpty()) {
        EmptyState(Icons.Filled.Link, "Ссылок, созданных вами, нет")
        return
    }
    LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(active, key = { _, l -> l.link }) { index, link ->
            SegmentTile(index, active.size) { LinkRow(link, onRevoke = { onRevoke(link.link) }, onDelete = null) }
        }
        if (revoked.isNotEmpty()) {
            item {
                Text(
                    "Отозванные",
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            itemsIndexed(revoked, key = { _, l -> "r-" + l.link }) { index, link ->
                SegmentTile(index, revoked.size) { LinkRow(link, onRevoke = null, onDelete = { onDelete(link.link) }) }
            }
        }
    }
}

@Composable
private fun LinkRow(link: InviteLinkItem, onRevoke: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            link.name.ifBlank { link.link.removePrefix("https://") },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            link.link.removePrefix("https://"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val details = buildList {
            if (link.isPrimary) add(tr("Основная"))
            add(tr("Вступили: {0}", link.memberCount))
            if (link.memberLimit > 0) add(tr("Лимит: {0}", link.memberLimit))
            if (link.pendingRequests > 0) add(tr("Заявок: {0}", link.pendingRequests))
            if (link.createsJoinRequest) add(tr("По заявке"))
            if (link.expirationDate > 0) add(tr("До {0}", formatDate(link.expirationDate)))
        }.joinToString(" · ")
        Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!link.isRevoked) {
                IconButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("link", link.link))
                    Toast.makeText(context, tr("Ссылка скопирована"), Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Filled.ContentCopy, tr("Копировать")) }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link.link)
                    context.startActivity(Intent.createChooser(send, null))
                }) { Icon(Icons.Filled.Share, tr("Поделиться")) }
            }
            if (onRevoke != null && !link.isPrimary) {
                IconButton(onClick = onRevoke) { Icon(Icons.Filled.Close, tr("Отозвать")) }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, tr("Удалить")) }
            }
        }
    }
}

@Composable
private fun CreateLinkDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, lifetime: LinkLifetime, memberLimit: Int, createsJoinRequest: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var lifetime by remember { mutableStateOf(LinkLifetime.FOREVER) }
    var limit by remember { mutableStateOf("") }
    var requests by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Edit, null) },
        title = { Text("Новая ссылка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    label = { Text("Название (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ConnectedChoiceGroup(
                    options = LinkLifetime.entries,
                    selected = lifetime,
                    label = { tr(it.label) },
                    onSelect = { lifetime = it },
                )
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it.filter(Char::isDigit).take(5) },
                    label = { Text("Лимит участников (пусто — без лимита)") },
                    singleLine = true,
                    enabled = !requests,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Одобрять заявки вручную", Modifier.weight(1f))
                    Switch(checked = requests, onCheckedChange = { requests = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name.trim(), lifetime, limit.toIntOrNull() ?: 0, requests) }) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ── Статистика и бусты ──────────────────────────────────────────────────────────────────────────

private fun formatNumber(v: Double): String =
    if (v == v.toLong().toDouble()) "%,d".format(v.toLong()) else "%.1f".format(v)

@Composable
private fun StatsTab(loaded: Boolean, stats: ChatStatsResult?, error: String?) {
    when {
        !loaded -> Centered { LoadingIndicator(contained = true) }
        stats == null -> EmptyState(Icons.Filled.Settings, error ?: "Статистика недоступна")
        else -> LazyColumn(contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val boost = stats.boost
            val cards = stats.items.size + if (boost != null) 1 else 0
            var index = 0
            if (boost != null) {
                val i = index++
                item(key = "boost") { SegmentTile(i, cards) { BoostCard(boost) } }
            }
            stats.items.forEach { stat ->
                val i = index++
                item(key = stat.title) {
                    SegmentTile(i, cards) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stat.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatNumber(stat.value), style = MaterialTheme.typography.headlineSmall)
                            }
                            if (stat.growthPercent != 0.0) {
                                val up = stat.growthPercent > 0
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = if (up) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                ) {
                                    Text(
                                        (if (up) "+" else "") + "%.1f%%".format(stat.growthPercent),
                                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelLargeEmphasized,
                                        color = if (up) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoostCard(boost: BoostInfo) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Уровень {0}", boost.level), style = MaterialTheme.typography.titleLarge)
        val span = (boost.nextLevelBoosts - boost.currentLevelBoosts).coerceAtLeast(1)
        val progress = if (boost.nextLevelBoosts == 0) 1f
        else ((boost.boosts - boost.currentLevelBoosts).toFloat() / span).coerceIn(0f, 1f)
        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(
            if (boost.nextLevelBoosts == 0) tr("Бустов: {0} (максимальный уровень)", boost.boosts)
            else tr("Бустов: {0} из {1} до следующего уровня", boost.boosts, boost.nextLevelBoosts),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (boost.url.isNotBlank()) {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, boost.url)
                context.startActivity(Intent.createChooser(send, null))
            }) { Text("Поделиться ссылкой на буст") }
        }
    }
}

// ── Режимы чата ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun SlowModeTab(
    loaded: Boolean,
    current: Int?,
    steps: List<Int>,
    autoDelete: Int,
    autoDeleteSteps: List<Int>,
    onSelect: (Int) -> Unit,
    onAutoDelete: (Int) -> Unit,
) {
    if (!loaded) {
        Centered { LoadingIndicator(contained = true) }
        return
    }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val cards = buildList<@Composable () -> Unit> {
            if (current != null) add {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Медленный режим", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Участники смогут отправлять не больше одного сообщения за выбранный интервал. На админов это не распространяется.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ConnectedChoiceGroup(
                        options = steps,
                        selected = steps.minByOrNull { kotlin.math.abs(it - current) } ?: 0,
                        label = { GroupAdminRepository.formatDelay(it) },
                        onSelect = onSelect,
                    )
                }
            }
            add {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Автоудаление сообщений", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Новые сообщения в чате будут удаляться через выбранный срок.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ConnectedChoiceGroup(
                        options = autoDeleteSteps,
                        selected = autoDeleteSteps.minByOrNull { kotlin.math.abs(it - autoDelete) } ?: 0,
                        label = { GroupAdminRepository.formatAutoDelete(it) },
                        onSelect = onAutoDelete,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            cards.forEachIndexed { i, card -> SegmentTile(i, cards.size) { card() } }
        }
    }
}
