package app.yougram.feature.chat.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.ui.viewinterop.AndroidView
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import app.yougram.core.ui.component.LoadingIndicator
import app.yougram.core.ui.component.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.yougram.core.ui.component.rememberFileBitmap
import app.yougram.core.util.ApkInstaller
import app.yougram.feature.chat.data.FileState
import app.yougram.feature.chat.data.MediaItem
import app.yougram.feature.chat.data.MediaKind
import java.io.File
import kotlinx.coroutines.flow.flowOf

private const val AnimationAutoLimit = 8L shl 20

/** Медиа внутри пузыря: фото/видео/GIF/стикер с превью или строка документа. */
@Composable
fun MessageMedia(media: MediaItem, viewModel: ChatViewModel, onOpenPhoto: (MediaItem) -> Unit) {
    // Платное медиа, которое ещё не куплено: файлов нет, показываем размытую заглушку с ценой.
    if (media.locked) {
        LockedPaidMedia(media)
        return
    }
    val context = LocalContext.current
    val full by remember(media.fileId) { viewModel.fileState(media.fileId) }.collectAsState(FileState())
    var viewer by remember { mutableStateOf(false) }
    var stickerSetOpen by remember { mutableStateOf(false) }
    val onClick: () -> Unit = {
        val path = full.path
        val playable = (media.kind == MediaKind.VIDEO || media.kind == MediaKind.ANIMATION) &&
                media.mimeType != "image/gif"
        when {
            media.kind == MediaKind.STICKER -> Unit
            // Фото открывается сразу: просмотрщик сам показывает превью и докачивает оригинал.
            media.kind == MediaKind.PHOTO -> onOpenPhoto(media)
            // APK (скачанный) отдаём системному установщику.
            path != null && media.kind == MediaKind.DOCUMENT && ApkInstaller.isApk(media.name, media.mimeType) ->
                ApkInstaller.install(context, path)
            path != null && playable -> viewer = true
            path != null -> openFile(context, path, media.mimeType)
            full.active -> viewModel.pauseDownload(media.fileId)
            else -> viewModel.download(media.fileId, 16)
        }
    }
    when (media.kind) {
        MediaKind.DOCUMENT -> DocumentRow(media, full, onClick)
        MediaKind.VOICE -> VoiceNoteRow(media, viewModel, full)
        MediaKind.VIDEO_NOTE -> VideoNoteView(media, viewModel, full)
        MediaKind.STICKER -> Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable { stickerSetOpen = true }) {
            StickerView(media, viewModel, full)
        }
        else -> VisualMedia(media, viewModel, full, onClick)
    }
    if (stickerSetOpen && media.stickerSetId != 0L) StickerSetSheet(media.stickerSetId, viewModel) { stickerSetOpen = false }
    val viewerPath = full.path
    if (viewer && viewerPath != null) VideoViewerDialog(viewerPath) { viewer = false }
}

