package app.yougram.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.yougram.core.i18n.Text

private class AuthFeature(val icon: ImageVector, val title: String, val text: String)

private val features = listOf(
    AuthFeature(
        Icons.Filled.VisibilityOff,
        "Режим призрака",
        "Читайте чаты, не отправляя отметку «прочитано». Собеседник не узнает, что вы видели сообщение.",
    ),
    AuthFeature(
        Icons.Filled.History,
        "Архив удалённого и правок",
        "Yougram сохраняет на устройстве удалённые сообщения, историю правок и вложения. В обычном Telegram они пропадают.",
    ),
    AuthFeature(
        Icons.Filled.FilterAlt,
        "Фильтры и теневой бан",
        "Скрывайте сообщения по своим шаблонам и людей целиком. Это работает только у вас, собеседник ничего не узнает.",
    ),
    AuthFeature(
        Icons.Filled.Palette,
        "Глубокая кастомизация",
        "Свои темы и акценты, шрифты из Google Fonts, значок приложения, плашки чатов и настраиваемое размытие стекла.",
    ),
    AuthFeature(
        Icons.Filled.Extension,
        "Плагины",
        "Магазин плагинов с установкой в одно касание. Плагины расширяют клиент тем, чего нет в Telegram.",
    ),
    AuthFeature(
        Icons.Filled.AutoAwesome,
        "ИИ-ассистент",
        "Встроенный помощник прямо в приложении, без сторонних ботов.",
    ),
    AuthFeature(
        Icons.Filled.WorkspacePremium,
        "Premium-оформление на устройстве",
        "Статус Premium и звезда в интерфейсе только у вас на экране, для остальных ничего не меняется.",
    ),
)

/** Окно «Что умеет Yougram»: возможности, которых нет в обычном Telegram. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthFeaturesSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Что умеет Yougram", style = MaterialTheme.typography.titleLarge)
            Text(
                "То, чего нет в обычном Telegram",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            features.forEach { f ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            f.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(f.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            f.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}