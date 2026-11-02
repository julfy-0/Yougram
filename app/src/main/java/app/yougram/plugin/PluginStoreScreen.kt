@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.settings.extras

import android.graphics.BitmapFactory
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.core.ui.component.TextButton
import app.yougram.plugin.NativePluginManager
import app.yougram.plugin.PluginStore
import app.yougram.plugin.StorePlugin
import app.yougram.plugin.SuspiciousPluginException
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Текст поиска магазина живёт вне экрана, чтобы не сбрасываться при переходе «Новые» ↔ «Все». */
object PluginStoreSearch {
    var query by mutableStateOf("")
}

private sealed interface Catalog {
    data object Loading : Catalog
    data class Ready(val items: List<StorePlugin>) : Catalog
    data class Failed(val message: String) : Catalog
}

/** Вкладка «Плагины»: магазин в стиле Play Маркета. Установленные плагины — в «Менеджере плагинов» (кнопка в шапке). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginStoreScreen(manager: NativePluginManager, contentPadding: PaddingValues, newOnly: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val installed by manager.plugins.collectAsState()
    var catalog by remember { mutableStateOf<Catalog>(Catalog.Loading) }
    var reload by remember { mutableStateOf(0) }
    val query = PluginStoreSearch.query
    var category by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<StorePlugin?>(null) }
    var pending by remember { mutableStateOf<PendingPlugin?>(null) }
    // Прогресс по id: 0..1 — скачивание, -1 — установка.
    val progress = remember { mutableStateMapOf<String, Float>() }

    LaunchedEffect(reload) {
        catalog = Catalog.Loading
        catalog = runCatching { PluginStore.fetchCatalog() }
            .fold({ Catalog.Ready(it) }, { Catalog.Failed(it.message ?: tr("Не удалось загрузить каталог")) })
    }

    fun report(result: Result<*>, name: String) {
        result
            .onSuccess { Toast.makeText(context, tr("Плагин «{0}» установлен", name), Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, tr("Ошибка плагина: {0}", it.message), Toast.LENGTH_LONG).show() }
    }

    fun install(plugin: StorePlugin) {
        if (plugin.id in progress) return
        scope.launch {
            progress[plugin.id] = 0f
            var file: File? = null
            try {
                file = PluginStore.download(plugin, context.cacheDir) { progress[plugin.id] = it }
                progress[plugin.id] = -1f
                val f = file
                val result = withContext(Dispatchers.IO) { runCatching { manager.installFromZip(f, false) } }
                val err = result.exceptionOrNull()
                if (err is SuspiciousPluginException) {
                    pending = PendingPlugin(f, err.report) // файл удалит диалог
                    file = null
                } else {
                    report(result, plugin.name)
                }
            } catch (e: Exception) {
                Toast.makeText(context, tr("Ошибка плагина: {0}", e.message), Toast.LENGTH_LONG).show()
            } finally {
                progress.remove(plugin.id)
                file?.delete()
            }
        }
    }

    val items = (catalog as? Catalog.Ready)?.items.orEmpty()
    val categories = items.map { it.category }.distinct()
    val filtered = items.filter { p ->
        (category == null || p.category == category) &&
                (query.isBlank() || p.name.contains(query, true) || p.description.contains(query, true) || p.author.contains(query, true))
    }
    // «Новые»: свежие сверху (по полю added, иначе по порядку в каталоге); «Все»: по алфавиту.
    val visible = if (newOnly) {
        filtered.sortedWith(compareByDescending<StorePlugin> { it.added }.thenByDescending { items.indexOf(it) })
    } else filtered.sortedBy { it.name.lowercase() }
    val updates = if (newOnly) emptyList() else visible.filter { p -> installed.firstOrNull { it.manifest.id == p.id }?.let { PluginStore.isNewer(p.version, it.manifest.version) } == true }
    val rest = visible - updates.toSet()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item("search") {
            TextField(
                value = query,
                onValueChange = { PluginStoreSearch.query = it },
                placeholder = { Text("Поиск плагинов") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        if (categories.size > 1) {
            item("chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    item {
                        CategoryChip(tr("Все"), category == null) { category = null }
                    }
                    items(categories) { c -> CategoryChip(c, category == c) { category = if (category == c) null else c } }
                }
            }
        }

        when (val c = catalog) {
            Catalog.Loading -> item("loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    LinearProgressIndicator(Modifier.width(120.dp))
                }
            }
            is Catalog.Failed -> item("failed") {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Extension, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Не удалось загрузить каталог", style = MaterialTheme.typography.titleMedium)
                    Text(c.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = { reload++ }) { Text("Повторить") }
                }
            }
            is Catalog.Ready -> {
                if (visible.isEmpty()) {
                    item("empty") {
                        EmptyStore(if (items.isEmpty()) "Пока ничего нет" else "Ничего не найдено")
                    }
                }
                if (updates.isNotEmpty()) {
                    item("h-upd") { StoreHeader(tr("Обновления")) }
                    storeGroup(updates, installed.associate { it.manifest.id to it.manifest.version }, progress, { details = it }, ::install)
                }
                if (rest.isNotEmpty()) {
                    item("h-all") { StoreHeader(if (newOnly) tr("Новые поступления") else if (updates.isEmpty()) tr("Все плагины") else tr("Остальные")) }
                    storeGroup(rest, installed.associate { it.manifest.id to it.manifest.version }, progress, { details = it }, ::install)
                }
            }
        }
    }

    details?.let { p ->
        val installedVersion = installed.firstOrNull { it.manifest.id == p.id }?.manifest?.version
        ModalBottomSheet(onDismissRequest = { details = null }) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StoreIcon(p, 64.dp)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge)
                        Text(p.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                StoreActionButton(p, installedVersion, progress[p.id], Modifier.fillMaxWidth()) { install(p) }
                Text(
                    "v${p.version}" + if (p.size > 0) " · " + Formatter.formatShortFileSize(context, p.size) else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(p.longDescription, style = MaterialTheme.typography.bodyMedium)
                if (p.permissions.isNotEmpty()) {
                    Text("Разрешения", style = MaterialTheme.typography.titleSmall)
                    Text(p.permissions.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (p.changelog.isNotBlank()) {
                    Text("Что нового", style = MaterialTheme.typography.titleSmall)
                    Text(p.changelog, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    tr("Нативный код не изолирован: он работает с теми же правами, что и приложение. Ставьте только плагины, которым доверяете."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    pending?.let { p ->
        PluginScanDialog(
            p,
            onInstall = {
                pending = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { manager.installFromZip(p.file, allowSuspicious = true) }.also { p.file.delete() }
                    }
                    result
                        .onSuccess { Toast.makeText(context, tr("Плагин «{0}» установлен", it.manifest.name), Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, tr("Ошибка плагина: {0}", it.message), Toast.LENGTH_LONG).show() }
                }
            },
            onCancel = { pending = null; p.file.delete() },
        )
    }
}

/** Пустой магазин: по центру «печенька» на 12 лепестков с иконкой, ниже подпись. */
@Composable
private fun EmptyStore(message: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 96.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Extension,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StoreHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun CategoryChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** Соединённая группа карточек: крупные скругления снаружи, малые между карточками. */
private fun androidx.compose.foundation.lazy.LazyListScope.storeGroup(
    list: List<StorePlugin>,
    installedVersions: Map<String, String>,
    progress: Map<String, Float>,
    onOpen: (StorePlugin) -> Unit,
    onInstall: (StorePlugin) -> Unit,
) {
    items(list, key = { it.id }) { p ->
        val index = list.indexOf(p)
        val big = 24.dp
        val small = 6.dp
        val shape = RoundedCornerShape(
            topStart = if (index == 0) big else small, topEnd = if (index == 0) big else small,
            bottomStart = if (index == list.lastIndex) big else small, bottomEnd = if (index == list.lastIndex) big else small,
        )
        Surface(
            onClick = { onOpen(p) },
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StoreIcon(p, 56.dp)
                    Column(Modifier.padding(start = 12.dp, end = 8.dp).weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${p.author} · v${p.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (p.description.isNotBlank()) {
                            Text(
                                p.description,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    StoreActionButton(p, installedVersions[p.id], progress[p.id]) { onInstall(p) }
                }
                progress[p.id]?.let { v ->
                    Spacer(Modifier.height(8.dp))
                    if (v < 0f) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { v }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun StoreActionButton(
    plugin: StorePlugin,
    installedVersion: String?,
    progress: Float?,
    modifier: Modifier = Modifier,
    onInstall: () -> Unit,
) {
    val label = when {
        progress != null -> if (progress < 0f) "Установка…" else "Загрузка…"
        installedVersion == null -> "Установить"
        PluginStore.isNewer(plugin.version, installedVersion) -> "Обновить"
        else -> "Установлено"
    }
    FilledTonalButton(
        onClick = onInstall,
        enabled = progress == null && (installedVersion == null || PluginStore.isNewer(plugin.version, installedVersion)),
        modifier = modifier,
    ) { Text(label) }
}

@Composable
private fun StoreIcon(plugin: StorePlugin, size: androidx.compose.ui.unit.Dp) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, plugin.iconUrl) {
        value = plugin.iconUrl?.let { PluginStore.fetchBytes(it) }
            ?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                plugin.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}