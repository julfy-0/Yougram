@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.yougram.feature.settings.extras

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.filled.Check
import android.graphics.BitmapFactory
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import app.yougram.plugin.PluginFeedback
import app.yougram.plugin.PluginRating
import app.yougram.plugin.PluginReportReasons
import app.yougram.plugin.PluginReview
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr
import app.yougram.core.settings.PlateArea
import app.yougram.core.ui.component.TextButton
import app.yougram.core.ui.glass.plateColor
import app.yougram.feature.settings.component.segmentShape
import app.yougram.plugin.NativePluginManager
import app.yougram.plugin.PluginStore
import app.yougram.plugin.StorePlugin
import app.yougram.plugin.VectorIcon
import app.yougram.plugin.VectorIconParser
import app.yougram.plugin.VectorIconView
import app.yougram.plugin.SuspiciousPluginException
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Текст поиска магазина живёт вне экрана, чтобы не сбрасываться при переходе «Новые» ↔ «Все». */
object PluginStoreSearch {
    var query by mutableStateOf("")

    /** Выбранная категория и список доступных категорий: показываются в верхней панели главного экрана. */
    var category by mutableStateOf<String?>(null)
    var categories by mutableStateOf<List<String>>(emptyList())
}

/** Высота строки поиска и ряда категорий в верхней панели магазина. */
val PluginStoreSearchHeight = 64.dp
val PluginStoreChipsHeight = 48.dp

/** Поиск и категории магазина плагинов — содержимое верхней панели (под заголовком). */
@Composable
fun PluginStoreTopBar() {
    val query = PluginStoreSearch.query
    val categories = PluginStoreSearch.categories
    val category = PluginStoreSearch.category
    Column(Modifier.fillMaxWidth()) {
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        if (categories.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(PluginStoreChipsHeight),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item { CategoryChip(tr("Все"), category == null) { PluginStoreSearch.category = null } }
                items(categories) { c ->
                    CategoryChip(c, category == c) { PluginStoreSearch.category = if (category == c) null else c }
                }
            }
        }
    }
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
    val category = PluginStoreSearch.category
    var details by remember { mutableStateOf<StorePlugin?>(null) }
    var pending by remember { mutableStateOf<PendingPlugin?>(null) }
    var pendingDelete by remember { mutableStateOf<StorePlugin?>(null) }
    // Прогресс по id: 0..1 — скачивание, -1 — установка.
    val progress = remember { mutableStateMapOf<String, Float>() }
    var ratings by remember { mutableStateOf<Map<String, PluginRating>>(emptyMap()) }

    LaunchedEffect(reload) {
        catalog = Catalog.Loading
        catalog = runCatching { PluginStore.fetchCatalog() }
            .fold({ Catalog.Ready(it) }, { Catalog.Failed(it.message ?: tr("Не удалось загрузить каталог")) })
        ratings = runCatching { PluginFeedback.fetchRatings() }.getOrDefault(emptyMap())
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
    LaunchedEffect(categories) {
        PluginStoreSearch.categories = categories
        if (PluginStoreSearch.category?.let { it !in categories } == true) PluginStoreSearch.category = null
    }
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
                        Box(Modifier.animateItem()) { EmptyStore(if (items.isEmpty()) "Пока ничего нет" else "Ничего не найдено") }
                    }
                }
                if (updates.isNotEmpty()) {
                    item("h-upd") { Box(Modifier.animateItem()) { StoreHeader(tr("Обновления")) } }
                    storeGroup(updates, installed.associate { it.manifest.id to it.manifest.version }, progress, ratings, { details = it }, ::install, { pendingDelete = it })
                }
                if (rest.isNotEmpty()) {
                    item("h-all") { Box(Modifier.animateItem()) { StoreHeader(if (newOnly) tr("Новые поступления") else if (updates.isEmpty()) tr("Все плагины") else tr("Остальные")) } }
                    storeGroup(rest, installed.associate { it.manifest.id to it.manifest.version }, progress, ratings, { details = it }, ::install, { pendingDelete = it })
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
                        Text(tr("Разработчик: {0}", p.developerLabel()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TrustBadge(p.verified, Modifier.padding(top = 2.dp))
                    }
                }
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
                FeedbackSection(p, ratings[p.id]) {
                    scope.launch { ratings = runCatching { PluginFeedback.fetchRatings() }.getOrDefault(ratings) }
                }
                Text(
                    tr("Нативный код не изолирован: он работает с теми же правами, что и приложение. Ставьте только плагины, которым доверяете."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Кнопки действий — в самом низу карточки.
                StoreActionButton(p, installedVersion, progress[p.id], Modifier.fillMaxWidth()) { install(p) }
                if (installedVersion != null) {
                    OutlinedButton(
                        onClick = { pendingDelete = p },
                        enabled = progress[p.id] == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Удалить")
                    }
                }
            }
        }
    }

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить плагин?") },
            text = { Text(p.name) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch(Dispatchers.IO) { manager.uninstall(p.id) }
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
        )
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
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    // Цвет и текст плавно перетекают, а на выбранной категории раскрывается галочка.
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest,
        motion.defaultEffectsSpec(),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        motion.defaultEffectsSpec(),
        label = "chipContent",
    )
    Surface(onClick = onClick, shape = CircleShape, color = container, contentColor = content) {
        Row(
            Modifier
                .animateContentSize(motion.defaultSpatialSpec())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(motion.defaultEffectsSpec()) + expandHorizontally(motion.defaultSpatialSpec()),
                exit = fadeOut(motion.defaultEffectsSpec()) + shrinkHorizontally(motion.defaultSpatialSpec()),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(16.dp))
            }
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** «@логин» из каталога, а если его нет — автор из manifest.json. */
private fun StorePlugin.developerLabel() = if (developer.isNotBlank()) "@$developer" else author

