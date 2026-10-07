package app.yougram.feature.settings.telegram

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LooksTwo
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yougram.core.ui.component.FileAvatar
import app.yougram.feature.chat.data.ChatRepository
import app.yougram.feature.chat.data.ContactItem
import app.yougram.feature.chat.data.ProfileItem
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import kotlinx.coroutines.launch

/** Официальный бот Telegram: оформление Premium и покупка звёзд проходят там, а не в приложении. */
private const val PREMIUM_BOT = "https://t.me/PremiumBot"
private const val STARS_DOCS = "https://core.telegram.org/bots/payments-stars"

internal fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun Hero(icon: ImageVector, title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PremiumScreen(isPremium: Boolean?, contentPadding: PaddingValues) {
    val context = LocalContext.current
    SettingsPageColumn(contentPadding) {
        Hero(
            Icons.Filled.Star,
            "Telegram Premium",
            "Выходите за лимиты и получайте эксклюзивные возможности с подпиской Telegram Premium.",
        )
        if (isPremium == true) {
            SettingsFootnote("У вас уже подключён Telegram Premium — спасибо за поддержку!")
        }
        SettingGroup {
            item { SettingRow("Истории", subtitle = "Неограниченное число публикаций, приоритетный показ, анонимный режим, сохранение истории просмотров и не только.", icon = Icons.Filled.AutoStories) }
            item { SettingRow("Неограниченное облачное хранилище", subtitle = "4 ГБ на один файл, неограниченное хранилище для ваших чатов и медиа.", icon = Icons.Filled.Cloud) }
            item { SettingRow("Удвоение лимитов", subtitle = "1000 каналов, 30 папок, 10 закреплённых чатов, 20 публичных ссылок, 4 аккаунта и многое другое.", icon = Icons.Filled.LooksTwo) }
            item { SettingRow("Скорость загрузки", subtitle = "Файлы скачиваются с максимальной скоростью.", icon = Icons.Filled.Speed) }
            item { SettingRow("Голос в текст", subtitle = "Расшифровка голосовых и видеосообщений.", icon = Icons.Filled.Mic) }
            item { SettingRow("Без рекламы", subtitle = "В публичных каналах не показывается реклама Telegram.", icon = Icons.Filled.Block) }
            item { SettingRow("Эмодзи и реакции", subtitle = "Тысячи эксклюзивных эмодзи и реакций, уникальные стикеры.", icon = Icons.Filled.EmojiEmotions) }
        }
        SettingsFootnote("Стоимость зависит от региона. Подписка оформляется через официальный бот Telegram.")
        Button(onClick = { openUrl(context, PREMIUM_BOT) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (isPremium == true) "Управление подпиской" else "Подписаться")
        }
    }
}

@Composable
fun StarsScreen(contentPadding: PaddingValues, onGift: () -> Unit) {
    val context = LocalContext.current
    SettingsPageColumn(contentPadding) {
        Hero(
            Icons.Filled.Star,
            "Звёзды Telegram",
            "Покупайте звёзды и получайте контент и услуги в мини-приложениях Telegram.",
        )
        Button(onClick = { openUrl(context, PREMIUM_BOT) }, modifier = Modifier.fillMaxWidth()) {
            Text("Купить звёзды")
        }
        OutlinedButton(onClick = onGift, modifier = Modifier.fillMaxWidth()) {
            Text("Подарить звёзды друзьям")
        }
        SettingGroup {
            item {
                SettingRow(
                    "Заработать звёзды",
                    subtitle = "Делитесь ссылками на мини-приложения и получайте часть их дохода в звёздах.",
                    icon = Icons.Filled.Share,
                    value = "NEW",
                    onClick = { openUrl(context, STARS_DOCS) },
                )
            }
        }
        SettingsFootnote("Баланс звёзд и история операций доступны в официальном приложении Telegram.")
    }
}

@Composable
fun BusinessScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    SettingsPageColumn(contentPadding) {
        Hero(
            Icons.Filled.Storefront,
            "Telegram для бизнеса",
            "Превратите аккаунт в бизнес-страницу с дополнительными возможностями. Telegram Premium временно включает в себя преимущества Telegram для бизнеса.",
        )
        SettingGroup {
            item { SettingRow("Адрес", subtitle = "Местоположение и адрес вашего бизнеса в профиле.", icon = Icons.Filled.LocationOn) }
            item { SettingRow("Часы работы", subtitle = "График работы для удобства клиентов.", icon = Icons.Filled.Schedule) }
            item { SettingRow("Быстрые ответы", subtitle = "Заготовленные ответы с форматированным текстом и медиафайлами.", icon = Icons.Filled.Bolt) }
            item { SettingRow("Приветствие и автоответ", subtitle = "Автоматические сообщения для новых клиентов и в нерабочее время.", icon = Icons.Filled.Forum) }
            item { SettingRow("Чат-боты", subtitle = "Подключайте ботов, которые отвечают клиентам от вашего имени.", icon = Icons.Filled.SmartToy) }
        }
        SettingsFootnote("Функции бизнес-аккаунта настраиваются в официальном приложении Telegram; здесь они пока недоступны.")
        Button(onClick = { openUrl(context, PREMIUM_BOT) }, modifier = Modifier.fillMaxWidth()) {
            Text("Подписаться")
        }
    }
}

/** Нижняя панель «Отправить подарок»: выбор человека; нажатие открывает чат с ним. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftSheet(repository: ChatRepository, onOpenChat: (Long) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val me by produceState<ProfileItem?>(null) { value = runCatching { repository.loadProfile() }.getOrNull() }
    val contacts by produceState<List<ContactItem>>(emptyList()) { value = runCatching { repository.loadContacts() }.getOrDefault(emptyList()) }
    val shown = contacts.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    fun open(userId: Long) {
        scope.launch {
            try {
                onOpenChat(repository.openPrivateChat(userId))
                onDismiss()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Ошибка", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Отправить подарок", modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("Поиск людей для отправки подарка…") },
            singleLine = true,
        )
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            val profile = me
            if (profile != null && query.isBlank()) {
                item { GiftSection("Это вы") }
                item {
                    GiftPerson(profile.name, "приобретите себе подарок", profile.avatarFileId, repository) { open(profile.id) }
                }
            }
            if (shown.isNotEmpty()) item { GiftSection("Контакты") }
            items(shown, key = { it.id }) { c ->
                GiftPerson(c.name, c.statusText, c.avatarFileId, repository) { open(c.id) }
            }
        }
    }
}

@Composable
private fun GiftSection(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun GiftPerson(name: String, subtitle: String, avatarFileId: Int?, repository: ChatRepository, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileAvatar(title = name, fileId = avatarFileId, fileState = repository::fileState)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
