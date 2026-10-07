package app.yougram.feature.settings.telegram

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramSettingsScreen(
    onBack: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenStars: () -> Unit,
    onOpenBusiness: () -> Unit,
    onSendGift: () -> Unit,
    onAskQuestion: () -> Unit,
) {
    val uri = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Telegram") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsRow("Telegram Premium", Icons.Default.Star, onOpenPremium)
            SettingsRow("Звёзды Telegram", Icons.Default.Favorite, onOpenStars)
            SettingsRow("Telegram для бизнеса", Icons.Default.ShoppingCart, onOpenBusiness)
            SettingsRow("Отправить подарок", Icons.Default.Share, onSendGift)
            HorizontalDivider()
            SettingsRow("Задать вопрос", Icons.Default.Email, onAskQuestion)
            SettingsRow("Вопросы о Telegram", Icons.Default.Info) { uri.openUri("https://telegram.org/faq") }
            SettingsRow("Возможности Telegram", Icons.Default.Star) { uri.openUri("https://telegram.org/tour") }
            SettingsRow("Политика конфиденциальности", Icons.Default.Lock) { uri.openUri("https://telegram.org/privacy") }
        }
    }
}

@Composable
private fun SettingsRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
