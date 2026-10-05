package app.yougram.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.yougram.data.AppUpdater
import app.yougram.data.UpdateState
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.OptionValueString
import app.yougram.data.getOrThrow

@Composable
fun AboutScreen(contentPadding: PaddingValues, updater: AppUpdater, client: TdlClient) {
    val context = LocalContext.current
    val update by updater.state.collectAsState()
    LaunchedEffect(Unit) { updater.check() }
    val tdlibVersion by produceState("…", client) {
        value = runCatching {
            (client.getOption(name = "version").getOrThrow() as? OptionValueString)?.value
        }.getOrNull() ?: "—"
    }
    val openUrl = { url: String ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var showPrime by remember { mutableStateOf(false) }

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
                    title = "Версия API",
                    subtitle = "Telegram API (TDLib)",
                    value = tdlibVersion,
                    icon = Icons.Filled.Info,
                )
            }
            item {
                SettingRow(
                    title = "Сборка",
                    subtitle = "Код версии: ${BuildConfig.VERSION_CODE}",
                    value = "v${BuildConfig.VERSION_NAME}",
                    icon = Icons.Filled.Build,
                    onClick = {
                        val now = SystemClock.uptimeMillis()
                        // Серия тапов обрывается, если пауза больше 1.5 секунд.
                        taps = if (now - lastTap > 1500) 1 else taps + 1
                        lastTap = now
                        if (taps >= 5) {
                            taps = 0
                            showPrime = true
                        }
                    },
                )
            }
        }

        SectionLabel("Обновление")
        SettingGroup {
            item {
                when (val u = update) {
                    UpdateState.Idle, UpdateState.Checking -> SettingRow(
                        title = "Проверка обновлений…", icon = Icons.Filled.SystemUpdate,
                    )
                    UpdateState.UpToDate -> SettingRow(
                        title = "Установлена последняя версия", icon = Icons.Filled.SystemUpdate,
                        value = "Проверить", onClick = { updater.check() },
                    )
                    is UpdateState.Available -> SettingRow(
                        title = "Доступна версия ${u.info.versionName}",
                        subtitle = u.info.notes.ifBlank { null },
                        icon = Icons.Filled.SystemUpdate,
                        value = "Скачать", onClick = { updater.download() },
                    )
                    is UpdateState.Downloading -> SettingRow(
                        title = "Загрузка ${(u.progress * 100).toInt()}%", icon = Icons.Filled.SystemUpdate,
                        below = {
                            LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
                        },
                    )
                    is UpdateState.Ready -> SettingRow(
                        title = "Версия ${u.info.versionName} скачана",
                        subtitle = "Нажмите, чтобы установить",
                        icon = Icons.Filled.SystemUpdate,
                        value = "Обновить",
                        onClick = {
                            if (!updater.install()) {
                                Toast.makeText(context, "Разрешите установку из этого приложения и нажмите снова", Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                    is UpdateState.Error -> SettingRow(
                        title = u.message, icon = Icons.Filled.SystemUpdate,
                        value = "Повторить", onClick = { updater.check() },
                    )
                }
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

    if (showPrime) PrimeEasterEgg(onDismiss = { showPrime = false })
}