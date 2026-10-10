package app.yougram.feature.settings.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build as AndroidBuild
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import app.yougram.core.ui.component.LinearWavyProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import app.yougram.core.ui.component.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.yougram.BuildConfig
import app.yougram.R
import app.yougram.core.telegram.getOrThrow
import app.yougram.feature.account.data.AccountManager
import app.yougram.feature.settings.SettingsPage
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.extras.PrimeEasterEgg
import app.yougram.feature.settings.telegram.openUrl
import app.yougram.feature.update.data.AppUpdater
import app.yougram.feature.update.data.UpdateState
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.OptionValueString
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SOURCE_URL = "https://github.com/julfy-0/yougram"
private const val CHANNEL_URL = "https://t.me/yougram" // TODO: ссылка на официальный канал

private data class Credit(val name: String, val role: String, val userId: Long)

// TODO: впиши реальных людей и их Telegram ID (числовой id аккаунта)
private val credits = listOf(
    Credit("julfy", "Разработка", 5558165896L),
    Credit("Дизайнер", "Дизайн интерфейса", 5558165896L),
    Credit("Переводчик", "Локализация", 123456789L),
)

private enum class DebugConfirm { ResetSettings, ClearDatabase }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    contentPadding: PaddingValues,
    updater: AppUpdater,
    client: TdlClient,
    accounts: AccountManager,
    onNavigate: (SettingsPage) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var showDebug by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<DebugConfirm?>(null) }
    val openDebug = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        showDebug = true
    }
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
        // Баннер (долгое нажатие — отладка)
        YougramAboutBanner(Modifier.combinedClickable(onClick = {}, onLongClick = openDebug))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = openDebug)
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
                text = "Yougram v${BuildConfig.VERSION_NAME} · на базе TDLib $tdlibVersion",
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

        SectionLabel("Проект")
        SettingGroup {
            item {
                SettingRow(
                    title = "Исходный код",
                    subtitle = "Открытый код на GitHub",
                    icon = Icons.Filled.Code,
                    onClick = { openUrl(SOURCE_URL) },
                )
            }
            item {
                SettingRow(
                    title = "Официальный канал",
                    subtitle = "Новости, опросы и бета-тесты",
                    icon = Icons.Filled.Campaign,
                    onClick = { openUrl(CHANNEL_URL) },
                )
            }
        }

        SectionLabel("Разработчики и благодарности")
        SettingGroup {
            credits.forEach { c ->
                item {
                    SettingRow(
                        title = c.name,
                        subtitle = c.role,
                        icon = Icons.Filled.Person,
                        onClick = { openUrl("tg://user?id=${c.userId}") },
                    )
                }
            }
            item {
                SettingRow(
                    title = "TDLib",
                    subtitle = "Telegram Database Library",
                    icon = Icons.Filled.Favorite,
                    onClick = { openUrl("https://core.telegram.org/tdlib") },
                )
            }
        }

        SettingsFootnote("Баннер расположен в app/src/main/res/drawable/about_banner.png (рекомендуемые размеры: 1000×400 px, 2.5:1).")
    }

    if (showPrime) PrimeEasterEgg(onDismiss = { showPrime = false })

    if (showDebug) {
        ModalBottomSheet(onDismissRequest = { showDebug = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Отладка",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                SettingRow(
                    title = "Экспорт логов",
                    subtitle = "Отправить разработчику при сбоях",
                    icon = Icons.Filled.Share,
                    onClick = {
                        showDebug = false
                        scope.launch {
                            val uri = exportLogs(context, tdlibVersion)
                            if (uri == null) {
                                Toast.makeText(context, "Не удалось собрать логи", Toast.LENGTH_SHORT).show()
                            } else {
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, "Экспорт логов").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    },
                )
                SettingRow(
                    title = "Перезапустить приложение",
                    icon = Icons.Filled.RestartAlt,
                    onClick = { showDebug = false; accounts.restartApp() },
                )
                SettingRow(
                    title = "Сбросить настройки",
                    subtitle = "Тема, плашки, уведомления и т.д.",
                    icon = Icons.Filled.SettingsBackupRestore,
                    onClick = { showDebug = false; confirm = DebugConfirm.ResetSettings },
                )
                SettingRow(
                    title = "Очистить внутреннюю базу данных",
                    subtitle = "Удалит кэш TDLib активного аккаунта",
                    icon = Icons.Filled.DeleteForever,
                    onClick = { showDebug = false; confirm = DebugConfirm.ClearDatabase },
                )
            }
        }
    }

    confirm?.let { action ->
        val (title, text) = when (action) {
            DebugConfirm.ResetSettings ->
                "Сбросить настройки?" to "Все настройки Yougram вернутся к значениям по умолчанию. Приложение перезапустится."
            DebugConfirm.ClearDatabase ->
                "Очистить базу данных?" to "Локальная база и файлы активного аккаунта будут удалены, потребуется войти заново. Приложение перезапустится."
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (action) {
                        DebugConfirm.ResetSettings -> {
                            context.getSharedPreferences("yougram_settings", Context.MODE_PRIVATE).edit().clear().commit()
                            accounts.restartApp()
                        }
                        DebugConfirm.ClearDatabase -> {
                            val id = accounts.activeAccountId.value
                            runCatching { File(context.filesDir, id).deleteRecursively() }
                            runCatching {
                                File(context.getExternalFilesDir(null) ?: context.filesDir, "${id}_files").deleteRecursively()
                            }
                            accounts.restartApp()
                        }
                    }
                }) { Text("Подтвердить") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
        )
    }
}

/** Собирает logcat приложения в файл в cache/logs и отдаёт content:// Uri для шаринга. */
private suspend fun exportLogs(context: Context, tdlibVersion: String): android.net.Uri? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "logs").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "yougram_log_${System.currentTimeMillis()}.txt")
            val proc = ProcessBuilder("logcat", "-d", "-v", "threadtime").redirectErrorStream(true).start()
            file.bufferedWriter().use { w ->
                w.appendLine("Yougram ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), TDLib $tdlibVersion")
                w.appendLine("Android ${AndroidBuild.VERSION.RELEASE} (API ${AndroidBuild.VERSION.SDK_INT}), ${AndroidBuild.MANUFACTURER} ${AndroidBuild.MODEL}")
                w.appendLine("----")
                proc.inputStream.bufferedReader().copyTo(w)
            }
            proc.waitFor()
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()
    }