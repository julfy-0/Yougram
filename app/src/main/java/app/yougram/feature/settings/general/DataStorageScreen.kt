package app.yougram.feature.settings.general

import app.yougram.core.i18n.tr
import android.net.TrafficStats
import android.os.Process
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import app.yougram.core.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.yougram.core.settings.SettingsRepository
import app.yougram.feature.auth.ui.ProxyManagerDialog
import app.yougram.feature.settings.SettingsDetailsViewModel
import app.yougram.feature.settings.component.ChoiceDialog
import app.yougram.feature.settings.component.ConfirmDialog
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.component.SwitchRow

private val CallsSavingOptions = listOf("Никогда", "Только в роуминге", "Всегда")

private enum class DataDialog { ClearCache, ResetAutoDownload, CallsSaving, Proxy, Drafts, StorageWarn, StorageCap }

private const val GB = 1L shl 30

/** Варианты порога предупреждения, байт (0 — не предупреждать). */
private val WarnOptions = listOf(0L, 1 * GB, 2 * GB, 3 * GB, 5 * GB, 10 * GB, 20 * GB)

/** Варианты лимита автозагрузки, байт (0 — без лимита). */
private val CapOptions = listOf(0L, 2 * GB, 5 * GB, 10 * GB, 20 * GB, 50 * GB)

private fun gbLabel(bytes: Long, zero: String) = if (bytes <= 0L) zero else tr("{0} ГБ", bytes / GB)

private fun yesNo(value: Boolean) = if (value) "Да" else "Нет"

private fun appTrafficBytes(): Long {
    val rx = TrafficStats.getUidRxBytes(Process.myUid())
    val tx = TrafficStats.getUidTxBytes(Process.myUid())
    val unsupported = TrafficStats.UNSUPPORTED.toLong()
    return (if (rx == unsupported) 0L else rx) + (if (tx == unsupported) 0L else tx)
}

