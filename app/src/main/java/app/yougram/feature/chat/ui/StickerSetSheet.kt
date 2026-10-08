package app.yougram.feature.chat.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.component.LoadingIndicator
import app.yougram.feature.chat.data.StickerSetFull
import kotlinx.coroutines.launch

/** Лист со всеми стикерами набора: открывается тапом по стикеру в чате. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerSetSheet(setId: Long, viewModel: ChatViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var set by remember(setId) { mutableStateOf<StickerSetFull?>(null) }
    var failed by remember(setId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(setId) {
        runCatching { viewModel.loadStickerSetFull(setId) }
            .onSuccess { set = it }
            .onFailure { failed = true }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp)) {
            val current = set
            when {
                failed -> Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                    Text("Не удалось загрузить набор", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                current == null -> Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(32.dp))
                }
                else -> {
                    Text(current.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                    Text(
                        "${current.stickers.size} стикеров",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(76.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(current.stickers, key = { it.fileId }) { sticker ->
                            StickerThumb(sticker, viewModel, 76.dp) { }
                        }
                    }
                    val action: () -> Unit = {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                runCatching { viewModel.setStickerSetInstalled(current.id, !current.installed) }
                                    .onSuccess { set = current.copy(installed = !current.installed) }
                                    .onFailure { Toast.makeText(context, "Не удалось изменить набор", Toast.LENGTH_SHORT).show() }
                                busy = false
                            }
                        }
                    }
                    if (current.installed) {
                        OutlinedButton(onClick = action, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Удалить набор") }
                    } else {
                        Button(onClick = action, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Добавить набор") }
                    }
                    Box(Modifier.size(12.dp))
                }
            }
        }
    }
}