/** Заглушка платного медиа: размытое превью, замок и цена в звёздах. */
@Composable
private fun LockedPaidMedia(media: MediaItem) {
    val lowTier = app.yougram.core.ui.rememberDeviceTier() == app.yougram.core.ui.DeviceTier.Low
    val mini = remember(media.miniThumb) {
        media.miniThumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val ratio = if (media.width > 0 && media.height > 0) {
        (media.width.toFloat() / media.height).coerceIn(0.6f, 1.8f)
    } else 1f
    Box(
        Modifier
            .width(260.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        mini?.let { Image(it, null, Modifier.fillMaxSize().then(if (lowTier) Modifier else Modifier.blur(24.dp)), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${media.paidStars}", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (media.kind == MediaKind.VIDEO) "Платное видео" else "Платное фото",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun StickerView(media: MediaItem, viewModel: ChatViewModel, full: FileState) {
    val preview by remember(media.previewFileId) {
        media.previewFileId?.let(viewModel::fileState) ?: flowOf(FileState())
    }.collectAsState(FileState())
    LaunchedEffect(media.fileId) { viewModel.download(media.fileId, 16) }
    LaunchedEffect(media.previewFileId) {
        media.previewFileId?.takeIf { it != media.fileId }?.let { viewModel.download(it, 16) }
    }
    val ratio = if (media.width > 0 && media.height > 0) {
        (media.width.toFloat() / media.height).coerceIn(0.5f, 2f)
    } else 1f
    val path = full.path
    val isTgs = media.mimeType == "application/x-tgsticker"
    val isWebm = media.mimeType == "video/webm"
    val previewBitmap = rememberFileBitmap(preview.path, 384)
    val staticBitmap = if (!isTgs && !isWebm) rememberFileBitmap(path, 384) else null
    val lowTier = app.yougram.core.ui.rememberDeviceTier() == app.yougram.core.ui.DeviceTier.Low

    Box(Modifier.width(160.dp).aspectRatio(ratio), contentAlignment = Alignment.Center) {
        val fallback = staticBitmap ?: previewBitmap
        when {
            path != null && isTgs && !lowTier -> {
                fallback?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                TgsSticker(path, Modifier.fillMaxSize())
            }
            path != null && isWebm && !lowTier -> {
                fallback?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                LoopingVideo(path, Modifier.fillMaxSize(), crop = false)
            }
            fallback != null -> Image(fallback, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else -> LoadingIndicator(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun VisualMedia(media: MediaItem, viewModel: ChatViewModel, full: FileState, onClick: () -> Unit) {
    val preview by remember(media.previewFileId) {
        media.previewFileId?.let(viewModel::fileState) ?: flowOf(FileState())
    }.collectAsState(FileState())
    LaunchedEffect(media.previewFileId) {
        media.previewFileId?.let { viewModel.download(it, 16) }
    }
    LaunchedEffect(media.fileId) {
        if (media.kind == MediaKind.ANIMATION && media.size in 1..AnimationAutoLimit) viewModel.download(media.fileId, 8)
    }
    val mini = remember(media.miniThumb) {
        media.miniThumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val previewBitmap = rememberFileBitmap(preview.path, 640)
    val ratio = if (media.width > 0 && media.height > 0) {
        (media.width.toFloat() / media.height).coerceIn(0.6f, 1.8f)
    } else 1f
    val animPath = full.path.takeIf { media.kind == MediaKind.ANIMATION && media.mimeType != "image/gif" }
    val isGif = media.kind == MediaKind.ANIMATION && media.mimeType == "image/gif"
    val lowTierGif = app.yougram.core.ui.rememberDeviceTier() == app.yougram.core.ui.DeviceTier.Low
    // На слабом железе GIF показываем статичным первым кадром, на остальных — анимированным.
    val gifBitmap = if (isGif && lowTierGif) rememberFileBitmap(full.path, 640) else null
    val gifPath = full.path.takeIf { isGif && !lowTierGif }
    // Пока полный файл не скачан, превью размыто. На слабом железе вместо blur показываем только крошечную миниатюру:
    // растянутая, она и так выглядит размытой.
    val blurred = full.path == null
    val lowTier = app.yougram.core.ui.rememberDeviceTier() == app.yougram.core.ui.DeviceTier.Low
    val blurMod = if (blurred && !lowTier) Modifier.blur(24.dp) else Modifier

    Box(
        Modifier
            .width(260.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        mini?.let {
            Image(it, null, Modifier.fillMaxSize().then(blurMod), contentScale = ContentScale.Crop)
        }
        if (!(blurred && lowTier)) {
            previewBitmap?.let {
                Image(it, null, Modifier.fillMaxSize().then(blurMod), contentScale = ContentScale.Crop)
            }
        }
        gifBitmap?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (gifPath != null) AnimatedGif(gifPath, Modifier.fillMaxSize())
        if (animPath != null) {
            LoopingVideo(animPath, Modifier.fillMaxSize())
        }
        if (blurred) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                if (full.active || full.partial) {
                    DownloadControl(full, Color.White, 36.dp)
                } else {
                    Icon(Icons.Filled.Download, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        } else if (media.kind != MediaKind.PHOTO && animPath == null && gifBitmap == null && gifPath == null) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                if (full.active) {
                    DownloadProgress(full, Modifier.size(36.dp), Color.White)
                } else {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(media: MediaItem, full: FileState, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            when {
                full.active || full.partial -> DownloadControl(full, MaterialTheme.colorScheme.onPrimary, 32.dp)
                full.path != null -> Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.onPrimary)
                else -> Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                media.name.ifEmpty { media.kind.label },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                Formatter.formatShortFileSize(context, media.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Файл скачан не до конца (загрузка на паузе). */
private val FileState.partial get() = path == null && downloaded > 0 && total > 0

/** Кольцо прогресса: во время загрузки внутри «пауза», на паузе — «скачать» (тап продолжает). */
@Composable
private fun DownloadControl(state: FileState, color: Color, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        DownloadProgress(state, Modifier.fillMaxSize(), color)
        Icon(
            if (state.active) Icons.Filled.Pause else Icons.Filled.Download,
            contentDescription = if (state.active) "Приостановить" else "Продолжить",
            tint = color,
            modifier = Modifier.size(size / 2),
        )
    }
}

@Composable
private fun DownloadProgress(state: FileState, modifier: Modifier, color: Color) {
    if (state.total > 0) {
        CircularWavyProgressIndicator(
            progress = { (state.downloaded.toFloat() / state.total).coerceIn(0f, 1f) },
            modifier = modifier,
            color = color,
        )
    } else {
        LoadingIndicator(modifier = modifier, color = color)
    }
}

internal fun openFile(context: Context, path: String, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType.ifEmpty { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Нет приложения для открытия файла", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "Не удалось открыть файл", Toast.LENGTH_SHORT).show()
    }
}

/** Анимированный GIF/WebP через ImageDecoder: декодируем сразу в размер пузыря, чтобы не держать полный кадр в памяти. */
@Composable
private fun AnimatedGif(path: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        update = { view ->
            if (view.tag != path) {
                view.tag = path
                runCatching {
                    val source = ImageDecoder.createSource(File(path))
                    val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                        val side = maxOf(info.size.width, info.size.height)
                        if (side > 640) {
                            val k = 640f / side
                            decoder.setTargetSize(
                                (info.size.width * k).toInt().coerceAtLeast(1),
                                (info.size.height * k).toInt().coerceAtLeast(1),
                            )
                        }
                    }
                    view.setImageDrawable(drawable)
                    (drawable as? AnimatedImageDrawable)?.apply {
                        repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                        start()
                    }
                }
            }
        },
        onRelease = { view ->
            (view.drawable as? AnimatedImageDrawable)?.stop()
            view.setImageDrawable(null)
            view.tag = null
        },
    )
}