@Composable
private fun DangerRow(title: String, onClick: () -> Unit) {
    Text(
        title,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
fun DataStorageScreen(viewModel: SettingsDetailsViewModel, settings: SettingsRepository, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val prefs by settings.dataPrefs.collectAsState()
    val proxies by settings.proxies.collectAsState()
    val activeProxyId by settings.activeProxyId.collectAsState()
    var traffic by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        viewModel.loadStorage()
        traffic = appTrafficBytes()
    }
    var dialog by remember { mutableStateOf<DataDialog?>(null) }
    val storage = state.storage
    fun size(bytes: Long) = Formatter.formatFileSize(context, bytes)

    SettingsPageColumn(contentPadding) {
        SectionLabel("Использование сети и кэша")
        SettingGroup {
            item { SettingRow("Использование памяти", icon = Icons.Filled.Storage, value = storage?.let { size(it.filesBytes + it.databaseBytes) } ?: "…") }
            item { SettingRow("Использование трафика", icon = Icons.Filled.BarChart, value = size(traffic)) }
            item { SettingRow("Очистить кэш", subtitle = "Файлы можно скачать заново", icon = Icons.Filled.Delete, onClick = { dialog = DataDialog.ClearCache }) }
        }

        SectionLabel("Лимиты памяти")
        SettingGroup {
            item {
                SettingRow(
                    "Предупреждать при занятой памяти",
                    subtitle = "Покажем уведомление с кнопкой очистки кэша",
                    value = gbLabel(prefs.storageWarnBytes, "Выкл."),
                    onClick = { dialog = DataDialog.StorageWarn },
                )
            }
            item {
                SettingRow(
                    "Лимит автозагрузки",
                    subtitle = "Выше этого объёма ничего не скачивается само",
                    value = gbLabel(prefs.autoDownloadCapBytes, "Без лимита"),
                    onClick = { dialog = DataDialog.StorageCap },
                )
            }
        }
        SettingsFootnote("Считается вся память приложения: скачанные файлы и база. Фото, видео и файлы выше лимита по-прежнему можно скачать вручную — нажатием на сообщение. Превью, стикеры и аватарки загружаются всегда, они маленькие.")

        SectionLabel("Автозагрузка медиа")
        SettingGroup {
            item { SwitchRow("Через мобильную сеть", prefs.autoMobile, { v -> settings.updateDataPrefs { it.copy(autoMobile = v) } }, subtitle = "Фото, Видео (10 MB), Файлы (1 MB)") }
            item { SwitchRow("Через сети Wi-Fi", prefs.autoWifi, { v -> settings.updateDataPrefs { it.copy(autoWifi = v) } }, subtitle = "Фото, Видео (15 MB), Файлы (3 MB)") }
            item { SwitchRow("В роуминге", prefs.autoRoaming, { v -> settings.updateDataPrefs { it.copy(autoRoaming = v) } }, subtitle = "Фото") }
            item { DangerRow("Сбросить настройки") { dialog = DataDialog.ResetAutoDownload } }
        }

        SectionLabel("Сохранять в галерее")
        SettingGroup {
            item { SwitchRow("Личные чаты", prefs.saveToGalleryPrivate, { v -> settings.updateDataPrefs { it.copy(saveToGalleryPrivate = v) } }, subtitle = yesNo(prefs.saveToGalleryPrivate)) }
            item { SwitchRow("Группы", prefs.saveToGalleryGroups, { v -> settings.updateDataPrefs { it.copy(saveToGalleryGroups = v) } }, subtitle = yesNo(prefs.saveToGalleryGroups)) }
            item { SwitchRow("Каналы", prefs.saveToGalleryChannels, { v -> settings.updateDataPrefs { it.copy(saveToGalleryChannels = v) } }, subtitle = yesNo(prefs.saveToGalleryChannels)) }
        }

        SectionLabel("Стриминг")
        SettingGroup {
            item { SwitchRow("Стриминг аудиофайлов и видео", prefs.streamMedia, { v -> settings.updateDataPrefs { it.copy(streamMedia = v) } }) }
            item { SwitchRow("Stream MKV Videos β", prefs.streamMkv, { v -> settings.updateDataPrefs { it.copy(streamMkv = v) } }) }
            item { SwitchRow("Stream ALL Videos β", prefs.streamAll, { v -> settings.updateDataPrefs { it.copy(streamAll = v) } }) }
        }
        SettingsFootnote("Когда это возможно, приложение будет воспроизводить видеозаписи и музыку, не дожидаясь завершения загрузки.")

        SectionLabel("Звонки")
        SettingGroup {
            item {
                SettingRow(
                    "Экономия трафика",
                    value = CallsSavingOptions.getOrElse(prefs.callsDataSaving) { CallsSavingOptions[1] },
                    onClick = { dialog = DataDialog.CallsSaving },
                )
            }
        }

        SectionLabel("Прокси")
        SettingGroup {
            item {
                SettingRow(
                    "Прокси-серверы",
                    value = proxies.firstOrNull { it.id == activeProxyId }?.title
                        ?: if (proxies.isEmpty()) null else tr("Выключен · сохранено: {0}", proxies.size),
                    onClick = { dialog = DataDialog.Proxy },
                )
            }
            item { SettingRow("Удалить черновики", onClick = { dialog = DataDialog.Drafts }) }
        }
    }

    when (dialog) {
        DataDialog.ClearCache -> ConfirmDialog(
            title = "Очистить кэш?",
            text = "Скачанные фото, видео и файлы будут удалены с устройства.",
            confirmLabel = "Очистить",
            onConfirm = {
                viewModel.clearCache()
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        DataDialog.StorageWarn -> ChoiceDialog(
            title = "Предупреждать при",
            options = WarnOptions,
            selected = prefs.storageWarnBytes,
            label = { gbLabel(it, "Не предупреждать") },
            onSelect = { v ->
                settings.updateDataPrefs { it.copy(storageWarnBytes = v) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        DataDialog.StorageCap -> ChoiceDialog(
            title = "Лимит автозагрузки",
            options = CapOptions,
            selected = prefs.autoDownloadCapBytes,
            label = { gbLabel(it, "Без лимита") },
            onSelect = { v ->
                settings.updateDataPrefs { it.copy(autoDownloadCapBytes = v) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        DataDialog.ResetAutoDownload -> ConfirmDialog(
            title = "Сбросить настройки?",
            text = "Параметры автозагрузки медиа вернутся к значениям по умолчанию.",
            confirmLabel = "Сбросить",
            onConfirm = {
                settings.updateDataPrefs { it.copy(autoMobile = true, autoWifi = true, autoRoaming = true, storageWarnBytes = 5L shl 30, autoDownloadCapBytes = 10L shl 30) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        DataDialog.CallsSaving -> ChoiceDialog(
            title = "Экономия трафика",
            options = CallsSavingOptions.indices.toList(),
            selected = prefs.callsDataSaving,
            label = { CallsSavingOptions[it] },
            onSelect = { v ->
                settings.updateDataPrefs { it.copy(callsDataSaving = v) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        DataDialog.Proxy -> ProxyManagerDialog(
            proxies = proxies,
            activeId = activeProxyId,
            onSelect = settings::selectProxy,
            onSave = settings::saveProxy,
            onDelete = settings::deleteProxy,
            onDismiss = { dialog = null },
        )
        DataDialog.Drafts -> ConfirmDialog(
            title = "Удалить черновики?",
            text = "Все несохранённые черновики сообщений будут удалены.",
            confirmLabel = "Удалить",
            onConfirm = {
                Toast.makeText(context, tr("Удаление черновиков появится после интеграции с TDLib"), Toast.LENGTH_SHORT).show()
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}