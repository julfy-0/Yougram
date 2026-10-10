package app.yougram.feature.chat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.TextUnit
import app.yougram.core.ui.DeviceTier
import app.yougram.core.ui.component.CustomEmojiSpan
import app.yougram.core.ui.component.customEmojiKey
import app.yougram.core.ui.component.rememberFileBitmap
import app.yougram.core.ui.rememberDeviceTier
import app.yougram.feature.chat.data.FileState
import app.yougram.feature.chat.data.StickerFmt
import app.yougram.feature.chat.data.StickerItem
import kotlinx.coroutines.flow.flowOf

/** Словарь inline-содержимого для Text(): по одному placeholder на каждое уникальное эмодзи в сообщении. */
@Composable
fun rememberEmojiInlineContent(
    emojis: List<CustomEmojiSpan>,
    viewModel: ChatViewModel,
    size: TextUnit,
    animated: Boolean,
): Map<String, InlineTextContent> {
    if (emojis.isEmpty()) return emptyMap()
    val tier = rememberDeviceTier()
    return remember(emojis, size, animated, tier) {
        emojis.map { it.id }.distinct().associate { id ->
            customEmojiKey(id) to InlineTextContent(
                Placeholder(size, size, PlaceholderVerticalAlign.TextCenter),
            ) { CustomEmojiView(id, viewModel, animated && tier != DeviceTier.Low, tier == DeviceTier.High, size * 0.8f) }
        }
    }
}

/** Одно премиум-эмодзи: сначала статичное превью, поверх — анимация (если разрешена). */
@Composable
fun CustomEmojiView(
    id: Long,
    viewModel: ChatViewModel,
    animated: Boolean,
    allowVideo: Boolean,
    fallbackSize: TextUnit = TextUnit.Unspecified,
) {
    val item by produceState<StickerItem?>(null, id) { value = viewModel.customEmoji(id) }
    val sticker = item ?: return
    LaunchedEffect(sticker.fileId) { viewModel.download(sticker.fileId, 8) }
    LaunchedEffect(sticker.thumbFileId) { sticker.thumbFileId?.let { viewModel.download(it, 8) } }
    val full by remember(sticker.fileId) { viewModel.fileState(sticker.fileId) }.collectAsState(FileState())
    val thumb by remember(sticker.thumbFileId) {
        sticker.thumbFileId?.let(viewModel::fileState) ?: flowOf(FileState())
    }.collectAsState(FileState())
    val still = rememberFileBitmap(if (sticker.format == StickerFmt.STATIC) full.path else thumb.path, 128)
    Box(Modifier.fillMaxSize()) {
        // Пока превью нет (или оно не загрузилось), показываем обычный эмодзи вместо пустого места.
        if (still != null) {
            Image(still, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else if (sticker.emoji.isNotEmpty()) {
            Text(sticker.emoji, fontSize = fallbackSize)
        }
        val path = full.path
        if (animated && path != null) {
            when (sticker.format) {
                StickerFmt.TGS -> TgsSticker(path, Modifier.fillMaxSize())
                StickerFmt.WEBM -> if (allowVideo) LoopingVideo(path, Modifier.fillMaxSize(), crop = false)
                StickerFmt.STATIC -> Unit
            }
        }
    }
}
