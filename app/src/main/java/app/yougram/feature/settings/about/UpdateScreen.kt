package app.yougram.feature.settings.about

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import app.yougram.core.ui.component.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yougram.BuildConfig
import app.yougram.R
import app.yougram.core.settings.GlassSettings
import app.yougram.core.telegram.getOrThrow
import app.yougram.core.ui.glass.BackdropState
import app.yougram.core.ui.glass.glass
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.update.data.AppUpdater
import app.yougram.feature.update.data.UpdateInfo
import app.yougram.feature.update.data.UpdateState
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.OptionValueString

/** Баннер «О приложении»: используется и на экране «О приложении», и на экране обновления. */
@Composable
internal fun YougramAboutBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.about_banner),
            contentDescription = "Баннер Yougram",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * Страница «Обновление клиента» — обычная страница настроек с общими верхней и нижней панелями.
 * Сверху версия и список изменений (если есть обновление), ниже версия клиента, версия API и номер сборки.
 * Кнопка действия («Скачать» → прогресс → «Установить», а если обновления нет — «Проверить») закреплена
 * внизу на стеклянной панели — см. [UpdateGlassBar], её рисует MainScreen вместо нижней навигации.
 */
@Composable
fun UpdateScreen(
    updater: AppUpdater,
    client: TdlClient,
    contentPadding: PaddingValues,
) {
    val state by updater.state.collectAsState()
    val tdlibVersion by produceState("…", client) {
        value = runCatching {
            (client.getOption(name = "version").getOrThrow() as? OptionValueString)?.value
        }.getOrNull() ?: "—"
    }

    // Информация о версии нужна и при ошибке скачивания — запоминаем последнюю.
    var lastInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(state) {
        when (val s = state) {
            is UpdateState.Available -> lastInfo = s.info
            is UpdateState.Downloading -> lastInfo = s.info
            is UpdateState.Ready -> lastInfo = s.info
            is UpdateState.UpToDate -> lastInfo = null
            else -> Unit
        }
    }
    val hasUpdate = state is UpdateState.Available || state is UpdateState.Downloading || state is UpdateState.Ready
    val info = lastInfo.takeIf { hasUpdate }

    // Снизу страницу закрывает стеклянная панель с кнопкой: оставляем под неё место.
    val padding = PaddingValues(
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding() + UpdateBarExtraPadding,
    )
    SettingsPageColumn(padding) {
        YougramAboutBanner()

        if (info != null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
                Text(
                    "Доступна версия ${info.versionName}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Сейчас установлена v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionLabel("Что нового")
            val lines = remember(info) { info.notes.changelogLines() }
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (lines.isEmpty()) {
                        Text(
                            "Список изменений не указан.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        lines.forEach { line ->
                            Row {
                                Text("•", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(line, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        } else {
            // Обновления нет (или ещё проверяем / ошибка).
            val (text, isError) = when (val s = state) {
                UpdateState.Idle, UpdateState.Checking -> "Проверка обновлений…" to false
                UpdateState.UpToDate -> "Установлена последняя версия" to false
                is UpdateState.Error -> s.message to true
                else -> "" to false
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        SectionLabel("О клиенте")
        SettingGroup {
            item {
                SettingRow(
                    title = "Версия клиента",
                    icon = Icons.Filled.Info,
                    value = BuildConfig.VERSION_NAME,
                )
            }
            item {
                SettingRow(
                    title = "Версия API",
                    subtitle = "Telegram API (TDLib)",
                    icon = Icons.Filled.Info,
                    value = tdlibVersion,
                )
            }
            item {
                SettingRow(
                    title = "Номер сборки",
                    icon = Icons.Filled.Build,
                    value = BuildConfig.VERSION_CODE.toString(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Дополнительный нижний отступ страницы под панель с кнопкой (поверх отступа под обычную нижнюю панель). */
private val UpdateBarExtraPadding = 40.dp

/**
 * Нижняя панель страницы обновления: тот же «стеклянный» стиль с размытием, что у остальных панелей клиента.
 * В MainScreen показывается вместо нижней навигации, пока открыта страница «Обновление».
 */
@Composable
fun UpdateGlassBar(
    updater: AppUpdater,
    backdrop: BackdropState,
    glass: GlassSettings,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val state by updater.state.collectAsState()
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = bottomInset + 8.dp)
            .glass(backdrop, glass, RoundedCornerShape(32.dp))
            .padding(12.dp),
    ) {
        UpdateActions(updater, state)
    }
}

/** Прогресс загрузки (если качаем) и большая кнопка действия. */
@Composable
private fun UpdateActions(updater: AppUpdater, s: UpdateState) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (s is UpdateState.Downloading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Загрузка…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            LinearWavyProgressIndicator(
                progress = { s.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val label = when (s) {
            is UpdateState.Available -> "Скачать"
            is UpdateState.Downloading -> "Загрузка…"
            is UpdateState.Ready -> "Установить"
            UpdateState.Checking, UpdateState.Idle -> "Проверка…"
            UpdateState.UpToDate, is UpdateState.Error -> "Проверить"
        }
        Button(
            onClick = {
                when (s) {
                    is UpdateState.Available -> updater.download()
                    is UpdateState.Ready -> if (!updater.install()) {
                        Toast.makeText(context, "Разрешите установку из этого приложения и нажмите снова", Toast.LENGTH_LONG).show()
                    }
                    UpdateState.UpToDate, is UpdateState.Error -> updater.check()
                    else -> Unit
                }
            },
            enabled = s !is UpdateState.Downloading && s !is UpdateState.Checking && s !is UpdateState.Idle,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(60.dp),
        ) { Text(label, style = MaterialTheme.typography.titleMedium) }
    }
}

/** Разбивает текст `notes` на пункты: по строкам, убирая маркеры «-», «*», «•». */
private fun String.changelogLines(): List<String> =
    lines()
        .map { it.trim().trimStart('-', '*', '•', '·').trim() }
        .filter { it.isNotEmpty() }