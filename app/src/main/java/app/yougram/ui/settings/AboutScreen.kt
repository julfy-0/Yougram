package app.yougram.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yougram.BuildConfig
import app.yougram.R

@Composable
fun AboutScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val openUrl = { url: String ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    SettingsPageColumn(contentPadding) {
        // Место под баннер
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.about_banner),
                contentDescription = "Баннер О приложении",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Yougram",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Версия ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Современный и быстрый клиент Telegram для Android",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        SectionLabel("Информация")
        SettingGroup {
            item {
                SettingRow(
                    title = "Версия Yougram",
                    subtitle = "Текущий релиз",
                    value = BuildConfig.VERSION_NAME,
                    icon = Icons.Filled.Info,
                )
            }
            item {
                SettingRow(
                    title = "Сборка",
                    subtitle = "Код версии: ${BuildConfig.VERSION_CODE}",
                    value = "v${BuildConfig.VERSION_NAME}",
                    icon = Icons.Filled.Build,
                )
            }
        }

        SectionLabel("Ссылки")
        SettingGroup {
            item {
                SettingRow(
                    title = "Вопросы о Telegram",
                    icon = Icons.Filled.QuestionMark,
                    onClick = { openUrl("https://telegram.org/faq") },
                )
            }
            item {
                SettingRow(
                    title = "Политика конфиденциальности",
                    icon = Icons.Filled.VerifiedUser,
                    onClick = { openUrl("https://telegram.org/privacy") },
                )
            }
        }

        SettingsFootnote("Баннер расположен в app/src/main/res/drawable/about_banner.png (рекомендуемые размеры: 1000×400 px, 2.5:1).")
    }
}
