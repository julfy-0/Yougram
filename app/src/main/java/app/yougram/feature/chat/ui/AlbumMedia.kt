package app.yougram.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.yougram.feature.chat.data.MediaItem
import app.yougram.feature.chat.data.MediaKind

/** Сколько плиток в каждом ряду сетки альбома из [count] медиа. */
private fun albumRows(count: Int): List<Int> = when (count) {
    2 -> listOf(2)
    3 -> listOf(1, 2)
    4 -> listOf(2, 2)
    5 -> listOf(2, 3)
    6 -> listOf(3, 3)
    else -> buildList {
        when (count % 3) {
            1 -> add(1)
            2 -> add(2)
        }
        repeat(count / 3) { add(3) }
    }
}

/** Альбом в одном пузыре: сетка из фото/видео, а для документов и аудио — колонка. */
@Composable
fun AlbumMedia(items: List<MediaItem>, viewModel: ChatViewModel, onOpenPhoto: (MediaItem) -> Unit) {
    val visual = items.all { (it.kind == MediaKind.PHOTO || it.kind == MediaKind.VIDEO) && !it.locked }
    if (!visual) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEach { media -> key(media.fileId) { MessageMedia(media, viewModel, onOpenPhoto) } }
        }
        return
    }
    val chunks = ArrayList<List<MediaItem>>()
    var from = 0
    for (n in albumRows(items.size)) {
        chunks += items.subList(from, from + n)
        from += n
    }
    Column(
        modifier = Modifier.width(260.dp).clip(RoundedCornerShape(16.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        chunks.forEach { chunk ->
            val rowHeight = when (chunk.size) {
                1 -> 190.dp
                2 -> 140.dp
                else -> 96.dp
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(rowHeight),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                chunk.forEach { media ->
                    key(media.fileId) {
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            MessageMedia(media, viewModel, onOpenPhoto, tile = true)
                        }
                    }
                }
            }
        }
    }
}
