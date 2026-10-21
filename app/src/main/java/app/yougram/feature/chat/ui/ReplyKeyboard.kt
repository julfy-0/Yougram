package app.yougram.feature.chat.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.DeviceTier
import app.yougram.core.ui.rememberDeviceTier
import app.yougram.feature.chat.data.MessageItem
import app.yougram.feature.chat.data.ReplyKey
import app.yougram.feature.chat.data.ReplyKeyboardItem

/** Активная reply-клавиатура: берётся от последнего входящего сообщения с разметкой (список: новые — первыми). */
fun activeReplyKeyboard(messages: List<MessageItem>): Pair<ReplyKeyboardItem, Long>? {
    for (m in messages) {
        if (m.isOutgoing) continue
        if (m.removesKeyboard) return null
        m.replyKeyboard?.let { return it to m.id }
    }
    return null
}

private val ReplyKeyOuter = 16.dp
private val ReplyKeyInner = 6.dp

/** Панель кнопок бота под полем ввода: ряды равной ширины, сгруппированные скругления. */
@Composable
fun ReplyKeyboardPanel(
    keyboard: ReplyKeyboardItem,
    modifier: Modifier = Modifier,
    onKey: (ReplyKey) -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        keyboard.rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEachIndexed { colIndex, key ->
                    ReplyKeyCell(
                        key = key,
                        firstRow = rowIndex == 0,
                        lastRow = rowIndex == keyboard.rows.lastIndex,
                        firstCol = colIndex == 0,
                        lastCol = colIndex == row.lastIndex,
                        modifier = Modifier.weight(1f),
                        onClick = { onKey(key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyKeyCell(
    key: ReplyKey,
    firstRow: Boolean,
    lastRow: Boolean,
    firstCol: Boolean,
    lastCol: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val lowTier = rememberDeviceTier() == DeviceTier.Low
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val cornerSpec: FiniteAnimationSpec<Dp> = if (lowTier) snap() else MaterialTheme.motionScheme.fastSpatialSpec()
    val inner by animateDpAsState(if (pressed) ReplyKeyOuter else ReplyKeyInner, cornerSpec, label = "replyKeyInner")
    val i = inner.coerceAtLeast(0.dp)
    val shape = RoundedCornerShape(
        topStart = if (firstRow && firstCol) ReplyKeyOuter else i,
        topEnd = if (firstRow && lastCol) ReplyKeyOuter else i,
        bottomStart = if (lastRow && firstCol) ReplyKeyOuter else i,
        bottomEnd = if (lastRow && lastCol) ReplyKeyOuter else i,
    )
    Surface(
        modifier = modifier.heightIn(min = 52.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
        onClick = onClick,
    ) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                key.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}
