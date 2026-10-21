package app.yougram.feature.settings

import app.yougram.core.i18n.tr
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.yougram.core.settings.PlateArea
import app.yougram.core.settings.SettingsRepository
import app.yougram.core.ui.component.TextButton
import app.yougram.core.ui.glass.plateColor
import app.yougram.feature.settings.component.EditDialog
import app.yougram.feature.settings.component.SettingsFootnote

/** Плоская модель списка: заголовки категорий и вкладки между ними (вкладка принадлежит ближайшему заголовку выше). */
private sealed interface FlatRow {
    val key: String
}

private data class HeaderRow(val section: Int, val title: String) : FlatRow {
    override val key: String get() = "h$section"
}

private data class EntryRow(val id: String) : FlatRow {
    override val key: String get() = "e$id"
}

private fun toFlat(layout: List<HomeSection>): List<FlatRow> = buildList {
    layout.forEachIndexed { si, s ->
        add(HeaderRow(si, s.title))
        s.entries.forEach { add(EntryRow(it)) }
    }
}

private fun fromFlat(flat: List<FlatRow>): List<HomeSection> {
    val out = mutableListOf<HomeSection>()
    var title: String? = null
    var current = mutableListOf<String>()
    for (row in flat) {
        when (row) {
            is HeaderRow -> {
                title?.let { out += HomeSection(it, current) }
                title = row.title
                current = mutableListOf()
            }
            is EntryRow -> current += row.id
        }
    }
    title?.let { out += HomeSection(it, current) }
    return out
}

private val OuterCorner = 28.dp
private val InnerCorner = 8.dp

/**
 * Редактор главного экрана настроек: вкладки перетаскиваются за ручку (в том числе между категориями),
 * у категории есть переименование, перемещение и удаление, можно добавить новую категорию или сбросить всё.
 */
