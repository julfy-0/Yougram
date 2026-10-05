package app.yougram.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.yougram.data.FileState
import app.yougram.data.MediaItem
import app.yougram.data.MediaKind
import app.yougram.ui.rememberFileBitmap
import kotlinx.coroutines.flow.flowOf
import java.io.File

/** Медиа внутри пузыря: фото/видео/GIF с превью или строка документа. */
@Composable
fun MessageMedia(media: MediaItem, viewModel: ChatViewModel, onOpenPhoto: (MediaItem) -> Unit) {
    val context = LocalContext.current
    val full by remember(media.fileId) { viewModel.fileState(media.fileId) }.collectAsState(FileState())
    val onClick: () -> Unit = {
        val path = full.path
        when {
            media.kind == MediaKind.PHOTO -> onOpenPhoto(media)
            path != null -> openFile(context, path, media.mimeType)
            !full.active -> viewModel.download(media.fileId, 16)
        }
    }
    when (media.kind) {
        MediaKind.DOCUMENT -> DocumentRow(media, full, onClick)
        MediaKind.VOICE -> VoiceNoteRow(media, viewModel, full)
        MediaKind.VIDEO_NOTE -> VideoNoteView(media, viewModel, full)
        else -> VisualMedia(media, viewModel, full, onClick)
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
    val mini = remember(media.miniThumb) {
        media.miniThumb?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    val previewBitmap = rememberFileBitmap(preview.path, 1024)
    val ratio = if (media.width > 0 && media.height > 0) {
        (media.width.toFloat() / media.height).coerceIn(0.6f, 1.8f)
    } else 1f

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
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        previewBitmap?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (media.kind != MediaKind.PHOTO && media.kind != MediaKind.STICKER) {
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
                full.active -> DownloadProgress(full, Modifier.size(32.dp), MaterialTheme.colorScheme.onPrimary)
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

@Composable
private fun DownloadProgress(state: FileState, modifier: Modifier, color: Color) {
    if (state.total > 0) {
        CircularProgressIndicator(
            progress = { (state.downloaded.toFloat() / state.total).coerceIn(0f, 1f) },
            modifier = modifier,
            color = color,
            strokeWidth = 2.5.dp,
        )
    } else {
        CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = 2.5.dp)
    }
}

private fun openFile(context: Context, path: String, mimeType: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(path))
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeType.ifEmpty { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Нет приложения для открытия файла", Toast.LENGTH_SHORT).show()
    }
}