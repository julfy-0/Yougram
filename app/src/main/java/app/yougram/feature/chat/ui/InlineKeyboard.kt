package app.yougram.feature.chat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.unit.Dp
import app.yougram.core.ui.DeviceTier
import app.yougram.core.ui.rememberDeviceTier
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

    // Сгруппированная клавиатура: внешние углы крупные, внутренние мелкие; нажатая кнопка «округляется».
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        message.buttons.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEachIndexed { colIndex, button ->
                    InlineButtonCell(
                        button = button,
                        firstRow = rowIndex == 0,
                        lastRow = rowIndex == message.buttons.lastIndex,
                        firstCol = colIndex == 0,
                        lastCol = colIndex == row.lastIndex,
                        pending = pending == rowIndex to colIndex,
                        modifier = Modifier.weight(1f),
                        onClick = { press(rowIndex, colIndex, button) },
                    )
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

private val KeyOuter = 16.dp
private val KeyInner = 6.dp

@Composable
private fun InlineButtonCell(
    button: InlineButton,
    firstRow: Boolean,
    lastRow: Boolean,
    firstCol: Boolean,
    lastCol: Boolean,
    pending: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val lowTier = rememberDeviceTier() == DeviceTier.Low
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val cornerSpec: FiniteAnimationSpec<Dp> = if (lowTier) snap() else MaterialTheme.motionScheme.fastSpatialSpec()
    // При нажатии внутренние углы вырастают до внешних: форма «перетекает».
    val inner by animateDpAsState(if (pressed) KeyOuter else KeyInner, cornerSpec, label = "keyInner")
    val i = inner.coerceAtLeast(0.dp)
    val shape = RoundedCornerShape(
        topStart = if (firstRow && firstCol) KeyOuter else i,
        topEnd = if (firstRow && lastCol) KeyOuter else i,
        bottomStart = if (lastRow && firstCol) KeyOuter else i,
        bottomEnd = if (lastRow && lastCol) KeyOuter else i,
    )
    Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .alpha(if (pending) 0.5f else 1f),
        shape = shape,
        // Полупрозрачные кнопки: сквозь них виден пузырь сообщения.
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        interactionSource = interaction,
        onClick = onClick,
    ) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(
                button.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
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
                    modifier = Modifier.size(12.dp).align(Alignment.TopEnd),
                )
            }
        }
    }
}