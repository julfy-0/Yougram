package app.yougram.feature.chat.comments.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Кнопка «Комментарии» под постом канала.
 * [attached] = true — полоса внутри пузыря поста, вплотную к нему (углы обрезает пузырь);
 * false — отдельная таблетка под сообщением (для постов без пузыря).
 */
@Composable
fun CommentsButton(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    attached: Boolean = true,
) {
    val container = MaterialTheme.colorScheme.secondaryContainer
    val content = MaterialTheme.colorScheme.onSecondaryContainer
    val label = if (count > 0) commentsLabel(count) else "Оставить комментарий"

    // Внутри пузыря кнопка «парит» с небольшим отступом от текста и краёв пузыря.
    Column(if (attached) modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp) else modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(if (attached) MaterialTheme.shapes.medium else CircleShape)
                .background(container)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(
                    start = if (attached) 14.dp else 14.dp,
                    end = if (attached) 8.dp else 8.dp,
                    top = 6.dp,
                    bottom = 6.dp,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            // Шеврон в круглом «пятне»: акцентное действие на контейнере.
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun commentsLabel(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    val word = when {
        mod100 in 11..14 -> "комментариев"
        mod10 == 1 -> "комментарий"
        mod10 in 2..4 -> "комментария"
        else -> "комментариев"
    }
    return "$n $word"
}