@Composable
fun HomeLayoutEditorScreen(settings: SettingsRepository, contentPadding: PaddingValues) {
    val entries = remember { homeEntries(null, "").associateBy { it.id } }
    val saved by settings.homeLayout.collectAsState()
    val layout = remember(saved) { HomeLayout.normalize(HomeLayout.parse(saved), entries.keys.toList()) }
    val save = { l: List<HomeSection> -> settings.setHomeLayout(HomeLayout.toJson(l)) }

    // Рабочая копия: во время перетаскивания меняется только она, в настройки пишем при отпускании.
    // Состояние создаётся один раз (его читают долгоживущие обработчики жестов), а при внешнем изменении раскладки пересинхронизируется.
    var flat by remember { mutableStateOf(toFlat(layout)) }
    var syncedLayout by remember { mutableStateOf(layout) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var renaming by remember { mutableStateOf<Int?>(null) }
    if (syncedLayout != layout && dragKey == null) {
        syncedLayout = layout
        flat = toFlat(layout)
    }
    val listState = rememberLazyListState()
    val edgePx = with(LocalDensity.current) { 72.dp.toPx() }

    fun reorder(key: String) {
        val visible = listState.layoutInfo.visibleItemsInfo
        val info = visible.firstOrNull { it.key == key } ?: return
        val center = info.offset + dragOffset + info.size / 2f
        val rowKeys = flat.map { it.key }.toSet()
        val target = visible.firstOrNull { t ->
            t.key != key && t.key in rowKeys && center >= t.offset && center <= t.offset + t.size
        } ?: return
        val from = flat.indexOfFirst { it.key == key }
        val to = flat.indexOfFirst { it.key == target.key }
        // Выше самой первой категории вкладку поставить нельзя: у неё не будет заголовка.
        if (from < 0 || to <= 0) return
        val newOffset = if (to > from) target.offset + target.size - info.size else target.offset
        val visualTop = info.offset + dragOffset
        flat = flat.toMutableList().apply { add(to, removeAt(from)) }
        dragOffset = visualTop - newOffset
    }

    fun finishDrag() {
        if (dragKey == null) return
        dragKey = null
        dragOffset = 0f
        save(fromFlat(flat))
    }

    // Автопрокрутка, пока вкладку держат у верхнего или нижнего края.
    LaunchedEffect(dragKey) {
        val key = dragKey ?: return@LaunchedEffect
        while (true) {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
            if (info != null) {
                val top = info.offset + dragOffset
                val bottom = top + info.size
                val delta = when {
                    top < listState.layoutInfo.viewportStartOffset + edgePx -> -18f
                    bottom > listState.layoutInfo.viewportEndOffset - edgePx -> 18f
                    else -> 0f
                }
                if (delta != 0f) {
                    dragOffset += listState.scrollBy(delta)
                    reorder(key)
                }
            }
            withFrameNanos { }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "hint") {
            SettingsFootnote("Тяните вкладку за ручку справа: её можно переставить и перенести в другую категорию. Изменения применяются сразу.")
        }
        itemsIndexed(flat, key = { _, row -> row.key }) { index, row ->
            when (row) {
                is HeaderRow -> CategoryHeader(
                    title = row.title,
                    empty = flat.getOrNull(index + 1) !is EntryRow,
                    canUp = row.section > 0,
                    canDown = row.section < layout.lastIndex,
                    canDelete = layout.size > 1,
                    onRename = { renaming = row.section },
                    onUp = { save(HomeLayout.moveSection(layout, row.section, -1)) },
                    onDown = { save(HomeLayout.moveSection(layout, row.section, 1)) },
                    onDelete = { save(HomeLayout.deleteSection(layout, row.section)) },
                    modifier = Modifier.animateItem(),
                )
                is EntryRow -> {
                    val e = entries[row.id] ?: return@itemsIndexed
                    val dragging = dragKey == row.key
                    val shape: Shape = if (dragging) RoundedCornerShape(OuterCorner) else RoundedCornerShape(
                        topStart = if (flat.getOrNull(index - 1) is EntryRow) InnerCorner else OuterCorner,
                        topEnd = if (flat.getOrNull(index - 1) is EntryRow) InnerCorner else OuterCorner,
                        bottomStart = if (flat.getOrNull(index + 1) is EntryRow) InnerCorner else OuterCorner,
                        bottomEnd = if (flat.getOrNull(index + 1) is EntryRow) InnerCorner else OuterCorner,
                    )
                    EntryCard(
                        entry = e,
                        shape = shape,
                        dragging = dragging,
                        modifier = (if (dragging) Modifier.zIndex(1f) else Modifier.animateItem())
                            .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
                        handle = Modifier.pointerInput(row.key) {
                            detectDragGestures(
                                onDragStart = {
                                    dragKey = row.key
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    reorder(row.key)
                                },
                                onDragEnd = { finishDrag() },
                                onDragCancel = { finishDrag() },
                            )
                        },
                    )
                }
            }
        }
        item(key = "footer") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { save(HomeLayout.addSection(layout)) }) { Text("Добавить категорию") }
                TextButton(onClick = { settings.setHomeLayout(null) }) { Text("Сбросить") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    renaming?.let { idx ->
        EditDialog(
            title = "Название категории",
            labels = listOf("Название"),
            initial = listOf(layout.getOrNull(idx)?.title.orEmpty()),
            onConfirm = { values ->
                val name = values.firstOrNull().orEmpty().trim()
                if (name.isNotEmpty()) save(HomeLayout.rename(layout, idx, name))
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun CategoryHeader(
    title: String,
    empty: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    canDelete: Boolean,
    onRename: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onRename, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Edit, tr("Переименовать")) }
            IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.KeyboardArrowUp, tr("Категория выше")) }
            IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.KeyboardArrowDown, tr("Категория ниже")) }
            IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Delete, tr("Удалить категорию")) }
        }
        if (empty) {
            Text(
                "Пусто: перетащите сюда вкладку",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryCard(entry: HomeEntry, shape: Shape, dragging: Boolean, modifier: Modifier, handle: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = plateColor(PlateArea.Settings),
        shadowElevation = if (dragging) 12.dp else 0.dp,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(entry.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                entry.subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.DragHandle, tr("Перетащить"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}