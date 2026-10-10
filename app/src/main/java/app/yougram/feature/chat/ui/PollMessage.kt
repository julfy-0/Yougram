package app.yougram.feature.chat.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yougram.feature.chat.data.PollItem
import kotlinx.coroutines.launch

/**
 * Опрос внутри пузыря сообщения. Цвета берутся из [LocalContentColor] пузыря, поэтому подходит и
 * для своих, и для чужих сообщений. Один ответ отправляется сразу по нажатию, несколько — кнопкой.
 */
@Composable
fun PollMessage(
    poll: PollItem,
    messageId: Long,
    viewModel: ChatViewModel,
    canStop: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = app.yougram.core.ui.rememberHaptics()
    val content = LocalContentColor.current
    var busy by remember(poll.id) { mutableStateOf(false) }
    // Выбор в опросе с несколькими ответами копится локально до нажатия «Проголосовать».
    val selection = remember(poll.id, poll.options.map { it.chosen }) {
        poll.options.mapIndexed { i, o -> if (o.chosen) i else -1 }.filter { it >= 0 }.toMutableStateList()
    }
    val locked = poll.closed || (poll.voted && !poll.allowsRevoting)
    val showResults = poll.canSeeResults && (poll.voted || poll.closed)

    fun send(ids: List<Int>) {
        if (busy) return
        busy = true
        haptics(app.yougram.core.ui.Haptics.Kind.Click)
        scope.launch {
            viewModel.votePoll(messageId, ids).onFailure {
                haptics(app.yougram.core.ui.Haptics.Kind.Reject)
                Toast.makeText(context, it.message ?: "Не удалось проголосовать", Toast.LENGTH_SHORT).show()
            }
            busy = false
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(poll.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = buildString {
                append(
                    when {
                        poll.closed -> "Опрос завершён"
                        poll.quiz -> "Викторина"
                        poll.anonymous -> "Анонимный опрос"
                        else -> "Открытый опрос"
                    },
                )
                if (poll.multiple && !poll.closed) append(" · несколько ответов")
            },
            style = MaterialTheme.typography.labelMedium,
            color = content.copy(alpha = 0.7f),
        )

        poll.options.forEachIndexed { index, option ->
            val selected = if (poll.multiple) index in selection else option.chosen
            val fraction = if (showResults) (option.percent / 100f).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(content.copy(alpha = 0.08f))
                    .clickable(enabled = !locked && !busy) {
                        if (poll.multiple) {
                            if (index in selection) selection.remove(index) else selection.add(index)
                        } else if (option.chosen) {
                            // Повторное нажатие на свой ответ снимает голос (только если разрешено переголосовывать).
                            send(emptyList())
                        } else {
                            send(listOf(index))
                        }
                    },
            ) {
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(content.copy(alpha = if (selected) 0.30f else 0.16f)),
                    )
                }
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = when {
                        showResults && option.correct -> Icons.Filled.Check
                        poll.multiple && selected -> Icons.Filled.CheckBox
                        poll.multiple -> Icons.Filled.CheckBoxOutlineBlank
                        selected -> Icons.Filled.RadioButtonChecked
                        else -> Icons.Filled.RadioButtonUnchecked
                    }
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = content.copy(alpha = 0.85f))
                    Text(
                        option.text,
                        Modifier.weight(1f).padding(horizontal = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (showResults) {
                        Text(
                            "${option.percent}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(IntrinsicWidthPercent),
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = voterLabel(poll.totalVoters),
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            if (!poll.closed && poll.multiple && !(poll.voted && !poll.allowsRevoting)) {
                TextButton(
                    enabled = !busy && selection.isNotEmpty(),
                    onClick = { send(selection.sorted()) },
                ) { Text("Проголосовать") }
            }
            if (!poll.closed && poll.voted && poll.allowsRevoting && poll.multiple) {
                TextButton(enabled = !busy, onClick = { send(emptyList()) }) { Text("Отменить") }
            }
            if (!poll.closed && canStop) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            viewModel.stopPoll(messageId).onFailure {
                                Toast.makeText(context, it.message ?: "Не удалось завершить опрос", Toast.LENGTH_SHORT).show()
                            }
                            busy = false
                        }
                    },
                ) { Text("Завершить") }
            }
        }
    }
}

private val IntrinsicWidthPercent = 44.dp

/** «1 голос», «2 голоса», «5 голосов»; для нуля — «Пока нет голосов». */
private fun voterLabel(count: Int): String {
    if (count <= 0) return "Пока нет голосов"
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> "голосов"
        mod10 == 1 -> "голос"
        mod10 in 2..4 -> "голоса"
        else -> "голосов"
    }
    return "$count $word"
}
