package app.yougram.feature

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TelegramSettingsEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text("Telegram") },
        supportingContent = { Text("Premium, Звёзды, Бизнес, Подарки, Помощь") },
        leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
        modifier = modifier.clickable(onClick = onClick),
    )
}
