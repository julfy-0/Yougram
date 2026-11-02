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

private class AttachActionTile(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** Панель вложений: те же кнопки-плашки и тот же стиль, что у панели эмодзи; живёт в нижней стеклянной панели. */
@Composable
fun AttachActionsPanel(
    onFile: () -> Unit,
    onMedia: () -> Unit,
    onPoll: () -> Unit,
    onLocation: () -> Unit,
    onVoice: () -> Unit,
    onRound: () -> Unit,
) {
    val tiles = listOf(
        AttachActionTile(Icons.Filled.AttachFile, tr("Файл"), onFile),
        AttachActionTile(Icons.Filled.Image, tr("Фото и видео"), onMedia),
        AttachActionTile(Icons.Filled.Poll, tr("Опрос"), onPoll),
        AttachActionTile(Icons.Filled.LocationOn, tr("Геопозиция"), onLocation),
        AttachActionTile(Icons.Filled.Mic, tr("Голосовое"), onVoice),
        AttachActionTile(Icons.Filled.Videocam, tr("Кружок"), onRound),
    )
    val rows = tiles.chunked(3)
    val big = 24.dp
    val small = 6.dp
    // Соединённая группа: крупные скругления только по внешним углам, внутри малые, зазор 2dp.
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEachIndexed { c, tile ->
                    val top = r == 0
                    val bottom = r == rows.lastIndex
                    val left = c == 0
                    val right = c == row.lastIndex
                    val shape = RoundedCornerShape(
                        topStart = if (top && left) big else small,
                        topEnd = if (top && right) big else small,
                        bottomStart = if (bottom && left) big else small,
                        bottomEnd = if (bottom && right) big else small,
                    )
                    Surface(
                        onClick = tile.onClick,
                        shape = shape,
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