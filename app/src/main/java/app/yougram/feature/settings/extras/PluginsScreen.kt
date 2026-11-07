package app.yougram.feature.settings.extras

import app.yougram.core.i18n.tr
import app.yougram.core.ui.glass.plateColor
import app.yougram.core.settings.PlateArea
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.key
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.yougram.core.i18n.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.yougram.feature.settings.component.CheckSwitch
import app.yougram.feature.settings.component.SectionLabel
import app.yougram.feature.settings.component.SettingGroup
import app.yougram.feature.settings.component.SettingRow
import app.yougram.feature.settings.component.SettingsFootnote
import app.yougram.feature.settings.component.SettingsPageColumn
import app.yougram.feature.settings.component.segmentShape
import app.yougram.plugin.InstalledPlugin
import app.yougram.plugin.NativePluginManager
import app.yougram.plugin.SuspiciousPluginException
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
    var settingsFor by remember { mutableStateOf<InstalledPlugin?>(null) }

    var pending by remember { mutableStateOf<PendingPlugin?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val temp = File(context.cacheDir, "plugin-import-${System.nanoTime()}.ygplugin")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                        ?: error("Не удалось прочитать файл")
                    manager.installFromZip(temp)
                }
            }
            val err = result.exceptionOrNull()
            if (err is SuspiciousPluginException) {
                pending = PendingPlugin(temp, err.report) // файл оставляем до решения пользователя
                return@launch
            }
            withContext(Dispatchers.IO) { temp.delete() }
            result
                .onSuccess { Toast.makeText(context, tr("Плагин «{0}» установлен", it.manifest.name), Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, tr("Ошибка плагина: {0}", it.message), Toast.LENGTH_LONG).show() }
        }
    }

    SettingsPageColumn(contentPadding) {
        SettingGroup {
            item {
                SettingRow(
                    title = "Плагины",
                    subtitle = tr("Нативные плагины (.so) через JNI · API {0} · {1}", NativePluginManager.API_VERSION, Build.SUPPORTED_ABIS.first()),
                    icon = Icons.Filled.Code,
                    value = if (NativePluginManager.hostAvailable) "хост активен" else "хост недоступен",
                )
            }
        }

        SectionLabel("Установленные")
        if (plugins.isEmpty()) EmptyPluginsCard()
        // Больше двух плагинов — связанная группа (крайние углы крупные, внутренние малые, зазор 2dp);
        // один или два остаются отдельными карточками.
        val connected = plugins.size > 2
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (connected) 2.dp else 8.dp)) {
            plugins.forEachIndexed { index, plugin ->
                key(plugin.manifest.id) {
                    PluginCard(
                        plugin = plugin,
                        shape = if (connected) segmentShape(index, plugins.size) else CardShape,
                        onToggle = { manager.setEnabled(plugin.manifest.id, it) },
                        onSettings = { settingsFor = plugin },
                        onDelete = { pendingDelete = plugin },
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
            tr("Нативный код не изолирован: он работает с теми же правами, что и приложение, и его сбой закрывает Yougram. ") +
                    tr("Ставьте только плагины, которым доверяете."),
        )
        SettingsFootnote("SDK 2.3: заголовок yougram_plugin.h и пример example_hello.cpp в исходниках приложения.")
    }

    if (confirmInstall) {
        AlertDialog(
            onDismissRequest = { confirmInstall = false },
            title = { Text("Установить плагин?") },
            text = {
                Text(
                    tr("Плагин — это машинный код. Он получит полный доступ к данным Yougram и вашему аккаунту Telegram. ") +
                            tr("Устанавливайте только из источников, которым доверяете."),
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

    pending?.let { p ->
        PluginScanDialog(
            p,
            onInstall = {
                pending = null
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching { manager.installFromZip(p.file, allowSuspicious = true) }.also { p.file.delete() }
                    }
                    r.onSuccess { Toast.makeText(context, tr("Плагин «{0}» установлен", it.manifest.name), Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(context, tr("Ошибка плагина: {0}", it.message), Toast.LENGTH_LONG).show() }
                }
            },
            onCancel = { pending = null; p.file.delete() },
        )
    }

    settingsFor?.let { plugin ->
        PluginSettingsDialog(plugin, manager) { settingsFor = null }
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

/** Иконка плагина из его пакета (manifest.json -> "icon"); null, если её нет или файл некорректен. */
@Composable
private fun rememberPluginIcon(plugin: InstalledPlugin): PluginIcon? =
    androidx.compose.runtime.remember(plugin.directory, plugin.manifest.icon) {
        val name = plugin.manifest.icon
        if (name.isBlank()) return@remember null
        runCatching {
            val dir = plugin.directory.canonicalFile
            val f = java.io.File(dir, name).canonicalFile
            if (!f.path.startsWith(dir.path + java.io.File.separator) || !f.isFile || f.length() > 512 * 1024) null
            else decodePluginIcon(f.readBytes())
        }.getOrNull()
    }

private val CardShape = RoundedCornerShape(28.dp)

/** Карточка установленного плагина: иконка, имя, версия и автор, описание, ошибка, переключатель и действия. */
@Composable
private fun PluginCard(
    plugin: InstalledPlugin,
    shape: androidx.compose.ui.graphics.Shape,
    onToggle: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    val m = plugin.manifest
    val icon = rememberPluginIcon(plugin)
    // Выключенный плагин без ошибки выглядит приглушённо; переключатель и кнопки остаются яркими.
    val dim = if (plugin.enabled || plugin.error != null) 1f else 0.6f
    Surface(
        onClick = onSettings,
        shape = shape,
        color = plateColor(PlateArea.Settings),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PluginCardIcon(m.name, icon, Modifier.alpha(dim))
                Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp).alpha(dim)) {
                    Text(m.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "v${m.version} · ${m.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.padding(end = 8.dp)) { CheckSwitch(plugin.enabled, onToggle) }
            }
            if (m.description.isNotBlank()) {
                Text(
                    m.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp, end = 8.dp).alpha(dim),
                )
            }
            plugin.error?.let {
                Text(
                    tr("Ошибка: {0}", it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PluginChip("API ${m.api}")
                Spacer(Modifier.width(6.dp))
                PluginChip("C++")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = tr("Настройки плагина"))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = tr("Удалить"))
                }
            }
        }
    }
}

@Composable
private fun PluginCardIcon(name: String, icon: PluginIcon?, modifier: Modifier = Modifier) {
    Box(
        modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            PluginIconImage(icon, Modifier.fillMaxSize())
        } else {
            Text(
                name.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PluginChip(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EmptyPluginsCard() {
    Surface(shape = CardShape, color = plateColor(PlateArea.Settings), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Пока нет плагинов", style = MaterialTheme.typography.titleMedium)
            Text(
                "Установите .ygplugin с библиотекой на C++",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}