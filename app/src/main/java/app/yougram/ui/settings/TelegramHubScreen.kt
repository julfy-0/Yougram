package app.yougram.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink

/** Вкладка «Telegram»: Premium, Звёзды, Бизнес, подарок и справочные пункты. */
@Composable
fun TelegramHubScreen(
    viewModel: SettingsHomeViewModel,
    contentPadding: PaddingValues,
    onNavigate: (SettingsPage) -> Unit,
    onOpenChat: (Long) -> Unit,
    onGift: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var askDialog by remember { mutableStateOf(false) }

    val openUrl = { url: String ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    SettingsPageColumn(contentPadding) {
        SettingGroup {
            item { SettingRow("Telegram Premium", icon = Icons.Filled.Star, onClick = { onNavigate(SettingsPage.Premium) }) }
            item { SettingRow("Звёзды Telegram", icon = Icons.Filled.Star, onClick = { onNavigate(SettingsPage.Stars) }) }
            item { SettingRow("Telegram для бизнеса", icon = Icons.Filled.Storefront, onClick = { onNavigate(SettingsPage.Business) }) }
            item { SettingRow("Отправить подарок", icon = Icons.Filled.CardGiftcard, onClick = onGift) }
        }

        SectionLabel("Помощь")
        SettingGroup {
            item { SettingRow("Задать вопрос", icon = Icons.Filled.ChatBubble, onClick = { askDialog = true }) }
            item { SettingRow("Вопросы о Telegram", icon = Icons.Filled.QuestionMark, onClick = { openUrl("https://telegram.org/faq") }) }
            item { SettingRow("Возможности Telegram", icon = Icons.Filled.Lightbulb, onClick = { openUrl("https://telegram.org/tour") }) }
            item { SettingRow("Политика конфиденциальности", icon = Icons.Filled.VerifiedUser, onClick = { openUrl("https://telegram.org/privacy") }) }
        }
    }

    if (askDialog) {
        val linkColor = MaterialTheme.colorScheme.primary
        AlertDialog(
            onDismissRequest = { askDialog = false },
            title = { Text("Задать вопрос") },
            text = {
                Text(
                    buildAnnotatedString {
                        append("Поддержкой Telegram занимаются волонтёры. Мы стараемся отвечать как можно быстрее, однако иногда приходится немного подождать.\n\nОзнакомьтесь с ")
                        withLink(LinkAnnotation.Url("https://telegram.org/faq", TextLinkStyles(SpanStyle(color = linkColor)))) {
                            append("частыми вопросами о Telegram")
                        }
                        append(": там есть важные советы по устранению неисправностей и ответы на подробные вопросы.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askDialog = false
                    viewModel.openSupport(onOpenChat)
                }) { Text("Спросить") }
            },
            dismissButton = { TextButton(onClick = { askDialog = false }) { Text("Отмена") } },
        )
    }
}
