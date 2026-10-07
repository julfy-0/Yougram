package app.yougram.feature.settings.extras

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import app.yougram.core.ui.component.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yougram.feature.settings.component.CheckSwitch
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.plugin.InstalledPlugin
import app.yougram.plugin.NativePluginManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Вкладка «Плагины»: нативные (C++) плагины — список, включение, установка из .ygplugin. */
@Composable
fun PluginsScreen(manager: NativePluginManager, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val plugins by manager.plugins.collectAsState()
    var pendingDelete by remember { mutableStateOf<InstalledPlugin?>(null) }
    var confirmInstall by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val temp = File(context.cacheDir, "plugin-import-${System.nanoTime()}.ygplugin")
                try {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                            ?: error("Не удалось прочитать файл")
                        manager.installFromZip(temp)
                    }
                } finally {
                    temp.delete()
                }
            }
            result
                .onSuccess { Toast.makeText(context, "Плагин «${it.manifest.name}» установлен", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Ошибка плагина: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    SettingsPageColumn(contentPadding) {
        SettingGroup {
            item {
                SettingRow(
                    title = "Плагины на C++",
                    subtitle = "Нативные модули (.so) через JNI · API ${NativePluginManager.API_VERSION} · ${Build.SUPPORTED_ABIS.first()}",
                    icon = Icons.Filled.Code,
                    value = if (NativePluginManager.hostAvailable) "хост активен" else "хост недоступен",
                )
            }
        }

        SectionLabel("Установленные")
        SettingGroup {
            if (plugins.isEmpty()) {
                item { SettingRow(title = "Пока нет плагинов", subtitle = "Установите .ygplugin с библиотекой на C++") }
            }
            plugins.forEach { plugin ->
                item {
                    SettingRow(
                        title = plugin.manifest.name,
                        subtitle = "v${plugin.manifest.version} · ${plugin.manifest.author} · C++",
                        icon = Icons.Filled.Extension,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CheckSwitch(plugin.enabled) { manager.setEnabled(plugin.manifest.id, it) }
                                IconButton(onClick = { pendingDelete = plugin }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить")
                                }
                            }
                        },
                        below = if (plugin.hasDetails()) ({ PluginDetails(plugin) }) else null,
                    )
                }
            }
        }

        SectionLabel("Управление")
        SettingGroup {
            item {
                SettingRow(
                    title = "Установить .ygplugin",
                    subtitle = "zip: manifest.json и lib/<abi>/lib….so",
                    icon = Icons.Filled.FileOpen,
                    onClick = { confirmInstall = true },
                )
            }
            item {
                SettingRow(
                    title = "Перезагрузить плагины",
                    icon = Icons.Filled.Refresh,
                    onClick = { manager.loadAll() },
                )
            }
        }
        SettingsFootnote(
            "Нативный код не изолирован: он работает с теми же правами, что и приложение, и его сбой закрывает Yougram. " +
                "Ставьте только плагины, которым доверяете.",
        )
        SettingsFootnote("SDK: заголовок yougram_plugin.h и пример example_hello.cpp в исходниках приложения.")
    }

    if (confirmInstall) {
        AlertDialog(
            onDismissRequest = { confirmInstall = false },
            title = { Text("Установить плагин?") },
            text = {
                Text(
                    "Плагин — это машинный код. Он получит полный доступ к данным Yougram и вашему аккаунту Telegram. " +
                        "Устанавливайте только из источников, которым доверяете.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmInstall = false
                    picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }) { Text("Выбрать файл") }
            },
            dismissButton = { TextButton(onClick = { confirmInstall = false }) { Text("Отмена") } },
        )
    }

    pendingDelete?.let { plugin ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить плагин?") },
            text = { Text(plugin.manifest.name) },
            confirmButton = {
                TextButton(onClick = {
                    manager.uninstall(plugin.manifest.id)
                    pendingDelete = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
        )
    }
}

private fun InstalledPlugin.hasDetails() =
    manifest.description.isNotBlank() || error != null || manifest.permissions.isNotEmpty()

@Composable
private fun PluginDetails(plugin: InstalledPlugin) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (plugin.manifest.description.isNotBlank()) {
            Text(plugin.manifest.description, style = MaterialTheme.typography.bodyMedium)
        }
        plugin.error?.let {
            Text("Ошибка: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (plugin.manifest.permissions.isNotEmpty()) {
            Text(
                "Разрешения: ${plugin.manifest.permissions.joinToString()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
