package app.yougram.feature.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yougram.core.ui.component.rememberFileBitmap
import app.yougram.feature.chat.data.FileState
import app.yougram.feature.chat.data.GifItem
import app.yougram.feature.chat.data.StickerFmt
import app.yougram.feature.chat.data.StickerItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

@Composable
fun StickerThumb(sticker: StickerItem, viewModel: ChatViewModel, size: Dp, onClick: () -> Unit) {
    val fileId = sticker.thumbFileId ?: sticker.fileId
    val state by viewModel.fileState(fileId).collectAsState(FileState())
    LaunchedEffect(fileId) { viewModel.download(fileId, 8) }
    val bitmap = rememberFileBitmap(state.path, 256)
    val needsFull = sticker.thumbFileId == null && sticker.format != StickerFmt.STATIC
    Box(
        Modifier.size(size).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(bitmap, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
            needsFull -> Text(sticker.emoji, fontSize = 28.sp)
            else -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
fun StickerTab(
    viewModel: ChatViewModel,
    query: String,
    onQueryChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onSent: () -> Unit,
) {
    val stickers by viewModel.stickers.collectAsState()
    val sets by viewModel.stickerSets.collectAsState()
    var selected by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) { viewModel.loadStickerSets() }
    LaunchedEffect(selected, query) {
        if (query.isNotBlank()) {
            delay(300)
            viewModel.loadStickers(query)
        } else {
            viewModel.openStickerSet(selected)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            singleLine = true,
            placeholder = { Text("Поиск стикеров или эмодзи") },
            shape = RoundedCornerShape(16.dp),
            // Полупрозрачное поле, чтобы блюр панели просвечивал и под ним.
            colors = TextFieldDefaults.colors(
                focusedContainerColor = pickerChipColor(false),
                unfocusedContainerColor = pickerChipColor(false),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                TextButton(onClick = { selected = 0L }) {
                    Text(if (selected == 0L) "✓ Недавние" else "🕘 Недавние")
                }
            }
            items(sets, key = { it.id }) { set ->
                val cover = set.cover
                if (cover != null) {
                    StickerThumb(cover, viewModel, 44.dp) { selected = set.id }
                } else {
                    TextButton(onClick = { selected = set.id }) { Text(set.title.take(2)) }
                }
            }
            item {
                TextButton(onClick = onPickFile) { Text("Из файла") }
            }
        }
        if (stickers.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isNotBlank()) "По этому запросу стикеры не найдены" else "Нет недавних стикеров. Выберите набор выше.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(76.dp),
                modifier = Modifier.fillMaxWidth().height(260.dp),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(stickers, key = { it.fileId }) { sticker ->
                    StickerThumb(sticker, viewModel, 76.dp) {
                        viewModel.sendSticker(sticker)
                        onSent()
                    }
                }
            }
        }
    }
}

@Composable
private fun GifThumb(gif: GifItem, viewModel: ChatViewModel, onClick: () -> Unit) {
    val fileId = gif.thumbFileId
    val state by (fileId?.let(viewModel::fileState) ?: flowOf(FileState()))
        .collectAsState(FileState())
    LaunchedEffect(fileId) { fileId?.let { viewModel.download(it, 8) } }
    val bitmap = rememberFileBitmap(state.path, 384)
    Box(
        Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            fileId != null && state.active -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("GIF", style = MaterialTheme.typography.titleMedium)
                Text("▶", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun GifTab(viewModel: ChatViewModel, onPickFile: () -> Unit, onSent: () -> Unit) {
    val gifs by viewModel.gifs.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadGifs() }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            TextButton(onClick = onPickFile) { Text("Выбрать GIF / MP4") }
        }
        if (gifs.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Сохранённых GIF пока нет. Добавьте GIF в избранное в Telegram или выберите файл с устройства.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier.fillMaxWidth().height(280.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(gifs, key = { it.fileId }) { gif ->
                    GifThumb(gif, viewModel) {
                        viewModel.sendGif(gif)
                        onSent()
                    }
                }
            }
        }
    }
}
