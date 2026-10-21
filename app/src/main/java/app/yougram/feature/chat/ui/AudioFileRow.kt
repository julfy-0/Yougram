package app.yougram.feature.chat.ui

import app.yougram.core.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.component.LoadingIndicator
import app.yougram.feature.chat.data.FileState
import app.yougram.feature.chat.data.MediaItem
import kotlinx.coroutines.delay

/** Музыкальный файл (аудио, не голосовое): кнопка, название, исполнитель, прогресс и длительность. */
@Composable
fun AudioFileRow(
    media: MediaItem,
    full: FileState,
    modifier: Modifier = Modifier.width(260.dp),
    onDownload: () -> Unit,
) {
    val current by AudioPlayback.current.collectAsState()
    val playing by AudioPlayback.playing.collectAsState()
    val progress by AudioPlayback.progress.collectAsState()
    val isCurrent = current == media.fileId
    val active = isCurrent && playing

    LaunchedEffect(active) {
        while (active) {
            AudioPlayback.tick()
            delay(100)
        }
    }

    val tint = MaterialTheme.colorScheme.onPrimary
    Row(
        modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button) {
                    val path = full.path
                    when {
                        path != null -> AudioPlayback.toggle(media.fileId, path)
                        !full.active -> onDownload()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                full.active -> LoadingIndicator(Modifier.size(28.dp), color = tint)
                full.path == null -> Icon(Icons.Filled.Download, tr("Скачать"), tint = tint)
                active -> Icon(Icons.Filled.Pause, tr("Пауза"), tint = tint)
                else -> Icon(Icons.Filled.PlayArrow, tr("Воспроизвести"), tint = tint)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(
                media.name.ifBlank { "Аудио" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (media.artist.isNotBlank()) {
                Text(
                    media.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isCurrent) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp),
                )
            }
            Text(
                "%d:%02d".format(media.duration / 60, media.duration % 60),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
