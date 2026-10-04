package app.yougram.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.yougram.data.SettingsRepository

@Composable
fun DataStorageScreen(viewModel: SettingsDetailsViewModel, settings: SettingsRepository, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val prefs by settings.dataPrefs.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadStorage() }
    var confirmClear by remember { mutableStateOf(false) }
    val storage = state.storage
    fun size(bytes: Long) = Formatter.formatFileSize(context, bytes)

    SettingsPageColumn(contentPadding) {
        SectionLabel("Использование памяти")
        SettingGroup {
            item { SettingRow("Кэш медиа", icon = Icons.Filled.Storage, value = storage?.let { size(it.filesBytes) } ?: "…") }
            item { SettingRow("База данных", icon = Icons.Filled.Storage, value = storage?.let { size(it.databaseBytes) } ?: "…") }
            item { SettingRow("Очистить кэш", subtitle = "Файлы можно скачать заново", icon = Icons.Filled.Delete, onClick = { confirmClear = true }) }
        }

        SectionLabel("Автозагрузка медиа")
        SettingGroup {
            item { SwitchRow("Фото", prefs.autoPhotos, { v -> settings.updateDataPrefs { it.copy(autoPhotos = v) } }, icon = Icons.Filled.Image) }
            item { SwitchRow("Видео", prefs.autoVideos, { v -> settings.updateDataPrefs { it.copy(autoVideos = v) } }, icon = Icons.Filled.VideoLibrary) }
            item { SwitchRow("Файлы", prefs.autoFiles, { v -> settings.updateDataPrefs { it.copy(autoFiles = v) } }, icon = Icons.Filled.AttachFile) }
            item { SwitchRow("Только по Wi-Fi", prefs.onlyWifi, { v -> settings.updateDataPrefs { it.copy(onlyWifi = v) } }, icon = Icons.Filled.Wifi) }
        }
        SettingsFootnote("Параметры автозагрузки сохраняются, но пока не влияют на загрузку медиа в чатах.")
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Очистить кэш?",
            text = "Скачанные фото, видео и файлы будут удалены с устройства.",
            confirmLabel = "Очистить",
            onConfirm = {
                viewModel.clearCache()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}