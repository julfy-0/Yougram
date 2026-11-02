package app.yougram.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text
import app.yougram.core.i18n.tr

private class AttachTile(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** Панель вложений: те же кнопки-плашки и тот же стиль, что у панели эмодзи; живёт в нижней стеклянной панели. */
@Composable
fun AttachPanel(
    onFile: () -> Unit,
    onMedia: () -> Unit,
    onPoll: () -> Unit,
    onLocation: () -> Unit,
    onVoice: () -> Unit,
    onRound: () -> Unit,
) {
    val tiles = listOf(
        AttachTile(Icons.Filled.AttachFile, tr("Файл"), onFile),
        AttachTile(Icons.Filled.Image, tr("Фото и видео"), onMedia),
        AttachTile(Icons.Filled.Poll, tr("Опрос"), onPoll),
        AttachTile(Icons.Filled.LocationOn, tr("Геопозиция"), onLocation),
        AttachTile(Icons.Filled.Mic, tr("Голосовое"), onVoice),
        AttachTile(Icons.Filled.Videocam, tr("Кружок"), onRound),
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile ->
                    Surface(
                        onClick = tile.onClick,
                        shape = RoundedCornerShape(20.dp),
                        color = pickerChipColor(false),
                        modifier = Modifier.weight(1f).height(84.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(tile.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.height(6.dp))
                            Text(tile.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}