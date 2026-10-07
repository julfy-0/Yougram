package app.yougram.feature.chat.comments.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val accent = MaterialTheme.colorScheme.primary
    val label = if (count > 0) commentsLabel(count) else "Оставить комментарий"

    // Внутри пузыря кнопка «парит» с небольшим отступом от текста и краёв пузыря.
    Column(if (attached) modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp) else modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (attached) Modifier.clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.10f))
                    else Modifier.clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                )
                .clickable(onClick = onClick)
                .padding(
                    start = if (attached) 14.dp else 12.dp,
                    end = if (attached) 10.dp else 8.dp,
                    top = if (attached) 10.dp else 8.dp,
                    bottom = if (attached) 10.dp else 8.dp,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier.padding(start = 8.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
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
