package app.yougram.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yougram.feature.chat.comments.data.CommentsInfo

/** Кнопка под постом. Цвета берёт из BubbleStyle, поэтому всегда читаема. */
@Composable
fun CommentsButton(info: CommentsInfo, style: BubbleStyle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (info.count == 0) "Оставить комментарий" else "Комментарии: ${info.count}"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(style.chipBg.copy(alpha = style.chipBg.alpha * 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = style.link, fontWeight = FontWeight.Medium)
        Text(if (info.hasUnread) "●" else "›", color = if (info.hasUnread) style.link else style.secondary)
    }
}