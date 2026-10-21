package app.yougram.core.ui.wallpaper

import app.yougram.core.i18n.tr
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/** Полноэкранный выбор обоев поверх текущего экрана (диалог), навигация не нужна. */
@Composable
fun WallpaperDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            WallpaperScreen(onBack = onDismiss)
        }
    }
}

@Composable
fun WallpaperScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад")) }
            Text("Обои чата", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            WallpaperContent()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Содержимое выбора обоев без собственной прокрутки и шапки: встраивается в «Настройки → Внешний вид». */
@Composable
fun WallpaperContent() {
    val context = LocalContext.current
    val store = remember { WallpaperStore.get(context) }
    val config by store.config.collectAsState()
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(if (config.kind == WallpaperKind.Pattern) 1 else if (config.kind == WallpaperKind.Photo) 2 else 0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            runCatching { store.setPhoto(uri) }
                .onFailure { Toast.makeText(context, tr("Не удалось загрузить фото"), Toast.LENGTH_SHORT).show() }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Preview(config)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Цвета", "Узоры", "Фото").forEachIndexed { i, title ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(title) })
            }
        }
        Spacer(Modifier.height(14.dp))
        when (tab) {
            0 -> PresetsTab(config, store)
            1 -> PatternsTab(config, store)
            else -> PhotoTab(config, store, onPick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
        }
        Spacer(Modifier.height(16.dp))
        if (config.kind != WallpaperKind.None) {
            OutlinedButton(
                onClick = { store.update { it.copy(kind = WallpaperKind.None) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Убрать обои") }
        }
    }
}

@Composable
private fun Preview(config: WallpaperConfig) {
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxWidth().height(200.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        WallpaperBackground(config, Modifier.fillMaxSize(), patternScale = 0.8f)
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Bottom) {
            Bubble("Привет! Как тебе такие обои?", mine = false)
            Spacer(Modifier.height(6.dp))
            Bubble("Выглядит отлично", mine = true)
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else Color(0xFF3A3A40))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PresetsTab(config: WallpaperConfig, store: WallpaperStore) {
    val tiles = WallpaperPresets
    tiles.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { p ->
                val selected = config.kind == WallpaperKind.Preset && config.presetId == p.id
                Tile(
                    selected = selected,
                    title = p.title,
                    modifier = Modifier.weight(1f),
                    onClick = { store.update { it.copy(kind = WallpaperKind.Preset, presetId = p.id) } },
                ) {
                    WallpaperBackground(
                        WallpaperConfig(kind = WallpaperKind.Preset, presetId = p.id),
                        Modifier.fillMaxSize(),
                    )
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun PatternsTab(config: WallpaperConfig, store: WallpaperStore) {
    Text("Подложка", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WallpaperPresets.forEach { p ->
            val selected = config.presetId == p.id
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(if (p.colors.size == 1) listOf(p.colors[0], p.colors[0]) else p.colors))
                    .border(
                        BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        CircleShape,
                    )
                    .clickable { store.update { it.copy(kind = WallpaperKind.Pattern, presetId = p.id) } },
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    WallpaperPattern.entries.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { pattern ->
                val selected = config.kind == WallpaperKind.Pattern && config.pattern == pattern
                Tile(
                    selected = selected,
                    title = pattern.title,
                    modifier = Modifier.weight(1f),
                    onClick = { store.update { it.copy(kind = WallpaperKind.Pattern, pattern = pattern) } },
                ) {
                    WallpaperBackground(
                        WallpaperConfig(
                            kind = WallpaperKind.Pattern,
                            presetId = config.presetId,
                            pattern = pattern,
                            patternIntensity = config.patternIntensity.coerceAtLeast(0.14f),
                        ),
                        Modifier.fillMaxSize(),
                        patternScale = 0.6f,
                    )
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    LabeledSlider("Яркость узора", config.patternIntensity, 0.04f..0.4f) { v -> store.update { it.copy(patternIntensity = v) } }
}

@Composable
private fun PhotoTab(config: WallpaperConfig, store: WallpaperStore, onPick: () -> Unit) {
    val hasPhoto = config.photoStamp != 0L
    Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
        Text(if (hasPhoto) "Выбрать другое фото" else "Выбрать фото из галереи")
    }
    if (hasPhoto) {
        Spacer(Modifier.height(8.dp))
        if (config.kind != WallpaperKind.Photo) {
            OutlinedButton(
                onClick = { store.update { it.copy(kind = WallpaperKind.Photo) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Включить сохранённое фото") }
            Spacer(Modifier.height(8.dp))
        }
        LabeledSlider("Затемнение", config.photoDim, 0f..0.8f) { v -> store.update { it.copy(photoDim = v) } }
        LabeledSlider("Размытие", config.photoBlur, 0f..24f) { v -> store.update { it.copy(photoBlur = v) } }
        OutlinedButton(onClick = { store.removePhoto() }, modifier = Modifier.fillMaxWidth()) { Text("Удалить фото") }
    } else {
        Spacer(Modifier.height(8.dp))
        Text(
            "Фото сохраняется внутри приложения и подгоняется под экран.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun Tile(
    selected: Boolean,
    title: String,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(shape)
                .border(
                    BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    shape,
                )
                .clickable(onClick = onClick),
        ) {
            content()
            if (selected) {
                Box(
                    Modifier.align(Alignment.Center).size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

/** Готовая строка для экрана настроек: тап открывает выбор обоев. Вставить одной строкой `WallpaperSettingsRow()`. */
@Composable
fun WallpaperSettingsRow(modifier: Modifier = Modifier) {
    var open by remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("Обои чата", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Цвета, узоры и своё фото",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (open) WallpaperDialog(onDismiss = { open = false })
}