private val StoreCardShape = RoundedCornerShape(28.dp)

/** Как в «Установленных»: больше двух плагинов — связанная группа (крупные углы снаружи, малые между карточками), иначе отдельные карточки. */
private fun androidx.compose.foundation.lazy.LazyListScope.storeGroup(
    list: List<StorePlugin>,
    installedVersions: Map<String, String>,
    progress: Map<String, Float>,
    ratings: Map<String, PluginRating>,
    onOpen: (StorePlugin) -> Unit,
    onInstall: (StorePlugin) -> Unit,
    onDelete: (StorePlugin) -> Unit,
) {
    val connected = list.size > 2
    itemsIndexed(list, key = { _, p -> p.id }) { index, p ->
        StoreCard(
            modifier = Modifier.animateItem(),
            shape = if (connected) segmentShape(index, list.size) else StoreCardShape,
            gap = if (connected) 0.dp else 6.dp,
            plugin = p,
            installedVersion = installedVersions[p.id],
            progress = progress[p.id],
            rating = ratings[p.id],
            onOpen = { onOpen(p) },
            onInstall = { onInstall(p) },
            onDelete = { onDelete(p) },
        )
    }
}

@Composable
private fun StoreCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    gap: androidx.compose.ui.unit.Dp,
    plugin: StorePlugin,
    installedVersion: String?,
    progress: Float?,
    rating: PluginRating?,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        onClick = onOpen,
        shape = shape,
        color = plateColor(PlateArea.Settings),
        modifier = modifier.fillMaxWidth().padding(bottom = gap),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreIcon(plugin, 56.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        plugin.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        tr("Разработчик: {0}", plugin.developerLabel()) + " · v${plugin.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TrustBadge(plugin.verified, Modifier.padding(top = 2.dp))
                    rating?.let { RatingLine(it, Modifier.padding(top = 2.dp)) }
                }
            }
            if (plugin.description.isNotBlank()) {
                Text(
                    plugin.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (plugin.category.isNotBlank()) {
                    StoreChip(plugin.category)
                    Spacer(Modifier.width(6.dp))
                }
                if (plugin.size > 0) {
                    StoreChip(Formatter.formatShortFileSize(context, plugin.size))
                }
            }
            // Кнопка действия — отдельной строкой под категорией и размером, на всю ширину карточки.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                StoreActionButton(plugin, installedVersion, progress, Modifier.weight(1f)) { onInstall() }
                if (installedVersion != null) {
                    IconButton(onClick = onDelete, enabled = progress == null) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = tr("Удалить"),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            progress?.let { v ->
                Spacer(Modifier.height(8.dp))
                if (v < 0f) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { v }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StoreChip(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** Метка доверия: доверенный разработчик (галочка) или непроверенный (предупреждение). */
@Composable
private fun TrustBadge(verified: Boolean, modifier: Modifier = Modifier) {
    val color = if (verified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (verified) Icons.Filled.Verified else Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (verified) "Доверенный разработчик" else "Не проверен",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
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

/** Иконка плагина: картинка и признак «одноцветная» (такую красим в акцентный цвет). */
internal class PluginIcon(val image: ImageBitmap?, val vector: VectorIcon?, val mono: Boolean)

/**
 * true, если иконка с прозрачностью и все видимые пиксели серые/чёрно-белые (глиф или силуэт).
 * Полноцветные логотипы и картинки без прозрачности не трогаем: после перекраски они стали бы сплошным пятном.
 */
internal fun isMonochromeIcon(bmp: android.graphics.Bitmap): Boolean {
    val w = bmp.width
    val h = bmp.height
    if (w == 0 || h == 0 || !bmp.hasAlpha()) return false
    val step = maxOf(1, minOf(w, h) / 32)
    var seen = 0
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val c = bmp.getPixel(x, y)
            if (android.graphics.Color.alpha(c) > 128) {
                seen++
                val r = android.graphics.Color.red(c)
                val g = android.graphics.Color.green(c)
                val b = android.graphics.Color.blue(c)
                if (maxOf(r, g, b) - minOf(r, g, b) > 40) return false
            }
            x += step
        }
        y += step
    }
    return seen > 0
}

/** Растровая (png/webp/jpg) или векторная (svg / Android vector xml) иконка по содержимому файла. */
internal fun decodePluginIcon(bytes: ByteArray): PluginIcon? {
    if (VectorIconParser.looksLikeVector(bytes)) return VectorIconParser.parse(bytes)?.let { PluginIcon(null, it, true) }
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return PluginIcon(bmp.asImageBitmap(), null, isMonochromeIcon(bmp))
}

/** Рисует иконку; одноцветные и все векторные красятся в акцентный цвет темы (поверх primaryContainer). */
@Composable
internal fun PluginIconImage(icon: PluginIcon, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onPrimaryContainer
    val vector = icon.vector
    val image = icon.image
    if (vector != null) {
        VectorIconView(vector, tint, modifier)
    } else if (image != null) {
        Image(
            image, null, modifier,
            contentScale = ContentScale.Crop,
            colorFilter = if (icon.mono) ColorFilter.tint(tint) else null,
        )
    }
}

@Composable
private fun StoreIcon(plugin: StorePlugin, size: androidx.compose.ui.unit.Dp) {
    val icon by produceState<PluginIcon?>(null, plugin.iconUrl) {
        value = plugin.iconUrl?.let { PluginStore.fetchBytes(it) }?.let { bytes ->
            withContext(Dispatchers.Default) { decodePluginIcon(bytes) }
        }
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val i = icon
        if (i != null) {
            PluginIconImage(i, Modifier.fillMaxSize())
        } else {
            Text(
                plugin.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun RatingLine(rating: PluginRating, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(4.dp))
        Text(
            "${rating.avg} · ${rating.count}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Пять звёзд; если передан [onPick] — ими можно выбрать оценку. */
@Composable
private fun Stars(value: Int, size: androidx.compose.ui.unit.Dp, onPick: ((Int) -> Unit)? = null) {
    Row {
        for (i in 1..5) {
            val m = if (onPick != null) Modifier.size(size).clickable { onPick(i) } else Modifier.size(size)
            Icon(
                if (i <= value) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                modifier = m,
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/** Блок «Отзывы» в окне деталей: сводка, кнопки «Написать отзыв» и «Пожаловаться», список отзывов. */
@Composable
private fun FeedbackSection(plugin: StorePlugin, rating: PluginRating?, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var reviews by remember(plugin.id) { mutableStateOf<List<PluginReview>?>(null) }
    var failed by remember(plugin.id) { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }

    LaunchedEffect(plugin.id, refresh) {
        val r = runCatching { PluginFeedback.fetchReviews(context, plugin.id) }
        reviews = r.getOrNull() ?: reviews
        failed = r.isFailure
    }
    val mine = reviews?.firstOrNull { it.mine }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Отзывы", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (rating != null) RatingLine(rating)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { writing = true }) { Text(if (mine != null) "Изменить отзыв" else "Написать отзыв") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { reporting = true }) {
                Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Пожаловаться")
            }
        }
        when {
            reviews == null && failed -> Text(
                "Отзывы сейчас недоступны",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            reviews == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            reviews!!.isEmpty() -> Text(
                "Пока нет отзывов. Будьте первым!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> reviews!!.forEach { r ->
                ReviewItem(r, onDelete = {
                    scope.launch {
                        runCatching { PluginFeedback.deleteMyReview(context, plugin.id) }
                            .onSuccess { refresh++; onChanged() }
                            .onFailure { Toast.makeText(context, it.message ?: "Ошибка", Toast.LENGTH_LONG).show() }
                    }
                })
            }
        }
    }

    if (writing) {
        ReviewDialog(
            initial = mine,
            onDismiss = { writing = false },
            onSend = { stars, text, name ->
                writing = false
                scope.launch {
                    runCatching { PluginFeedback.submitReview(context, plugin.id, stars, text, name) }
                        .onSuccess {
                            Toast.makeText(context, "Спасибо за отзыв!", Toast.LENGTH_SHORT).show()
                            refresh++; onChanged()
                        }
                        .onFailure { Toast.makeText(context, it.message ?: "Ошибка", Toast.LENGTH_LONG).show() }
                }
            },
        )
    }
    if (reporting) {
        ReportDialog(
            onDismiss = { reporting = false },
            onSend = { reason, text ->
                reporting = false
                scope.launch {
                    runCatching { PluginFeedback.submitReport(context, plugin.id, reason, text) }
                        .onSuccess { Toast.makeText(context, "Жалоба отправлена, спасибо", Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, it.message ?: "Ошибка", Toast.LENGTH_LONG).show() }
                }
            },
        )
    }
}

@Composable
private fun ReviewItem(review: PluginReview, onDelete: () -> Unit) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stars(review.rating, 14.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    (review.name.ifBlank { "Пользователь MaterialGram" }) + " · " +
                            android.text.format.DateFormat.getMediumDateFormat(context).format(java.util.Date(review.ts * 1000)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (review.mine) TextButton(onClick = onDelete) { Text("Удалить") }
            }
            if (review.text.isNotBlank()) Text(review.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReviewDialog(initial: PluginReview?, onDismiss: () -> Unit, onSend: (Int, String, String) -> Unit) {
    val context = LocalContext.current
    var stars by remember { mutableStateOf(initial?.rating ?: 0) }
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ваш отзыв") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Stars(stars, 36.dp) { stars = it }
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 500) text = it },
                    placeholder = { Text("Что вы думаете о плагине? (необязательно)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    placeholder = { Text("Имя (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (stars == 0) Toast.makeText(context, "Выберите оценку от 1 до 5", Toast.LENGTH_SHORT).show()
                else onSend(stars, text.trim(), name.trim())
            }) { Text("Отправить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSend: (String, String) -> Unit) {
    var reason by remember { mutableStateOf(PluginReportReasons.first().first) }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пожаловаться на плагин") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PluginReportReasons.forEach { (code, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { reason = code },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == code, onClick = { reason = code })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 1000) text = it },
                    placeholder = { Text("Подробности (необязательно)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSend(reason, text.trim()) }) { Text("Отправить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}