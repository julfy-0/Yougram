package app.yougram.feature.chat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.component.LocalOpenLink
import app.yougram.core.ui.component.TextButton
import app.yougram.feature.chat.data.InlineButton
import app.yougram.feature.chat.data.InlineButtonKind
import app.yougram.feature.chat.data.MessageItem
import kotlinx.coroutines.launch

/** Inline-клавиатура бота под сообщением: ряды кнопок одинаковой ширины внутри ряда. */
@Composable
fun InlineKeyboard(message: MessageItem, viewModel: ChatViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val openLink = LocalOpenLink.current
    val scope = rememberCoroutineScope()
    // Позиция кнопки, ответа на которую ждём; такая кнопка приглушена и повторно не нажимается.
    var pending by remember(message.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var alertText by remember { mutableStateOf<String?>(null) }

    fun press(row: Int, col: Int, button: InlineButton) {
        when (button.kind) {
            InlineButtonKind.URL -> button.url?.let(openLink)
            InlineButtonKind.COPY -> {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("text", button.copyText.orEmpty()))
                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
            }
            InlineButtonKind.CALLBACK -> {
                if (pending != null) return
                val data = button.data ?: return
                pending = row to col
                scope.launch {
                    viewModel.pressButton(message.id, data)
                        .onSuccess { answer ->
                            when {
                                answer.url.isNotBlank() -> openLink(answer.url)
                                answer.text.isBlank() -> Unit
                                answer.showAlert -> alertText = answer.text
                                else -> Toast.makeText(context, answer.text, Toast.LENGTH_SHORT).show()
                            }
                        }
                        .onFailure { e ->
                            Toast.makeText(context, e.message ?: "Бот не ответил", Toast.LENGTH_SHORT).show()
                        }
                    pending = null
                }
            }
            InlineButtonKind.OTHER ->
                Toast.makeText(context, "Эта кнопка пока не поддерживается", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        message.buttons.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEachIndexed { colIndex, button ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .alpha(if (pending == rowIndex to colIndex) 0.5f else 1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                        onClick = { press(rowIndex, colIndex, button) },
                    ) {
                        Box(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                button.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val badge = when (button.kind) {
                                InlineButtonKind.URL -> Icons.Filled.NorthEast
                                InlineButtonKind.COPY -> Icons.Filled.ContentCopy
                                else -> null
                            }
                            if (badge != null) {
                                Icon(
                                    badge,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp).align(Alignment.TopEnd),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    alertText?.let { text ->
        AlertDialog(
            onDismissRequest = { alertText = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { alertText = null }) { Text("OK") } },
        )
    }
}